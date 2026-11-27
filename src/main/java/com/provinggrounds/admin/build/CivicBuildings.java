package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.CIVIC;

/**
 * 檔案: CivicBuildings.java
 * 目的: 都市地標風格 6 棟超大型建築（均無木箱、無新增電梯、樓梯最後一階完整齊平、玻璃片／鐵欄／石牆自動連接、物資點緊貼掩體）。
 */
final class CivicBuildings {

    private CivicBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("civ_high_school", CIVIC, "國立高級中學與體育館",
            "49×35 超大型校園雙建築：西翼 3 層教學樓（長廊與 6 間附講台課桌椅書架教室＋天台），東翼挑高籃球體育館（雙籃框、階梯看台與 2 樓環廊）（52 個物資點）",
            49, 35, 19, 52, CivicBuildings::highSchool));

        out.accept(new BuildingBlueprint("civ_airport_terminal", CIVIC, "國際機場航廈與航管塔台",
            "47×35 玻璃巨構航廈：報到櫃檯島、安檢門、免稅店與行李轉盤、2 樓超長候機廳與雙登機空橋走廊，側翼 4 層 360° 全景航管塔台（含單座電梯）（50 個物資點）",
            47, 35, 25, 50, CivicBuildings::airportTerminal));

        out.accept(new BuildingBlueprint("civ_parking_garage", CIVIC, "四層立體環道停車場",
            "41×33 開放式立體停車大樓：入口收費亭、密集柱陣掩體、各層停放多台彩色實體車輛掩體＋雙側 3 格寬斜坡車道直通露天頂層（44 個物資點）",
            41, 33, 23, 44, CivicBuildings::parkingGarage));

        out.accept(new BuildingBlueprint("civ_grand_library", CIVIC, "市立中央大圖書館",
            "39×31 3 層採光中庭大圖書館：1～2 樓密集書架迷宮、借還書櫃檯與閱讀長桌掩體，3 樓珍藏古籍環廊與屋頂花園露台（42 個物資點）",
            39, 31, 19, 42, CivicBuildings::grandLibrary));

        out.accept(new BuildingBlueprint("civ_metro_station", CIVIC, "都會中央火車站",
            "43×33 雙月台車站大廳：玻璃拱頂候車廳含售票櫃檯與剪票閘門陣，後方雙島式月台停靠 25 格長可登車交火通勤列車＋2 樓跨軌觀景天橋（44 個物資點）",
            43, 33, 18, 44, CivicBuildings::metroStation));

        out.accept(new BuildingBlueprint("civ_bank_vault", CIVIC, "聯邦中央銀行與金庫",
            "37×29 3 層古典列柱銀行：1 樓防彈玻璃櫃檯大廳與後方厚壁重裝金庫（金塊堆掩體），2 樓理財室隔間與機房，3 樓行長室與屋頂堡壘（40 個物資點）",
            37, 29, 19, 40, CivicBuildings::bankVault));
    }

    private static void highSchool(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-24, 0, 0, 24, 19, 34);

        // 校門廣場與兩翼建築地板 (z=2..33)
        c.floor(-24, 0, 0, 24, 34, Blocks.POLISHED_ANDESITE);
        // 西翼 3 層教學樓 (x=-23..-1, z=2..33, y=0, 6, 12, 17)
        c.box(-23, 0, 2, -1, 0, 33, Blocks.SMOOTH_STONE);
        c.walls(-23, 1, 2, -1, 5, 33, Blocks.BRICKS);
        c.box(-23, 6, 2, -1, 6, 33, Blocks.SMOOTH_STONE);
        c.walls(-23, 7, 2, -1, 11, 33, Blocks.WHITE_CONCRETE);
        c.box(-23, 12, 2, -1, 12, 33, Blocks.SMOOTH_STONE);
        c.walls(-23, 13, 2, -1, 16, 33, Blocks.WHITE_CONCRETE);
        c.box(-23, 17, 2, -1, 17, 33, Blocks.GRAY_CONCRETE);
        c.parapet(-23, 18, 2, -1, 33, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-23, 1, 2, -1, 17, 33, Blocks.DEEPSLATE_BRICKS);

        // 正門玄關雨棚與大門
        c.box(-4, 5, 0, 4, 5, 2, Blocks.STONE_BRICK_SLAB);
        c.doorway(-2, 1, 2, 5);
        c.doorway(-12, 1, 2, 3);
        c.doorway(-12, 1, 33, 3);
        c.doorwaySide(-23, 1, 17, 3);

        // 西翼教室玻璃窗與 1..3 樓 6 間獨立教室 (z=2..12 / 13..22 / 23..33, x=-22..-7)
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowZ(-23, fy, 4, 10, fy + 1, Blocks.GLASS_PANE);
            c.windowZ(-23, fy, 15, 20, fy + 1, Blocks.GLASS_PANE);
            c.windowZ(-23, fy, 25, 31, fy + 1, Blocks.GLASS_PANE);
        }
        for (int fy : new int[] { 1, 7, 13 }) {
            // 教室走廊牆 (x=-7)
            c.box(-7, fy, 3, -7, fy + 4, 32, Blocks.WHITE_CONCRETE);
            c.box(-22, fy, 12, -7, fy + 4, 12, Blocks.WHITE_CONCRETE);
            c.box(-22, fy, 23, -7, fy + 4, 23, Blocks.WHITE_CONCRETE);
            c.doorwaySide(-7, fy, 7, 2);
            c.doorwaySide(-7, fy, 17, 2);
            c.doorwaySide(-7, fy, 27, 2);
            c.windowZ(-7, fy + 2, 4, 6, fy + 3, Blocks.GLASS_PANE);
            c.windowZ(-7, fy + 2, 14, 16, fy + 3, Blocks.GLASS_PANE);
            c.windowZ(-7, fy + 2, 24, 26, fy + 3, Blocks.GLASS_PANE);
            for (int zBase : new int[] { 4, 14, 25 }) {
                c.counter(-21, zBase, -17, zBase, fy, Blocks.DARK_OAK_PLANKS);
                c.deskUnit(-19, fy, zBase + 3, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Direction.SOUTH);
                c.deskUnit(-15, fy, zBase + 3, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Direction.SOUTH);
                c.deskUnit(-19, fy, zBase + 6, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Direction.SOUTH);
                c.deskUnit(-15, fy, zBase + 6, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Direction.SOUTH);
                c.box(-10, fy, zBase + 1, -9, fy + 2, zBase + 1, Blocks.BOOKSHELF);
            }
        }
        // 教學樓寬 2 格樓梯 (無電梯)：x=-5..-4, 0->6->12->17
        c.staircaseZ(-5, -4, 0, 6, 8, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-3, -2, 6, 12, 21, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-5, -4, 12, 17, 23, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);

        // === 東翼挑高室內籃球體育館 (x=0..23, z=2..33, 屋頂 y=15) ===
        c.box(0, 0, 2, 23, 0, 33, Blocks.OAK_PLANKS);
        c.walls(0, 1, 2, 23, 14, 33, Blocks.LIGHT_GRAY_CONCRETE);
        c.clear(1, 1, 3, 22, 14, 32);
        c.box(0, 15, 2, 23, 15, 33, Blocks.DEEPSLATE_TILES);
        c.parapet(0, 16, 2, 23, 33, Blocks.STONE_BRICK_WALL, true);
        // 貫通教學樓與體育館通道
        c.doorwaySide(-1, 1, 7, 3);
        c.doorwaySide(-1, 1, 24, 3);
        c.doorwaySide(0, 1, 7, 3);
        c.doorwaySide(0, 1, 24, 3);
        c.doorwaySide(-1, 7, 15, 3);
        c.doorwaySide(0, 7, 15, 3);
        c.doorway(10, 1, 2, 4);
        c.doorway(10, 1, 33, 4);
        c.doorwaySide(23, 1, 16, 3);
        // 籃球場邊線＋雙籃框＋觀眾階梯看台＋2 樓環廊
        c.box(5, 0, 6, 16, 0, 29, Blocks.BIRCH_PLANKS);
        c.box(5, 0, 17, 16, 0, 18, Blocks.ORANGE_CONCRETE);
        c.pillar(10, 1, 5, 5, Blocks.IRON_BARS);  c.set(10, 5, 6, Blocks.ORANGE_CONCRETE);
        c.pillar(10, 1, 5, 30, Blocks.IRON_BARS); c.set(10, 5, 29, Blocks.ORANGE_CONCRETE);
        for (int s = 0; s < 4; s++) {
            c.box(22 - s, 1 + s, 7, 22, 1 + s, 28, Blocks.SPRUCE_PLANKS);
        }
        c.box(1, 6, 3, 5, 6, 32, Blocks.SMOOTH_STONE);
        c.windowZ(5, 7, 3, 32, 7, Blocks.IRON_BARS);
        c.staircaseZ(1, 2, 0, 6, 11, 1, Blocks.OAK_STAIRS, Blocks.SMOOTH_STONE, Blocks.OAK_PLANKS);
        c.bunkerCoverL(9, 1, 14, 2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(12, 1, 21, -2, -2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        c.ceilingLights(-20, -4, 5, 4, 31, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-20, -4, 11, 4, 31, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-20, -4, 16, 4, 31, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(4, 20, 14, 6, 30, 7, 7, Blocks.SEA_LANTERN);

        // 52 個物資箱點位（貼近教室牆壁／書架／看台／護欄）
        c.loot(-12, 1, 7);  c.loot(-12, 1, 17); c.loot(-12, 1, 28);
        c.loot(-18, 1, 9);  c.loot(-18, 1, 19); c.loot(-18, 1, 29); c.loot(-6, 1, 17);
        c.loot(-12, 7, 7);  c.loot(-12, 7, 17); c.loot(-12, 7, 28);
        c.loot(-18, 7, 9);  c.loot(-18, 7, 19); c.loot(-18, 7, 29); c.loot(-6, 7, 15);
        c.loot(-12, 13, 7); c.loot(-12, 13, 17); c.loot(-12, 13, 28);
        c.loot(-18, 13, 9); c.loot(-18, 13, 19); c.loot(-18, 13, 29); c.loot(-6, 13, 17);
        c.loot(-16, 18, 5); c.loot(-16, 18, 30); c.loot(-6, 18, 5);  c.loot(-6, 18, 30);
        c.loot(2, 1, 4);    c.loot(2, 1, 31);   c.loot(7, 1, 9);    c.loot(14, 1, 9);
        c.loot(7, 1, 17);   c.loot(14, 1, 17);  c.loot(7, 1, 26);   c.loot(14, 1, 26);
        c.loot(21, 5, 10);  c.loot(21, 5, 17);  c.loot(21, 5, 25);
        c.loot(4, 7, 6);    c.loot(4, 7, 18);   c.loot(4, 7, 29);
        c.loot(4, 16, 4);   c.loot(4, 16, 31);  c.loot(21, 16, 4);  c.loot(21, 16, 31);
        c.loot(12, 16, 31); c.loot(-12, 18, 31); c.loot(10, 1, 13); c.loot(12, 1, 22);
        c.loot(-15, 1, 5);  c.loot(-15, 7, 5);  c.loot(-15, 13, 5); c.loot(-11, 1, 24); c.loot(-11, 7, 24);
    }

    private static void airportTerminal(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-23, 0, 0, 23, 25, 34);

        c.floor(-23, 0, 0, 23, 34, Blocks.POLISHED_ANDESITE);
        // 前庭機場計程車接客區
        c.parkedCar(-15, 1, 1, Blocks.YELLOW_CONCRETE, false);
        c.parkedCar(5, 1, 1, Blocks.WHITE_CONCRETE, false);

        // 主航廈 (x=-22..13, z=4..27, y=0, 6, 13)
        c.box(-22, 0, 4, 13, 0, 27, Blocks.SMOOTH_QUARTZ);
        c.walls(-22, 1, 4, 13, 5, 27, Blocks.WHITE_CONCRETE);
        c.box(-22, 6, 4, 13, 6, 27, Blocks.SMOOTH_QUARTZ);
        c.walls(-22, 7, 4, 13, 12, 27, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-22, 13, 4, 13, 13, 27, Blocks.GRAY_CONCRETE);
        c.box(-16, 13, 10, 7, 13, 21, Blocks.LIGHT_BLUE_STAINED_GLASS);
        c.parapet(-22, 14, 4, 13, 27, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-22, 1, 4, 13, 13, 27, Blocks.GRAY_CONCRETE);

        c.doorway(-12, 1, 4, 4);
        c.doorway(-2, 1, 4, 4);
        c.doorway(-7, 1, 27, 5);
        c.doorwaySide(-22, 1, 15, 3);
        c.windowX(-20, 2, 4, -14, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-8, 2, 4, -3, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(3, 2, 4, 11, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-20, 8, 4, 11, 11, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-20, 8, 27, 11, 11, Blocks.CYAN_STAINED_GLASS_PANE);

        // 1 樓：報到櫃檯島＋安檢通道＋免稅店＋行李轉盤
        for (int x : new int[] { -17, -7, 3 }) {
            c.counter(x - 2, 8, x + 2, 9, 1, Blocks.QUARTZ_BLOCK);
        }
        for (int x : new int[] { -14, -4, 6 }) {
            c.box(x, 1, 14, x, 3, 14, Blocks.IRON_BARS);
            c.box(x + 2, 1, 14, x + 2, 3, 14, Blocks.IRON_BARS);
            c.box(x, 3, 14, x + 2, 3, 14, Blocks.DEEPSLATE_TILES);
        }
        c.box(-20, 1, 19, -15, 3, 19, Blocks.BOOKSHELF); // 免稅商店
        c.box(-8, 1, 20, -1, 1, 24, Blocks.BLACK_CONCRETE);
        c.clear(-6, 1, 21, -3, 1, 23);
        c.box(3, 1, 20, 10, 1, 24, Blocks.BLACK_CONCRETE);
        c.clear(5, 1, 21, 8, 1, 23);

        // 2 樓：候機大廳長排座椅＋咖啡吧＋雙凸出登機空橋 (z=27..34)
        for (int z : new int[] { 10, 16, 22 }) {
            c.sofaX(-15, -9, 7, z, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
            c.sofaX(-2, 4, 7, z, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        }
        c.counter(7, 10, 11, 15, 7, Blocks.DARK_OAK_PLANKS);
        for (int bx0 : new int[] { -16, 2 }) {
            c.box(bx0, 6, 27, bx0 + 5, 6, 34, Blocks.DEEPSLATE_TILES);
            c.walls(bx0, 7, 27, bx0 + 5, 10, 34, Blocks.CYAN_STAINED_GLASS_PANE);
            c.box(bx0, 11, 27, bx0 + 5, 11, 34, Blocks.GRAY_CONCRETE);
            c.doorway(bx0 + 1, 7, 27, 3);
            c.doorway(bx0 + 1, 7, 34, 3);
        }

        // 航廈寬 2 格對稱樓梯 (0->6->13)
        c.staircaseZ(-20, -19, 0, 6, 11, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(10, 11, 0, 6, 11, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-20, -19, 6, 13, 24, -1, Blocks.QUARTZ_STAIRS, Blocks.GRAY_CONCRETE, Blocks.QUARTZ_BLOCK);

        // === 東側 4 層 360° 全景航管塔台 (x=14..23, z=7..18, 樓板 y=0, 6, 12, 18, 23；保留全建物唯一單座電梯) ===
        c.walls(14, 1, 7, 23, 18, 18, Blocks.GRAY_CONCRETE);
        c.clear(15, 1, 8, 22, 18, 17);
        c.box(14, 6, 7, 23, 6, 18, Blocks.SMOOTH_STONE);
        c.box(14, 12, 7, 23, 12, 18, Blocks.SMOOTH_STONE);
        c.box(13, 18, 6, 23, 18, 19, Blocks.DEEPSLATE_BRICKS);
        c.walls(13, 19, 6, 23, 22, 19, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(13, 23, 6, 23, 23, 19, Blocks.DEEPSLATE_TILES);
        c.pillar(18, 24, 25, 12, Blocks.IRON_BARS);
        c.doorwaySide(13, 1, 11, 3);
        c.doorwaySide(14, 1, 11, 3);
        c.doorwaySide(13, 7, 11, 3);
        c.doorwaySide(14, 7, 11, 3);
        c.deskUnit(16, 19, 10, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        c.deskUnit(20, 19, 14, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.staircaseZ(16, 17, 0, 6, 10, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(16, 17, 6, 12, 15, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(16, 17, 12, 18, 10, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.elevator(21, 16, 0, 6, 12, 18);

        c.ceilingLights(-19, 10, 5, 6, 25, 7, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-19, 10, 12, 6, 25, 7, 7, Blocks.SEA_LANTERN);

        // 50 個物資箱點位（貼近櫃檯／座椅／空橋牆／汽車）
        c.loot(-17, 1, 7);  c.loot(-7, 1, 7);  c.loot(3, 1, 7);
        c.loot(-17, 1, 10); c.loot(-7, 1, 10); c.loot(3, 1, 10);
        c.loot(-17, 1, 20); c.loot(-9, 1, 22); c.loot(2, 1, 22);  c.loot(11, 1, 22);
        c.loot(-6, 1, 22);  c.loot(6, 1, 22);  c.loot(-20, 1, 25); c.loot(18, 1, 9);
        c.loot(-12, 7, 9);  c.loot(-1, 7, 9);  c.loot(9, 7, 9);
        c.loot(-12, 7, 15); c.loot(-1, 7, 15); c.loot(9, 7, 16);
        c.loot(-12, 7, 21); c.loot(-1, 7, 21); c.loot(-13, 7, 26); c.loot(4, 7, 26);
        c.loot(-14, 7, 30); c.loot(-12, 7, 33); c.loot(4, 7, 30);  c.loot(6, 7, 33);
        c.loot(18, 7, 9);   c.loot(18, 13, 9);  c.loot(18, 13, 16);
        c.loot(15, 19, 8);  c.loot(21, 19, 8);  c.loot(15, 19, 17); c.loot(21, 19, 17);
        c.loot(-19, 14, 6); c.loot(11, 14, 6);  c.loot(-19, 14, 25); c.loot(11, 14, 25);
        c.loot(-5, 14, 5);  c.loot(5, 14, 5);   c.loot(-5, 14, 26);  c.loot(5, 14, 26);
        c.loot(-11, 1, 14); c.loot(-1, 1, 14);  c.loot(9, 1, 14);   c.loot(-6, 7, 26);  c.loot(0, 7, 11);
        c.loot(-14, 1, 3);  c.loot(8, 1, 3);
    }

    private static void parkingGarage(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-20, 0, 0, 20, 23, 32);

        // 4 層立體停車場 (y=0, 6, 12, 18；兩側 3 格寬車道斜坡 x=-18..-16 & 16..18)
        for (int fl = 0; fl < 4; fl++) {
            int fy = fl * 6;
            if (fl == 0) c.floor(-20, 0, 0, 20, 32, Blocks.GRAY_CONCRETE);
            else c.box(-20, fy, 0, 20, fy, 32, Blocks.GRAY_CONCRETE);
            c.parapet(-20, fy + 1, 0, 20, 32, Blocks.STONE_BRICK_WALL, false);
            if (fl == 0) {
                c.doorway(-3, 1, 0, 7);
                c.doorway(-3, 1, 32, 7);
                c.doorwaySide(-20, 1, 15, 4);
                c.doorwaySide(20, 1, 15, 4);
            }
        }
        // 密集結構柱陣掩體 (提供全場鋼柱遮蔽)
        c.columnGrid(-14, 14, 1, 18, 4, 28, 7, 8, Blocks.DEEPSLATE_BRICKS);
        // 入口收費亭
        c.walls(-2, 1, 1, 2, 3, 3, Blocks.WHITE_CONCRETE);
        c.box(-2, 4, 1, 2, 4, 3, Blocks.SMOOTH_STONE_SLAB);
        c.doorwaySide(2, 1, 2, 1);
        c.windowX(-1, 2, 1, 1, 2, Blocks.GLASS_PANE);

        // 各樓層停放彩色實體車輛掩體
        c.parkedCar(-11, 1, 8, Blocks.RED_CONCRETE, true);
        c.parkedCar(-11, 1, 20, Blocks.BLUE_CONCRETE, true);
        c.parkedCar(11, 1, 8, Blocks.YELLOW_CONCRETE, true);
        c.parkedCar(11, 1, 20, Blocks.GREEN_CONCRETE, true);
        c.parkedCar(0, 1, 16, Blocks.WHITE_CONCRETE, false);

        c.parkedCar(-11, 7, 8, Blocks.ORANGE_CONCRETE, true);
        c.parkedCar(-11, 7, 22, Blocks.CYAN_CONCRETE, true);
        c.parkedCar(11, 7, 8, Blocks.WHITE_CONCRETE, true);
        c.parkedCar(11, 7, 22, Blocks.RED_CONCRETE, true);
        c.parkedCar(0, 7, 14, Blocks.LIGHT_GRAY_CONCRETE, false);

        c.parkedCar(-11, 13, 10, Blocks.BLUE_CONCRETE, true);
        c.parkedCar(-11, 13, 24, Blocks.YELLOW_CONCRETE, true);
        c.parkedCar(11, 13, 10, Blocks.LIME_CONCRETE, true);
        c.parkedCar(11, 13, 24, Blocks.ORANGE_CONCRETE, true);
        c.parkedCar(0, 13, 18, Blocks.BLACK_CONCRETE, false);

        c.parkedCar(-11, 19, 12, Blocks.WHITE_CONCRETE, true);
        c.parkedCar(11, 19, 12, Blocks.BLUE_CONCRETE, true);
        c.parkedCar(0, 19, 22, Blocks.RED_CONCRETE, false);
        c.bunkerCoverL(-4, 19, 8, 2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(4, 19, 8, -2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 雙側寬 3 格斜坡車道 (0->6->12->18，無電梯)
        c.staircaseZ(-18, -16, 0, 6, 8, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(16, 18, 0, 6, 8, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(-18, -16, 6, 12, 25, -1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(16, 18, 6, 12, 25, -1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(-18, -16, 12, 18, 8, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(16, 18, 12, 18, 8, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);

        c.ceilingLights(-12, 12, 5, 4, 28, 8, 8, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 11, 4, 28, 8, 8, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 17, 4, 28, 8, 8, Blocks.SEA_LANTERN);

        // 44 個物資箱點位（貼近車輛／支柱／矮牆）
        for (int fy : new int[] { 1, 7, 13, 19 }) {
            c.loot(-9, fy, 8);   c.loot(9, fy, 8);
            c.loot(-9, fy, 22);  c.loot(9, fy, 22);
            c.loot(-2, fy, 15);  c.loot(2, fy, 15);
            c.loot(0, fy, 4);    c.loot(0, fy, 29);
            c.loot(-14, fy, 13); c.loot(14, fy, 13);
            c.loot(-11, fy, 30);
        }
    }

    private static void grandLibrary(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-19, 0, 0, 19, 19, 30);

        c.floor(-19, 0, 0, 19, 30, Blocks.POLISHED_ANDESITE);
        c.box(-18, 0, 3, 18, 0, 30, Blocks.SPRUCE_PLANKS);
        c.walls(-18, 1, 3, 18, 5, 30, Blocks.WHITE_CONCRETE);
        c.box(-18, 6, 3, 18, 6, 30, Blocks.SMOOTH_QUARTZ);
        c.walls(-18, 7, 3, 18, 11, 30, Blocks.WHITE_CONCRETE);
        c.box(-18, 12, 3, 18, 12, 30, Blocks.SMOOTH_QUARTZ);
        c.walls(-18, 13, 3, 18, 16, 30, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-18, 17, 3, 18, 17, 30, Blocks.DEEPSLATE_TILES);
        c.parapet(-18, 18, 3, 18, 30, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-18, 1, 3, 18, 17, 30, Blocks.QUARTZ_PILLAR);

        // 古典門廊＋四向入口＋採光玻璃天窗
        for (int px : new int[] { -6, -3, 3, 6 }) c.pillar(px, 1, 5, 0, Blocks.QUARTZ_PILLAR);
        c.box(-7, 5, 0, 7, 5, 2, Blocks.SMOOTH_QUARTZ);
        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 30, 5);
        c.doorwaySide(-18, 1, 16, 3);
        c.doorwaySide(18, 1, 16, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-16, fy, 3, -8, fy + 2, Blocks.GLASS_PANE);
            c.windowX(8, fy, 3, 16, fy + 2, Blocks.GLASS_PANE);
            c.windowZ(-18, fy, 6, 13, fy + 2, Blocks.GLASS_PANE);
            c.windowZ(18, fy, 6, 13, fy + 2, Blocks.GLASS_PANE);
        }

        // 中庭挑空井 (x=-5..5, z=11..21)
        c.box(-5, 6, 11, 5, 16, 21, Blocks.AIR);
        c.box(-6, 7, 10, 6, 7, 22, Blocks.DARK_OAK_FENCE);
        c.box(-5, 7, 11, 5, 7, 21, Blocks.AIR);
        c.box(-6, 13, 10, 6, 13, 22, Blocks.DARK_OAK_FENCE);
        c.box(-5, 13, 11, 5, 13, 21, Blocks.AIR);
        c.box(-6, 17, 10, 6, 17, 22, Blocks.LIGHT_BLUE_STAINED_GLASS);

        // 1F：借閱櫃檯＋書架迷宮陣＋閱讀長桌
        c.counter(-4, 7, 4, 7, 1, Blocks.DARK_OAK_PLANKS);
        for (int z : new int[] { 8, 13, 18, 23 }) {
            c.box(-14, 1, z, -8, 3, z, Blocks.BOOKSHELF);
            c.box(8, 1, z, 14, 3, z, Blocks.BOOKSHELF);
        }
        c.planter(-2, 14, 2, 18, 1, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);

        // 2F & 3F 書庫長廊
        for (int fy : new int[] { 7, 13 }) {
            for (int z : new int[] { 8, 15, 23 }) {
                c.box(-14, fy, z, -9, fy + 2, z, Blocks.BOOKSHELF);
                c.box(9, fy, z, 14, fy + 2, z, Blocks.BOOKSHELF);
            }
            c.counter(-4, 25, 4, 25, fy, Blocks.DARK_OAK_PLANKS);
        }
        // 屋頂花園露台掩體
        c.tallCover(-13, 7, -8, 7, 18, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(8, 7, 13, 7, 18, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.planter(-12, 23, -8, 25, 18, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);
        c.planter(8, 23, 12, 25, 18, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);

        c.staircaseZ(-17, -16, 0, 6, 19, 1, Blocks.DARK_OAK_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(16, 17, 0, 6, 19, 1, Blocks.DARK_OAK_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(-17, -16, 6, 12, 24, -1, Blocks.DARK_OAK_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(16, 17, 12, 17, 19, 1, Blocks.DARK_OAK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-15, 15, 5, 6, 28, 7, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 11, 6, 28, 7, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 16, 6, 28, 7, 7, Blocks.SEA_LANTERN);

        // 42 個物資箱點位（貼近書架／花圃／櫃檯／護欄）
        c.loot(-11, 1, 9);  c.loot(11, 1, 9);   c.loot(-11, 1, 14); c.loot(11, 1, 14);
        c.loot(-11, 1, 19); c.loot(11, 1, 19);  c.loot(-11, 1, 24); c.loot(11, 1, 24);
        c.loot(-3, 1, 8);   c.loot(3, 1, 8);    c.loot(-3, 1, 16);  c.loot(3, 1, 16);
        c.loot(-5, 1, 28);  c.loot(5, 1, 28);
        c.loot(-11, 7, 9);  c.loot(11, 7, 9);   c.loot(-11, 7, 16); c.loot(11, 7, 16);
        c.loot(-11, 7, 24); c.loot(11, 7, 24);  c.loot(-6, 7, 12);  c.loot(6, 7, 12);
        c.loot(-6, 7, 20);  c.loot(6, 7, 20);   c.loot(-3, 7, 26);  c.loot(3, 7, 26);
        c.loot(-11, 13, 9); c.loot(11, 13, 9);  c.loot(-11, 13, 16); c.loot(11, 13, 16);
        c.loot(-11, 13, 24); c.loot(11, 13, 24); c.loot(-6, 13, 14); c.loot(6, 13, 14);
        c.loot(-3, 13, 26); c.loot(3, 13, 26);
        c.loot(-10, 18, 8); c.loot(10, 18, 8);  c.loot(-10, 18, 22); c.loot(10, 18, 22);
        c.loot(-16, 18, 16); c.loot(16, 18, 16);
    }

    private static void metroStation(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-21, 0, 0, 21, 18, 32);

        c.floor(-21, 0, 0, 21, 32, Blocks.POLISHED_ANDESITE);
        c.box(-20, 0, 3, 20, 0, 31, Blocks.SMOOTH_STONE);
        c.walls(-20, 1, 3, 20, 11, 31, Blocks.LIGHT_GRAY_CONCRETE);
        c.walls(-20, 1, 3, 20, 2, 31, Blocks.DEEPSLATE_BRICKS);
        c.clear(-19, 1, 4, 19, 11, 30);
        c.cornerPillars(-20, 1, 3, 20, 12, 31, Blocks.DEEPSLATE_BRICKS);

        // 大拱頂車站入口與月台鐵軌開口
        c.doorway(-3, 1, 3, 7);
        c.doorway(-3, 1, 31, 7);
        for (int y = 1; y <= 6; y++) {
            for (int z = 20; z <= 26; z++) {
                c.air(-20, y, z);
                c.air(20, y, z);
            }
        }
        c.windowX(-18, 7, 3, -6, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(6, 7, 3, 18, 9, Blocks.CYAN_STAINED_GLASS_PANE);

        // 前廳售票島＋剪票閘門＋雙島式月台停放 25 格長通勤列車 (x=-12..12, z=21..25)
        c.counter(-6, 7, 6, 8, 1, Blocks.BLUE_CONCRETE);
        for (int gx = -12; gx <= 12; gx += 4) {
            c.tallCover(gx, 12, gx + 1, 12, 1, Blocks.IRON_BLOCK, Blocks.SMOOTH_STONE_SLAB);
        }
        // 列車 (z=21..25, y=1..5) 可進入與穿行
        c.box(-13, 0, 20, 13, 0, 26, Blocks.BLACK_CONCRETE);
        c.box(-12, 1, 21, 12, 5, 25, Blocks.WHITE_CONCRETE);
        c.box(-12, 2, 21, 12, 2, 25, Blocks.BLUE_CONCRETE);
        c.clear(-11, 2, 22, 11, 4, 24);
        c.doorway(-7, 2, 21, 2); c.doorway(0, 2, 21, 2); c.doorway(7, 2, 21, 2);
        c.doorway(-7, 2, 25, 2); c.doorway(0, 2, 25, 2); c.doorway(7, 2, 25, 2);
        c.stair(-7, 1, 20, Blocks.STONE_BRICK_STAIRS, Direction.NORTH);
        c.stair(0, 1, 20, Blocks.STONE_BRICK_STAIRS, Direction.NORTH);
        c.stair(7, 1, 20, Blocks.STONE_BRICK_STAIRS, Direction.NORTH);
        c.sofaX(-5, -2, 2, 23, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
        c.sofaX(2, 5, 2, 23, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);

        // 2 樓跨軌觀景天橋 (y=6, z=5..9 & z=27..30, x=±19..±14 & z=13..17)
        c.box(-19, 6, 4, -14, 6, 30, Blocks.DEEPSLATE_TILES);
        c.box(14, 6, 4, 19, 6, 30, Blocks.DEEPSLATE_TILES);
        c.box(-14, 6, 14, 14, 6, 17, Blocks.DEEPSLATE_TILES);
        c.windowX(-14, 7, 14, 14, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-14, 7, 17, 14, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(-14, 7, 4, 30, 7, Blocks.IRON_BARS);
        c.windowZ(14, 7, 4, 30, 7, Blocks.IRON_BARS);
        c.tallCover(-3, 15, 3, 16, 7, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);

        c.staircaseZ(-18, -17, 0, 6, 10, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);
        c.staircaseZ(17, 18, 0, 6, 10, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);

        // 玻璃拱頂 (y=12..18)
        c.box(-20, 12, 3, 20, 12, 31, Blocks.DEEPSLATE_BRICKS);
        c.clear(-18, 12, 5, 18, 12, 29);
        c.gableRoofZ(-20, 12, 3, 20, 31, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.CYAN_STAINED_GLASS);
        c.ceilingLights(-16, 16, 11, 6, 28, 8, 7, Blocks.SEA_LANTERN);

        // 44 個物資箱點位（貼近售票台／閘門／車廂壁與座椅／月台與欄杆）
        c.loot(-5, 1, 9);   c.loot(5, 1, 9);    c.loot(-12, 1, 11); c.loot(12, 1, 11);
        c.loot(-4, 1, 13);  c.loot(4, 1, 13);   c.loot(-15, 1, 6);  c.loot(15, 1, 6);
        c.loot(-15, 1, 17); c.loot(15, 1, 17);  c.loot(-9, 1, 20);  c.loot(9, 1, 20);
        c.loot(-9, 1, 26);  c.loot(9, 1, 26);   c.loot(-3, 1, 26);  c.loot(3, 1, 26);
        c.loot(-9, 2, 23);  c.loot(-4, 2, 24);  c.loot(4, 2, 24);   c.loot(9, 2, 23);
        c.loot(-16, 1, 28); c.loot(16, 1, 28);  c.loot(-6, 1, 29);  c.loot(6, 1, 29);
        c.loot(-16, 7, 6);  c.loot(16, 7, 6);   c.loot(-16, 7, 15); c.loot(16, 7, 15);
        c.loot(-16, 7, 22); c.loot(16, 7, 22);  c.loot(-16, 7, 28); c.loot(16, 7, 28);
        c.loot(-9, 7, 15);  c.loot(9, 7, 15);   c.loot(-4, 7, 15);  c.loot(4, 7, 15);
        c.loot(0, 7, 16);   c.loot(-15, 7, 10); c.loot(15, 7, 10); c.loot(-15, 7, 26);
        c.loot(15, 7, 26);  c.loot(-1, 1, 19);  c.loot(1, 1, 19);   c.loot(0, 1, 11);
    }

    private static void bankVault(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-18, 0, 0, 18, 19, 28);

        c.floor(-18, 0, 0, 18, 28, Blocks.POLISHED_ANDESITE);
        c.box(-17, 0, 4, 17, 0, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 1, 4, 17, 5, 28, Blocks.STONE_BRICKS);
        c.box(-17, 6, 4, 17, 6, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 7, 4, 17, 11, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 12, 4, 17, 12, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 13, 4, 17, 16, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-17, 17, 4, 17, 17, 28, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-17, 18, 4, 17, 28, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-17, 1, 4, 17, 17, 28, Blocks.QUARTZ_PILLAR);

        // 古典門柱走廊 (z=0..4)
        for (int px : new int[] { -8, -4, 4, 8 }) c.pillar(px, 1, 5, 1, Blocks.QUARTZ_PILLAR);
        c.box(-9, 5, 0, 9, 5, 3, Blocks.SMOOTH_QUARTZ);
        c.doorway(-2, 1, 4, 5);
        c.doorway(-2, 1, 28, 4);
        c.doorwaySide(-17, 1, 15, 3);
        c.doorwaySide(17, 1, 15, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-14, fy, 4, -7, fy + 2, Blocks.IRON_BARS);
            c.windowX(7, fy, 4, 14, fy + 2, Blocks.IRON_BARS);
            c.windowZ(-17, fy, 7, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(17, fy, 7, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // 1F：銀行防彈櫃檯大廳＋後方重裝金庫 (x=-7..7, z=18..26)
        c.counter(-8, 10, 8, 10, 1, Blocks.QUARTZ_BLOCK);
        c.windowX(-8, 2, 10, 8, 3, Blocks.CYAN_STAINED_GLASS_PANE);
        c.walls(-7, 1, 18, 7, 5, 26, Blocks.DEEPSLATE_BRICKS);
        c.doorway(-1, 1, 18, 3);
        c.doorwaySide(-7, 1, 21, 2);
        c.doorwaySide(7, 1, 21, 2);
        c.box(-4, 1, 21, -2, 2, 23, Blocks.GOLD_BLOCK);
        c.box(2, 1, 21, 4, 2, 23, Blocks.GOLD_BLOCK);

        // 2F & 3F 理財辦公室＋屋頂掩體
        c.box(-6, 7, 6, -6, 11, 23, Blocks.WHITE_CONCRETE);
        c.box(6, 7, 6, 6, 11, 23, Blocks.WHITE_CONCRETE);
        c.doorwaySide(-6, 7, 14, 3);
        c.doorwaySide(6, 7, 14, 3);
        c.deskUnit(-11, 7, 10, Blocks.DARK_OAK_PLANKS, Blocks.OAK_STAIRS, Direction.NORTH);
        c.deskUnit(11, 7, 10, Blocks.DARK_OAK_PLANKS, Blocks.OAK_STAIRS, Direction.NORTH);
        c.serverRack(-12, 7, 20, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        c.serverRack(12, 7, 20, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        c.counter(-4, 14, 4, 14, 13, Blocks.DARK_OAK_PLANKS);
        c.tallCover(-11, 21, -7, 21, 13, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(7, 21, 11, 21, 13, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(-10, 9, -6, 9, 18, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(6, 9, 10, 9, 18, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        c.staircaseZ(-16, -15, 0, 6, 18, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(15, 16, 0, 6, 18, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-16, -15, 6, 12, 23, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(15, 16, 12, 17, 18, 1, Blocks.QUARTZ_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.QUARTZ_BLOCK);

        c.ceilingLights(-13, 13, 5, 6, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-13, 13, 11, 6, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-13, 13, 16, 6, 26, 6, 6, Blocks.SEA_LANTERN);

        // 40 個物資箱點位（全部貼近櫃檯／金庫壁／金塊堆／辦公桌／女兒牆）
        c.loot(-6, 1, 9);   c.loot(6, 1, 9);    c.loot(-6, 1, 11);  c.loot(6, 1, 11);
        c.loot(-14, 1, 6);  c.loot(14, 1, 6);   c.loot(-12, 1, 20); c.loot(12, 1, 20);
        c.loot(-5, 1, 20);  c.loot(5, 1, 20);   c.loot(-5, 1, 24);  c.loot(5, 1, 24);
        c.loot(0, 1, 24);   c.loot(-8, 1, 1);   c.loot(8, 1, 1);
        c.loot(-10, 7, 11); c.loot(10, 7, 11);  c.loot(-11, 7, 20); c.loot(11, 7, 20);
        c.loot(-4, 7, 8);   c.loot(4, 7, 8);    c.loot(-4, 7, 22);  c.loot(4, 7, 22);
        c.loot(-14, 7, 26); c.loot(14, 7, 26);
        c.loot(-11, 13, 8); c.loot(11, 13, 8);  c.loot(-11, 13, 20); c.loot(11, 13, 20);
        c.loot(-4, 13, 15); c.loot(4, 13, 15);  c.loot(0, 13, 25);  c.loot(-13, 13, 25);
        c.loot(13, 13, 25);
        c.loot(-10, 18, 10); c.loot(10, 18, 10); c.loot(-12, 18, 25); c.loot(12, 18, 25);
        c.loot(0, 18, 7);   c.loot(0, 18, 25);
    }
}
