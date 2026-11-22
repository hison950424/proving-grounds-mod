package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: MapRenderJob.java
 * 目的: 產生地圖俯視圖（/pg map render <地圖>），給大地圖與小地圖用。
 *   跟原版地圖物品一樣：每一格取最上面那個方塊的地圖顏色，依跟北邊的高低差加明暗，水依深度加明暗。
 *   範圍：地圖圓圈外加 16 格。地圖半徑 512 以內每格 1 個方塊，更大的地圖會降解析度（每格 2、3… 個方塊）。
 *   區塊要載入才讀得到，所以跟 MarkerScanJob 一樣分批：加載入票 → 等載入完成 → 讀取 → 移除載入票。
 *   載入票不會寫進存檔。只在大廳執行；產生期間不能開局。
 * 執行者：伺服器（WorldEvents 每 tick 呼叫 tickCurrent()）。
 */
public final class MapRenderJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("provinggrounds_maprender", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 1;
    private static final int BATCH_SIDE = 8;
    private static final int BATCH_TIMEOUT = 400;
    private static final int MARGIN = 16;
    private static final int MAX_SIZE = 1024;

    @Nullable private static MapRenderJob current;

    private final ServerLevel level;
    private final MapRegistry.BRMap map;
    private final CommandSourceStack source;
    private final long startMs = System.currentTimeMillis();
    private final int minX, minZ, size, scale;
    private final double reach; // 圓圈 + 邊界
    private final byte[] color;  // 原版地圖顏色 id（0 = 沒資料）
    private final short[] height;
    private final byte[] water;  // 水深（0 = 不是水）
    private final Deque<List<ChunkPos>> batches = new ArrayDeque<>();
    private final int totalBatches;
    @Nullable private List<ChunkPos> active;
    private int waited, timeouts;

    private MapRenderJob(ServerLevel level, MapRegistry.BRMap map, CommandSourceStack source) {
        this.level = level;
        this.map = map;
        this.source = source;
        this.reach = map.radius + MARGIN;
        this.scale = Math.max(1, (int) Math.ceil(reach * 2 / MAX_SIZE));
        this.minX = (int) Math.floor(map.centerX - reach);
        this.minZ = (int) Math.floor(map.centerZ - reach);
        this.size = (int) Math.ceil(reach * 2 / scale) + 1;
        this.color = new byte[size * size];
        this.height = new short[size * size];
        this.water = new byte[size * size];

        int minCX = minX >> 4, maxCX = (minX + size * scale) >> 4;
        int minCZ = minZ >> 4, maxCZ = (minZ + size * scale) >> 4;
        for (int bx = minCX; bx <= maxCX; bx += BATCH_SIDE) {
            for (int bz = minCZ; bz <= maxCZ; bz += BATCH_SIDE) {
                List<ChunkPos> batch = new ArrayList<>();
                for (int cx = bx; cx < bx + BATCH_SIDE && cx <= maxCX; cx++) {
                    for (int cz = bz; cz < bz + BATCH_SIDE && cz <= maxCZ; cz++) {
                        if (chunkTouchesCircle(cx, cz)) batch.add(new ChunkPos(cx, cz));
                    }
                }
                if (!batch.isEmpty()) batches.add(batch);
            }
        }
        totalBatches = batches.size();
    }

    private boolean chunkTouchesCircle(int cx, int cz) {
        double nx = Math.max(cx << 4, Math.min(map.centerX, (cx << 4) + 16));
        double nz = Math.max(cz << 4, Math.min(map.centerZ, (cz << 4) + 16));
        double dx = nx - map.centerX, dz = nz - map.centerZ;
        return dx * dx + dz * dz <= reach * reach;
    }

    // ── 對外 ────────────────────────────────────────────────────────────────

    public static boolean isRunning() { return current != null; }

    @Nullable
    public static String start(MinecraftServer server, MapRegistry.BRMap map, CommandSourceStack source) {
        if (current != null) return "已經有地圖在產生中，請等它完成（或 /pg map render cancel）。";
        if (MarkerScanJob.isRunning()) return "舊箱子 marker 掃描進行中，請等它完成。";
        current = new MapRenderJob(server.overworld(), map, source);
        current.say(String.format("開始產生地圖 #%d 的俯視圖（%d × %d，每格 %d 個方塊），共 %d 批區塊，請稍候…",
            map.id, current.size, current.size, current.scale, current.totalBatches), ChatFormatting.YELLOW);
        return null;
    }

    public static boolean cancel() {
        if (current == null) return false;
        current.releaseActive();
        current.say("已取消地圖產生。", ChatFormatting.YELLOW);
        current = null;
        return true;
    }

    public static void reset() {
        if (current != null) current.releaseActive();
        current = null;
    }

    public static void tickCurrent() {
        if (current != null && current.tick()) current = null;
    }

    // ── 每 tick ─────────────────────────────────────────────────────────────

    private boolean tick() {
        if (active == null) {
            if (batches.isEmpty()) {
                finish();
                return true;
            }
            active = batches.poll();
            waited = 0;
            for (ChunkPos c : active) level.getChunkSource().addRegionTicket(TICKET, c, TICKET_DISTANCE, c);
            return false;
        }
        waited++;
        boolean loaded = true;
        for (ChunkPos c : active) {
            if (level.getChunkSource().getChunkNow(c.x, c.z) == null) { loaded = false; break; }
        }
        if (!loaded && waited < BATCH_TIMEOUT) return false;
        if (!loaded) timeouts++;

        for (ChunkPos c : active) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(c.x, c.z);
            if (chunk != null) sample(chunk);
        }
        releaseActive();

        int done = totalBatches - batches.size();
        if (done % 20 == 0 && !batches.isEmpty()) {
            say(String.format("地圖產生進度 %d / %d 批", done, totalBatches), ChatFormatting.GRAY);
        }
        return false;
    }

    /** 讀取一個區塊裡落在圖片上的每一格。 */
    private void sample(LevelChunk chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        for (int lx = 0; lx < 16; lx++) {
            int wx = baseX + lx;
            if (Math.floorMod(wx - minX, scale) != 0) continue;
            int px = (wx - minX) / scale;
            if (px < 0 || px >= size) continue;
            for (int lz = 0; lz < 16; lz++) {
                int wz = baseZ + lz;
                if (Math.floorMod(wz - minZ, scale) != 0) continue;
                int pz = (wz - minZ) / scale;
                if (pz < 0 || pz >= size) continue;
                double dx = wx - map.centerX, dz = wz - map.centerZ;
                if (dx * dx + dz * dz > reach * reach) continue;

                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) + 1;
                BlockState state;
                MapColor mc;
                do {
                    y--;
                    pos.set(wx, y, wz);
                    state = chunk.getBlockState(pos);
                    mc = state.getMapColor(level, pos);
                } while (mc == MapColor.NONE && y > minY);

                int depth = 0;
                if (!state.getFluidState().isEmpty()) {
                    int wy = y;
                    BlockState below;
                    do {
                        wy--;
                        depth++;
                        below = chunk.getBlockState(pos.set(wx, wy, wz));
                    } while (wy > minY && depth < 20 && !below.getFluidState().isEmpty());
                }
                int i = pz * size + px;
                color[i] = (byte) mc.id;
                height[i] = (short) y;
                water[i] = (byte) depth;
            }
        }
    }

    /** 加明暗、壓縮存檔。 */
    private void finish() {
        byte[] pixels = new byte[size * size];
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                int i = pz * size + px;
                int id = color[i] & 0xFF;
                if (id == 0) continue;
                MapColor.Brightness b;
                if (water[i] > 0) {
                    double d = water[i] * 0.1 + ((px + pz) & 1) * 0.2;
                    b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    int north = pz > 0 && color[i - size] != 0 ? height[i - size] : height[i];
                    double d = (height[i] - north) * 4.0 / (scale + 4) + (((px + pz) & 1) - 0.5) * 0.4;
                    b = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                pixels[i] = (byte) (id * 4 + b.id);
            }
        }
        MapImageStore.MapImage img = MapImageStore.save(level.getServer(), map.id, minX, minZ, size, scale, pixels);
        say(String.format("地圖 #%d 俯視圖產生完成（%d × %d、壓縮後 %d KB、耗時 %s）。下一局開始時會傳給玩家。",
            map.id, size, size, img.data().length / 1024, elapsed()),
            ChatFormatting.GREEN);
        if (timeouts > 0) {
            say("注意：有 " + timeouts + " 批區塊等太久沒載入，地圖上那些位置會是空白，可以再執行一次。", ChatFormatting.RED);
        }
        ProvingGroundsMod.LOGGER.info("Map image {} rendered: size={} scale={} bytes={} timeouts={}",
            map.id, size, scale, img.data().length, timeouts);
    }

    private void releaseActive() {
        if (active == null) return;
        for (ChunkPos c : active) level.getChunkSource().removeRegionTicket(TICKET, c, TICKET_DISTANCE, c);
        active = null;
    }

    /** 從開始到現在經過的時間（例如「2 分 05 秒」）。 */
    private String elapsed() {
        long s = (System.currentTimeMillis() - startMs) / 1000;
        return s >= 60 ? (s / 60) + " 分 " + String.format("%02d", s % 60) + " 秒" : s + " 秒";
    }

    private void say(String msg, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal("[PG] " + msg).withStyle(color), false);
    }
}
