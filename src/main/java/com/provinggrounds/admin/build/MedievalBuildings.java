package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.MEDIEVAL;

/**
 * 檔案: MedievalBuildings.java
 * 目的: 歐式古典與中世紀城堡風格 6 棟超大型建築（全數無木箱、無電梯、樓梯最後一階完整齊平、石牆／彩繪玻璃／鐵欄自動連接、物資點緊貼掩體）。
 */
final class MedievalBuildings {

    private MedievalBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("med_gothic_cathedral", MEDIEVAL, "哥德式雙塔大教堂",
            "41×37 高聳教堂：正面雙鐘樓可登頂架槍、彩繪玫瑰窗、柱廊中殿與 8 排橡木長椅掩體、高台祭壇與 2 樓環形管風琴迴廊（46 個物資點）",
            41, 37, 29, 46, MedievalBuildings::gothicCathedral));

        out.accept(new BuildingBlueprint("med_crusader_castle", MEDIEVAL, "十字軍王者石砌城堡",
            "45×39 完整防衛要塞：吊閘城門與連通環道城牆、四角 3 層箭塔、水井鐵匠中庭、後方 3 層國王主堡（騎士長桌宴會廳與寶座議事廳）（52 個物資點）",
            45, 39, 25, 52, MedievalBuildings::crusaderCastle));

        out.accept(new BuildingBlueprint("med_grand_theater", MEDIEVAL, "巴洛克皇家大劇院",
            "43×35 古典皇家劇院：柱廊大廳、多排階梯紅色座席、挑高華麗大舞台與後台兩層化妝間、2／3 樓馬蹄形皇家包廂迴廊俯瞰全場（46 個物資點）",
            43, 35, 22, 46, MedievalBuildings::grandTheater));

        out.accept(new BuildingBlueprint("med_fortified_abbey", MEDIEVAL, "山丘要塞修道院",
            "41×33 圍合式石拱修道院：拱廊藥草噴泉中庭、北翼挑高禮拜堂、東翼 2 層抄經藏書室、西翼食堂與東北 3 層鐘塔（42 個物資點）",
            41, 33, 21, 42, MedievalBuildings::fortifiedAbbey));

        out.accept(new BuildingBlueprint("med_harbor_keep", MEDIEVAL, "港灣海防星形堡壘",
            "41×35 石砌星形海防要塞：厚實外城垣＋雙層砲口迴廊含石製艦砲掩體，中央 3 層司令主堡與全圈城垛步道（44 個物資點）",
            41, 35, 20, 44, MedievalBuildings::harborKeep));

        out.accept(new BuildingBlueprint("med_market_hall", MEDIEVAL, "中世紀公會交易大廳",
            "39×31 半木石砌大型市集公會廳：1 樓雙排商會攤位與拍賣高台，2 樓木欄環繞公會辦公室與陳列廊，雙寬樓梯通屋頂露台（42 個物資點）",
            39, 31, 19, 42, MedievalBuildings::marketHall));
    }

    private static void gothicCathedral(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-20, 0, 0, 20, 29, 36);

        c.floor(-20, 0, 0, 20, 36, Blocks.STONE_BRICKS);
        // 主教堂體 (x=-18..18, z=3..35, y=1..14, 人字大屋頂 y=15..28)
        c.walls(-18, 1, 8, 18, 14, 35, Blocks.STONE_BRICKS);
        c.clear(-17, 1, 9, 17, 14, 34);
        c.box(-18, 15, 8, 18, 15, 35, Blocks.DEEPSLATE_TILES);
        c.box(-16, 15, 10, 16, 15, 33, Blocks.AIR); // 挑高聖堂穹頂
        c.gableRoofZ(-18, 15, 8, 18, 35, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.STONE_BRICKS);

        // 正面雙高聳鐘樓 (x=-17..-10 & 10..17, z=2..9, y=1..27)＋中間玫瑰彩繪大窗
        for (int tx : new int[] { -17, 10 }) {
            c.walls(tx, 1, 2, tx + 7, 25, 9, Blocks.STONE_BRICKS);
            c.clear(tx + 1, 1, 3, tx + 6, 25, 8);
            c.box(tx, 6, 2, tx + 7, 6, 9, Blocks.SMOOTH_STONE);
            c.box(tx, 12, 2, tx + 7, 12, 9, Blocks.SMOOTH_STONE);
            c.box(tx, 18, 2, tx + 7, 18, 9, Blocks.SMOOTH_STONE);
            c.box(tx, 25, 2, tx + 7, 25, 9, Blocks.DEEPSLATE_BRICKS);
            c.parapet(tx, 26, 2, tx + 7, 9, Blocks.STONE_BRICK_WALL, true);
            c.doorway(tx + 2, 1, 2, 3);
            c.doorway(tx + 2, 1, 9, 3);
            c.doorway(tx + 2, 7, 9, 3);
            c.windowX(tx + 2, 14, 2, tx + 5, 16, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowX(tx + 2, 20, 2, tx + 5, 23, Blocks.IRON_BARS);
        }
        // 中央大門牆＋彩繪玫瑰窗
        c.walls(-9, 1, 8, 9, 14, 8, Blocks.STONE_BRICKS);
        c.doorway(-2, 1, 8, 5);
        c.doorway(-2, 1, 35, 5);
        c.doorwaySide(-18, 1, 20, 4);
        c.doorwaySide(18, 1, 20, 4);
        c.box(-3, 8, 8, 3, 12, 8, Blocks.PURPLE_STAINED_GLASS_PANE);
        c.box(-1, 9, 8, 1, 11, 8, Blocks.CYAN_STAINED_GLASS_PANE);
        for (int z : new int[] { 12, 18, 24, 30 }) {
            c.windowZ(-18, 3, z, z + 2, 11, Blocks.BLUE_STAINED_GLASS_PANE);
            c.windowZ(18, 3, z, z + 2, 11, Blocks.BLUE_STAINED_GLASS_PANE);
        }

        // 1 樓：宏偉石柱廊＋8 排橡木長椅掩體＋高台主祭壇＋側室書架
        c.columnGrid(-9, 9, 1, 14, 12, 28, 18, 6, Blocks.CHISELED_STONE_BRICKS);
        for (int z = 13; z <= 25; z += 3) {
            c.sofaX(-7, -2, 1, z, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
            c.sofaX(2, 7, 1, z, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        }
        c.box(-6, 1, 28, 6, 2, 33, Blocks.SMOOTH_QUARTZ);
        c.counter(-2, 30, 2, 31, 3, Blocks.GOLD_BLOCK);
        c.box(-16, 1, 29, -11, 3, 29, Blocks.BOOKSHELF);
        c.box(11, 1, 29, 16, 3, 29, Blocks.BOOKSHELF);

        // 2 樓：兩側環廊＋管風琴音樂高台 (y=7, x=-17..-10 & 10..17, z=9..34)
        c.box(-17, 7, 9, -10, 7, 34, Blocks.SPRUCE_PLANKS);
        c.box(10, 7, 9, 17, 7, 34, Blocks.SPRUCE_PLANKS);
        c.box(-10, 7, 9, 10, 7, 12, Blocks.SPRUCE_PLANKS);
        c.windowZ(-10, 8, 12, 34, 8, Blocks.STONE_BRICK_WALL);
        c.windowZ(10, 8, 12, 34, 8, Blocks.STONE_BRICK_WALL);
        c.windowX(-10, 8, 12, 10, 8, Blocks.STONE_BRICK_WALL);
        for (int px = -4; px <= 4; px += 2) c.pillar(px, 8, 12, 9, Blocks.GOLD_BLOCK);

        // 雙鐘樓全連通樓梯 (0->6->12->18->25，最後一段接 y=18->25 共 7 階)
        c.staircaseZ(-15, -14, 0, 6, 3, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(14, 15, 0, 6, 3, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-15, -14, 6, 12, 8, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(14, 15, 6, 12, 8, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-15, -14, 12, 18, 3, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-12, -11, 18, 25, 8, -1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        // 教堂後側也有樓梯接 2F 環廊 (0->7)
        c.staircaseZ(-16, -15, 0, 7, 25, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(15, 16, 0, 7, 25, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-15, 15, 14, 10, 33, 6, 7, Blocks.OCHRE_FROGLIGHT);

        // 46 個物資箱點位（貼近長椅／石柱／書架／牆壁）
        c.loot(-13, 1, 5);  c.loot(13, 1, 5);
        c.loot(-7, 1, 13);  c.loot(7, 1, 13);  c.loot(-17, 1, 14); c.loot(17, 1, 14);
        c.loot(-17, 1, 20); c.loot(17, 1, 20); c.loot(-17, 1, 26); c.loot(17, 1, 26);
        c.loot(-14, 1, 30); c.loot(14, 1, 30); c.loot(-5, 1, 19);  c.loot(5, 1, 19);
        c.loot(0, 1, 27);   c.loot(-3, 3, 30); c.loot(3, 3, 30);   c.loot(-4, 1, 7);
        c.loot(-11, 8, 14); c.loot(11, 8, 14); c.loot(-11, 8, 22); c.loot(11, 8, 22);
        c.loot(-11, 8, 30); c.loot(11, 8, 30); c.loot(-6, 8, 13);  c.loot(6, 8, 13);
        c.loot(-12, 7, 5);  c.loot(12, 7, 5);  c.loot(-12, 13, 5); c.loot(12, 13, 5);
        c.loot(-13, 19, 5); c.loot(-16, 26, 4); c.loot(-11, 26, 7); c.loot(12, 13, 7);
        c.loot(-15, 1, 1);  c.loot(15, 1, 1);  c.loot(-7, 1, 5);   c.loot(7, 1, 5);
        c.loot(-2, 1, 16);  c.loot(2, 1, 22);  c.loot(-11, 8, 18); c.loot(11, 8, 18);
        c.loot(-11, 8, 26); c.loot(11, 8, 26); c.loot(-13, 26, 5); c.loot(-16, 13, 6);
    }

    private static void crusaderCastle(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-22, 0, 0, 22, 25, 38);

        // 45x39 城牆環道城堡 (外城 -22..22, z=2..38, 城牆高 5, 走道 y=6)
        c.floor(-22, 0, 0, 22, 38, Blocks.COBBLESTONE);
        c.walls(-22, 1, 2, 22, 5, 38, Blocks.STONE_BRICKS);
        c.box(-22, 6, 2, 22, 6, 38, Blocks.SMOOTH_STONE);
        c.clear(-19, 6, 5, 19, 6, 35); // 內庭露空
        c.parapet(-22, 7, 2, 22, 38, Blocks.STONE_BRICK_WALL, true);
        // 吊閘門樓 (z=0..4, x=-4..4)
        c.walls(-4, 1, 0, 4, 10, 4, Blocks.STONE_BRICKS);
        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 4, 5);
        c.box(-2, 4, 0, 2, 4, 0, Blocks.IRON_BARS);
        c.doorway(-2, 1, 38, 5);
        c.doorwaySide(-22, 1, 15, 3);
        c.doorwaySide(22, 1, 15, 3);

        // 4 座 3 層圓角／方角箭塔 (y=0, 6, 12, 17)
        for (int[] tc : new int[][] { { -22, 2 }, { 16, 2 }, { -22, 32 }, { 16, 32 } }) {
            c.walls(tc[0], 1, tc[1], tc[0] + 6, 16, tc[1] + 6, Blocks.STONE_BRICKS);
            c.clear(tc[0] + 1, 1, tc[1] + 1, tc[0] + 5, 16, tc[1] + 5);
            c.box(tc[0], 6, tc[1], tc[0] + 6, 6, tc[1] + 6, Blocks.SPRUCE_PLANKS);
            c.box(tc[0], 12, tc[1], tc[0] + 6, 12, tc[1] + 6, Blocks.SPRUCE_PLANKS);
            c.box(tc[0], 17, tc[1], tc[0] + 6, 17, tc[1] + 6, Blocks.STONE_BRICKS);
            c.parapet(tc[0], 18, tc[1], tc[0] + 6, tc[1] + 6, Blocks.STONE_BRICK_WALL, true);
            c.doorwaySide(tc[0] < 0 ? tc[0] + 6 : tc[0], 1, tc[1] + 2, 2);
            c.doorway(tc[0] + 2, 7, tc[1] < 10 ? tc[1] + 6 : tc[1], 2);
            c.staircaseZ(tc[0] + 1, tc[0] + 2, 0, 6, tc[1] + 1, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
            c.staircaseZ(tc[0] + 4, tc[0] + 5, 6, 12, tc[1] + 5, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
            c.staircaseZ(tc[0] + 1, tc[0] + 2, 12, 17, tc[1] + 1, 1, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS);
        }

        // 中央大院：石井＋鐵匠棚＋戰術木柵掩體
        c.box(-2, 1, 12, 2, 1, 16, Blocks.STONE_BRICKS);
        c.box(-1, 0, 13, 1, 1, 15, Blocks.WATER);
        c.bunkerCoverL(-13, 1, 11, 3, 2, Blocks.OAK_PLANKS, Blocks.OAK_SLAB);
        c.counter(-15, 14, -12, 14, 1, Blocks.ANVIL);
        c.bunkerCoverL(13, 1, 11, -3, 2, Blocks.OAK_PLANKS, Blocks.OAK_SLAB);
        c.serverRack(14, 1, 14, 2, Blocks.BLAST_FURNACE, Blocks.STONE_BRICKS);

        // 後方 3 層國王主堡 (x=-12..12, z=21..35, 樓板 y=0, 6, 12, 18, 塔頂 y=24)
        c.walls(-12, 1, 21, 12, 17, 35, Blocks.STONE_BRICKS);
        c.clear(-11, 1, 22, 11, 17, 34);
        c.box(-12, 6, 21, 12, 6, 35, Blocks.SPRUCE_PLANKS);
        c.box(-12, 12, 21, 12, 12, 35, Blocks.SPRUCE_PLANKS);
        c.box(-12, 18, 21, 12, 18, 35, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-12, 19, 21, 12, 35, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-2, 1, 21, 5);
        c.doorway(-2, 1, 35, 5);
        c.doorwaySide(-12, 1, 27, 3);
        c.doorwaySide(12, 1, 27, 3);
        c.windowX(-9, 8, 21, -4, 10, Blocks.YELLOW_STAINED_GLASS_PANE);
        c.windowX(4, 8, 21, 9, 10, Blocks.YELLOW_STAINED_GLASS_PANE);
        // 1F 騎士宴會廳長桌＋2F 王座＋3F 兵書庫
        c.counter(-6, 26, 6, 27, 1, Blocks.DARK_OAK_PLANKS);
        c.sofaX(-6, 6, 1, 25, Blocks.OAK_STAIRS, Direction.NORTH);
        c.sofaX(-6, 6, 1, 28, Blocks.OAK_STAIRS, Direction.SOUTH);
        c.box(-2, 7, 31, 2, 8, 33, Blocks.RED_WOOL);
        c.set(0, 9, 32, Blocks.GOLD_BLOCK);
        c.box(-8, 13, 25, -4, 15, 25, Blocks.BOOKSHELF);
        c.box(4, 13, 25, 8, 15, 25, Blocks.BOOKSHELF);
        c.staircaseZ(-10, -9, 0, 6, 24, 1, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(9, 10, 6, 12, 30, -1, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(-10, -9, 12, 18, 24, 1, Blocks.SPRUCE_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-9, 9, 5, 23, 33, 6, 5, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-9, 9, 11, 23, 33, 6, 5, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-9, 9, 17, 23, 33, 6, 5, Blocks.OCHRE_FROGLIGHT);

        // 52 個物資箱點位（貼近水井／長桌／寶座／城牆垛口／書架）
        c.loot(-19, 1, 5);  c.loot(19, 1, 5);   c.loot(-19, 1, 35); c.loot(19, 1, 35);
        c.loot(-19, 7, 5);  c.loot(19, 7, 5);   c.loot(-19, 7, 35); c.loot(19, 7, 35);
        c.loot(-19, 13, 5); c.loot(19, 13, 5);  c.loot(-19, 13, 35); c.loot(19, 13, 35);
        c.loot(-19, 18, 5); c.loot(19, 18, 5);  c.loot(-19, 18, 35); c.loot(19, 18, 35);
        c.loot(-13, 1, 12); c.loot(13, 1, 12);  c.loot(-14, 1, 18); c.loot(14, 1, 18);
        c.loot(0, 1, 11);   c.loot(0, 1, 17);   c.loot(-10, 7, 3);  c.loot(10, 7, 3);
        c.loot(-20, 7, 15); c.loot(20, 7, 15);  c.loot(-20, 7, 25); c.loot(20, 7, 25);
        c.loot(-6, 1, 24);  c.loot(6, 1, 24);   c.loot(-6, 1, 29);  c.loot(6, 1, 29);
        c.loot(0, 1, 34);   c.loot(-6, 7, 23);  c.loot(6, 7, 23);   c.loot(-4, 7, 31);  c.loot(4, 7, 31);
        c.loot(-3, 7, 32);  c.loot(-6, 13, 26); c.loot(6, 13, 26);  c.loot(0, 13, 22);
        c.loot(0, 13, 33);  c.loot(-10, 19, 25); c.loot(10, 19, 25); c.loot(-10, 19, 32); c.loot(10, 19, 32);
        c.loot(0, 19, 22);  c.loot(-15, 7, 37); c.loot(15, 7, 37);  c.loot(0, 7, 37);   c.loot(-3, 1, 2);  c.loot(3, 1, 2);
    }

    private static void grandTheater(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-21, 0, 0, 21, 22, 34);

        c.floor(-21, 0, 0, 21, 34, Blocks.POLISHED_ANDESITE);
        c.box(-20, 0, 4, 20, 0, 34, Blocks.RED_CONCRETE);
        c.walls(-20, 1, 4, 20, 18, 34, Blocks.SANDSTONE);
        c.clear(-19, 1, 5, 19, 18, 33);
        c.box(-20, 19, 4, 20, 19, 34, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-20, 20, 4, 20, 34, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-20, 1, 4, 20, 19, 34, Blocks.QUARTZ_PILLAR);

        // 正面古典列柱門廊 (z=0..4)
        for (int px = -12; px <= 12; px += 6) c.pillar(px, 1, 12, 1, Blocks.QUARTZ_PILLAR);
        c.box(-14, 13, 0, 14, 13, 3, Blocks.SMOOTH_QUARTZ);
        c.doorway(-2, 1, 4, 5);
        c.doorway(-12, 1, 4, 3);
        c.doorway(10, 1, 4, 3);
        c.doorwaySide(-20, 1, 18, 3);
        c.doorwaySide(20, 1, 18, 3);

        // 前廳售票大廳 (z=5..10)
        c.box(-19, 1, 10, 19, 5, 10, Blocks.SMOOTH_QUARTZ);
        c.doorway(-2, 1, 10, 5);
        c.doorway(-14, 1, 10, 3);
        c.doorway(12, 1, 10, 3);
        c.counter(-6, 7, 6, 7, 1, Blocks.DARK_OAK_PLANKS);

        // 1 樓階梯座位觀眾席 (z=12..23)＋挑高大舞台 (z=24..30)＋後台兩層化妝間 (z=31..33)
        for (int row = 0; row < 4; row++) {
            int rz = 12 + row * 3;
            c.sofaX(-11, -3, 1, rz, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
            c.sofaX(3, 11, 1, rz, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        }
        c.box(-14, 1, 24, 14, 2, 30, Blocks.SPRUCE_PLANKS);
        c.pillar(-13, 3, 13, 24, Blocks.RED_WOOL);
        c.pillar(13, 3, 13, 24, Blocks.RED_WOOL);
        c.box(-12, 3, 26, -8, 3, 27, Blocks.STONE_BRICKS);
        c.box(8, 3, 26, 12, 3, 27, Blocks.STONE_BRICKS);
        c.box(-19, 1, 31, 19, 12, 31, Blocks.WHITE_CONCRETE);
        c.doorway(-14, 3, 31, 3);
        c.doorway(12, 3, 31, 3);

        // 2 樓 (y=7) & 3 樓 (y=13) 馬蹄形環繞包廂迴廊
        for (int fy : new int[] { 7, 13 }) {
            c.box(-19, fy, 5, -13, fy, 33, Blocks.SPRUCE_PLANKS);
            c.box(13, fy, 5, 19, fy, 33, Blocks.SPRUCE_PLANKS);
            c.box(-13, fy, 5, 13, fy, 11, Blocks.SPRUCE_PLANKS);
            c.windowZ(-13, fy + 1, 11, 31, fy + 1, Blocks.DARK_OAK_FENCE);
            c.windowZ(13, fy + 1, 11, 31, fy + 1, Blocks.DARK_OAK_FENCE);
            c.windowX(-13, fy + 1, 11, 13, fy + 1, Blocks.DARK_OAK_FENCE);
            c.sofaZ(-16, fy + 1, 14, 22, Blocks.DARK_OAK_STAIRS, Direction.EAST);
            c.sofaZ(16, fy + 1, 14, 22, Blocks.DARK_OAK_STAIRS, Direction.WEST);
        }

        // 雙寬樓梯連接 0->7->13->19 (無電梯)
        c.staircaseZ(-18, -17, 0, 7, 15, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(17, 18, 0, 7, 15, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(-18, -17, 7, 13, 29, -1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(17, 18, 13, 19, 20, -1, Blocks.DARK_OAK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-16, 16, 18, 7, 30, 8, 7, Blocks.SEA_LANTERN);

        // 46 個物資箱點位（貼近櫃檯／座席／舞台邊／木欄／女兒牆）
        c.loot(-15, 1, 8);  c.loot(15, 1, 8);   c.loot(0, 1, 8);
        c.loot(-15, 1, 14); c.loot(15, 1, 14);  c.loot(-15, 1, 21); c.loot(15, 1, 21);
        c.loot(-3, 1, 15);  c.loot(3, 1, 21);   c.loot(-7, 3, 26);  c.loot(7, 3, 26);  c.loot(0, 3, 30);
        c.loot(-10, 1, 32); c.loot(0, 1, 32);   c.loot(10, 1, 32);
        c.loot(-15, 8, 10); c.loot(15, 8, 10);  c.loot(0, 8, 10);   c.loot(-7, 8, 10); c.loot(7, 8, 10);
        c.loot(-14, 8, 19); c.loot(14, 8, 19);  c.loot(-14, 8, 28); c.loot(14, 8, 28);
        c.loot(-15, 14, 10); c.loot(15, 14, 10); c.loot(0, 14, 10);  c.loot(-7, 14, 10); c.loot(7, 14, 10);
        c.loot(-14, 14, 19); c.loot(14, 14, 19); c.loot(-14, 14, 28); c.loot(14, 14, 28);
        c.loot(-18, 20, 8); c.loot(18, 20, 8);  c.loot(-18, 20, 20); c.loot(18, 20, 20);
        c.loot(-18, 20, 30); c.loot(18, 20, 30); c.loot(0, 20, 5);  c.loot(0, 20, 33);
        c.loot(-11, 1, 1);  c.loot(11, 1, 1);   c.loot(-11, 1, 14); c.loot(11, 1, 14); c.loot(0, 8, 6);
    }

    private static void fortifiedAbbey(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-20, 0, 0, 20, 21, 32);

        c.floor(-20, 0, 0, 20, 32, Blocks.COBBLESTONE);
        c.walls(-19, 1, 2, 19, 5, 31, Blocks.STONE_BRICKS);
        c.clear(-18, 1, 3, 18, 5, 30);
        c.parapet(-19, 6, 2, 19, 31, Blocks.STONE_BRICK_WALL, true);
        c.doorway(-2, 1, 2, 5);
        c.doorway(-2, 1, 31, 5);
        c.doorwaySide(-19, 1, 15, 3);
        c.doorwaySide(19, 1, 15, 3);

        // 中庭藥草園噴泉＋迴廊支柱
        c.box(-2, 1, 13, 2, 1, 17, Blocks.STONE_BRICK_WALL);
        c.box(-1, 0, 14, 1, 1, 16, Blocks.WATER);
        c.planter(-8, 10, -5, 13, 1, Blocks.OAK_PLANKS, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);
        c.planter(5, 10, 8, 13, 1, Blocks.OAK_PLANKS, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);

        // 西翼 2 層石造食堂 (-18..-11, z=4..20) & 東翼 2 層抄經書庫 (11..18, z=4..20)
        for (int[] wing : new int[][] { { -18, -11 }, { 11, 18 } }) {
            int x0 = wing[0], x1 = wing[1];
            c.box(x0, 0, 4, x1, 0, 20, Blocks.SPRUCE_PLANKS);
            c.walls(x0, 1, 4, x1, 5, 20, Blocks.STONE_BRICKS);
            c.box(x0, 6, 4, x1, 6, 20, Blocks.SPRUCE_PLANKS);
            c.walls(x0, 7, 4, x1, 11, 20, Blocks.STONE_BRICKS);
            c.gableRoofZ(x0, 12, 4, x1, 20, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.STONE_BRICKS);
            int innerX = x0 < 0 ? x1 : x0;
            c.doorwaySide(innerX, 1, 10, 3);
            c.doorway(x0 + 3, 1, 20, 2);
            if (x0 < 0) {
                c.counter(x0 + 2, 7, x1 - 2, 10, 1, Blocks.DARK_OAK_PLANKS);
                c.bedUnit(x0 + 1, 6, x0 + 3, 6, 7, Blocks.GRAY_WOOL, Blocks.WHITE_WOOL);
                c.bedUnit(x0 + 1, 12, x0 + 3, 12, 7, Blocks.GRAY_WOOL, Blocks.WHITE_WOOL);
            } else {
                c.box(x0 + 2, 1, 7, x1 - 2, 3, 7, Blocks.BOOKSHELF);
                c.box(x0 + 2, 7, 10, x1 - 2, 9, 10, Blocks.BOOKSHELF);
            }
            int stX = x0 < 0 ? x0 + 1 : x1 - 2;
            c.staircaseZ(stX, stX + 1, 0, 6, 13, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
            c.ceilingLights(x0 + 2, x1 - 2, 5, 6, 18, 4, 6, Blocks.OCHRE_FROGLIGHT);
            c.ceilingLights(x0 + 2, x1 - 2, 11, 6, 18, 4, 6, Blocks.OCHRE_FROGLIGHT);
        }

        // 北翼挑高禮拜堂 (-18..10, z=21..30)＋東北 3 層鐘樓 (11..18, z=21..30, y=0..21)
        c.box(-18, 0, 21, 10, 0, 30, Blocks.SMOOTH_STONE);
        c.walls(-18, 1, 21, 10, 11, 30, Blocks.STONE_BRICKS);
        c.clear(-17, 1, 22, 9, 11, 29);
        c.doorway(-6, 1, 21, 4);
        c.sofaX(-12, -8, 1, 24, Blocks.OAK_STAIRS, Direction.NORTH);
        c.sofaX(0, 4, 1, 24, Blocks.OAK_STAIRS, Direction.NORTH);
        c.counter(-5, 28, -2, 28, 1, Blocks.GOLD_BLOCK);
        c.gableRoofZ(-18, 12, 21, 10, 30, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.STONE_BRICKS);
        c.ceilingLights(-15, 7, 11, 23, 28, 7, 4, Blocks.OCHRE_FROGLIGHT);

        c.box(11, 0, 21, 18, 0, 30, Blocks.SMOOTH_STONE);
        c.walls(11, 1, 21, 18, 18, 30, Blocks.STONE_BRICKS);
        c.clear(12, 1, 22, 17, 18, 29);
        c.box(11, 6, 21, 18, 6, 30, Blocks.SPRUCE_PLANKS);
        c.box(11, 12, 21, 18, 12, 30, Blocks.SPRUCE_PLANKS);
        c.box(11, 18, 21, 18, 18, 30, Blocks.DEEPSLATE_BRICKS);
        c.parapet(11, 19, 21, 18, 30, Blocks.STONE_BRICK_WALL, true);
        c.doorwaySide(10, 1, 24, 3);
        c.doorwaySide(11, 1, 24, 3);
        c.staircaseZ(12, 13, 0, 6, 22, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
        c.staircaseZ(15, 16, 6, 12, 28, -1, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
        c.staircaseZ(12, 13, 12, 18, 22, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);

        // 42 個物資箱點位（貼近噴泉／植栽／牆壁／書架／長椅／鐘樓垛口）
        c.loot(-3, 1, 14);  c.loot(3, 1, 14);   c.loot(-6, 1, 9);   c.loot(6, 1, 9);
        c.loot(-16, 1, 6);  c.loot(-12, 1, 9);  c.loot(-12, 1, 18); c.loot(12, 1, 8);
        c.loot(16, 1, 18);  c.loot(12, 1, 14);  c.loot(-12, 7, 6);  c.loot(-12, 7, 11);
        c.loot(-12, 7, 18); c.loot(12, 7, 9);   c.loot(12, 7, 14);  c.loot(12, 7, 18);
        c.loot(-14, 1, 23); c.loot(-10, 1, 25); c.loot(2, 1, 25);   c.loot(-6, 1, 28);
        c.loot(6, 1, 28);   c.loot(0, 1, 28);   c.loot(-16, 1, 28); c.loot(16, 1, 28);
        c.loot(16, 7, 23);  c.loot(12, 7, 27);  c.loot(16, 13, 23); c.loot(12, 13, 27);
        c.loot(12, 19, 22); c.loot(17, 19, 22); c.loot(12, 19, 29); c.loot(17, 19, 29);
        c.loot(-17, 1, 3);  c.loot(17, 1, 3);   c.loot(-7, 1, 19);  c.loot(7, 1, 19);
        c.loot(-14, 7, 16); c.loot(14, 7, 16);  c.loot(-17, 1, 23); c.loot(7, 1, 23);
        c.loot(-9, 1, 29);  c.loot(9, 1, 29);
    }

    private static void harborKeep(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-20, 0, 0, 20, 20, 34);

        c.floor(-20, 0, 0, 20, 34, Blocks.COBBLESTONE);
        // 外城壁壘 (x=-19..19, z=2..33, y=1..6, 城垛步道 y=6)
        c.walls(-19, 1, 2, 19, 5, 33, Blocks.STONE_BRICKS);
        c.box(-19, 6, 2, 19, 6, 33, Blocks.SMOOTH_STONE);
        c.clear(-15, 6, 6, 15, 6, 29);
        c.parapet(-19, 7, 2, 19, 33, Blocks.STONE_BRICK_WALL, true);
        c.doorway(-2, 1, 2, 5);
        c.doorway(-2, 1, 33, 5);
        c.doorwaySide(-19, 1, 17, 4);
        c.doorwaySide(19, 1, 17, 4);

        // 砲口石製海防巨砲掩體 (東南／西南／東北／西北)
        for (int[] cpos : new int[][] { { -14, 6 }, { 14, 6 }, { -14, 28 }, { 14, 28 } }) {
            c.box(cpos[0] - 1, 1, cpos[1] - 1, cpos[0] + 1, 2, cpos[1] + 1, Blocks.DEEPSLATE_BRICKS);
            c.box(cpos[0] - 1, 7, cpos[1] - 1, cpos[0] + 1, 8, cpos[1] + 1, Blocks.DEEPSLATE_BRICKS);
        }

        // 中央 3 層司令主堡 (x=-9..9, z=11..24, y=0, 6, 12, 17)
        c.box(-9, 0, 11, 9, 0, 24, Blocks.SMOOTH_STONE);
        c.walls(-9, 1, 11, 9, 16, 24, Blocks.STONE_BRICKS);
        c.clear(-8, 1, 12, 8, 16, 23);
        c.box(-9, 6, 11, 9, 6, 24, Blocks.SPRUCE_PLANKS);
        c.box(-9, 12, 11, 9, 12, 24, Blocks.SPRUCE_PLANKS);
        c.box(-9, 17, 11, 9, 17, 24, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-9, 18, 11, 9, 24, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-1, 1, 11, 3);
        c.doorway(-1, 1, 24, 3);
        c.doorwaySide(-9, 1, 16, 3);
        c.doorwaySide(9, 1, 16, 3);
        c.counter(-4, 17, 4, 17, 1, Blocks.DARK_OAK_PLANKS);
        c.deskUnit(-4, 7, 15, Blocks.DARK_OAK_PLANKS, Blocks.OAK_STAIRS, Direction.NORTH);
        c.box(3, 7, 20, 6, 9, 20, Blocks.BOOKSHELF);
        c.tallCover(-4, 17, 4, 17, 13, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 城壁雙寬登牆樓梯 + 主堡樓梯
        c.staircaseZ(-18, -17, 0, 6, 11, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(17, 18, 0, 6, 11, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-8, -7, 0, 6, 14, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
        c.staircaseZ(7, 8, 6, 12, 21, -1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);
        c.staircaseZ(-8, -7, 12, 17, 14, 1, Blocks.DARK_OAK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.ceilingLights(-6, 6, 5, 13, 22, 6, 5, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-6, 6, 11, 13, 22, 6, 5, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-6, 6, 16, 13, 22, 6, 5, Blocks.OCHRE_FROGLIGHT);

        // 44 個物資箱點位（貼近艦砲座／城壁垛口／主堡桌子／書架）
        c.loot(-12, 1, 6);  c.loot(12, 1, 6);   c.loot(-12, 1, 28); c.loot(12, 1, 28);
        c.loot(-17, 1, 5);  c.loot(17, 1, 5);   c.loot(-17, 1, 30); c.loot(17, 1, 30);
        c.loot(-5, 1, 4);   c.loot(5, 1, 4);    c.loot(-5, 1, 31);  c.loot(5, 1, 31);
        c.loot(-4, 1, 16);  c.loot(4, 1, 16);   c.loot(-4, 1, 18);  c.loot(4, 1, 18);
        c.loot(-5, 1, 22);  c.loot(5, 1, 22);
        c.loot(-16, 7, 4);  c.loot(16, 7, 4);   c.loot(-16, 7, 31); c.loot(16, 7, 31);
        c.loot(-12, 7, 8);  c.loot(12, 7, 8);   c.loot(-12, 7, 26); c.loot(12, 7, 26);
        c.loot(0, 7, 3);    c.loot(0, 7, 32);   c.loot(-4, 7, 16);  c.loot(4, 7, 19);
        c.loot(-5, 7, 21);  c.loot(5, 7, 21);
        c.loot(-5, 13, 13); c.loot(5, 13, 13);  c.loot(-4, 13, 18); c.loot(4, 13, 18);
        c.loot(0, 13, 22);  c.loot(-5, 13, 22);
        c.loot(-7, 18, 13); c.loot(7, 18, 13);  c.loot(-7, 18, 22); c.loot(7, 18, 22);
        c.loot(0, 18, 12);  c.loot(0, 18, 23);
    }

    private static void marketHall(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-19, 0, 0, 19, 19, 30);

        c.floor(-19, 0, 0, 19, 30, Blocks.COBBLESTONE);
        c.box(-18, 0, 3, 18, 0, 29, Blocks.SPRUCE_PLANKS);
        c.walls(-18, 1, 3, 18, 12, 29, Blocks.WHITE_CONCRETE);
        c.walls(-18, 1, 3, 18, 2, 29, Blocks.STONE_BRICKS);
        c.clear(-17, 1, 4, 17, 12, 28);
        c.cornerPillars(-18, 1, 3, 18, 12, 29, Blocks.DARK_OAK_LOG);
        c.gableRoofZ(-18, 13, 3, 18, 29, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS);

        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 29, 5);
        c.doorwaySide(-18, 1, 15, 4);
        c.doorwaySide(18, 1, 15, 4);
        for (int fy : new int[] { 3, 8 }) {
            c.windowX(-15, fy, 3, -6, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowX(6, fy, 3, 15, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowZ(-18, fy, 6, 12, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowZ(18, fy, 6, 12, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
        }

        // 1F：雙排商會攤位掩體＋中央拍賣高台
        for (int z : new int[] { 8, 14, 21 }) {
            c.tallCover(-12, z, -7, z, 1, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_SLAB);
            c.tallCover(7, z, 12, z, 1, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_SLAB);
        }
        c.box(-3, 1, 14, 3, 1, 17, Blocks.STONE_BRICKS);
        c.counter(-1, 2, 1, 2, 1, Blocks.DARK_OAK_PLANKS);

        // 2F 環形公會迴廊 (y=6)＋辦公室書架隔間
        c.box(-17, 6, 4, -10, 6, 28, Blocks.SPRUCE_PLANKS);
        c.box(10, 6, 4, 17, 6, 28, Blocks.SPRUCE_PLANKS);
        c.box(-10, 6, 4, 10, 6, 8, Blocks.SPRUCE_PLANKS);
        c.box(-10, 6, 24, 10, 6, 28, Blocks.SPRUCE_PLANKS);
        c.windowZ(-10, 7, 8, 24, 7, Blocks.DARK_OAK_FENCE);
        c.windowZ(10, 7, 8, 24, 7, Blocks.DARK_OAK_FENCE);
        c.windowX(-10, 7, 8, 10, 7, Blocks.DARK_OAK_FENCE);
        c.windowX(-10, 7, 24, 10, 7, Blocks.DARK_OAK_FENCE);
        c.box(-15, 7, 13, -12, 9, 13, Blocks.BOOKSHELF);
        c.box(12, 7, 13, 15, 9, 13, Blocks.BOOKSHELF);
        c.counter(-3, 25, 3, 25, 7, Blocks.DARK_OAK_PLANKS);

        c.staircaseZ(-16, -15, 0, 6, 17, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(15, 16, 0, 6, 17, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-14, 14, 12, 6, 26, 7, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-14, 14, 5, 6, 26, 7, 6, Blocks.OCHRE_FROGLIGHT);

        // 42 個物資箱點位（全部貼近攤位／拍賣台／書架／護欄／牆壁）
        c.loot(-12, 1, 9);  c.loot(-7, 1, 9);  c.loot(7, 1, 9);   c.loot(12, 1, 9);
        c.loot(-12, 1, 15); c.loot(-7, 1, 15); c.loot(7, 1, 15);  c.loot(12, 1, 15);
        c.loot(-12, 1, 22); c.loot(-7, 1, 22); c.loot(7, 1, 22);  c.loot(12, 1, 22);
        c.loot(-4, 1, 15);  c.loot(4, 1, 15);  c.loot(0, 2, 15);  c.loot(-16, 1, 5);
        c.loot(16, 1, 5);   c.loot(-16, 1, 27); c.loot(16, 1, 27); c.loot(-4, 1, 27);
        c.loot(4, 1, 27);
        c.loot(-11, 7, 10); c.loot(11, 7, 10); c.loot(-11, 7, 18); c.loot(11, 7, 18);
        c.loot(-11, 7, 23); c.loot(11, 7, 23); c.loot(-13, 7, 14); c.loot(13, 7, 14);
        c.loot(-5, 7, 7);   c.loot(5, 7, 7);   c.loot(-5, 7, 25);  c.loot(5, 7, 25);
        c.loot(0, 7, 7);    c.loot(0, 7, 26);  c.loot(-16, 7, 7);  c.loot(16, 7, 7);
        c.loot(-16, 7, 27); c.loot(16, 7, 27); c.loot(-16, 7, 12); c.loot(16, 7, 12);
        c.loot(0, 1, 6);
    }
}
