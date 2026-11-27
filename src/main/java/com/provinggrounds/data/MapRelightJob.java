package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

/**
 * 檔案: MapRelightJob.java
 * 目的: 依 Tick 預算重建大逃殺地圖各區塊的 ChunkSkyLightSources 與 ThreadedLevelLightEngine 天空／方塊光照層，
 *       並補送 ClientboundLightUpdatePacket 給附近玩家，徹底消除地形平坦化後殘留在地板上的異常黑塊／陰影。
 *   - 可由 BRMapGenerator 建圖完成後自動呼叫（startInternal），也可由管理員指令 /pg map relight 單獨執行。
 */
public final class MapRelightJob {

    private static final int BUDGET_MS = 16;
    private static final int MAX_WAIT_TICKS = 100;

    private enum Phase { INIT, WAIT_INIT, PROPAGATE, WAIT_PROPAGATE, SYNC }

    private record QueueItem(ServerLevel level, MapRegistry.BRMap map, CommandSourceStack src) {}

    private static final class Job {
        final ServerLevel level;
        final MapRegistry.BRMap map;
        final CommandSourceStack src;
        final ThreadedLevelLightEngine light;
        final List<ChunkPos> chunks = new ArrayList<>();
        final long startMs = System.currentTimeMillis();
        Phase phase = Phase.INIT;
        int idx;
        int waitTicks;

        Job(ServerLevel level, MapRegistry.BRMap map, CommandSourceStack src) {
            this.level = level;
            this.map = map;
            this.src = src;
            this.light = level.getChunkSource().getLightEngine();

            int reach = (int) Math.ceil(map.radius) + 32;
            int minCX = ((int) Math.floor(map.centerX) - reach) >> 4;
            int maxCX = ((int) Math.ceil(map.centerX) + reach) >> 4;
            int minCZ = ((int) Math.floor(map.centerZ) - reach) >> 4;
            int maxCZ = ((int) Math.ceil(map.centerZ) + reach) >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    double nx = Math.max(cx << 4, Math.min(map.centerX, (cx << 4) + 16));
                    double nz = Math.max(cz << 4, Math.min(map.centerZ, (cz << 4) + 16));
                    if (Math.hypot(nx - map.centerX, nz - map.centerZ) <= reach) {
                        chunks.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }
    }

    private static final Deque<QueueItem> QUEUE = new ArrayDeque<>();
    @Nullable
    private static Job current;

    private MapRelightJob() {}

    public static boolean isRunning() {
        return current != null || !QUEUE.isEmpty();
    }

    public static String statusText() {
        Job j = current;
        if (j == null) return "[光照修復] 目前沒有進行中的任務。";
        return String.format("[光照修復] 地圖 #%d「%s」（%s：%d/%d 區塊；排程尚餘 %d 張）",
            j.map.id, j.map.name, j.phase.name(), j.idx, j.chunks.size(), QUEUE.size());
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
    }

    public static boolean cancel(CommandSourceStack src) {
        if (!isRunning()) return false;
        QUEUE.clear();
        Job j = current;
        current = null;
        if (j != null) {
            say(src, String.format("[光照修復] 已取消 #%d「%s」與後續排程。", j.map.id, j.map.name), ChatFormatting.YELLOW);
        }
        return true;
    }

    /** 由管理員指令啟動（可傳單張或多張）。成功回傳 null，否則回傳錯誤訊息。 */
    @Nullable
    public static String startList(MinecraftServer server, List<MapRegistry.BRMap> maps, CommandSourceStack src) {
        if (!GameManager.get().isInLobby() || BattleRoyaleMode.get().isRunning()) {
            return "大逃殺進行中無法修復地圖光照，請回到大廳後執行。";
        }
        if (isRunning()) return statusText();
        if (BRMapGenerator.isRunning()) return "地圖生成作業進行中，請等完成後再執行。";
        if (MapRenderJob.isRunning()) return "俯視圖繪製進行中，請等完成後再執行。";
        if (maps.isEmpty()) return "找不到需要修復光照的地圖。";

        ServerLevel level = server.overworld();
        for (MapRegistry.BRMap m : maps) {
            QUEUE.add(new QueueItem(level, m, src));
        }
        launchNext();
        if (maps.size() > 1) {
            say(src, String.format("[光照修復] 已排程共 %d 張地圖重建天空與方塊光照。", maps.size()), ChatFormatting.AQUA);
        }
        return null;
    }

    /** 供 BRMapGenerator 建圖結束前自動呼叫，不額外做生成器互斥檢查。 */
    static void startInternal(ServerLevel level, MapRegistry.BRMap map, CommandSourceStack src) {
        QUEUE.clear();
        current = new Job(level, map, src);
        say(src, String.format("[#%d %s] 正在重建 %d 個區塊的天空／方塊光照以消除殘留陰影…",
            map.id, map.name, current.chunks.size()), ChatFormatting.GRAY);
    }

