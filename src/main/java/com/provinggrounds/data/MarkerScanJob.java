package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 檔案: MarkerScanJob.java
 * 目的: 處理舊 Datapack 留下的箱子 marker（tag = loot_marker，不含空投 crate_airdrop）。
 *   舊 marker 的標籤：loot_marker + crate_general/medical/attach/high + map_1~map_5。
 *   屬於哪張地圖以 map_N 標籤為準；沒有地圖標籤的才用「位置在不在地圖圓圈內」判斷。
 *   IMPORT：把 marker 的位置登記成新系統的箱子點位（只取位置，不管舊的箱子種類）。
 *   PURGE ：刪除 marker。
 *   MIGRATE：/pg loot import 用。先全部匯入並存檔，確認點位真的登記了，再刪掉那些 marker
 *            （沒登記成功的 marker 會留著）。
 * 實體只有在區塊載入後才找得到，所以把地圖範圍切成一批批區塊：
 *   加載入票 → 等實體載入完成 → 掃描 → 移除載入票 → 下一批。
 * 載入票不會寫進存檔，伺服器中途關閉也不會留下常駐載入的區塊。
 * 執行者：伺服器（由 WorldEvents 每 tick 呼叫 tick()）。
 */
public final class MarkerScanJob {

    public enum Mode { IMPORT, PURGE, MIGRATE }

