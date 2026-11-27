package com.provinggrounds.admin.build;

import com.provinggrounds.data.BRMapPresets.RegionBiome;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 檔案: WildStructures.java
 * 目的: 提供給大逃殺程序化地圖使用的第二層「小型衛星房區（8 種）」與第三層「野外戰術微據點（4 種）」建築藍圖：
 *   - 全數遵守無木頭儲物箱／木桶、無電梯、寬出入口、完整室內掩體與照明規範；
 *   - 每個小型建物約 11×11～15×13（內含 8～9 個物資點）；每個野區微據點約 11×11（內含 3 個露天掩體物資點）。
 */
public final class WildStructures {

    public enum SmallKind {
        HOUSE(13, 11, 8),
        GAS_STATION(15, 13, 9),
        BUNKER(13, 11, 8),
        WAREHOUSE(15, 13, 9),
        MINKA(13, 11, 8),
        COTTAGE(13, 11, 8),
        ADOBE(13, 11, 8),
        ARCTIC_POD(13, 11, 8);

        public final int width;
        public final int depth;
        public final int lootCount;

        SmallKind(int width, int depth, int lootCount) {
            this.width = width;
            this.depth = depth;
            this.lootCount = lootCount;
        }
    }

    public enum MicroKind {
        CHECKPOINT(11, 11, 3),
        FIELD_CAMP(11, 11, 3),
        CONTAINER_YARD(11, 11, 3),
        RUIN_WALLS(11, 11, 3);

        public final int width;
        public final int depth;
        public final int lootCount;

        MicroKind(int width, int depth, int lootCount) {
            this.width = width;
            this.depth = depth;
            this.lootCount = lootCount;
        }
    }

    private WildStructures() {}

    /** 依區域生態選出最搭配的小型房區建築種類。 */
    public static SmallKind[] smallKindsFor(RegionBiome biome) {
        return switch (biome) {
            case DESERT_SAND, RED_CANYON -> new SmallKind[] { SmallKind.ADOBE, SmallKind.BUNKER, SmallKind.WAREHOUSE };
            case ARCTIC_SNOW -> new SmallKind[] { SmallKind.ARCTIC_POD, SmallKind.BUNKER, SmallKind.WAREHOUSE };
            case SAKURA_HIGHLAND -> new SmallKind[] { SmallKind.MINKA, SmallKind.COTTAGE, SmallKind.HOUSE };
            case MEDIEVAL_VALLEY -> new SmallKind[] { SmallKind.COTTAGE, SmallKind.BUNKER, SmallKind.MINKA };
            case INDUSTRIAL_YARD, HARBOR_COAST -> new SmallKind[] { SmallKind.WAREHOUSE, SmallKind.GAS_STATION, SmallKind.BUNKER };
            default -> new SmallKind[] { SmallKind.HOUSE, SmallKind.GAS_STATION, SmallKind.WAREHOUSE };
        };
    }

    public static void buildSmall(SmallKind kind, BuildingCanvas c) {
        switch (kind) {
            case HOUSE -> buildHouse(c);
            case GAS_STATION -> buildGasStation(c);
            case BUNKER -> buildBunker(c);
            case WAREHOUSE -> buildWarehouse(c);
            case MINKA -> buildMinka(c);
            case COTTAGE -> buildCottage(c);
            case ADOBE -> buildAdobe(c);
            case ARCTIC_POD -> buildArcticPod(c);
        }
    }

    public static void buildMicroPoi(MicroKind kind, BuildingCanvas c, RegionBiome biome) {
        switch (kind) {
            case CHECKPOINT -> buildCheckpoint(c, biome);
            case FIELD_CAMP -> buildFieldCamp(c, biome);
            case CONTAINER_YARD -> buildContainerYard(c, biome);
            case RUIN_WALLS -> buildRuinWalls(c, biome);
        }
    }

    // ── 小型房區（8 種，無木箱／無電梯／寬門寬梯／室內照明）───────────────────

