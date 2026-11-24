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
        SET_WEAPON_MODE,    // 管理員：槍械模式（arg = WeaponMode 的順序）
        GOTO_VENUE,         // 自己：傳送到場地（arg = 場地選單的順序）
        SET_GAME_MODE,      // 管理員：選模式（arg = GameManager.GameMode 的順序）
        SET_TDM_KILLS, SET_TDM_MINUTES, SET_DOM_SCORE, SET_GK_MULTIPLIER // 管理員：對戰設定（arg = 新的值）
    }

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
    public com.provinggrounds.modes.arena.ArenaManager.Settings arenaSettings() {
        var c = PGConfig.SERVER.arena;
        return new com.provinggrounds.modes.arena.ArenaManager.Settings(
            tdmKills > 0 ? tdmKills : c.tdmKills.get(), tdmMinutes > 0 ? tdmMinutes : c.tdmMinutes.get(),
            domScore > 0 ? domScore : c.domScore.get(), gkMultiplier > 0 ? gkMultiplier : c.gkMultiplier.get());
    }

    /** 槍械模式（這一場只出某一類的槍）。 */
    public WeaponMode weaponMode() { return weaponMode; }
    private final Set<UUID> ready = new HashSet<>();
    private int countdownTicks = -1; // 強制開始倒數，-1 = 沒在倒數
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

    public void tick(MinecraftServer server) {
        if (countdownTicks >= 0) {
            if (!GameManager.get().isInLobby()) {
                countdownTicks = -1;
                dirty = true;
            } else {
                if (countdownTicks % 20 == 0 && countdownTicks > 0) announceCountdown(server, countdownTicks / 20);
                if (countdownTicks == 0) {
                    countdownTicks = -1;
                    String error = doStart(server);
                    if (error != null) notifyOps(server, "強制開始失敗：" + error);
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

    // ── 玩家進出、回大廳 ────────────────────────────────────────────────────

    public void onLogin(ServerPlayer p) {
        if (GameManager.get().isInLobby()) {
            if (isJoinPending(p)) leaveSpectate(p);
            else if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
        }
        dirty = true;
    }

    public void onLogout(ServerPlayer p) {
        ready.remove(p.getUUID());
        dirty = true;
    }

    /** 一局結束回到大廳（GameManager.reset 呼叫）：處理「本局結束後加入」的觀戰者。 */
    public void onReturnToLobby(@Nullable MinecraftServer server) {
        ready.clear();
        countdownTicks = -1;
        dirty = true;
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isJoinPending(p)) leaveSpectate(p);
            else if (isSpectator(p)) p.setGameMode(GameType.SPECTATOR);
        }
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
                TeamManager.get().assignTeam(sender, TeamManager.Team.of(arg));
            }
            case TOGGLE_READY -> {
                if (isSpectator(sender)) return;
                if (!ready.remove(sender.getUUID())) ready.add(sender.getUUID());
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
            case SET_WEAPON_MODE -> weaponMode = WeaponMode.byOrdinal(arg);
            case SET_GAME_MODE -> {
                GameManager.GameMode[] modes = GameManager.GameMode.values();
                if (arg <= 0 || arg >= modes.length) return false; // 0 = NONE
                gameMode = modes[arg];
                if (gameMode == GameManager.GameMode.TDM || gameMode == GameManager.GameMode.DOM) teamCount = 2; // 紅藍兩隊
            }
            case SET_TDM_KILLS -> tdmKills = Math.max(10, Math.min(10_000, arg));
            case SET_TDM_MINUTES -> tdmMinutes = Math.max(1, Math.min(120, arg));
            case SET_DOM_SCORE -> domScore = Math.max(100, Math.min(100_000, arg));
            case SET_GK_MULTIPLIER -> gkMultiplier = Math.max(1, Math.min(20, arg));
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
                List<String> notReady = notReady(server, sender);
                if (!notReady.isEmpty()) {
                    deny(sender, "還有人沒準備：" + String.join("、", notReady) + "。要直接開始請按「強制開始」。");
                    return false;
                }
                String error = doStart(server);
                if (error != null) { deny(sender, error); return false; }
            }
            case FORCE_START -> {
                if (countdownTicks >= 0) return false;
                int sec = PGConfig.SERVER.forceStartSeconds.get();
                countdownTicks = sec * 20;
                broadcastChat(server, Component.literal("[PG] 管理員強制開始！" + sec + " 秒後開始遊戲。").withStyle(ChatFormatting.GOLD));
            }
            case CANCEL_COUNTDOWN -> {
                if (countdownTicks < 0) return false;
                countdownTicks = -1;
                broadcastChat(server, Component.literal("[PG] 管理員取消了開始倒數。").withStyle(ChatFormatting.YELLOW));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                }
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
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("管理員強制開始，即將開始遊戲").withStyle(ChatFormatting.WHITE)));
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

    // ── 同步封包 ────────────────────────────────────────────────────────────

    public LobbyStatePacket buildPacket(MinecraftServer server) {
        List<LobbyStatePacket.Entry> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            TeamManager.Team t = TeamManager.get().getTeam(p).orElse(null);
            players.add(new LobbyStatePacket.Entry(p.getUUID(), p.getGameProfile().getName(),
                t == null ? -1 : t.id, ready.contains(p.getUUID()), isSpectator(p), isJoinPending(p), p.hasPermissions(2)));
        }
        int map = mapId;
        if (MapRegistry.get(server).getMap(map) == null) {
            Iterator<MapRegistry.BRMap> it = MapRegistry.get(server).all().iterator();
            map = it.hasNext() ? it.next().id : -1;
        }
        return new LobbyStatePacket(GameManager.get().isInLobby(), map, preset.ordinal(), variant.ordinal(),
            teamMode.ordinal(), teamCount, countdownTicks < 0 ? -1 : (countdownTicks + 19) / 20,
            com.provinggrounds.special.SpecialConfig.enabledSafe(), weaponMode.ordinal(),
            gameMode.ordinal(), arenaSettings(), players);
    }
}
