package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.admin.build.BuildingCanvas;
import com.provinggrounds.admin.build.WildStructures;
import com.provinggrounds.block.AltarBlock;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.data.BRMapLayout.CompoundBox;
import com.provinggrounds.data.BRMapLayout.CoverSpot;
import com.provinggrounds.data.BRMapLayout.MicroPoi;
import com.provinggrounds.data.BRMapLayout.PlacedBuilding;
import com.provinggrounds.data.BRMapLayout.Plan;
import com.provinggrounds.data.BRMapLayout.RoadSeg;
import com.provinggrounds.data.BRMapLayout.SmallBuilding;
import com.provinggrounds.data.BRMapPresets.Preset;
import com.provinggrounds.data.BRMapPresets.RegionBiome;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: BRMapGenerator.java
 * 目的: 伺服器端程序化大逃殺地圖生成器（指令 /pg map generate）。
 *   - 支援一次生成或原地重建 5 張三層高密度大逃殺地圖（300 / 400 / 500 / 600 / 700 格）：
 *     1. 自然地景＋大小城區平台＋放射／環狀公路／支線小路＋跨河橋樑；
 *     2. 第一層大城區（52 棟無木箱建築）＋第二層小型衛星房區（2～3 棟小建築圍合＋低矮院牆）；
 *     3. 第三層路旁檢查哨／野戰營地微據點＋復活祭壇廣場（AltarBlock + AltarLedger）；
 *     4. 高密度野外成片樹林、巨石岩脊、長條戰術矮牆、車輛與貨櫃掩體，最後自動繪製俯視圖。
 */
public final class BRMapGenerator {

    private static final int GRID = 5000;
    private static final int CHUNK_BUDGET_MS = 18;
    private static final int SMALL_PER_TICK = 3;
    private static final int MICRO_PER_TICK = 4;
    private static final int COVER_BUDGET_MS = 14;

    private enum Phase { TERRAIN, MAJOR_BUILDINGS, SMALL_BUILDINGS, ALTARS_AND_WILD, RELIGHT, WAIT_RENDER }

    private record QueueItem(Preset preset, @Nullable Integer replaceMapId, @Nullable int[] forcedCenter) {}

    private static final class Job {
        final ServerLevel level;
        final Preset preset;
        final CommandSourceStack src;
        final int mapId;
        final Plan plan;
        final boolean isRegen;
        final Deque<ChunkPos> terrainChunks = new ArrayDeque<>();
        final int totalChunks;
        int doneChunks;
        int majorIdx;
        int smallIdx;
        boolean yardWallsDone;
        int lootAdded;
        boolean altarsPlaced;
        int microIdx;
        int coverIdx;
        boolean relightStarted;
        boolean renderStarted;
        Phase phase = Phase.TERRAIN;
        final long startMs = System.currentTimeMillis();

