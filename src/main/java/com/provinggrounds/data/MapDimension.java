package com.provinggrounds.data;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 檔案: MapDimension.java
 * 目的: 「每張地圖一個維度」可行性實驗（計畫書 5 的 H4；只做第一張地圖「經典」；伺服器端）。
 *   兩個維度（資料包 data/provinggrounds/dimension/，空的虛空世界）：
 *     provinggrounds:classic_template ＝ 地圖原樣（只讀，當還原的來源）
 *     provinggrounds:classic_play     ＝ 比賽用（可以被破壞，每場結束後還原）
 *   做法：不動存檔檔案，伺服器開著的時候用指令分批複製方塊。
 *     copy  ：主世界經典地圖範圍 → 樣板＋比賽維度（第一次建立用）
 *     reset ：樣板 → 比賽維度，只比對差異、改回不一樣的方塊（含方塊實體資料，例如箱子內容），並清掉掉落物與投射物
 *   每個 tick 最多用 budget 毫秒（預設 10，伺服器一個 tick 是 50 毫秒），做完回報總耗時、區塊數、改了幾個方塊。
 *   stats：各維度的平均 tick 時間、載入區塊數，以及記憶體使用量，用來和現行方案（五張地圖都在主世界、玻璃修復）比較。
 *   已知限制（實驗版）：只處理方塊與方塊實體；畫、物品展示框、盔甲架這類「實體裝飾」沒有複製。
 *   這個實驗不改大逃殺的對局流程（對局仍在主世界），要不要全面改成每張地圖一個維度由使用者決定。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class MapDimension {

    public static final ResourceKey<Level> TEMPLATE = key("classic_template"), PLAY = key("classic_play");

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ProvingGroundsMod.MOD_ID, path));
    }

    /** 一個分批工作。 */
    private static final class Job {
        final String name;
        final ServerLevel src;
        final List<ServerLevel> dsts;
        final boolean clearEntities;
        final Deque<long[]> chunks = new ArrayDeque<>();
        final int total;
        @Nullable final CommandSourceStack reporter;
        long nanos, changed;
        int ticks, done;
        final long startMs = System.currentTimeMillis();

        Job(String name, ServerLevel src, List<ServerLevel> dsts, boolean clearEntities, int cx, int cz, int radius, @Nullable CommandSourceStack reporter) {
            this.name = name; this.src = src; this.dsts = dsts; this.clearEntities = clearEntities; this.reporter = reporter;
            int minX = (cx - radius) >> 4, maxX = (cx + radius) >> 4, minZ = (cz - radius) >> 4, maxZ = (cz + radius) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunks.add(new long[] {x, z});
            total = chunks.size();
        }
    }

    @Nullable private static Job job;
    private static int budgetMs = 10;

    private MapDimension() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) return;
        Job j = job;
        long t0 = System.nanoTime(), limit = budgetMs * 1_000_000L;
        while (!j.chunks.isEmpty() && System.nanoTime() - t0 < limit) {
            long[] c = j.chunks.poll();
            for (ServerLevel dst : j.dsts) j.changed += copyChunk(j.src, dst, (int) c[0], (int) c[1], j.clearEntities);
            j.done++;
        }
        j.nanos += System.nanoTime() - t0;
        j.ticks++;
        if (j.ticks % 100 == 0 && j.reporter != null) {
            j.reporter.sendSystemMessage(Component.literal("[維度實驗] " + j.name + " " + j.done + "/" + j.total + " 區塊").withStyle(ChatFormatting.GRAY));
        }
        if (j.chunks.isEmpty()) {
            job = null;
            String msg = String.format("[維度實驗] %s 完成：%d 區塊、改了 %d 個方塊、實際運算 %.1f 秒、經過 %d tick（%.1f 秒）",
                j.name, j.total, j.changed, j.nanos / 1e9, j.ticks, (System.currentTimeMillis() - j.startMs) / 1000.0);
            ProvingGroundsMod.LOGGER.info(msg);
            if (j.reporter != null) j.reporter.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        }
    }

    /** 把 src 的一個區塊抄到 dst：只改不一樣的方塊；回傳改了幾個。 */
    private static int copyChunk(ServerLevel src, ServerLevel dst, int cx, int cz, boolean clearEntities) {
        LevelChunk s = src.getChunk(cx, cz), d = dst.getChunk(cx, cz);
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = Math.min(s.getSections().length, d.getSections().length);
        for (int i = 0; i < n; i++) {
            LevelChunkSection ss = s.getSection(i), ds = d.getSection(i);
            if (ss.hasOnlyAir() && ds.hasOnlyAir()) continue;
            int baseY = src.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState a = ss.getBlockState(x, y, z);
                if (a == ds.getBlockState(x, y, z)) continue;
                pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                dst.setBlock(pos, a, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                changed++;
            }
        }
        // 方塊實體資料（箱子內容、告示牌文字…）一律以來源為準
        for (BlockEntity be : new ArrayList<>(s.getBlockEntities().values())) {
            BlockEntity target = dst.getBlockEntity(be.getBlockPos());
            if (target == null || target.getType() != be.getType()) continue;
            CompoundTag tag = be.saveWithoutMetadata();
            target.load(tag);
            target.setChanged();
            dst.sendBlockUpdated(be.getBlockPos(), target.getBlockState(), target.getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (clearEntities) {
            AABB box = new AABB(cx << 4, dst.getMinBuildHeight(), cz << 4, (cx << 4) + 16, dst.getMaxBuildHeight(), (cz << 4) + 16);
            for (Entity e : dst.getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof Projectile)) e.discard();
        }
        return changed;
    }

    // ── 指令 /pg dimtest ──

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pg").then(Commands.literal("dimtest").requires(s -> s.hasPermission(2))
            .then(Commands.literal("copy").executes(c -> start(c.getSource(), true, -1))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2000)).executes(c -> start(c.getSource(), true, IntegerArgumentType.getInteger(c, "radius")))))
            .then(Commands.literal("reset").executes(c -> start(c.getSource(), false, -1))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2000)).executes(c -> start(c.getSource(), false, IntegerArgumentType.getInteger(c, "radius")))))
            .then(Commands.literal("cancel").executes(c -> {
                job = null;
                c.getSource().sendSuccess(() -> Component.literal("已取消"), true);
                return 1;
            }))
            .then(Commands.literal("budget").then(Commands.argument("ms", IntegerArgumentType.integer(1, 45)).executes(c -> {
                budgetMs = IntegerArgumentType.getInteger(c, "ms");
                c.getSource().sendSuccess(() -> Component.literal("每 tick 最多 " + budgetMs + " 毫秒"), true);
                return 1;
            })))
            .then(Commands.literal("tp").executes(c -> tp(c.getSource(), PLAY)))
            .then(Commands.literal("tptemplate").executes(c -> tp(c.getSource(), TEMPLATE)))
            .then(Commands.literal("back").executes(c -> tp(c.getSource(), Level.OVERWORLD)))
            .then(Commands.literal("stats").executes(c -> stats(c.getSource())))));
    }

    private static MapRegistry.BRMap classic(MinecraftServer server) {
        return MapRegistry.get(server).getMap(1);
    }

    private static int start(CommandSourceStack src, boolean copy, int radiusOverride) {
        MinecraftServer server = src.getServer();
        ServerLevel tpl = server.getLevel(TEMPLATE), play = server.getLevel(PLAY);
        if (tpl == null || play == null) { src.sendFailure(Component.literal("找不到實驗維度（資料包沒載入？）")); return 0; }
        if (job != null) { src.sendFailure(Component.literal("已經有工作在跑：" + job.name)); return 0; }
        MapRegistry.BRMap m = classic(server);
        if (m == null) { src.sendFailure(Component.literal("找不到地圖 1（經典）")); return 0; }
        int r = radiusOverride > 0 ? radiusOverride : (int) Math.ceil(m.radius);
        job = copy
            ? new Job("複製（主世界 → 樣板＋比賽）", server.overworld(), List.of(tpl, play), false, (int) m.centerX, (int) m.centerZ, r, src)
            : new Job("還原（樣板 → 比賽）", tpl, List.of(play), true, (int) m.centerX, (int) m.centerZ, r, src);
        int total = job.total;
        src.sendSuccess(() -> Component.literal("[維度實驗] 開始：" + job.name + "，中心 (" + (int) m.centerX + ", " + (int) m.centerZ
            + ")、半徑 " + r + "、共 " + total + " 區塊，每 tick 最多 " + budgetMs + " 毫秒"), true);
        return 1;
    }

    private static int tp(CommandSourceStack src, ResourceKey<Level> dim) {
        ServerPlayer p = src.getPlayer();
        ServerLevel level = src.getServer().getLevel(dim);
        if (p == null || level == null) { src.sendFailure(Component.literal("要由玩家執行，或維度不存在")); return 0; }
        MapRegistry.BRMap m = classic(src.getServer());
        double x = dim == Level.OVERWORLD ? p.getX() : m != null ? m.centerX : 0, z = dim == Level.OVERWORLD ? p.getZ() : m != null ? m.centerZ : 0;
        int y = level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)
            .getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x) & 15, (int) Math.floor(z) & 15) + 2;
        p.teleportTo(level, x, Math.max(y, level.getMinBuildHeight() + 2), z, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int stats(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        StringBuilder sb = new StringBuilder(String.format("[維度實驗] 全部平均 tick %.2f ms", server.getAverageTickTime()));
        for (ServerLevel level : server.getAllLevels()) {
            long[] times = server.getTickTime(level.dimension());
            double avg = 0;
            if (times != null && times.length > 0) { for (long t : times) avg += t; avg = avg / times.length / 1e6; }
            sb.append(String.format("\n  %s：tick %.2f ms、載入區塊 %d、實體 %d", level.dimension().location(), avg,
                level.getChunkSource().getLoadedChunksCount(), countEntities(level)));
        }
        Runtime rt = Runtime.getRuntime();
        sb.append(String.format("\n  記憶體：%d / %d MB", (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
        String text = sb.toString();
        ProvingGroundsMod.LOGGER.info(text);
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int countEntities(ServerLevel level) {
        int n = 0;
        for (Entity ignored : level.getAllEntities()) n++;
        return n;
    }
}
