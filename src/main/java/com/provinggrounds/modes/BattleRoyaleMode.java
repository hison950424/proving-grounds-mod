package com.provinggrounds.modes;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.AltarBlockEntity;
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
import com.provinggrounds.modes.br.RespawnKit;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: BattleRoyaleMode.java
 * 目的: BR 大逃殺主邏輯（伺服器端單例，由 WorldEvents 每 tick 呼叫 tick()）。
 *
 * 流程：PREPARING（倒數，期間分批清理並刷新戰利品）
 *     → PLANE（搭飛機橫跨地圖，按 Shift 跳出；飛到尾端自動踢出）
 *     → ACTIVE（自由落體按 Shift 開傘；縮圈、圈外傷害、倒地、復活、勝負判定）
 *     → ENDING（宣布勝者、分批清理戰利品）→ 還原所有玩家，回大廳。
 *
 * 倒地（Phase 1b）：受到致死傷害時，若同隊還有站著的隊友就倒地（孤狼、隊伍最後一人、
 *   虛空／指令致死直接死亡）。倒地趴下緩速、不能做任何動作；bleed_seconds 內沒被扶起就死亡。
 *   同隊站著的隊友靠近蹲下 revive_seconds 秒即扶起，扶的期間流血暫停。全隊都倒地 → 全部死亡。
 * 復活牌：死亡時在屍體位置掉落（孤狼、競技模式已用過祭壇的人沒有）。只有同隊撿得到，撿起後
 *   存在撿的人身上（不佔背包）。第一次掉落的牌 revive_card_ground_seconds 秒內沒人撿、或掉進
 *   虛空 → 該玩家確定死亡。持有者倒地或死亡時牌會再掉落，再掉落的牌沒有期限。
 *   全隊都死了 → 不用等計時，全隊確定死亡。
 * 祭壇：身上有隊友復活牌的人在祭壇旁蹲下或按住右鍵 altar_activate_seconds 秒，
 *   復活同隊所有「牌已被撿起」的死者；他們傳到祭壇上空重新跳傘。祭壇進入冷卻。
 *   競技模式每人一場只能被祭壇復活一次。
 *
 * 一般模式：死亡直接變成自由觀察者。
 * 競技模式：死亡後只能觀看同隊存活隊友的視角，整隊淘汰後才變成自由觀察者。
 * 空投（Phase 1c）由 AirdropManager 處理：第 2 階段縮圈結束時開窗。
 *
 * 參賽者原本的遊戲模式記在玩家的 persistent data（pg_br），伺服器崩潰後玩家重新登入也能還原。
 */
public class BattleRoyaleMode {

    public enum Phase { IDLE, PREPARING, PLANE, ACTIVE, ENDING }
    public enum Variant {
        NORMAL("一般"), COMPETITIVE("競技");
        public final String displayName;
        Variant(String displayName) { this.displayName = displayName; }
    }
    private enum PlayerState { PLANE, FREEFALL, CHUTE, GROUND, DEAD }
    /** 死亡原因（決定死亡訊息、擊殺歸屬、是否掉復活牌）。 */
    private enum Cause { DAMAGE, BLEED, TEAM_WIPE, LOGOUT }

    private static final class Participant {
        final UUID id;
        final String name;
        PlayerState state = PlayerState.GROUND;
        boolean alive = true;
        boolean sneakReleased = false;
        int groundTicks = 0;
        @Nullable Entity carrier;
        @Nullable Vec3 lastSafe;

        // 倒地
        boolean downed = false;
        int bleedTicks;
        @Nullable UUID downedBy;
        int lockedSlot;
        @Nullable Entity seat;
        int reviveTicks;
        @Nullable UUID reviver;

        // 死亡 / 復活
        boolean finalDead = false;   // 確定死亡（不能再被復活）
        boolean usedAltar = false;   // 競技模式：已用掉祭壇復活機會
        final List<UUID> heldCards = new ArrayList<>(); // 身上持有的隊友復活牌（死者 UUID）

        // 祭壇
        @Nullable BlockPos altarTarget;
        int altarTicks;
        @Nullable BlockPos altarUsePos;
        int lastAltarUse = -1000;

        // 這一 tick 的讀條（給 HUD）
        byte progressKind;
        float progress;

        Participant(ServerPlayer p) {
            this.id = p.getUUID();
            this.name = p.getGameProfile().getName();
        }
    }

    /** 地上的復活牌。 */
    private static final class Card {
        final UUID victim;
        @Nullable ItemEntity entity;
        final int deadline;          // 到期的 tickCount；-1 = 再掉落的牌，沒有期限
        Vec3 lastSafe;
        @Nullable ChunkPos ticket;

        Card(UUID victim, int deadline, Vec3 pos) {
            this.victim = victim; this.deadline = deadline; this.lastSafe = pos;
        }
    }

