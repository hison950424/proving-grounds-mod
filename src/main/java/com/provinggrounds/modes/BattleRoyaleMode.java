package com.provinggrounds.modes;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.modes.br.AirdropManager;
import com.provinggrounds.modes.br.BRMovement;
import com.provinggrounds.modes.br.Circle;
import com.provinggrounds.modes.br.CirclePreset;
import com.provinggrounds.modes.br.LootSpawner;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: BattleRoyaleMode.java
 * 目的: BR 大逃殺主邏輯（伺服器端單例，由 WorldEvents 每 tick 呼叫 tick()）。
 *
 * 流程：PREPARING（倒數，期間分批清理並刷新戰利品）
 *     → PLANE（搭飛機橫跨地圖，按 Shift 跳出；飛到尾端自動踢出）
 *     → ACTIVE（自由落體按 Shift 開傘；縮圈、圈外傷害、淘汰、勝負判定）
 *     → ENDING（宣布勝者、分批清理戰利品）→ 還原所有玩家，回大廳。
 *
 * 一般模式：死亡直接變成自由觀察者。
 * 競技模式：死亡後只能觀看同隊存活隊友的視角，整隊淘汰後才變成自由觀察者。
 *
 * 參賽者原本的遊戲模式記在玩家的 persistent data（pg_br），伺服器崩潰後玩家重新登入也能還原。
 * 空投（Phase 1c）由 AirdropManager 處理：第 2 階段縮圈結束時開窗。
 * 倒地、復活牌、祭壇（Phase 1b）尚未實作。
 */
public class BattleRoyaleMode {

    public enum Phase { IDLE, PREPARING, PLANE, ACTIVE, ENDING }
    public enum Variant {
        NORMAL("一般"), COMPETITIVE("競技");
        public final String displayName;
        Variant(String displayName) { this.displayName = displayName; }
    }
    private enum PlayerState { PLANE, FREEFALL, CHUTE, GROUND, DEAD }

    private static final class Participant {
        final UUID id;
        final String name;
        PlayerState state = PlayerState.GROUND;
        boolean alive = true;
        boolean sneakReleased = false;
        int groundTicks = 0;
        @Nullable Entity carrier;

        Participant(ServerPlayer p) {
            this.id = p.getUUID();
            this.name = p.getGameProfile().getName();
        }
    }

    public static final String RESTORE_TAG = "pg_br";
    private static final int ENDING_SECONDS = 10;
    private static final int CHUTE_DURATION_TICKS = 20 * 600;

    private static final BattleRoyaleMode INSTANCE = new BattleRoyaleMode();
    public static BattleRoyaleMode get() { return INSTANCE; }

    private Phase phase = Phase.IDLE;
    private Variant variant = Variant.NORMAL;
    private CirclePreset preset = CirclePreset.STANDARD;
    @Nullable private MapRegistry.BRMap map;
    @Nullable private ServerLevel level;
    @Nullable private Circle circle;
    @Nullable private LootSpawner loot;
    @Nullable private AirdropManager airdrops;
    private long gameId;
    private int phaseTicks;
    private int tickCount;
    private int startTeamCount;

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    /** 受害者 → 曾經對他造成傷害的玩家（助攻判定用）。 */
    private final Map<UUID, Set<UUID>> damagers = new HashMap<>();

    // 飛機航線
    private Vec3 planeStart = Vec3.ZERO, planeDir = Vec3.ZERO;
    private double planeLength, planeTravelled;

    private BattleRoyaleMode() {}

    // ═════════════════════════════════════════════════════════════════════════
    // 查詢
    // ═════════════════════════════════════════════════════════════════════════

    public Phase getPhase() { return phase; }
    public boolean isRunning() { return phase != Phase.IDLE; }
    @Nullable public MapRegistry.BRMap getMap() { return map; }

    public boolean isParticipant(UUID id) { return participants.containsKey(id); }

    public boolean isAliveParticipant(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive;
    }

