package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team.Visibility;

import java.util.*;

/**
 * 檔案: TeamManager.java
 * 目的: 隊伍分配管理。隊伍數量與每隊人數都沒有上限，另有孤狼（每人一隊）。
 *       開局時同步建立原版計分板隊伍（pg_team_1、pg_solo_1…），設定「對其他隊伍隱藏名牌」，
 *       讓只有同隊玩家看得到彼此的名條。
 */
public class TeamManager {

    /**
     * 隊伍。編號 1、2、3… 沒有上限；0 = 孤狼（每人自成一隊）。
     * 前 8 隊有固定顏色與名稱（紅藍綠黃青粉橙白），之後顏色循環、名稱為「第 N 隊」。
     * 同一個編號永遠是同一個物件，所以可以直接用 == 比較。
     */
    public static final class Team {
        private static final ChatFormatting[] COLORS = {
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.WHITE,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_PURPLE };
        private static final String[] NAMES = { "紅隊", "藍隊", "綠隊", "黃隊", "青隊", "粉隊", "橙隊", "白隊" };
        private static final Map<Integer, Team> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

        public static final Team SOLO = new Team(0, ChatFormatting.GRAY, "孤狼");

        public final int id;
        public final ChatFormatting color;
        public final String displayName;

        private Team(int id, ChatFormatting color, String displayName) {
            this.id = id;
            this.color = color;
            this.displayName = displayName;
        }

        /** 取得第 n 隊（n <= 0 為孤狼）。 */
        public static Team of(int n) {
            if (n <= 0) return SOLO;
            return CACHE.computeIfAbsent(n, k -> new Team(k, COLORS[(k - 1) % COLORS.length],
                k <= NAMES.length ? NAMES[k - 1] : "第 " + k + " 隊"));
        }

        /** 指令用名稱：solo、red/blue/green/yellow 或數字。認不得回傳 null。 */
        @javax.annotation.Nullable
        public static Team parse(String s) {
            String v = s.toLowerCase(java.util.Locale.ROOT);
            switch (v) {
                case "solo": return SOLO;
                case "red": return of(1);
                case "blue": return of(2);
                case "green": return of(3);
                case "yellow": return of(4);
                default:
                    try { return of(Integer.parseInt(v)); } catch (NumberFormatException e) { return null; }
            }
        }

        @Override
        public String toString() { return id == 0 ? "SOLO" : "TEAM_" + id; }
    }

    private static final String VANILLA_PREFIX = "pg_";

    private static final TeamManager INSTANCE = new TeamManager();
    public static TeamManager get() { return INSTANCE; }

    private final Map<UUID, Team> playerTeams = new HashMap<>();

    private TeamManager() {}

    // ── 分配 ─────────────────────────────────────────────────────────────────

    public void assignTeam(ServerPlayer player, Team team) {
        playerTeams.put(player.getUUID(), team);
        ProvingGroundsMod.LOGGER.info("Player {} assigned to team {}", player.getName().getString(), team);
    }

    public void unassign(ServerPlayer player) {
        playerTeams.remove(player.getUUID());
    }

    /** 把玩家隨機平均分到 1..teamCount 隊（管理員按「自動分隊」）。 */
    public void balance(List<ServerPlayer> players, int teamCount) {
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) assignTeam(shuffled.get(i), Team.of(i % Math.max(1, teamCount) + 1));
    }

    public Optional<Team> getTeam(ServerPlayer player) {
        return Optional.ofNullable(playerTeams.get(player.getUUID()));
    }

    public Team getTeamOrSolo(UUID id) {
        return playerTeams.getOrDefault(id, Team.SOLO);
    }

    public boolean isSameTeam(ServerPlayer a, ServerPlayer b) {
        return isSameTeam(a.getUUID(), b.getUUID());
    }

    public boolean isSameTeam(UUID a, UUID b) {
        if (a.equals(b)) return true;
        Team ta = playerTeams.get(a);
        Team tb = playerTeams.get(b);
        return ta != null && ta == tb && ta != Team.SOLO;
    }

    /** 隊伍識別字串：固定隊伍用隊名，孤狼用 UUID（每人一隊）。用於計算存活隊伍數。 */
    public String teamKey(UUID id) {
        Team t = getTeamOrSolo(id);
        return t == Team.SOLO ? "solo:" + id : "team:" + t.id;
    }

    public List<ServerPlayer> getTeamPlayers(Team team, MinecraftServer server) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (playerTeams.get(p.getUUID()) == team) result.add(p);
        }
        return result;
    }

    // ── 原版計分板隊伍同步 ──────────────────────────────────────────────────────

    /** 為參賽者建立原版隊伍並加入。孤狼每人一個 pg_solo_N。 */
    public void syncVanillaTeams(MinecraftServer server, Collection<ServerPlayer> players) {
        Scoreboard sb = server.getScoreboard();
        removeVanillaTeams(server);
        int soloIndex = 1;
        for (ServerPlayer p : players) {
            Team t = getTeamOrSolo(p.getUUID());
            String name = t == Team.SOLO ? VANILLA_PREFIX + "solo_" + (soloIndex++) : VANILLA_PREFIX + "team_" + t.id;
            PlayerTeam team = sb.getPlayerTeam(name);
            if (team == null) {
                team = sb.addPlayerTeam(name);
                team.setDisplayName(Component.literal(t.displayName));
                team.setColor(t.color);
                team.setNameTagVisibility(Visibility.HIDE_FOR_OTHER_TEAMS);
                team.setAllowFriendlyFire(true); // 友軍誤傷改由 FriendlyFireEvents 精細控管（避免原版擋掉自己與隊友的爆炸／投擲物）
                team.setSeeFriendlyInvisibles(true);
            }
            sb.addPlayerToTeam(p.getScoreboardName(), team);
        }
    }

    /** 移除所有 pg_ 開頭的原版隊伍（結束、重置、崩潰後重啟都會呼叫）。 */
    public void removeVanillaTeams(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        for (PlayerTeam team : new ArrayList<>(sb.getPlayerTeams())) {
            if (team.getName().startsWith(VANILLA_PREFIX)) sb.removePlayerTeam(team);
        }
    }

    /**
     * 遊戲重置時呼叫：移除原版隊伍，但保留玩家選好的隊伍，
     * 下一局不用重新分隊。要清空分隊用 {@link #clearAssignments()}。
     */
    public void reset() {
        MinecraftServer server = GameManager.get().getServer();
        if (server != null) removeVanillaTeams(server);
        ProvingGroundsMod.LOGGER.info("TeamManager reset.");
    }

    public void clearAssignments() {
        playerTeams.clear();
    }
}