    public static final String RESTORE_TAG = "pg_br";
    public static final String CARD_TAG = "pg_card_victim";
    private static final int ENDING_SECONDS = 10;
    private static final int CHUTE_DURATION_TICKS = 20 * 600;
    /** 復活牌所在區塊的載入票（不存檔，伺服器重啟自動消失）。 */
    private static final TicketType<ChunkPos> CARD_TICKET =
        TicketType.create("provinggrounds_card", Comparator.comparingLong(ChunkPos::toLong));

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
    private boolean resolving = false;

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    /** 受害者 → 曾經對他造成傷害的玩家（助攻判定用）。 */
    private final Map<UUID, Set<UUID>> damagers = new HashMap<>();
    /** 死者 → 地上的復活牌。 */
    private final Map<UUID, Card> groundCards = new LinkedHashMap<>();

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
    /** 本局識別碼（沒有遊戲時為 0）。祭壇用它判斷冷卻是不是這一局的。 */
    public long currentGameId() { return gameId; }

    public boolean isParticipant(UUID id) { return participants.containsKey(id); }

    public boolean isAliveParticipant(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive;
    }

    public boolean isDowned(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && p.downed;
    }

    /** 坐姿倒地時，這是不是他的座椅（不准離座）。 */
    public boolean isDownedSeat(ServerPlayer p, Entity vehicle) {
        Participant part = participants.get(p.getUUID());
        return part != null && part.downed && part.seat != null && part.seat == vehicle;
    }

    /** 自由落體或開傘中（取消摔落傷害用）。 */
    public boolean isDropping(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && (p.state == PlayerState.FREEFALL || p.state == PlayerState.CHUTE);
    }

