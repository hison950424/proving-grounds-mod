package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.CirclePreset;
import com.provinggrounds.network.LobbyStatePacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: LobbyManager.java
 * 目的: 大廳流程（伺服器端，單例）。
 *   管理員（OP 等級 2）：選地圖、縮圈方案、一般／競技、分隊方式（玩家自選／管理員分配）、隊伍數量、
 *                       幫玩家換隊、自動分隊、全部移出隊伍、指定誰去觀戰、開始、強制開始（全體提示＋倒數）、
 *                       取消倒數、強制結束進行中的遊戲。
 *   玩家：自選隊伍（自選模式時）、準備／取消準備。
 *   觀戰者：打開選單按「加入遊戲」→ 大廳中立刻變冒險模式回大廳；遊戲中則等這局結束才回來。
 * 觀戰與「本局結束後加入」用玩家存檔標籤記錄（伺服器重開也保留）。
 * 任何變動都會在下一 tick 把 {@link LobbyStatePacket} 廣播給所有人，選單即時更新。
 * 執行者：伺服器（WorldEvents 每 tick 呼叫 tick()；客戶端操作經 LobbyActionPacket 呼叫 handle()）。
 */
public final class LobbyManager {

    /** 分隊方式。 */
    public enum TeamMode {
        FREE("玩家自選"), ASSIGN("管理員分配");
        public final String displayName;
        TeamMode(String displayName) { this.displayName = displayName; }
    }

    /** 客戶端可以要求的操作（arg、target 依操作而定）。 */
    public enum Action {
        SET_MAP, SET_PRESET, SET_VARIANT, SET_TEAM_MODE, SET_TEAM_COUNT, // 管理員：設定
        JOIN_TEAM,          // 自己換隊（arg = 隊伍編號，0 = 孤狼）
        SET_PLAYER_TEAM,    // 管理員：幫 target 換隊（arg = 隊伍編號）
        AUTO_BALANCE,       // 管理員：隨機平均分隊
        TOGGLE_READY,       // 準備／取消準備
        SET_SPECTATOR,      // 管理員：target 觀戰（arg 1）或取消觀戰（arg 0）
        JOIN_GAME,          // 觀戰者自己按「加入遊戲」
        START, FORCE_START, CANCEL_COUNTDOWN, // 管理員：開始
        END_GAME,           // 管理員：強制結束進行中的遊戲、全員回大廳
        CLEAR_TEAMS,        // 管理員：所有人移出隊伍（變成未分隊）
        SET_SPECIAL,        // 管理員：特殊物品模式開（arg 1）／關（arg 0）
        SET_BLOCK_DAMAGE,   // 管理員：大逃殺炸毀方塊（arg 0 關、1 全部、2 只有槍械、3 只有投擲物）
        SET_CIRCLE_STAGES, SET_CIRCLE_INTERVAL, SET_CIRCLE_SPEED, SET_CIRCLE_WAIT, SET_CIRCLE_FINAL, // 管理員：自訂縮圈（計畫書 6 D2；速度 arg = 倍率 × 10）
        SET_MY_CLASS,       // 自己：開始前先選職業（arg = ArenaClass 的順序；團隊死鬥、佔點用）
        SET_WEAPON_MODE,    // 管理員：槍械模式（arg = WeaponMode 的順序）
        GOTO_VENUE,         // 自己：傳送到場地（arg = 場地選單的順序）
        SET_GAME_MODE,      // 管理員：選模式（arg = GameManager.GameMode 的順序）
        SET_TDM_KILLS, SET_TDM_MINUTES, SET_DOM_SCORE, SET_GK_MULTIPLIER, // 管理員：對戰設定（arg = 新的值）
        SET_CIRCLE_HOLD,    // 管理員：自訂縮圈的決賽圈停留秒數（計畫書 7 B）
        SET_ARENA_DOWNED,   // 管理員：團隊模式倒地開（arg 1）／關（arg 0）（計畫書 7 E）
        SET_FRIENDLY_FIRE,  // 管理員：所有模式的友軍誤傷開（arg 1）／關（arg 0）（只限槍械子彈；投擲物與爆炸物強制有友傷）
        CONFIRM_CLASS,      // 計畫書 10：強制選角階段確認職業（arg = ArenaClass 順序）
        CONFIRM_SPECIAL,    // 計畫書 10：佔點強制選特殊道具階段確認道具（arg = SpecialType 順序）
        SET_ARENA_MAP       // 管理員：團隊模式場地（arg = arenaMapChoices 索引）
    }

