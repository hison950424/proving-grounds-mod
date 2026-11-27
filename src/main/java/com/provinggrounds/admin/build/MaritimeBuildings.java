package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.MARITIME;

/**
 * 檔案: MaritimeBuildings.java
 * 目的: 海港軍艦與極地生態風格 6 棟超大型建築（全數無木箱、無電梯、樓梯最後一階完整齊平、玻璃／鐵欄／石牆自動連接、物資點緊貼掩體）。
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

        out.accept(new BuildingBlueprint("mar_lighthouse_pier", MARITIME, "海角燈塔與港務碼頭",
            "39×33 海岸棧橋要塞：港務碼頭雙層倉儲、可進入貨櫃與繫船柱掩體，以及東南側 25 格高紅白海角大燈塔（多段旋轉樓梯直達全景燈室）（42 個物資點）",
            39, 33, 26, 42, MaritimeBuildings::lighthousePier));

        out.accept(new BuildingBlueprint("mar_cargo_freighter", MARITIME, "遠洋貨櫃運輸輪",
            "31×47 超長海運貨輪：下層機艙通道、主甲板交錯疊放 6 座雙層可進入貨櫃與跨櫃天橋，船尾 3 層船員艦橋室與全景操舵台（46 個物資點）",
            31, 47, 20, 46, MaritimeBuildings::cargoFreighter));

        out.accept(new BuildingBlueprint("mar_ice_observatory", MARITIME, "極地冰川科考天文台",
            "39×31 冰藍科研站：西翼 2 層生態實驗溫室、東翼通訊機櫃房，中央 3 層冰藍穹頂天文望遠鏡主廳與高處觀測露台（42 個物資點）",
            39, 31, 21, 42, MaritimeBuildings::iceObservatory));
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

        // 50 個物資箱點位（貼近艦艙壁／主砲／機櫃／控台／甲板欄杆）
        c.loot(-5, 1, 16); c.loot(5, 1, 16);  c.loot(-5, 1, 26); c.loot(5, 1, 26);
        c.loot(-5, 1, 36); c.loot(5, 1, 36);  c.loot(-3, 1, 47); c.loot(-8, 1, 47); c.loot(8, 1, 47);
        c.loot(0, 5, 12);  c.loot(-5, 5, 10); c.loot(5, 5, 10);  c.loot(-11, 5, 18); c.loot(11, 5, 18);
        c.loot(-11, 5, 28); c.loot(11, 5, 28); c.loot(-11, 5, 38); c.loot(11, 5, 38);
        c.loot(-3, 5, 19); c.loot(3, 5, 19);  c.loot(-3, 5, 34); c.loot(3, 5, 34);
        c.loot(-4, 5, 24); c.loot(4, 5, 30);
        c.loot(-10, 5, 42); c.loot(10, 5, 42); c.loot(-10, 5, 47); c.loot(10, 5, 47); c.loot(0, 5, 38);
        c.loot(-5, 11, 20); c.loot(5, 11, 20); c.loot(-5, 11, 32); c.loot(5, 11, 32);
        c.loot(-3, 11, 25); c.loot(-6, 11, 17); c.loot(6, 11, 17);
        c.loot(-4, 16, 22); c.loot(4, 16, 22); c.loot(-4, 16, 28); c.loot(4, 16, 28); c.loot(0, 16, 21);
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

        // 48 個物資箱點位（貼近巨樹幹／岩山／矮牆／護欄）
        c.loot(-16, 1, 6);  c.loot(16, 1, 6);   c.loot(-16, 1, 36); c.loot(16, 1, 36);
        c.loot(-7, 1, 7);   c.loot(7, 1, 7);    c.loot(-7, 1, 35);  c.loot(7, 1, 35);
        c.loot(-15, 1, 17); c.loot(15, 1, 17);  c.loot(-15, 1, 25); c.loot(15, 1, 25);
        c.loot(-9, 1, 14);  c.loot(9, 1, 14);   c.loot(-9, 1, 28);  c.loot(9, 1, 28);
        c.loot(-3, 1, 10);  c.loot(3, 1, 32);   c.loot(-11, 1, 21); c.loot(11, 1, 21);
        c.loot(0, 1, 7);    c.loot(0, 1, 35);   c.loot(-19, 1, 22); c.loot(19, 1, 22);
        c.loot(-16, 9, 6);  c.loot(16, 9, 6);   c.loot(-16, 9, 36); c.loot(16, 9, 36);
        c.loot(-8, 9, 6);   c.loot(8, 9, 6);    c.loot(-8, 9, 36);  c.loot(8, 9, 36);
        c.loot(0, 9, 6);    c.loot(0, 9, 36);   c.loot(-15, 9, 25); c.loot(15, 9, 25);
        c.loot(-15, 9, 10); c.loot(15, 9, 10);  c.loot(-15, 9, 31); c.loot(15, 9, 31);
        c.loot(-4, 9, 6);   c.loot(4, 9, 6);    c.loot(-4, 9, 36);  c.loot(4, 9, 36);
        c.loot(-12, 1, 5);  c.loot(12, 1, 5);   c.loot(-12, 1, 37); c.loot(12, 1, 37);
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

        // 46 個物資箱點位（貼近雪車／牆面／機櫃／雷達塔／護欄）
        c.loot(-16, 1, 8);  c.loot(-16, 1, 16); c.loot(-8, 1, 6);   c.loot(-8, 1, 19);
        c.loot(-16, 7, 8);  c.loot(-16, 7, 16); c.loot(-8, 7, 10);  c.loot(-8, 7, 18);
        c.loot(-16, 13, 8); c.loot(-16, 13, 18); c.loot(-8, 13, 8); c.loot(-8, 13, 18);
        c.loot(6, 1, 8);    c.loot(18, 1, 8);   c.loot(11, 1, 13);  c.loot(6, 1, 18);  c.loot(18, 1, 18);
        c.loot(6, 10, 8);   c.loot(16, 10, 8);  c.loot(6, 10, 18);  c.loot(16, 10, 18);
        c.loot(-6, 1, 24);  c.loot(6, 1, 24);   c.loot(-6, 1, 30);  c.loot(6, 1, 30);
        c.loot(-6, 7, 25);  c.loot(5, 7, 25);   c.loot(-6, 7, 31);  c.loot(4, 7, 29);
        c.loot(-6, 13, 26); c.loot(6, 13, 26);  c.loot(-6, 13, 31); c.loot(6, 13, 31);
        c.loot(-7, 19, 24); c.loot(7, 19, 24);  c.loot(-7, 19, 31); c.loot(7, 19, 31);
        c.loot(-12, 1, 4);  c.loot(12, 1, 4);   c.loot(-19, 1, 28); c.loot(19, 1, 28);
        c.loot(-3, 1, 15);  c.loot(3, 1, 15);   c.loot(-11, 1, 25); c.loot(11, 1, 25); c.loot(0, 1, 32);
    }

    private static void lighthousePier(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-19, 0, 0, 19, 26, 32);

        // 港務棧橋碼頭區 (-18..6, z=0..32)＋港務倉庫 (-17..-3, z=6..20)＋東南 25 格紅白大燈塔 (8..18, z=18..28)
        c.floor(-19, 0, 0, 19, 32, Blocks.STONE_BRICKS);
        c.box(-18, 0, 0, 6, 0, 32, Blocks.SPRUCE_PLANKS);
        c.parapet(-18, 1, 0, 6, 32, Blocks.DARK_OAK_FENCE, false);
        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 32, 5);

        // 港務倉庫 (x=-17..-3, z=6..21, y=0, 6, 12)
        c.box(-17, 0, 6, -3, 0, 21, Blocks.DEEPSLATE_TILES);
        c.walls(-17, 1, 6, -3, 11, 21, Blocks.GRAY_CONCRETE);
        c.clear(-16, 1, 7, -4, 11, 20);
        c.box(-17, 6, 6, -3, 6, 21, Blocks.SPRUCE_PLANKS);
        c.box(-17, 12, 6, -3, 12, 21, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-17, 13, 6, -3, 21, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-11, 1, 6, 3);
        c.doorway(-11, 1, 21, 3);
        c.doorwaySide(-3, 1, 12, 3);
        c.tallCover(-14, 10, -9, 10, 1, Blocks.OXIDIZED_COPPER, Blocks.STONE_BRICK_SLAB);
        c.counter(-10, 16, -5, 16, 7, Blocks.DARK_OAK_PLANKS);
        c.bedUnit(-16, 8, -14, 9, 7, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL);
        c.staircaseZ(-16, -15, 0, 6, 12, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
        c.staircaseZ(-5, -4, 6, 12, 19, -1, Blocks.DARK_OAK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.ceilingLights(-15, -5, 5, 8, 19, 5, 5, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, -5, 11, 8, 19, 5, 5, Blocks.SEA_LANTERN);

        // 棧橋貨櫃與繫船柱掩體 (z=24..31 & x=0..5)
        c.box(0, 1, 24, 5, 4, 29, Blocks.BLUE_CONCRETE);
        c.clear(1, 1, 25, 4, 3, 28);
        c.doorway(2, 1, 24, 2);
        c.doorway(2, 1, 29, 2);
        for (int pz : new int[] { 5, 15, 25 }) c.pillar(6, 1, 2, pz, Blocks.POLISHED_DEEPSLATE);

        // 東南 25 格高紅白大燈塔 (x=8..18, z=10..20, y=0..26)
        c.box(8, 0, 10, 18, 0, 20, Blocks.DEEPSLATE_BRICKS);
        for (int fy = 1; fy <= 18; fy++) {
            c.walls(8, fy, 10, 18, fy, 20, (fy / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE);
        }
        c.clear(9, 1, 11, 17, 18, 19);
        c.box(8, 6, 10, 18, 6, 20, Blocks.SMOOTH_STONE);
        c.box(8, 12, 10, 18, 12, 20, Blocks.SMOOTH_STONE);
        c.box(8, 18, 10, 18, 18, 20, Blocks.DEEPSLATE_BRICKS);
        c.walls(8, 19, 10, 18, 23, 20, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(12, 19, 14, 14, 22, 16, Blocks.SEA_LANTERN);
        c.box(8, 24, 10, 18, 24, 20, Blocks.RED_CONCRETE);
        c.parapet(8, 25, 10, 18, 20, Blocks.IRON_BARS, false);
        c.doorwaySide(8, 1, 14, 3);
        c.staircaseZ(10, 11, 0, 6, 11, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(15, 16, 6, 12, 18, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(10, 11, 12, 18, 11, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.staircaseZ(15, 16, 18, 24, 18, -1, Blocks.STONE_BRICK_STAIRS, Blocks.RED_CONCRETE, Blocks.STONE_BRICKS);

        // 42 個物資箱點位（貼近貨櫃／倉牆／繫船柱／燈塔內壁／欄杆）
        c.loot(-15, 1, 8);  c.loot(-5, 1, 8);   c.loot(-13, 1, 11); c.loot(-9, 1, 11);
        c.loot(-15, 1, 19); c.loot(-5, 1, 19);  c.loot(-15, 7, 10); c.loot(-5, 7, 10);
        c.loot(-8, 7, 15);  c.loot(-15, 7, 19); c.loot(-5, 7, 19);
        c.loot(-15, 13, 8); c.loot(-5, 13, 8);  c.loot(-15, 13, 19); c.loot(-5, 13, 19);
        c.loot(-10, 13, 14); c.loot(1, 1, 26);  c.loot(4, 1, 27);   c.loot(5, 1, 5);
        c.loot(5, 1, 15);   c.loot(-3, 1, 30);  c.loot(5, 1, 30);
        c.loot(10, 1, 18);  c.loot(16, 1, 12);  c.loot(16, 1, 18);  c.loot(13, 1, 12);
        c.loot(10, 7, 12);  c.loot(16, 7, 18);  c.loot(10, 7, 18);  c.loot(10, 13, 18);
        c.loot(16, 13, 12); c.loot(16, 13, 18); c.loot(10, 19, 12); c.loot(16, 19, 12);
        c.loot(10, 19, 18); c.loot(16, 19, 18); c.loot(10, 25, 12); c.loot(16, 25, 12);
        c.loot(10, 25, 18); c.loot(16, 25, 18); c.loot(12, 1, 4);   c.loot(12, 1, 24);
    }

    private static void cargoFreighter(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-15, 0, 0, 15, 20, 46);

        c.floor(-14, 0, 2, 14, 46, Blocks.DEEPSLATE_BRICKS);
        c.walls(-13, 1, 6, 13, 4, 46, Blocks.RED_CONCRETE);
        c.walls(-8, 1, 1, 8, 4, 6, Blocks.RED_CONCRETE);
        c.clear(-12, 1, 7, 12, 3, 45);
        c.box(-13, 4, 6, 13, 4, 46, Blocks.DEEPSLATE_TILES);
        c.box(-8, 4, 1, 8, 4, 6, Blocks.DEEPSLATE_TILES);
        c.parapet(-13, 5, 6, 13, 46, Blocks.IRON_BARS, false);
        c.parapet(-8, 5, 1, 8, 6, Blocks.IRON_BARS, false);
        c.doorwaySide(-13, 1, 22, 3);
        c.doorwaySide(13, 1, 22, 3);

        // 甲板上 6 座雙層可進入貨櫃 (左右兩列，z=9..15, 18..24, 27..33，底部 y=5, 頂部 y=9 連天橋)
        int[] zStarts = { 9, 18, 27 };
        net.minecraft.world.level.block.Block[] colors = { Blocks.ORANGE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GREEN_CONCRETE };
        for (int i = 0; i < zStarts.length; i++) {
            int zs = zStarts[i];
            for (int sx : new int[] { -10, 4 }) {
                c.box(sx, 5, zs, sx + 6, 8, zs + 5, colors[i]);
                c.clear(sx + 1, 5, zs + 1, sx + 5, 7, zs + 4);
                c.doorway(sx + 2, 5, zs, 2);
                c.doorway(sx + 2, 5, zs + 5, 2);
                c.tallCover(sx + 2, zs + 2, sx + 4, zs + 2, 5, Blocks.IRON_BLOCK, Blocks.SMOOTH_STONE_SLAB);
            }
            c.box(-4, 9, zs + 2, 4, 9, zs + 3, Blocks.DEEPSLATE_BRICKS); // 跨櫃天橋
        }

        // 船尾 3 層船員艦橋樓 (x=-10..10, z=35..44, y=4, 10, 15, 19)
        c.walls(-10, 5, 35, 10, 14, 44, Blocks.WHITE_CONCRETE);
        c.clear(-9, 5, 36, 9, 14, 43);
        c.box(-10, 10, 35, 10, 10, 44, Blocks.SMOOTH_STONE);
        c.box(-10, 15, 35, 10, 15, 44, Blocks.SMOOTH_STONE);
        c.walls(-8, 16, 36, 8, 18, 42, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(-8, 19, 36, 8, 19, 42, Blocks.DEEPSLATE_BRICKS);
        c.doorway(-1, 5, 35, 3);
        c.doorwaySide(-10, 5, 39, 3);
        c.doorwaySide(10, 5, 39, 3);
        c.counter(-5, 38, 5, 38, 5, Blocks.DARK_OAK_PLANKS);
        c.deskUnit(-4, 11, 38, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(4, 11, 38, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.counter(-4, 16, 4, 16, 16, Blocks.BLACK_CONCRETE);

        // 船艙與艦橋雙寬樓梯 + 登櫃階梯 (0->4->9 / 4->10->15)
        c.staircaseZ(-11, -10, 0, 4, 24, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(10, 11, 0, 4, 24, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-2, -1, 4, 9, 10, 1, Blocks.STONE_BRICK_STAIRS, Blocks.ORANGE_CONCRETE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(1, 2, 4, 9, 28, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GREEN_CONCRETE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-9, -8, 4, 10, 36, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(8, 9, 10, 15, 42, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.ceilingLights(-10, 10, 3, 10, 43, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-8, 8, 9, 37, 42, 6, 4, Blocks.SEA_LANTERN);
        c.ceilingLights(-8, 8, 14, 37, 42, 6, 4, Blocks.SEA_LANTERN);

        // 46 個物資箱點位（貼近貨櫃內壁／貨櫃頂／船舷欄杆／艦橋控制台）
        c.loot(-8, 1, 12); c.loot(8, 1, 12);  c.loot(-8, 1, 28); c.loot(8, 1, 28);
        c.loot(-5, 1, 38); c.loot(5, 1, 38);  c.loot(0, 1, 18);  c.loot(0, 1, 43);
        for (int zs : zStarts) {
            c.loot(-9, 5, zs + 1);  c.loot(-5, 5, zs + 4);
            c.loot(5, 5, zs + 1);   c.loot(9, 5, zs + 4);
            c.loot(-7, 9, zs + 2);  c.loot(7, 9, zs + 2);
            c.loot(0, 10, zs + 2);
        }
        c.loot(-12, 5, 12); c.loot(12, 5, 12); c.loot(-12, 5, 30); c.loot(12, 5, 30);
        c.loot(-3, 5, 3);   c.loot(3, 5, 3);
        c.loot(-6, 5, 39);  c.loot(6, 5, 39);  c.loot(0, 5, 42);
        c.loot(-6, 11, 41); c.loot(6, 11, 41); c.loot(0, 11, 37);
        c.loot(-5, 16, 38); c.loot(5, 16, 38); c.loot(-5, 16, 41); c.loot(5, 16, 41);
        c.loot(0, 20, 39);
    }

    private static void iceObservatory(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-19, 0, 0, 19, 21, 30);

        c.floor(-19, 0, 0, 19, 30, Blocks.SNOW_BLOCK);
        c.box(-17, 0, 4, 17, 0, 28, Blocks.POLISHED_DIORITE);
        c.walls(-17, 1, 4, 17, 5, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 6, 4, 17, 6, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 7, 4, 17, 11, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 12, 4, 17, 12, 28, Blocks.DEEPSLATE_TILES);
        c.parapet(-17, 13, 4, 17, 28, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-17, 1, 4, 17, 12, 28, Blocks.PACKED_ICE);

        c.doorway(-2, 1, 4, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-17, 1, 15, 3);
        c.doorwaySide(17, 1, 15, 3);
        for (int fy : new int[] { 2, 8 }) {
            c.windowX(-14, fy, 4, -6, fy + 2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            c.windowX(6, fy, 4, 14, fy + 2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            c.windowZ(-17, fy, 7, 12, fy + 2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            c.windowZ(17, fy, 7, 12, fy + 2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        }

        // 中央 3 層冰藍天文望遠鏡圓廳 (y=13..21)＋屋頂觀測台
        c.box(-5, 6, 11, 5, 11, 21, Blocks.AIR);
        c.box(-6, 7, 10, 6, 7, 22, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.box(-5, 7, 11, 5, 7, 21, Blocks.AIR);
        c.box(-5, 13, 11, 5, 16, 21, Blocks.WHITE_CONCRETE);
        c.clear(-4, 13, 12, 4, 16, 20);
        c.doorway(-1, 13, 11, 3);
        c.doorway(-1, 13, 21, 3);
        c.box(-4, 17, 12, 4, 19, 20, Blocks.LIGHT_BLUE_STAINED_GLASS);
        c.pillar(0, 14, 18, 16, Blocks.IRON_BLOCK);
        c.set(0, 19, 16, Blocks.SEA_LANTERN);

        // 西翼植物研究溫室＋東翼通訊伺服器室
        c.planter(-14, 10, -10, 13, 1, Blocks.QUARTZ_BLOCK, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);
        c.tallCover(-14, 21, -9, 21, 1, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ_SLAB);
        c.serverRack(12, 1, 10, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.serverRack(12, 1, 18, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.deskUnit(-11, 7, 9, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(11, 7, 9, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.tallCover(-12, 8, -8, 8, 13, Blocks.PACKED_ICE, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(8, 8, 12, 8, 13, Blocks.PACKED_ICE, Blocks.SMOOTH_STONE_SLAB);

        c.staircaseZ(-16, -15, 0, 6, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(15, 16, 0, 6, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-16, -15, 6, 12, 23, -1, Blocks.QUARTZ_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.QUARTZ_BLOCK);

        c.ceilingLights(-14, 14, 5, 6, 26, 7, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-14, 14, 11, 6, 26, 7, 6, Blocks.SEA_LANTERN);

        // 42 個物資箱點位（貼近花圃／機櫃／欄杆／望遠鏡座／冰塊掩體）
        c.loot(-12, 1, 9);  c.loot(-9, 1, 12);  c.loot(-11, 1, 20); c.loot(11, 1, 10);
        c.loot(11, 1, 18);  c.loot(-6, 1, 14);  c.loot(6, 1, 14);   c.loot(-6, 1, 18);
        c.loot(6, 1, 18);   c.loot(-4, 1, 26);  c.loot(4, 1, 26);   c.loot(-14, 1, 5);
        c.loot(14, 1, 5);   c.loot(-14, 1, 26); c.loot(14, 1, 26);
        c.loot(-11, 7, 10); c.loot(11, 7, 10);  c.loot(-7, 7, 14);  c.loot(7, 7, 14);
        c.loot(-7, 7, 20);  c.loot(7, 7, 20);   c.loot(-3, 7, 9);   c.loot(3, 7, 9);
        c.loot(-3, 7, 23);  c.loot(3, 7, 23);   c.loot(-14, 7, 26); c.loot(14, 7, 26);
        c.loot(-10, 13, 9); c.loot(10, 13, 9);  c.loot(-15, 13, 6); c.loot(15, 13, 6);
        c.loot(-15, 13, 26); c.loot(15, 13, 26); c.loot(-7, 13, 15); c.loot(7, 13, 15);
        c.loot(-3, 13, 16); c.loot(3, 13, 16);  c.loot(0, 13, 23);  c.loot(0, 13, 8);
        c.loot(-14, 1, 1);  c.loot(14, 1, 1);   c.loot(-11, 1, 15);
    }
}