    private boolean inPlay() {
        return phase == Phase.PLANE || phase == Phase.ACTIVE;
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
        this.gameId = System.currentTimeMillis(); // 新的 id → 所有祭壇自動就緒
        this.circle = null;
        this.tickCount = 0;
        participants.clear();
        damagers.clear();
        groundCards.clear();

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
            case PLANE -> { tickPlane(); tickDropping(); tickRevival(); checkWin(); }
            case ACTIVE -> { tickDropping(); tickCircle(); tickRevival(); tickSpectators(); checkWin(); }
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

        // 倒數期間管理員可能換過隊（/pg team join）：登機前重新同步原版隊伍與開局隊伍數
        List<ServerPlayer> online = new ArrayList<>();
        for (Participant part : participants.values()) {
            ServerPlayer sp = player(part.id);
            if (sp != null) online.add(sp);
        }
        TeamManager.get().syncVanillaTeams(level.getServer(), online);
        startTeamCount = countAliveTeams();

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

            if (p.onGround() || p.isInWater() || (p.isPassenger() && part.seat == null) || p.getAbilities().flying) {
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
        for (Participant part : new ArrayList<>(participants.values())) {
            if (!part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p == null || !circle.isOutside(p.getX(), p.getZ())) continue;
            p.hurt(p.damageSources().outOfBorder(), damage);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 受傷 / 致死
    // ═════════════════════════════════════════════════════════════════════════

    /** 記錄傷害來源（助攻判定），並中斷受傷者的祭壇讀條。 */
    public void onDamaged(ServerPlayer victim, DamageSource source, float amount) {
        if (amount <= 0 || !inPlay()) return;
        Participant vp = participants.get(victim.getUUID());
        if (vp != null) { vp.altarTicks = 0; vp.altarTarget = null; }
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
        if (phase == Phase.ENDING || phase == Phase.PREPARING) {
            healFull(victim); // 登機前與結算期間不會死（不跳出原版死亡畫面）
            return true;
        }
        if (!part.alive || !inPlay()) return false;
        if (part.downed) {
            eliminate(victim, part, source, Cause.DAMAGE); // 倒地中再受致死傷害 → 死亡
        } else if (canBeDowned(part, source)) {
            down(victim, part, source);
        } else {
            eliminate(victim, part, source, Cause.DAMAGE);
        }
        return true;
    }

    /** 同隊還有至少 1 位站著的隊友才會倒地；孤狼、虛空、/kill 直接死亡。 */
    private boolean canBeDowned(Participant part, DamageSource source) {
        // 只認虛空與 /kill。不能用「無視無敵」標籤判斷：TACZ 的 bullet_void 子彈也在那個標籤裡，
        // 會讓被槍打死的人全部跳過倒地直接死亡。
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return false;
        if (TeamManager.get().getTeamOrSolo(part.id) == TeamManager.Team.SOLO) return false;
        for (Participant other : participants.values()) {
            if (other == part || !other.alive || other.downed) continue;
            if (TeamManager.get().isSameTeam(other.id, part.id) && player(other.id) != null) return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 倒地與扶起
    // ═════════════════════════════════════════════════════════════════════════

    private void down(ServerPlayer victim, Participant part, DamageSource source) {
        PGConfig c = PGConfig.SERVER;
        ServerPlayer attacker = creditFrom(victim, source);

        part.downed = true;
        part.bleedTicks = c.brBleedSeconds.get() * 20;
        part.reviveTicks = 0;
        part.reviver = null;
        part.downedBy = attacker != null ? attacker.getUUID() : null;
        part.lockedSlot = victim.getInventory().selected;
        part.altarTicks = 0;
        part.altarTarget = null;

        victim.stopUsingItem();
        victim.closeContainer();
        victim.setSprinting(false);
        victim.clearFire();
        victim.setHealth((float) Math.min(c.brDownedHealth.get(), victim.getMaxHealth()));
        BRMovement.applyDowned(victim);
        if (c.brDownedStyle.get() == PGConfig.DownedStyle.SIT) {
            sitDown(victim, part);
        } else {
            victim.setForcedPose(Pose.SWIMMING);
        }

        // 倒地時身上的隊友復活牌掉回地上
        dropHeldCards(victim, part, safePos(victim, part));

        Component msg = attacker != null
            ? Component.literal(part.name + " 被 " + attacker.getGameProfile().getName() + " 擊倒")
            : Component.literal(part.name + " 倒地了");
        broadcast(msg.copy().withStyle(ChatFormatting.GRAY));
        title(victim, Component.literal("你倒地了").withStyle(ChatFormatting.RED),
            Component.literal("等待隊友扶起（" + c.brBleedSeconds.get() + " 秒）").withStyle(ChatFormatting.GRAY));
        level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);

        resolveTeams();
    }

    /** 坐姿倒地：在地面放一個隱形座椅讓玩家坐上去。 */
    private void sitDown(ServerPlayer victim, Participant part) {
        assert level != null;
        BlockPos pos = victim.blockPosition();
        for (int i = 0; i < 64 && pos.getY() > level.getMinBuildHeight()
            && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
            pos = pos.below();
        }
        Display.ItemDisplay seat = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        seat.moveTo(victim.getX(), pos.getY() + 0.1, victim.getZ(), victim.getYRot(), 0);
        level.addFreshEntity(seat);
        victim.stopRiding();
        victim.startRiding(seat, true);
        part.seat = seat;
    }

    /** 解除倒地狀態（扶起、死亡、遊戲結束時）。要先把 downed 設成 false 才能離開座椅。 */
    private void clearDowned(@Nullable ServerPlayer p, Participant part) {
        if (!part.downed && part.seat == null) return;
        part.downed = false;
        part.reviveTicks = 0;
        part.reviver = null;
        Entity seat = part.seat;
        part.seat = null;
        if (p != null) {
            p.setForcedPose(null);
            BRMovement.removeDowned(p);
            if (seat != null && p.getVehicle() == seat) p.stopRiding();
        }
        if (seat != null) seat.discard();
    }

    private void tickDowned() {
        PGConfig c = PGConfig.SERVER;
        int reviveTotal = c.brReviveSeconds.get() * 20;
        Set<UUID> busy = new HashSet<>();
        for (Participant d : new ArrayList<>(participants.values())) {
            if (!d.alive || !d.downed) continue;
            ServerPlayer dp = player(d.id);
            if (dp == null) continue;

            // 鎖住手持格（伺服器端沒辦法阻止客戶端切換，只能每 tick 改回來）
            if (dp.getInventory().selected != d.lockedSlot) {
                dp.getInventory().selected = d.lockedSlot;
                dp.connection.send(new ClientboundSetCarriedItemPacket(d.lockedSlot));
            }
            // 坐姿：確保還坐著
            if (d.seat != null && d.seat.isAlive() && dp.getVehicle() != d.seat) dp.startRiding(d.seat, true);

            ServerPlayer helper = findReviver(d, dp, busy);
            if (helper != null) {
                busy.add(helper.getUUID());
                if (!helper.getUUID().equals(d.reviver)) { d.reviver = helper.getUUID(); d.reviveTicks = 0; }
                d.reviveTicks++; // 扶的期間流血暫停
                float prog = d.reviveTicks / (float) reviveTotal;
                d.progressKind = ClientBRState.PROGRESS_BEING_REVIVED;
                d.progress = prog;
                Participant hp = participants.get(helper.getUUID());
                if (hp != null) { hp.progressKind = ClientBRState.PROGRESS_REVIVING; hp.progress = prog; }
                if (d.reviveTicks >= reviveTotal) revive(dp, d, helper);
            } else {
                d.reviver = null;
                d.reviveTicks = 0;
                if (--d.bleedTicks <= 0) eliminate(dp, d, null, Cause.BLEED);
            }
        }
    }

    /** 找扶起者：同隊、站著、在地面、蹲下、在範圍內、這 tick 沒在扶別人。優先沿用正在扶的人。 */
    @Nullable
    private ServerPlayer findReviver(Participant d, ServerPlayer dp, Set<UUID> busy) {
        double range = PGConfig.SERVER.brReviveRange.get();
        if (d.reviver != null) {
            ServerPlayer cur = player(d.reviver);
            if (cur != null && canRevive(d, dp, cur, range, busy)) return cur;
        }
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (Participant h : participants.values()) {
            if (h == d) continue;
            ServerPlayer hp = player(h.id);
            if (hp == null || !canRevive(d, dp, hp, range, busy)) continue;
            double dist = hp.distanceToSqr(dp);
            if (dist < bestDist) { bestDist = dist; best = hp; }
        }
        return best;
    }

    private boolean canRevive(Participant d, ServerPlayer dp, ServerPlayer hp, double range, Set<UUID> busy) {
        Participant h = participants.get(hp.getUUID());
        return h != null && h.alive && !h.downed && h.state == PlayerState.GROUND && !busy.contains(h.id)
            && TeamManager.get().isSameTeam(h.id, d.id) && hp.isShiftKeyDown() && !hp.isSpectator()
            && hp.distanceTo(dp) <= range;
    }

    private void revive(ServerPlayer dp, Participant d, ServerPlayer helper) {
        clearDowned(dp, d);
        d.downedBy = null;
        damagers.remove(d.id);
        dp.setHealth((float) Math.max(1.0, dp.getMaxHealth() * PGConfig.SERVER.brReviveHealthRatio.get()));
        String helperName = helper.getGameProfile().getName();
        title(dp, Component.literal("已被扶起").withStyle(ChatFormatting.GREEN),
            Component.literal(helperName + " 把你扶起來了").withStyle(ChatFormatting.WHITE));
        helper.displayClientMessage(Component.literal("已扶起 " + d.name).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, dp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        notifyTeam(d.id, Component.literal(helperName + " 扶起了 " + d.name).withStyle(ChatFormatting.GREEN), null);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 死亡
    // ═════════════════════════════════════════════════════════════════════════

    private void eliminate(ServerPlayer victim, Participant part, @Nullable DamageSource source, Cause cause) {
        boolean competitive = variant == Variant.COMPETITIVE;
        PGConfig c = PGConfig.SERVER;
        PlayerDataManager data = PlayerDataManager.get();

        // 擊殺歸屬：直接傷害來源 → 最後攻擊者 → 把他打倒的人（流血、圈外致死時）
        ServerPlayer killer = source != null ? creditFrom(victim, source) : null;
        if (killer == null && part.downedBy != null && cause != Cause.LOGOUT) {
            ServerPlayer downer = player(part.downedBy);
            if (downer != null && isParticipant(downer.getUUID()) && !TeamManager.get().isSameTeam(downer, victim)) killer = downer;
        }

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

        Component deathMsg = switch (cause) {
            case DAMAGE -> source != null ? source.getLocalizedDeathMessage(victim) : Component.literal(part.name + " 死亡");
            case BLEED -> Component.literal(part.name + " 流血過多死亡");
            case TEAM_WIPE -> Component.literal(part.name + " 的隊友全數倒地，" + part.name + " 陣亡");
            case LOGOUT -> Component.literal(part.name + " 已離線，視為淘汰");
        };
        broadcast(deathMsg.copy().withStyle(ChatFormatting.GRAY));
        PGNetwork.sendToAll(new KillFeedPacket(
            killer != null ? killer.getGameProfile().getName() : "", killer != null ? colorOf(killer.getUUID()) : 0xFFFFFF,
            part.name, colorOf(victim.getUUID())));

        // 屍體位置：掉進虛空時用最後站穩的位置
        Vec3 corpse = safePos(victim, part);
        clearDowned(victim, part);
        dropHeldCards(victim, part, corpse);

        // 掉落全部物品，然後變成觀察者
        victim.getInventory().dropAll();
        discardCarrier(part);
        victim.stopRiding();
        BRMovement.remove(victim);
        healFull(victim);
        victim.setGameMode(GameType.SPECTATOR);

        part.alive = false;
        part.state = PlayerState.DEAD;
        part.downedBy = null;
        part.altarTicks = 0;
        part.altarTarget = null;

        // 復活牌：孤狼、離線、競技模式已用過祭壇的人沒有；隊友全滅時也沒有（沒人能撿）
        boolean solo = TeamManager.get().getTeamOrSolo(part.id) == TeamManager.Team.SOLO;
        boolean teammateAlive = false;
        for (Participant other : participants.values()) {
            if (other != part && other.alive && TeamManager.get().isSameTeam(other.id, part.id)) { teammateAlive = true; break; }
        }
        boolean hasCard = !solo && teammateAlive && cause != Cause.LOGOUT && !(competitive && part.usedAltar);
        part.finalDead = !hasCard;
        if (hasCard) spawnCard(part, corpse, c.brCardGroundSeconds.get() * 20);

        int teamsLeft = countAliveTeams();
        Component sub = hasCard
            ? Component.literal("隊友可在 " + mmss(c.brCardGroundSeconds.get()) + " 內撿起你的復活牌").withStyle(ChatFormatting.GRAY)
            : Component.literal("剩餘 " + teamsLeft + " 隊").withStyle(ChatFormatting.GRAY);
        title(victim, Component.literal("已淘汰").withStyle(ChatFormatting.RED), sub);
        if (competitive) attachToTeammate(victim);

        resolveTeams();
    }

    /** 確定死亡：不能再被復活。移除他的復活牌並通知同隊。 */
    private void finalizeDeath(Participant part, String reason) {
        if (part.alive || part.finalDead) return;
        part.finalDead = true;
        Card card = groundCards.remove(part.id);
        if (card != null) removeCard(card);
        for (Participant other : participants.values()) other.heldCards.remove(part.id);

        ServerPlayer v = player(part.id);
        if (v != null) {
            v.sendSystemMessage(Component.literal("你已確定死亡（" + reason + "），無法再被復活。").withStyle(ChatFormatting.RED));
        }
        notifyTeam(part.id, Component.literal("隊友 " + part.name + " 已確定死亡（" + reason + "）").withStyle(ChatFormatting.RED), part.id);
    }

    /**
     * 整隊檢查（每次有人倒地或死亡後呼叫）：
     *   全隊都倒地（沒有站著的人）→ 倒地者全部死亡；
     *   全隊都死了（沒人能撿牌也沒人能啟動祭壇）→ 全隊確定死亡。
     */
    private void resolveTeams() {
        if (resolving) return;
        resolving = true;
        try {
            boolean changed;
            do {
                changed = false;
                Map<String, List<Participant>> teams = new LinkedHashMap<>();
                for (Participant p : participants.values()) {
                    teams.computeIfAbsent(TeamManager.get().teamKey(p.id), k -> new ArrayList<>()).add(p);
                }
                for (List<Participant> members : teams.values()) {
                    boolean standing = members.stream().anyMatch(p -> p.alive && !p.downed);
                    if (!standing) {
                        for (Participant p : members) {
                            if (!p.alive || !p.downed) continue;
                            ServerPlayer sp = player(p.id);
                            if (sp != null) { eliminate(sp, p, null, Cause.TEAM_WIPE); changed = true; }
                        }
                    }
                    if (members.stream().noneMatch(p -> p.alive)) {
                        for (Participant p : members) {
                            if (!p.finalDead) finalizeDeath(p, "隊伍已全滅");
                        }
                    }
                }
            } while (changed);
        } finally {
            resolving = false;
        }
    }

    /** 參賽者離線：PREPARING 期間直接移出；遊戲中視為淘汰（物品掉在原地，沒有復活牌）。 */
    public void onLogout(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        if (part == null) return;
        if (phase == Phase.PREPARING) {
            participants.remove(p.getUUID());
            return;
        }
        if (inPlay()) {
            if (part.alive) eliminate(p, part, null, Cause.LOGOUT);
            else if (!part.finalDead) finalizeDeath(part, "已離線");
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
    // 倒地／復活牌／祭壇 每 tick
    // ═════════════════════════════════════════════════════════════════════════

    private void tickRevival() {
        for (Participant part : participants.values()) {
            part.progressKind = ClientBRState.PROGRESS_NONE;
            part.progress = 0;
            if (!part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p != null && p.onGround() && p.getY() >= level.getMinBuildHeight()) part.lastSafe = p.position();
        }
        tickDowned();
        tickCards();
        tickAltars();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 復活牌
    // ═════════════════════════════════════════════════════════════════════════

    /** 在 pos 生成 part 的復活牌。deadlineTicks < 0 = 再掉落的牌，沒有期限。 */
    private void spawnCard(Participant part, Vec3 pos, int deadlineTicks) {
        Card card = new Card(part.id, deadlineTicks >= 0 ? tickCount + deadlineTicks : -1, pos);
        card.entity = createCardEntity(part, pos);
        groundCards.put(part.id, card);
        updateTicket(card);
    }

    private ItemEntity createCardEntity(Participant part, Vec3 pos) {
        assert level != null;
        ItemStack stack = new ItemStack(PGBlocks.REVIVE_CARD.get());
        TeamManager.Team team = TeamManager.get().getTeamOrSolo(part.id);
        stack.setHoverName(Component.literal("復活牌：" + part.name).withStyle(team.color));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(CARD_TAG, part.id);
        tag.putLong("pg_game", gameId); // 讓不同局、不同時間的牌不會合併

        ItemEntity e = new ItemEntity(level, pos.x, pos.y + 0.2, pos.z, stack, 0, 0.15, 0);
        e.setInvulnerable(true);   // 不受火、岩漿、爆炸、仙人掌傷害（虛空另外處理）
        e.setGlowingTag(true);
        e.lifespan = Integer.MAX_VALUE; // 不自然消失；期限由本類別自己管理
        e.setPickUpDelay(20);
        level.addFreshEntity(e);
        // 加入死者的原版隊伍 → 發光顏色 = 隊伍顏色
        PlayerTeam vanillaTeam = level.getScoreboard().getPlayersTeam(part.name);
        if (vanillaTeam != null) level.getScoreboard().addPlayerToTeam(e.getStringUUID(), vanillaTeam);
        return e;
    }

    private void tickCards() {
        assert level != null;
        for (Card card : new ArrayList<>(groundCards.values())) {
            Participant victim = participants.get(card.victim);
            if (victim == null || victim.alive || victim.finalDead) { groundCards.remove(card.victim); removeCard(card); continue; }
            ItemEntity e = card.entity;

            boolean gone = e == null || e.isRemoved();
            if (!gone && e.getY() < level.getMinBuildHeight()) { e.discard(); gone = true; } // 掉進虛空
            if (gone) {
                if (card.deadline >= 0) {
                    groundCards.remove(card.victim);
                    removeCard(card);
                    finalizeDeath(victim, "復活牌消失");
                } else {
                    // 再掉落的牌要留到遊戲結束：送回上一個安全位置
                    card.entity = createCardEntity(victim, card.lastSafe);
                    updateTicket(card);
                }
                continue;
            }

            if (e.onGround()) card.lastSafe = e.position();
            updateTicket(card);
            if (tickCount % 10 == 0) {
                Integer rgb = TeamManager.get().getTeamOrSolo(card.victim).color.getColor();
                int c = rgb != null ? rgb : 0xFFFFFF;
                Vector3f color = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
                level.sendParticles(new DustParticleOptions(color, 1.2f), e.getX(), e.getY() + 0.4, e.getZ(), 3, 0.2, 0.25, 0.2, 0);
            }
            if (card.deadline >= 0 && tickCount >= card.deadline) {
                groundCards.remove(card.victim);
                removeCard(card);
                finalizeDeath(victim, "復活牌逾時無人撿起");
            }
        }
    }

    /**
     * 撿取事件（EntityItemPickupEvent）。回傳 true 代表取消原版撿取。
     *   復活牌：一律不進背包；同隊站著的人碰到就吸走，轉成身上的資料；敵隊吸不到。
     *   倒地中的玩家不能撿任何東西。
     */
    public boolean onItemPickup(ServerPlayer player, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.is(PGBlocks.REVIVE_CARD.get())) {
            CompoundTag tag = stack.getTag();
            UUID victimId = tag != null && tag.hasUUID(CARD_TAG) ? tag.getUUID(CARD_TAG) : null;
            Card card = victimId != null ? groundCards.get(victimId) : null;
            if (card == null || !inPlay()) {
                item.discard(); // 不屬於本局的殘留牌
                return true;
            }
            if (card.entity != item) {
                item.discard(); // 重新生成後殘留的舊掉落物（例如區塊重新載入），移除避免重複
                return true;
            }
            Participant holder = participants.get(player.getUUID());
            if (holder == null || !holder.alive || holder.downed || !TeamManager.get().isSameTeam(holder.id, victimId)) {
                return true;
            }
            groundCards.remove(victimId);
            removeCard(card);
            holder.heldCards.add(victimId);

            Participant victim = participants.get(victimId);
            String victimName = victim != null ? victim.name : "?";
            player.displayClientMessage(Component.literal("撿起 " + victimName + " 的復活牌，帶到祭壇可復活隊友")
                .withStyle(ChatFormatting.GOLD), true);
            player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            ServerPlayer vp = player(victimId);
            if (vp != null) {
                vp.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " 撿起了你的復活牌")
                    .withStyle(ChatFormatting.GREEN));
            }
            return true;
        }
        Participant part = participants.get(player.getUUID());
        return part != null && part.downed;
    }

    /** 持有者倒地或死亡：持有的牌掉回地上，每個死者一個掉落物，沒有期限。 */
    private void dropHeldCards(ServerPlayer holder, Participant part, Vec3 pos) {
        if (part.heldCards.isEmpty()) return;
        int dropped = 0;
        for (UUID victimId : new ArrayList<>(part.heldCards)) {
            Participant v = participants.get(victimId);
            if (v != null && !v.alive && !v.finalDead && !groundCards.containsKey(victimId)) {
                spawnCard(v, pos, -1);
                dropped++;
            }
        }
        part.heldCards.clear();
        if (dropped > 0) {
            notifyTeam(part.id, Component.literal(part.name + " 身上的 " + dropped + " 張復活牌掉在地上了")
                .withStyle(ChatFormatting.YELLOW), null);
        }
    }

    private void removeCard(Card card) {
        if (card.entity != null && !card.entity.isRemoved()) card.entity.discard();
        card.entity = null;
        if (card.ticket != null && level != null) {
            level.getChunkSource().removeRegionTicket(CARD_TICKET, card.ticket, 2, card.ticket);
            card.ticket = null;
        }
    }

    /** 讓復活牌所在區塊保持載入（附近沒人也不會卸載，才偵測得到逾時與消失）。 */
    private void updateTicket(Card card) {
        if (level == null || card.entity == null) return;
        ChunkPos cp = card.entity.chunkPosition();
        if (cp.equals(card.ticket)) return;
        if (card.ticket != null) level.getChunkSource().removeRegionTicket(CARD_TICKET, card.ticket, 2, card.ticket);
        level.getChunkSource().addRegionTicket(CARD_TICKET, cp, 2, cp);
        card.ticket = cp;
    }

    private void clearAllCards() {
        for (Card card : groundCards.values()) removeCard(card);
        groundCards.clear();
    }

    private boolean isAwaiting(UUID id) {
        Participant p = participants.get(id);
        return p != null && !p.alive && !p.finalDead;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 祭壇
    // ═════════════════════════════════════════════════════════════════════════

    /** 玩家對祭壇按右鍵（按住時每 4 tick 一次）。 */
    public void onAltarUse(ServerPlayer p, BlockPos lowerPos) {
        Participant part = participants.get(p.getUUID());
        if (part == null || !part.alive || part.downed || !inPlay()) return;
        part.lastAltarUse = tickCount;
        part.altarUsePos = lowerPos.immutable();
    }

    private void tickAltars() {
        assert level != null;
        PGConfig c = PGConfig.SERVER;
        int need = c.brAltarActivateSeconds.get() * 20;
        double range = c.brAltarRange.get();
        for (Participant part : participants.values()) {
            ServerPlayer p = part.alive ? player(part.id) : null;
            if (p == null || part.downed || part.state != PlayerState.GROUND) { resetAltar(part); continue; }

            boolean using = part.altarUsePos != null && tickCount - part.lastAltarUse <= 6;
            BlockPos target = using ? part.altarUsePos : p.isShiftKeyDown() ? nearestAltar(p, range) : null;
            if (target != null && !nearAltar(p, target, range)) target = null;
            AltarBlockEntity altar = target != null && level.getBlockEntity(target) instanceof AltarBlockEntity a ? a : null;
            if (altar == null) { resetAltar(part); continue; }

            boolean holding = part.heldCards.stream().anyMatch(this::isAwaiting);
            if (!holding) {
                if (using) p.displayClientMessage(Component.literal("你身上沒有隊友的復活牌").withStyle(ChatFormatting.GRAY), true);
                resetAltar(part);
                continue;
            }
            if (!altar.isReady()) {
                p.displayClientMessage(Component.literal("祭壇冷卻中，還要 " + altar.cooldownSecondsLeft() + " 秒")
                    .withStyle(ChatFormatting.GRAY), true);
                resetAltar(part);
                continue;
            }
            if (!target.equals(part.altarTarget)) { part.altarTarget = target; part.altarTicks = 0; }
            part.altarTicks++;
            part.progressKind = ClientBRState.PROGRESS_ALTAR;
            part.progress = part.altarTicks / (float) need;
            if (part.altarTicks >= need) {
                activateAltar(p, part, target, altar);
                resetAltar(part);
            }
        }
    }

    private static void resetAltar(Participant part) {
        part.altarTicks = 0;
        part.altarTarget = null;
    }

    @Nullable
    private BlockPos nearestAltar(ServerPlayer p, double range) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : AltarBlockEntity.LOADED) {
            if (!nearAltar(p, pos, range)) continue;
            double d = p.position().distanceToSqr(Vec3.atBottomCenterOf(pos));
            if (d < bestDist) { bestDist = d; best = pos; }
        }
        return best;
    }

    /** 水平距離在範圍內、高度差 3 格內（祭壇本身兩格高）。 */
    private static boolean nearAltar(ServerPlayer p, BlockPos pos, double range) {
        double dx = p.getX() - (pos.getX() + 0.5), dz = p.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= (range + 0.5) * (range + 0.5) && Math.abs(p.getY() - pos.getY()) <= 3;
    }

    /** 復活同隊所有「牌已被撿起」的死者（任何一位站著的隊友身上的牌都算）。 */
    private void activateAltar(ServerPlayer activator, Participant part, BlockPos pos, AltarBlockEntity altar) {
        assert level != null;
        String team = TeamManager.get().teamKey(part.id);
        List<Participant> revived = new ArrayList<>();
        for (Participant holder : participants.values()) {
            if (!holder.alive || holder.downed || !TeamManager.get().teamKey(holder.id).equals(team)) continue;
            for (UUID v : new ArrayList<>(holder.heldCards)) {
                Participant vp = participants.get(v);
                if (vp != null && isAwaiting(v) && player(v) != null) {
                    holder.heldCards.remove(v);
                    revived.add(vp);
                }
            }
        }
        if (revived.isEmpty()) return;

        altar.startCooldown(PGConfig.SERVER.brAltarCooldownSeconds.get() * 20);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1.2f);
        List<String> names = new ArrayList<>();
        for (Participant vp : revived) {
            ServerPlayer v = player(vp.id);
            if (v == null) continue;
            respawnAtAltar(v, vp, pos);
            names.add(vp.name);
        }
        notifyTeam(part.id, Component.literal(activator.getGameProfile().getName() + " 啟動了祭壇：" + String.join("、", names) + " 復活了！")
            .withStyle(ChatFormatting.GREEN), null);
        ProvingGroundsMod.LOGGER.info("Altar at {} revived {}", pos.toShortString(), names);
    }

    /** 祭壇復活：傳到祭壇上空、清空背包、給基礎裝備、重新跳傘。 */
    private void respawnAtAltar(ServerPlayer v, Participant vp, BlockPos altarPos) {
        assert level != null;
        vp.alive = true;
        vp.finalDead = false;
        vp.downed = false;
        vp.state = PlayerState.FREEFALL;
        vp.sneakReleased = false;
        vp.groundTicks = 0;
        resetAltar(vp);
        if (variant == Variant.COMPETITIVE) vp.usedAltar = true;
        damagers.remove(vp.id);

        if (v.getCamera() != v) v.setCamera(v);
        v.setGameMode(GameType.SURVIVAL);
        v.getInventory().clearContent();
        v.removeAllEffects();
        healFull(v);
        v.getFoodData().setSaturation(5f);
        RespawnKit.give(v);
        double y = Math.min(PGConfig.SERVER.brAltarRespawnHeight.get(), level.getMaxBuildHeight() - 2);
        v.teleportTo(level, altarPos.getX() + 0.5, y, altarPos.getZ() + 0.5, v.getYRot(), 0);
        v.fallDistance = 0;
        BRMovement.apply(v);
        title(v, Component.literal("已復活").withStyle(ChatFormatting.GREEN),
            Component.literal("按 Shift 開傘").withStyle(ChatFormatting.WHITE));
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

    /** 存活隊伍數（倒地也算存活）。 */
    private int countAliveTeams() {
        Set<String> teams = new HashSet<>();
        for (Participant part : participants.values()) {
            if (part.alive) teams.add(TeamManager.get().teamKey(part.id));
        }
        return teams.size();
    }

    private void checkWin() {
        if (!inPlay()) return;
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

        releaseAll();
        AirdropManager.cleanupAll(level);
        airdrops = null;
        GameManager.get().endGame();
        loot = new LootSpawner(level, map, LootSpawner.Mode.CLEAR);
        phase = Phase.ENDING;
        phaseTicks = ENDING_SECONDS * 20;
        ProvingGroundsMod.LOGGER.info("BR ended: winners={}", winnerNames);
    }

    /** 解除所有倒地、座椅、飛機載具，移除所有復活牌。 */
    private void releaseAll() {
        for (Participant part : participants.values()) {
            discardCarrier(part);
            clearDowned(player(part.id), part);
            part.heldCards.clear();
            part.progressKind = ClientBRState.PROGRESS_NONE; // 結算畫面不殘留讀條
            part.progress = 0;
            resetAltar(part);
        }
        clearAllCards();
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
        releaseAll();
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
        groundCards.clear();
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

    /** 還原成原版狀態：移除速度修改與倒地姿勢、清空背包、恢復遊戲模式、傳回世界出生點。 */
    public void restore(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(RESTORE_TAG);
        GameType mode = GameType.byId(tag.contains("mode") ? tag.getInt("mode") : GameType.SURVIVAL.getId());
        p.getPersistentData().remove(RESTORE_TAG);

        p.stopRiding();
        p.setForcedPose(null);
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
        List<UUID> crawling = new ArrayList<>();
        boolean crawlStyle = PGConfig.SERVER.brDownedStyle.get() == PGConfig.DownedStyle.CRAWL;
        for (Participant part : participants.values()) {
            if (!part.alive) continue;
            alivePlayers++;
            if (part.downed && crawlStyle) crawling.add(part.id);
        }
        short aliveTeams = (short) countAliveTeams();
        float jump = PGConfig.SERVER.brJumpHeight.get().floatValue();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Participant part = participants.get(p.getUUID());
            byte self = ClientBRState.SELF_NONE;
            byte progressKind = 0, timerKind = ClientBRState.TIMER_NONE, cards = 0;
            float progress = 0;
            int timer = 0;
            if (part != null && phase != Phase.PREPARING) {
                self = switch (part.state) {
                    case PLANE -> ClientBRState.SELF_PLANE;
                    case FREEFALL -> ClientBRState.SELF_FREEFALL;
                    case CHUTE -> ClientBRState.SELF_CHUTE;
                    case GROUND -> ClientBRState.SELF_ALIVE;
                    case DEAD -> ClientBRState.SELF_DEAD;
                };
                if (part.alive && part.downed) {
                    self = ClientBRState.SELF_DOWNED;
                    timerKind = ClientBRState.TIMER_BLEED;
                    timer = (part.bleedTicks + 19) / 20;
                } else if (!part.alive) {
                    Card card = groundCards.get(part.id);
                    if (part.finalDead) {
                        timerKind = ClientBRState.TIMER_FINAL;
                    } else if (card != null) {
                        timerKind = ClientBRState.TIMER_CARD_GROUND;
                        timer = card.deadline >= 0 ? Math.max(0, (card.deadline - tickCount + 19) / 20) : -1;
                    } else {
                        timerKind = ClientBRState.TIMER_CARD_HELD;
                    }
                }
                progressKind = part.progressKind;
                progress = part.progress;
                cards = (byte) Math.min(127, part.heldCards.stream().filter(this::isAwaiting).count());
            }
            boolean movement = part != null && part.alive && phase != Phase.PREPARING && phase != Phase.ENDING;
            PGNetwork.sendTo(p, new BRStatePacket(phaseId, seconds, shrinking, stage, total,
                cx, cz, r, nx, nz, nr, (short) alivePlayers, aliveTeams, self, movement ? jump : 0f,
                progressKind, progress, timerKind, (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, timer)),
                cards, crawling));
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

    /** 由傷害來源找出應得擊殺的敵方參賽者（直接來源 → 最後攻擊者）。 */
    @Nullable
    private ServerPlayer creditFrom(ServerPlayer victim, DamageSource source) {
        ServerPlayer killer = null;
        if (source.getEntity() instanceof ServerPlayer sp && sp != victim) killer = sp;
        else if (victim.getKillCredit() instanceof ServerPlayer kc && kc != victim) killer = kc;
        if (killer != null && (!isParticipant(killer.getUUID()) || TeamManager.get().isSameTeam(killer, victim))) killer = null;
        return killer;
    }

    /** 屍體／牌的位置：掉出世界時改用最後站穩的位置。 */
    private Vec3 safePos(ServerPlayer p, Participant part) {
        assert level != null;
        if (p.getY() < level.getMinBuildHeight() + 1 && part.lastSafe != null) return part.lastSafe;
        return p.position();
    }

    /** 傳訊息給同隊所有在線上的參賽者（except 可排除一人）。 */
    private void notifyTeam(UUID member, Component msg, @Nullable UUID except) {
        for (Participant other : participants.values()) {
            if (other.id.equals(except) || !TeamManager.get().isSameTeam(other.id, member)) continue;
            ServerPlayer p = player(other.id);
            if (p != null) p.sendSystemMessage(msg);
        }
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

    private static String mmss(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
