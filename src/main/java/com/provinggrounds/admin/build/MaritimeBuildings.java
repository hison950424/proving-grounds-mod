package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.MARITIME;

/**
 * 檔案: MaritimeBuildings.java
 * 目的: 海港軍艦與極地生態風格 3 棟超大型建築（全數無木箱、樓梯最後一階完整齊平、玻璃／鐵欄／石牆自動連接）。
 */
final class MaritimeBuildings {

    private MaritimeBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("mar_destroyer_ship", MARITIME, "近海護衛驅逐軍艦",
            "29×51 超大型現代護衛軍艦：艦首主砲塔與飛彈蓋板掩體、3 層裝甲艦橋上層結構（戰情室／餐廳／360° 駕駛台）、開放舷道與艦尾直升機起降甲板（50 個物資點）",
            29, 51, 22, 50, MaritimeBuildings::destroyerShip));

        out.accept(new BuildingBlueprint("mar_biodome_jungle", MARITIME, "穹頂生態溫室巨蛋",
            "43×43 透明玻璃半球巨蛋：深板岩拱肋玻璃圓頂、四向氣密閘門，內部參天巨樹與岩山掩體、池塘與 2 層環形高空鋼構天橋（48 個物資點）",
            43, 43, 23, 48, MaritimeBuildings::biodomeJungle));

        out.accept(new BuildingBlueprint("mar_arctic_base", MARITIME, "極地雪山軍事基地",
            "45×35 極地研究站與裝甲基地：雪地哨卡庭院、東翼車庫停放 2 台實體極地裝甲雪車、西翼 2 層科研宿舍、後方 3 層圓頂氣象雷達高塔（46 個物資點）",
            45, 35, 21, 46, MaritimeBuildings::arcticBase));
    }

    private static void destroyerShip(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-14, 0, 0, 14, 22, 50);

        // 軍艦主甲板 (z=4..49, 甲板面 y=4, 下層船艙 y=0)
        c.floor(-13, 0, 10, 13, 49, Blocks.GRAY_CONCRETE);
        c.floor(-6, 0, 2, 6, 9, Blocks.DEEPSLATE_BRICKS);
        // 艦體外殼與銳利艦首 (y=0..4)
        c.walls(-12, 1, 12, 12, 4, 49, Blocks.LIGHT_GRAY_CONCRETE);
        c.walls(-7, 1, 5, 7, 4, 12, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-3, 1, 1, 3, 4, 5, Blocks.DEEPSLATE_BRICKS);
        // 主甲板 (y=4)＋全艦圍欄
        c.box(-12, 4, 12, 12, 4, 49, Blocks.DEEPSLATE_TILES);
        c.box(-7, 4, 5, 7, 4, 12, Blocks.DEEPSLATE_TILES);
        c.parapet(-12, 5, 12, 12, 49, Blocks.IRON_BARS, false);
        c.parapet(-7, 5, 5, 7, 12, Blocks.IRON_BARS, false);
        // 碼頭登艦側面舷門
        c.doorwaySide(-12, 1, 20, 3);
        c.doorwaySide(12, 1, 20, 3);

        // === 艦首武裝甲板 (z=5..15, y=5) ===
        c.box(-2, 5, 8, 2, 6, 11, Blocks.GRAY_CONCRETE); // 艦首主砲塔
        c.box(0, 6, 4, 0, 6, 7, Blocks.IRON_BLOCK);       // 主砲砲管
        c.tallCover(-4, 13, 4, 14, 5, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB); // 垂直飛彈蓋掩體

        // === 中央 3 層艦橋上層結構 (x=-8..8, z=16..37, 樓板 y=4, 10, 15, 20) ===
        c.walls(-8, 5, 16, 8, 9, 37, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-8, 10, 16, 8, 10, 37, Blocks.SMOOTH_STONE);
        c.walls(-7, 11, 18, 7, 14, 34, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-7, 15, 18, 7, 15, 34, Blocks.SMOOTH_STONE);
        c.walls(-6, 16, 20, 6, 19, 29, Blocks.CYAN_STAINED_GLASS_PANE); // 360° 玻璃艦橋駕駛室
        c.box(-6, 20, 20, 6, 20, 29, Blocks.DEEPSLATE_BRICKS);
        c.pillar(0, 21, 22, 24, Blocks.IRON_BARS);
        c.set(0, 22, 24, Blocks.SEA_LANTERN);

        c.doorway(-1, 5, 16, 3);
        c.doorway(-1, 5, 37, 3);
        c.doorwaySide(-8, 5, 26, 3);
        c.doorwaySide(8, 5, 26, 3);
        c.doorway(-1, 11, 18, 3);
        c.doorway(-1, 16, 20, 3);

        // 艦橋內部與下層船艙豐富掩體
        c.counter(-5, 21, 5, 21, 5, Blocks.DEEPSLATE_TILES); // 戰情室長桌
        c.serverRack(-6, 5, 25, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.serverRack(6, 5, 25, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.bedUnit(-10, 1, -7, 1, 1, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL); // 船艙 1 樓鋪位
        c.bedUnit(7, 1, 10, 1, 1, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL);
        c.deskUnit(-3, 11, 22, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(3, 11, 22, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.counter(-4, 16, 4, 16, 16, Blocks.BLACK_CONCRETE);

        // 艦尾直升機起降平台 (z=39..48, y=5 畫白 H 停機坪)
        c.box(-4, 4, 41, 4, 4, 47, Blocks.GRAY_CONCRETE);
        c.box(-2, 4, 42, -2, 4, 46, Blocks.WHITE_CONCRETE);
        c.box(2, 4, 42, 2, 4, 46, Blocks.WHITE_CONCRETE);
        c.box(-1, 4, 44, 1, 4, 44, Blocks.WHITE_CONCRETE);

        // 艦內多段寬 2 格樓梯 (無電梯)：0->4->10->15->20
        c.staircaseZ(-10, -9, 0, 4, 18, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(9, 10, 0, 4, 18, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-6, -5, 4, 10, 29, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(5, 6, 4, 10, 29, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-5, -4, 10, 15, 29, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(4, 5, 15, 20, 21, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);

        c.ceilingLights(-9, 9, 3, 14, 46, 7, 8, Blocks.SEA_LANTERN);
        c.ceilingLights(-6, 6, 9, 18, 35, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-5, 5, 14, 20, 32, 5, 5, Blocks.SEA_LANTERN);

        // 50 個物資箱點位
        c.loot(-5, 1, 16); c.loot(5, 1, 16);  c.loot(-5, 1, 26); c.loot(5, 1, 26);
        c.loot(-5, 1, 36); c.loot(5, 1, 36);  c.loot(0, 1, 44);  c.loot(-8, 1, 44); c.loot(8, 1, 44);
        c.loot(0, 5, 6);   c.loot(-5, 5, 10); c.loot(5, 5, 10);  c.loot(-10, 5, 18); c.loot(10, 5, 18);
        c.loot(-10, 5, 28); c.loot(10, 5, 28); c.loot(-10, 5, 38); c.loot(10, 5, 38);
        c.loot(-3, 5, 19); c.loot(3, 5, 19);  c.loot(-3, 5, 34); c.loot(3, 5, 34);
        c.loot(0, 5, 24);  c.loot(0, 5, 30);
        c.loot(-7, 5, 42); c.loot(7, 5, 42);  c.loot(-7, 5, 47); c.loot(7, 5, 47);  c.loot(0, 5, 39);
        c.loot(-5, 11, 20); c.loot(5, 11, 20); c.loot(-5, 11, 32); c.loot(5, 11, 32);
        c.loot(0, 11, 25);  c.loot(-6, 11, 17); c.loot(6, 11, 17);
        c.loot(-4, 16, 22); c.loot(4, 16, 22); c.loot(-4, 16, 28); c.loot(4, 16, 28); c.loot(0, 16, 25);
        c.loot(-4, 21, 22); c.loot(4, 21, 22); c.loot(-4, 21, 27); c.loot(4, 21, 27);
        c.loot(0, 21, 27);  c.loot(-3, 1, 8);  c.loot(3, 1, 8);   c.loot(-8, 1, 24); c.loot(8, 1, 24);
    }

    private static void biodomeJungle(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-21, 0, 0, 21, 23, 42);

        // 43x43 穹頂生態溫室巨蛋 (中心 cz=21, 外牆 y=1..5, 透明玻璃半球屋頂 y=6..22)
        c.floor(-21, 0, 0, 21, 42, Blocks.GRASS_BLOCK);
        c.walls(-21, 1, 0, 21, 5, 42, Blocks.STONE_BRICKS);
        c.clear(-20, 1, 1, 20, 5, 41);
        for (int s = 1; s <= 17; s++) {
            int r = 21 - s;
            int y = 5 + s;
            c.walls(-r, y, 21 - r, r, y, 21 + r, (s % 5 == 0) ? Blocks.DEEPSLATE_BRICKS : Blocks.GLASS);
        }
        c.box(-4, 22, 17, 4, 22, 25, Blocks.SEA_LANTERN);
        // 四向氣密閘門
        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 42, 5);
        c.doorwaySide(-21, 1, 19, 5);
        c.doorwaySide(21, 1, 19, 5);

        // 內部自然叢林地貌：中央池塘水潭＋4 棵高大叢林巨樹＋青苔巨岩掩體＋雙研究棚
        c.box(-4, 0, 17, 4, 0, 25, Blocks.WATER);
        c.box(-3, 0, 18, 3, 0, 24, Blocks.WATER);
        for (int[] tree : new int[][] { { -10, 11 }, { 10, 11 }, { -10, 31 }, { 10, 31 } }) {
            c.box(tree[0], 1, tree[1], tree[0] + 1, 9, tree[1] + 1, Blocks.JUNGLE_LOG);
            c.box(tree[0] - 2, 8, tree[1] - 2, tree[0] + 3, 10, tree[1] + 3, Blocks.JUNGLE_LEAVES);
        }
        c.box(-14, 1, 20, -12, 3, 22, Blocks.MOSSY_COBBLESTONE);
        c.box(12, 1, 20, 14, 3, 22, Blocks.MOSSY_COBBLESTONE);
        c.bunkerCoverL(0, 1, 8, 3, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(0, 1, 34, -3, -2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 2 樓環形高空鋼橋天橋 (y=8, 外圈 x=-17..17, z=4..38, 內切洞 x=-13..13, z=8..34)
        c.box(-17, 8, 4, -14, 8, 38, Blocks.DEEPSLATE_TILES);
        c.box(14, 8, 4, 17, 8, 38, Blocks.DEEPSLATE_TILES);
        c.box(-14, 8, 4, 14, 8, 7, Blocks.DEEPSLATE_TILES);
        c.box(-14, 8, 35, 14, 8, 38, Blocks.DEEPSLATE_TILES);
        c.windowZ(-14, 9, 7, 35, 9, Blocks.IRON_BARS);
        c.windowZ(14, 9, 7, 35, 9, Blocks.IRON_BARS);
        c.windowX(-14, 9, 7, 14, 9, Blocks.IRON_BARS);
        c.windowX(-14, 9, 35, 14, 9, Blocks.IRON_BARS);
        // 雙研究帳篷站點
        c.counter(-16, 19, -15, 22, 9, Blocks.QUARTZ_BLOCK);
        c.counter(15, 19, 16, 22, 9, Blocks.QUARTZ_BLOCK);

        // 寬 2 格鋼橋樓梯 0->8 (無電梯)
        c.staircaseZ(-17, -16, 0, 8, 12, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(16, 17, 0, 8, 12, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);

        // 48 個物資箱點位
        c.loot(-16, 1, 6);  c.loot(16, 1, 6);   c.loot(-16, 1, 36); c.loot(16, 1, 36);
        c.loot(-7, 1, 7);   c.loot(7, 1, 7);    c.loot(-7, 1, 35);  c.loot(7, 1, 35);
        c.loot(-15, 1, 17); c.loot(15, 1, 17);  c.loot(-15, 1, 25); c.loot(15, 1, 25);
        c.loot(-7, 1, 15);  c.loot(7, 1, 15);   c.loot(-7, 1, 27);  c.loot(7, 1, 27);
        c.loot(0, 1, 14);   c.loot(0, 1, 28);   c.loot(-9, 1, 21);  c.loot(9, 1, 21);
        c.loot(0, 1, 5);    c.loot(0, 1, 37);   c.loot(-18, 1, 21); c.loot(18, 1, 21);
        c.loot(-16, 9, 6);  c.loot(16, 9, 6);   c.loot(-16, 9, 36); c.loot(16, 9, 36);
        c.loot(-8, 9, 5);   c.loot(8, 9, 5);    c.loot(-8, 9, 36);  c.loot(8, 9, 36);
        c.loot(0, 9, 5);    c.loot(0, 9, 36);   c.loot(-16, 9, 25); c.loot(16, 9, 25);
        c.loot(-15, 9, 10); c.loot(15, 9, 10);  c.loot(-15, 9, 31); c.loot(15, 9, 31);
        c.loot(-4, 9, 5);   c.loot(4, 9, 5);    c.loot(-4, 9, 36);  c.loot(4, 9, 36);
        c.loot(-12, 1, 6);  c.loot(12, 1, 6);   c.loot(-12, 1, 36); c.loot(12, 1, 36);
    }

    private static void arcticBase(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-22, 0, 0, 22, 21, 34);

        c.floor(-22, 0, 0, 22, 34, Blocks.SNOW_BLOCK);
        c.parapet(-22, 1, 0, 22, 34, Blocks.STONE_BRICK_WALL, true);
        c.doorway(-3, 1, 0, 7);
        c.doorway(-3, 1, 34, 7);

        // 西翼 2 層科研宿舍大樓 (x=-21..-4, z=5..21, y=0, 6, 12)
        c.box(-21, 0, 5, -4, 0, 21, Blocks.POLISHED_DIORITE);
        c.walls(-21, 1, 5, -4, 5, 21, Blocks.WHITE_CONCRETE);
        c.box(-21, 6, 5, -4, 6, 21, Blocks.SMOOTH_QUARTZ);
        c.walls(-21, 7, 5, -4, 11, 21, Blocks.WHITE_CONCRETE);
        c.box(-21, 12, 5, -4, 12, 21, Blocks.DEEPSLATE_TILES);
        c.parapet(-21, 13, 5, -4, 21, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-13, 1, 5, 3);
        c.doorwaySide(-4, 1, 12, 3);
        c.windowX(-19, 2, 5, -15, 3, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.windowX(-19, 8, 5, -15, 9, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.bedUnit(-20, 7, -18, 8, 1, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(-20, 11, -18, 12, 1, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL);
        c.serverRack(-10, 1, 18, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.deskUnit(-15, 7, 10, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.tallCover(-12, 15, -7, 15, 7, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.staircaseZ(-6, -5, 0, 6, 14, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-19, -18, 6, 12, 19, -1, Blocks.QUARTZ_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.QUARTZ_BLOCK);

        // 東翼挑高裝甲雪車庫 (x=3..21, z=5..21, y=0..10)＋2 台極地履帶裝甲車
        c.box(3, 0, 5, 21, 0, 21, Blocks.POLISHED_ANDESITE);
        c.walls(3, 1, 5, 21, 8, 21, Blocks.LIGHT_GRAY_CONCRETE);
        c.clear(4, 1, 6, 20, 8, 20);
        c.box(3, 9, 5, 21, 9, 21, Blocks.DEEPSLATE_BRICKS);
        c.parapet(3, 10, 5, 21, 21, Blocks.IRON_BARS, false);
        c.doorway(8, 1, 5, 7);
        c.doorwaySide(3, 1, 12, 3);
        c.parkedCar(8, 1, 13, Blocks.WHITE_CONCRETE, true);
        c.parkedCar(16, 1, 13, Blocks.ORANGE_CONCRETE, true);
        c.staircaseZ(18, 19, 0, 9, 10, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);

        // 後方 3 層極地圓頂氣象雷達高塔 (x=-10..10, z=22..33, y=0, 6, 12, 18)
        c.box(-10, 0, 22, 10, 0, 33, Blocks.DEEPSLATE_TILES);
        c.walls(-10, 1, 22, 10, 17, 33, Blocks.GRAY_CONCRETE);
        c.clear(-9, 1, 23, 9, 17, 32);
        c.box(-10, 6, 22, 10, 6, 33, Blocks.SMOOTH_STONE);
        c.box(-10, 12, 22, 10, 12, 33, Blocks.SMOOTH_STONE);
        c.box(-10, 18, 22, 10, 18, 33, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-10, 19, 22, 10, 33, Blocks.STONE_BRICK_WALL, true);
        c.box(-3, 19, 25, 3, 21, 30, Blocks.LIGHT_BLUE_STAINED_GLASS);
        c.pillar(0, 19, 21, 27, Blocks.SEA_LANTERN);
        c.doorway(-2, 1, 22, 5);
        c.doorwaySide(-10, 1, 26, 3);
        c.doorwaySide(10, 1, 26, 3);
        c.tallCover(-6, 26, -2, 26, 1, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.serverRack(6, 7, 29, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.deskUnit(0, 13, 28, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.staircaseZ(-9, -8, 0, 6, 24, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(8, 9, 6, 12, 31, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-9, -8, 12, 18, 24, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);

        c.ceilingLights(-19, -6, 5, 7, 19, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-19, -6, 11, 7, 19, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-8, 8, 5, 24, 31, 6, 5, Blocks.SEA_LANTERN);
        c.ceilingLights(-8, 8, 11, 24, 31, 6, 5, Blocks.SEA_LANTERN);
        c.ceilingLights(-8, 8, 17, 24, 31, 6, 5, Blocks.SEA_LANTERN);

        // 46 個物資箱點位
        c.loot(-16, 1, 8);  c.loot(-16, 1, 16); c.loot(-8, 1, 8);   c.loot(-8, 1, 18);
        c.loot(-16, 7, 8);  c.loot(-16, 7, 16); c.loot(-8, 7, 10);  c.loot(-8, 7, 18);
        c.loot(-16, 13, 8); c.loot(-16, 13, 18); c.loot(-8, 13, 8); c.loot(-8, 13, 18);
        c.loot(6, 1, 8);    c.loot(18, 1, 8);   c.loot(12, 1, 13);  c.loot(6, 1, 18);  c.loot(18, 1, 18);
        c.loot(6, 10, 8);   c.loot(16, 10, 8);  c.loot(6, 10, 18);  c.loot(16, 10, 18);
        c.loot(-6, 1, 24);  c.loot(6, 1, 24);   c.loot(-6, 1, 30);  c.loot(6, 1, 30);
        c.loot(-6, 7, 25);  c.loot(6, 7, 25);   c.loot(-6, 7, 31);  c.loot(0, 7, 28);
        c.loot(-6, 13, 26); c.loot(6, 13, 26);  c.loot(-6, 13, 31); c.loot(6, 13, 31);
        c.loot(-7, 19, 24); c.loot(7, 19, 24);  c.loot(-7, 19, 31); c.loot(7, 19, 31);
        c.loot(-12, 1, 2);  c.loot(12, 1, 2);   c.loot(-18, 1, 28); c.loot(18, 1, 28);
        c.loot(0, 1, 15);   c.loot(0, 1, 20);   c.loot(-12, 1, 25); c.loot(12, 1, 25); c.loot(0, 1, 28);
    }
}
