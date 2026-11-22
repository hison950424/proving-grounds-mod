package com.provinggrounds.modes.br;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.StatsLeaderboardPacket;
import com.provinggrounds.network.StatsPersonalPacket;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: StatsBoard.java
 * 目的: 數據看板的伺服器端（計畫書 B5）。
 *   - 個人數據：玩家登入、打完一場、升級時，各送一次「他自己」的大逃殺累計數據給本人（StatsPersonalPacket）。
 *   - 全服排行：資料變動時標記，最多每 10 秒重算一次（所有看板共用一份），
 *     只送給站在任一看板 48 格內的玩家；同一份排行對同一個人只送一次（StatsLeaderboardPacket）。
 *   - 擺放與破壞：只有 OP（權限 2）或創造模式可以。
 *   資料來源是 PlayerDataManager 的 BR ModeStats；等級用累計 XP 計算。離線玩家依最後使用的名稱列入排行。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class StatsBoard {

    private static final int INTERVAL_TICKS = 200;
    private static final double NEAR = 48;
    private static final int MAX_ENTRIES = 200;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> BOARDS = new HashMap<>();
    private static final Map<UUID, Integer> sentVersion = new HashMap<>();
    private static boolean dirty = true;
    private static int version;
    private static StatsLeaderboardPacket cached = new StatsLeaderboardPacket(List.of());
    private static int tick;

    private StatsBoard() {}

    // ── 看板位置 ─────────────────────────────────────────────────────────────

    public static void register(ResourceKey<Level> dim, BlockPos pos) {
        BOARDS.computeIfAbsent(dim, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(ResourceKey<Level> dim, BlockPos pos) {
        Set<BlockPos> set = BOARDS.get(dim);
        if (set != null) set.remove(pos);
    }

    private static boolean nearBoard(ServerPlayer p) {
        Set<BlockPos> set = BOARDS.get(p.level().dimension());
        if (set == null) return false;
        for (BlockPos b : set) {
            if (p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) <= NEAR * NEAR) return true;
        }
        return false;
    }

    // ── 資料變動 ─────────────────────────────────────────────────────────────

    /** 有人的數據變了（打完一場、升級）：排行下次重算。 */
    public static void onStatsChanged() {
        dirty = true;
    }

    /** 右鍵看板：下一次同步時重送排行給這個人（例如他剛好錯過）。 */
    public static void markNear(Player p) {
        sentVersion.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % INTERVAL_TICKS != 0) return;
        MinecraftServer server = event.getServer();
        if (BOARDS.values().stream().allMatch(Set::isEmpty)) return;
        if (dirty) {
            rebuild();
            dirty = false;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Integer v = sentVersion.get(p.getUUID());
            if ((v == null || v != version) && nearBoard(p)) {
                PGNetwork.sendTo(p, cached);
                sentVersion.put(p.getUUID(), version);
            }
        }
    }

    private static void rebuild() {
        List<StatsLeaderboardPacket.Entry> list = entries();
        list.sort(BY_WINS);
        cached = new StatsLeaderboardPacket(List.copyOf(list.subList(0, Math.min(MAX_ENTRIES, list.size()))));
        version++;
    }

    private static final Comparator<StatsLeaderboardPacket.Entry> BY_WINS = Comparator
        .comparingInt(StatsLeaderboardPacket.Entry::wins).reversed()
        .thenComparing(Comparator.comparingInt(StatsLeaderboardPacket.Entry::kills).reversed());
    private static final Comparator<StatsLeaderboardPacket.Entry> BY_KILLS = Comparator
        .comparingInt(StatsLeaderboardPacket.Entry::kills).reversed()
        .thenComparing(Comparator.comparingInt(StatsLeaderboardPacket.Entry::wins).reversed());

    /** 所有有名字的玩家（含離線）的 BR 數據。 */
    private static List<StatsLeaderboardPacket.Entry> entries() {
        List<StatsLeaderboardPacket.Entry> list = new ArrayList<>();
        PlayerDataManager.get().all().forEach((id, s) -> {
            if (s.lastName.isEmpty()) return;
            PlayerDataManager.ModeStats m = s.modes.get(PlayerDataManager.MODE_BR);
            if (m == null) m = new PlayerDataManager.ModeStats();
            list.add(new StatsLeaderboardPacket.Entry(id, s.lastName, m.wins, m.games, m.kills, m.deaths, m.assists, s.getLevel()));
        });
        return list;
    }

    // ── 個人數據 ─────────────────────────────────────────────────────────────

    /** 送玩家自己的數據給本人（登入、打完一場、升級時）。 */
    public static void sendPersonal(ServerPlayer p) {
        PlayerDataManager.PlayerStats s = PlayerDataManager.get().getStats(p);
        PlayerDataManager.ModeStats m = s.mode(PlayerDataManager.MODE_BR);
        List<StatsLeaderboardPacket.Entry> all = entries();
        int level = s.getLevel();
        long toNext = Math.max(0, PlayerDataManager.xpForNextLevel(level) - s.xp);
        PGNetwork.sendTo(p, new StatsPersonalPacket(p.getGameProfile().getName(), level, s.xp, toNext,
            m.games, m.wins, m.kills, m.deaths, m.assists, m.bestKills, rank(all, BY_WINS, p.getUUID()), rank(all, BY_KILLS, p.getUUID())));
    }

    private static int rank(List<StatsLeaderboardPacket.Entry> all, Comparator<StatsLeaderboardPacket.Entry> order, UUID id) {
        List<StatsLeaderboardPacket.Entry> sorted = new ArrayList<>(all);
        sorted.sort(order);
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id().equals(id)) return i + 1;
        return 0;
    }

    /** 打完一場：所有線上參與者更新個人數據，排行重算。 */
    public static void afterMatch(Iterable<UUID> participants) {
        onStatsChanged();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sendPersonal(p);
        }
    }

    // ── 事件 ────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sendPersonal(p);
            sentVersion.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sentVersion.remove(event.getEntity().getUUID());
    }

    private static boolean admin(Player p) {
        return p.isCreative() || p.hasPermissions(2);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(PGBlocks.STATS_BOARD.get()) && event.getEntity() instanceof Player p && !admin(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(PGBlocks.STATS_BOARD.get()) && !admin(event.getPlayer())) event.setCanceled(true);
    }

    /** 伺服器關閉：清掉記憶體裡的看板位置。 */
    public static void clear() {
        BOARDS.clear();
        sentVersion.clear();
        dirty = true;
    }
}