    public static final String MARKER_TAG  = "loot_marker";
    public static final String AIRDROP_TAG = "crate_airdrop";

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("provinggrounds_scan", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 1;
    private static final int BATCH_SIDE = 8;       // 每批 8×8 個區塊
    private static final int BATCH_TIMEOUT = 400;  // 一批最多等 20 秒
    private static final int MARGIN = 64;          // 掃描範圍比地圖半徑多 64 格，順便統計範圍外的 marker

    @Nullable private static MarkerScanJob current;

    private final ServerLevel level;
    private final MapRegistry.BRMap map;
    private final Mode mode;
    private final CommandSourceStack source;
    private final Deque<List<ChunkPos>> batches = new ArrayDeque<>();
    private final List<List<ChunkPos>> allBatches = new ArrayList<>();
    private final int totalBatches;

    @Nullable private List<ChunkPos> active;
    private int waited;
    private int found, imported, duplicate, outside, removed, timeouts;
    private int kept;              // MIGRATE 第二階段：位置沒有對應點位、所以沒刪的 marker
    private boolean deleting;      // MIGRATE 是否已進入第二階段（刪除）

    private MarkerScanJob(ServerLevel level, MapRegistry.BRMap map, Mode mode, CommandSourceStack source) {
        this.level = level;
        this.map = map;
        this.mode = mode;
        this.source = source;
        double r = map.radius + MARGIN;
        int minCX = ((int) Math.floor(map.centerX - r)) >> 4, maxCX = ((int) Math.floor(map.centerX + r)) >> 4;
        int minCZ = ((int) Math.floor(map.centerZ - r)) >> 4, maxCZ = ((int) Math.floor(map.centerZ + r)) >> 4;
        for (int bx = minCX; bx <= maxCX; bx += BATCH_SIDE) {
            for (int bz = minCZ; bz <= maxCZ; bz += BATCH_SIDE) {
                List<ChunkPos> batch = new ArrayList<>();
                for (int cx = bx; cx < bx + BATCH_SIDE && cx <= maxCX; cx++) {
                    for (int cz = bz; cz < bz + BATCH_SIDE && cz <= maxCZ; cz++) {
                        if (chunkTouchesCircle(cx, cz, r)) batch.add(new ChunkPos(cx, cz));
                    }
                }
                if (!batch.isEmpty()) batches.add(batch);
            }
        }
        allBatches.addAll(batches);
        totalBatches = batches.size();
    }

    private boolean chunkTouchesCircle(int cx, int cz, double r) {
        double nx = Math.max(cx << 4, Math.min(map.centerX, (cx << 4) + 16));
        double nz = Math.max(cz << 4, Math.min(map.centerZ, (cz << 4) + 16));
        double dx = nx - map.centerX, dz = nz - map.centerZ;
        return dx * dx + dz * dz <= r * r;
    }

    // ── 對外 ────────────────────────────────────────────────────────────────

    public static boolean isRunning() { return current != null; }

    /** 開始掃描。已有工作在跑時回傳錯誤訊息。 */
    @Nullable
    public static String start(MinecraftServer server, MapRegistry.BRMap map, Mode mode, CommandSourceStack source) {
        if (current != null) return "已經有一個 marker 掃描在進行中，請等它完成（或 /pg loot markers cancel）。";
        if (MapRenderJob.isRunning()) return "地圖俯視圖產生中，請等它完成。";
        current = new MarkerScanJob(server.overworld(), map, mode, source);
        current.say(String.format("開始%s地圖 #%d 的舊箱子 marker，共 %d 批區塊，請稍候…",
            switch (mode) { case IMPORT -> "匯入"; case PURGE -> "刪除"; case MIGRATE -> "轉換（匯入後刪除）"; }, map.id, current.totalBatches), ChatFormatting.YELLOW);
        return null;
    }

    public static boolean cancel() {
        if (current == null) return false;
        current.releaseActive();
        current.say("已取消。" + current.summary(), ChatFormatting.YELLOW);
        current = null;
        return true;
    }

    /** 伺服器關閉時呼叫：放掉載入票，不留狀態。 */
    public static void reset() {
        if (current != null) current.releaseActive();
        current = null;
    }

    public static void tickCurrent() {
        if (current != null && current.tick()) current = null;
    }

    // ── 每 tick ─────────────────────────────────────────────────────────────

    /** 完成時回傳 true。 */
    private boolean tick() {
        if (active == null) {
            if (batches.isEmpty()) return finish();
            active = batches.poll();
            waited = 0;
            for (ChunkPos c : active) level.getChunkSource().addRegionTicket(TICKET, c, TICKET_DISTANCE, c);
            return false;
        }

        waited++;
        boolean loaded = true;
        for (ChunkPos c : active) {
            if (!level.areEntitiesLoaded(c.toLong())) { loaded = false; break; }
        }
        if (!loaded && waited < BATCH_TIMEOUT) return false;
        if (!loaded) timeouts++;

        scan(active);
        releaseActive();

        int done = totalBatches - batches.size();
        if (done % 10 == 0 && !batches.isEmpty()) {
            say(deleting
                ? String.format("刪除進度 %d / %d 批，已刪除 %d 個 marker。", done, totalBatches, removed)
                : String.format("進度 %d / %d 批，目前找到 %d 個 marker。", done, totalBatches, found), ChatFormatting.GRAY);
        }
        return false;
    }

    private void scan(List<ChunkPos> chunks) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            minX = Math.min(minX, c.getMinBlockX()); maxX = Math.max(maxX, c.getMaxBlockX() + 1);
            minZ = Math.min(minZ, c.getMinBlockZ()); maxZ = Math.max(maxZ, c.getMaxBlockZ() + 1);
        }
        AABB box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);
        List<Entity> markers = new ArrayList<>(level.getEntities(EntityType.MARKER, box,
            e -> e.getTags().contains(MARKER_TAG) && !e.getTags().contains(AIRDROP_TAG)));

        MapRegistry registry = MapRegistry.get(level.getServer());
        for (Entity e : markers) {
            // 舊 Datapack 的 marker 帶有 map_1 ~ map_5 標籤，以標籤為準；沒有地圖標籤的才看位置在不在圓圈裡
            int tagged = mapTagOf(e);
            if (tagged > 0 && tagged != map.id) continue; // 別張地圖的
            boolean inside = tagged == map.id || map.contains(e.getX(), e.getZ());
            if (deleting) {
                // MIGRATE 第二階段：只刪「位置已確認登記成點位」的 marker
                if (!inside) continue;
                if (map.lootPoints.contains(BlockPos.containing(e.position()))) { e.discard(); removed++; }
                else kept++;
                continue;
            }
            found++;
            if (mode == Mode.IMPORT || mode == Mode.MIGRATE) {
                if (!inside) { outside++; continue; }
                if (registry.addPoint(map, BlockPos.containing(e.position()))) imported++; else duplicate++;
            } else {
                if (!inside) { outside++; continue; } // 範圍外的不動，避免誤刪別張地圖的
                e.discard();
                removed++;
            }
        }
    }

    /** marker 上的 map_N 標籤；沒有就回傳 0。 */
    private static int mapTagOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith("map_")) {
                try { return Integer.parseInt(t.substring(4)); } catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }

    private void releaseActive() {
        if (active == null) return;
        for (ChunkPos c : active) level.getChunkSource().removeRegionTicket(TICKET, c, TICKET_DISTANCE, c);
        active = null;
    }

    /** 一個階段跑完。整個工作結束回傳 true；MIGRATE 進入第二階段回傳 false。 */
    private boolean finish() {
        if (mode == Mode.MIGRATE && !deleting) {
            // 先存檔，確保點位寫進硬碟之後才刪 marker（中途當機也不會兩邊都沒有）
            level.getServer().overworld().getDataStorage().save();
            say("匯入完成並已存檔：" + summary(), ChatFormatting.GREEN);
            if (imported + duplicate == 0) {
                say("沒有任何點位可確認，不刪除 marker。", ChatFormatting.YELLOW);
                ProvingGroundsMod.LOGGER.info("Marker MIGRATE on map {}: nothing imported, {}", map.id, summary());
                return true;
            }
            say("開始刪除已確認登記的 marker…", ChatFormatting.YELLOW);
            deleting = true;
            batches.addAll(allBatches);
            return false;
        }
        say("完成！" + summary(), ChatFormatting.GREEN);
        if (timeouts > 0) {
            say(String.format("注意：有 %d 批區塊等太久沒載完，裡面的 marker 可能漏掉，可以再執行一次（重複的點位不會重複登記）。",
                timeouts), ChatFormatting.RED);
        }
        ProvingGroundsMod.LOGGER.info("Marker {} on map {}: {}", mode, map.id, summary());
        return true;
    }

    private String summary() {
        if (deleting) {
            return String.format("已刪除 %d 個 marker%s。地圖 #%d 共有 %d 個點位。", removed,
                kept > 0 ? "，" + kept + " 個沒有對應點位所以保留" : "", map.id, map.lootPoints.size());
        }
        String base = mode != Mode.PURGE
            ? String.format("找到 %d 個，新增點位 %d 個，已存在 %d 個，地圖範圍外 %d 個（未匯入）。地圖 #%d 現在共有 %d 個點位。",
                found, imported, duplicate, outside, map.id, map.lootPoints.size())
            : String.format("找到 %d 個，刪除 %d 個，地圖範圍外 %d 個（未刪除）。", found, removed, outside);
        return base;
    }

    private void say(String msg, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal("[PG] " + msg).withStyle(color), false);
    }
}
