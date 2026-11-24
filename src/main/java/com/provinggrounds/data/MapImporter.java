package com.provinggrounds.data;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 檔案: MapImporter.java
 * 目的: 匯入新地圖（伺服器端；管理員指令 /pg map import）。
 *   1. 把地圖的存檔資料夾（裡面有 region 資料夾的那層）放進伺服器根目錄的 map_imports/ 底下，例如 map_imports/新地圖/region/r.0.0.mca。
 *   2. /pg map import info <資料夾>：列出存檔裡有方塊的範圍，建議中心與半徑。
 *   3. /pg map import start <資料夾> <來源中心X> <來源中心Z> <半徑> <地圖名稱>：
 *      - 自動找一個和現有地圖相距約 5000 格的空位（中心對齊區塊，地圖不會被切開）；
 *      - 伺服器開著就能做，每個 tick 最多用 15 毫秒，一個區塊一個區塊地把方塊、方塊實體（箱子內容、告示牌…）、
 *        實體（畫、物品展示框、盔甲架…）搬過來；整個區塊從最低到最高都照來源覆蓋（來源是空氣的地方就是空氣，原本的地形會被清掉）；
 *      - 舊版本的存檔會先用 Minecraft 內建的轉換器升級成 1.20.1 格式；
 *      - 做完自動登記成新的大逃殺地圖（/pg map list 看得到），並提示接下來要做的事（產生俯視圖、設定箱子點位）。
 *   4. /pg map import cancel：中途取消（已經搬過去的區塊不會復原）。
 *   限制：生態域（草地顏色、天氣）不搬，用目的地原本的；只搬主世界（region 資料夾）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class MapImporter {

    private static final String DIR = "map_imports";
    private static final int GRID = 5000, BUDGET_MS = 15;
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private static final class Job {
        final Path world;
        final String name;
        final int mapId, dxChunks, dzChunks;
        final double srcX, srcZ, radius;
        final Deque<ChunkPos> chunks = new ArrayDeque<>();
        final int total;
        final CommandSourceStack src;
        final Map<String, RegionFile> regions = new HashMap<>();
        int done, missing, errors, entities;
        long nanos;
        final long startMs = System.currentTimeMillis();

        Job(Path world, String name, int mapId, double srcX, double srcZ, double radius, int dxChunks, int dzChunks, CommandSourceStack src) {
            this.world = world; this.name = name; this.mapId = mapId; this.srcX = srcX; this.srcZ = srcZ; this.radius = radius;
            this.dxChunks = dxChunks; this.dzChunks = dzChunks; this.src = src;
            int minX = (int) Math.floor((srcX - radius) / 16), maxX = (int) Math.floor((srcX + radius) / 16);
            int minZ = (int) Math.floor((srcZ - radius) / 16), maxZ = (int) Math.floor((srcZ + radius) / 16);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunks.add(new ChunkPos(x, z));
            total = chunks.size();
        }

        @Nullable
        CompoundTag read(String folder, ChunkPos pos) throws IOException {
            String file = "r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca";
            Path p = world.resolve(folder).resolve(file);
            String key = folder + "/" + file;
            RegionFile rf = regions.get(key);
            if (rf == null) {
                if (!Files.exists(p)) return null;
                rf = new RegionFile(p, world.resolve(folder), false);
                regions.put(key, rf);
            }
            if (!rf.hasChunk(pos)) return null;
            try (DataInputStream in = rf.getChunkDataInputStream(pos)) {
                return in == null ? null : NbtIo.read(in);
            }
        }

        void close() {
            for (RegionFile rf : regions.values()) {
                try { rf.close(); } catch (IOException ignored) { }
            }
            regions.clear();
        }
    }

    @Nullable private static Job job;

    private MapImporter() {}

    public static boolean isRunning() { return job != null; }

    // ── 每 tick 處理 ──

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) return;
        Job j = job;
        ServerLevel level = event.getServer().overworld();
        long t0 = System.nanoTime(), limit = BUDGET_MS * 1_000_000L;
        while (!j.chunks.isEmpty() && System.nanoTime() - t0 < limit) {
            ChunkPos from = j.chunks.poll();
            try {
                if (!copyChunk(j, level, from)) j.missing++;
            } catch (Exception e) {
                j.errors++;
                ProvingGroundsMod.LOGGER.warn("Map import: chunk {} failed: {}", from, e.toString());
            }
            j.done++;
        }
        j.nanos += System.nanoTime() - t0;
        if (j.done % 200 < 4 && !j.chunks.isEmpty()) {
            j.src.sendSystemMessage(Component.literal("[匯入地圖] " + j.done + " / " + j.total + " 區塊").withStyle(ChatFormatting.GRAY));
        }
        if (j.chunks.isEmpty()) finish(event.getServer(), j);
    }

    private static void finish(MinecraftServer server, Job j) {
        job = null;
        j.close();
        double cx = j.srcX + j.dxChunks * 16.0, cz = j.srcZ + j.dzChunks * 16.0;
        MapRegistry.get(server).setMap(j.mapId, j.name, cx, cz, j.radius);
        String msg = String.format("[匯入地圖] 完成「%s」：%d 區塊（來源沒有的 %d、失敗 %d）、實體 %d 個，耗時 %.1f 秒。已登記為地圖 #%d，中心 (%.0f, %.0f)、半徑 %.0f。",
            j.name, j.total, j.missing, j.errors, j.entities, (System.currentTimeMillis() - j.startMs) / 1000.0, j.mapId, cx, cz, j.radius);
        ProvingGroundsMod.LOGGER.info(msg);
        j.src.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        j.src.sendSystemMessage(Component.literal("接下來：/pg map render " + j.mapId + "（產生小地圖與大地圖的俯視圖）；到地圖裡用 /pg loot add "
            + j.mapId + " 設定箱子點位；用 /pg map list 確認。").withStyle(ChatFormatting.AQUA));
    }

    /** 搬一個區塊。來源沒有這個區塊回傳 false（目的地那格就清成空氣）。 */
    private static boolean copyChunk(Job j, ServerLevel level, ChunkPos from) throws IOException {
        ChunkPos to = new ChunkPos(from.x + j.dxChunks, from.z + j.dzChunks);
        int dx = j.dxChunks * 16, dz = j.dzChunks * 16;
        LevelChunk dest = level.getChunk(to.x, to.z);

        CompoundTag tag = j.read("region", from);
        Map<Integer, PalettedContainer<BlockState>> sections = new HashMap<>();
        ListTag blockEntities = new ListTag(), legacyEntities = new ListTag();
        if (tag != null) {
            tag = level.getChunkSource().chunkMap.upgradeChunkTag(Level.OVERWORLD, level::getDataStorage, tag, Optional.empty());
            for (Tag t : tag.getList("sections", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) t;
                if (!s.contains("block_states", Tag.TAG_COMPOUND)) continue;
                STATES.parse(NbtOps.INSTANCE, s.getCompound("block_states")).result().ifPresent(c -> sections.put((int) s.getByte("Y"), c));
            }
            blockEntities = tag.getList("block_entities", Tag.TAG_COMPOUND);
            legacyEntities = tag.getList("entities", Tag.TAG_COMPOUND); // 1.17 以前的存檔，實體還在區塊資料裡
        }

        // 清掉目的地的方塊實體與實體（玩家除外）
        for (BlockPos p : new ArrayList<>(dest.getBlockEntities().keySet())) dest.removeBlockEntity(p);
        AABB box = new AABB(to.getMinBlockX(), level.getMinBuildHeight(), to.getMinBlockZ(), to.getMaxBlockX() + 1, level.getMaxBuildHeight(), to.getMaxBlockZ() + 1);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();

        // 方塊：整個區塊從最低到最高照來源覆蓋
        BlockState air = Blocks.AIR.defaultBlockState();
        LevelChunkSection[] destSections = dest.getSections();
        for (int i = 0; i < destSections.length; i++) {
            LevelChunkSection ds = destSections[i];
            PalettedContainer<BlockState> ss = sections.get(level.getSectionYFromSectionIndex(i));
            if (ss == null && ds.hasOnlyAir()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                ds.setBlockState(x, y, z, ss == null ? air : ss.get(x, y, z), false);
            }
            ds.recalcBlockCounts();
        }

        // 方塊實體（箱子內容等）
        Set<BlockPos> withData = new HashSet<>();
        for (Tag t : blockEntities) {
            CompoundTag be = ((CompoundTag) t).copy();
            BlockPos pos = new BlockPos(be.getInt("x") + dx, be.getInt("y"), be.getInt("z") + dz);
            be.putInt("x", pos.getX());
            be.putInt("z", pos.getZ());
            BlockEntity loaded = BlockEntity.loadStatic(pos, dest.getBlockState(pos), be);
            if (loaded != null) { dest.setBlockEntity(loaded); withData.add(pos); }
        }
        // 需要方塊實體、但來源沒有資料的方塊（少見）：建一個空的，避免箱子打不開
        for (int i = 0; i < destSections.length; i++) {
            LevelChunkSection ds = destSections[i];
            if (ds.hasOnlyAir() || !ds.maybeHas(s -> s.getBlock() instanceof EntityBlock)) continue;
            int baseY = level.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState s = ds.getBlockState(x, y, z);
                if (!(s.getBlock() instanceof EntityBlock eb)) continue;
                BlockPos pos = new BlockPos(to.getMinBlockX() + x, baseY + y, to.getMinBlockZ() + z);
                if (withData.contains(pos)) continue;
                BlockEntity fresh = eb.newBlockEntity(pos, s);
                if (fresh != null) dest.setBlockEntity(fresh);
            }
        }

        Heightmap.primeHeightmaps(dest, EnumSet.allOf(Heightmap.Types.class));
        dest.setUnsaved(true);

        // 實體（畫、物品展示框、盔甲架…）
        CompoundTag ent = j.read("entities", from);
        List<CompoundTag> list = new ArrayList<>();
        for (Tag t : legacyEntities) list.add((CompoundTag) t);
        if (ent != null) {
            int ver = NbtUtils.getDataVersion(ent, -1);
            ent = DataFixTypes.ENTITY_CHUNK.update(DataFixers.getDataFixer(), ent, ver, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            for (Tag t : ent.getList("Entities", Tag.TAG_COMPOUND)) list.add((CompoundTag) t);
        }
        for (CompoundTag e : list) {
            CompoundTag copy = e.copy();
            shiftEntity(copy, dx, dz);
            if ("minecraft:player".equals(copy.getString("id"))) continue;
            Entity spawned = EntityType.loadEntityRecursive(copy, level, en -> { en.setUUID(UUID.randomUUID()); return en; });
            if (spawned != null && level.tryAddFreshEntityWithPassengers(spawned)) j.entities++;
        }

        // 光照重算，完成後把區塊重新送給附近的玩家
        ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
        dest.setLightCorrect(false);
        light.initializeLight(dest, false).thenCompose(c -> light.lightChunk(c, false)).thenRunAsync(() -> {
            var players = level.getChunkSource().chunkMap.getPlayers(to, false);
            if (players.isEmpty()) return;
            var packet = new ClientboundLevelChunkWithLightPacket(dest, light, null, null);
            for (ServerPlayer p : players) p.connection.send(packet);
        }, level.getServer());
        return tag != null;
    }

    /** 實體位置往目的地平移（含騎乘的實體、掛在牆上的畫與展示框）。 */
    private static void shiftEntity(CompoundTag e, int dx, int dz) {
        ListTag pos = e.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() == 3) {
            ListTag n = new ListTag();
            n.add(DoubleTag.valueOf(pos.getDouble(0) + dx));
            n.add(DoubleTag.valueOf(pos.getDouble(1)));
            n.add(DoubleTag.valueOf(pos.getDouble(2) + dz));
            e.put("Pos", n);
        }
        if (e.contains("TileX")) { e.putInt("TileX", e.getInt("TileX") + dx); e.putInt("TileZ", e.getInt("TileZ") + dz); }
        e.remove("UUID");
        for (Tag t : e.getList("Passengers", Tag.TAG_COMPOUND)) shiftEntity((CompoundTag) t, dx, dz);
    }

    // ── 指令 ──

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("import").requires(s -> s.hasPermission(2))
            .then(Commands.literal("info").then(Commands.argument("folder", StringArgumentType.string())
                .executes(c -> info(c.getSource(), StringArgumentType.getString(c, "folder")))))
            .then(Commands.literal("start").then(Commands.argument("folder", StringArgumentType.string())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(32, 4000))
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "folder"),
                                    DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "z"),
                                    IntegerArgumentType.getInteger(c, "radius"), StringArgumentType.getString(c, "name")))))))))
            .then(Commands.literal("cancel").executes(c -> {
                Job j = job;
                if (j == null) { c.getSource().sendFailure(Component.literal("目前沒有在匯入地圖。")); return 0; }
                job = null;
                j.close();
                c.getSource().sendSuccess(() -> Component.literal("已取消匯入（已經搬過去的 " + j.done + " 個區塊不會復原）。"), true);
                return 1;
            }));
    }

    /** map_imports/<folder>，不能跑出這個資料夾。 */
    @Nullable
    private static Path folder(CommandSourceStack src, String name) {
        Path base = src.getServer().getServerDirectory().toPath().resolve(DIR).toAbsolutePath().normalize();
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base) || p.equals(base)) { src.sendFailure(Component.literal("資料夾名稱不對。")); return null; }
        if (!Files.isDirectory(p.resolve("region"))) {
            src.sendFailure(Component.literal("找不到 " + DIR + "/" + name + "/region（要放地圖存檔裡有 region 資料夾的那一層）。"));
            return null;
        }
        return p;
    }

    private static int info(CommandSourceStack src, String name) {
        Path world = folder(src, name);
        if (world == null) return 0;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, count = 0, files = 0;
        try (var stream = Files.list(world.resolve("region"))) {
            for (Path p : stream.toList()) {
                Matcher m = REGION.matcher(p.getFileName().toString());
                if (!m.matches()) continue;
                int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
                files++;
                try (RegionFile rf = new RegionFile(p, world.resolve("region"), false)) {
                    for (int i = 0; i < 32; i++) for (int k = 0; k < 32; k++) {
                        ChunkPos cp = new ChunkPos(rx * 32 + i, rz * 32 + k);
                        if (!rf.hasChunk(cp)) continue;
                        count++;
                        minX = Math.min(minX, cp.x); maxX = Math.max(maxX, cp.x); minZ = Math.min(minZ, cp.z); maxZ = Math.max(maxZ, cp.z);
                    }
                }
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("讀取失敗：" + e.getMessage()));
            return 0;
        }
        if (count == 0) { src.sendFailure(Component.literal("region 資料夾裡沒有區塊。")); return 0; }
        int bx0 = minX * 16, bx1 = maxX * 16 + 15, bz0 = minZ * 16, bz1 = maxZ * 16 + 15;
        int cx = (bx0 + bx1) / 2, cz = (bz0 + bz1) / 2, r = Math.max(bx1 - bx0, bz1 - bz0) / 2 + 1;
        String msg = String.format("[匯入地圖] %s：%d 個 region 檔、%d 個區塊，範圍 X %d～%d、Z %d～%d。\n建議：/pg map import start \"%s\" %d %d %d <地圖名稱>"
            + "\n（存檔周圍如果有很多自然地形，請自己縮小半徑、改成地圖真正的中心）", name, files, count, bx0, bx1, bz0, bz1, name, cx, cz, Math.min(r, 4000));
        src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.AQUA), false);
        return count;
    }

    private static int start(CommandSourceStack src, String name, double x, double z, int radius, String mapName) {
        if (job != null) { src.sendFailure(Component.literal("已經在匯入「" + job.name + "」。")); return 0; }
        if (com.provinggrounds.modes.BattleRoyaleMode.get().isRunning()) { src.sendFailure(Component.literal("大逃殺進行中，請結束後再匯入。")); return 0; }
        Path world = folder(src, name);
        if (world == null) return 0;
        MapRegistry reg = MapRegistry.get(src.getServer());
        int id = 1;
        for (MapRegistry.BRMap m : reg.all()) id = Math.max(id, m.id + 1);
        double[] slot = freeSlot(reg, radius);
        int dxc = (int) Math.round((slot[0] - x) / 16.0), dzc = (int) Math.round((slot[1] - z) / 16.0);
        Job j = new Job(world, mapName, id, x, z, radius, dxc, dzc, src);
        job = j;
        int fid = id;
        src.sendSuccess(() -> Component.literal(String.format("[匯入地圖] 開始匯入「%s」→ 地圖 #%d，目的地中心 (%.0f, %.0f)、半徑 %d、共 %d 區塊。"
            + "伺服器可以照常使用，完成後聊天欄會通知。", mapName, fid, x + dxc * 16.0, z + dzc * 16.0, radius, j.total)).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    /** 找一個空位：以 5000 格為間隔的格點，和現有每張地圖的距離都要大於「兩邊半徑 + 1000」。 */
    private static double[] freeSlot(MapRegistry reg, int radius) {
        for (int ring = 1; ring <= 20; ring++) {
            for (int i = -ring; i <= ring; i++) for (int k = -ring; k <= ring; k++) {
                if (Math.max(Math.abs(i), Math.abs(k)) != ring) continue;
                double cx = i * (double) GRID, cz = k * (double) GRID;
                boolean ok = true;
                for (MapRegistry.BRMap m : reg.all()) {
                    double d = Math.hypot(m.centerX - cx, m.centerZ - cz);
                    if (d < m.radius + radius + 1000) { ok = false; break; }
                }
                if (ok) return new double[] {cx, cz};
            }
        }
        return new double[] {GRID * 21.0, 0};
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pg").then(Commands.literal("map").then(command())));
    }
}