        Job(ServerLevel level, Preset preset, CommandSourceStack src, int mapId, Plan plan, boolean isRegen) {
            this.level = level;
            this.preset = preset;
            this.src = src;
            this.mapId = mapId;
            this.plan = plan;
            this.isRegen = isRegen;

            int reach = preset.radius() + 16;
            int minCX = (plan.centerX - reach) >> 4;
            int maxCX = (plan.centerX + reach) >> 4;
            int minCZ = (plan.centerZ - reach) >> 4;
            int maxCZ = (plan.centerZ + reach) >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    double nx = Math.max(cx << 4, Math.min(plan.centerX, (cx << 4) + 16));
                    double nz = Math.max(cz << 4, Math.min(plan.centerZ, (cz << 4) + 16));
                    if (Math.hypot(nx - plan.centerX, nz - plan.centerZ) <= reach) {
                        terrainChunks.add(new ChunkPos(cx, cz));
                    }
                }
            }
            this.totalChunks = terrainChunks.size();
        }
    }

    private static final Deque<QueueItem> QUEUE = new ArrayDeque<>();
    @Nullable private static Job current;
    @Nullable private static CommandSourceStack queueSource;

    private BRMapGenerator() {}

    public static boolean isRunning() {
        return current != null || !QUEUE.isEmpty();
    }

    public static String statusText() {
        if (current == null) return "目前沒有在生成大逃殺地圖。";
        return String.format("%s #%d「%s」（半徑 %d）：階段 %s｜地形 %d/%d｜大建築 %d/%d｜小房區建物 %d/%d｜佇列尚餘 %d 張",
            current.isRegen ? "正在原地重建" : "正在生成",
            current.mapId, current.preset.displayName(), current.preset.radius(), current.phase.name(),
            current.doneChunks, current.totalChunks,
            current.majorIdx, current.plan.buildings.size(),
            current.smallIdx, current.plan.smallBuildings.size(), QUEUE.size());
    }

    @Nullable
    public static String startSingle(MinecraftServer server, Preset preset, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        QUEUE.clear();
        queueSource = src;
        launchNext(server, new QueueItem(preset, null, null), src);
        return null;
    }

    @Nullable
    public static String startAll(MinecraftServer server, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        QUEUE.clear();
        for (Preset p : BRMapPresets.all()) QUEUE.add(new QueueItem(p, null, null));
        queueSource = src;
        QueueItem first = QUEUE.poll();
        if (first != null) {
            say(src, String.format("[生成地圖] 已排程依序生成全部 %d 張三層高密度地圖（300 / 400 / 500 / 600 / 700 格）。",
                BRMapPresets.all().size()), ChatFormatting.AQUA);
            launchNext(server, first, src);
        }
        return null;
    }

    /** 自動找出目前已存在的 5 張主題地圖並在原地重建成高密度版本（保留編號與位置）。 */
    @Nullable
    public static String startRegenAll(MinecraftServer server, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        MapRegistry reg = MapRegistry.get(server);
        List<QueueItem> matched = new ArrayList<>();
        for (MapRegistry.BRMap m : reg.all()) {
            for (Preset p : BRMapPresets.all()) {
                if (m.name.equals(p.displayName())) {
                    int[] center = { ((int) Math.round(m.centerX) >> 4) << 4, ((int) Math.round(m.centerZ) >> 4) << 4 };
                    matched.add(new QueueItem(p, m.id, center));
                    break;
                }
            }
        }
        if (matched.isEmpty()) {
            return "找不到已生成的 5 張主題地圖；請先使用 /pg map generate all 生成，或指定編號 /pg map generate regen <編號> <預設>。";
        }
        QUEUE.clear();
        QUEUE.addAll(matched);
        queueSource = src;
        QueueItem first = QUEUE.poll();
        if (first != null) {
            say(src, String.format("[重建地圖] 找到 %d 張現有主題地圖，開始依序原地升級為三層高密度版本…", matched.size()), ChatFormatting.AQUA);
            launchNext(server, first, src);
        }
        return null;
    }

    /** 指定現有地圖編號在原地重蓋成指定預設（保留編號與現有中心）。 */
    @Nullable
    public static String startRegenOne(MinecraftServer server, int mapId, Preset preset, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        MapRegistry.BRMap m = MapRegistry.get(server).getMap(mapId);
        if (m == null) return "找不到地圖 #" + mapId + "。";
        int[] center = { ((int) Math.round(m.centerX) >> 4) << 4, ((int) Math.round(m.centerZ) >> 4) << 4 };
        QUEUE.clear();
        queueSource = src;
        launchNext(server, new QueueItem(preset, mapId, center), src);
        return null;
    }

    @Nullable
    private static String checkReady(CommandSourceStack src) {
        if (!GameManager.get().isInLobby() || BattleRoyaleMode.get().isRunning()) {
            return "大逃殺進行中，請回到大廳後再生成地圖。";
        }
        if (isRunning()) return statusText();
        if (MapRelightJob.isRunning()) return "光照修復作業進行中，請等完成後再開始生成。";
        if (MapRenderJob.isRunning()) return "俯視圖繪製進行中，請等完成後再開始生成。";
        if (MarkerScanJob.isRunning()) return "掃描作業進行中，請等完成後再開始生成。";
        return null;
    }

    public static boolean cancel(CommandSourceStack src) {
        if (!isRunning()) return false;
        QUEUE.clear();
        if (current != null) {
            say(src, String.format("[生成地圖] 已取消「%s」（#%d）與後續排程。",
                current.preset.displayName(), current.mapId), ChatFormatting.YELLOW);
            current = null;
        }
        queueSource = null;
        return true;
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
        queueSource = null;
    }

    public static void tickCurrent() {
        if (current == null) {
            if (!QUEUE.isEmpty() && queueSource != null && !MapRenderJob.isRunning()) {
                launchNext(queueSource.getServer(), QUEUE.poll(), queueSource);
            }
            return;
        }
        if (tickJob(current)) {
            MinecraftServer server = current.level.getServer();
            current = null;
            if (!QUEUE.isEmpty() && queueSource != null && !MapRenderJob.isRunning()) {
                launchNext(server, QUEUE.poll(), queueSource);
            } else if (QUEUE.isEmpty()) {
                queueSource = null;
            }
        }
    }

    private static void launchNext(MinecraftServer server, QueueItem item, CommandSourceStack src) {
        Preset preset = item.preset();
        MapRegistry reg = MapRegistry.get(server);
        boolean isRegen = item.replaceMapId() != null;
        int id;
        int[] slot;
        if (isRegen) {
            id = item.replaceMapId();
            slot = item.forcedCenter() != null ? item.forcedCenter() : freeSlot(reg, preset.radius());
            MapRegistry.BRMap oldMap = reg.getMap(id);
            if (oldMap != null) {
                reg.clearPoints(oldMap);
                AltarLedger ledger = AltarLedger.get(server);
                for (BlockPos ap : new ArrayList<>(ledger.entries())) {
                    if (oldMap.contains(ap.getX() + 0.5, ap.getZ() + 0.5)) {
                        ledger.remove(ap);
                    }
                }
            }
            reg.setMap(id, preset.displayName(), slot[0], slot[1], preset.radius());
        } else {
            id = 1;
            for (MapRegistry.BRMap m : reg.all()) id = Math.max(id, m.id + 1);
            slot = freeSlot(reg, preset.radius());
            reg.setMap(id, preset.displayName(), slot[0], slot[1], preset.radius());
        }

        Plan plan = BRMapLayout.buildPlan(preset, slot[0], slot[1]);
        Job job = new Job(server.overworld(), preset, src, id, plan, isRegen);
        current = job;
        say(src, String.format("[生成地圖] %s #%d「%s」：中心 (%d, %d)、半徑 %d｜%d 大城區(%d 棟)＋%d 小房區(%d 棟)＋%d 微據點＋%d 野外掩體＋%d 祭壇…",
            isRegen ? "開始原地升級重建" : "開始建立", id, preset.displayName(), slot[0], slot[1], preset.radius(),
            plan.districts.size(), plan.buildings.size(), plan.compounds.size(), plan.smallBuildings.size(),
            plan.microPois.size(), plan.covers.size(), plan.altars.size()), ChatFormatting.YELLOW);
    }

    /** 找一個 16 格對齊且與現有所有地圖不相鄰的空曠格點。 */
    private static int[] freeSlot(MapRegistry reg, int radius) {
        for (int ring = 1; ring <= 25; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int k = -ring; k <= ring; k++) {
                    if (Math.max(Math.abs(i), Math.abs(k)) != ring) continue;
                    int cx = ((i * GRID) >> 4) << 4;
                    int cz = ((k * GRID) >> 4) << 4;
                    boolean ok = true;
                    for (MapRegistry.BRMap m : reg.all()) {
                        double d = Math.hypot(m.centerX - cx, m.centerZ - cz);
                        if (d < m.radius + radius + 1200) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) return new int[] { cx, cz };
                }
            }
        }
        return new int[] { (GRID * 26) & ~15, 0 };
    }

    // ── 分階段每 Tick 推進 ──────────────────────────────────────────────────

    private static boolean tickJob(Job job) {
        return switch (job.phase) {
            case TERRAIN -> tickTerrain(job);
            case MAJOR_BUILDINGS -> tickMajorBuildings(job);
            case SMALL_BUILDINGS -> tickSmallBuildings(job);
            case ALTARS_AND_WILD -> tickAltarsAndWild(job);
            case RELIGHT -> tickRelight(job);
            case WAIT_RENDER -> tickWaitRender(job);
        };
    }

    private static boolean tickTerrain(Job job) {
        long deadline = System.nanoTime() + CHUNK_BUDGET_MS * 1_000_000L;
        while (!job.terrainChunks.isEmpty() && System.nanoTime() < deadline) {
            ChunkPos cp = job.terrainChunks.poll();
            generateTerrainChunk(job, cp);
            job.doneChunks++;
            if (job.doneChunks % 250 == 0 && !job.terrainChunks.isEmpty()) {
                say(job.src, String.format("[#%d %s] 地形建立進度 %d / %d 區塊",
                    job.mapId, job.preset.displayName(), job.doneChunks, job.totalChunks), ChatFormatting.GRAY);
            }
        }
        if (job.terrainChunks.isEmpty()) {
            job.phase = Phase.MAJOR_BUILDINGS;
            say(job.src, String.format("[#%d %s] 地形完成，開始建造 %d 棟大城區建築…",
                job.mapId, job.preset.displayName(), job.plan.buildings.size()), ChatFormatting.GRAY);
        }
        return false;
    }

    private static boolean tickMajorBuildings(Job job) {
        if (job.majorIdx >= job.plan.buildings.size()) {
            job.phase = Phase.SMALL_BUILDINGS;
            say(job.src, String.format("[#%d %s] 大城區完成，開始建造 %d 個小房區聚落（%d 棟建築）…",
                job.mapId, job.preset.displayName(), job.plan.compounds.size(), job.plan.smallBuildings.size()), ChatFormatting.GRAY);
            return false;
        }
        PlacedBuilding b = job.plan.buildings.get(job.majorIdx++);
        BuildingCanvas canvas = new BuildingCanvas(job.level, new BlockPos(b.originX(), b.y(), b.originZ()), b.facing());
        b.bp().builder().build(canvas);
        canvas.finalizeShapes();
        registerLoot(job, canvas);

        if (job.majorIdx % 8 == 0) {
            say(job.src, String.format("[#%d %s] 大城區建築進度 %d / %d（物資點 %d）",
                job.mapId, job.preset.displayName(), job.majorIdx, job.plan.buildings.size(), job.lootAdded), ChatFormatting.GRAY);
        }
        return false;
    }

    private static boolean tickSmallBuildings(Job job) {
        for (int k = 0; k < SMALL_PER_TICK && job.smallIdx < job.plan.smallBuildings.size(); k++) {
            SmallBuilding sb = job.plan.smallBuildings.get(job.smallIdx++);
            BuildingCanvas canvas = new BuildingCanvas(job.level, new BlockPos(sb.originX(), sb.y(), sb.originZ()), sb.facing());
            WildStructures.buildSmall(sb.kind(), canvas);
            canvas.finalizeShapes();
            registerLoot(job, canvas);
        }
        if (job.smallIdx >= job.plan.smallBuildings.size()) {
            if (!job.yardWallsDone) {
                for (CompoundBox c : job.plan.compounds) {
                    buildCompoundLowWalls(job.level, c, job.preset.seed());
                }
                job.yardWallsDone = true;
            }
            job.phase = Phase.ALTARS_AND_WILD;
        }
        return false;
    }

    private static boolean tickAltarsAndWild(Job job) {
        if (!job.altarsPlaced) {
            AltarLedger ledger = AltarLedger.get(job.level.getServer());
            for (BlockPos altarPos : job.plan.altars) {
                buildAltarPlaza(job.level, altarPos, job.plan.biomeAtWorld(altarPos.getX(), altarPos.getZ()));
                ledger.add(altarPos);
            }
            job.altarsPlaced = true;
            say(job.src, String.format("[#%d %s] 已設置 %d 座祭壇，正在佈置 %d 個野區微據點與 %d 處樹林／野外掩體…",
                job.mapId, job.preset.displayName(), job.plan.altars.size(), job.plan.microPois.size(), job.plan.covers.size()), ChatFormatting.GRAY);
            return false;
        }
        for (int k = 0; k < MICRO_PER_TICK && job.microIdx < job.plan.microPois.size(); k++) {
            MicroPoi m = job.plan.microPois.get(job.microIdx++);
            BuildingCanvas canvas = new BuildingCanvas(job.level, new BlockPos(m.x(), m.y(), m.z()), m.facing());
            WildStructures.buildMicroPoi(m.kind(), canvas, m.biome());
            canvas.finalizeShapes();
            registerLoot(job, canvas);
        }
        if (job.microIdx < job.plan.microPois.size()) return false;

        long deadline = System.nanoTime() + COVER_BUDGET_MS * 1_000_000L;
        while (job.coverIdx < job.plan.covers.size() && System.nanoTime() < deadline) {
            buildOutdoorCover(job.level, job.plan, job.plan.covers.get(job.coverIdx++), job.preset.seed());
        }
        if (job.coverIdx >= job.plan.covers.size()) {
            job.phase = Phase.RELIGHT;
        }
        return false;
    }

    private static boolean tickRelight(Job job) {
        if (!job.relightStarted) {
            job.relightStarted = true;
            MapRegistry.BRMap map = MapRegistry.get(job.level.getServer()).getMap(job.mapId);
            if (map == null) {
                job.phase = Phase.WAIT_RENDER;
                return false;
            }
            MapRelightJob.startInternal(job.level, map, job.src);
            return false;
        }
        if (MapRelightJob.isRunning()) return false;
        job.phase = Phase.WAIT_RENDER;
        return false;
    }

    private static void registerLoot(Job job, BuildingCanvas canvas) {
        MapRegistry reg = MapRegistry.get(job.level.getServer());
        MapRegistry.BRMap map = reg.getMap(job.mapId);
        if (map == null) return;
        for (BlockPos lp : canvas.lootPoints()) {
            if (map.contains(lp.getX() + 0.5, lp.getZ() + 0.5) && reg.addPoint(map, lp)) {
                job.lootAdded++;
            }
        }
    }

    private static boolean tickWaitRender(Job job) {
        if (!job.renderStarted) {
            job.renderStarted = true;
            MinecraftServer server = job.level.getServer();
            MapRegistry.BRMap map = MapRegistry.get(server).getMap(job.mapId);
            double secs = (System.currentTimeMillis() - job.startMs) / 1000.0;

            MutableComponent doneMsg = Component.literal(String.format(
                "[生成地圖] 地圖 #%d「%s」（半徑 %d 格）%s完成（%.1f 秒）：%d 大城區建物＋%d 小房區建物＋%d 微據點、%d 物資點、%d 祭壇 [點此傳送]。",
                job.mapId, job.preset.displayName(), job.preset.radius(), job.isRegen ? "原地重建" : "建造", secs,
                job.plan.buildings.size(), job.plan.smallBuildings.size(), job.plan.microPois.size(),
                job.lootAdded, job.plan.altars.size()))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/tp @s " + job.plan.centerX + " 120 " + job.plan.centerZ))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("傳送至地圖 #" + job.mapId + " 中央上空"))));
            job.src.sendSuccess(() -> doneMsg, true);
            ProvingGroundsMod.LOGGER.info("Generated BR map #{} ({}) at ({}, {}), radius={}, major={}, small={}, micro={}, loot={}, altars={}",
                job.mapId, job.preset.key(), job.plan.centerX, job.plan.centerZ, job.preset.radius(),
                job.plan.buildings.size(), job.plan.smallBuildings.size(), job.plan.microPois.size(),
                job.lootAdded, job.plan.altars.size());

            LobbyManager.get().broadcastLobbyMapImage(server);
            if (map != null && !MapRenderJob.isRunning()) {
                MapRenderJob.start(server, map, job.src);
            }
            return false;
        }
        if (MapRenderJob.isRunning()) return false;
        LobbyManager.get().broadcastLobbyMapImage(job.level.getServer());
        return true;
    }

    // ── 地形區塊寫入 ────────────────────────────────────────────────────────

    private static void generateTerrainChunk(Job job, ChunkPos cp) {
        ServerLevel level = job.level;
        LevelChunk chunk = level.getChunk(cp.x, cp.z);

        for (BlockPos p : new ArrayList<>(chunk.getBlockEntities().keySet())) chunk.removeBlockEntity(p);
        AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
            cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();

        int minSectionY = level.getMinSection();
        int maxClearY = 136;
        int waterLevel = job.preset.waterLevel();
        Plan plan = job.plan;

        int[][] topY = new int[16][16];
        BlockState[][] topBlock = new BlockState[16][16];
        BlockState[][] subBlock = new BlockState[16][16];
        BlockState[][] deepBlock = new BlockState[16][16];
        boolean[][] isWater = new boolean[16][16];
        boolean[][] isBridge = new boolean[16][16];
        boolean[][] isBridgeRail = new boolean[16][16];

        for (int lx = 0; lx < 16; lx++) {
            int wx = cp.getMinBlockX() + lx;
            for (int lz = 0; lz < 16; lz++) {
                int wz = cp.getMinBlockZ() + lz;
                RegionBiome biome = plan.biomeAtWorld(wx, wz);
                int sy = plan.surfaceY(wx, wz);
                RoadSeg nearRoad = plan.nearestRoad(wx, wz);
                double roadDist = nearRoad == null ? Double.MAX_VALUE : pointToSegmentDist(
                    wx + 0.5, wz + 0.5, nearRoad.x0() + 0.5, nearRoad.z0() + 0.5, nearRoad.x1() + 0.5, nearRoad.z1() + 0.5) - nearRoad.halfWidth();
                boolean road = roadDist <= 0.0;
                boolean countryPath = road && nearRoad.countryPath();
                boolean roadEdge = road && !countryPath && roadDist > -0.9;

                if (road && sy <= waterLevel) {
                    isBridge[lx][lz] = true;
                    isBridgeRail[lx][lz] = (roadEdge || countryPath && roadDist > -0.7) && ((wx + wz) & 1) == 0;
                    topY[lx][lz] = sy;
                    isWater[lx][lz] = true;
                    topBlock[lx][lz] = Blocks.GRAVEL.defaultBlockState();
                    subBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                    deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                } else if (sy <= waterLevel) {
                    isWater[lx][lz] = true;
                    topY[lx][lz] = sy;
                    topBlock[lx][lz] = Blocks.SAND.defaultBlockState();
                    subBlock[lx][lz] = Blocks.SANDSTONE.defaultBlockState();
                    deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                } else {
                    topY[lx][lz] = sy;
                    if (countryPath) {
                        topBlock[lx][lz] = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
                            ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState();
                        subBlock[lx][lz] = Blocks.COBBLESTONE.defaultBlockState();
                        deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                    } else if (road) {
                        boolean centerLine = roadDist <= -2.1 && Math.abs((wx + wz) % 6) < 3;
                        topBlock[lx][lz] = centerLine
                            ? Blocks.WHITE_CONCRETE.defaultBlockState()
                            : (roadEdge ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
                        subBlock[lx][lz] = Blocks.COBBLESTONE.defaultBlockState();
                        deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                    } else if (inDistrictPlaza(plan, wx, wz)) {
                        topBlock[lx][lz] = districtGroundBlock(biome);
                        subBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                        deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                    } else if (inCompoundYard(plan, wx, wz)) {
                        topBlock[lx][lz] = compoundYardBlock(biome, wx, wz, job.preset.seed());
                        subBlock[lx][lz] = naturalSubBlock(biome);
                        deepBlock[lx][lz] = Blocks.STONE.defaultBlockState();
                    } else {
                        topBlock[lx][lz] = naturalTopBlock(biome, sy, wx, wz, job.preset.seed());
                        subBlock[lx][lz] = naturalSubBlock(biome);
                        deepBlock[lx][lz] = naturalDeepBlock(biome, sy);
                    }
                }
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState bridgeDeck = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState bridgeRail = Blocks.STONE_BRICK_WALL.defaultBlockState();

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection sec = sections[si];
            int secY = minSectionY + si;
            int baseY = secY << 4;
            if (baseY > maxClearY && sec.hasOnlyAir()) continue;

            for (int ly = 0; ly < 16; ly++) {
                int wy = baseY + ly;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int sy = topY[lx][lz];
                        BlockState bs;
                        if (wy <= level.getMinBuildHeight()) {
                            bs = bedrock;
                        } else if (wy < 0) {
                            bs = deepslate;
                        } else if (wy <= sy - 4) {
                            bs = deepBlock[lx][lz];
                        } else if (wy < sy) {
                            bs = subBlock[lx][lz];
                        } else if (wy == sy) {
                            bs = topBlock[lx][lz];
                        } else if (isWater[lx][lz] && wy <= waterLevel) {
                            bs = (job.preset.theme() == BRMapPresets.Theme.ARCTIC && wy == waterLevel)
                                ? Blocks.PACKED_ICE.defaultBlockState()
                                : water;
                        } else if (isBridge[lx][lz] && wy == waterLevel + 2) {
                            bs = bridgeDeck;
                        } else if (isBridgeRail[lx][lz] && wy == waterLevel + 3) {
                            bs = bridgeRail;
                        } else {
                            bs = air;
                        }
                        if (!bs.isAir() || !sec.getBlockState(lx, ly, lz).isAir()) {
                            sec.setBlockState(lx, ly, lz, bs, false);
                        }
                    }
                }
            }
            sec.recalcBlockCounts();
        }

        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
    }

    private static double pointToSegmentDist(double px, double pz, double x0, double z0, double x1, double z1) {
        double vx = x1 - x0, vz = z1 - z0;
        double lenSq = vx * vx + vz * vz;
        if (lenSq <= 1e-6) return Math.hypot(px - x0, pz - z0);
        double t = Math.max(0.0, Math.min(1.0, ((px - x0) * vx + (pz - z0) * vz) / lenSq));
        return Math.hypot(px - (x0 + t * vx), pz - (z0 + t * vz));
    }

    private static boolean inDistrictPlaza(Plan plan, int wx, int wz) {
        for (BRMapLayout.DistrictBox d : plan.districts) {
            if (wx >= d.minX() && wx <= d.maxX() && wz >= d.minZ() && wz <= d.maxZ()) return true;
        }
        return false;
    }

    private static boolean inCompoundYard(Plan plan, int wx, int wz) {
        for (CompoundBox c : plan.compounds) {
            if (wx >= c.minX() && wx <= c.maxX() && wz >= c.minZ() && wz <= c.maxZ()) return true;
        }
        return false;
    }

    private static BlockState districtGroundBlock(RegionBiome biome) {
        return switch (biome) {
            case DESERT_SAND, RED_CANYON -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case SAKURA_HIGHLAND, MEDIEVAL_VALLEY -> Blocks.STONE_BRICKS.defaultBlockState();
            case ARCTIC_SNOW -> Blocks.POLISHED_DIORITE.defaultBlockState();
            case INDUSTRIAL_YARD -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            default -> Blocks.SMOOTH_STONE.defaultBlockState();
        };
    }

    private static BlockState compoundYardBlock(RegionBiome biome, int wx, int wz, long seed) {
        double p = BRMapPresets.hash01(wx, wz, seed ^ 0xCCCL);
        return switch (biome) {
            case DESERT_SAND, RED_CANYON -> p < 0.7 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case ARCTIC_SNOW -> p < 0.6 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
            default -> p < 0.65 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        };
    }

    private static BlockState naturalTopBlock(RegionBiome biome, int y, int wx, int wz, long seed) {
        double patch = BRMapPresets.hash01(wx, wz, seed ^ 0x555L);
        return switch (biome) {
            case DESERT_SAND -> Blocks.SAND.defaultBlockState();
            case RED_CANYON -> (y >= 77 || patch < 0.28)
                ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.RED_SAND.defaultBlockState();
            case ARCTIC_SNOW -> y >= 81
                ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
            case SAKURA_HIGHLAND -> patch < 0.14
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            case INDUSTRIAL_YARD -> patch < 0.25
                ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private static BlockState naturalSubBlock(RegionBiome biome) {
        return switch (biome) {
            case DESERT_SAND -> Blocks.SANDSTONE.defaultBlockState();
            case RED_CANYON -> Blocks.RED_SANDSTONE.defaultBlockState();
            case ARCTIC_SNOW -> Blocks.PACKED_ICE.defaultBlockState();
            default -> Blocks.DIRT.defaultBlockState();
        };
    }

    private static BlockState naturalDeepBlock(RegionBiome biome, int y) {
        if (y >= 80 && biome == RegionBiome.RED_CANYON) return Blocks.RED_SANDSTONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    // ── 小房區院牆、祭壇廣場與密集野外掩體 ────────────────────────────────────

    private static void buildCompoundLowWalls(ServerLevel level, CompoundBox c, long seed) {
        Block wallBlk = (c.biome() == RegionBiome.DESERT_SAND || c.biome() == RegionBiome.RED_CANYON)
            ? Blocks.SANDSTONE_WALL
            : (c.biome() == RegionBiome.ARCTIC_SNOW ? Blocks.DEEPSLATE_BRICK_WALL : Blocks.STONE_BRICK_WALL);
        BlockState wall = wallBlk.defaultBlockState();
        int y = c.y() + 1;
        // 沿著聚落外緣放置不封死的轉角護欄掩體（保留大開口進出，提供庭院對槍遮蔽）
        for (int x = c.minX(); x <= c.maxX(); x++) {
            boolean cornerSegment = Math.abs(x - c.minX()) <= 7 || Math.abs(x - c.maxX()) <= 7;
            if (!cornerSegment) continue;
            if (BRMapPresets.hash01(x, c.minZ(), seed ^ 0xD01L) > 0.20) placeFreeAirWall(level, x, y, c.minZ(), wall);
            if (BRMapPresets.hash01(x, c.maxZ(), seed ^ 0xD02L) > 0.20) placeFreeAirWall(level, x, y, c.maxZ(), wall);
        }
        for (int z = c.minZ() + 1; z < c.maxZ(); z++) {
            boolean cornerSegment = Math.abs(z - c.minZ()) <= 7 || Math.abs(z - c.maxZ()) <= 7;
            if (!cornerSegment) continue;
            if (BRMapPresets.hash01(c.minX(), z, seed ^ 0xD03L) > 0.20) placeFreeAirWall(level, c.minX(), y, z, wall);
            if (BRMapPresets.hash01(c.maxX(), z, seed ^ 0xD04L) > 0.20) placeFreeAirWall(level, c.maxX(), y, z, wall);
        }
    }

    private static void placeFreeAirWall(ServerLevel level, int x, int y, int z, BlockState wall) {
        BlockPos p = new BlockPos(x, y, z);
        if (level.getBlockState(p).isAir()) {
            level.setBlock(p, wall, Block.UPDATE_ALL);
        }
    }

    private static void buildAltarPlaza(ServerLevel level, BlockPos altarPos, RegionBiome biome) {
        Block floorBlk = switch (biome) {
            case DESERT_SAND, RED_CANYON -> Blocks.SMOOTH_SANDSTONE;
            case ARCTIC_SNOW -> Blocks.QUARTZ_BLOCK;
            case SAKURA_HIGHLAND, MEDIEVAL_VALLEY -> Blocks.STONE_BRICKS;
            default -> Blocks.POLISHED_DEEPSLATE;
        };
        Block pillarBlk = switch (biome) {
            case DESERT_SAND, RED_CANYON -> Blocks.CHISELED_SANDSTONE;
            case ARCTIC_SNOW -> Blocks.POLISHED_DIORITE;
            default -> Blocks.DEEPSLATE_BRICKS;
        };
        Block slabBlk = switch (biome) {
            case DESERT_SAND, RED_CANYON -> Blocks.SANDSTONE_SLAB;
            case ARCTIC_SNOW -> Blocks.SMOOTH_QUARTZ_SLAB;
            default -> Blocks.STONE_BRICK_SLAB;
        };

        int ax = altarPos.getX(), ay = altarPos.getY(), az = altarPos.getZ();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos fp = new BlockPos(ax + dx, ay - 1, az + dz);
                level.setBlock(fp, floorBlk.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                for (int clearY = 0; clearY <= 4; clearY++) {
                    level.setBlock(new BlockPos(ax + dx, ay + clearY, az + dz), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        int[] signs = { -3, 3 };
        for (int sx : signs) {
            for (int sz : signs) {
                level.setBlock(new BlockPos(ax + sx, ay, az + sz), pillarBlk.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(new BlockPos(ax + sx, ay + 1, az + sz), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
                BlockPos cx1 = new BlockPos(ax + sx - Integer.signum(sx), ay, az + sz);
                BlockPos cx2 = new BlockPos(ax + sx, ay, az + sz - Integer.signum(sz));
                BlockState sb = slabBlk.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                level.setBlock(cx1, sb, Block.UPDATE_ALL);
                level.setBlock(cx2, sb, Block.UPDATE_ALL);
            }
        }
        BlockState altarLower = PGBlocks.ALTAR.get().defaultBlockState()
            .setValue(AltarBlock.HALF, DoubleBlockHalf.LOWER)
            .setValue(AltarBlock.READY, true);
        BlockState altarUpper = PGBlocks.ALTAR.get().defaultBlockState()
            .setValue(AltarBlock.HALF, DoubleBlockHalf.UPPER)
            .setValue(AltarBlock.READY, true);
        level.setBlock(altarPos, altarLower, Block.UPDATE_ALL);
        level.setBlock(altarPos.above(), altarUpper, Block.UPDATE_ALL);
    }

    private static void buildOutdoorCover(ServerLevel level, Plan plan, CoverSpot c, long seed) {
        int wx = c.x(), wz = c.z();
        int wy = plan.surfaceY(wx, wz) + 1;
        Direction dir = Direction.from2DDataValue(Math.floorMod(wx * 31 + wz * 17, 4));
        BuildingCanvas canvas = new BuildingCanvas(level, new BlockPos(wx, wy - 1, wz), dir);

        switch (c.kind()) {
            case 0 -> {
                Block body = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.TERRACOTTA;
                    case ARCTIC_SNOW -> Blocks.WHITE_CONCRETE;
                    default -> Blocks.GREEN_CONCRETE;
                };
                canvas.parkedCar(0, 1, 0, body, ((wx + wz) & 1) == 0);
            }
            case 1 -> {
                Block base = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.SANDSTONE;
                    case ARCTIC_SNOW -> Blocks.POLISHED_DIORITE;
                    default -> Blocks.STONE_BRICKS;
                };
                Block slab = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.SMOOTH_SANDSTONE_SLAB;
                    case ARCTIC_SNOW -> Blocks.SMOOTH_QUARTZ_SLAB;
                    default -> Blocks.STONE_BRICK_SLAB;
                };
                canvas.bunkerCoverL(-1, 1, 0, 3, 3, base, slab);
            }
            case 2 -> {
                if (c.biome() == RegionBiome.DESERT_SAND || c.biome() == RegionBiome.RED_CANYON) {
                    canvas.pillar(-2, 1, 3, 0, Blocks.SANDSTONE);
                    canvas.pillar(2, 1, 2, 1, Blocks.RED_SANDSTONE);
                    canvas.box(-1, 1, 0, 1, 1, 0, Blocks.SMOOTH_SANDSTONE_SLAB);
                } else {
                    Block col = (c.biome() == RegionBiome.INDUSTRIAL_YARD || c.biome() == RegionBiome.HARBOR_COAST)
                        ? Blocks.BLUE_CONCRETE : Blocks.CYAN_CONCRETE;
                    canvas.box(-1, 1, -2, 1, 3, 2, col);
                    canvas.clear(0, 1, -2, 0, 2, 2);
                }
            }
            case 3 -> {
                // 成簇樹林帶：3 棵高低交錯樹干＋樹叢＋倒木，有效切斷野外視線
                Block trunk = switch (c.biome()) {
                    case ARCTIC_SNOW -> Blocks.SPRUCE_LOG;
                    case SAKURA_HIGHLAND -> Blocks.CHERRY_LOG;
                    default -> Blocks.OAK_LOG;
                };
                Block leaves = switch (c.biome()) {
                    case ARCTIC_SNOW -> Blocks.SPRUCE_LEAVES;
                    case SAKURA_HIGHLAND -> Blocks.CHERRY_LEAVES;
                    default -> Blocks.OAK_LEAVES;
                };
                int[][] trees = { {0, 0, 4}, {-4, -3, 3}, {4, 3, 5} };
                for (int[] t : trees) {
                    int tx = t[0], tz = t[1], h = t[2];
                    canvas.box(tx - 2, h, tz - 2, tx + 2, h + 1, tz + 2, leaves);
                    canvas.box(tx - 1, h + 2, tz - 1, tx + 1, h + 2, tz + 1, leaves);
                    canvas.pillar(tx, 1, h, tz, trunk);
                }
                // 倒木低掩體＋樹叢
                canvas.box(-2, 1, 2, 1, 1, 2, trunk);
                if (BRMapPresets.hash01(wx, wz, seed ^ 0xE01L) > 0.4) {
                    canvas.box(2, 1, -3, 3, 2, -2, leaves);
                }
            }
            case 4 -> {
                // 多格不規則巨石／山岩柱／冰丘群
                Block rock = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.RED_SANDSTONE;
                    case ARCTIC_SNOW -> Blocks.PACKED_ICE;
                    default -> Blocks.ANDESITE;
                };
                Block slab = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.RED_SANDSTONE_SLAB;
                    case ARCTIC_SNOW -> Blocks.SMOOTH_QUARTZ_SLAB;
                    default -> Blocks.ANDESITE_SLAB;
                };
                canvas.box(-2, 1, -1, 1, 2, 2, rock);
                canvas.box(-1, 3, 0, 0, 3, 1, rock);
                canvas.bottomSlab(2, 1, 0, slab);
                canvas.bottomSlab(-2, 3, 0, slab);
            }
            default -> {
                // 長條戰術矮牆／田埂半掩體（5 格寬、附半磚探頭位）
                Block wall = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.SANDSTONE;
                    case ARCTIC_SNOW -> Blocks.DEEPSLATE_BRICKS;
                    default -> Blocks.COBBLESTONE;
                };
                Block slab = switch (c.biome()) {
                    case DESERT_SAND, RED_CANYON -> Blocks.SANDSTONE_SLAB;
                    case ARCTIC_SNOW -> Blocks.DEEPSLATE_BRICK_SLAB;
                    default -> Blocks.STONE_BRICK_SLAB;
                };
                canvas.tallCover(-2, 0, 2, 0, 1, wall, slab);
            }
        }
        canvas.finalizeShapes();
    }

    private static void say(CommandSourceStack src, String msg, ChatFormatting color) {
        src.sendSuccess(() -> Component.literal(msg).withStyle(color), false);
    }
}