    /** 自由落體或開傘中（取消摔落傷害用）。 */
    public boolean isDropping(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && (p.state == PlayerState.FREEFALL || p.state == PlayerState.CHUTE);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 開始
    // ═════════════════════════════════════════════════════════════════════════

    /** 開始一局 BR。成功回傳 null，失敗回傳錯誤訊息。 */
    @Nullable
    public String start(MinecraftServer server, int mapId, CirclePreset preset, Variant variant) {
        if (phase != Phase.IDLE || !GameManager.get().isInLobby()) return "目前已有遊戲進行中。";
        MapRegistry.BRMap m = MapRegistry.get(server).getMap(mapId);
        if (m == null) return "找不到地圖 #" + mapId + "，請先用 /pg map set 登記。";

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) players.add(p);
        }
        int min = PGConfig.SERVER.minPlayersToStart.get();
        if (players.size() < min) return "人數不足：需要至少 " + min + " 人，目前 " + players.size() + " 人。";

        if (!GameManager.get().startGame(GameManager.GameMode.BATTLE_ROYALE)) return "無法切換遊戲狀態。";

        this.map = m;
        this.level = server.overworld();
        this.preset = preset;
        this.variant = variant;
        this.gameId = System.currentTimeMillis();
        this.circle = null;
        this.tickCount = 0;
        participants.clear();
        damagers.clear();

        for (ServerPlayer p : players) join(p);
        TeamManager.get().syncVanillaTeams(server, players);
        startTeamCount = countAliveTeams();

        AirdropManager.cleanupAll(level); // 清掉上一局（例如當機）殘留的空投
        airdrops = new AirdropManager(level);
        loot = new LootSpawner(level, m, LootSpawner.Mode.SPAWN);
        phase = Phase.PREPARING;
        phaseTicks = PGConfig.SERVER.lobbyCountdownSeconds.get() * 20;

