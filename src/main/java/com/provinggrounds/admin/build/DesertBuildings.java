package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.DESERT;

/**
 * 檔案: DesertBuildings.java
 * 目的: 沙漠戰區與古遺跡風格 6 棟超大型建築（全數無木箱、無電梯、樓梯最後一階完整齊平、石牆／木欄／玻璃自動連接、物資點緊貼掩體）。
 */
final class DesertBuildings {

    private DesertBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("des_bazaar_citadel", DESERT, "沙漠泥磚市集城寨",
            "45×35 沙漠城鎮巷戰中心：彩色布棚市集主街、噴泉廣場與大量攤販掩體，四周 4 棟 2～3 層砂岩建築由空中木橋與連通平頂屋頂貫串（48 個物資點）",
            45, 35, 18, 48, DesertBuildings::bazaarCitadel));

        out.accept(new BuildingBlueprint("des_oasis_palace", DESERT, "荒漠黃金綠洲宮殿",
            "43×37 黃金圓頂宮殿：拱門柱廊大門、正中央露天綠洲水池庭園與棕櫚涼亭、後方 2 層黃金寶座大殿、雙翼衛兵房與觀景屋頂（48 個物資點）",
            43, 37, 22, 48, DesertBuildings::oasisPalace));

        out.accept(new BuildingBlueprint("des_pyramid_temple", DESERT, "沙漠巨型階梯金字塔",
            "43×43 超巨型階梯金字塔：外部四面石階直衝塔頂神龕、四方墓室入口直通內部 3 層立體回字石室迷宮、巨柱大廳與黃金法老祭壇（46 個物資點）",
            43, 43, 24, 46, DesertBuildings::pyramidTemple));

        out.accept(new BuildingBlueprint("des_caravanserai", DESERT, "絲路商隊堡壘客棧",
            "43×35 雙層四合院砂岩客棧要塞：厚重拱門入口、中庭馬廄棚與蓄水池掩體、四面 2 樓木欄拱廊客房與連通平頂狙擊步道（44 個物資點）",
            43, 35, 17, 44, DesertBuildings::caravanserai));

        out.accept(new BuildingBlueprint("des_canyon_outpost", DESERT, "荒漠峽谷裝甲前哨",
            "39×31 紅砂岩沙漠軍事哨站：沙包防線、2 台實體沙漠迷彩軍卡掩體、2 層裝甲兵舍與西北 3 層高架警戒塔（40 個物資點）",
            39, 31, 19, 40, DesertBuildings::canyonOutpost));

        out.accept(new BuildingBlueprint("des_sunken_ruins", DESERT, "古神殿柱廊遺址廣場",
            "41×35 沙漠古神殿廢墟戰場：雙巨石門柱、16 根高聳神殿石柱與傾倒石垣掩體、兩側 2 層祭司石室與後方階梯太陽神台（42 個物資點）",
            41, 35, 18, 42, DesertBuildings::sunkenRuins));
    }

    private static void bazaarCitadel(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-22, 0, 0, 22, 18, 34);

        c.floor(-22, 0, 0, 22, 34, Blocks.SMOOTH_SANDSTONE);
        c.box(-5, 0, 0, 5, 0, 34, Blocks.SANDSTONE);
        // 中央綠洲水池噴泉
        c.box(-2, 1, 15, 2, 1, 19, Blocks.SANDSTONE_WALL);
        c.box(-1, 0, 16, 1, 1, 18, Blocks.WATER);
        c.pillar(0, 1, 3, 17, Blocks.CHISELED_SANDSTONE);
        // 市集彩色遮陽布棚與石檯掩體
        for (int[] stall : new int[][] { { -4, 6 }, { 2, 6 }, { -4, 24 }, { 2, 24 } }) {
            c.tallCover(stall[0], stall[1], stall[0] + 2, stall[1], 1, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE_SLAB);
            c.box(stall[0], 4, stall[1] - 1, stall[0] + 2, 4, stall[1] + 1, Blocks.ORANGE_WOOL);
        }

        // 4 棟多層砂岩堡樓 (SW, NW, SE, NE)
        adobeBlock(c, -21, -7, 2, 15, true, false);
        adobeBlock(c, -21, -7, 19, 32, true, true);
        adobeBlock(c, 7, 21, 2, 15, false, false);
        adobeBlock(c, 7, 21, 19, 32, false, true);

        // 2 樓跨街空中木橋連結東西兩側 (z=11..13 & z=22..24, y=6)
        for (int bz : new int[] { 11, 22 }) {
            c.box(-7, 6, bz, 7, 6, bz + 2, Blocks.SPRUCE_PLANKS);
            c.windowX(-7, 7, bz, 7, 7, Blocks.OAK_FENCE);
            c.windowX(-7, 7, bz + 2, 7, 7, Blocks.OAK_FENCE);
        }
        // 南北同側二樓空橋 (z=15..19, x=-14..-12 & 12..14)
        c.box(-15, 6, 15, -12, 6, 19, Blocks.SMOOTH_SANDSTONE);
        c.box(12, 6, 15, 15, 6, 19, Blocks.SMOOTH_SANDSTONE);

        // 48 個物資箱點位 (每棟堡樓貼牆／掩體各 10 點 + 市集貼攤位與橋欄 8 點 = 48)
        for (int[] reg : new int[][] { { -14, 8 }, { -14, 25 }, { 14, 8 }, { 14, 25 } }) {
            int cx = reg[0], cz = reg[1];
            c.loot(cx - 5, 1, cz - 4); c.loot(cx + 5, 1, cz + 4); c.loot(cx - 1, 1, cz + 2);
            c.loot(cx - 5, 7, cz - 4); c.loot(cx + 5, 7, cz + 4); c.loot(cx, 7, cz - 4);
            c.loot(cx - 4, 13, cz);    c.loot(cx + 4, 13, cz);
            c.loot(cx, 13, cz - 5);    c.loot(cx, 13, cz + 5);
        }
        c.loot(-3, 1, 7);  c.loot(3, 1, 25); c.loot(-3, 1, 17); c.loot(3, 1, 17);
        c.loot(0, 7, 12);  c.loot(0, 7, 23); c.loot(-14, 7, 17); c.loot(14, 7, 17);
    }

    private static void adobeBlock(BuildingCanvas c, int x0, int x1, int z0, int z1, boolean westSide, boolean rear) {
        c.box(x0, 0, z0, x1, 0, z1, Blocks.RED_SANDSTONE);
        c.walls(x0, 1, z0, x1, 5, z1, Blocks.SMOOTH_SANDSTONE);
        c.box(x0, 6, z0, x1, 6, z1, Blocks.SMOOTH_SANDSTONE);
        c.walls(x0, 7, z0, x1, 11, z1, Blocks.SANDSTONE);
        c.box(x0, 12, z0, x1, 12, z1, Blocks.SMOOTH_SANDSTONE);
        c.parapet(x0, 13, z0, x1, z1, Blocks.SANDSTONE_WALL, true);

        int streetX = westSide ? x1 : x0;
        int midZ = (z0 + z1) / 2;
        int midX = (x0 + x1) / 2;
        c.doorwaySide(streetX, 1, midZ - 1, 3);
        c.doorwaySide(streetX, 7, z0 + 9 - (rear ? 5 : 0), 2);
        c.doorway(midX - 1, 1, rear ? z1 : z0, 3);
        c.windowZ(streetX, 2, z0 + 2, z0 + 4, 3, Blocks.OAK_FENCE);
        c.windowZ(streetX, 8, z0 + 2, z0 + 4, 3 + 6, Blocks.OAK_FENCE);
        c.bunkerCoverL(midX, 1, midZ + 2, westSide ? -2 : 2, -2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.counter(midX - 2, z0 + 3, midX + 2, z0 + 3, 7, Blocks.TERRACOTTA);
        c.tallCover(midX - 3, midZ, midX + 3, midZ, 13, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);

        int sx = westSide ? x0 + 1 : x1 - 2;
        c.staircaseZ(sx, sx + 1, 0, 6, z0 + 3, 1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(sx + 3 * (westSide ? 1 : -1), sx + 3 * (westSide ? 1 : -1) + 1, 6, 12, z1 - 3, -1,
            Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.ceilingLights(x0 + 3, x1 - 3, 5, z0 + 3, z1 - 3, 6, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(x0 + 3, x1 - 3, 11, z0 + 3, z1 - 3, 6, 6, Blocks.OCHRE_FROGLIGHT);
    }

    private static void oasisPalace(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-21, 0, 0, 21, 22, 36);

        c.floor(-21, 0, 0, 21, 36, Blocks.SMOOTH_SANDSTONE);
        c.walls(-20, 1, 2, 20, 11, 35, Blocks.SMOOTH_SANDSTONE);
        c.clear(-19, 1, 3, 19, 11, 34);
        c.box(-20, 6, 2, 20, 6, 35, Blocks.RED_SANDSTONE);
        c.box(-20, 12, 2, 20, 12, 35, Blocks.SMOOTH_SANDSTONE);
        c.parapet(-20, 13, 2, 20, 35, Blocks.SANDSTONE_WALL, true);
        c.cornerPillars(-20, 1, 2, 20, 13, 35, Blocks.GOLD_BLOCK);

        // 中央露天綠洲庭園與涼亭 (x=-8..8, z=8..22)
        c.box(-8, 6, 8, 8, 12, 22, Blocks.AIR);
        c.box(-9, 7, 7, 9, 7, 23, Blocks.SANDSTONE_WALL);
        c.box(-8, 7, 8, 8, 7, 22, Blocks.AIR);
        c.box(-6, 0, 10, 6, 0, 20, Blocks.WATER);
        c.box(-2, 1, 13, 2, 1, 17, Blocks.SMOOTH_SANDSTONE);
        c.pillar(-2, 2, 5, 13, Blocks.QUARTZ_PILLAR); c.pillar(2, 2, 5, 13, Blocks.QUARTZ_PILLAR);
        c.pillar(-2, 2, 5, 17, Blocks.QUARTZ_PILLAR); c.pillar(2, 2, 5, 17, Blocks.QUARTZ_PILLAR);
        c.box(-3, 5, 12, 3, 5, 18, Blocks.GOLD_BLOCK);
        // 四角棕櫚樹
        for (int[] p : new int[][] { { -7, 9 }, { 7, 9 }, { -7, 21 }, { 7, 21 } }) {
            c.pillar(p[0], 1, 5, p[1], Blocks.JUNGLE_LOG);
            c.box(p[0] - 1, 5, p[1] - 1, p[0] + 1, 6, p[1] + 1, Blocks.JUNGLE_LEAVES);
        }

        // 四方大門與花窗
        c.doorway(-2, 1, 2, 5);
        c.doorway(-2, 1, 35, 5);
        c.doorwaySide(-20, 1, 16, 4);
        c.doorwaySide(20, 1, 16, 4);
        for (int fy : new int[] { 2, 8 }) {
            c.windowX(-17, fy, 2, -6, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowX(6, fy, 2, 17, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
        }

        // 後方黃金寶座大殿 (z=24..34)＋左右衛兵與書房掩體
        c.box(-4, 1, 30, 4, 2, 33, Blocks.GOLD_BLOCK);
        c.tallCover(-16, 12, -11, 12, 1, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(11, 12, 16, 12, 1, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.box(-17, 1, 26, -12, 3, 26, Blocks.BOOKSHELF);
        c.bedUnit(14, 26, 17, 27, 1, Blocks.CYAN_WOOL, Blocks.WHITE_WOOL);

        // 屋頂觀景金頂亭 (y=13..21)
        c.box(-6, 13, 26, 6, 13, 33, Blocks.GOLD_BLOCK);
        c.walls(-5, 14, 27, 5, 17, 32, Blocks.SMOOTH_SANDSTONE);
        c.box(-5, 18, 27, 5, 20, 32, Blocks.GOLD_BLOCK);
        c.doorway(-1, 14, 27, 3);
        c.doorway(-1, 14, 32, 3);

        // 雙寬樓梯 0->6->12 (無電梯)
        c.staircaseZ(-18, -17, 0, 6, 15, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(17, 18, 0, 6, 15, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(-18, -17, 6, 12, 28, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(17, 18, 6, 12, 28, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);

        c.ceilingLights(-16, 16, 5, 4, 33, 7, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-16, 16, 11, 4, 33, 7, 7, Blocks.SEA_LANTERN);

        // 48 個物資箱點位（貼近棕櫚／矮牆／書架／護欄／金亭）
        c.loot(-12, 1, 4);  c.loot(12, 1, 4);   c.loot(-14, 1, 13); c.loot(14, 1, 13);
        c.loot(-14, 1, 22); c.loot(14, 1, 22);  c.loot(-12, 1, 27); c.loot(12, 1, 27);
        c.loot(-5, 1, 31);  c.loot(5, 1, 31);   c.loot(0, 2, 15);   c.loot(0, 1, 29);
        c.loot(-9, 1, 15);  c.loot(9, 1, 15);   c.loot(-5, 1, 4);   c.loot(5, 1, 4);
        c.loot(-13, 7, 5);  c.loot(13, 7, 5);   c.loot(-13, 7, 16); c.loot(13, 7, 16);
        c.loot(-13, 7, 26); c.loot(13, 7, 26);  c.loot(-6, 7, 28);  c.loot(6, 7, 28);
        c.loot(0, 7, 5);    c.loot(0, 7, 33);   c.loot(-10, 7, 10); c.loot(10, 7, 10);
        c.loot(-10, 7, 21); c.loot(10, 7, 21);
        c.loot(-16, 13, 5); c.loot(16, 13, 5);  c.loot(-16, 13, 15); c.loot(16, 13, 15);
        c.loot(-16, 13, 25); c.loot(16, 13, 25); c.loot(-10, 13, 12); c.loot(10, 13, 12);
        c.loot(-10, 13, 22); c.loot(10, 13, 22); c.loot(0, 13, 4);   c.loot(0, 13, 25);
        c.loot(0, 14, 29);  c.loot(-3, 14, 30); c.loot(3, 14, 30);  c.loot(-12, 13, 33); c.loot(12, 13, 33); c.loot(0, 1, 34);
    }

    private static void pyramidTemple(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-21, 0, 0, 21, 24, 42);

        // 43x43 階梯金字塔外殼 (中心 cz = 21；逐層內縮共 20 層至 y=20，頂端平頂神龕)
        c.floor(-21, 0, 0, 21, 42, Blocks.SMOOTH_SANDSTONE);
        for (int step = 1; step <= 20; step++) {
            int y = step;
            int r = 21 - step;
            c.walls(-r, y, 21 - r, r, y, 21 + r, (step % 4 == 0) ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE);
        }
        c.box(-4, 20, 17, 4, 20, 25, Blocks.GOLD_BLOCK);
        c.parapet(-4, 21, 17, 4, 25, Blocks.SANDSTONE_WALL, true);
        c.pillar(0, 21, 23, 21, Blocks.SEA_LANTERN);

        // 外部四向石階上塔頂 (確保寬 3 格一路爬到 y=20)
        for (int s = 1; s <= 17; s++) {
            int r = 21 - s;
            for (int w = -1; w <= 1; w++) {
                c.stair(w, s, 21 - r, Blocks.SANDSTONE_STAIRS, Direction.NORTH);
                c.stair(w, s, 21 + r, Blocks.SANDSTONE_STAIRS, Direction.SOUTH);
                c.stair(-r, s, 21 + w, Blocks.SANDSTONE_STAIRS, Direction.EAST);
                c.stair(r, s, 21 + w, Blocks.SANDSTONE_STAIRS, Direction.WEST);
            }
        }

        // 內部 3 層立體迷宮石室 (1F y=0:-16..16, 2F y=6:-11..11, 3F y=12:-6..6)
        c.box(-12, 6, 9, 12, 6, 33, Blocks.SMOOTH_SANDSTONE);
        c.box(-7, 12, 14, 7, 12, 28, Blocks.SMOOTH_SANDSTONE);
        // 1F 四方入口隧道
        for (int z = 0; z <= 8; z++) { c.doorway(-1, 1, z, 3); c.doorway(-1, 1, 42 - z, 3); }
        for (int x = -21; x <= -13; x++) c.doorwaySide(x, 1, 20, 3);
        for (int x = 13; x <= 21; x++) c.doorwaySide(x, 1, 20, 3);

        // 內部巨柱陣＋黃金祭壇＋防爆砂岩矮牆掩體
        c.columnGrid(-10, 10, 1, 5, 11, 31, 5, 5, Blocks.CHISELED_SANDSTONE);
        c.box(-2, 1, 19, 2, 3, 23, Blocks.GOLD_BLOCK);
        c.bunkerCoverL(-7, 1, 14, 2, 2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(7, 1, 14, -2, 2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(-7, 1, 28, 2, -2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(7, 1, 28, -2, -2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(-4, 7, 4, 7, 7, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.tallCover(-4, 24, 4, 24, 7, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);

        // 內部樓梯 0->6->12＋3 樓通塔頂缺口階梯 12->20
        c.staircaseZ(-11, -10, 0, 6, 15, 1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(10, 11, 0, 6, 15, 1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(-6, -5, 6, 12, 22, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(-1, 0, 12, 20, 19, 1, Blocks.SANDSTONE_STAIRS, Blocks.GOLD_BLOCK, Blocks.SANDSTONE);

        c.ceilingLights(-12, 12, 5, 9, 33, 6, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-8, 8, 11, 13, 29, 5, 5, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-4, 4, 16, 17, 25, 4, 4, Blocks.SEA_LANTERN);

        // 46 個物資箱點位（貼近石柱／祭壇／矮牆／角落）
        c.loot(-11, 1, 10); c.loot(11, 1, 10); c.loot(-11, 1, 32); c.loot(11, 1, 32);
        c.loot(-14, 1, 17); c.loot(14, 1, 17); c.loot(-14, 1, 25); c.loot(14, 1, 25);
        c.loot(-6, 1, 13);  c.loot(6, 1, 13);  c.loot(-6, 1, 29);  c.loot(6, 1, 29);
        c.loot(-4, 1, 17);  c.loot(4, 1, 17);  c.loot(-4, 1, 25);  c.loot(4, 1, 25);
        c.loot(0, 1, 12);   c.loot(0, 1, 30);
        c.loot(-9, 7, 12);  c.loot(9, 7, 12);  c.loot(-9, 7, 30);  c.loot(9, 7, 30);
        c.loot(-6, 7, 21);  c.loot(6, 7, 21);  c.loot(0, 7, 13);   c.loot(0, 7, 29);
        c.loot(-9, 7, 21);  c.loot(9, 7, 21);  c.loot(-3, 7, 17);  c.loot(3, 7, 17);
        c.loot(-5, 13, 16); c.loot(5, 13, 16); c.loot(-5, 13, 26); c.loot(5, 13, 26);
        c.loot(0, 13, 16);  c.loot(-3, 13, 21); c.loot(3, 13, 21);
        c.loot(-3, 21, 18); c.loot(3, 21, 18); c.loot(-3, 21, 24); c.loot(3, 21, 24);
        c.loot(-2, 1, 5);   c.loot(2, 1, 37);  c.loot(-15, 1, 22); c.loot(15, 1, 22); c.loot(0, 13, 25);
    }

    private static void caravanserai(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-21, 0, 0, 21, 17, 34);

        c.floor(-21, 0, 0, 21, 34, Blocks.SMOOTH_SANDSTONE);
        // 回字形 2 層商隊客棧 (外圈 x=-20..20, z=2..33, 內院留空 x=-11..11, z=10..25)
        c.walls(-20, 1, 2, 20, 11, 33, Blocks.SANDSTONE);
        c.clear(-19, 1, 3, 19, 11, 32);
        c.box(-20, 6, 2, 20, 6, 33, Blocks.SPRUCE_PLANKS);
        c.box(-20, 12, 2, 20, 12, 33, Blocks.SMOOTH_SANDSTONE);
        c.clear(-11, 6, 10, 11, 12, 25); // 中庭露空

        c.parapet(-20, 13, 2, 20, 33, Blocks.SANDSTONE_WALL, true);
        c.windowX(-11, 7, 10, 11, 7, Blocks.OAK_FENCE);
        c.windowX(-11, 7, 25, 11, 7, Blocks.OAK_FENCE);
        c.windowZ(-11, 7, 10, 25, 7, Blocks.OAK_FENCE);
        c.windowZ(11, 7, 10, 25, 7, Blocks.OAK_FENCE);

        // 內院牆線＋四向大門
        c.walls(-12, 1, 9, 12, 5, 26, Blocks.SMOOTH_SANDSTONE);
        c.clear(-11, 1, 10, 11, 5, 25);
        c.doorway(-2, 1, 2, 5);
        c.doorway(-2, 1, 9, 5);
        c.doorway(-2, 1, 26, 5);
        c.doorway(-2, 1, 33, 5);
        c.doorwaySide(-20, 1, 16, 3);
        c.doorwaySide(-12, 1, 16, 3);
        c.doorwaySide(12, 1, 16, 3);
        c.doorwaySide(20, 1, 16, 3);

        // 中庭蓄水池＋東西馬廄棚掩體
        c.box(-2, 1, 15, 2, 1, 19, Blocks.SANDSTONE_WALL);
        c.box(-1, 0, 16, 1, 1, 18, Blocks.WATER);
        c.tallCover(-9, 14, -7, 14, 1, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.tallCover(7, 14, 9, 14, 1, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.counter(-8, 21, -5, 21, 1, Blocks.DARK_OAK_PLANKS);
        c.counter(5, 21, 8, 21, 1, Blocks.DARK_OAK_PLANKS);

        // 雙翼客房隔間與屋頂掩體
        for (int fy : new int[] { 1, 7 }) {
            for (int z : new int[] { 12, 22 }) {
                c.box(-19, fy, z, -14, fy + 4, z, Blocks.SMOOTH_SANDSTONE);
                c.doorway(-16, fy, z, 2);
                c.box(14, fy, z, 19, fy + 4, z, Blocks.SMOOTH_SANDSTONE);
                c.doorway(15, fy, z, 2);
            }
            c.bedUnit(-19, 15, -17, 16, fy, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
            c.bedUnit(17, 15, 19, 16, fy, Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL);
        }
        c.tallCover(-16, 5, -10, 5, 13, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(10, 5, 16, 5, 13, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(-5, 29, 5, 29, 13, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);

        c.staircaseZ(-18, -17, 0, 6, 17, 1, Blocks.SANDSTONE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SANDSTONE);
        c.staircaseZ(17, 18, 0, 6, 17, 1, Blocks.SANDSTONE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SANDSTONE);
        c.staircaseZ(-18, -17, 6, 12, 26, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(17, 18, 6, 12, 26, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        c.ceilingLights(-16, 16, 5, 4, 31, 8, 7, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-16, 16, 11, 4, 31, 8, 7, Blocks.OCHRE_FROGLIGHT);

        // 44 個物資箱點位（貼近水池／廄棚／客房隔牆／欄杆／垛口）
        c.loot(-3, 1, 15);  c.loot(3, 1, 15);   c.loot(-8, 1, 15);  c.loot(8, 1, 15);
        c.loot(-7, 1, 20);  c.loot(7, 1, 20);   c.loot(-14, 1, 5);  c.loot(14, 1, 5);
        c.loot(-14, 1, 13); c.loot(14, 1, 13);  c.loot(-14, 1, 23); c.loot(14, 1, 23);
        c.loot(-14, 1, 30); c.loot(14, 1, 30);  c.loot(-5, 1, 31);  c.loot(5, 1, 31);
        c.loot(-14, 7, 5);  c.loot(14, 7, 5);   c.loot(-14, 7, 13); c.loot(14, 7, 13);
        c.loot(-14, 7, 23); c.loot(14, 7, 23);  c.loot(-14, 7, 30); c.loot(14, 7, 30);
        c.loot(-5, 7, 8);   c.loot(5, 7, 8);    c.loot(-5, 7, 27);  c.loot(5, 7, 27);
        c.loot(0, 7, 8);    c.loot(0, 7, 27);
        c.loot(-14, 13, 6); c.loot(14, 13, 6);  c.loot(-18, 13, 15); c.loot(18, 13, 15);
        c.loot(-18, 13, 25); c.loot(18, 13, 25); c.loot(-14, 13, 30); c.loot(14, 13, 30);
        c.loot(-4, 13, 3);  c.loot(4, 13, 3);   c.loot(-4, 13, 30); c.loot(4, 13, 30);
        c.loot(0, 1, 7);    c.loot(0, 1, 28);
    }

    private static void canyonOutpost(BuildingCanvas c) {
        c.foundation(Blocks.RED_SANDSTONE);
        c.clear(-19, 0, 0, 19, 19, 30);

        c.floor(-19, 0, 0, 19, 30, Blocks.SMOOTH_SANDSTONE);
        c.walls(-18, 1, 2, 18, 4, 29, Blocks.RED_SANDSTONE);
        c.clear(-17, 1, 3, 17, 4, 28);
        c.parapet(-18, 5, 2, 18, 29, Blocks.SANDSTONE_WALL, true);
        c.doorway(-2, 1, 2, 5);
        c.doorway(-2, 1, 29, 5);
        c.doorwaySide(-18, 1, 14, 3);
        c.doorwaySide(18, 1, 14, 3);

        // 前院沙包掩體＋2 台實體沙漠迷彩軍卡
        c.bunkerCoverL(-6, 1, 6, 2, 2, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.bunkerCoverL(6, 1, 6, -2, 2, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);
        c.parkedCar(-11, 1, 10, Blocks.TERRACOTTA, true);
        c.parkedCar(11, 1, 10, Blocks.YELLOW_TERRACOTTA, true);

        // 2 層裝甲營房大樓 (-16..6, z=18..28, y=0, 6, 12)＋東側 3 層高架警戒塔 (9..17, z=18..28, y=0..19)
        c.box(-16, 0, 18, 6, 0, 28, Blocks.DEEPSLATE_TILES);
        c.walls(-16, 1, 18, 6, 11, 28, Blocks.SMOOTH_SANDSTONE);
        c.clear(-15, 1, 19, 5, 11, 27);
        c.box(-16, 6, 18, 6, 6, 28, Blocks.RED_SANDSTONE);
        c.box(-16, 12, 18, 6, 12, 28, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-16, 13, 18, 6, 28, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-7, 1, 18, 3);
        c.doorway(-7, 1, 28, 3);
        c.windowX(-14, 8, 18, -9, 10, Blocks.IRON_BARS);
        c.windowX(-3, 8, 18, 4, 10, Blocks.IRON_BARS);
        c.bedUnit(-15, 20, -13, 21, 1, Blocks.BROWN_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(-15, 24, -13, 25, 1, Blocks.BROWN_WOOL, Blocks.WHITE_WOOL);
        c.counter(-5, 22, 2, 22, 1, Blocks.POLISHED_DEEPSLATE);
        c.deskUnit(-10, 7, 21, Blocks.DARK_OAK_PLANKS, Blocks.OAK_STAIRS, Direction.NORTH);
        c.serverRack(3, 7, 25, 3, Blocks.BLAST_FURNACE, Blocks.SEA_LANTERN);

        c.walls(9, 1, 18, 17, 16, 28, Blocks.DEEPSLATE_BRICKS);
        c.clear(10, 1, 19, 16, 16, 27);
        c.box(9, 6, 18, 17, 6, 28, Blocks.SMOOTH_STONE);
        c.box(9, 12, 18, 17, 12, 28, Blocks.SMOOTH_STONE);
        c.box(9, 17, 18, 17, 17, 28, Blocks.DEEPSLATE_TILES);
        c.parapet(9, 18, 18, 17, 28, Blocks.STONE_BRICK_WALL, true);
        c.doorwaySide(9, 1, 22, 3);

        c.staircaseZ(-15, -14, 0, 6, 20, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);
        c.staircaseZ(4, 5, 6, 12, 25, -1, Blocks.SANDSTONE_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.SANDSTONE);
        c.staircaseZ(11, 12, 0, 6, 20, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(14, 15, 6, 12, 26, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(11, 12, 12, 17, 20, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);

        c.ceilingLights(-12, 4, 5, 20, 26, 6, 4, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-12, 4, 11, 20, 26, 6, 4, Blocks.OCHRE_FROGLIGHT);

        // 40 個物資箱點位（貼近軍卡／沙包／外牆／營房床鋪與機櫃／塔樓）
        c.loot(-5, 1, 7);   c.loot(5, 1, 7);    c.loot(-13, 1, 10); c.loot(13, 1, 10);
        c.loot(-9, 1, 10);  c.loot(9, 1, 10);   c.loot(-16, 1, 4);  c.loot(16, 1, 4);
        c.loot(-16, 1, 15); c.loot(16, 1, 15);  c.loot(-12, 1, 20); c.loot(-12, 1, 25);
        c.loot(-4, 1, 23);  c.loot(2, 1, 23);   c.loot(10, 1, 20);  c.loot(15, 1, 25);
        c.loot(-10, 7, 20); c.loot(2, 7, 24);   c.loot(-5, 7, 26);  c.loot(-14, 7, 26);
        c.loot(0, 7, 20);   c.loot(10, 7, 20);  c.loot(15, 7, 20);  c.loot(12, 7, 26);
        c.loot(-14, 13, 20); c.loot(4, 13, 20); c.loot(-14, 13, 26); c.loot(4, 13, 26);
        c.loot(-5, 13, 20); c.loot(-5, 13, 26); c.loot(11, 13, 25); c.loot(15, 13, 20);
        c.loot(10, 18, 20); c.loot(16, 18, 20); c.loot(10, 18, 26); c.loot(16, 18, 26);
        c.loot(13, 18, 24); c.loot(-11, 1, 14); c.loot(11, 1, 14);  c.loot(0, 1, 16);
    }

    private static void sunkenRuins(BuildingCanvas c) {
        c.foundation(Blocks.SANDSTONE);
        c.clear(-20, 0, 0, 20, 18, 34);

        c.floor(-20, 0, 0, 20, 34, Blocks.SMOOTH_SANDSTONE);
        c.parapet(-20, 1, 0, 20, 34, Blocks.SANDSTONE_WALL, true);
        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 34, 5);
        c.doorwaySide(-20, 1, 16, 4);
        c.doorwaySide(20, 1, 16, 4);

        // 正面雙獅石門柱＋中央 16 根神殿巨柱廣場＋倒塌石牆掩體
        c.box(-6, 1, 2, -4, 6, 4, Blocks.CHISELED_SANDSTONE);
        c.box(4, 1, 2, 6, 6, 4, Blocks.CHISELED_SANDSTONE);
        c.columnGrid(-9, 9, 1, 8, 8, 24, 6, 5, Blocks.CHISELED_SANDSTONE);
        c.bunkerCoverL(-6, 1, 11, 2, 2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(6, 1, 11, -2, 2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(-6, 1, 21, 2, -2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(6, 1, 21, -2, -2, Blocks.RED_SANDSTONE, Blocks.SANDSTONE_SLAB);

        // 兩側 2 層祭司石室 (x=-19..-13 & x=13..19, z=6..26, y=0, 6, 12)
        for (int[] side : new int[][] { { -19, -13 }, { 13, 19 } }) {
            int x0 = side[0], x1 = side[1];
            c.walls(x0, 1, 6, x1, 11, 26, Blocks.SANDSTONE);
            c.clear(x0 + 1, 1, 7, x1 - 1, 11, 25);
            c.box(x0, 6, 6, x1, 6, 26, Blocks.RED_SANDSTONE);
            c.box(x0, 12, 6, x1, 12, 26, Blocks.SMOOTH_SANDSTONE);
            c.parapet(x0, 13, 6, x1, 26, Blocks.SANDSTONE_WALL, true);
            int innerX = x0 < 0 ? x1 : x0;
            c.doorwaySide(innerX, 1, 11, 2);
            c.doorwaySide(innerX, 1, 20, 2);
            c.windowZ(innerX, 8, 10, 22, 9, Blocks.SANDSTONE_WALL);
            int stX = x0 < 0 ? x0 + 1 : x1 - 2;
            c.staircaseZ(stX, stX + 1, 0, 6, 14, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);
            c.staircaseZ(stX, stX + 1, 6, 12, 22, -1, Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
            c.ceilingLights(x0 + 2, x1 - 2, 5, 8, 24, 3, 6, Blocks.OCHRE_FROGLIGHT);
            c.ceilingLights(x0 + 2, x1 - 2, 11, 8, 24, 3, 6, Blocks.OCHRE_FROGLIGHT);
        }

        // 後方階梯太陽神台 (z=27..32)
        c.box(-10, 1, 27, 10, 3, 32, Blocks.RED_SANDSTONE);
        c.staircaseZ(-2, 2, 0, 3, 25, 1, Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE, Blocks.SANDSTONE);
        c.pillar(0, 4, 7, 30, Blocks.GOLD_BLOCK);
        c.set(0, 8, 30, Blocks.SEA_LANTERN);

        // 42 個物資箱點位（全部貼近巨柱／倒塌石垣／石室壁／太陽神台）
        c.loot(-5, 1, 5);   c.loot(5, 1, 5);    c.loot(-8, 1, 8);   c.loot(8, 1, 8);
        c.loot(-2, 1, 8);   c.loot(2, 1, 8);    c.loot(-8, 1, 18);  c.loot(8, 1, 18);
        c.loot(-2, 1, 18);  c.loot(2, 1, 18);   c.loot(-8, 1, 24);  c.loot(8, 1, 24);
        c.loot(-5, 1, 12);  c.loot(5, 1, 12);   c.loot(-5, 1, 20);  c.loot(5, 1, 20);
        c.loot(-6, 4, 29);  c.loot(6, 4, 29);   c.loot(0, 4, 29);
        c.loot(-15, 1, 8);  c.loot(15, 1, 8);   c.loot(-15, 1, 24); c.loot(15, 1, 24);
        c.loot(-15, 7, 9);  c.loot(15, 7, 9);   c.loot(-15, 7, 16); c.loot(15, 7, 16);
        c.loot(-15, 7, 24); c.loot(15, 7, 24);
        c.loot(-15, 13, 8); c.loot(15, 13, 8);  c.loot(-15, 13, 16); c.loot(15, 13, 16);
        c.loot(-15, 13, 24); c.loot(15, 13, 24); c.loot(-18, 13, 12); c.loot(18, 13, 12);
        c.loot(-18, 13, 20); c.loot(18, 13, 20);
        c.loot(-18, 1, 4);  c.loot(18, 1, 4);   c.loot(0, 1, 26);
    }
}