    private static void launchNext() {
        QueueItem item = QUEUE.poll();
        if (item == null) return;
        current = new Job(item.level(), item.map(), item.src());
        say(item.src(), String.format("[光照修復] 開始重建地圖 #%d「%s」（%d 個區塊）…",
            item.map().id, item.map().name, current.chunks.size()), ChatFormatting.YELLOW);
    }

    public static void tickCurrent() {
        Job j = current;
        if (j == null) {
            if (!QUEUE.isEmpty()) launchNext();
            return;
        }
        try {
            if (tickJob(j)) {
                double secs = (System.currentTimeMillis() - j.startMs) / 1000.0;
                say(j.src, String.format("[光照修復] 地圖 #%d「%s」已完成 %d 個區塊的光照重建（耗時 %.1f 秒）。",
                    j.map.id, j.map.name, j.chunks.size(), secs), ChatFormatting.GREEN);
                ProvingGroundsMod.LOGGER.info("Relit BR map #{} ({}) across {} chunks in {} ms",
                    j.map.id, j.map.name, j.chunks.size(), System.currentTimeMillis() - j.startMs);
                current = null;
                if (!QUEUE.isEmpty()) launchNext();
            }
        } catch (Exception e) {
            ProvingGroundsMod.LOGGER.error("MapRelightJob failed on map #{}", j.map.id, e);
            say(j.src, "[光照修復] 發生錯誤：" + e.getMessage(), ChatFormatting.RED);
            current = null;
            QUEUE.clear();
        }
    }

    private static boolean tickJob(Job j) {
        long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L;
        switch (j.phase) {
            case INIT -> {
                int minSecY = j.level.getMinSection();
                while (j.idx < j.chunks.size() && System.nanoTime() < deadline) {
                    ChunkPos cp = j.chunks.get(j.idx++);
                    LevelChunk chunk = j.level.getChunk(cp.x, cp.z);
                    Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
                    chunk.initializeLightSources();
                    j.light.setLightEnabled(cp, false);
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int si = 0; si < sections.length; si++) {
                        SectionPos sp = SectionPos.of(cp, minSecY + si);
                        j.light.updateSectionStatus(sp, sections[si].hasOnlyAir());
                        j.light.queueSectionData(LightLayer.SKY, sp, new DataLayer());
                        j.light.queueSectionData(LightLayer.BLOCK, sp, new DataLayer());
                    }
                    j.light.setLightEnabled(cp, true);
                    chunk.setUnsaved(true);
                }
                j.light.tryScheduleUpdate();
                if (j.idx >= j.chunks.size()) {
                    j.phase = Phase.WAIT_INIT;
                    j.waitTicks = 0;
                    j.idx = 0;
                }
            }
            case WAIT_INIT -> {
                j.light.tryScheduleUpdate();
                j.waitTicks++;
                if (!j.light.hasLightWork() || j.waitTicks >= MAX_WAIT_TICKS) {
                    j.phase = Phase.PROPAGATE;
                    j.idx = 0;
                }
            }
            case PROPAGATE -> {
                while (j.idx < j.chunks.size() && System.nanoTime() < deadline) {
                    ChunkPos cp = j.chunks.get(j.idx++);
                    LevelChunk chunk = j.level.getChunk(cp.x, cp.z);
                    j.light.propagateLightSources(cp);
                    chunk.findBlockLightSources((pos, state) -> j.light.checkBlock(pos));
                }
                j.light.tryScheduleUpdate();
                if (j.idx >= j.chunks.size()) {
                    j.phase = Phase.WAIT_PROPAGATE;
                    j.waitTicks = 0;
                    j.idx = 0;
                }
            }
            case WAIT_PROPAGATE -> {
                j.light.tryScheduleUpdate();
                j.waitTicks++;
                if (!j.light.hasLightWork() || j.waitTicks >= MAX_WAIT_TICKS) {
                    j.phase = Phase.SYNC;
                    j.idx = 0;
                }
            }
            case SYNC -> {
                while (j.idx < j.chunks.size() && System.nanoTime() < deadline) {
                    ChunkPos cp = j.chunks.get(j.idx++);
                    LevelChunk chunk = j.level.getChunk(cp.x, cp.z);
                    chunk.setLightCorrect(true);
                    chunk.setUnsaved(true);
                    List<ServerPlayer> watchers = j.level.getChunkSource().chunkMap.getPlayers(cp, false);
                    if (!watchers.isEmpty()) {
                        ClientboundLightUpdatePacket pkt = new ClientboundLightUpdatePacket(cp, j.light, null, null);
                        for (ServerPlayer p : watchers) p.connection.send(pkt);
                    }
                }
                return j.idx >= j.chunks.size();
            }
        }
        return false;
    }

    private static void say(CommandSourceStack src, String msg, ChatFormatting color) {
        src.sendSuccess(() -> Component.literal(msg).withStyle(color), false);
    }
}