        broadcast(Component.literal("▸ BATTLE ROYALE").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(String.format("  地圖 #%d %s｜%s｜%s模式｜%d 人 / %d 隊",
                m.id, m.name, preset.displayName, variant.displayName, players.size(), startTeamCount))
                .withStyle(ChatFormatting.WHITE)));
        broadcast(Component.literal(String.format("%d 秒後登機。戰利品點位 %d 個，預期 %.0f 箱。",
            phaseTicks / 20, m.lootPoints.size(), LootSpawner.expectedCrates(m))).withStyle(ChatFormatting.GRAY));
        ProvingGroundsMod.LOGGER.info("BR start: map={} preset={} variant={} players={}", m.id, preset, variant, players.size());
        return null;
    }

    /** 加入成為參賽者（開局時或倒數期間登入）。 */
    private void join(ServerPlayer p) {
        markForRestore(p);
        participants.put(p.getUUID(), new Participant(p));
        GameManager.get().addPlayer(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 每 tick
    // ═════════════════════════════════════════════════════════════════════════

    public void tick() {
        if (phase == Phase.IDLE || level == null || map == null) return;
        MinecraftServer server = level.getServer();
        tickCount++;

        // 所有人都離線（GameManager 已進入 END）→ 直接結束
        if (GameManager.get().getState() == GameManager.GameState.END && phase != Phase.ENDING) {
            beginEnding(null);
        }

        switch (phase) {
            case PREPARING -> tickPreparing();
            case PLANE -> { tickPlane(); tickDropping(); checkWin(); }
            case ACTIVE -> { tickDropping(); tickCircle(); tickSpectators(); checkWin(); }
            case ENDING -> tickEnding();
            default -> {}
        }

        if (phase != Phase.IDLE && tickCount % 5 == 0) syncClients(server);
    }

    private void tickPreparing() {
        if (loot != null) loot.tick();
        if (phaseTicks > 0) {
            phaseTicks--;
            int sec = phaseTicks / 20;
            if (phaseTicks % 20 == 0 && (sec == 20 || sec == 10 || (sec <= 5 && sec >= 1))) {
                broadcast(Component.literal("登機倒數 " + sec + " 秒").withStyle(ChatFormatting.YELLOW));
                playSoundAll(SoundEvents.NOTE_BLOCK_HAT.value(), 1f);
            }
            return;
        }
        if (loot != null && !loot.isDone()) {
            if (tickCount % 40 == 0) {
                broadcast(Component.literal("等待戰利品刷新完成…剩 " + loot.remaining() + " 個點位").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        beginPlane();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 飛機
    // ═════════════════════════════════════════════════════════════════════════

    private void beginPlane() {
        assert level != null && map != null;
        RandomSource rand = level.getRandom();
        double angle = rand.nextDouble() * Math.PI * 2;
        planeDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 perp = new Vec3(-planeDir.z, 0, planeDir.x);
        double offset = (rand.nextDouble() * 2 - 1) * map.radius * 0.4;
        Vec3 pass = new Vec3(map.centerX, PGConfig.SERVER.brPlaneHeight.get(), map.centerZ).add(perp.scale(offset));
        planeLength = map.radius * 2.2;
        planeStart = pass.subtract(planeDir.scale(planeLength / 2));
        planeTravelled = 0;

        float yaw = (float) (Math.toDegrees(Math.atan2(planeDir.z, planeDir.x)) - 90);
        for (Participant part : participants.values()) {
            ServerPlayer p = player(part.id);
            if (p == null || !part.alive) continue;
            level.getChunk((int) Math.floor(planeStart.x) >> 4, (int) Math.floor(planeStart.z) >> 4);
            p.teleportTo(level, planeStart.x, planeStart.y, planeStart.z, yaw, 0);
            Display.ItemDisplay carrier = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            carrier.moveTo(planeStart.x, planeStart.y, planeStart.z, yaw, 0);
            level.addFreshEntity(carrier);
            p.startRiding(carrier, true);
            part.carrier = carrier;
            part.state = PlayerState.PLANE;
            BRMovement.apply(p);
            title(p, Component.literal("已登機").withStyle(ChatFormatting.GOLD),
                Component.literal("按 Shift 跳傘").withStyle(ChatFormatting.WHITE));
        }
        phase = Phase.PLANE;
        broadcast(Component.literal("飛機起飛！任何時候按 Shift 跳出，飛到尾端會自動跳出。").withStyle(ChatFormatting.AQUA));
    }

    private void tickPlane() {
        planeTravelled += PGConfig.SERVER.brPlaneSpeed.get() / 20.0;
        boolean end = planeTravelled >= planeLength;
        Vec3 pos = planeStart.add(planeDir.scale(Math.min(planeTravelled, planeLength)));

        boolean anyoneOnPlane = false;
        for (Participant part : participants.values()) {
            if (part.state != PlayerState.PLANE) continue;
            ServerPlayer p = player(part.id);
            if (p == null) { discardCarrier(part); continue; }
            if (part.carrier != null && part.carrier.isAlive()) {
                part.carrier.moveTo(pos.x, pos.y, pos.z, part.carrier.getYRot(), 0);
            }
            boolean stillRiding = part.carrier != null && p.getVehicle() == part.carrier;
            if (!stillRiding || end) {
                if (stillRiding) p.stopRiding();
                jump(p, part);
            } else {
                anyoneOnPlane = true;
            }
        }
        if (end || !anyoneOnPlane) beginActive();
    }

    private void jump(ServerPlayer p, Participant part) {
        discardCarrier(part);
        part.state = PlayerState.FREEFALL;
        part.sneakReleased = false;
        part.groundTicks = 0;
        p.fallDistance = 0;
        p.displayClientMessage(Component.literal("自由落體中：按 Shift 開傘").withStyle(ChatFormatting.YELLOW), true);
    }

    private void discardCarrier(Participant part) {
        if (part.carrier != null) {
            part.carrier.ejectPassengers();
            part.carrier.discard();
            part.carrier = null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 跳傘
    // ═════════════════════════════════════════════════════════════════════════

    private void tickDropping() {
        for (Participant part : participants.values()) {
            if (!part.alive || (part.state != PlayerState.FREEFALL && part.state != PlayerState.CHUTE)) continue;
            ServerPlayer p = player(part.id);
            if (p == null) continue;

            if (part.state == PlayerState.FREEFALL) {
                boolean sneak = p.isShiftKeyDown();
                if (!sneak) {
                    part.sneakReleased = true; // 剛跳機時還按著 Shift，要先放開再按才開傘
                } else if (part.sneakReleased) {
                    part.state = PlayerState.CHUTE;
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CHUTE_DURATION_TICKS, 0, false, false, false));
                    level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA, SoundSource.PLAYERS, 1f, 1f);
                    p.displayClientMessage(Component.literal("已開傘").withStyle(ChatFormatting.GREEN), true);
                }
            }

            if (p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying) {
                if (++part.groundTicks >= 10) { // 落地後留 10 tick 緩衝，確保摔落傷害已被取消
                    part.state = PlayerState.GROUND;
                    p.removeEffect(MobEffects.SLOW_FALLING);
                    p.fallDistance = 0;
                }
            } else {
                part.groundTicks = 0;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 縮圈
    // ═════════════════════════════════════════════════════════════════════════

    private void beginActive() {
        assert map != null && level != null;
        for (Participant part : participants.values()) discardCarrier(part);
        double maxSpeed = PGConfig.SERVER.brSprintSpeed() * PGConfig.SERVER.brCircleMaxSpeedFactor.get();
        circle = new Circle(map.centerX, map.centerZ, map.radius, preset.ratios(), preset.intervalSeconds(),
            maxSpeed, level.getRandom());
        phase = Phase.ACTIVE;
        broadcast(Component.literal(String.format("第 1 階段：%d 秒後開始縮圈。", circle.secondsLeft()))
            .withStyle(ChatFormatting.AQUA));
    }

    private void tickCircle() {
        if (circle == null) return;
        int warnTicks = PGConfig.SERVER.brCircleWarnSeconds.get() * 20;
        if (!circle.isShrinking() && !circle.finished() && circle.ticksLeft() == warnTicks + 1) {
            broadcast(Component.literal(String.format("⚠ %d 秒後開始縮圈（第 %d / %d 階段）",
                warnTicks / 20, circle.displayStage(), circle.totalStages())).withStyle(ChatFormatting.YELLOW));
        }
        switch (circle.tick()) {
            case SHRINK_STARTED -> {
                broadcast(Component.literal(String.format("▼ 第 %d 階段縮圈開始！", circle.displayStage()))
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                playSoundAll(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6f);
            }
            case SHRINK_ENDED -> {
                // 第 2 階段縮圈結束 → 開啟空投時間窗（到最後一階段縮圈結束）
                if (airdrops != null && circle.completedStages() == 2) {
                    airdrops.openWindow(circle.totalStages() - 2, preset.intervalSeconds());
                }
                if (circle.finished()) {
                    broadcast(Component.literal("最終圈已成形。").withStyle(ChatFormatting.RED));
                } else {
                    broadcast(Component.literal(String.format("第 %d 階段：%d 秒後開始縮圈。",
                        circle.displayStage(), circle.secondsLeft())).withStyle(ChatFormatting.AQUA));
                }
            }
            default -> {}
        }
        if (airdrops != null) airdrops.tick(circle);

        if (tickCount % 20 != 0) return;
        PGConfig c = PGConfig.SERVER;
        float damage = (float) (c.brDamageBase.get() + c.brDamagePerStage.get() * circle.damageStageIndex());
        if (damage <= 0) return;
        for (Participant part : participants.values()) {
            if (!part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p == null || !circle.isOutside(p.getX(), p.getZ())) continue;
            p.hurt(p.damageSources().outOfBorder(), damage);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 淘汰
    // ═════════════════════════════════════════════════════════════════════════

    /** 記錄傷害來源（助攻判定）。 */
    public void onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        if (amount <= 0 || (phase != Phase.PLANE && phase != Phase.ACTIVE)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        if (!isAliveParticipant(victim.getUUID()) || !isParticipant(attacker.getUUID())) return;
        if (TeamManager.get().isSameTeam(attacker, victim)) return;
        damagers.computeIfAbsent(victim.getUUID(), k -> new HashSet<>()).add(attacker.getUUID());
    }

    /**
     * 參賽者受到致死傷害。回傳 true 代表 BR 接手處理（呼叫端應取消死亡事件）。
     */
    public boolean onLethalDamage(ServerPlayer victim, DamageSource source) {
        Participant part = participants.get(victim.getUUID());
        if (part == null) return false;
        if (phase == Phase.ENDING) {
            healFull(victim); // 結算期間不再淘汰任何人
            return true;
        }
        if (!part.alive || (phase != Phase.PLANE && phase != Phase.ACTIVE)) return false;
        eliminate(victim, part, source);
        return true;
    }

    private void eliminate(ServerPlayer victim, Participant part, @Nullable DamageSource source) {
        boolean competitive = variant == Variant.COMPETITIVE;
        PGConfig c = PGConfig.SERVER;
        PlayerDataManager data = PlayerDataManager.get();

        ServerPlayer killer = null;
        if (source != null) {
            if (source.getEntity() instanceof ServerPlayer sp && sp != victim) killer = sp;
            else if (victim.getKillCredit() instanceof ServerPlayer kc && kc != victim) killer = kc;
        }
        if (killer != null && !isParticipant(killer.getUUID())) killer = null;

        data.addDeath(victim);
        if (killer != null && !TeamManager.get().isSameTeam(killer, victim)) {
            data.addKill(killer);
            int xp = data.addXp(killer, c.xpKill.get(), competitive);
            killer.displayClientMessage(Component.literal("擊殺 " + part.name + "  +" + xp + " XP")
                .withStyle(ChatFormatting.GOLD), true);
        }
        Set<UUID> hitBy = damagers.remove(victim.getUUID());
        if (hitBy != null) {
            for (UUID id : hitBy) {
                if (killer != null && id.equals(killer.getUUID())) continue;
                ServerPlayer assister = player(id);
                if (assister == null) continue;
                data.addAssist(assister);
                int xp = data.addXp(assister, c.xpAssist.get(), competitive);
                assister.displayClientMessage(Component.literal("助攻 " + part.name + "  +" + xp + " XP")
                    .withStyle(ChatFormatting.YELLOW), true);
            }
        }

        Component deathMsg = source != null
            ? source.getLocalizedDeathMessage(victim)
            : Component.literal(part.name + " 已離線，視為淘汰");
        broadcast(deathMsg.copy().withStyle(ChatFormatting.GRAY));
        PGNetwork.sendToAll(new KillFeedPacket(
            killer != null ? killer.getGameProfile().getName() : "", killer != null ? colorOf(killer.getUUID()) : 0xFFFFFF,
            part.name, colorOf(victim.getUUID())));

        // 屍體位置掉落全部物品，然後變成觀察者
        victim.getInventory().dropAll();
        discardCarrier(part);
        victim.stopRiding();
        BRMovement.remove(victim);
        healFull(victim);
        victim.setGameMode(GameType.SPECTATOR);

        part.alive = false;
        part.state = PlayerState.DEAD;

        int teamsLeft = countAliveTeams();
        title(victim, Component.literal("已淘汰").withStyle(ChatFormatting.RED),
            Component.literal("剩餘 " + teamsLeft + " 隊").withStyle(ChatFormatting.GRAY));
        if (competitive) attachToTeammate(victim);
    }

    /** 參賽者離線：PREPARING 期間直接移出；遊戲中視為淘汰（物品掉在原地）。 */
    public void onLogout(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        if (part == null) return;
        if (phase == Phase.PREPARING) {
            participants.remove(p.getUUID());
            return;
        }
        if (part.alive && (phase == Phase.PLANE || phase == Phase.ACTIVE)) {
            eliminate(p, part, null);
        }
        discardCarrier(part);
    }

    /** 玩家登入：倒數期間加入參賽；遊戲中變觀察者；沒有遊戲但有還原標記就還原。 */
    public void onLogin(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(RESTORE_TAG);
        boolean sameGame = tag.contains("game") && tag.getLong("game") == gameId && phase != Phase.IDLE;

        if (phase == Phase.PREPARING && !isParticipant(p.getUUID()) && !p.isSpectator()) {
            join(p);
            if (level != null) {
                List<ServerPlayer> all = new ArrayList<>();
                for (Participant part : participants.values()) {
                    ServerPlayer sp = player(part.id);
                    if (sp != null) all.add(sp);
                }
                TeamManager.get().syncVanillaTeams(level.getServer(), all);
                startTeamCount = countAliveTeams();
            }
            p.sendSystemMessage(Component.literal("已加入即將開始的 BR。").withStyle(ChatFormatting.GREEN));
        } else if (phase != Phase.IDLE) {
            if (!sameGame) markForRestore(p);
            p.setGameMode(GameType.SPECTATOR);
            p.sendSystemMessage(Component.literal("BR 進行中，你目前是觀察者。").withStyle(ChatFormatting.GRAY));
        } else if (!tag.isEmpty()) {
            restore(p);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 觀戰（競技模式）
    // ═════════════════════════════════════════════════════════════════════════

    private void tickSpectators() {
        if (variant != Variant.COMPETITIVE) return;
        for (Participant part : participants.values()) {
            if (part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p == null) continue;
            Entity cam = p.getCamera();
            boolean watchingTeammate = cam != p && cam instanceof ServerPlayer target
                && isAliveParticipant(target.getUUID()) && TeamManager.get().isSameTeam(target, p);
            if (!watchingTeammate) attachToTeammate(p);
        }
    }

    /** 把觀察者鎖到一位存活隊友的視角；整隊淘汰時放開成自由觀察者。 */
    private void attachToTeammate(ServerPlayer spectator) {
        for (Participant other : participants.values()) {
            if (!other.alive || !TeamManager.get().isSameTeam(other.id, spectator.getUUID())) continue;
            ServerPlayer mate = player(other.id);
            if (mate != null) {
                spectator.setCamera(mate);
                return;
            }
        }
        if (spectator.getCamera() != spectator) spectator.setCamera(spectator);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 勝負與結束
    // ═════════════════════════════════════════════════════════════════════════

    private int countAliveTeams() {
        Set<String> teams = new HashSet<>();
        for (Participant part : participants.values()) {
            if (part.alive) teams.add(TeamManager.get().teamKey(part.id));
        }
        return teams.size();
    }

    private void checkWin() {
        if (phase != Phase.PLANE && phase != Phase.ACTIVE) return;
        Set<String> alive = new HashSet<>();
        for (Participant part : participants.values()) {
            if (part.alive) alive.add(TeamManager.get().teamKey(part.id));
        }
        // 開局只有 1 隊（單人測試）時，要等全員淘汰才結束
        int threshold = startTeamCount > 1 ? 1 : 0;
        if (alive.size() <= threshold) {
            beginEnding(alive.size() == 1 ? alive.iterator().next() : null);
        }
    }

    private void beginEnding(@Nullable String winnerKey) {
        if (phase == Phase.ENDING || phase == Phase.IDLE || level == null || map == null) return;
        boolean competitive = variant == Variant.COMPETITIVE;
        PlayerDataManager data = PlayerDataManager.get();
        List<String> winnerNames = new ArrayList<>();
        String teamLabel = null;

        if (winnerKey != null && startTeamCount > 1) {
            for (Participant part : participants.values()) {
                if (!TeamManager.get().teamKey(part.id).equals(winnerKey)) continue;
                winnerNames.add(part.name);
                ServerPlayer p = player(part.id);
                if (p == null) continue;
                data.addWin(p);
                int xp = data.addXp(p, PGConfig.SERVER.xpWin.get(), competitive);
                p.sendSystemMessage(Component.literal("勝利！+" + xp + " XP").withStyle(ChatFormatting.GOLD));
                TeamManager.Team t = TeamManager.get().getTeamOrSolo(part.id);
                if (t != TeamManager.Team.SOLO) teamLabel = t.displayName;
            }
        }

        Component headline = winnerNames.isEmpty()
            ? Component.literal("本局結束").withStyle(ChatFormatting.GRAY)
            : Component.literal((teamLabel != null ? teamLabel + " " : "") + String.join("、", winnerNames) + " 獲勝！")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        broadcast(headline);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            title(p, winnerNames.isEmpty() ? Component.literal("本局結束") : Component.literal("WINNER WINNER").withStyle(ChatFormatting.GOLD),
                headline);
        }
        playSoundAll(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);

        for (Participant part : participants.values()) discardCarrier(part);
        AirdropManager.cleanupAll(level);
        airdrops = null;
        GameManager.get().endGame();
        loot = new LootSpawner(level, map, LootSpawner.Mode.CLEAR);
        phase = Phase.ENDING;
        phaseTicks = ENDING_SECONDS * 20;
        ProvingGroundsMod.LOGGER.info("BR ended: winners={}", winnerNames);
    }

    private void tickEnding() {
        if (loot != null) loot.tick();
        if (phaseTicks > 0) { phaseTicks--; return; }
        if (loot != null && !loot.isDone()) return;
        finish();
    }

    /** 管理員測試用：立刻投下一個空投。成功回傳 null，失敗回傳原因。 */
    @Nullable
    public String forceAirdrop() {
        if (phase != Phase.ACTIVE || circle == null || airdrops == null) return "只能在 BR 進行中（飛機起飛後）使用。";
        if (!airdrops.forceDrop(circle)) return "已經有一個空投正在掉落。";
        return null;
    }

    /** 正常結束：還原所有玩家並回大廳。 */
    private void finish() {
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        clearState();
        if (server != null) restoreAllOnline(server);
        PGNetwork.sendToAll(BRStatePacket.none());
        GameManager.get().reset();
    }

    /**
     * 強制停止（/pg reset、伺服器關閉）。cleanLoot=true 時同步清掉所有箱子；
     * false 時留給下一局開局清理（開局一定會先清每個點位）。
     */
    public void forceStop(boolean cleanLoot) {
        if (phase == Phase.IDLE) return;
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        for (Participant part : participants.values()) discardCarrier(part);
        if (cleanLoot && level != null && map != null) {
            new LootSpawner(level, map, LootSpawner.Mode.CLEAR).runToCompletion();
        }
        // 空投一律清掉（數量很少，同步處理不會卡），也順便解除強制載入的區塊
        if (level != null) AirdropManager.cleanupAll(level);
        clearState();
        if (server != null) restoreAllOnline(server);
        PGNetwork.sendToAll(BRStatePacket.none());
        ProvingGroundsMod.LOGGER.info("BR force-stopped (cleanLoot={}).", cleanLoot);
    }

    private void clearState() {
        phase = Phase.IDLE;
        participants.clear();
        damagers.clear();
        circle = null;
        loot = null;
        airdrops = null;
        map = null;
        gameId = 0;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 還原
    // ═════════════════════════════════════════════════════════════════════════

    private void markForRestore(ServerPlayer p) {
        CompoundTag data = p.getPersistentData();
        CompoundTag tag = data.getCompound(RESTORE_TAG);
        if (!tag.contains("mode")) tag.putInt("mode", p.gameMode.getGameModeForPlayer().getId());
        tag.putLong("game", gameId);
        data.put(RESTORE_TAG, tag);
    }

    private void restoreAllOnline(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getPersistentData().contains(RESTORE_TAG)) restore(p);
        }
    }

    /** 還原成原版狀態：移除速度修改、清空背包、恢復遊戲模式、傳回世界出生點。 */
    public void restore(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(RESTORE_TAG);
        GameType mode = GameType.byId(tag.contains("mode") ? tag.getInt("mode") : GameType.SURVIVAL.getId());
        p.getPersistentData().remove(RESTORE_TAG);

        p.stopRiding();
        if (p.getCamera() != p) p.setCamera(p);
        BRMovement.remove(p);
        p.getInventory().clearContent();
        p.removeAllEffects();
        healFull(p);
        p.setGameMode(mode);
        ServerLevel overworld = p.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        p.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
        PGNetwork.sendTo(p, BRStatePacket.none());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 客戶端同步
    // ═════════════════════════════════════════════════════════════════════════

    private void syncClients(MinecraftServer server) {
        if (map == null) return;
        byte phaseId = switch (phase) {
            case PREPARING -> ClientBRState.PHASE_PREPARING;
            case PLANE -> ClientBRState.PHASE_PLANE;
            case ACTIVE -> ClientBRState.PHASE_ACTIVE;
            case ENDING -> ClientBRState.PHASE_ENDING;
            default -> ClientBRState.PHASE_NONE;
        };
        int seconds = switch (phase) {
            case PREPARING, ENDING -> (phaseTicks + 19) / 20;
            case PLANE -> (int) Math.ceil((planeLength - planeTravelled) / PGConfig.SERVER.brPlaneSpeed.get());
            case ACTIVE -> circle != null ? circle.secondsLeft() : 0;
            default -> 0;
        };
        float cx, cz, r, nx, nz, nr;
        boolean shrinking = false;
        byte stage = 0, total = (byte) preset.ratios().length;
        if (circle != null) {
            cx = (float) circle.centerX(); cz = (float) circle.centerZ(); r = (float) circle.radius();
            nx = (float) circle.nextX(); nz = (float) circle.nextZ(); nr = (float) circle.nextRadius();
            shrinking = circle.isShrinking();
            stage = (byte) circle.displayStage();
            total = (byte) circle.totalStages();
        } else {
            cx = (float) map.centerX; cz = (float) map.centerZ; r = (float) map.radius;
            nx = 0; nz = 0; nr = -1;
        }
        int alivePlayers = 0;
        for (Participant part : participants.values()) if (part.alive) alivePlayers++;
        short aliveTeams = (short) countAliveTeams();
        float jump = PGConfig.SERVER.brJumpHeight.get().floatValue();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Participant part = participants.get(p.getUUID());
            byte self = ClientBRState.SELF_NONE;
            if (part != null) {
                self = switch (part.state) {
                    case PLANE -> ClientBRState.SELF_PLANE;
                    case FREEFALL -> ClientBRState.SELF_FREEFALL;
                    case CHUTE -> ClientBRState.SELF_CHUTE;
                    case GROUND -> ClientBRState.SELF_ALIVE;
                    case DEAD -> ClientBRState.SELF_DEAD;
                };
                if (phase == Phase.PREPARING) self = ClientBRState.SELF_NONE;
            }
            boolean movement = part != null && part.alive && phase != Phase.PREPARING && phase != Phase.ENDING;
            PGNetwork.sendTo(p, new BRStatePacket(phaseId, seconds, shrinking, stage, total,
                cx, cz, r, nx, nz, nr, (short) alivePlayers, aliveTeams, self, movement ? jump : 0f));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 小工具
    // ═════════════════════════════════════════════════════════════════════════

    @Nullable
    private ServerPlayer player(UUID id) {
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private int colorOf(UUID id) {
        Integer c = TeamManager.get().getTeamOrSolo(id).color.getColor();
        return c != null ? c : 0xFFFFFF;
    }

    private static void healFull(LivingEntity e) {
        e.setHealth(e.getMaxHealth());
        e.clearFire();
        e.fallDistance = 0;
        if (e instanceof ServerPlayer p) {
            p.getFoodData().setFoodLevel(20);
        }
    }

    private void broadcast(Component msg) {
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        if (server != null) server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private void playSoundAll(net.minecraft.sounds.SoundEvent sound, float pitch) {
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(sound, SoundSource.MASTER, 1f, pitch);
        }
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
