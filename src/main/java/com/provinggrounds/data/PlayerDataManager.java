package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: PlayerDataManager.java
 * 目的: 跨局玩家資料（擊殺、死亡、助攻、勝場、累計 XP、等級）。
 *       以 Forge SavedData 存成 world/data/provinggrounds_players.dat，伺服器崩潰後可讀回。
 *       等級門檻：累計 XP 達 base 升 Lv2，之後每級多 step（Lv N 門檻 = base + step × (N−2)）。
 *       XP 只增不減。「積分」系統尚未定案，這裡不實作。
 */
public class PlayerDataManager {

    public static final String FILE = "provinggrounds_players";

    private static final PlayerDataManager INSTANCE = new PlayerDataManager();
    public static PlayerDataManager get() { return INSTANCE; }

    @Nullable
    private Storage storage;

    private PlayerDataManager() {}

    /** 伺服器啟動時呼叫，從 overworld 的 data storage 取得（或建立）存檔。 */
    public void attach(MinecraftServer server) {
        storage = server.overworld().getDataStorage().computeIfAbsent(Storage::load, Storage::new, FILE);
        ProvingGroundsMod.LOGGER.info("Player data loaded: {} entries.", storage.stats.size());
    }

    public void detach() { storage = null; }

    public void loadPlayer(ServerPlayer player) {
        PlayerStats s = getStats(player.getUUID());
        s.lastName = player.getGameProfile().getName();
        markDirty();
    }

    public void addKill(ServerPlayer player)   { getStats(player).kills++;   markDirty(); }
    public void addDeath(ServerPlayer player)  { getStats(player).deaths++;  markDirty(); }
    public void addAssist(ServerPlayer player) { getStats(player).assists++; markDirty(); }
    public void addWin(ServerPlayer player)    { getStats(player).wins++;    markDirty(); }

    /**
     * 增加 XP。competitive=true 時乘上競技倍率並四捨五入（50→68、10→14、5→7）。
     * 升級時通知玩家。回傳實際增加的 XP。
     */
    public int addXp(ServerPlayer player, int baseXp, boolean competitive) {
        int gained = competitive
            ? (int) Math.round(baseXp * PGConfig.SERVER.xpCompetitiveMultiplier.get())
            : baseXp;
        PlayerStats s = getStats(player);
        int before = levelFor(s.xp);
        s.xp += gained;
        int after = levelFor(s.xp);
        markDirty();
        if (after > before) {
            player.sendSystemMessage(Component.literal("▲ 升級！Lv." + before + " → Lv." + after)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        return gained;
    }

    public PlayerStats getStats(ServerPlayer player) { return getStats(player.getUUID()); }

    public PlayerStats getStats(UUID id) {
        if (storage == null) {
            // 理論上不會發生（伺服器啟動後才有玩家）；退而求其次用暫存物件避免崩潰
            ProvingGroundsMod.LOGGER.warn("PlayerDataManager used before attach(); stats will not persist.");
            return new PlayerStats();
        }
        return storage.stats.computeIfAbsent(id, k -> new PlayerStats());
    }

    private void markDirty() { if (storage != null) storage.setDirty(); }

    // ── 等級計算 ─────────────────────────────────────────────────────────────

    /** 依累計 XP 計算等級。 */
    public static int levelFor(long xp) {
        long base = PGConfig.SERVER.levelBaseXp.get();
        long step = PGConfig.SERVER.levelStepXp.get();
        if (xp < base) return 1;
        return (int) (2 + (xp - base) / step);
    }

    /** 升到 level+1 所需的累計 XP。 */
    public static long xpForNextLevel(int level) {
        long base = PGConfig.SERVER.levelBaseXp.get();
        long step = PGConfig.SERVER.levelStepXp.get();
        return base + step * (level - 1L);
    }

    // ── 資料 ─────────────────────────────────────────────────────────────────

    public static class PlayerStats {
        public int kills = 0;
        public int deaths = 0;
        public int assists = 0;
        public int wins = 0;
        public long xp = 0;
        public String lastName = "";

        public int getLevel() { return levelFor(xp); }

        public double getKD() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("kills", kills);
            t.putInt("deaths", deaths);
            t.putInt("assists", assists);
            t.putInt("wins", wins);
            t.putLong("xp", xp);
            t.putString("name", lastName);
            return t;
        }

        static PlayerStats load(CompoundTag t) {
            PlayerStats s = new PlayerStats();
            s.kills = t.getInt("kills");
            s.deaths = t.getInt("deaths");
            s.assists = t.getInt("assists");
            s.wins = t.getInt("wins");
            s.xp = t.getLong("xp");
            s.lastName = t.getString("name");
            return s;
        }

        @Override
        public String toString() {
            return String.format("K=%d D=%d A=%d W=%d KD=%.2f XP=%d Lv=%d",
                kills, deaths, assists, wins, getKD(), xp, getLevel());
        }
    }

    private static class Storage extends SavedData {
        final Map<UUID, PlayerStats> stats = new HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag players = new CompoundTag();
            stats.forEach((id, s) -> players.put(id.toString(), s.save()));
            tag.put("players", players);
            return tag;
        }

        static Storage load(CompoundTag tag) {
            Storage st = new Storage();
            CompoundTag players = tag.getCompound("players");
            for (String key : players.getAllKeys()) {
                try {
                    st.stats.put(UUID.fromString(key), PlayerStats.load(players.getCompound(key)));
                } catch (IllegalArgumentException e) {
                    ProvingGroundsMod.LOGGER.warn("Skipping invalid UUID in player data: {}", key);
                }
            }
            return st;
        }
    }
}
