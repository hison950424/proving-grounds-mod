package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.admin.build.BuildingCanvas;
import com.provinggrounds.block.AltarBlock;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.data.BRMapLayout.CoverSpot;
import com.provinggrounds.data.BRMapLayout.PlacedBuilding;
import com.provinggrounds.data.BRMapLayout.Plan;
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
 *   - 支援一次依序生成 5 張區域密集主題地圖（半徑 300 / 400 / 500 / 600 / 700 格）或單獨生成指定地圖。
 *   - 自動尋覓距現有地圖 >= 1200 格且區塊對齊的 5000 格空位：
 *     1. 分批產生自然高低差、主題生態地表、城區平台、主幹道／跨河橋樑；
 *     2. 逐棟建立複用自 52 棟無木箱建築庫的密集建築群並登記所有物資點進地圖；
 *     3. 於安全廣場設置多座復活祭壇（AltarBlock + AltarLedger）與野外車輛／沙包／掩體；
 *     4. 自動呼叫 MapRenderJob 繪製俯視圖並同步大廳地圖預覽。
 */
public final class BRMapGenerator {

    private static final int GRID = 5000;
    private static final int CHUNK_BUDGET_MS = 18;
    private static final int COVER_PER_TICK = 12;

    private enum Phase { TERRAIN, BUILDINGS, ALTARS_AND_COVER, WAIT_RENDER }

    private static final class Job {
        final ServerLevel level;
        final Preset preset;
        final CommandSourceStack src;
        final int mapId;
        final Plan plan;
        final Deque<ChunkPos> terrainChunks = new ArrayDeque<>();
        final int totalChunks;
        int doneChunks;
        int buildingIdx;
        int lootAdded;
        boolean altarsPlaced;
        int coverIdx;
        boolean renderStarted;
        Phase phase = Phase.TERRAIN;
        final long startMs = System.currentTimeMillis();

