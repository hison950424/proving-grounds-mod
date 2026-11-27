package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 檔案: ArenaMapGenerator.java
 * 目的: 伺服器端團隊地圖生成器（指令 /pg arena map generate 與 regen）。
 *   - 依 Tick 預算分塊建造室內雙層地圖「黑曜地堡」（ObsidianBunkerBlueprint），避免主執行緒卡頓。
 *   - 完工後自動將全部紅藍重生點、佔點據點 A/B/C、基地出口屏障、槍王 44 個重生點寫入 {@link ArenaMapRegistry} 並切換為目前場地，
 *     管理員不需再手動設定任何座標即可直接開局團隊死鬥／佔點／槍王之王。
 *   - 嚴禁使用任何木頭儲物箱／陷阱箱／木桶，掩體皆改為深板岩／砂包／貨櫃等實戰結構方塊。
 */
public final class ArenaMapGenerator {

    private static final int CHUNK_BUDGET_MS = 16;

    private static final class Job {
        final ServerLevel level;
        final CommandSourceStack src;
        final BlockPos origin;
        final ObsidianBunkerBlueprint.Built built;
        final Deque<ChunkPos> chunks = new ArrayDeque<>();
        final int totalChunks;
        int doneChunks;
        int shapeIdx;
        final int minCX, minCZ, maxCX, maxCZ;
        final boolean isRegen;
        final long startMs = System.currentTimeMillis();

        Job(ServerLevel level, CommandSourceStack src, BlockPos origin, ObsidianBunkerBlueprint.Built built, boolean isRegen) {
            this.level = level;
            this.src = src;
            this.origin = origin;
            this.built = built;
            this.isRegen = isRegen;
            this.minCX = origin.getX() >> 4;
            this.maxCX = (origin.getX() + ObsidianBunkerBlueprint.WIDTH - 1) >> 4;
            this.minCZ = origin.getZ() >> 4;
            this.maxCZ = (origin.getZ() + ObsidianBunkerBlueprint.DEPTH - 1) >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
            this.totalChunks = chunks.size();
        }
    }

    @Nullable
    private static Job current;

    private ArenaMapGenerator() {}

    public static boolean isRunning() {
        return current != null;
    }

    public static String statusText() {
        Job j = current;
        if (j == null) return "[團隊地圖] 目前沒有正在生成的場地。";
        return String.format("[團隊地圖] 正在建立「%s」：區塊 %d/%d",
            ObsidianBunkerBlueprint.MAP_NAME, j.doneChunks, j.totalChunks);
    }

    public static void reset() {
        current = null;
    }

    public static boolean cancel(CommandSourceStack src) {
        if (current == null) return false;
        current = null;
        say(src, "[團隊地圖] 已取消生成作業。", ChatFormatting.YELLOW);
        com.provinggrounds.core.TaskProgress.clear(src.getServer());
        return true;
    }

    /** 開始生成（或重建）黑曜地堡。成功啟動回傳 null，否則回傳錯誤訊息。 */
    @Nullable
    public static String startObsidianBunker(MinecraftServer server, CommandSourceStack src, boolean regen) {
        if (!GameManager.get().isInLobby() || ArenaManager.isRunning() || BattleRoyaleMode.get().isRunning()) {
            return "對戰進行中無法建立團隊地圖，請先回到大廳。";
        }
        if (current != null) return statusText();

        ArenaMapRegistry areg = ArenaMapRegistry.get(server);
        ArenaMapRegistry.ArenaMapDef existing = areg.getMap(ObsidianBunkerBlueprint.MAP_ID);
        BlockPos origin;
        if (regen && existing != null) {
            origin = existing.origin();
        } else if (existing != null && !regen) {
            origin = existing.origin();
        } else {
            origin = findIsolatedOrigin(server);
        }

        ObsidianBunkerBlueprint.Built built = ObsidianBunkerBlueprint.build();
        current = new Job(server.overworld(), src, origin, built, existing != null);
        say(src, String.format("[團隊地圖] %s「%s」(%s) 於座標 (%d, %d, %d)，尺寸 %d×%d×%d…",
            existing != null ? "重建覆蓋" : "開始生成",
            ObsidianBunkerBlueprint.MAP_NAME, ObsidianBunkerBlueprint.MAP_ID,
            origin.getX(), origin.getY(), origin.getZ(),
            ObsidianBunkerBlueprint.WIDTH, ObsidianBunkerBlueprint.HEIGHT, ObsidianBunkerBlueprint.DEPTH),
            ChatFormatting.YELLOW);
        return null;
    }

    private static BlockPos findIsolatedOrigin(MinecraftServer server) {
        MapRegistry brMaps = MapRegistry.get(server);
        for (int step = 0; step < 32; step++) {
            int x = ((2400 + step * 512) >> 4) << 4;
            int z = ((-2400) >> 4) << 4;
            boolean ok = true;
            for (MapRegistry.BRMap m : brMaps.all()) {
                double d = Math.hypot((x + ObsidianBunkerBlueprint.WIDTH * 0.5) - m.centerX,
                    (z + ObsidianBunkerBlueprint.DEPTH * 0.5) - m.centerZ);
                if (d < m.radius + 300) {
                    ok = false;
                    break;
                }
            }
            if (ok) return new BlockPos(x, 64, z);
        }
        return new BlockPos(2400, 64, -2400);
    }

