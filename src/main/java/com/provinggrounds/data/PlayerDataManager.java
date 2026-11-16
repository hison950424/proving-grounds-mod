package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 跨局玩家資料管理（K/D、勝場、等級）。
 * 目前使用記憶體 Map；之後整合 SavedData 持久化到 NBT。
 */
public class PlayerDataManager {

    private static final PlayerDataManager INSTANCE = new PlayerDataManager();
    public static PlayerDataManager get() { return INSTANCE; }

    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    private PlayerDataManager() {}

    public void loadPlayer(ServerPlayer player) {
        stats.computeIfAbsent(player.getUUID(), id -> {
            ProvingGroundsMod.LOGGER.debug("Creating stats entry for {}", player.getName().getString());
            return new PlayerStats();
        });
    }

    public void addKill(ServerPlayer player) {
        getStats(player).kills++;
    }

    public void addDeath(ServerPlayer player) {
        getStats(player).deaths++;
    }

    public void addWin(ServerPlayer player) {
        getStats(player).wins++;
    }

    public PlayerStats getStats(ServerPlayer player) {
        return stats.computeIfAbsent(player.getUUID(), id -> new PlayerStats());
    }

    public static class PlayerStats {
        public int kills  = 0;
        public int deaths = 0;
        public int wins   = 0;
        public int level  = 1;

        public double getKD() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        @Override
        public String toString() {
            return String.format("K=%d D=%d W=%d KD=%.2f Lv=%d", kills, deaths, wins, getKD(), level);
        }
    }
}