    private static void buildHouse(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-6, 0, 0, 6, 11, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.SMOOTH_STONE);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.WHITE_CONCRETE);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.GRAY_CONCRETE);
        c.walls(-6, 6, 0, 6, 9, 10, Blocks.WHITE_CONCRETE);
        c.box(-6, 10, 0, 6, 10, 10, Blocks.DEEPSLATE_TILES);
        c.parapet(-6, 11, 0, 6, 10, Blocks.STONE_BRICK_WALL, false);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.windowX(-5, -3, 2, 0, 2, Blocks.GLASS_PANE);
        c.windowX(3, 5, 2, 0, 2, Blocks.GLASS_PANE);
        c.windowX(-5, -3, 7, 0, 2, Blocks.GLASS_PANE);
        c.windowX(3, 5, 7, 0, 2, Blocks.GLASS_PANE);
        c.windowZ(-6, 3, 7, 7, 2, Blocks.GLASS_PANE);
        c.windowZ(6, 3, 7, 7, 2, Blocks.GLASS_PANE);

        c.sofaX(-5, -2, 1, 5, Blocks.OAK_STAIRS, Direction.SOUTH);
        c.counter(1, 5, 1, 8, 1, Blocks.DARK_OAK_PLANKS);
        c.set(-4, 6, 7, Blocks.OAK_FENCE);
        c.set(-4, 7, 7, Blocks.OAK_PRESSURE_PLATE);
        c.tallCover(1, 5, 3, 5, 6, Blocks.BOOKSHELF, Blocks.OAK_SLAB);

        c.staircaseZ(4, 5, 0, 5, 4, 1, Blocks.OAK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.OAK_PLANKS);
        c.staircaseZ(-5, -4, 5, 10, 4, 1, Blocks.OAK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.OAK_PLANKS);

        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SEA_LANTERN);
        c.ceilingLights(-4, 4, 9, 2, 8, 4, 4, Blocks.SEA_LANTERN);

        c.loot(-5, 1, 3); c.loot(-2, 1, 9); c.loot(2, 1, 7); c.loot(5, 1, 2);
        c.loot(-5, 6, 8); c.loot(2, 6, 4); c.loot(0, 6, 9);
        c.loot(0, 11, 5);
    }

    private static void buildGasStation(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-7, 0, 0, 7, 10, 12);
        c.floor(-7, 0, 0, 7, 12, Blocks.GRAY_CONCRETE);
        // 前庭加油島遮雨棚 (z=0..4)
        c.box(-7, 5, 0, 7, 5, 4, Blocks.RED_CONCRETE);
        c.box(-7, 6, 0, 7, 6, 4, Blocks.WHITE_CONCRETE);
        c.pillar(-6, 1, 4, 1, Blocks.WHITE_CONCRETE);
        c.pillar(6, 1, 4, 1, Blocks.WHITE_CONCRETE);
        c.box(-2, 1, 2, -1, 2, 2, Blocks.RED_CONCRETE);
        c.box(2, 1, 2, 3, 2, 2, Blocks.RED_CONCRETE);
        // 後側商店本體 (z=5..12)
        c.walls(-7, 1, 5, 7, 5, 12, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-7, 5, 5, 7, 5, 12, Blocks.DEEPSLATE_TILES);
        c.parapet(-7, 6, 5, 7, 12, Blocks.IRON_BARS, false);
        c.doorway(-1, 1, 5, 3);
        c.doorwaySide(7, 1, 8, 3);
        c.windowX(-6, -3, 2, 5, 2, Blocks.GLASS_PANE);
        c.windowX(3, 6, 2, 5, 2, Blocks.GLASS_PANE);

        c.counter(-6, -2, 1, 11, 1, Blocks.POLISHED_ANDESITE);
        c.tallCover(1, 9, 4, 9, 1, Blocks.IRON_BLOCK, Blocks.SMOOTH_STONE_SLAB);
        c.staircaseZ(-6, -5, 0, 5, 7, 1, Blocks.STONE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);

        c.ceilingLights(-5, 5, 4, 2, 10, 4, 4, Blocks.SEA_LANTERN);

        c.loot(-3, 1, 2); c.loot(1, 1, 2);
        c.loot(-5, 1, 10); c.loot(-2, 1, 10); c.loot(2, 1, 8); c.loot(5, 1, 11);
        c.loot(-6, 6, 11); c.loot(5, 6, 6); c.loot(0, 6, 9);
    }

    private static void buildBunker(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-6, 0, 0, 6, 9, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.DEEPSLATE_TILES);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.DEEPSLATE_BRICKS);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.GRAY_CONCRETE);
        c.parapet(-6, 6, 0, 6, 10, Blocks.STONE_BRICK_WALL, true);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.windowX(-5, -3, 2, 0, 1, Blocks.IRON_BARS);
        c.windowX(3, 5, 2, 0, 1, Blocks.IRON_BARS);
        c.windowZ(-6, 3, 7, 2, 1, Blocks.IRON_BARS);
        c.windowZ(6, 3, 7, 2, 1, Blocks.IRON_BARS);

        c.bunkerCoverL(-4, 1, 4, 2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(4, 1, 4, -2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(-3, 4, 3, 4, 6, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILE_SLAB);

        c.staircaseZ(4, 5, 0, 5, 5, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.DEEPSLATE_BRICKS);
        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SEA_LANTERN);

        c.loot(-5, 1, 2); c.loot(5, 1, 2); c.loot(-3, 1, 6); c.loot(3, 1, 6);
        c.loot(0, 1, 9);
        c.loot(-4, 6, 2); c.loot(4, 6, 8); c.loot(0, 6, 5);
    }

    private static void buildWarehouse(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-7, 0, 0, 7, 10, 12);
        c.floor(-7, 0, 0, 7, 12, Blocks.POLISHED_ANDESITE);
        c.walls(-7, 1, 0, 7, 7, 12, Blocks.CYAN_TERRACOTTA);
        c.box(-7, 8, 0, 7, 8, 12, Blocks.DEEPSLATE_TILES);
        c.parapet(-7, 9, 0, 7, 12, Blocks.IRON_BARS, false);
        // 二樓環形夾層貓道 (y=4)
        c.box(-6, 4, 2, -2, 4, 11, Blocks.IRON_BLOCK);
        c.box(2, 4, 2, 6, 4, 11, Blocks.IRON_BLOCK);
        c.box(-2, 4, 9, 2, 4, 11, Blocks.IRON_BLOCK);

        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 12, 5);
        c.tallCover(-5, 4, -3, 8, 1, Blocks.ORANGE_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(3, 4, 5, 8, 1, Blocks.BLUE_CONCRETE, Blocks.SMOOTH_STONE_SLAB);

        c.staircaseZ(-6, -5, 0, 4, 7, 1, Blocks.STONE_BRICK_STAIRS, Blocks.IRON_BLOCK, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(5, 6, 4, 8, 3, -1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.ceilingLights(-5, 5, 7, 2, 10, 5, 4, Blocks.SEA_LANTERN);

        c.loot(-6, 1, 3); c.loot(6, 1, 3); c.loot(-4, 1, 9); c.loot(4, 1, 9);
        c.loot(-5, 5, 9); c.loot(5, 5, 9); c.loot(0, 5, 10);
        c.loot(-5, 9, 5); c.loot(5, 9, 9);
    }

    private static void buildMinka(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-6, 0, 0, 6, 11, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.DARK_OAK_PLANKS);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.WHITE_TERRACOTTA);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.OAK_PLANKS);
        c.walls(-6, 6, 0, 6, 9, 10, Blocks.WHITE_TERRACOTTA);
        c.pagodaRoof(-6, 10, 0, 6, 10, 4, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.windowX(-5, -3, 2, 0, 2, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(3, 5, 2, 0, 2, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(-5, -3, 7, 0, 2, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(3, 5, 7, 0, 2, Blocks.WHITE_STAINED_GLASS_PANE);

        c.set(0, 1, 5, Blocks.DARK_OAK_FENCE);
        c.set(0, 2, 5, Blocks.OAK_PRESSURE_PLATE);
        c.tallCover(-4, 6, -2, 6, 1, Blocks.DARK_OAK_PLANKS, Blocks.OAK_SLAB);
        c.tallCover(-3, 5, 3, 5, 6, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.SPRUCE_SLAB);
        c.staircaseZ(4, 5, 0, 5, 4, 1, Blocks.SPRUCE_STAIRS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);

        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SHROOMLIGHT);
        c.ceilingLights(-4, 4, 9, 2, 8, 4, 4, Blocks.SHROOMLIGHT);

        c.loot(-5, 1, 2); c.loot(-3, 1, 7); c.loot(2, 1, 9); c.loot(5, 1, 2);
        c.loot(-5, 6, 8); c.loot(0, 6, 6); c.loot(5, 6, 8); c.loot(-2, 6, 2);
    }

    private static void buildCottage(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-6, 0, 0, 6, 12, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.COBBLESTONE);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.STONE_BRICKS);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.OAK_PLANKS);
        c.walls(-6, 6, 0, 6, 8, 10, Blocks.WHITE_TERRACOTTA);
        c.gableRoofZ(-6, 9, 0, 6, 10, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.doorwaySide(-6, 1, 5, 2);
        c.doorwaySide(6, 1, 5, 2);
        c.windowX(-5, -3, 2, 0, 1, Blocks.GLASS_PANE);
        c.windowX(3, 5, 2, 0, 1, Blocks.GLASS_PANE);
        c.windowX(-5, -3, 7, 0, 1, Blocks.GLASS_PANE);
        c.windowX(3, 5, 7, 0, 1, Blocks.GLASS_PANE);

        c.tallCover(-4, 4, -2, 4, 1, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB);
        c.tallCover(2, 6, 4, 6, 6, Blocks.BOOKSHELF, Blocks.OAK_SLAB);
        c.staircaseZ(4, 5, 0, 5, 4, 1, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.COBBLESTONE);

        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SHROOMLIGHT);
        c.ceilingLights(-4, 4, 8, 2, 8, 4, 4, Blocks.SHROOMLIGHT);

        c.loot(-5, 1, 2); c.loot(5, 1, 2); c.loot(-3, 1, 6); c.loot(2, 1, 9);
        c.loot(-5, 6, 8); c.loot(0, 6, 8); c.loot(3, 6, 7); c.loot(-2, 6, 2);
    }

    private static void buildAdobe(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-6, 0, 0, 6, 10, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.SMOOTH_SANDSTONE);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.SANDSTONE);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.TERRACOTTA);
        c.walls(-6, 6, 0, 6, 8, 10, Blocks.SMOOTH_SANDSTONE);
        c.box(-6, 9, 0, 6, 9, 10, Blocks.RED_SANDSTONE);
        c.parapet(-6, 10, 0, 6, 10, Blocks.SANDSTONE_WALL, true);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.windowX(-5, -3, 2, 0, 1, Blocks.ACACIA_FENCE);
        c.windowX(3, 5, 2, 0, 1, Blocks.ACACIA_FENCE);
        c.windowZ(-6, 3, 7, 2, 1, Blocks.ACACIA_FENCE);
        c.windowZ(6, 3, 7, 2, 1, Blocks.ACACIA_FENCE);

        c.bunkerCoverL(-4, 1, 4, 2, 2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(1, 6, 3, 6, 6, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.staircaseZ(4, 5, 0, 5, 4, 1, Blocks.SANDSTONE_STAIRS, Blocks.TERRACOTTA, Blocks.SANDSTONE);
        c.staircaseZ(-5, -4, 5, 9, 4, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);

        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SHROOMLIGHT);
        c.ceilingLights(-4, 4, 8, 2, 8, 4, 4, Blocks.SHROOMLIGHT);

        c.loot(-5, 1, 2); c.loot(-2, 1, 5); c.loot(5, 1, 9);
        c.loot(-5, 6, 2); c.loot(2, 6, 7); c.loot(5, 6, 9);
        c.loot(-4, 10, 8); c.loot(4, 10, 3);
    }

    private static void buildArcticPod(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-6, 0, 0, 6, 10, 10);
        c.floor(-6, 0, 0, 6, 10, Blocks.POLISHED_DIORITE);
        c.walls(-6, 1, 0, 6, 4, 10, Blocks.QUARTZ_BLOCK);
        c.box(-6, 5, 0, 6, 5, 10, Blocks.CYAN_CONCRETE);
        c.walls(-6, 6, 0, 6, 8, 10, Blocks.SNOW_BLOCK);
        c.box(-6, 9, 0, 6, 9, 10, Blocks.IRON_BLOCK);
        c.parapet(-6, 10, 0, 6, 10, Blocks.IRON_BARS, false);

        c.doorway(-1, 1, 0, 3);
        c.doorway(-1, 1, 10, 3);
        c.windowX(-5, -3, 2, 0, 2, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(3, 5, 2, 0, 2, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-5, -3, 7, 0, 1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.windowX(3, 5, 7, 0, 1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);

        c.tallCover(-4, 4, -2, 4, 1, Blocks.DEEPSLATE_TILES, Blocks.SMOOTH_QUARTZ_SLAB);
        c.deskUnit(0, 6, 7, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.staircaseZ(4, 5, 0, 5, 4, 1, Blocks.QUARTZ_STAIRS, Blocks.CYAN_CONCRETE, Blocks.SMOOTH_QUARTZ);
        c.staircaseZ(-5, -4, 5, 9, 4, 1, Blocks.QUARTZ_STAIRS, Blocks.IRON_BLOCK, Blocks.SMOOTH_QUARTZ);

        c.ceilingLights(-4, 4, 4, 2, 8, 4, 4, Blocks.SEA_LANTERN);
        c.ceilingLights(-4, 4, 8, 2, 8, 4, 4, Blocks.SEA_LANTERN);

        c.loot(-5, 1, 8); c.loot(-3, 1, 5); c.loot(5, 1, 2);
        c.loot(-2, 6, 2); c.loot(1, 6, 8); c.loot(5, 6, 4);
        c.loot(-4, 10, 4); c.loot(4, 10, 8);
    }

    // ── 第三層：野外戰術微據點（4 種，露天掩體＋3 個掩體旁物資點）────────────────

    private static void buildCheckpoint(BuildingCanvas c, RegionBiome biome) {
        Block wall = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SANDSTONE : Blocks.DEEPSLATE_BRICKS;
        Block slab = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SMOOTH_SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB;
        Block ground = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SMOOTH_SANDSTONE : Blocks.GRAVEL;

        c.floor(-5, 0, -5, 5, 5, ground);
        c.clear(-5, 1, -5, 5, 6, 5);
        c.bunkerCoverL(-4, 1, -4, 3, 2, wall, slab);
        c.bunkerCoverL(4, 1, 4, -3, -2, wall, slab);
        c.parkedCar(-2, 1, 1, Blocks.GREEN_CONCRETE, true);
        c.pillar(4, 1, 4, -4, Blocks.IRON_BARS);
        c.set(4, 5, -4, Blocks.SEA_LANTERN);

        c.loot(-3, 1, -3);
        c.loot(3, 1, 3);
        c.loot(0, 1, 1);
    }

    private static void buildFieldCamp(BuildingCanvas c, RegionBiome biome) {
        Block ground = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SANDSTONE : (biome == RegionBiome.ARCTIC_SNOW ? Blocks.SNOW_BLOCK : Blocks.COARSE_DIRT);
        Block tentA = (biome == RegionBiome.ARCTIC_SNOW) ? Blocks.WHITE_WOOL : Blocks.GREEN_WOOL;
        Block tentB = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON) ? Blocks.ORANGE_WOOL : Blocks.BROWN_WOOL;

        c.floor(-5, 0, -5, 5, 5, ground);
        c.clear(-5, 1, -5, 5, 6, 5);
        // 兩座開放帳篷
        for (int z = -4; z <= -1; z++) {
            c.box(-4, 1, z, -4, 2, z, tentA);
            c.box(-1, 1, z, -1, 2, z, tentA);
            c.box(-4, 3, z, -1, 3, z, tentA);
        }
        for (int z = 1; z <= 4; z++) {
            c.box(1, 1, z, 1, 2, z, tentB);
            c.box(4, 1, z, 4, 2, z, tentB);
            c.box(1, 3, z, 4, 3, z, tentB);
        }
        c.tallCover(1, -3, 3, -3, 1, Blocks.OAK_LOG, Blocks.OAK_SLAB);
        c.set(0, 1, 0, Blocks.CAMPFIRE);
        c.pillar(-4, 1, 4, 4, Blocks.OAK_FENCE);
        c.set(-4, 5, 4, Blocks.SHROOMLIGHT);

        c.loot(-3, 1, -2);
        c.loot(2, 1, 3);
        c.loot(2, 1, -2);
    }

    private static void buildContainerYard(BuildingCanvas c, RegionBiome biome) {
        c.floor(-5, 0, -5, 5, 5, Blocks.POLISHED_ANDESITE);
        c.clear(-5, 1, -5, 5, 6, 5);
        // 兩個可穿行貨櫃（無木箱）
        c.box(-4, 1, -3, -2, 3, 2, Blocks.BLUE_CONCRETE);
        c.clear(-3, 1, -3, -3, 2, 2);
        c.box(2, 1, -2, 4, 3, 3, Blocks.ORANGE_CONCRETE);
        c.clear(3, 1, -2, 3, 2, 3);
        c.ladder(1, 1, 3, 0, Direction.WEST);
        c.bunkerCoverL(0, 1, 4, 2, -1, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);

        c.loot(-3, 1, 0);
        c.loot(3, 1, 1);
        c.loot(3, 4, 0);
    }

    private static void buildRuinWalls(BuildingCanvas c, RegionBiome biome) {
        Block stone = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SANDSTONE : Blocks.MOSSY_STONE_BRICKS;
        Block slab = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB;
        Block ground = (biome == RegionBiome.DESERT_SAND || biome == RegionBiome.RED_CANYON)
            ? Blocks.SMOOTH_SANDSTONE : Blocks.MOSS_BLOCK;

        c.floor(-5, 0, -5, 5, 5, ground);
        c.clear(-5, 1, -5, 5, 6, 5);
        c.bunkerCoverL(-4, 1, -4, 4, 3, stone, slab);
        c.bunkerCoverL(4, 1, 4, -4, -3, stone, slab);
        c.pillar(-4, 1, 4, -4, stone);
        c.pillar(4, 1, 4, 4, stone);
        c.tallCover(-1, 0, 1, 0, 1, stone, slab);
        c.set(-4, 5, -4, Blocks.SHROOMLIGHT);

        c.loot(-3, 1, -3);
        c.loot(3, 1, 3);
        c.loot(0, 1, 1);
    }
}