    /** 團隊模式可選場地（id, 名稱），第一個固定是設定檔的經典場地，其餘依 id 排序；客戶端以相同順序顯示。 */
    public static List<String[]> arenaMapChoices(MinecraftServer server) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] { com.provinggrounds.modes.arena.ArenaMapRegistry.DEFAULT_ID, "經典場地（設定檔）" });
        com.provinggrounds.modes.arena.ArenaMapRegistry.get(server).customMaps().stream()
            .sorted(java.util.Comparator.comparing(com.provinggrounds.modes.arena.ArenaMapRegistry.ArenaMapDef::id))
            .forEach(d -> out.add(new String[] { d.id().toLowerCase(java.util.Locale.ROOT), d.name() }));
        return out;
    }

    /** 大廳強制選擇階段：0 = 一般大廳、1 = 強制選職業、2 = 佔點強制選特殊道具。 */
    public static final int DRAFT_NONE = 0, DRAFT_CLASS = 1, DRAFT_SPECIAL = 2;

    public static final String SPECTATOR_TAG = "pg_spectator";
    public static final String JOIN_AFTER_TAG = "pg_join_after";
    public static final int MAX_TEAMS = 64;

    private static final LobbyManager INSTANCE = new LobbyManager();
    public static LobbyManager get() { return INSTANCE; }

    private int mapId = -1; // -1 = 還沒選（用第一張地圖）
    private CirclePreset preset = CirclePreset.STANDARD;
    private BattleRoyaleMode.Variant variant = BattleRoyaleMode.Variant.NORMAL;
    private TeamMode teamMode = TeamMode.FREE;
    private int teamCount = 4;
    private WeaponMode weaponMode = WeaponMode.ALL;
    private GameManager.GameMode gameMode = GameManager.GameMode.BATTLE_ROYALE;
    private int tdmKills = -1, tdmMinutes = -1, domScore = -1, gkMultiplier = -1; // -1 = 用伺服器設定的預設值

    /** 大廳選的模式。 */
    public GameManager.GameMode gameMode() { return gameMode; }

    /** 團隊死鬥／佔點／槍王之王的設定（沒調過就用伺服器設定的預設值）。 */
    /** 團隊模式倒地開關（計畫書 7 E；null = 用設定檔的預設值）。 */
    @Nullable private Boolean arenaDowned;
    /** 所有模式的友軍誤傷開關（計畫書 9；null = 用設定檔的預設值）。只影響槍械子彈，投擲物與爆炸物永遠有友傷。 */
    @Nullable private Boolean friendlyFire;

    public boolean friendlyFire() {
        if (friendlyFire != null) return friendlyFire;
        try { return PGConfig.SERVER.friendlyFireDefault.get(); } catch (IllegalStateException e) { return false; }
    }

    public com.provinggrounds.modes.arena.ArenaManager.Settings arenaSettings() {
        var c = PGConfig.SERVER.arena;
        return new com.provinggrounds.modes.arena.ArenaManager.Settings(
            tdmKills > 0 ? tdmKills : c.tdmKills.get(), tdmMinutes > 0 ? tdmMinutes : c.tdmMinutes.get(),
            domScore > 0 ? domScore : c.domScore.get(), gkMultiplier > 0 ? gkMultiplier : c.gkMultiplier.get(),
            arenaDowned != null ? arenaDowned : c.downedDefault.get());
    }

    /** 槍械模式（這一場只出某一類的槍）。 */
    public WeaponMode weaponMode() { return weaponMode; }

    /** 自訂縮圈：把值夾在設定允許的範圍內再存檔。 */
    private static void setCustom(net.minecraftforge.common.ForgeConfigSpec.IntValue v, int value, int min, int max) {
        v.set(Math.max(min, Math.min(max, value)));
        v.save();
    }

    /**
     * 自訂縮圈目前的值：階段數、間隔、速度 × 10、開場等待、最後一圈 %、決賽圈停留秒數（計畫書 7 B）、
     * 以及「目前選的方案」在目前地圖上的預估總秒數（計畫書 7 A6：由伺服器用最新設定算，客戶端的設定副本只在登入時同步，會過期）。
     */
    private static int[] customCircle(CirclePreset preset, double mapRadius) {
        try {
            PGConfig c = PGConfig.SERVER;
            return new int[] { c.brCustomStages.get(), c.brCustomInterval.get(), (int) Math.round(c.brCustomSpeed.get() * 10),
                c.brCustomFirstWait.get(), c.brCustomFinal.get(), CirclePreset.CUSTOM.finalHoldSeconds(),
                (int) Math.round(preset.estimateSeconds(mapRadius)) };
        } catch (IllegalStateException e) {
            return new int[] { 4, 90, 6, 60, 0, 45, 0 };
        }
    }

    /** 地圖半徑（預估縮圈時間用；沒有地圖時 400）。 */
    private static double mapRadius(net.minecraft.server.MinecraftServer server, int map) {
        MapRegistry.BRMap m = MapRegistry.get(server).getMap(map);
        return m == null ? 400 : m.radius;
    }

    /** 玩家在大廳預選的職業（沒選過 = 自由人）。 */
    private final java.util.Map<UUID, com.provinggrounds.modes.arena.ArenaClass> classChoice = new java.util.HashMap<>();

    public com.provinggrounds.modes.arena.ArenaClass chosenClass(UUID id) {
        return classChoice.getOrDefault(id, com.provinggrounds.modes.arena.ArenaClass.FREELANCER);
    }

    public void rememberClass(UUID id, com.provinggrounds.modes.arena.ArenaClass cls) {
        classChoice.put(id, cls);
    }

    /** 團隊死鬥、佔點：固定紅藍兩隊，沒有孤狼（計畫書 6 A1）。 */
    public boolean twoTeams() {
        return gameMode == GameManager.GameMode.TDM || gameMode == GameManager.GameMode.DOM;
    }
    private final Set<UUID> ready = new HashSet<>();
    private int draftPhase = DRAFT_NONE;
    private final Set<UUID> draftDone = new HashSet<>();
    private int countdownTicks = -1; // 強制開始倒數，-1 = 沒在倒數
    private boolean forcedCountdown; // 這次開始流程是否由管理員「強制所有人準備」觸發（倒數副標題用）
    private boolean dirty = true;

    private LobbyManager() {}

    // ── 查詢 ────────────────────────────────────────────────────────────────

    public static boolean isSpectator(ServerPlayer p) {
        return p.getPersistentData().getBoolean(SPECTATOR_TAG);
    }

    public static boolean isJoinPending(ServerPlayer p) {
        return p.getPersistentData().getBoolean(JOIN_AFTER_TAG);
    }

    /** 這局要參賽的人：在線、不是觀戰者。 */
    public static List<ServerPlayer> participants(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isSpectator(p)) list.add(p);
        }
        return list;
    }

    /** 有變動：下一 tick 廣播給所有人。 */
    public void broadcast() { dirty = true; }

    // ── 每 tick ─────────────────────────────────────────────────────────────

    private boolean wasAllReady;

    /** 所有參賽者都按了準備（至少 1 人）。 */
    private boolean allReady(MinecraftServer server) {
        List<ServerPlayer> ps = participants(server);
        if (ps.isEmpty()) return false;
        for (ServerPlayer p : ps) if (!ready.contains(p.getUUID())) return false;
        return true;
    }

    /** 所有參賽者都完成了目前的強制選擇階段（選職業／選特殊道具）。 */
    private boolean allDraftDone(MinecraftServer server) {
        List<ServerPlayer> ps = participants(server);
        if (ps.isEmpty()) return false;
        for (ServerPlayer p : ps) if (!draftDone.contains(p.getUUID())) return false;
        return true;
    }

    /** 推進強制選角／選道具階段：全部選完後進下一階段或進入 10 秒大廳倒數。 */
    private void checkDraftAdvance(MinecraftServer server) {
        if (draftPhase == DRAFT_NONE || !allDraftDone(server)) return;
        if (draftPhase == DRAFT_CLASS && gameMode == GameManager.GameMode.DOM && com.provinggrounds.special.SpecialConfig.enabledSafe()) {
            draftPhase = DRAFT_SPECIAL;
            draftDone.clear();
            broadcastChat(server, Component.literal("[PG] 全員已選好職業！請選擇本場佔點特殊道具！").withStyle(ChatFormatting.AQUA));
            dirty = true;
            return;
        }
        draftPhase = DRAFT_NONE;
        draftDone.clear();
        countdownTicks = ALL_READY_SECONDS * 20;
        broadcastChat(server, Component.literal("[PG] 全員已確認完成，" + ALL_READY_SECONDS + " 秒後開始遊戲！").withStyle(ChatFormatting.GREEN));
        dirty = true;
    }

    /** 全員準備後倒數幾秒開始（所有模式；計畫書 6 A20）。 */
    public static final int ALL_READY_SECONDS = 10;

    public void tick(MinecraftServer server) {
        // 大廳裡所有參賽者都準備好：
        //   - 大逃殺：直接開局進入 10 秒登機倒數
        //   - 團隊死鬥／佔點（計畫書 10）：先強制進入「選職業」畫面，全員選完後（佔點且開特殊道具再選特殊道具），才進入大廳倒數 10 秒
        //   - 槍王之王：直接進入大廳倒數 10 秒
        if (GameManager.get().isInLobby()) {
            boolean all = allReady(server);
            if (all && !wasAllReady && countdownTicks < 0 && draftPhase == DRAFT_NONE) {
                if (gameMode == GameManager.GameMode.BATTLE_ROYALE) {
                    broadcastChat(server, Component.literal("[PG] 全員已準備，" + ALL_READY_SECONDS + " 秒後登機！").withStyle(ChatFormatting.GREEN));
                    String error = doStart(server);
                    if (error != null) broadcastChat(server, Component.literal("[PG] 無法開始：" + error).withStyle(ChatFormatting.RED));
                } else if (gameMode == GameManager.GameMode.TDM || gameMode == GameManager.GameMode.DOM) {
                    draftPhase = DRAFT_CLASS;
                    draftDone.clear();
                    broadcastChat(server, Component.literal("[PG] 全員已準備！請選擇職業，全員選完後開始 10 秒倒數。").withStyle(ChatFormatting.AQUA));
                } else {
                    countdownTicks = ALL_READY_SECONDS * 20;
                    broadcastChat(server, Component.literal("[PG] 全員已準備，" + ALL_READY_SECONDS + " 秒後開始遊戲！").withStyle(ChatFormatting.GREEN));
                }
                dirty = true;
            } else if (!all && (countdownTicks >= 0 || draftPhase != DRAFT_NONE)) {
                countdownTicks = -1;
                forcedCountdown = false;
                draftPhase = DRAFT_NONE;
                draftDone.clear();
                broadcastChat(server, Component.literal("[PG] 有人取消準備，開始流程已停止。").withStyle(ChatFormatting.YELLOW));
                clearTitles(server);
                dirty = true;
            } else if (draftPhase != DRAFT_NONE) {
                checkDraftAdvance(server);
            }
            wasAllReady = all;
        } else {
            wasAllReady = false;
            draftPhase = DRAFT_NONE;
            draftDone.clear();
        }
        if (countdownTicks >= 0) {

            if (!GameManager.get().isInLobby()) {
                countdownTicks = -1;
                dirty = true;
            } else {
                if (countdownTicks % 20 == 0 && countdownTicks > 0) announceCountdown(server, countdownTicks / 20);
                if (countdownTicks == 0) {
                    countdownTicks = -1;
                    String error = doStart(server);
                    if (error != null) broadcastChat(server, Component.literal("[PG] 無法開始：" + error).withStyle(ChatFormatting.RED));
                }
                if (countdownTicks > 0) countdownTicks--;
                if (countdownTicks % 20 == 0) dirty = true; // 每秒更新選單上的倒數
            }
        }
        if (dirty) {
            dirty = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PGNetwork.sendTo(p, buildPacket(server));
        }
    }

    private static void clearTitles(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        }
    }

    // ── 玩家進出、回大廳 ────────────────────────────────────────────────────

    public void onLogin(ServerPlayer p) {
        if (GameManager.get().isInLobby()) {
            if (isJoinPending(p)) leaveSpectate(p);
            else if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
            sendLobbyMapImage(p);
        }
        dirty = true;
    }

    public void onLogout(ServerPlayer p) {
        ready.remove(p.getUUID());
        draftDone.remove(p.getUUID());
        dirty = true;
    }

    /** 一局結束回到大廳（GameManager.reset 呼叫）：處理「本局結束後加入」的觀戰者。 */
    public void onReturnToLobby(@Nullable MinecraftServer server) {
        ready.clear();
        draftPhase = DRAFT_NONE;
        draftDone.clear();
        countdownTicks = -1;
        dirty = true;
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isJoinPending(p)) leaveSpectate(p);
            else if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
        }
        broadcastLobbyMapImage(server);
    }

    // ── 操作 ────────────────────────────────────────────────────────────────

    public void handle(ServerPlayer sender, Action action, int arg, @Nullable UUID targetId) {
        MinecraftServer server = sender.server;
        boolean op = sender.hasPermissions(2);
        boolean lobby = GameManager.get().isInLobby();
        ServerPlayer target = targetId != null ? server.getPlayerList().getPlayer(targetId) : null;

        if (action == Action.GOTO_VENUE) { // 場地選擇：對局中沒參賽的人也可以用
            com.provinggrounds.venue.VenueManager.goTo(sender, arg);
            return;
        }
        if (action == Action.JOIN_GAME) {
            joinGame(sender, lobby);
            return;
        }
        if (action == Action.END_GAME) {
            if (!op) { deny(sender, "只有管理員可以這麼做。"); return; }
            endGame(sender);
            return;
        }
        // 登機倒數期間按「取消準備」：退出這一局（人數不足時整局取消，回到大廳）
        if (action == Action.TOGGLE_READY && !lobby && BattleRoyaleMode.get().getPhase() == BattleRoyaleMode.Phase.PREPARING) {
            BattleRoyaleMode.get().withdraw(sender);
            dirty = true;
            return;
        }
        if (!lobby) {
            deny(sender, "遊戲進行中，大廳設定暫時不能改。");
            return;
        }

        switch (action) {
            case JOIN_TEAM -> {
                if (isSpectator(sender)) { deny(sender, "觀戰中不能選隊，請先按「加入遊戲」。"); return; }
                if (teamMode == TeamMode.ASSIGN && !op) { deny(sender, "目前由管理員分隊。"); return; }
                if (arg < 0 || arg > teamCount) return;
                if (arg == 0 && twoTeams()) { deny(sender, "團隊死鬥、佔點沒有孤狼，請選紅隊或藍隊。"); return; }
                TeamManager.get().assignTeam(sender, TeamManager.Team.of(arg));
            }
            case TOGGLE_READY -> {
                if (isSpectator(sender)) return;
                if (!ready.remove(sender.getUUID())) ready.add(sender.getUUID());
            }
            case SET_MY_CLASS -> {
                com.provinggrounds.modes.arena.ArenaClass[] all = com.provinggrounds.modes.arena.ArenaClass.values();
                if (arg < 0 || arg >= all.length) return;
                classChoice.put(sender.getUUID(), all[arg]);
            }
            case CONFIRM_CLASS -> {
                if (isSpectator(sender)) return;
                com.provinggrounds.modes.arena.ArenaClass[] all = com.provinggrounds.modes.arena.ArenaClass.values();
                if (arg < 0 || arg >= all.length) return;
                classChoice.put(sender.getUUID(), all[arg]);
                if (draftPhase == DRAFT_CLASS) {
                    draftDone.add(sender.getUUID());
                    checkDraftAdvance(server);
                }
            }
            case CONFIRM_SPECIAL -> {
                if (isSpectator(sender)) return;
                com.provinggrounds.special.DomGear.setChoice(sender, arg);
                if (draftPhase == DRAFT_SPECIAL) {
                    draftDone.add(sender.getUUID());
                    checkDraftAdvance(server);
                }
            }
            default -> {
                if (!op) { deny(sender, "只有管理員可以這麼做。"); return; }
                if (!handleAdmin(sender, server, action, arg, target)) return;
            }
        }
        dirty = true;
    }

    /** 管理員操作。回傳 false 表示沒有變動（已提示原因）。 */
    private boolean handleAdmin(ServerPlayer sender, MinecraftServer server, Action action, int arg, @Nullable ServerPlayer target) {
        switch (action) {
            case SET_MAP -> {
                if (MapRegistry.get(server).getMap(arg) == null) return false;
                mapId = arg;
                broadcastLobbyMapImage(server);
            }
            case SET_PRESET -> {
                CirclePreset[] all = CirclePreset.values();
                if (arg < 0 || arg >= all.length) return false;
                preset = all[arg];
            }
            case SET_VARIANT -> {
                BattleRoyaleMode.Variant[] all = BattleRoyaleMode.Variant.values();
                if (arg < 0 || arg >= all.length) return false;
                variant = all[arg];
            }
            case SET_SPECIAL -> com.provinggrounds.special.SpecialConfig.setEnabled(arg != 0);
            case SET_CIRCLE_STAGES -> setCustom(PGConfig.SERVER.brCustomStages, arg, 2, 8);
            case SET_CIRCLE_INTERVAL -> setCustom(PGConfig.SERVER.brCustomInterval, arg, 15, 600);
            case SET_CIRCLE_WAIT -> setCustom(PGConfig.SERVER.brCustomFirstWait, arg, 0, 600);
            case SET_CIRCLE_FINAL -> setCustom(PGConfig.SERVER.brCustomFinal, arg, 0, 50);
            case SET_CIRCLE_HOLD -> setCustom(PGConfig.SERVER.brCustomFinalHold, arg, 0, 600);
            case SET_CIRCLE_SPEED -> {
                double v = Math.max(0.1, Math.min(2.0, arg / 10.0));
                PGConfig.SERVER.brCustomSpeed.set(v);
                PGConfig.SERVER.brCustomSpeed.save();
            }
            case SET_BLOCK_DAMAGE -> {
                if (arg < 0 || arg > 3) return false;
                com.provinggrounds.config.PGConfig.SERVER.brExplosionBlockDamage.set(arg);
                com.provinggrounds.config.PGConfig.SERVER.brExplosionBlockDamage.save();
            }
            case SET_WEAPON_MODE -> weaponMode = WeaponMode.byOrdinal(arg);
            case SET_GAME_MODE -> {
                GameManager.GameMode[] modes = GameManager.GameMode.values();
                if (arg <= 0 || arg >= modes.length) return false; // 0 = NONE
                gameMode = modes[arg];
                if (twoTeams()) {
                    teamCount = 2; // 紅藍兩隊；孤狼、第 3 隊以後的人改回未分隊
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        TeamManager.get().getTeam(p).ifPresent(t -> { if (t.id == 0 || t.id > 2) TeamManager.get().unassign(p); });
                    }
                }
                if (gameMode == GameManager.GameMode.BATTLE_ROYALE) {
                    broadcastLobbyMapImage(server);
                }
            }
            case SET_TDM_KILLS -> tdmKills = Math.max(10, Math.min(10_000, arg));
            case SET_TDM_MINUTES -> tdmMinutes = Math.max(1, Math.min(120, arg));
            case SET_DOM_SCORE -> domScore = Math.max(100, Math.min(100_000, arg));
            case SET_GK_MULTIPLIER -> gkMultiplier = Math.max(1, Math.min(20, arg));
            case SET_ARENA_DOWNED -> arenaDowned = arg != 0;
            case SET_FRIENDLY_FIRE -> friendlyFire = arg != 0;
            case SET_ARENA_MAP -> {
                List<String[]> choices = arenaMapChoices(server);
                if (arg < 0 || arg >= choices.size()) return false;
                if (!com.provinggrounds.modes.arena.ArenaMapRegistry.get(server).setActive(choices.get(arg)[0])) return false;
                broadcastChat(server, Component.literal("[PG] 團隊模式場地已切換為「" + choices.get(arg)[1] + "」。").withStyle(ChatFormatting.AQUA));
            }
            case SET_TEAM_MODE -> {
                TeamMode[] all = TeamMode.values();
                if (arg < 0 || arg >= all.length) return false;
                teamMode = all[arg];
            }
            case SET_TEAM_COUNT -> {
                teamCount = Math.max(0, Math.min(MAX_TEAMS, arg)); // 0 = 全員孤狼（計畫書 5 的 F）
                // 編號超過隊伍數的人改回未分隊
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    TeamManager.get().getTeam(p).ifPresent(t -> { if (t.id > teamCount) TeamManager.get().unassign(p); });
                }
            }
            case SET_PLAYER_TEAM -> {
                // arg：1.. 隊伍、0 孤狼、-1 未分隊。觀戰者被拖到隊伍裡 → 先結束觀戰再分隊
                if (target == null || arg < -1 || arg > teamCount) return false;
                if (arg == 0 && twoTeams()) return false; // 團隊死鬥、佔點沒有孤狼
                if (isSpectator(target)) leaveSpectate(target);
                if (arg == -1) TeamManager.get().unassign(target);
                else TeamManager.get().assignTeam(target, TeamManager.Team.of(arg));
            }
            case CLEAR_TEAMS -> {
                TeamManager.get().clearAssignments();
                broadcastChat(server, Component.literal("[PG] 管理員已把所有人移出隊伍。").withStyle(ChatFormatting.YELLOW));
            }
            case AUTO_BALANCE -> {
                if (teamCount == 0) TeamManager.get().clearAssignments(); // 隊伍數 0：全員孤狼
                else TeamManager.get().balance(participants(server), teamCount);
                broadcastChat(server, Component.literal(teamCount == 0 ? "[PG] 隊伍數 0：全員孤狼。" : "[PG] 管理員已自動分成 " + teamCount + " 隊。").withStyle(ChatFormatting.YELLOW));
            }
            case SET_SPECTATOR -> {
                if (target == null) return false;
                if (arg != 0) {
                    target.getPersistentData().putBoolean(SPECTATOR_TAG, true);
                    target.getPersistentData().remove(JOIN_AFTER_TAG);
                    ready.remove(target.getUUID());
                    target.setGameMode(GameType.SPECTATOR);
                    target.sendSystemMessage(Component.literal("[PG] 管理員把你設為觀戰。打開選單（M）按「加入遊戲」可以回到大廳。")
                        .withStyle(ChatFormatting.GRAY));
                } else {
                    leaveSpectate(target);
                }
            }
            case START -> {
                // 計畫書 7 A2：移除「開始」按鈕。開始只有兩種方式：全員準備自動開始、管理員「強制所有人準備」
                deny(sender, "請讓所有人按「準備」，或按「強制所有人準備」。");
                return false;
            }
            case FORCE_START -> {
                // 計畫書 6 A20：強制開始改成「強制所有人準備」，接著走正常準備後流程（團隊模式先選角再倒數）
                if (countdownTicks >= 0 || draftPhase != DRAFT_NONE) return false;
                for (ServerPlayer p : participants(server)) ready.add(p.getUUID());
                forcedCountdown = true;
                broadcastChat(server, Component.literal("[PG] 管理員讓所有人進入準備狀態。").withStyle(ChatFormatting.GOLD));
            }
            case CANCEL_COUNTDOWN -> {
                if (countdownTicks < 0 && draftPhase == DRAFT_NONE) return false;
                countdownTicks = -1;
                forcedCountdown = false;
                draftPhase = DRAFT_NONE;
                draftDone.clear();
                ready.clear();
                broadcastChat(server, Component.literal("[PG] 管理員取消了開始流程。").withStyle(ChatFormatting.YELLOW));
                clearTitles(server);
            }
            default -> { return false; }
        }
        return true;
    }

    /** 還沒準備的參賽者名字（開始的管理員自己不算）。 */
    private List<String> notReady(MinecraftServer server, ServerPlayer starter) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : participants(server)) {
            if (p != starter && !ready.contains(p.getUUID())) names.add(p.getGameProfile().getName());
        }
        return names;
    }

    /** 真正開始遊戲。成功回傳 null。 */
    @Nullable
    private String doStart(MinecraftServer server) {
        if (gameMode != GameManager.GameMode.BATTLE_ROYALE) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
            }
            String error = com.provinggrounds.modes.arena.ArenaManager.start(server, gameMode, participants(server), arenaSettings());
            if (error == null) {
                ready.clear();
                countdownTicks = -1;
                dirty = true;
                ProvingGroundsMod.LOGGER.info("Lobby started {}", gameMode);
            }
            return error;
        }
        MapRegistry reg = MapRegistry.get(server);
        MapRegistry.BRMap m = reg.getMap(mapId);
        if (m == null) {
            Iterator<MapRegistry.BRMap> it = reg.all().iterator();
            if (!it.hasNext()) return "還沒有登記任何地圖（/pg map set）。";
            m = it.next();
        }
        // 觀戰者先切成觀察者模式，BR 開局時就不會把他們算進參賽者
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
        }
        String error = BattleRoyaleMode.get().start(server, m.id, preset, variant);
        if (error == null) {
            ready.clear();
            countdownTicks = -1;
            dirty = true;
            ProvingGroundsMod.LOGGER.info("Lobby started BR on map {}", m.id);
        }
        return error;
    }

    /** 管理員強制結束遊戲：停止 BR（清掉箱子與空投）、還原所有參賽者、回大廳。 */
    private void endGame(ServerPlayer sender) {
        if (GameManager.get().isInLobby()) { deny(sender, "目前沒有進行中的遊戲。"); return; }
        MinecraftServer server = sender.server;
        broadcastChat(server, Component.literal("[PG] 管理員 " + sender.getGameProfile().getName() + " 強制結束了遊戲，全員回到大廳。")
            .withStyle(ChatFormatting.GOLD));
        BattleRoyaleMode.get().forceStop(true);
        GameManager.get().reset();
        ProvingGroundsMod.LOGGER.info("Game force-ended from menu by {}", sender.getGameProfile().getName());
        dirty = true;
    }

    private void joinGame(ServerPlayer p, boolean lobby) {
        if (!isSpectator(p)) return;
        if (lobby) {
            leaveSpectate(p);
        } else {
            p.getPersistentData().putBoolean(JOIN_AFTER_TAG, true);
            p.sendSystemMessage(Component.literal("[PG] 這局結束後你會回到大廳（冒險模式）。").withStyle(ChatFormatting.GREEN));
        }
        dirty = true;
    }

    /** 結束觀戰：冒險模式、傳回大廳（世界出生點）。 */
    private void leaveSpectate(ServerPlayer p) {
        p.getPersistentData().remove(SPECTATOR_TAG);
        p.getPersistentData().remove(JOIN_AFTER_TAG);
        p.setGameMode(GameType.ADVENTURE);
        ServerLevel overworld = p.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        p.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.sendSystemMessage(Component.literal("[PG] 已加入大廳。").withStyle(ChatFormatting.GREEN));
        dirty = true;
    }

    // ── 提示 ────────────────────────────────────────────────────────────────

    private void announceCountdown(MinecraftServer server, int seconds) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(forcedCountdown ? "管理員強制開始，即將開始遊戲" : "全員已準備，即將開始遊戲").withStyle(ChatFormatting.WHITE)));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1f, seconds <= 3 ? 1.6f : 1.0f);
        }
    }

    private static void deny(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal("[PG] " + msg).withStyle(ChatFormatting.RED));
    }

    private static void broadcastChat(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void notifyOps(MinecraftServer server, String msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) deny(p, msg);
        }
        ProvingGroundsMod.LOGGER.warn(msg);
    }

    // ── 大廳預覽地圖與同步封包 ──────────────────────────────────────────────

    private int resolveMapId(MinecraftServer server) {
        int map = mapId;
        if (MapRegistry.get(server).getMap(map) == null) {
            Iterator<MapRegistry.BRMap> it = MapRegistry.get(server).all().iterator();
            map = it.hasNext() ? it.next().id : -1;
        }
        return map;
    }

    /** 大廳目前為大逃殺模式時，將選定地圖的俯視圖送給指定玩家預覽（沒有圖則清空客戶端舊圖）。 */
    public void sendLobbyMapImage(ServerPlayer p) {
        if (!GameManager.get().isInLobby() || gameMode != GameManager.GameMode.BATTLE_ROYALE) return;
        int id = resolveMapId(p.server);
        if (id < 0) return;
        com.provinggrounds.data.MapImageStore.MapImage img = com.provinggrounds.data.MapImageStore.get(p.server, id);
        if (img != null) com.provinggrounds.network.MapImagePacket.sendTo(p, img);
        else PGNetwork.sendTo(p, new com.provinggrounds.network.MapImagePacket(id, 0, 0, 0, 0, 1, 0, 1, new byte[0]));
        MapRegistry.BRMap map = MapRegistry.get(p.server).getMap(id);
        if (map != null) com.provinggrounds.network.MapLabelsPacket.sendTo(p, map);
    }

    /** 大廳切換地圖或切回大逃殺模式時，同步推播目前大逃殺俯視圖給全部玩家。 */
    public void broadcastLobbyMapImage(MinecraftServer server) {
        if (!GameManager.get().isInLobby() || gameMode != GameManager.GameMode.BATTLE_ROYALE) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendLobbyMapImage(p);
        }
    }

    public LobbyStatePacket buildPacket(MinecraftServer server) {
        List<LobbyStatePacket.Entry> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            TeamManager.Team t = TeamManager.get().getTeam(p).orElse(null);
            players.add(new LobbyStatePacket.Entry(p.getUUID(), p.getGameProfile().getName(),
                t == null ? -1 : t.id, ready.contains(p.getUUID()), isSpectator(p), isJoinPending(p), p.hasPermissions(2),
                chosenClass(p.getUUID()).ordinal(), draftDone.contains(p.getUUID())));
        }
        int map = resolveMapId(server);
        return new LobbyStatePacket(GameManager.get().isInLobby(), map, preset.ordinal(), variant.ordinal(),
            teamMode.ordinal(), teamCount, countdownTicks < 0 ? -1 : (countdownTicks + 19) / 20, draftPhase,
            com.provinggrounds.special.SpecialConfig.enabledSafe(), friendlyFire(), com.provinggrounds.core.BlockProtection.explosionMode(), weaponMode.ordinal(),
            gameMode.ordinal(), arenaSettings(), customCircle(preset, mapRadius(server, map)),
            com.provinggrounds.modes.arena.ArenaMapRegistry.get(server).activeId(), players);
    }
}
