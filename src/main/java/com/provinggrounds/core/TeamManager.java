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
 * 目的: 隊伍分配管理。紅/藍/綠/黃固定隊伍（人數無上限）與孤狼（每人一隊）。
 *       開局時同步建立原版計分板隊伍（pg_red、pg_solo_1…），設定「對其他隊伍隱藏名牌」，
 *       讓只有同隊玩家看得到彼此的名條。
 */
public class TeamManager {

    public enum Team {
        RED(ChatFormatting.RED, "紅隊"),
        BLUE(ChatFormatting.BLUE, "藍隊"),
        GREEN(ChatFormatting.GREEN, "綠隊"),
        YELLOW(ChatFormatting.YELLOW, "黃隊"),
        SOLO(ChatFormatting.GRAY, "孤狼");

        public final ChatFormatting color;
        public final String displayName;

        Team(ChatFormatting color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
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

    /** 自動平衡分配（紅藍兩隊，人數少的隊先加）。 */
    public void autoAssign(ServerPlayer player) {
        long redCount  = playerTeams.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = playerTeams.values().stream().filter(t -> t == Team.BLUE).count();
        assignTeam(player, (redCount <= blueCount) ? Team.RED : Team.BLUE);
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
        return t == Team.SOLO ? "solo:" + id : t.name();
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
            String name = t == Team.SOLO ? VANILLA_PREFIX + "solo_" + (soloIndex++) : VANILLA_PREFIX + t.name().toLowerCase();
            PlayerTeam team = sb.getPlayerTeam(name);
            if (team == null) {
                team = sb.addPlayerTeam(name);
                team.setDisplayName(Component.literal(t.displayName));
                team.setColor(t.color);
                team.setNameTagVisibility(Visibility.HIDE_FOR_OTHER_TEAMS);
                team.setAllowFriendlyFire(false);
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
