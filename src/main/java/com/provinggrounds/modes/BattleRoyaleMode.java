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
import com.provinggrounds.modes.br.SkydiveWings;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;
import com.provinggrounds.network.MapMarkersPacket;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 *   虛空／指令致死直接死亡）。倒地趴下緩速、不能做任何動作；倒地後每秒流血（bleed_per_second），血扣到 0 就死亡。
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
    // GLIDE = 鞘翅滑翔（剛跳出飛機），FREEFALL = 自由落體（收傘後，原版掉落），CHUTE = 開傘
    private enum PlayerState { PLANE, GLIDE, FREEFALL, CHUTE, GROUND, DEAD }

    private static boolean isDroppingState(PlayerState s) {
        return s == PlayerState.GLIDE || s == PlayerState.FREEFALL || s == PlayerState.CHUTE;
    }
    /** 死亡原因（決定死亡訊息、擊殺歸屬、是否掉復活牌）。 */
    private enum Cause { DAMAGE, BLEED, TEAM_WIPE, LOGOUT }

    private static final class Participant {
        final UUID id;
        final String name;
        PlayerState state = PlayerState.GROUND;
        boolean alive = true;
        int lastSkydiveToggle = -1000;   // 跳出飛機的 tick（跳出後 0.5 秒內按空白鍵不開傘）
        int boardTick = -1000;           // 登機的 tick（登機後 board_lock_seconds 秒內不能跳）
        int jumpGraceUntil = -1;         // 跳機後維持隱形與無敵到這個 tick（jump_invisible_seconds）
        int selfReviveTicks = -1;        // 急救針自救讀條進度（-1 = 沒有在自救）
        Vec3 selfRevivePos;              // 自救讀條時固定的位置（不能移動）
        boolean kitOnLanding = false;    // 祭壇復活：落地後才發基礎裝備
        int groundTicks = 0;
        int fallImmuneUntil = -1;        // 落地後短暫免摔落傷害緩衝（立即結束跳傘狀態仍不吃落地傷）
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
        int altarRevived;            // 被復活祭壇救回來幾次（戰績面板顯示，計畫書 6 B1）
        boolean offline;             // 掉線中，原地留著掉線分身（計畫書 7 D）
        final List<UUID> heldCards = new ArrayList<>(); // 身上持有的隊友復活牌（死者 UUID）

        // 祭壇
        @Nullable BlockPos altarTarget;
        int altarTicks;
        @Nullable BlockPos altarIntent;     // 按 F（或空手右鍵）後自動讀條的祭壇；中斷就清掉
        @Nullable UUID reviveIntent;        // 按 F 後要扶的隊友；中斷就清掉

        // 本場統計（B4）
        final com.provinggrounds.modes.br.MatchStats stats = new com.provinggrounds.modes.br.MatchStats();

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
    /** 目前的縮圈（沒有對局時是 null）。特殊物品用。 */
    @Nullable public Circle circle() { return circle; }
    /** 空投管理（沒有對局時是 null）。信號槍用。 */
    @Nullable public AirdropManager airdrops() { return airdrops; }
    /** 對局進行中（飛機或落地後）。 */
    public boolean isInPlay() { return inPlay(); }
    public boolean isRunning() { return phase != Phase.IDLE; }

    /** 方塊能不能被爆炸、子彈、火破壞：只有大逃殺進行中、而且在這場地圖圓圈外加 64 格內（BlockProtection 用）。 */
    public boolean allowsBlockDamage(net.minecraft.core.BlockPos pos) {
        MapRegistry.BRMap m = map;
        if (phase == Phase.IDLE || m == null) return false;
        double r = m.radius + 64, dx = pos.getX() + 0.5 - m.centerX, dz = pos.getZ() + 0.5 - m.centerZ;
        return dx * dx + dz * dz <= r * r;
    }

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

    /** 在飛機上。 */
    public boolean isOnPlane(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && p.state == PlayerState.PLANE;
    }

    /** 上飛機前的準備時間：參賽者不會受到傷害。 */
    public boolean isPreparingParticipant(UUID id) {
        return phase == Phase.PREPARING && participants.containsKey(id);
    }

    /** 受保護（隱形、無敵、不能攻擊）：在飛機上，或剛跳出飛機的 jump_invisible_seconds 秒內。 */
    public boolean isProtected(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && (p.state == PlayerState.PLANE || tickCount < p.jumpGraceUntil);
    }

    public boolean isAirborne(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && (p.state == PlayerState.PLANE || isDroppingState(p.state));
    }

    /** 自由落體或開傘中（取消摔落傷害用）。 */
    public boolean isDropping(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && isDroppingState(p.state);
    }

    /** 跳傘中或剛落地 12 tick 內（立即解除跳傘狀態的同時仍保底取消殘餘摔落速度傷害）。 */
    public boolean isFallImmune(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.alive && (isDroppingState(p.state) || tickCount <= p.fallImmuneUntil);
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
        if (com.provinggrounds.data.MarkerScanJob.isRunning()) return "舊箱子 marker 掃描進行中，請等它完成或 /pg loot markers cancel。";
        if (com.provinggrounds.data.MapRenderJob.isRunning()) return "地圖俯視圖產生中，請等它完成或 /pg map render cancel。";
        MapRegistry.BRMap m = MapRegistry.get(server).getMap(mapId);
        if (m == null) return "找不到地圖 #" + mapId + "，請先用 /pg map set 登記。";

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator() && !com.provinggrounds.core.LobbyManager.isSpectator(p)) players.add(p);
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
        spotted.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendMapImage(p); // 大地圖／小地圖俯視圖
        startTeamCount = countAliveTeams();

        AirdropManager.cleanupAll(level); // 清掉上一局（例如當機）殘留的空投
        com.provinggrounds.modes.br.Tombstones.cleanupAll(level); // 以及墓碑
        com.provinggrounds.special.SpecialWorld.cleanup(level.getServer()); // 以及特殊物品（地雷、假箱、分身、雪牆…）
        // 方塊還原：大廳被打破的玻璃、上一局沒來得及還原的方塊（例如中途當機）先修回去，
        // 再開始記錄這張地圖（圓圈外加 64 格）裡所有方塊的變化，比賽結束時還原
        int fixed = com.provinggrounds.data.BlockLedger.get(server).restoreAll(level);
        if (fixed > 0) broadcast(Component.literal("已還原上一局被破壞的方塊 " + fixed + " 個。").withStyle(ChatFormatting.GRAY));
        final double mcx = m.centerX, mcz = m.centerZ, mr = m.radius + 64;
        com.provinggrounds.data.BlockLedger.beginMatch(server, pos -> {
            double dx = pos.getX() + 0.5 - mcx, dz = pos.getZ() + 0.5 - mcz;
            return dx * dx + dz * dz <= mr * mr;
        }, (int) Math.floor(mcx - mr), (int) Math.floor(mcz - mr), (int) Math.ceil(mcx + mr), (int) Math.ceil(mcz + mr));
        airdrops = new AirdropManager(level);
        loot = new LootSpawner(level, m, LootSpawner.Mode.SPAWN);
        phase = Phase.PREPARING;
        phaseTicks = com.provinggrounds.core.LobbyManager.ALL_READY_SECONDS * 20; // 計畫書 7 A3：開局後 10 秒倒數就登機（沒有前置準備階段）

        broadcast(Component.literal("▸ 大逃殺").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
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
        com.provinggrounds.core.ProneManager.clear(p);
        com.provinggrounds.special.KnockbackShield.restore(p); // 大廳測試時舉著的擊退盾先還原
        com.provinggrounds.venue.VenueManager.leave(p); // 離開場地系統（拿掉場地的血量加成）
        com.provinggrounds.special.Totem.remove(p);
        markForRestore(p);
        participants.put(p.getUUID(), new Participant(p));
        GameManager.get().addPlayer(p);
        p.setGameMode(GameType.ADVENTURE); // 冒險模式：不能破壞／放置地圖方塊（同舊 Datapack），仍可開箱、用祭壇、開槍
        com.provinggrounds.modes.br.HotbarRules.clearKeepMelee(p); // 清空物品，但保留近戰武器（放第 3 格）
        com.provinggrounds.modes.br.Backpack.clear(p); com.provinggrounds.special.SpecialSlot.clear(p);
        p.removeAllEffects();
        applyMaxHealth(p);
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
            case PLANE -> { tickPlane(); tickDropping(); tickRevival(); tickFood(); checkWin(); }
            case ACTIVE -> { tickFood(); tickDropping(); tickCircle(); tickRevival(); tickSpectators(); tickMeleeSpeed(); tickDeadHealth(); tickOffline(); checkWin(); }
            case ENDING -> tickEnding();
            default -> {}
        }

        if (phase != Phase.IDLE && tickCount % 5 == 0) syncClients(server);
        if (phase != Phase.IDLE && tickCount % 20 == 3) syncBoard(server); // 戰績面板（計畫書 6 B1）
        if ((phase == Phase.PLANE || phase == Phase.ACTIVE) && tickCount % 5 == 2) syncMarkers(server); // 地圖標記
    }

    /** 拿近戰武器時速度改成 melee_speed_multiplier（BRMovement.refresh 只有數值變了才重新套用）。 */
    private void tickMeleeSpeed() {
        for (Participant part : participants.values()) {
            if (!part.alive || part.downed || part.state != PlayerState.GROUND) continue;
            ServerPlayer p = player(part.id);
            if (p != null) BRMovement.refresh(p);
        }
    }

    private void tickPreparing() {
        if (loot != null) loot.tick();
        if (phaseTicks > 0) {
            phaseTicks--;
            int sec = phaseTicks / 20;
            if (phaseTicks % 20 == 0 && sec >= 1) {
                if (sec <= 10) {
                    // 最後 10 秒：每秒在畫面中央顯示數字＋音效，最後 3 秒音調變高
                    for (ServerPlayer p : participantPlayers()) {
                        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 4));
                        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("即將登機").withStyle(ChatFormatting.WHITE)));
                        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.valueOf(sec))
                            .withStyle(sec <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD)));
                    }
                    playSoundAll(SoundEvents.NOTE_BLOCK_HAT.value(), sec <= 3 ? 1.6f : 1.0f);
                } else if (sec % 10 == 0) {
                    broadcast(Component.literal("登機倒數 " + sec + " 秒").withStyle(ChatFormatting.YELLOW));
                    playSoundAll(SoundEvents.NOTE_BLOCK_HAT.value(), 1f);
                }
            }
            if (phaseTicks == 0 && (loot == null || loot.isDone())) {
                playSoundAll(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f); // 倒數結束：登機鈴聲
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
            com.provinggrounds.modes.br.HotbarRules.clearKeepMelee(p); // 登機時再清一次，只留近戰武器（第 3 格）
            p.teleportTo(level, planeStart.x, planeStart.y, planeStart.z, yaw, 0);
            Display.ItemDisplay carrier = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            carrier.moveTo(planeStart.x, planeStart.y, planeStart.z, yaw, 0);
            level.addFreshEntity(carrier);
            p.startRiding(carrier, true);
            part.carrier = carrier;
            part.state = PlayerState.PLANE;
            part.boardTick = tickCount;
            healFull(p); // 上飛機自動回滿血（100）
            part.jumpGraceUntil = -1;
            // 飛機上大家擠在一起看不到畫面：全員隱形（沒有粒子、沒有圖示）。同隊因為隊伍設定 seeFriendlyInvisibles 會看到半透明；
            // 飛機上也不會受傷、不能攻擊（BREvents、GunCompat）。跳出飛機時解除。
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            part.stats.startAlive(tickCount); // 存活時間從飛機起飛算起
            BRMovement.apply(p);
            // 操作提示只由客戶端 HUD（快捷欄上方）顯示，這裡只給標題，避免同一句話出現兩次
            title(p, Component.literal("已登機").withStyle(ChatFormatting.GOLD), Component.empty());
        }
        matchStartTick = tickCount;
        phase = Phase.PLANE;
        broadcast(Component.literal("飛機起飛！任何時候按空白鍵跳出，飛到尾端會自動跳出。").withStyle(ChatFormatting.AQUA));
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
                jump(p, part);
            } else {
                anyoneOnPlane = true;
            }
        }
        if (end || !anyoneOnPlane) beginActive();
    }

    private void jump(ServerPlayer p, Participant part) {
        part.state = PlayerState.GLIDE; // 先改狀態再下機：飛機座位在 PLANE 狀態時會擋住下機
        // 跳出後 jump_invisible_seconds 秒內維持隱形與無敵，時間到由 tickJumpGrace 解除
        part.jumpGraceUntil = tickCount + PGConfig.SERVER.jumpInvisibleSeconds.get() * 20;
        if (part.jumpGraceUntil <= tickCount) p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        discardCarrier(part);
        if (p.getVehicle() != null) p.stopRiding();
        part.groundTicks = 0;
        part.fallImmuneUntil = -1;
        part.lastSkydiveToggle = tickCount;
        p.fallDistance = 0;
        SkydiveWings.equip(p); // 穿上鞘翅，直接進入原版滑翔（速度、姿勢都是原版）
        p.startFallFlying();
    }

    /**
     * 玩家按下空白鍵（客戶端偵測後送 SkydiveKeyPacket）。所有判斷在伺服器：
     *   飛機上 → 跳出（鞘翅滑翔）；鞘翅滑翔 → 開傘；開傘 → 自由落體；自由落體 → 開傘（開傘與自由落體可以反覆切換）。
     *   切換之間有 0.5 秒冷卻（剛跳出飛機時空白鍵可能還按著，也不會一跳就開傘）。開傘之後就回不到鞘翅。
     */
    public void onSkydiveKey(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        if (part == null || !part.alive || part.downed || !inPlay()) return;
        if (part.state == PlayerState.PLANE) {
            // 登機後 board_lock_seconds 秒內按空白鍵沒反應（避免誤觸），HUD 會顯示倒數
            if (tickCount - part.boardTick < PGConfig.SERVER.boardLockSeconds.get() * 20) return;
            jump(p, part);
            syncChutes();
            if (level != null) syncClients(level.getServer());
            return;
        }
        if (tickCount - part.lastSkydiveToggle < 10) return;
        boolean changed = false;
        switch (part.state) {
            case GLIDE, FREEFALL -> { openChute(p, part, false); changed = true; }
            case CHUTE -> {
                // 收傘 → 自由落體（原版掉落，不改任何數值）
                part.state = PlayerState.FREEFALL;
                part.lastSkydiveToggle = tickCount;
                playChuteSound(p, false);
                changed = true;
            }
            default -> {}
        }
        if (changed) {
            syncChutes();
            if (level != null) syncClients(level.getServer());
        }
    }

    /**
     * 打開降落傘（按空白鍵，或鞘翅滑翔中離地 auto_chute_height 格時自動）。
     * 鞘翅在第一次開傘時拿掉，原版就不會再進入滑翔。開傘後的下降速度與方向由客戶端 ClientSkydive 控制。
     */
    private void openChute(ServerPlayer p, Participant part, boolean auto) {
        if (part.state != PlayerState.GLIDE && part.state != PlayerState.FREEFALL) return;
        part.state = PlayerState.CHUTE;
        part.lastSkydiveToggle = tickCount;
        p.stopFallFlying();
        SkydiveWings.removeAll(p);
        p.fallDistance = 0;
        playChuteSound(p, true);
    }

    /**
     * 開傘／收傘音效（原版音效疊在一起，附近的人也聽得到）：
     *   開傘：大翅膀拍動（傘衣「啪」一聲展開）＋布料＋裝備聲；收傘：短促的拍動＋布料收起＋皮甲聲。
     */
    private void playChuteSound(ServerPlayer p, boolean open) {
        double x = p.getX(), y = p.getY() + 1.5, z = p.getZ();
        if (open) {
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7f, 1.5f);
            level.playSound(null, x, y, z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.7f);
            level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_ELYTRA, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else {
            level.playSound(null, x, y, z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f);
            level.playSound(null, x, y, z, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
            level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 1.3f);
        }
    }


    /** 腳下到地面（任何有碰撞的方塊，含樹葉）或水面的距離；超過 maxDist 回傳 maxDist。 */
    private static double heightAboveGround(ServerPlayer p, double maxDist) {
        Vec3 from = p.position();
        net.minecraft.world.phys.BlockHitResult hit = p.level().clip(new net.minecraft.world.level.ClipContext(from, from.subtract(0, maxDist, 0),
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? maxDist : from.y - hit.getLocation().y;
    }

    /**
     * 是否已經落地。不只看玩家回報的 onGround（落在樹葉等方塊上時不可靠），
     * 伺服器另外檢查腳下 0.1 格內有沒有方塊、或是否在水中。
     */
    private static boolean landed(ServerPlayer p) {
        if (p.onGround() || p.isInWater() || p.getAbilities().flying) return true;
        return !p.level().noCollision(p, p.getBoundingBox().move(0, -0.1, 0));
    }

    /** 這個載具是不是這位玩家的飛機座位（按 Shift 不能下飛機，要按空白鍵跳）。 */
    public boolean isPlaneSeat(ServerPlayer p, Entity vehicle) {
        Participant part = participants.get(p.getUUID());
        return part != null && part.state == PlayerState.PLANE && part.carrier != null && part.carrier == vehicle;
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

    /** 淘汰的參賽者保持滿血（觀戰中、等復活時都是 100）。 */
    private void tickDeadHealth() {
        if (tickCount % 10 != 0) return;
        for (Participant part : participants.values()) {
            if (part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p != null && p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
    }

    /** 跳機後的隱形、無敵時間到了就解除。 */
    private void tickJumpGrace() {
        for (Participant part : participants.values()) {
            if (part.jumpGraceUntil < 0 || tickCount < part.jumpGraceUntil) continue;
            part.jumpGraceUntil = -1;
            ServerPlayer p = player(part.id);
            if (p != null) p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
    }

    /** 上一次送出的「開傘中」實體編號（有變化才送 ChuteSyncPacket；計畫書 6 D4）。 */
    private final Set<Integer> chuteSent = new HashSet<>();

    private void syncChutes() {
        if (level == null) return;
        Set<Integer> now = new HashSet<>();
        for (Participant part : participants.values()) {
            if (!part.alive || part.state != PlayerState.CHUTE) continue;
            ServerPlayer p = player(part.id);
            if (p != null) now.add(p.getId());
        }
        if (now.equals(chuteSent)) return;
        chuteSent.clear();
        chuteSent.addAll(now);
        PGNetwork.sendToAll(new com.provinggrounds.network.ChuteSyncPacket(now.stream().mapToInt(Integer::intValue).toArray()));
    }

    private void tickDropping() {
        tickJumpGrace();
        boolean stateChanged = false;
        for (Participant part : participants.values()) {
            if (!part.alive || !isDroppingState(part.state)) continue;
            ServerPlayer p = player(part.id);
            if (p == null) continue;

            p.fallDistance = 0;
            com.provinggrounds.item.ParachuteItem.removeAll(p); // 計畫書 6 D4：不再有降落傘物品（舊版留下的一律收掉）
            if (landed(p) || (p.isPassenger() && part.seat == null)) {
                // 腳一落地立刻結束跳傘狀態（不再延遲 5 tick），同時保留 12 tick 摔落傷害免疫緩衝
                part.groundTicks = 0;
                part.fallImmuneUntil = tickCount + 12;
                part.state = PlayerState.GROUND;
                p.fallDistance = 0;
                p.stopFallFlying();
                SkydiveWings.removeAll(p); // 落地：移除鞘翅（沒開傘就落地時也會清掉）
                com.provinggrounds.item.ParachuteItem.removeAll(p); // 落地：收回降落傘
                if (part.kitOnLanding) { part.kitOnLanding = false; RespawnKit.give(p); } // 祭壇復活：落地才發裝備
                stateChanged = true;
                continue;
            }
            part.groundTicks = 0;
            if (part.state == PlayerState.GLIDE) {
                // 鞘翅滑翔中：離地夠近就自動開傘；萬一滑翔被中斷（例如撞到東西）就重新開始滑翔
                double auto = PGConfig.SERVER.skydiveAutoChuteHeight.get();
                if (heightAboveGround(p, auto + 1) <= auto) {
                    openChute(p, part, true);
                    stateChanged = true;
                } else if (!p.isFallFlying()) {
                    p.startFallFlying();
                }
            }
        }
        syncChutes();
        if (stateChanged && level != null) {
            syncClients(level.getServer());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 縮圈
    // ═════════════════════════════════════════════════════════════════════════

    private void beginActive() {
        assert map != null && level != null;
        for (Participant part : participants.values()) discardCarrier(part);
        double maxSpeed = PGConfig.SERVER.brSprintSpeed() * preset.speedFactor(); // 每種模式各自的圈速度上限
        circle = new Circle(map.centerX, map.centerZ, map.radius, preset.ratios(), preset.intervalSeconds(), preset.finalHoldSeconds(),
            maxSpeed, level.getRandom());
        circle.addInitialWait(preset.firstWaitSeconds() * 20); // 開場第一次縮圈多等（每種模式各自設定）
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
                    broadcast(Component.literal("圈已縮到 0。").withStyle(ChatFormatting.RED));
                } else if (circle.inFinal()) {
                    broadcast(Component.literal(String.format("決賽圈：%d 秒後縮到 0！", circle.secondsLeft())).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
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
        if (!circle.started()) return; // 第一次縮圈開始前沒有圈外傷害
        for (Participant part : new ArrayList<>(participants.values())) {
            if (!part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p == null || !circle.isOutside(p.getX(), p.getZ())) continue;
            if (p.hasEffect(com.provinggrounds.special.SpecialRegistry.ZONE_SHELTER.get())) continue; // 圈外庇護
            p.hurt(p.damageSources().outOfBorder(), damage);
            // 原版受傷後會把伺服器的移動速度強制送回客戶端（hurtMarked），每秒扣血一次就像被推一下：圈外傷害不送
            p.hurtMarked = false;
        }
        // 掉線分身照一般規則受圈外傷害（計畫書 7 D）
        for (Participant part : new ArrayList<>(participants.values())) {
            if (!part.alive || !part.offline || level == null) continue;
            var body = com.provinggrounds.core.DisconnectBodies.bodyOf(level.getServer(), part.id);
            if (body != null && circle.isOutside(body.getX(), body.getZ())) body.hurt(body.damageSources().outOfBorder(), damage);
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
        if (vp != null && vp.selfReviveTicks >= 0) { // 受擊就取消自救（不消耗急救針）
            vp.selfReviveTicks = -1;
            vp.progressKind = ClientBRState.PROGRESS_NONE;
            victim.displayClientMessage(Component.literal("自救被打斷了（急救針沒有消耗）").withStyle(ChatFormatting.RED), true);
        }
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        if (!isAliveParticipant(victim.getUUID()) || !isParticipant(attacker.getUUID())) return;
        if (TeamManager.get().isSameTeam(attacker, victim)) return;
        damagers.computeIfAbsent(victim.getUUID(), k -> new HashSet<>()).add(attacker.getUUID());
        Participant ap = participants.get(attacker.getUUID());
        if (ap != null) ap.stats.damage += Math.min(amount, victim.getHealth()); // 本場傷害：只算實際扣掉的血
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
        if (part.state != PlayerState.GROUND) return false; // 空中（飛機、自由落體、開傘）沒有倒地
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
        com.provinggrounds.special.KnockbackShield.restore(victim); // 擊退盾：先還回原本那格的物品
        PGConfig c = PGConfig.SERVER;
        ServerPlayer attacker = creditFrom(victim, source);

        part.downed = true;
        part.bleedTicks = 20;
        part.stats.downs++;
        part.reviveTicks = 0;
        part.reviver = null;
        part.downedBy = attacker != null ? attacker.getUUID() : null;
        part.lockedSlot = victim.getInventory().selected;
        part.altarTicks = 0;
        part.altarTarget = null;

        // 強心劑效果中倒地（計畫書 7 C10）：倒地後前 5 秒（不超過強心劑剩下的時間）可以使用任何物品
        var stim = victim.getEffect(com.provinggrounds.special.SpecialRegistry.ADRENALINE.get());
        if (stim != null) {
            int free = Math.min(stim.getDuration(), com.provinggrounds.special.SpecialConfig.ticks(com.provinggrounds.special.SpecialConfig.get().stimDownedFreeSeconds));
            if (free > 0) com.provinggrounds.special.Engines.effect(victim, com.provinggrounds.special.SpecialRegistry.STIM_FREE.get(), free, 0);
        }
        com.provinggrounds.core.DownedCore.applyDowned(victim, c.brDownedHealth.get().floatValue()); // 共用的倒地效果（計畫書 7 E）
        if (c.brDownedStyle.get() == PGConfig.DownedStyle.SIT) {
            sitDown(victim, part);
        } else {
            victim.setForcedPose(Pose.SWIMMING);
        }

        // 倒地時身上的隊友復活牌掉回地上
        dropHeldCards(victim, part, safePos(victim, part));

        // 擊倒訊息只顯示在右上角擊殺資訊，不發到聊天室
        PGNetwork.sendToAll(new KillFeedPacket(
            attacker != null ? attacker.getGameProfile().getName() : "", attacker != null ? colorOf(attacker.getUUID()) : 0xFFFFFF,
            part.name, colorOf(victim.getUUID()), true, com.provinggrounds.core.KillEvents.flagsFor(attacker, victim, source)));
        title(victim, Component.literal("你倒地了").withStyle(ChatFormatting.RED),
            Component.literal("等待隊友扶起（" + bleedSecondsLeft(victim) + " 秒）").withStyle(ChatFormatting.GRAY));
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
        part.selfReviveTicks = -1;
        if (!part.downed && part.seat == null) return;
        part.downed = false;
        part.reviveTicks = 0;
        part.reviver = null;
        Entity seat = part.seat;
        part.seat = null;
        if (p != null) {
            com.provinggrounds.core.DownedCore.clearDowned(p);
            if (seat != null && p.getVehicle() == seat) p.stopRiding();
        }
        if (seat != null) seat.discard();
    }

    /**
     * BR 不用飢餓度：每 tick 把飽食度固定住。
     * 關閉自然回血時固定在 17（低於 18 就不會自然回血，高於 6 仍可衝刺），開啟時固定在 20。
     */
    private void tickFood() {
        int food = PGConfig.SERVER.brNaturalRegen.get() ? 20 : 17;
        for (Participant part : participants.values()) {
            if (!part.alive) continue;
            ServerPlayer p = player(part.id);
            if (p == null) continue;
            p.getFoodData().setFoodLevel(food);
            p.getFoodData().setSaturation(0f);
        }
    }

    private void tickDowned() {
        PGConfig c = PGConfig.SERVER;
        int reviveTotal = c.brReviveSeconds.get() * 20;
        Set<UUID> busy = new HashSet<>();
        for (Participant d : new ArrayList<>(participants.values())) {
            if (!d.alive || !d.downed) continue;
            ServerPlayer dp = player(d.id);
            if (dp == null) continue;

            // 倒地可以切換快捷欄，但手上的東西不能用（ActionLock；計畫書 4 的 C 章）
            // 坐姿：確保還坐著
            if (d.seat != null && d.seat.isAlive() && dp.getVehicle() != d.seat) dp.startRiding(d.seat, true);

            if (d.selfReviveTicks >= 0) { tickSelfRevive(dp, d); continue; } // 急救針自救中
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
                // 流血：每秒扣血，扣到 0 就死亡
                if (--d.bleedTicks <= 0) {
                    d.bleedTicks = 20;
                    float hp = dp.getHealth() - c.brBleedPerSecond.get().floatValue();
                    if (hp <= 0) eliminate(dp, d, null, Cause.BLEED);
                    else dp.setHealth(hp);
                }
            }
        }
    }

    // ── 急救針（計畫書 4 的 28 號）──────────────────────────────────────────

    public boolean isSelfReviving(UUID id) {
        Participant p = participants.get(id);
        return p != null && p.selfReviveTicks >= 0;
    }

    /** 自己倒地時使用急救針：開始 self_revive_seconds 秒讀條（不能移動、受擊取消），完成後自救並回滿血。 */
    public boolean startSelfRevive(ServerPlayer p) {
        Participant d = participants.get(p.getUUID());
        if (d == null || !d.alive || !d.downed || d.selfReviveTicks >= 0) return false;
        d.selfReviveTicks = 0;
        d.selfRevivePos = p.position();
        p.displayClientMessage(Component.literal("自救中…受到攻擊會被打斷").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private void tickSelfRevive(ServerPlayer dp, Participant d) {
        int total = Math.max(1, com.provinggrounds.special.SpecialConfig.ticks(com.provinggrounds.special.SpecialConfig.get().selfReviveSeconds));
        d.selfReviveTicks++;
        d.progressKind = ClientBRState.PROGRESS_BEING_REVIVED;
        d.progress = d.selfReviveTicks / (float) total;
        if (d.selfRevivePos != null && dp.position().distanceToSqr(d.selfRevivePos) > 0.01) { // 讀條中不能移動
            dp.teleportTo(d.selfRevivePos.x, d.selfRevivePos.y, d.selfRevivePos.z);
        }
        if (d.selfReviveTicks < total) return;
        // 完成：消耗急救針（還在特殊物品欄裡才算）
        if (com.provinggrounds.special.SpecialItem.typeOf(com.provinggrounds.special.SpecialSlot.get(dp)) == com.provinggrounds.special.SpecialType.FIRST_AID_SHOT) {
            com.provinggrounds.special.SpecialSlot.clear(dp);
        }
        clearDowned(dp, d);
        d.downedBy = null;
        d.progressKind = ClientBRState.PROGRESS_NONE;
        damagers.remove(d.id);
        dp.setHealth(dp.getMaxHealth());
        title(dp, Component.literal("自救成功").withStyle(ChatFormatting.GREEN), Component.empty());
        level.playSound(null, dp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        notifyTeam(d.id, Component.literal(d.name + " 用急救針自救了").withStyle(ChatFormatting.GREEN), null);
    }

    /** 正在扶隊友（讀條中）時使用急救針：立刻扶起並回滿血。 */
    public boolean instantRevive(ServerPlayer helper) {
        for (Participant d : participants.values()) {
            if (!d.alive || !d.downed || !helper.getUUID().equals(d.reviver) || d.reviveTicks <= 0) continue;
            ServerPlayer dp = player(d.id);
            if (dp == null) continue;
            revive(dp, d, helper);
            d.progressKind = ClientBRState.PROGRESS_NONE;
            Participant hp = participants.get(helper.getUUID());
            if (hp != null) hp.progressKind = ClientBRState.PROGRESS_NONE;
            dp.setHealth(dp.getMaxHealth());
            return true;
        }
        return false;
    }

    /** 倒地剩下的秒數（目前血量 ÷ 每秒流血量）。 */
    private static int bleedSecondsLeft(ServerPlayer p) {
        double perSec = PGConfig.SERVER.brBleedPerSecond.get();
        return (int) Math.ceil(p.getHealth() / perSec);
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
            && TeamManager.get().isSameTeam(h.id, d.id) && d.id.equals(h.reviveIntent) && !hp.isSpectator()
            && inReviveRange(h, hp, dp, range) && aimingAt(h, hp, dp, range);
    }

    /** 準心離開倒地隊友就中斷（受傷、移動不會中斷，只要準心還對著；計畫書 6 A19）。 */
    private boolean aimingAt(Participant h, ServerPlayer hp, ServerPlayer dp, double range) {
        if (aimsAtEntity(hp, dp, range)) return true;
        h.reviveIntent = null;
        hp.displayClientMessage(Component.literal("準心離開隊友，扶起中斷").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** 準心是否對著這個實體：視線射線碰到它的碰撞箱（稍微放大，倒地的人比較好瞄），中間沒有方塊擋住。 */
    private boolean aimsAtEntity(ServerPlayer p, Entity target, double range) {
        return com.provinggrounds.core.DownedCore.aimsAtEntity(p, target, range); // 共用（計畫書 7 E）
    }

    /** 準心指著的祭壇（下半部位置）；沒有指著祭壇回傳 null。 */
    @Nullable
    private BlockPos aimedAltar(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range + 1.5));
        net.minecraft.world.phys.BlockHitResult bh = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        BlockPos hit = bh.getBlockPos();
        if (level.getBlockEntity(hit) instanceof AltarBlockEntity) return hit;
        if (level.getBlockEntity(hit.below()) instanceof AltarBlockEntity) return hit.below();
        return null;
    }

    /** 超出扶起範圍就中斷（要重新按 F）。 */
    private boolean inReviveRange(Participant h, ServerPlayer hp, ServerPlayer dp, double range) {
        if (hp.distanceTo(dp) <= range) return true;
        h.reviveIntent = null;
        hp.displayClientMessage(Component.literal("離太遠，扶起中斷").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    // ── F 互動鍵（計畫書 5 的 B2）：按一下自動讀條，超出範圍、使用物品、受傷就中斷 ──

    /** 按 F：準星指著倒地隊友就扶他；allowNearest 時沒指著也找範圍內最近的倒地隊友。成功開始回傳 true。 */
    public boolean startReviveByKey(ServerPlayer helper, @Nullable Entity looked, boolean allowNearest) {
        Participant h = participants.get(helper.getUUID());
        if (h == null || !h.alive || h.downed || !inPlay()) return false;
        double range = PGConfig.SERVER.brReviveRange.get();
        Participant target = null;
        if (looked instanceof ServerPlayer lp) {
            Participant d = participants.get(lp.getUUID());
            if (d != null && d.alive && d.downed && TeamManager.get().isSameTeam(h.id, d.id) && helper.distanceTo(lp) <= range) target = d;
        }
        // 計畫書 6 A19：一定要準心對著倒地隊友按 F 才開始（不再自動找最近的）
        if (target == null) return false;
        h.reviveIntent = target.id;
        h.altarIntent = null;
        return true;
    }

    /** 按 F：在祭壇範圍內就開始讀條。 */
    public boolean startAltarByKey(ServerPlayer p) {
        if (level == null) return false;
        double range = PGConfig.SERVER.brAltarRange.get();
        BlockPos pos = aimedAltar(p, range); // 計畫書 6 A19：準心對著祭壇按 F 才開始
        if (pos == null || !nearAltar(p, pos, range)) return false;
        onAltarUse(p, pos);
        return true;
    }

    /** 使用物品、開槍、受傷：中斷扶人與祭壇讀條。 */
    public void interruptProgress(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        if (part == null || (part.reviveIntent == null && part.altarIntent == null)) return;
        part.reviveIntent = null;
        part.altarIntent = null;
        resetAltar(part);
        p.displayClientMessage(Component.literal("讀條中斷，再按 F 重新開始").withStyle(ChatFormatting.GRAY), true);
    }

    private void revive(ServerPlayer dp, Participant d, ServerPlayer helper) {
        clearDowned(dp, d);
        d.downedBy = null;
        damagers.remove(d.id);
        dp.setHealth((float) Math.min(PGConfig.SERVER.brReviveHealth.get(), dp.getMaxHealth()));
        Participant hp = participants.get(helper.getUUID());
        if (hp != null) { hp.stats.revives++; hp.reviveIntent = null; }
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
        com.provinggrounds.special.KnockbackShield.restore(victim); // 擊退盾：先還回原本那格的物品，再一起進墓碑
        com.provinggrounds.special.Totem.remove(victim);
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
        part.stats.deaths++;
        part.stats.stopAlive(tickCount);
        part.stats.lastDeathTick = tickCount;
        if (killer != null && !TeamManager.get().isSameTeam(killer, victim)) {
            data.addKill(killer);
            int xp = data.addXp(killer, c.xpKill.get(), competitive);
            Participant kp = participants.get(killer.getUUID());
            if (kp != null) { kp.stats.kills++; kp.stats.xp += xp; }
            killer.displayClientMessage(Component.literal("擊殺 " + part.name + "  +" + xp + " 經驗")
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
                Participant asp = participants.get(id);
                if (asp != null) { asp.stats.assists++; asp.stats.xp += xp; }
                assister.displayClientMessage(Component.literal("助攻 " + part.name + "  +" + xp + " 經驗")
                    .withStyle(ChatFormatting.YELLOW), true);
                com.provinggrounds.core.KillEvents.assist(assister, part.name);
            }
        }

        // 淘汰訊息只顯示在右上角擊殺資訊，不發到聊天室；伺服器 log 仍保留一行方便查詢
        ProvingGroundsMod.LOGGER.info("BR eliminated: {} by {} ({})", part.name,
            killer != null ? killer.getGameProfile().getName() : "-", cause);
        PGNetwork.sendToAll(new KillFeedPacket(
            killer != null ? killer.getGameProfile().getName() : "", killer != null ? colorOf(killer.getUUID()) : 0xFFFFFF,
            part.name, colorOf(victim.getUUID()), false, com.provinggrounds.core.KillEvents.flagsFor(killer, victim, source)));
        if (killer != null && !TeamManager.get().isSameTeam(killer, victim)) { // 擊殺圖示與音效（計畫書 5 的 E3）
            Participant kpart = participants.get(killer.getUUID());
            com.provinggrounds.core.KillEvents.brKill(killer, victim, source, kpart != null ? kpart.stats.kills : 1);
        }

        // 屍體位置：掉進虛空時用最後站穩的位置

        Vec3 corpse = safePos(victim, part);
        clearDowned(victim, part);
        dropHeldCards(victim, part, corpse);

        // 不噴裝：身上所有物品（含背包欄）收進屍體位置的墓碑，然後變成觀察者
        SkydiveWings.removeAll(victim);
        com.provinggrounds.item.ParachuteItem.removeAll(victim);
        com.provinggrounds.modes.br.Tombstones.place(victim, corpse);
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

        // 復活牌：孤狼、離線、競技模式已用過祭壇的人沒有；
        // 沒有「站著」的隊友時也沒有（倒地的隊友不算，他們接著會在 resolveTeams 裡一起死亡）
        boolean solo = TeamManager.get().getTeamOrSolo(part.id) == TeamManager.Team.SOLO;
        boolean teammateAlive = false;
        for (Participant other : participants.values()) {
            if (other != part && other.alive && !other.downed && TeamManager.get().isSameTeam(other.id, part.id)) { teammateAlive = true; break; }
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
                            else if (p.offline && level != null) { // 掉線倒地（計畫書 7 D）：分身也跟著死
                                var s = com.provinggrounds.core.DisconnectBodies.get(level.getServer(), p.id);
                                if (s != null) { offlineEliminate(p, s, null, "隊伍全倒"); changed = true; }
                            }
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

    /**
     * 登機倒數期間參賽者按「取消準備」：退出這一局並還原。
     * 剩下的線上參賽者少於最少開始人數時，整局取消、全員回大廳（計畫書 A6）。
     */
    public void withdraw(ServerPlayer p) {
        if (phase != Phase.PREPARING) return;
        Participant part = participants.remove(p.getUUID());
        if (part == null) return;
        restore(p);
        broadcast(Component.literal(part.name + " 取消準備，退出本局。").withStyle(ChatFormatting.YELLOW));
        List<ServerPlayer> remaining = participantPlayers();
        int min = PGConfig.SERVER.minPlayersToStart.get();
        if (remaining.size() < min) {
            broadcast(Component.literal(String.format("剩下 %d 人，少於最少開始人數 %d 人，取消開局。", remaining.size(), min))
                .withStyle(ChatFormatting.RED));
            forceStop(false);
            GameManager.get().reset();
            return;
        }
        GameManager.get().removePlayer(p);
        if (level != null) TeamManager.get().syncVanillaTeams(level.getServer(), remaining);
        startTeamCount = countAliveTeams();
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
            if (part.alive && part.state != PlayerState.PLANE) {
                // 計畫書 7 D：掉線分身。在超時（disconnect_timeout）前都算隊伍人數，分身被殺才淘汰
                goOffline(p, part);
            } else if (part.alive) eliminate(p, part, null, Cause.LOGOUT);
            else if (!part.finalDead) finalizeDeath(part, "已離線");
        }
        discardCarrier(part);
    }

    /** 掉線（或測試指令的模擬掉線）：原地留分身、保留參賽資格（計畫書 7 D）。 */
    private void goOffline(ServerPlayer p, Participant part) {
        part.offline = true;
        part.reviveIntent = null;
        part.altarIntent = null;
        if (part.seat != null) { if (p.getVehicle() == part.seat) p.stopRiding(); part.seat.discard(); part.seat = null; }
        com.provinggrounds.core.DisconnectBodies.spawn(p, "br", part.downed);
        com.provinggrounds.core.DisconnectBodies.notifyTeam(p.server, p.getUUID(), part.name + " 掉線了（原地留下分身）");
    }

    /** 玩家回來（或測試指令的模擬回歸）：分身還活著就回到分身的位置與血量繼續。回傳 true = 已處理。 */
    public boolean returnFromOffline(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        MinecraftServer server = p.server;
        var s = com.provinggrounds.core.DisconnectBodies.get(server, p.getUUID());
        if (s == null || !"br".equals(s.mode)) return false;
        com.provinggrounds.core.DisconnectBodies.setSimulated(p.getUUID(), false);
        if (part == null || !inPlay()) { com.provinggrounds.core.DisconnectBodies.remove(server, p.getUUID()); return false; }
        part.offline = false;
        if (s.dead || !part.alive) {
            // 分身已被殺：物品已經進了墓碑，背包清空、變旁觀
            com.provinggrounds.core.DisconnectBodies.remove(server, p.getUUID());
            p.getInventory().clearContent();
            com.provinggrounds.modes.br.Backpack.clear(p);
            com.provinggrounds.special.SpecialSlot.clear(p);
            p.setGameMode(GameType.SPECTATOR);
            p.sendSystemMessage(Component.literal("你掉線時分身被擊殺了，目前是觀察者。").withStyle(ChatFormatting.GRAY));
            sendMapImage(p);
            return true;
        }
        com.provinggrounds.core.DisconnectBodies.restore(p);
        GameManager.get().addPlayer(p);
        p.setGameMode(GameType.ADVENTURE);
        com.provinggrounds.core.DisconnectBodies.clearInvulnerability(p, true);
        if (part.state == PlayerState.GROUND) part.jumpGraceUntil = 0;
        if (level != null) TeamManager.get().syncVanillaTeams(level.getServer(), participantPlayers());
        BRMovement.apply(p);
        if (part.downed) {
            com.provinggrounds.core.DownedCore.applyDowned(p, p.getHealth());
            if (PGConfig.SERVER.brDownedStyle.get() == PGConfig.DownedStyle.SIT) sitDown(p, part);
        }
        sendMapImage(p);
        com.provinggrounds.core.DisconnectBodies.notifyTeam(server, p.getUUID(), part.name + " 回來了");
        p.displayClientMessage(Component.literal("歡迎回來，比賽繼續").withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** 測試指令：模擬掉線（計畫書 7 G）。 */
    public boolean debugGoOffline(ServerPlayer p) {
        Participant part = participants.get(p.getUUID());
        if (part == null || !part.alive || !inPlay() || part.offline) return false;
        goOffline(p, part);
        com.provinggrounds.core.DisconnectBodies.setSimulated(p.getUUID(), true);
        p.setGameMode(GameType.SPECTATOR);
        return true;
    }

    /** 掉線分身被殺（DisconnectBodies 呼叫）：照一般淘汰處理，物品用存下來的狀態放進墓碑。 */
    public void onBodyKilled(com.provinggrounds.core.DisconnectBodies.OfflineState s, @Nullable ServerPlayer killer, @Nullable DamageSource source) {
        Participant part = participants.get(s.owner);
        if (part == null || !part.alive) return;
        if (killer == null && part.downedBy != null) {
            ServerPlayer downer = player(part.downedBy);
            if (downer != null && !TeamManager.get().isSameTeam(downer.getUUID(), part.id)) killer = downer;
        }
        offlineEliminate(part, s, killer, "分身被擊殺");
    }

    /**
     * 不需要玩家物件的淘汰（計畫書 7 D：掉線分身被殺、掉線超時）。統計、擊殺、擊殺紀錄、墓碑、復活牌、整隊檢查都跟一般淘汰一樣，
     * 但不會有復活牌（跟以前掉線一樣）。玩家之後回來只能旁觀（回來時背包會被清空）。
     */
    private void offlineEliminate(Participant part, com.provinggrounds.core.DisconnectBodies.OfflineState s, @Nullable ServerPlayer killer, String why) {
        if (level == null || !part.alive) return;
        PGConfig c = PGConfig.SERVER;
        PlayerDataManager data = PlayerDataManager.get();
        boolean competitive = variant == Variant.COMPETITIVE;
        part.stats.deaths++;
        part.stats.stopAlive(tickCount);
        part.stats.lastDeathTick = tickCount;
        if (killer != null && !TeamManager.get().isSameTeam(killer.getUUID(), part.id)) {
            data.addKill(killer);
            int xp = data.addXp(killer, c.xpKill.get(), competitive);
            Participant kp = participants.get(killer.getUUID());
            if (kp != null) { kp.stats.kills++; kp.stats.xp += xp; }
            killer.displayClientMessage(Component.literal("擊殺 " + part.name + "  +" + xp + " 經驗").withStyle(ChatFormatting.GOLD), true);
            PGNetwork.sendTo(killer, new com.provinggrounds.network.KillNoticePacket(false, true, killer.getGameProfile().getName(), 0xFFFFFF,
                part.name, 0xFFFFFF, killer.getMainHandItem().copyWithCount(1), 0, kp != null ? kp.stats.kills : 1));
        } else {
            killer = null;
        }
        damagers.remove(part.id);
        ProvingGroundsMod.LOGGER.info("BR eliminated (offline): {} by {} ({})", part.name, killer != null ? killer.getGameProfile().getName() : "-", why);
        PGNetwork.sendToAll(new KillFeedPacket(killer != null ? killer.getGameProfile().getName() : "", killer != null ? colorOf(killer.getUUID()) : 0xFFFFFF,
            part.name, colorOf(part.id), false));
        clearDowned(null, part);
        dropHeldCards(null, part, s.pos);
        com.provinggrounds.modes.br.Tombstones.placeOffline(level, s.pos, s.name, s.items(),
            ItemStack.of(s.backpack), ItemStack.of(s.special));
        s.dead = true;
        part.alive = false;
        part.state = PlayerState.DEAD;
        part.downedBy = null;
        part.altarTicks = 0;
        part.altarTarget = null;
        finalizeDeath(part, why);
        resolveTeams();
    }

    /** 掉線超時（disconnect_timeout，跟團隊模式同一個設定）：視為淘汰，分身消失、物品放進墓碑。 */
    private void tickOffline() {
        if (level == null || tickCount % 20 != 0) return;
        MinecraftServer server = level.getServer();
        long limit = PGConfig.SERVER.arena.disconnectTimeout.get() * 20L;
        for (Participant part : new ArrayList<>(participants.values())) {
            if (!part.offline || !part.alive) continue;
            var s = com.provinggrounds.core.DisconnectBodies.get(server, part.id);
            if (s == null) { part.offline = false; continue; }
            if (level.getGameTime() - s.since < limit) continue;
            var body = com.provinggrounds.core.DisconnectBodies.bodyOf(server, part.id);
            if (body != null) s.pos = body.position();
            offlineEliminate(part, s, null, "掉線過久");
            com.provinggrounds.core.DisconnectBodies.remove(server, part.id);
            com.provinggrounds.core.DisconnectBodies.notifyTeam(server, part.id, part.name + " 因掉線過久被移出");
        }
    }

    /** 玩家登入：倒數期間加入參賽；遊戲中變觀察者；沒有遊戲但有還原標記就還原。 */
    public void onLogin(ServerPlayer p) {
        if (returnFromOffline(p)) return;
        if (!isAirborne(p.getUUID())) { SkydiveWings.removeAll(p); com.provinggrounds.item.ParachuteItem.removeAll(p); } // 不在跳傘中卻有翼裝、降落傘（例如跳傘時斷線）就清掉

        CompoundTag tag = p.getPersistentData().getCompound(RESTORE_TAG);
        boolean sameGame = tag.contains("game") && tag.getLong("game") == gameId && phase != Phase.IDLE;

        if (phase == Phase.PREPARING && !isParticipant(p.getUUID()) && !p.isSpectator()
                && !com.provinggrounds.core.LobbyManager.isSpectator(p)) {
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
            p.sendSystemMessage(Component.literal("已加入即將開始的大逃殺。").withStyle(ChatFormatting.GREEN));
            sendMapImage(p);
        } else if (phase != Phase.IDLE) {
            if (!sameGame) markForRestore(p);
            p.setGameMode(GameType.SPECTATOR);
            p.sendSystemMessage(Component.literal("大逃殺進行中，你目前是觀察者。").withStyle(ChatFormatting.GRAY));
            sendMapImage(p);
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
            if (holder == null || !holder.alive || holder.downed || !TeamManager.get().isSameTeam(holder.id, victimId)
                    || !sameVanillaTeam(player, victimId)) {
                return true; // 只有同隊（我們的隊伍與原版計分板隊伍都相同）站著的人吸得到
            }
            ProvingGroundsMod.LOGGER.info("Revive card of {} picked up by {} (team {} / {})", card.victim, player.getGameProfile().getName(),
                TeamManager.get().teamKey(holder.id), TeamManager.get().teamKey(victimId));
            groundCards.remove(victimId);
            removeCard(card);
            holder.heldCards.add(victimId);

            Participant victim = participants.get(victimId);
            String victimName = victim != null ? victim.name : "?";
            player.displayClientMessage(Component.literal("撿起 " + victimName + " 的復活牌，任何存活隊友到祭壇都可以復活他")
                .withStyle(ChatFormatting.GOLD), true);
            notifyTeam(holder.id, Component.literal(player.getGameProfile().getName() + " 撿起了 " + victimName + " 的復活牌：任何存活隊友到祭壇都可以復活他")
                .withStyle(ChatFormatting.GOLD), player.getUUID());
            player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            ServerPlayer vp = player(victimId);
            if (vp != null) {
                vp.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " 撿起了你的復活牌")
                    .withStyle(ChatFormatting.GREEN));
            }
            return true;
        }
        return false; // 倒地也可以吸取物品（計畫書 4 的 C 章）
    }

    /** 撿牌的人與死者在原版計分板上是不是同一隊（雙重確認，避免隊伍資料不同步時被敵人吸走）。 */
    private boolean sameVanillaTeam(ServerPlayer player, UUID victimId) {
        Participant v = participants.get(victimId);
        if (v == null || level == null) return false;
        net.minecraft.world.scores.PlayerTeam pt = player.getTeam() instanceof net.minecraft.world.scores.PlayerTeam t ? t : null;
        net.minecraft.world.scores.PlayerTeam vt = level.getScoreboard().getPlayersTeam(v.name);
        if (pt == null && vt == null) return true; // 兩邊都沒有原版隊伍：只看我們自己的隊伍
        return pt != null && pt == vt;
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

    /** 對祭壇按 F（或空手右鍵）：按一下就開始自動讀條（計畫書 5 的 B2）。 */
    public void onAltarUse(ServerPlayer p, BlockPos lowerPos) {
        Participant part = participants.get(p.getUUID());
        if (part == null || !part.alive || part.downed || !inPlay()) return;
        if (lowerPos.equals(part.altarIntent)) return; // 已經在讀這座
        part.altarIntent = lowerPos.immutable();
        part.reviveIntent = null;
        resetAltar(part);
    }

    private void tickAltars() {
        assert level != null;
        PGConfig c = PGConfig.SERVER;
        int need = c.brAltarActivateSeconds.get() * 20;
        double range = c.brAltarRange.get();
        for (Participant part : participants.values()) {
            ServerPlayer p = part.alive ? player(part.id) : null;
            if (p == null || part.downed || part.state != PlayerState.GROUND) { resetAltar(part); continue; }

            BlockPos target = part.altarIntent;
            boolean using = target != null;
            if (target != null && !nearAltar(p, target, range)) {
                p.displayClientMessage(Component.literal("離開祭壇範圍，讀條中斷").withStyle(ChatFormatting.GRAY), true);
                target = null;
            } else if (target != null && !target.equals(aimedAltar(p, range))) {
                p.displayClientMessage(Component.literal("準心離開祭壇，讀條中斷").withStyle(ChatFormatting.GRAY), true);
                target = null;
            }
            AltarBlockEntity altar = target != null && level.getBlockEntity(target) instanceof AltarBlockEntity a ? a : null;
            if (altar == null) { part.altarIntent = null; resetAltar(part); continue; }

            // 復活牌全隊共用：隊伍裡任何一個活著（沒倒地）的隊友撿到，其他存活隊友都可以到祭壇復活
            boolean holding = teamCards(part) > 0;
            if (!holding) {
                if (using) p.displayClientMessage(Component.literal("隊伍裡沒有人撿到復活牌").withStyle(ChatFormatting.GRAY), true);
                part.altarIntent = null;
                resetAltar(part);
                continue;
            }
            if (!altar.isReady()) {
                p.displayClientMessage(Component.literal("祭壇冷卻中，還要 " + altar.cooldownSecondsLeft() + " 秒")
                    .withStyle(ChatFormatting.GRAY), true);
                part.altarIntent = null;
                resetAltar(part);
                continue;
            }
            if (!target.equals(part.altarTarget)) { part.altarTarget = target; part.altarTicks = 0; }
            part.altarTicks++;
            part.progressKind = ClientBRState.PROGRESS_ALTAR;
            part.progress = part.altarTicks / (float) need;
            if (part.altarTicks >= need) {
                activateAltar(p, part, target, altar);
                part.altarIntent = null;
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
    /** 全隊（活著、沒倒地的隊友）持有、還在等復活的復活牌數。 */
    private int teamCards(Participant part) {
        String team = TeamManager.get().teamKey(part.id);
        int n = 0;
        for (Participant holder : participants.values()) {
            if (!holder.alive || holder.downed || !TeamManager.get().teamKey(holder.id).equals(team)) continue;
            for (UUID v : holder.heldCards) if (isAwaiting(v)) n++;
        }
        return n;
    }

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
                    vp.altarRevived++;
                }
            }
        }
        if (revived.isEmpty()) return;
        part.stats.altarRevives += revived.size();

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
        vp.state = PlayerState.GLIDE;
        vp.lastSkydiveToggle = tickCount;
        vp.groundTicks = 0;
        resetAltar(vp);
        if (variant == Variant.COMPETITIVE) vp.usedAltar = true;
        damagers.remove(vp.id);
        vp.stats.startAlive(tickCount);

        if (v.getCamera() != v) v.setCamera(v);
        v.setGameMode(GameType.ADVENTURE);
        v.getInventory().clearContent();
        com.provinggrounds.modes.br.Backpack.clear(v); com.provinggrounds.special.SpecialSlot.clear(v);
        v.removeAllEffects();
        com.provinggrounds.special.NewSpecials.clearGamble(v); // 賭徒釣竿的最大生命增減：復活後重新算
        applyMaxHealth(v);
        healFull(v);
        v.getFoodData().setSaturation(5f);
        vp.kitOnLanding = true; // 基礎裝備落地後才發
        SkydiveWings.equip(v);
        v.startFallFlying(); // 跟跳機一樣直接鞘翅滑翔
        double y = Math.min(PGConfig.SERVER.brAltarRespawnHeight.get(), level.getMaxBuildHeight() - 2);
        v.teleportTo(level, altarPos.getX() + 0.5, y, altarPos.getZ() + 0.5, v.getYRot(), 0);
        v.fallDistance = 0;
        BRMovement.apply(v);
        title(v, Component.literal("已復活").withStyle(ChatFormatting.GREEN), Component.empty()); // 跳傘提示由 HUD 顯示
    }

    /**
     * 重新跳傘（計畫書 6 E10～E12）：傳送到 (x, 起點高度 + 300, z)，進入跟跳機一樣的鞘翅滑翔。
     * 只有大逃殺進行中、存活、沒倒地、不在飛機上的參賽者有效。回傳 false = 這個人不能重新跳傘。
     */
    public boolean reSkydive(ServerPlayer v, double x, double baseY, double z) {
        Participant vp = participants.get(v.getUUID());
        if (level == null || !inPlay() || vp == null || !vp.alive || vp.downed || vp.state == PlayerState.PLANE) return false;
        if (v.level() != level) return false;
        if (v.isPassenger()) v.stopRiding();
        vp.state = PlayerState.GLIDE;
        vp.lastSkydiveToggle = tickCount;
        vp.groundTicks = 0;
        SkydiveWings.equip(v);
        v.startFallFlying();
        double y = Math.min(baseY + 300, level.getMaxBuildHeight() - 2);
        v.teleportTo(level, x, y, z, v.getYRot(), 0);
        v.fallDistance = 0;
        BRMovement.apply(v);
        return true;
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
                part.stats.xp += xp;
                p.sendSystemMessage(Component.literal("勝利！+" + xp + " 經驗").withStyle(ChatFormatting.GOLD));
                TeamManager.Team t = TeamManager.get().getTeamOrSolo(part.id);
                if (t != TeamManager.Team.SOLO) teamLabel = t.displayName;
            }
        }

        Component headline = winnerNames.isEmpty()
            ? Component.literal("本局結束").withStyle(ChatFormatting.GRAY)
            : Component.literal((teamLabel != null ? teamLabel + " " : "") + String.join("、", winnerNames) + " 獲勝！")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        broadcast(headline);
        publishResult(winnerKey != null && startTeamCount > 1 ? winnerKey : null, teamLabel, winnerNames);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            title(p, winnerNames.isEmpty() ? Component.literal("本局結束") : Component.literal("大吉大利，今晚吃雞").withStyle(ChatFormatting.GOLD),
                headline);
        }
        playSoundAll(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, PGConfig.SERVER.endSoundVolume.get().floatValue()); // 結束音效（音量可調，預設 0.5）

        releaseAll();
        AirdropManager.cleanupAll(level);
        com.provinggrounds.modes.br.Tombstones.cleanupAll(level);
        com.provinggrounds.special.SpecialWorld.cleanup(level.getServer());
        airdrops = null;
        GameManager.get().endGame();
        loot = new LootSpawner(level, map, LootSpawner.Mode.CLEAR);
        phase = Phase.ENDING;
        phaseTicks = ENDING_SECONDS * 20;
        ProvingGroundsMod.LOGGER.info("BR ended: winners={}", winnerNames);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 本場數據（B4）
    // ═════════════════════════════════════════════════════════════════════════

    @Nullable private com.provinggrounds.modes.br.MatchResult lastResult;
    private int matchStartTick;

    /** 最近一場的數據（/pg result 用）；還沒打過時為 null。 */
    @Nullable public com.provinggrounds.modes.br.MatchResult lastResult() { return lastResult; }

    /**
     * 結算：整理每位參與者的本場統計（含中途離線的人）、算名次，累加進玩家的 BR 總數據，
     * 送「本場數據」畫面給所有線上玩家，並在聊天欄輸出精簡版。
     */
    private void publishResult(@Nullable String winnerKey, @Nullable String teamLabel, List<String> winnerNames) {
        PGConfig c = PGConfig.SERVER;
        TeamManager tm = TeamManager.get();
        // 名次：獲勝隊第 1；其他隊依「最後一位隊員被淘汰的時間」越晚越前面
        Map<String, Integer> teamLast = new HashMap<>();
        for (Participant part : participants.values()) {
            part.stats.stopAlive(tickCount);
            String key = tm.teamKey(part.id);
            int t = part.alive ? Integer.MAX_VALUE : part.stats.lastDeathTick;
            teamLast.merge(key, t, Math::max);
        }
        if (winnerKey != null) teamLast.put(winnerKey, Integer.MAX_VALUE);
        List<Integer> order = new ArrayList<>(new java.util.TreeSet<>(teamLast.values()));
        java.util.Collections.reverse(order);

        List<com.provinggrounds.modes.br.MatchResult.Row> rows = new ArrayList<>();
        PlayerDataManager data = PlayerDataManager.get();
        for (Participant part : participants.values()) {
            String key = tm.teamKey(part.id);
            boolean winner = winnerKey != null && key.equals(winnerKey);
            int place = winner ? 1 : order.indexOf(teamLast.get(key)) + 1;
            TeamManager.Team team = tm.getTeamOrSolo(part.id);
            var s = part.stats;
            rows.add(new com.provinggrounds.modes.br.MatchResult.Row(part.id, part.name,
                team == TeamManager.Team.SOLO ? "孤狼" : team.displayName, colorOf(part.id), place, winner, player(part.id) == null,
                s.kills, s.assists, Math.round(s.damage), s.aliveTicks / 20, s.revives, s.altarRevives, s.xp));
            data.addMatch(part.id, part.name, PlayerDataManager.MODE_BR, winner, s.kills, s.deaths, s.assists, s.damage, s.aliveTicks);
        }
        var weights = new com.provinggrounds.modes.br.MatchResult.Weights(c.mvpKill.get().floatValue(), c.mvpAssist.get().floatValue(),
            c.mvpDamagePerPoint.get().floatValue(), c.mvpRevive.get().floatValue(), c.mvpAltar.get().floatValue(), c.mvpWinBonus.get().floatValue());
        String label = winnerNames.isEmpty() ? "本局結束" : (teamLabel != null ? teamLabel + " 獲勝" : String.join("、", winnerNames) + " 獲勝");
        lastResult = new com.provinggrounds.modes.br.MatchResult(label, List.copyOf(winnerNames), Math.max(0, (tickCount - matchStartTick) / 20), weights, rows);

        // 結算時只先把數據送到客戶端保存（不開畫面），回到大廳後（finish）才開啟「本場數據」畫面
        PGNetwork.sendToAll(new com.provinggrounds.network.MatchResultPacket(lastResult, false));
        for (Component line : resultChatLines(lastResult)) broadcast(line);
        for (var r : rows) {
            ServerPlayer p = player(r.id());
            if (p != null) p.sendSystemMessage(ownResultLine(r));
        }
        com.provinggrounds.modes.br.StatsBoard.afterMatch(participants.keySet());
    }

    /** 聊天欄精簡版：亮點（跟畫面、/pg result 用同一份計算）。 */
    public static List<Component> resultChatLines(com.provinggrounds.modes.br.MatchResult r) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("── 本場數據（" + com.provinggrounds.modes.br.MatchResult.mmss(r.durationSeconds()) + "）──").withStyle(ChatFormatting.GOLD));
        StringBuilder sb = new StringBuilder();
        for (var h : r.highlights()) {
            if (h.names().equals("無") && !h.title().startsWith("最高")) continue; // 次要項目沒人時不佔版面
            if (sb.length() > 0) sb.append("｜");
            sb.append(h.title()).append("：").append(h.names());
            if (!h.value().isEmpty()) sb.append("（").append(h.value()).append("）");
        }
        out.add(Component.literal(sb.toString()).withStyle(ChatFormatting.YELLOW));
        return out;
    }

    public static Component ownResultLine(com.provinggrounds.modes.br.MatchResult.Row r) {
        return Component.literal(String.format("你的成績：第 %d 名｜擊殺 %d｜助攻 %d｜傷害 %d｜存活 %s｜XP +%d（輸入 /pg result 重看）",
            r.place(), r.kills(), r.assists(), r.damage(), com.provinggrounds.modes.br.MatchResult.mmss(r.aliveSeconds()), r.xp()))
            .withStyle(ChatFormatting.AQUA);
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
        java.util.List<UUID> resultViewers = new ArrayList<>(participants.keySet());
        clearState();
        if (server != null) restoreAllOnline(server);
        if (server != null) com.provinggrounds.data.BlockLedger.endMatch(server); // 地圖方塊還原
        PGNetwork.sendToAll(BRStatePacket.none());
        GameManager.get().reset();
        // 回到大廳後才開啟本場數據畫面（參與者），觀戰者可以用 /pg result 看
        if (server != null && lastResult != null) {
            for (UUID id : resultViewers) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) PGNetwork.sendTo(p, new com.provinggrounds.network.MatchResultPacket(lastResult, true));
            }
        }
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
        if (level != null) { AirdropManager.cleanupAll(level); com.provinggrounds.modes.br.Tombstones.cleanupAll(level); com.provinggrounds.special.SpecialWorld.cleanup(level.getServer()); }
        clearState();
        if (server != null) restoreAllOnline(server);
        if (server != null) com.provinggrounds.data.BlockLedger.endMatch(server); // 地圖方塊還原
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
        com.provinggrounds.core.ProneManager.clear(p);

        p.stopRiding();
        SkydiveWings.removeAll(p);
        com.provinggrounds.item.ParachuteItem.removeAll(p);
        p.setForcedPose(null);
        if (p.getCamera() != p) p.setCamera(p);
        BRMovement.remove(p);
        p.getInventory().clearContent();
        com.provinggrounds.modes.br.Backpack.clear(p); com.provinggrounds.special.SpecialSlot.clear(p);
        p.removeAllEffects();
        removeMaxHealth(p);
        com.provinggrounds.venue.VenueManager.backToLobby(p); // 回到「大廳」場地（血量 100、恢復、飽食）
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

    /** 大逃殺戰績面板：每位參賽者的擊殺、存活／倒地／陣亡、被祭壇救回幾次。依隊伍、名字排好送給所有人。 */
    private void syncBoard(MinecraftServer server) {
        List<com.provinggrounds.network.BRBoardPacket.Row> rows = new ArrayList<>();
        List<Participant> list = new ArrayList<>(participants.values());
        list.sort(java.util.Comparator.comparing((Participant x) -> TeamManager.get().teamKey(x.id)).thenComparing(x -> x.name));
        for (Participant x : list) {
            byte st = !x.alive ? com.provinggrounds.network.BRBoardPacket.DEAD
                : x.downed ? com.provinggrounds.network.BRBoardPacket.DOWNED : com.provinggrounds.network.BRBoardPacket.ALIVE;
            ServerPlayer sp = player(x.id);
            int color = 0xFFFFFF;
            if (sp != null) {
                var t = TeamManager.get().getTeam(sp).orElse(null);
                Integer c = t != null ? t.color.getColor() : null;
                if (c != null) color = c;
            }
            rows.add(new com.provinggrounds.network.BRBoardPacket.Row(x.name, color, x.stats.kills, st, x.altarRevived));
        }
        com.provinggrounds.network.BRBoardPacket pkt = new com.provinggrounds.network.BRBoardPacket(rows);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) PGNetwork.sendTo(p, pkt);
    }

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
            cx = (float) circle.centerX(); cz = (float) circle.centerZ(); r = circle.started() ? (float) circle.radius() : -1f; // 第一次縮圈前沒有白圈
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
                    case GLIDE -> ClientBRState.SELF_GLIDE;
                    case FREEFALL -> ClientBRState.SELF_FREEFALL;
                    case CHUTE -> ClientBRState.SELF_CHUTE;
                    case GROUND -> ClientBRState.SELF_ALIVE;
                    case DEAD -> ClientBRState.SELF_DEAD;
                };
                if (part.alive && part.downed) {
                    self = ClientBRState.SELF_DOWNED;
                    timerKind = ClientBRState.TIMER_BLEED;
                    timer = bleedSecondsLeft(p);
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
                cards = (byte) Math.min(127, part.alive ? teamCards(part) : 0); // 顯示全隊共用的復活牌數
            }
            boolean movement = part != null && part.alive && phase != Phase.PREPARING && phase != Phase.ENDING;
            PGNetwork.sendTo(p, new BRStatePacket(phaseId, seconds, shrinking, stage, total,
                cx, cz, r, nx, nz, nr, (short) alivePlayers, aliveTeams, self, movement ? jump : 0f,
                progressKind, progress, timerKind, (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, timer)),
                cards, crawling));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 地圖（大地圖／小地圖）
    // ═════════════════════════════════════════════════════════════════════════

    /** 敵人最後被看到的位置。 */
    private record Spot(float x, float z, float yaw, int tick) {}
    /** 每一隊（teamKey）看到的敵人：敵人 UUID → 最後看到的位置。 */
    private final Map<String, Map<UUID, Spot>> spotted = new HashMap<>();
    private static final double SPOT_RANGE = 96;
    private static final double SPOT_COS = Math.cos(Math.toRadians(55)); // 視野半角 55°
    private static final int SPOT_KEEP_TICKS = 60;                       // 看不到後標記保留 3 秒

    /** 把這局地圖的俯視圖送給一位玩家（沒產生過就不送，客戶端只畫格線與標記）。 */
    private void sendMapImage(ServerPlayer p) {
        if (map == null || level == null) return;
        com.provinggrounds.data.MapImageStore.MapImage img = com.provinggrounds.data.MapImageStore.get(level.getServer(), map.id);
        if (img != null) com.provinggrounds.network.MapImagePacket.sendTo(p, img);
        else PGNetwork.sendTo(p, new com.provinggrounds.network.MapImagePacket(map.id, 0, 0, 0, 0, 1, 0, 1, new byte[0])); // 沒有俯視圖：清掉上一張
    }

    /** 更新「隊友視野內的敵人」：在 96 格內、在視野角度內、中間沒有方塊擋住。 */
    private void updateSpotting() {
        List<ServerPlayer> alive = new ArrayList<>();
        for (Participant part : participants.values()) {
            if (!part.alive) continue;
            ServerPlayer sp = player(part.id);
            if (sp != null && !sp.isSpectator()) alive.add(sp);
        }
        for (ServerPlayer watcher : alive) {
            String key = TeamManager.get().teamKey(watcher.getUUID());
            Vec3 eye = watcher.getEyePosition();
            Vec3 look = watcher.getLookAngle();
            for (ServerPlayer target : alive) {
                if (target == watcher || TeamManager.get().isSameTeam(watcher, target)) continue;
                if (target.isInvisible()) continue; // 隱形的人（飛機上、剛跳機、隱形斗篷）不會被看到、不會出現在小地圖
                String pair = watcher.getUUID() + "|" + target.getUUID();
                if (!reallySees(watcher, eye, look, target)) { spotStart.remove(pair); continue; }
                // 要連續看到 spot_delay_ticks 才標記（一閃而過的不算）
                int since = spotStart.computeIfAbsent(pair, k -> tickCount);
                if (tickCount - since < PGConfig.SERVER.spotDelayTicks.get()) continue;
                spotted.computeIfAbsent(key, k -> new HashMap<>())
                    .put(target.getUUID(), new Spot((float) target.getX(), (float) target.getZ(), target.getYRot(), tickCount));
            }
        }
        // 清掉過期的；正在隱形的人，之前留下的標記也立刻拿掉
        for (Map<UUID, Spot> m : spotted.values()) {
            m.values().removeIf(s -> tickCount - s.tick() > SPOT_KEEP_TICKS);
            m.keySet().removeIf(id -> { ServerPlayer sp = player(id); return sp != null && sp.isInvisible(); });
        }
        if (spotStart.size() > 4096) spotStart.clear(); // 保險：不會無限長大
    }

    /** 「開始看到」的 tick（看的人|被看的人）。 */
    private final Map<String, Integer> spotStart = new HashMap<>();

    /**
     * 真的看到敵人（小地圖標記用）：
     *   距離：spot_range 格內（蹲下 spot_range_crouch、趴下或倒地 spot_range_prone）；
     *   角度：在準星附近（spot_angle 度內；spot_close_range 格內放寬到 spot_close_angle 度）；
     *   遮擋：頭、身體、腳三個點至少看得到兩個（只露一點邊不算）。
     */
    private boolean reallySees(ServerPlayer watcher, Vec3 eye, Vec3 look, ServerPlayer target) {
        PGConfig c = PGConfig.SERVER;
        double h = target.getBbHeight();
        Vec3 center = target.position().add(0, h * 0.6, 0);
        Vec3 to = center.subtract(eye);
        double dist = to.length();
        if (dist < 0.01) return true;
        double range = target.getPose() == net.minecraft.world.entity.Pose.SWIMMING ? c.spotRangeProne.get()
            : target.isCrouching() ? c.spotRangeCrouch.get() : c.spotRange.get();
        if (dist > range) return false;
        double angle = dist <= c.spotCloseRange.get() ? c.spotCloseAngle.get() : c.spotAngle.get();
        if (to.scale(1 / dist).dot(look) < Math.cos(Math.toRadians(angle))) return false;
        int visible = 0;
        for (double f : new double[] { 0.9, 0.55, 0.1 }) { // 頭、身體、腳
            Vec3 p = target.position().add(0, h * f, 0);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, p, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, watcher));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) visible++;
        }
        return visible >= 2;
    }

    /** 每 5 tick 送地圖標記給每位玩家。 */
    private void syncMarkers(MinecraftServer server) {
        updateSpotting();
        List<MapMarkersPacket.Marker> drops = new ArrayList<>();
        if (airdrops != null) {
            for (AirdropManager.MapPoint d : airdrops.mapPoints()) {
                drops.add(new MapMarkersPacket.Marker(d.falling() ? MapMarkersPacket.AIRDROP_FALLING : MapMarkersPacket.AIRDROP,
                    d.x() + 0.5f, d.z() + 0.5f, 0, 0xFFD040, "", 0));
            }
        }
        // 復活祭壇（所有人都看得到）：冷卻中的用灰色
        for (BlockPos a : com.provinggrounds.data.AltarLedger.get(server).entries()) {
            boolean ready = !(level != null && level.isLoaded(a) && level.getBlockEntity(a) instanceof AltarBlockEntity ab && !ab.isReady());
            drops.add(new MapMarkersPacket.Marker(ready ? MapMarkersPacket.ALTAR : MapMarkersPacket.ALTAR_COOLDOWN,
                a.getX() + 0.5f, a.getZ() + 0.5f, 0, 0xB070FF, "", 0));
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<MapMarkersPacket.Marker> list = new ArrayList<>(drops);
            Participant self = participants.get(viewer.getUUID());
            for (Participant part : participants.values()) {
                if (!part.alive || part.id.equals(viewer.getUUID())) continue;
                ServerPlayer sp = player(part.id);
                if (sp == null) continue;
                boolean mate = self != null && TeamManager.get().isSameTeam(self.id, part.id);
                if (self == null) {
                    // 觀戰者（不是參賽者）：看得到所有參賽者
                    list.add(new MapMarkersPacket.Marker(part.downed ? MapMarkersPacket.TEAMMATE_DOWNED : MapMarkersPacket.PLAYER,
                        (float) sp.getX(), (float) sp.getZ(), sp.getYRot(), colorOf(part.id), part.name, 0));
                } else if (mate) {
                    list.add(new MapMarkersPacket.Marker(part.downed ? MapMarkersPacket.TEAMMATE_DOWNED : MapMarkersPacket.TEAMMATE,
                        (float) sp.getX(), (float) sp.getZ(), sp.getYRot(), colorOf(part.id), part.name, 0));
                }
            }
            if (self != null) {
                Map<UUID, Spot> seen = spotted.get(TeamManager.get().teamKey(self.id));
                if (seen != null) {
                    for (Map.Entry<UUID, Spot> e : seen.entrySet()) {
                        Spot s = e.getValue();
                        list.add(new MapMarkersPacket.Marker(MapMarkersPacket.ENEMY, s.x(), s.z(), s.yaw(), 0xE04848, "",
                            tickCount - s.tick()));
                    }
                }
            }
            PGNetwork.sendTo(viewer, new MapMarkersPacket(list));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 小工具
    // ═════════════════════════════════════════════════════════════════════════

    @Nullable
    private ServerPlayer player(UUID id) {
        if (com.provinggrounds.core.DisconnectBodies.isSimulated(id)) return null; // 測試指令的「模擬掉線」（計畫書 7 G）
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

    /** BR 最大血量用的屬性修改（固定 UUID，方便移除；會跟著玩家存檔，斷線重連仍保留）。 */
    private static final UUID MAX_HEALTH_MOD = UUID.fromString("5d0b7f3e-2c41-4b8e-9a63-7f1e0c2d4b10");

    /** 把最大血量調成設定值（不管原本基礎值是多少）。 */
    private static void applyMaxHealth(ServerPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MAX_HEALTH_MOD);
        double extra = PGConfig.SERVER.brMaxHealth.get() - attr.getValue();
        if (extra != 0) {
            attr.addPermanentModifier(new AttributeModifier(MAX_HEALTH_MOD, "pg_br_max_health", extra, AttributeModifier.Operation.ADDITION));
        }
    }

    /** 移除 BR 最大血量修改，恢復原本的最大血量。 */
    private static void removeMaxHealth(ServerPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(MAX_HEALTH_MOD);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
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
        playSoundAll(sound, pitch, 1f);
    }

    private void playSoundAll(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        MinecraftServer server = level != null ? level.getServer() : GameManager.get().getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        }
    }

    private static String mmss(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** 目前在線的參賽者。 */
    private List<ServerPlayer> participantPlayers() {
        List<ServerPlayer> list = new ArrayList<>();
        for (Participant part : participants.values()) {
            ServerPlayer p = player(part.id);
            if (p != null) list.add(p);
        }
        return list;
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