        Job(ServerLevel level, Preset preset, CommandSourceStack src, int mapId, Plan plan) {
            this.level = level;
            this.preset = preset;
            this.src = src;
            this.mapId = mapId;
            this.plan = plan;

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

    private static final Deque<Preset> QUEUE = new ArrayDeque<>();
    @Nullable private static Job current;
    @Nullable private static CommandSourceStack queueSource;

    private BRMapGenerator() {}

    public static boolean isRunning() {
        return current != null || !QUEUE.isEmpty();
    }

    public static String statusText() {
        if (current == null) return "目前沒有在生成大逃殺地圖。";
        return String.format("正在生成 #%d「%s」（半徑 %d）：階段 %s｜地形 %d/%d｜建築 %d/%d｜佇列尚餘 %d 張",
            current.mapId, current.preset.displayName(), current.preset.radius(), current.phase.name(),
            current.doneChunks, current.totalChunks, current.buildingIdx, current.plan.buildings.size(), QUEUE.size());
    }

    @Nullable
    public static String startSingle(MinecraftServer server, Preset preset, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        QUEUE.clear();
        queueSource = src;
        launchNext(server, preset, src);
        return null;
    }

    @Nullable
    public static String startAll(MinecraftServer server, CommandSourceStack src) {
        String guard = checkReady(src);
        if (guard != null) return guard;
        QUEUE.clear();
        QUEUE.addAll(BRMapPresets.all());
        queueSource = src;
        Preset first = QUEUE.poll();
        if (first != null) {
            say(src, String.format("[生成地圖] 已排程依序生成全部 %d 張地圖（300 / 400 / 500 / 600 / 700 格，各自帶密集區域建築＋物資點＋復活祭壇）。",
                BRMapPresets.all().size()), ChatFormatting.AQUA);
            launchNext(server, first, src);
        }
        return null;
    }

    @Nullable
    private static String checkReady(CommandSourceStack src) {
        if (!GameManager.get().isInLobby() || BattleRoyaleMode.get().isRunning()) {
            return "大逃殺進行中，請回到大廳後再生成地圖。";
        }
        if (isRunning()) return statusText();
        if (MapRenderJob.isRunning()) return "俯視圖繪製進行中，請等完成後再開始生成。";
        if (MarkerScanJob.isRunning()) return "掃描作業進行中，請等完成後再開始生成。";
        return null;
    }

    public static boolean cancel(CommandSourceStack src) {
        if (!isRunning()) return false;
        QUEUE.clear();
        if (current != null) {
            say(src, String.format("[生成地圖] 已取消生成「%s」（# %d）與後續排程。",
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

    private static void launchNext(MinecraftServer server, Preset preset, CommandSourceStack src) {
        MapRegistry reg = MapRegistry.get(server);
        int id = 1;
        for (MapRegistry.BRMap m : reg.all()) id = Math.max(id, m.id + 1);
        int[] slot = freeSlot(reg, preset.radius());
        reg.setMap(id, preset.displayName(), slot[0], slot[1], preset.radius());

        Plan plan = BRMapLayout.buildPlan(preset, slot[0], slot[1]);
        Job job = new Job(server.overworld(), preset, src, id, plan);
        current = job;
        say(src, String.format("[生成地圖] 開始建立 #%d「%s」：中心 (%d, %d)、半徑 %d 格、%d 個密集城區、%d 棟建築、%d 座復活祭壇（地形共 %d 區塊）…",
            id, preset.displayName(), slot[0], slot[1], preset.radius(),
            plan.districts.size(), plan.buildings.size(), plan.altars.size(), job.totalChunks), ChatFormatting.YELLOW);
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
            case BUILDINGS -> tickBuildings(job);
            case ALTARS_AND_COVER -> tickAltarsAndCover(job);
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
            job.phase = Phase.BUILDINGS;
            say(job.src, String.format("[#%d %s] 地形完成，開始建造 %d 棟密集建築群…",
                job.mapId, job.preset.displayName(), job.plan.buildings.size()), ChatFormatting.GRAY);
        }
        return false;
    }

    private static boolean tickBuildings(Job job) {
        if (job.buildingIdx >= job.plan.buildings.size()) {
            job.phase = Phase.ALTARS_AND_COVER;
            return false;
        }
        PlacedBuilding b = job.plan.buildings.get(job.buildingIdx++);
        BuildingCanvas canvas = new BuildingCanvas(job.level, new BlockPos(b.originX(), b.y(), b.originZ()), b.facing());
        b.bp().builder().build(canvas);
        canvas.finalizeShapes();

        MapRegistry reg = MapRegistry.get(job.level.getServer());
        MapRegistry.BRMap map = reg.getMap(job.mapId);
        if (map != null) {
            for (BlockPos lp : canvas.lootPoints()) {
                if (map.contains(lp.getX() + 0.5, lp.getZ() + 0.5) && reg.addPoint(map, lp)) {
                    job.lootAdded++;
                }
            }
        }
        if (job.buildingIdx % 6 == 0 || job.buildingIdx == job.plan.buildings.size()) {
            say(job.src, String.format("[#%d %s] 建築進度 %d / %d 棟（已登記物資點 %d）",
                job.mapId, job.preset.displayName(), job.buildingIdx, job.plan.buildings.size(), job.lootAdded), ChatFormatting.GRAY);
        }
        return false;
    }

    private static boolean tickAltarsAndCover(Job job) {
        if (!job.altarsPlaced) {
            AltarLedger ledger = AltarLedger.get(job.level.getServer());
            for (BlockPos altarPos : job.plan.altars) {
                buildAltarPlaza(job.level, altarPos, job.plan.biomeAtWorld(altarPos.getX(), altarPos.getZ()));
                ledger.add(altarPos);
            }
            job.altarsPlaced = true;
            say(job.src, String.format("[#%d %s] 已設置 %d 座復活祭壇，正在佈置 %d 處野外戰術掩體…",
                job.mapId, job.preset.displayName(), job.plan.altars.size(), job.plan.covers.size()), ChatFormatting.GRAY);
            return false;
        }
        for (int i = 0; i < COVER_PER_TICK && job.coverIdx < job.plan.covers.size(); i++) {
            buildOutdoorCover(job.level, job.plan, job.plan.covers.get(job.coverIdx++));
        }
        if (job.coverIdx >= job.plan.covers.size()) {
            job.phase = Phase.WAIT_RENDER;
        }
        return false;
    }

    private static boolean tickWaitRender(Job job) {
        if (!job.renderStarted) {
            job.renderStarted = true;
            MinecraftServer server = job.level.getServer();
            MapRegistry.BRMap map = MapRegistry.get(server).getMap(job.mapId);
            double secs = (System.currentTimeMillis() - job.startMs) / 1000.0;

            MutableComponent doneMsg = Component.literal(String.format(
                "[生成地圖] 地圖 #%d「%s」（半徑 %d 格）建造完成（耗時 %.1f 秒）：%d 棟建築、%d 個物資點、%d 座復活祭壇、中心 (%d, %d) [點此傳送]。",
                job.mapId, job.preset.displayName(), job.preset.radius(), secs,
                job.plan.buildings.size(), job.lootAdded, job.plan.altars.size(), job.plan.centerX, job.plan.centerZ))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/tp @s " + job.plan.centerX + " 120 " + job.plan.centerZ))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("傳送至地圖 #" + job.mapId + " 中央上空"))));
            job.src.sendSuccess(() -> doneMsg, true);
            ProvingGroundsMod.LOGGER.info("Generated BR map #{} ({}) at ({}, {}), radius={}, buildings={}, loot={}, altars={}",
                job.mapId, job.preset.key(), job.plan.centerX, job.plan.centerZ, job.preset.radius(),
                job.plan.buildings.size(), job.lootAdded, job.plan.altars.size());

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

        for (BlockPos p : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(p);
        }
        AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
            cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            e.discard();
        }

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
                double roadDist = plan.roadDistance(wx, wz);
                boolean road = roadDist <= 0.0;
                boolean roadEdge = roadDist > -0.9 && roadDist <= 0.0;

                if (road && sy <= waterLevel) {
                    isBridge[lx][lz] = true;
                    isBridgeRail[lx][lz] = roadEdge && ((wx + wz) & 1) == 0;
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
                    if (road) {
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
        BlockState stone = Blocks.STONE.defaultBlockState();
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
        chunk.setUnsaved(true);
    }

    private static boolean inDistrictPlaza(Plan plan, int wx, int wz) {
        for (BRMapLayout.DistrictBox d : plan.districts) {
            if (wx >= d.minX() && wx <= d.maxX() && wz >= d.minZ() && wz <= d.maxZ()) return true;
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

    // ── 復活祭壇廣場與野外戰術掩體 ──────────────────────────────────────────

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
        // 四角燈柱＋L 形掩體牆
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

    private static void buildOutdoorCover(ServerLevel level, Plan plan, CoverSpot c) {
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
            default -> {
                if (c.biome() == RegionBiome.DESERT_SAND || c.biome() == RegionBiome.RED_CANYON) {
                    canvas.box(-1, 1, -1, 1, 2, 1, Blocks.RED_SANDSTONE);
                    canvas.set(0, 3, 0, Blocks.RED_SANDSTONE_SLAB);
                } else {
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
                    canvas.box(-2, 4, -2, 2, 5, 2, leaves);
                    canvas.box(-1, 6, -1, 1, 6, 1, leaves);
                    canvas.pillar(0, 1, 4, 0, trunk);
                }
            }
        }
        canvas.finalizeShapes();
    }

    private static void say(CommandSourceStack src, String msg, ChatFormatting color) {
        src.sendSuccess(() -> Component.literal(msg).withStyle(color), false);
    }
}
