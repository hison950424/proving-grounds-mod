package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.SCIFI;

/**
 * 檔案: ScifiBuildings.java
 * 目的: 廢土科幻與普羅米修斯基地風格 4 棟大型／超大型建築（全數無木箱、樓梯最後一階完整齊平、青色玻璃片／鐵欄／石牆自動連接）。
 */
final class ScifiBuildings {

    private ScifiBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("sci_research_complex", SCIFI, "普羅米修斯生技實驗總部",
            "3 層科幻裝甲大樓：貫穿全樓的中央發光基因培養圓柱、左右生化隔離槽與伺服器機房、2 樓環形天橋與 4 間無塵室、3 樓艦橋甲板與採光天窗（42 個物資點）",
            37, 29, 20, 42, ScifiBuildings::researchComplex));

        out.accept(new BuildingBlueprint("sci_starport_hangar", SCIFI, "星際運輸艦巨型機庫",
            "超大跨距拱頂裝甲機庫：中央停放 17 格長可進入艙內的科幻星際運輸艇（含雙翼／駕駛艙／引擎）、雙層整修廊道與跨機高空天橋（40 個物資點）",
            39, 31, 18, 40, ScifiBuildings::starportHangar));

        out.accept(new BuildingBlueprint("sci_reactor_citadel", SCIFI, "反應爐能源要塞塔",
            "八角切角 4 層高塔要塞：四面發光導能槽與十字氣密入口、貫穿全塔的脈衝反應爐核心、四象限環形空橋戰場、頂端能量尖塔與單座電梯（38 個物資點）",
            29, 29, 25, 38, ScifiBuildings::reactorCitadel));

        out.accept(new BuildingBlueprint("sci_mech_bay", SCIFI, "巨型機甲整備格納庫",
            "45×35 超挑高戰鬥機甲基地：正中央豎立 20 格高實體雙足戰鬥機甲（可登艦胸艙／肩砲架／駕駛台）、3 層環繞檢修空橋、雷達校準室與防爆掩體陣（50 個物資點）",
            45, 35, 25, 50, ScifiBuildings::mechBay));
    }

    private static void researchComplex(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-18, 0, 0, 18, 20, 28);

        // 3 層白色＋深板岩科技大樓 (y=0, 6, 12, 18)
        c.floor(-18, 0, 0, 18, 28, Blocks.DEEPSLATE_TILES);
        c.box(-17, 0, 3, 17, 0, 28, Blocks.POLISHED_DIORITE);
        c.walls(-17, 1, 3, 17, 5, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 6, 3, 17, 6, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 7, 3, 17, 11, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 12, 3, 17, 12, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 13, 3, 17, 17, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-17, 18, 3, 17, 18, 28, Blocks.DEEPSLATE_BRICKS);
        c.cornerPillars(-17, 1, 3, 17, 18, 28, Blocks.DEEPSLATE_BRICKS);

        // 前庭門廊＋四向入口＋青色科技玻璃幕牆
        c.pillar(-6, 1, 5, 0, Blocks.DEEPSLATE_BRICKS);
        c.pillar(6, 1, 5, 0, Blocks.DEEPSLATE_BRICKS);
        c.box(-7, 5, 0, 7, 5, 2, Blocks.CYAN_CONCRETE);
        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-17, 1, 14, 3);
        c.doorwaySide(17, 1, 14, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-15, fy, 3, -7, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(7, fy, 3, 15, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-17, fy, 6, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-17, fy, 18, 25, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(17, fy, 6, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(17, fy, 18, 25, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // 中央貫穿 1～3 樓基因培養圓柱挑空井 (x=-6..6, z=10..20)＋屋頂天窗
        c.box(-6, 6, 10, 6, 17, 20, Blocks.AIR);
        c.box(-7, 7, 9, 7, 7, 21, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(-6, 7, 10, 6, 7, 20, Blocks.AIR);
        c.box(-7, 13, 9, 7, 13, 21, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(-6, 13, 10, 6, 13, 20, Blocks.AIR);
        c.box(-6, 18, 10, 6, 18, 20, Blocks.CYAN_STAINED_GLASS);
        // 中央核心培養柱 (x=-1..1, z=14..16, y=1..17)
        c.box(-2, 1, 13, 2, 1, 17, Blocks.DEEPSLATE_TILES);
        c.box(-1, 2, 14, 1, 17, 16, Blocks.CYAN_STAINED_GLASS);
        c.pillar(0, 2, 17, 15, Blocks.SEA_LANTERN);

        // === 1 樓：西翼生化培養槽＋東翼伺服器機庫 ===
        c.box(-8, 1, 5, -8, 5, 25, Blocks.WHITE_CONCRETE);
        c.doorwaySide(-8, 1, 14, 3);
        c.windowZ(-8, 2, 6, 12, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        for (int z : new int[] { 7, 12, 20 }) {
            c.box(-15, 1, z, -14, 4, z + 1, Blocks.CYAN_STAINED_GLASS);
            c.set(-15, 2, z, Blocks.SEA_LANTERN);
            c.counter(-12, z, -10, z, 1, Blocks.SMOOTH_QUARTZ);
        }
        c.box(8, 1, 5, 8, 5, 25, Blocks.WHITE_CONCRETE);
        c.doorwaySide(8, 1, 14, 3);
        c.windowZ(8, 2, 6, 12, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        for (int z : new int[] { 7, 11, 18, 22 }) {
            c.serverRack(12, 1, z, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
            c.serverRack(15, 1, z, 3, Blocks.DEEPSLATE_TILES, Blocks.REDSTONE_LAMP);
        }

        // === 2 樓：4 間玻璃隔離無塵實驗室 ===
        for (int[] room : new int[][] { { -16, -9, 4, 11 }, { -16, -9, 19, 26 }, { 9, 16, 4, 11 }, { 9, 16, 19, 26 } }) {
            int wallX = room[0] < 0 ? -9 : 9;
            int doorX = room[0] < 0 ? -9 : 9;
            int zMid = (room[2] + room[3]) / 2;
            c.box(room[0], 7, room[2], room[1], 11, room[2], Blocks.WHITE_CONCRETE);
            c.box(room[0], 7, room[3], room[1], 11, room[3], Blocks.WHITE_CONCRETE);
            c.box(wallX, 7, room[2], wallX, 11, room[3], Blocks.CYAN_STAINED_GLASS_PANE);
            c.doorwaySide(doorX, 7, zMid - 1, 2);
            c.deskUnit((room[0] + room[1]) / 2, 7, zMid, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        }

        // === 3 樓：艦橋式主控甲板與屋頂天線盤 ===
        c.counter(-5, 6, 5, 6, 13, Blocks.DEEPSLATE_TILES);
        c.deskUnit(-3, 13, 8, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        c.deskUnit(3, 13, 8, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        c.tallCover(-14, 14, -10, 14, 13, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(10, 14, 14, 14, 13, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE_SLAB);
        c.parapet(-17, 19, 3, 17, 28, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.pillar(13, 19, 23, 23, Blocks.IRON_BARS);
        c.box(12, 23, 22, 14, 23, 24, Blocks.IRON_BLOCK);

        // === 雙寬 2 格樓梯＋雙電梯井 ===
        c.staircaseZ(-16, -15, 0, 6, 12, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(15, 16, 0, 6, 12, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-16, -15, 6, 12, 17, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(15, 16, 12, 18, 12, 1, Blocks.QUARTZ_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        // 無電梯，改以對稱雙寬樓梯與增設掩體提升立體交火
        c.bunkerCoverL(-4, 1, 24, 2, -2, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_QUARTZ_SLAB);
        c.bunkerCoverL(4, 1, 24, -2, -2, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_QUARTZ_SLAB);

        c.ceilingLights(-15, 15, 5, 5, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 11, 5, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 17, 5, 26, 6, 6, Blocks.SEA_LANTERN);

        // 42 個物資箱點位
        c.loot(-12, 1, 9);  c.loot(-12, 1, 22); c.loot(10, 1, 9);  c.loot(10, 1, 22); c.loot(0, 1, 6);   c.loot(0, 1, 24);
        c.loot(-5, 1, 10);  c.loot(5, 1, 10);   c.loot(-5, 1, 20); c.loot(5, 1, 20);  c.loot(-12, 1, 16); c.loot(10, 1, 16);
        c.loot(-15, 1, 5);  c.loot(15, 1, 5);
        c.loot(-13, 7, 6);  c.loot(-11, 7, 9);  c.loot(-13, 7, 21); c.loot(-11, 7, 24);
        c.loot(13, 7, 6);   c.loot(11, 7, 9);   c.loot(13, 7, 21);  c.loot(11, 7, 24);
        c.loot(0, 7, 7);    c.loot(0, 7, 23);   c.loot(-6, 7, 23);  c.loot(6, 7, 7);
        c.loot(-12, 13, 8); c.loot(12, 13, 8);  c.loot(-4, 13, 24); c.loot(4, 13, 24);
        c.loot(-12, 13, 21); c.loot(12, 13, 21); c.loot(0, 13, 5);  c.loot(-8, 13, 12); c.loot(8, 13, 12); c.loot(0, 13, 25);
        c.loot(-12, 19, 15); c.loot(12, 19, 15); c.loot(-10, 19, 6); c.loot(10, 19, 6); c.loot(-10, 19, 24); c.loot(10, 19, 24);
    }

    private static void starportHangar(BuildingCanvas c) {
        c.foundation(Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.clear(-19, 0, 0, 19, 18, 30);

        // 機庫地板＋雙側跑道導引燈＋裝甲牆與拱頂
        c.floor(-19, 0, 0, 19, 30, Blocks.DEEPSLATE_TILES);
        for (int z = 2; z <= 28; z += 4) {
            c.set(-6, 0, z, Blocks.SEA_LANTERN);
            c.set(6, 0, z, Blocks.SEA_LANTERN);
        }
        c.walls(-18, 1, 2, 18, 11, 29, Blocks.LIGHT_GRAY_CONCRETE);
        c.walls(-18, 1, 2, 18, 2, 29, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.clear(-17, 1, 3, 17, 11, 28);
        c.cornerPillars(-18, 1, 2, 18, 12, 29, Blocks.POLISHED_BLACKSTONE_BRICKS);
        // 超寬機庫前後巨門 (寬 11 格，高 7 格)
        for (int y = 1; y <= 7; y++) {
            for (int x = -5; x <= 5; x++) {
                c.air(x, y, 2);
                c.air(x, y, 29);
            }
        }
        c.doorwaySide(-18, 1, 14, 4);
        c.doorwaySide(18, 1, 14, 4);
        c.windowX(-16, 8, 2, -7, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(7, 8, 2, 16, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(-18, 8, 5, 12, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(-18, 8, 19, 26, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(18, 8, 5, 12, 9, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(18, 8, 19, 26, 9, Blocks.CYAN_STAINED_GLASS_PANE);

        // === 1 樓核心亮點：停放一艘 17 格長、可進入艙內的實體星際運輸艇 (z=7..23) ===
        // 船身外殼 (x=-4..4, y=1..5, z=8..22)＋駕駛艙＋內部座位掩體
        c.box(-4, 1, 8, 4, 5, 22, Blocks.DEEPSLATE_TILES);
        c.box(-3, 2, 9, 3, 4, 21, Blocks.WHITE_CONCRETE);
        c.clear(-2, 2, 10, 2, 4, 20); // 機艙內部空間 (地板於 y=1)
        // 車頭駕駛艙與青色玻璃罩
        c.box(-3, 2, 7, 3, 3, 8, Blocks.CYAN_STAINED_GLASS);
        c.air(-2, 2, 8); c.air(0, 2, 8); c.air(2, 2, 8);
        c.deskUnit(0, 2, 9, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        // 左右機艙舷門與登機階梯 (y=1 進入機艙)
        c.doorwaySide(-4, 2, 14, 3);
        c.doorwaySide(4, 2, 14, 3);
        c.doorway(-1, 2, 22, 3);
        c.stair(-5, 1, 15, Blocks.QUARTZ_STAIRS, Direction.EAST);
        c.stair(5, 1, 15, Blocks.QUARTZ_STAIRS, Direction.WEST);
        // 艙內兩排戰術座椅掩體
        c.sofaZ(-2, 2, 11, 19, Blocks.POLISHED_ANDESITE_STAIRS, Direction.EAST);
        c.sofaZ(2, 2, 11, 19, Blocks.POLISHED_ANDESITE_STAIRS, Direction.WEST);
        // 左右雙機翼＋海晶燈引擎尾噴口
        c.box(-10, 2, 12, -5, 2, 19, Blocks.QUARTZ_BLOCK);
        c.box(5, 2, 12, 10, 2, 19, Blocks.QUARTZ_BLOCK);
        c.box(-3, 2, 23, -2, 3, 23, Blocks.SEA_LANTERN);
        c.box(2, 2, 23, 3, 3, 23, Blocks.SEA_LANTERN);

        // 機庫地板防爆掩體與能源充電柱
        c.tallCover(-14, 7, -10, 7, 1, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(10, 7, 14, 7, 1, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(-14, 23, -10, 23, 1, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(10, 23, 14, 23, 1, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.serverRack(-13, 1, 11, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        c.serverRack(13, 1, 11, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);

        // === 2 樓雙側整修廊道＋橫跨機庫的高空天橋 (y=6, 保持在運輸艇 y=5 之上不碰撞!) ===
        c.box(-17, 6, 3, -13, 6, 28, Blocks.DEEPSLATE_BRICKS);
        c.box(13, 6, 3, 17, 6, 28, Blocks.DEEPSLATE_BRICKS);
        c.box(-13, 6, 4, 13, 6, 6, Blocks.DEEPSLATE_TILES);
        c.box(-13, 6, 25, 13, 6, 27, Blocks.DEEPSLATE_TILES);
        // 雙層高空走廊護欄（鐵欄杆＋青色玻璃片自動相連）
        c.windowZ(-13, 7, 6, 25, 7, Blocks.IRON_BARS);
        c.windowZ(13, 7, 6, 25, 7, Blocks.IRON_BARS);
        c.windowX(-13, 7, 4, 13, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-13, 7, 6, 13, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-13, 7, 25, 13, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowX(-13, 7, 27, 13, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        // 兩翼管制檯
        c.counter(-16, 14, -15, 17, 7, Blocks.BLACK_CONCRETE);
        c.counter(15, 14, 16, 17, 7, Blocks.BLACK_CONCRETE);

        // 雙寬樓梯連接 1F -> 2F 整修廊道
        c.staircaseZ(-17, -16, 0, 6, 17, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(16, 17, 0, 6, 17, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);

        // 拱形機庫大屋頂 (y=12..18)
        c.box(-18, 12, 2, 18, 12, 29, Blocks.DEEPSLATE_BRICKS);
        c.box(-16, 12, 4, 16, 12, 27, Blocks.AIR); // 內部維持挑高拱頂
        c.gableRoofZ(-18, 12, 2, 18, 29, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE);
        c.ceilingLights(-15, 15, 11, 5, 26, 6, 6, Blocks.SEA_LANTERN);

        c.bunkerCoverL(-11, 1, 18, 2, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(11, 1, 18, -2, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 40 個物資箱點位
        c.loot(0, 2, 12);   c.loot(0, 2, 15);   c.loot(0, 2, 18);   c.loot(0, 2, 20);
        c.loot(-8, 3, 14);  c.loot(-8, 3, 17);  c.loot(8, 3, 14);   c.loot(8, 3, 17);
        c.loot(-12, 1, 5);  c.loot(12, 1, 5);   c.loot(-15, 1, 14); c.loot(15, 1, 14); c.loot(-12, 1, 25); c.loot(12, 1, 25);
        c.loot(-8, 1, 5);   c.loot(8, 1, 5);    c.loot(-8, 1, 25);  c.loot(8, 1, 25);  c.loot(-15, 1, 9);  c.loot(15, 1, 9);
        c.loot(-15, 1, 22); c.loot(15, 1, 22);  c.loot(-6, 1, 27);  c.loot(6, 1, 27);
        c.loot(-15, 7, 8);  c.loot(15, 7, 8);   c.loot(-15, 7, 25); c.loot(15, 7, 25);
        c.loot(-6, 7, 5);   c.loot(6, 7, 5);    c.loot(-6, 7, 26);  c.loot(6, 7, 26);
        c.loot(0, 7, 5);    c.loot(0, 7, 26);   c.loot(-15, 7, 12); c.loot(15, 7, 12); c.loot(-15, 7, 21); c.loot(15, 7, 21);
        c.loot(-15, 7, 5);  c.loot(15, 7, 5);
    }

    private static void reactorCitadel(BuildingCanvas c) {
        c.foundation(Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.clear(-14, 0, 0, 14, 25, 28);

        // 八角切角要塞塔 (x=-13..13, z=1..27；四層樓板 y=0, 6, 12, 18；頂端尖塔 y=19..25)
        c.floor(-14, 0, 0, 14, 28, Blocks.DEEPSLATE_TILES);
        for (int fl = 0; fl < 3; fl++) {
            int yBot = fl * 6 + 1;
            int yTop = fl * 6 + 5;
            c.walls(-13, yBot, 1, 13, yTop, 27, Blocks.DEEPSLATE_BRICKS);
            // 切出八角形斜角並做青色垂直導能槽
            for (int y = yBot; y <= yTop; y++) {
                c.set(-10, y, 1, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(10, y, 1, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(-10, y, 27, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(10, y, 27, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(-13, y, 4, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(-13, y, 24, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(13, y, 4, Blocks.CYAN_STAINED_GLASS_PANE);
                c.set(13, y, 24, Blocks.CYAN_STAINED_GLASS_PANE);
            }
            c.box(-12, yTop + 1, 2, 12, yTop + 1, 26, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        c.cornerPillars(-13, 1, 1, 13, 18, 27, Blocks.POLISHED_BLACKSTONE_BRICKS);

        // 四向十字型裝甲入口＋環繞景觀玻璃
        c.doorway(-2, 1, 1, 5);
        c.doorway(-2, 1, 27, 5);
        c.doorwaySide(-13, 1, 12, 5);
        c.doorwaySide(13, 1, 12, 5);
        for (int fy : new int[] { 8, 14 }) {
            c.windowX(-8, fy, 1, -4, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(4, fy, 1, 8, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(-8, fy, 27, -4, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(4, fy, 27, 8, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-13, fy, 7, 11, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(13, fy, 7, 11, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // 中央貫穿 1～3 樓脈衝反應爐核心柱 (x=-2..2, z=12..16)
        c.box(-4, 6, 10, 4, 17, 18, Blocks.AIR); // 2、3 樓環形中空圓井
        c.box(-5, 7, 9, 5, 7, 19, Blocks.IRON_BARS);
        c.box(-4, 7, 10, 4, 7, 18, Blocks.AIR);
        c.box(-5, 13, 9, 5, 13, 19, Blocks.IRON_BARS);
        c.box(-4, 13, 10, 4, 13, 18, Blocks.AIR);
        c.box(-2, 1, 12, 2, 18, 16, Blocks.CYAN_STAINED_GLASS);
        c.box(-1, 1, 13, 1, 18, 15, Blocks.PURPUR_BLOCK);
        c.pillar(0, 1, 25, 14, Blocks.SEA_LANTERN);

        // === 1 樓四象限防爆屏蔽掩體 ===
        c.tallCover(-8, 7, -5, 7, 1, Blocks.BLACK_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(5, 7, 8, 7, 1, Blocks.BLACK_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(-8, 21, -5, 21, 1, Blocks.BLACK_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(5, 21, 8, 21, 1, Blocks.BLACK_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.serverRack(-6, 1, 14, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.serverRack(6, 1, 14, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);

        // === 2 樓四象限冷卻組與玻璃觀測站 ===
        c.box(-9, 7, 6, -6, 9, 8, Blocks.OXIDIZED_COPPER);
        c.box(6, 7, 6, 9, 9, 8, Blocks.OXIDIZED_COPPER);
        c.box(-9, 7, 20, -6, 9, 22, Blocks.OXIDIZED_COPPER);
        c.box(6, 7, 20, 9, 9, 22, Blocks.OXIDIZED_COPPER);

        // === 3 樓高階指揮環艙 ===
        c.counter(-4, 6, 4, 6, 13, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.counter(-4, 22, 4, 22, 13, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.deskUnit(-7, 13, 14, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.EAST);
        c.deskUnit(7, 13, 14, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.WEST);

        // === 4 樓防禦天台與能量尖塔 (y=18..25) ===
        c.parapet(-12, 19, 2, 12, 26, Blocks.POLISHED_BLACKSTONE_BRICK_WALL, true);
        c.box(-2, 19, 12, 2, 20, 16, Blocks.DEEPSLATE_BRICKS);
        c.pillar(-2, 19, 23, 12, Blocks.IRON_BARS);
        c.pillar(2, 19, 23, 12, Blocks.IRON_BARS);
        c.pillar(-2, 19, 23, 16, Blocks.IRON_BARS);
        c.pillar(2, 19, 23, 16, Blocks.IRON_BARS);
        c.tallCover(-8, 7, -5, 7, 19, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICK_SLAB);
        c.tallCover(5, 7, 8, 7, 19, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICK_SLAB);

        // === 東西雙寬 2 格樓梯 + 南北電梯直井 ===
        c.staircaseZ(-11, -10, 0, 6, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(10, 11, 0, 6, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-11, -10, 6, 12, 20, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(10, 11, 12, 18, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.elevator(0, 24, 0, 6, 12, 18);
        c.bunkerCoverL(-4, 1, 6, 2, -2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(4, 1, 6, -2, -2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        c.ceilingLights(-10, 10, 5, 4, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-10, 10, 11, 4, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-10, 10, 17, 4, 24, 6, 6, Blocks.SEA_LANTERN);

        // 38 個物資箱點位
        c.loot(-6, 1, 5);  c.loot(6, 1, 5);   c.loot(-6, 1, 23); c.loot(6, 1, 23); c.loot(0, 1, 9);
        c.loot(0, 1, 19);  c.loot(-9, 1, 14); c.loot(9, 1, 14);  c.loot(-4, 1, 14); c.loot(4, 1, 14);
        c.loot(-7, 7, 4);  c.loot(7, 7, 4);   c.loot(-7, 7, 24); c.loot(7, 7, 24); c.loot(0, 7, 7);
        c.loot(0, 7, 21);  c.loot(-7, 7, 14); c.loot(7, 7, 14);  c.loot(-10, 7, 9); c.loot(10, 7, 9);
        c.loot(-6, 13, 8); c.loot(6, 13, 8);  c.loot(-6, 13, 20); c.loot(6, 13, 20);
        c.loot(0, 13, 8);  c.loot(0, 13, 20); c.loot(-9, 13, 14); c.loot(9, 13, 14);
        c.loot(-10, 13, 5); c.loot(10, 13, 5);
        c.loot(-7, 19, 14); c.loot(7, 19, 14); c.loot(0, 19, 9); c.loot(0, 19, 19); c.loot(-6, 19, 5); c.loot(6, 19, 23);
        c.loot(-6, 19, 23); c.loot(6, 19, 5);
    }

    private static void mechBay(BuildingCanvas c) {
        c.foundation(Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.clear(-22, 0, 0, 22, 25, 34);

        // 45x35 超挑高機甲整備格納庫 (主庫 -21..21, z=2..34, y=0..24)
        c.floor(-22, 0, 0, 22, 34, Blocks.DEEPSLATE_TILES);
        c.walls(-21, 1, 2, 21, 22, 34, Blocks.GRAY_CONCRETE);
        c.walls(-21, 1, 2, 21, 3, 34, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.clear(-20, 1, 3, 20, 22, 33);
        c.cornerPillars(-21, 1, 2, 21, 23, 34, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.box(-21, 23, 2, 21, 23, 34, Blocks.DEEPSLATE_BRICKS);
        c.box(-8, 23, 10, 8, 23, 24, Blocks.CYAN_STAINED_GLASS);
        c.parapet(-21, 24, 2, 21, 34, Blocks.POLISHED_BLACKSTONE_BRICK_WALL, true);

        // 超寬機庫巨門與側門
        for (int y = 1; y <= 8; y++) {
            for (int x = -5; x <= 5; x++) {
                c.air(x, y, 2);
                c.air(x, y, 34);
            }
        }
        c.doorwaySide(-21, 1, 16, 4);
        c.doorwaySide(21, 1, 16, 4);
        for (int fy : new int[] { 8, 15 }) {
            c.windowX(-18, fy, 2, -8, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(8, fy, 2, 18, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-21, fy, 6, 13, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(21, fy, 6, 13, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // === 正中央 20 格高實體雙足戰鬥機甲 (x=-7..7, z=13..21, y=1..20) ===
        // 雙腳與腿甲 (可從腿間底下穿行)
        c.box(-6, 1, 15, -2, 8, 19, Blocks.DEEPSLATE_BRICKS);
        c.box(2, 1, 15, 6, 8, 19, Blocks.DEEPSLATE_BRICKS);
        c.box(-5, 3, 15, -3, 6, 19, Blocks.ORANGE_CONCRETE);
        c.box(3, 3, 15, 5, 6, 19, Blocks.ORANGE_CONCRETE);
        // 骨盆與發光能源胸核 (y=9..16)
        c.box(-5, 9, 15, 5, 10, 19, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.box(-5, 11, 14, 5, 16, 20, Blocks.GRAY_CONCRETE);
        c.clear(-3, 12, 15, 3, 15, 19); // 機甲駕駛胸艙空間 (地板 y=11)
        c.box(-1, 13, 14, 1, 14, 14, Blocks.CYAN_STAINED_GLASS);
        c.set(0, 12, 16, Blocks.SEA_LANTERN);
        c.deskUnit(0, 12, 16, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        c.doorway(-1, 12, 20, 3);
        // 雙肩巨砲與機甲頭部天線 (y=17..21)
        c.box(-9, 13, 15, -6, 17, 19, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.box(6, 13, 15, 9, 17, 19, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.box(-8, 17, 11, -7, 18, 20, Blocks.IRON_BLOCK);
        c.box(7, 17, 11, 8, 18, 20, Blocks.IRON_BLOCK);
        c.box(-2, 17, 16, 2, 19, 18, Blocks.WHITE_CONCRETE);
        c.box(-1, 18, 15, 1, 18, 15, Blocks.SEA_LANTERN);
        c.pillar(0, 20, 22, 17, Blocks.IRON_BARS);

        // === 1 樓地面防爆掩體與雷達校準機房 ===
        c.bunkerCoverL(-14, 1, 8, 3, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(14, 1, 8, -3, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.serverRack(-17, 1, 26, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.serverRack(17, 1, 26, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.counter(-11, 28, -7, 28, 1, Blocks.DEEPSLATE_TILES);
        c.counter(7, 28, 11, 28, 1, Blocks.DEEPSLATE_TILES);

        // === 2F (y=6)、3F (y=12) 環繞整備鋼台＋直通機甲胸艙高空檢修橋 ===
        for (int fy : new int[] { 6, 12 }) {
            c.box(-20, fy, 4, -14, fy, 33, Blocks.DEEPSLATE_TILES);
            c.box(14, fy, 4, 20, fy, 33, Blocks.DEEPSLATE_TILES);
            c.box(-14, fy, 27, 14, fy, 33, Blocks.DEEPSLATE_TILES);
            c.windowZ(-14, fy + 1, 4, 27, fy + 1, Blocks.IRON_BARS);
            c.windowZ(14, fy + 1, 4, 27, fy + 1, Blocks.IRON_BARS);
            c.windowX(-14, fy + 1, 27, 14, fy + 1, Blocks.IRON_BARS);
        }
        // 3F 兩側橫跨接通機甲胸艙與肩甲的檢修橋 (y=12, z=16..18)
        c.box(-14, 12, 16, -6, 12, 18, Blocks.DEEPSLATE_TILES);
        c.box(6, 12, 16, 14, 12, 18, Blocks.DEEPSLATE_TILES);
        c.box(-4, 12, 20, 4, 12, 27, Blocks.DEEPSLATE_TILES);
        // 4F 高空屋頂檢修環廊 (y=18)＋直達屋頂出口
        c.box(-20, 18, 4, -14, 18, 33, Blocks.DEEPSLATE_BRICKS);
        c.box(14, 18, 4, 20, 18, 33, Blocks.DEEPSLATE_BRICKS);
        c.windowZ(-14, 19, 4, 33, 19, Blocks.IRON_BARS);
        c.windowZ(14, 19, 4, 33, 19, Blocks.IRON_BARS);

        // 雙側寬 2 格樓梯 (無電梯)：0->6->12->18->23
        c.staircaseZ(-20, -19, 0, 6, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(19, 20, 0, 6, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(-20, -19, 6, 12, 26, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(19, 20, 6, 12, 26, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(-20, -19, 12, 18, 11, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        c.staircaseZ(19, 20, 18, 23, 26, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);

        c.ceilingLights(-18, 18, 22, 5, 31, 8, 8, Blocks.SEA_LANTERN);

        // 50 個物資箱點位
        c.loot(0, 1, 10);  c.loot(0, 1, 24);   c.loot(-10, 1, 6);  c.loot(10, 1, 6);
        c.loot(-16, 1, 14); c.loot(16, 1, 14); c.loot(-16, 1, 24); c.loot(16, 1, 24);
        c.loot(-12, 1, 30); c.loot(12, 1, 30); c.loot(0, 1, 30);   c.loot(-4, 1, 17);  c.loot(4, 1, 17);
        c.loot(-17, 7, 8);  c.loot(17, 7, 8);   c.loot(-17, 7, 18); c.loot(17, 7, 18);
        c.loot(-17, 7, 28); c.loot(17, 7, 28); c.loot(-8, 7, 30);  c.loot(8, 7, 30);   c.loot(0, 7, 30);
        c.loot(-17, 13, 8); c.loot(17, 13, 8);  c.loot(-17, 13, 18); c.loot(17, 13, 18);
        c.loot(-17, 13, 28); c.loot(17, 13, 28); c.loot(-8, 13, 17); c.loot(8, 13, 17);
        c.loot(-2, 12, 17); c.loot(2, 12, 17);  c.loot(0, 13, 24);  c.loot(-10, 13, 30); c.loot(10, 13, 30);
        c.loot(-8, 19, 15); c.loot(8, 19, 15);  c.loot(-17, 19, 10); c.loot(17, 19, 10);
        c.loot(-17, 19, 25); c.loot(17, 19, 25); c.loot(-17, 19, 31); c.loot(17, 19, 31);
        c.loot(-15, 24, 10); c.loot(15, 24, 10); c.loot(-15, 24, 24); c.loot(15, 24, 24);
        c.loot(0, 24, 6);   c.loot(0, 24, 30);  c.loot(0, 1, 5);
    }
}
