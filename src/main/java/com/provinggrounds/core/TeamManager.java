package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 隊伍分配管理。支援紅/藍固定隊伍和孤狼模式。
 */
public class TeamManager {

    public enum Team { RED, BLUE, SOLO }

    private static final TeamManager INSTANCE = new TeamManager();
    public static TeamManager get() { return INSTANCE; }

    private final Map<UUID, Team> playerTeams = new HashMap<>();

    private TeamManager() {}

    // ── Assignment ────────────────────────────────────────────────────────────

    public void assignTeam(ServerPlayer player, Team team) {
        playerTeams.put(player.getUUID(), team);
        ProvingGroundsMod.LOGGER.info("Player {} assigned to team {}", player.getName().getString(), team);
    }

    /** 自動平衡分配（人數少的隊先加）。 */
    public void autoAssign(ServerPlayer player) {
        long redCount  = playerTeams.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = playerTeams.values().stream().filter(t -> t == Team.BLUE).count();
        Team assigned  = (redCount <= blueCount) ? Team.RED : Team.BLUE;
        assignTeam(player, assigned);
    }

    public Optional<Team> getTeam(ServerPlayer player) {
        return Optional.ofNullable(playerTeams.get(player.getUUID()));
    }

    public boolean isSameTeam(ServerPlayer a, ServerPlayer b) {
        Team ta = playerTeams.get(a.getUUID());
        Team tb = playerTeams.get(b.getUUID());
        return ta != null && ta == tb && ta != Team.SOLO;
    }

    public List<ServerPlayer> getTeamPlayers(Team team, net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (playerTeams.getOrDefault(p.getUUID(), null) == team) {
                result.add(p);
            }
        }
        return result;
    }

    public void reset() {
        playerTeams.clear();
        ProvingGroundsMod.LOGGER.info("TeamManager reset.");
    }
}