    public static void tickCurrent() {
        Job j = current;
        if (j == null) return;
        try {
            long deadline = System.nanoTime() + CHUNK_BUDGET_MS * 1_000_000L;
            int totalCols = ObsidianBunkerBlueprint.WIDTH * ObsidianBunkerBlueprint.DEPTH;
            if (!j.chunks.isEmpty()) {
                com.provinggrounds.core.TaskProgress.update(j.level.getServer(), "生成團隊地圖「" + ObsidianBunkerBlueprint.MAP_NAME + "」",
                    String.format("放置方塊 %d / %d 區塊", j.doneChunks, j.totalChunks),
                    0.8f * j.doneChunks / Math.max(1, j.totalChunks));
            } else {
                com.provinggrounds.core.TaskProgress.update(j.level.getServer(), "生成團隊地圖「" + ObsidianBunkerBlueprint.MAP_NAME + "」",
                    "修正玻璃與欄杆連接", 0.8f + 0.2f * j.shapeIdx / Math.max(1, totalCols));
            }
            if (!j.chunks.isEmpty()) {
                while (!j.chunks.isEmpty() && System.nanoTime() < deadline) {
                    ChunkPos cp = j.chunks.poll();
                    placeChunk(j, cp);
                    j.doneChunks++;
                }
                if (j.chunks.isEmpty()) {
                    clearEntitiesInside(j);
                    say(j.src, "[團隊地圖] 方塊放置完成，正在修正玻璃與欄杆連接形狀…", ChatFormatting.GRAY);
                }
                return;
            }

            // 第二階段：逐塊更新形狀（確保玻璃片、鐵欄杆、樓梯連接完美）
            int totalColumns = ObsidianBunkerBlueprint.WIDTH * ObsidianBunkerBlueprint.DEPTH;
            while (j.shapeIdx < totalColumns && System.nanoTime() < deadline) {
                int lx = j.shapeIdx / ObsidianBunkerBlueprint.DEPTH;
                int lz = j.shapeIdx % ObsidianBunkerBlueprint.DEPTH;
                for (int ly = 0; ly < ObsidianBunkerBlueprint.HEIGHT; ly++) {
                    ObsidianBunkerBlueprint.Cell cell = j.built.grid[lx][ly][lz];
                    if (needsShapeUpdate(cell)) {
                        BlockPos pos = j.origin.offset(lx, ly, lz);
                        BlockState cur = j.level.getBlockState(pos);
                        BlockState shaped = Block.updateFromNeighbourShapes(cur, j.level, pos);
                        if (shaped != cur) {
                            j.level.setBlock(pos, shaped, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
                j.shapeIdx++;
            }

            if (j.shapeIdx >= totalColumns) {
                finish(j);
                current = null;
            }
        } catch (Exception e) {
            ProvingGroundsMod.LOGGER.error("ArenaMapGenerator failed", e);
            say(j.src, "[團隊地圖] 生成失敗：" + e.getMessage(), ChatFormatting.RED);
            com.provinggrounds.core.TaskProgress.clear(j.level.getServer());
            current = null;
        }
    }

    private static void placeChunk(Job j, ChunkPos cp) {
        j.level.getChunk(cp.x, cp.z);
        int xStart = Math.max(0, (cp.x << 4) - j.origin.getX());
        int xEnd = Math.min(ObsidianBunkerBlueprint.WIDTH - 1, ((cp.x << 4) + 15) - j.origin.getX());
        int zStart = Math.max(0, (cp.z << 4) - j.origin.getZ());
        int zEnd = Math.min(ObsidianBunkerBlueprint.DEPTH - 1, ((cp.z << 4) + 15) - j.origin.getZ());

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        BlockState subFloor = Blocks.DEEPSLATE.defaultBlockState();
        BlockState clearAir = Blocks.AIR.defaultBlockState();

        for (int lx = xStart; lx <= xEnd; lx++) {
            for (int lz = zStart; lz <= zEnd; lz++) {
                int wx = j.origin.getX() + lx;
                int wz = j.origin.getZ() + lz;
                // 地堡底盤下襯一層深板岩防地底穿透；頂蓋上方清乾淨避免山丘覆蓋
                mp.set(wx, j.origin.getY() - 1, wz);
                j.level.setBlock(mp, subFloor, flags);
                for (int ly = 0; ly < ObsidianBunkerBlueprint.HEIGHT; ly++) {
                    mp.set(wx, j.origin.getY() + ly, wz);
                    BlockState state = mapBlock(j.built.grid[lx][ly][lz], lx, ly, lz);
                    j.level.setBlock(mp, state, flags);
                }
                for (int top = 0; top < 6; top++) {
                    mp.set(wx, j.origin.getY() + ObsidianBunkerBlueprint.HEIGHT + top, wz);
                    if (!j.level.getBlockState(mp).isAir()) {
                        j.level.setBlock(mp, clearAir, flags);
                    }
                }
            }
        }
    }

    private static void clearEntitiesInside(Job j) {
        AABB box = new AABB(
            j.origin.getX(), j.origin.getY(), j.origin.getZ(),
            j.origin.getX() + ObsidianBunkerBlueprint.WIDTH,
            j.origin.getY() + ObsidianBunkerBlueprint.HEIGHT,
            j.origin.getZ() + ObsidianBunkerBlueprint.DEPTH
        );
        for (Entity e : j.level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
            e.discard();
        }
    }

    private static void finish(Job j) {
        ArenaMapRegistry reg = ArenaMapRegistry.get(j.level.getServer());
        ArenaMapRegistry.ArenaMapDef def = j.built.toMapDef(j.origin);
        reg.put(def);
        reg.setActive(def.id());

        long sec = Math.max(1, (System.currentTimeMillis() - j.startMs) / 1000);
        say(j.src, String.format(
            "[團隊地圖] 「%s」完成（耗時 %d 秒）並已自動設為目前場地！",
            def.name(), sec), ChatFormatting.GREEN);
        say(j.src, String.format(
            "  紅隊重生點 %d 個｜藍隊重生點 %d 個｜佔點據點 %d 個（A/B/C）｜基地屏障 %d 道｜槍王重生點 %d 個",
            def.redSpawns().size(), def.blueSpawns().size(), def.domPoints().size(),
            def.barriers().size(), def.gkSpawns().size()), ChatFormatting.AQUA);

        Component tpBtn = Component.literal("  [點我傳送到黑曜地堡參觀]")
            .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pg arena map tp " + def.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("執行 /pg arena map tp " + def.id()))));
        j.src.sendSuccess(() -> tpBtn, false);
        com.provinggrounds.core.TaskProgress.done(j.level.getServer(), "生成團隊地圖「" + def.name() + "」", "完成");
        com.provinggrounds.network.MenuRequestPacket.sendToAll(j.level.getServer());
        com.provinggrounds.core.LobbyManager.get().broadcast();
    }

    private static boolean needsShapeUpdate(ObsidianBunkerBlueprint.Cell c) {
        return c == ObsidianBunkerBlueprint.Cell.GLASS_PANE
            || c == ObsidianBunkerBlueprint.Cell.IRON_BARS
            || c == ObsidianBunkerBlueprint.Cell.COVER_HALF
            || c == ObsidianBunkerBlueprint.Cell.STAIR_E
            || c == ObsidianBunkerBlueprint.Cell.STAIR_W
            || c == ObsidianBunkerBlueprint.Cell.STAIR_N
            || c == ObsidianBunkerBlueprint.Cell.STAIR_S;
    }

    private static BlockState mapBlock(ObsidianBunkerBlueprint.Cell cell, int lx, int ly, int lz) {
        return switch (cell) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WALL_OUTER -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case WALL_INNER -> {
                if (ly == 1 || ly == 7) yield Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                yield ((lx + lz) % 5 == 0) ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            }
            case WALL_RED -> Blocks.RED_CONCRETE.defaultBlockState();
            case WALL_BLUE -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case CEILING -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            case FLOOR_MAIN -> Blocks.SMOOTH_STONE.defaultBlockState();
            case FLOOR_CORRIDOR -> ((lx + lz) % 2 == 0) ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState();
            case FLOOR_METAL -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            case FLOOR_RED -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case FLOOR_BLUE -> Blocks.BLUE_TERRACOTTA.defaultBlockState();
            case FLOOR_UPPER -> ((lx + lz) % 2 == 0) ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            case POINT_FLOOR_A, POINT_FLOOR_B, POINT_FLOOR_C -> Blocks.IRON_BLOCK.defaultBlockState();
            case LIGHT -> Blocks.SEA_LANTERN.defaultBlockState();
            case LIGHT_WARM -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
            case REACTOR_CORE -> Blocks.SEA_LANTERN.defaultBlockState();
            case GLASS_PANE -> Blocks.LIGHT_GRAY_STAINED_GLASS_PANE.defaultBlockState();
            case TINTED_GLASS -> Blocks.TINTED_GLASS.defaultBlockState();
            case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState();
            case COVER_FULL -> ((lx + lz) % 2 == 0) ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.POLISHED_BASALT.defaultBlockState();
            case COVER_HALF -> Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case COVER_CRATE -> (ly == 1) ? Blocks.SMITHING_TABLE.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
            case COVER_SANDBAG -> Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case COVER_SERVER -> (ly == 2) ? Blocks.OBSERVER.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case COVER_PILLAR -> Blocks.POLISHED_BASALT.defaultBlockState();
            case COVER_PIPE -> Blocks.OXIDIZED_COPPER.defaultBlockState();
            case STAIR_E -> Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);
            case STAIR_W -> Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM);
            case STAIR_N -> Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
            case STAIR_S -> Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM);
        };
    }

    private static void say(CommandSourceStack src, String msg, ChatFormatting color) {
        src.sendSuccess(() -> Component.literal(msg).withStyle(color), false);
    }
}
