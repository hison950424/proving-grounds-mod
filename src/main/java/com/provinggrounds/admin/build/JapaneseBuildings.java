package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.JAPANESE;

/**
 * 檔案: JapaneseBuildings.java
 * 目的: 和風城郭與庭園風格 4 棟大型／超大型建築（全數無木箱、樓梯最後一階完整齊平、柵欄／石牆／白玻璃格柵自動連接）。
 */
final class JapaneseBuildings {

    private JapaneseBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("jp_tenshukaku", JAPANESE, "五重威嚴天守閣",
            "4 層層疊飛簷城郭：石垣基座與四方城門、武士兵器陳列室、2 樓緣側木欄迴廊與議事大廳、3 樓藏書紙門迷宮、4 樓 360° 展望樓＋單座電梯（38 個物資點）",
            31, 31, 27, 38, JapaneseBuildings::tenshukaku));

        out.accept(new BuildingBlueprint("jp_onsen_ryokan", JAPANESE, "大型和風溫泉旅館",
            "2 層回字形中庭大旅館：正中央露天溫泉池與櫻花石庭、西翼榻榻米宴會廳、東翼湯屋隔間、2 樓環中庭木欄廊道與 6 間獨立和室（38 個物資點）",
            35, 29, 17, 38, JapaneseBuildings::onsenRyokan));

        out.accept(new BuildingBlueprint("jp_grand_temple", JAPANESE, "千本大佛殿與藏經閣",
            "雙層挑高宏偉佛殿：寬闊石階大門廊與香爐石燈籠掩體、朱紅雙排巨柱陣、金身佛壇與禪室廂房、2 樓 U 形空中迴廊與三排藏經閣書架（34 個物資點）",
            33, 27, 21, 34, JapaneseBuildings::grandTemple));

        out.accept(new BuildingBlueprint("jp_edo_street", JAPANESE, "江戶二層連棟商店街",
            "43×35 古街道巷戰區：雙朱紅大鳥居＋青石商店街與木攤掩體、左右對峙 6 棟二層和風町家商鋪（茶屋／刀坊／酒肆／書店／藥鋪／綢緞行）＋跨街木橋（48 個物資點）",
            43, 35, 17, 48, JapaneseBuildings::edoStreet));
    }

    private static void tenshukaku(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-15, 0, 0, 15, 27, 30);

        // 1 樓石垣外庭＋主樓底座 (x=-14..14, z=2..28)
        c.floor(-15, 0, 0, 15, 30, Blocks.COBBLESTONE);
        c.box(-14, 0, 2, 14, 0, 28, Blocks.STONE_BRICKS);
        c.tallCover(-6, 0, -4, 0, 1, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_SLAB);
        c.tallCover(4, 0, 6, 0, 1, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_SLAB);

        // 1 樓 (y=0..5)：石垣與白牆＋深色橡木柱＋四方城門
        c.walls(-14, 1, 2, 14, 5, 28, Blocks.WHITE_CONCRETE);
        c.walls(-14, 1, 2, 14, 2, 28, Blocks.STONE_BRICKS);
        c.clear(-13, 1, 3, 13, 5, 27);
        c.cornerPillars(-14, 1, 5, 14, 5, 28, Blocks.DARK_OAK_LOG);
        c.doorway(-1, 1, 2, 3);
        c.doubleDoor(-1, 1, 2, Blocks.DARK_OAK_DOOR);
        c.doorway(-1, 1, 28, 3);
        c.doorwaySide(-14, 1, 14, 3);
        c.doorwaySide(14, 1, 14, 3);
        c.windowX(-11, 3, 2, -6, 4, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(6, 3, 2, 11, 4, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowZ(-14, 3, 6, 11, 4, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowZ(14, 3, 6, 11, 4, Blocks.WHITE_STAINED_GLASS_PANE);

        // 1 樓飛簷屋簷＋2 樓地板 (y=6, 主室 -12..12, z=4..26)
        c.box(-14, 6, 2, 14, 6, 28, Blocks.SPRUCE_PLANKS);
        pagodaEave(c, -15, 6, 1, 15, 29, Blocks.DEEPSLATE_BRICK_STAIRS);

        // 2 樓 (y=6..11)：緣側迴廊護欄 (-14..14) ＋ 主城牆 (-12..12, z=4..26)
        c.parapet(-14, 7, 2, 14, 28, Blocks.DARK_OAK_FENCE, false);
        c.walls(-12, 7, 4, 12, 11, 26, Blocks.WHITE_CONCRETE);
        c.cornerPillars(-12, 7, 11, 12, 11, 26, Blocks.DARK_OAK_LOG);
        c.doorway(-1, 7, 4, 3);
        c.doorway(-1, 7, 26, 3);
        c.doorwaySide(-12, 7, 14, 3);
        c.doorwaySide(12, 7, 14, 3);
        c.windowX(-10, 8, 4, -5, 9, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(5, 8, 4, 10, 9, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowZ(-12, 8, 7, 11, 9, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowZ(12, 8, 7, 11, 9, Blocks.WHITE_STAINED_GLASS_PANE);

        // 2 樓飛簷＋3 樓地板 (y=12, 主室 -10..10, z=6..24)
        c.box(-12, 12, 4, 12, 12, 26, Blocks.SPRUCE_PLANKS);
        pagodaEave(c, -13, 12, 3, 13, 27, Blocks.DEEPSLATE_BRICK_STAIRS);

        // 3 樓 (y=12..17)：緣側欄杆＋主室
        c.parapet(-12, 13, 4, 12, 26, Blocks.DARK_OAK_FENCE, false);
        c.walls(-10, 13, 6, 10, 17, 24, Blocks.WHITE_CONCRETE);
        c.cornerPillars(-10, 13, 17, 10, 17, 24, Blocks.DARK_OAK_LOG);
        c.doorway(-1, 13, 6, 3);
        c.doorway(-1, 13, 24, 3);
        c.doorwaySide(-10, 13, 14, 3);
        c.doorwaySide(10, 13, 14, 3);
        c.windowX(-8, 14, 6, -4, 15, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(4, 14, 6, 8, 15, Blocks.WHITE_STAINED_GLASS_PANE);

        // 3 樓飛簷＋4 樓地板 (y=18, 展望天守 -7..7, z=9..21)
        c.box(-10, 18, 6, 10, 18, 24, Blocks.DARK_OAK_PLANKS);
        pagodaEave(c, -11, 18, 5, 11, 25, Blocks.DEEPSLATE_BRICK_STAIRS);

        // 4 樓頂層展望室 (y=18..22)＋大唐破風頂簷 (y=23..26)
        c.parapet(-10, 19, 6, 10, 24, Blocks.DARK_OAK_FENCE, false);
        c.walls(-7, 19, 9, 7, 22, 21, Blocks.WHITE_CONCRETE);
        c.cornerPillars(-7, 19, 22, 7, 22, 21, Blocks.DARK_OAK_LOG);
        c.doorway(-1, 19, 9, 3);
        c.doorway(-1, 19, 21, 3);
        c.doorwaySide(-7, 19, 14, 2);
        c.doorwaySide(7, 19, 14, 2);
        c.windowX(-5, 20, 9, -3, 21, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowX(3, 20, 9, 5, 21, Blocks.WHITE_STAINED_GLASS_PANE);
        c.pagodaRoof(-7, 23, 9, 7, 21, 4, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES);
        c.set(-2, 27, 15, Blocks.GOLD_BLOCK);
        c.set(2, 27, 15, Blocks.GOLD_BLOCK);

        // === 1 樓內裝：玄關屏風＋主柱陣＋西側兵器台＋東側茶室庭園 ===
        c.tallCover(-3, 6, 3, 6, 1, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB);
        c.pillar(-5, 1, 17, 10, Blocks.DARK_OAK_LOG);
        c.pillar(5, 1, 17, 10, Blocks.DARK_OAK_LOG);
        c.pillar(-5, 1, 17, 20, Blocks.DARK_OAK_LOG);
        c.pillar(5, 1, 17, 20, Blocks.DARK_OAK_LOG);
        // 西側武士兵器陳列區（無木箱）
        c.counter(-12, 8, -8, 8, 1, Blocks.ANVIL);
        c.box(-12, 1, 11, -9, 3, 11, Blocks.BOOKSHELF);
        c.tallCover(-12, 16, -8, 16, 1, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        // 東側枯山水茶室
        c.planter(8, 7, 12, 11, 1, Blocks.DARK_OAK_PLANKS, Blocks.MOSS_BLOCK, Blocks.CHERRY_SAPLING);
        c.counter(9, 14, 12, 16, 1, Blocks.STRIPPED_OAK_LOG);

        // === 2 樓：榻榻米議事廳＋主座高台與屏風 ===
        c.box(-4, 6, 9, 4, 6, 21, Blocks.BAMBOO_MOSAIC);
        c.counter(-3, 22, 3, 24, 7, Blocks.DARK_OAK_PLANKS);
        c.sofaX(-2, 2, 7, 23, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        c.box(-9, 7, 10, -7, 9, 10, Blocks.WHITE_STAINED_GLASS_PANE);
        c.box(7, 7, 10, 9, 9, 10, Blocks.WHITE_STAINED_GLASS_PANE);
        c.tallCover(-9, 17, -6, 17, 7, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_SLAB);
        c.tallCover(6, 17, 9, 17, 7, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_SLAB);

        // === 3 樓：環形藏書閣與格子拉門迷宮 ===
        for (int z : new int[] { 9, 13 }) {
            c.box(-8, 13, z, -5, 15, z, Blocks.BOOKSHELF);
            c.box(5, 13, z, 8, 15, z, Blocks.BOOKSHELF);
        }
        c.box(-8, 13, 21, -5, 15, 21, Blocks.BOOKSHELF);
        c.windowX(-3, 13, 15, 3, 14, Blocks.WHITE_STAINED_GLASS_PANE);

        // === 4 樓：內側四柱掩體＋茶桌 ===
        c.counter(-2, 14, 2, 16, 19, Blocks.DARK_OAK_PLANKS);

        // === 雙寬 2 格樓梯 (1F->2F->3F->4F)＋電梯直井 ===
        c.staircaseZ(-8, -7, 0, 6, 17, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(7, 8, 6, 12, 21, -1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(-5, -4, 12, 18, 14, 1, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.windowZ(-6, 7, 17, 23, 7, Blocks.DARK_OAK_FENCE);
        c.windowZ(6, 13, 15, 21, 13, Blocks.DARK_OAK_FENCE);
        c.windowZ(-3, 19, 14, 20, 19, Blocks.DARK_OAK_FENCE);
        c.elevator(5, 20, 0, 6, 12, 18);

        c.ceilingLights(-10, 10, 5, 5, 25, 6, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-9, 9, 11, 6, 23, 6, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-7, 7, 17, 8, 22, 6, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-4, 4, 22, 11, 19, 4, 4, Blocks.OCHRE_FROGLIGHT);

        c.bunkerCoverL(-4, 1, 24, 2, -2, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(4, 1, 24, -2, -2, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_SLAB);

        // 38 個物資箱點位
        c.loot(-10, 1, 6);  c.loot(-10, 1, 22); c.loot(10, 1, 6);   c.loot(10, 1, 22);  c.loot(0, 1, 15);
        c.loot(0, 1, 4);    c.loot(0, 1, 25);   c.loot(-12, 1, 13); c.loot(12, 1, 18);  c.loot(-6, 1, 23); c.loot(6, 1, 23);
        c.loot(-10, 7, 14); c.loot(10, 7, 14);  c.loot(0, 7, 21);   c.loot(0, 7, 6);    c.loot(0, 7, 14);
        c.loot(-13, 7, 5);  c.loot(13, 7, 5);   c.loot(-13, 7, 25); c.loot(13, 7, 25);  c.loot(-13, 7, 15); c.loot(13, 7, 15);
        c.loot(-7, 13, 12); c.loot(7, 13, 12);  c.loot(-7, 13, 21); c.loot(7, 13, 21);
        c.loot(-11, 13, 15); c.loot(11, 13, 15); c.loot(0, 13, 8);  c.loot(0, 13, 22);  c.loot(-11, 13, 7); c.loot(11, 13, 7);
        c.loot(-9, 19, 15); c.loot(9, 19, 15);  c.loot(0, 19, 12);
        c.loot(0, 19, 7);   c.loot(0, 19, 23);  c.loot(0, 19, 18);
    }

    private static void pagodaEave(BuildingCanvas c, int x0, int y, int z0, int x1, int z1, Block stairBlock) {
        for (int x = x0; x <= x1; x++) {
            c.stair(x, y, z0, stairBlock, Direction.NORTH);
            c.stair(x, y, z1, stairBlock, Direction.SOUTH);
        }
        for (int z = z0 + 1; z < z1; z++) {
            c.stair(x0, y, z, stairBlock, Direction.EAST);
            c.stair(x1, y, z, stairBlock, Direction.WEST);
        }
    }

    private static void onsenRyokan(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-17, 0, 0, 17, 17, 28);

        // 1 樓地面＋前門唐破風門廊 (z=0..2)＋主體 (z=3..28)
        c.floor(-17, 0, 0, 17, 28, Blocks.STONE_BRICKS);
        c.box(-16, 0, 3, 16, 0, 28, Blocks.SPRUCE_PLANKS);
        c.walls(-16, 1, 3, 16, 5, 28, Blocks.WHITE_CONCRETE);
        c.cornerPillars(-16, 1, 5, 16, 11, 28, Blocks.CHERRY_LOG);
        // 正門屋簷
        c.pillar(-4, 1, 5, 0, Blocks.CHERRY_LOG);
        c.pillar(4, 1, 5, 0, Blocks.CHERRY_LOG);
        c.box(-5, 5, 0, 5, 5, 2, Blocks.DEEPSLATE_BRICK_STAIRS);

        // 2 樓地板 (y=6)＋外牆 (y=7..11)＋和風瓦頂 (y=12..17)
        c.box(-16, 6, 3, 16, 6, 28, Blocks.SPRUCE_PLANKS);
        c.walls(-16, 7, 3, 16, 11, 28, Blocks.WHITE_CONCRETE);
        c.pagodaRoof(-16, 12, 3, 16, 28, 5, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES);

        // 四面大門與拉門玻璃窗
        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-16, 1, 14, 3);
        c.doorwaySide(16, 1, 14, 3);
        for (int fy : new int[] { 2, 8 }) {
            c.windowX(-14, fy, 3, -6, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
            c.windowX(6, fy, 3, 14, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
            c.windowZ(-16, fy, 6, 11, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
            c.windowZ(-16, fy, 19, 25, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
            c.windowZ(16, fy, 6, 11, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
            c.windowZ(16, fy, 19, 25, fy + 2, Blocks.WHITE_STAINED_GLASS_PANE);
        }

        // === 正中央露天溫泉池庭園（貫穿 2F 與屋頂開天井 x=-5..5, z=10..20）===
        c.box(-5, 6, 10, 5, 17, 20, Blocks.AIR);
        c.box(-6, 7, 9, 6, 7, 21, Blocks.CHERRY_FENCE);
        c.box(-5, 7, 10, 5, 7, 20, Blocks.AIR);
        // 溫泉池水與石庭掩體
        c.box(-5, 1, 10, 5, 1, 20, Blocks.MOSSY_COBBLESTONE_WALL);
        c.box(-4, 0, 11, 4, 0, 19, Blocks.SEA_LANTERN);
        c.box(-4, 1, 11, 4, 1, 19, Blocks.WATER);
        c.air(-1, 1, 10); c.air(0, 1, 10); c.air(1, 1, 10); // 正面留出走入中庭賞景缺口
        // 庭園中央假山＋櫻花樹掩體
        c.box(-1, 1, 14, 1, 2, 16, Blocks.MOSSY_COBBLESTONE);
        c.pillar(0, 3, 5, 15, Blocks.CHERRY_LOG);
        c.box(-2, 5, 13, 2, 6, 17, Blocks.CHERRY_LEAVES);

        // === 1 樓左右兩翼：大廳櫃檯＋西翼宴會廳＋東翼湯屋 ===
        c.counter(-4, 6, 4, 6, 1, Blocks.CHERRY_PLANKS);
        // 西翼榻榻米宴會廳
        c.box(-14, 0, 8, -8, 0, 23, Blocks.BAMBOO_MOSAIC);
        c.box(-7, 1, 6, -7, 5, 24, Blocks.WHITE_CONCRETE);
        c.doorwaySide(-7, 1, 9, 2);
        c.doorwaySide(-7, 1, 20, 2);
        for (int z : new int[] { 11, 16, 21 }) {
            c.counter(-13, z, -10, z, 1, Blocks.DARK_OAK_PLANKS);
            c.sofaX(-13, -10, 1, z - 1, Blocks.SPRUCE_STAIRS, Direction.NORTH);
        }
        // 東翼男女大浴場隔間
        c.box(7, 1, 6, 7, 5, 24, Blocks.STONE_BRICKS);
        c.doorwaySide(7, 1, 9, 2);
        c.doorwaySide(7, 1, 20, 2);
        c.tallCover(9, 14, 14, 14, 1, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.box(10, 0, 8, 14, 0, 12, Blocks.WATER);
        c.box(10, 0, 18, 14, 0, 22, Blocks.WATER);

        // === 2 樓：6 間獨立和室套房 (3 西 + 3 東) ===
        for (int zBase : new int[] { 5, 12, 19 }) {
            c.box(-15, 7, zBase + 6, -8, 11, zBase + 6, Blocks.WHITE_CONCRETE);
            c.box(-8, 7, zBase, -8, 11, zBase + 6, Blocks.WHITE_STAINED_GLASS_PANE);
            c.doorwaySide(-8, 7, zBase + 2, 2);
            c.bedUnit(-14, zBase + 1, -12, zBase + 2, 7, Blocks.PINK_WOOL, Blocks.WHITE_WOOL);
            c.counter(-15, zBase + 4, -13, zBase + 4, 7, Blocks.DARK_OAK_PLANKS);

            c.box(8, 7, zBase + 6, 15, 11, zBase + 6, Blocks.WHITE_CONCRETE);
            c.box(8, 7, zBase, 8, 11, zBase + 6, Blocks.WHITE_STAINED_GLASS_PANE);
            c.doorwaySide(8, 7, zBase + 2, 2);
            c.bedUnit(12, zBase + 1, 14, zBase + 2, 7, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL);
            c.counter(13, zBase + 4, 15, zBase + 4, 7, Blocks.DARK_OAK_PLANKS);
        }

        // === 雙寬 2 格樓梯 (1F->2F，無電梯) ===
        c.staircaseZ(-15, -14, 0, 6, 22, 1, Blocks.CHERRY_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS);
        c.staircaseZ(14, 15, 0, 6, 22, 1, Blocks.CHERRY_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS);
        c.bunkerCoverL(-3, 7, 25, 2, -1, Blocks.CHERRY_PLANKS, Blocks.SPRUCE_SLAB);
        c.bunkerCoverL(3, 7, 25, -2, -1, Blocks.CHERRY_PLANKS, Blocks.SPRUCE_SLAB);

        c.ceilingLights(-14, 14, 5, 5, 26, 7, 6, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(-14, 14, 11, 5, 26, 7, 6, Blocks.OCHRE_FROGLIGHT);

        // 38 個物資箱點位
        c.loot(-3, 1, 8);   c.loot(3, 1, 8);    c.loot(-12, 1, 8);  c.loot(-12, 1, 19);
        c.loot(12, 1, 7);   c.loot(12, 1, 16);  c.loot(-2, 1, 25);  c.loot(2, 1, 25);
        c.loot(-6, 1, 15);  c.loot(6, 1, 15);   c.loot(-9, 1, 13);  c.loot(-9, 1, 23);  c.loot(9, 1, 14);  c.loot(9, 1, 22);
        c.loot(-12, 1, 25); c.loot(12, 1, 25);  c.loot(0, 1, 22);
        c.loot(-11, 7, 8);  c.loot(-9, 7, 6);   c.loot(-11, 7, 15); c.loot(-9, 7, 13);  c.loot(-11, 7, 22); c.loot(-9, 7, 20);
        c.loot(11, 7, 8);   c.loot(9, 7, 6);    c.loot(11, 7, 15);  c.loot(9, 7, 13);   c.loot(11, 7, 22);  c.loot(9, 7, 20);
        c.loot(-5, 7, 24);  c.loot(5, 7, 24);   c.loot(0, 7, 6);    c.loot(0, 7, 23);   c.loot(-7, 7, 15);  c.loot(7, 7, 15);
        c.loot(-7, 7, 8);   c.loot(7, 7, 8);    c.loot(0, 7, 26);
    }

    private static void grandTemple(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-16, 0, 0, 16, 21, 26);

        // 莊嚴石磚平台 (z=0..4)＋高大主殿 (z=5..26)
        c.floor(-16, 0, 0, 16, 26, Blocks.STONE_BRICKS);
        c.box(-15, 0, 5, 15, 0, 26, Blocks.SMOOTH_STONE);
        // 前庭大香爐掩體與石燈籠
        c.box(-2, 1, 1, 2, 2, 3, Blocks.POLISHED_DEEPSLATE);
        c.box(-1, 3, 2, 1, 3, 2, Blocks.CAMPFIRE);
        c.pillar(-6, 1, 2, 2, Blocks.STONE_BRICK_WALL);
        c.set(-6, 3, 2, Blocks.OCHRE_FROGLIGHT);
        c.pillar(6, 1, 2, 2, Blocks.STONE_BRICK_WALL);
        c.set(6, 3, 2, Blocks.OCHRE_FROGLIGHT);

        // 主殿外牆 (y=1..13)＋朱紅大柱＋雙重飛簷屋頂 (y=14..21)
        c.walls(-15, 1, 5, 15, 13, 26, Blocks.WHITE_CONCRETE);
        c.walls(-15, 1, 5, 15, 2, 26, Blocks.RED_TERRACOTTA);
        c.clear(-14, 1, 6, 14, 13, 25);
        c.cornerPillars(-15, 1, 5, 15, 13, 26, Blocks.RED_CONCRETE);
        c.doorway(-2, 1, 5, 5);
        c.doorway(-2, 1, 26, 5);
        c.doorwaySide(-15, 1, 15, 4);
        c.doorwaySide(15, 1, 15, 4);
        for (int fy : new int[] { 3, 9 }) {
            c.windowX(-12, fy, 5, -5, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowX(5, fy, 5, 12, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowZ(-15, fy, 8, 13, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
            c.windowZ(15, fy, 8, 13, fy + 2, Blocks.YELLOW_STAINED_GLASS_PANE);
        }
        pagodaEave(c, -16, 7, 4, 16, 27, Blocks.DEEPSLATE_BRICK_STAIRS);
        c.pagodaRoof(-15, 14, 5, 15, 26, 7, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES);
        c.set(0, 21, 15, Blocks.GOLD_BLOCK);

        // === 1 樓：朱紅雙排巨柱走廊＋中央莊嚴大佛壇＋東西禪室 ===
        for (int z : new int[] { 8, 13, 18, 22 }) {
            c.pillar(-6, 1, 13, z, Blocks.RED_CONCRETE);
            c.pillar(6, 1, 13, z, Blocks.RED_CONCRETE);
        }
        // 中央挑高大佛座台與金身雕塑
        c.box(-3, 1, 16, 3, 2, 20, Blocks.SMOOTH_QUARTZ);
        c.box(-2, 3, 17, 2, 5, 19, Blocks.GOLD_BLOCK);
        c.box(-1, 6, 17, 1, 8, 19, Blocks.GOLD_BLOCK);
        c.set(0, 9, 18, Blocks.SEA_LANTERN);
        // 供桌與廂房書架
        c.counter(-3, 12, 3, 12, 1, Blocks.DARK_OAK_PLANKS);
        c.tallCover(-12, 10, -8, 10, 1, Blocks.BOOKSHELF, Blocks.SPRUCE_SLAB);
        c.tallCover(8, 10, 12, 10, 1, Blocks.BOOKSHELF, Blocks.SPRUCE_SLAB);
        c.tallCover(-12, 20, -8, 20, 1, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(8, 20, 12, 20, 1, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_SLAB);

        // === 2 樓：U 形空中迴廊 (y=7)＋後側藏經閣密庫 ===
        c.box(-14, 7, 6, -7, 7, 25, Blocks.SPRUCE_PLANKS);
        c.box(7, 7, 6, 14, 7, 25, Blocks.SPRUCE_PLANKS);
        c.box(-7, 7, 21, 7, 7, 25, Blocks.SPRUCE_PLANKS);
        c.windowZ(-7, 8, 6, 21, 8, Blocks.DARK_OAK_FENCE);
        c.windowZ(7, 8, 6, 21, 8, Blocks.DARK_OAK_FENCE);
        c.windowX(-7, 8, 21, 7, 8, Blocks.DARK_OAK_FENCE);
        // 藏經閣書架排
        for (int x : new int[] { -12, -9, 9, 12 }) {
            c.box(x, 8, 8, x, 10, 11, Blocks.BOOKSHELF);
        }
        c.box(-4, 8, 23, -1, 10, 23, Blocks.BOOKSHELF);
        c.box(1, 8, 23, 4, 10, 23, Blocks.BOOKSHELF);

        // === 寬 2 格左右對稱樓梯 (0->7, 7 階) ===
        c.staircaseZ(-14, -13, 0, 7, 13, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.staircaseZ(13, 14, 0, 7, 13, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);

        c.ceilingLights(-12, 12, 13, 7, 24, 6, 6, Blocks.OCHRE_FROGLIGHT);

        // 34 個物資箱點位
        c.loot(-10, 1, 2);  c.loot(10, 1, 2);   c.loot(-3, 1, 1);   c.loot(3, 1, 1);
        c.loot(-4, 1, 9);   c.loot(4, 1, 9);    c.loot(-10, 1, 7);  c.loot(10, 1, 7);
        c.loot(-10, 1, 23); c.loot(10, 1, 23);  c.loot(-2, 1, 23);  c.loot(2, 1, 23);
        c.loot(-9, 1, 14);  c.loot(9, 1, 14);   c.loot(-4, 1, 15);  c.loot(4, 1, 15);  c.loot(-10, 1, 18); c.loot(10, 1, 18);
        c.loot(-10, 8, 12); c.loot(10, 8, 12);  c.loot(-10, 8, 23); c.loot(10, 8, 23); c.loot(-3, 8, 24);  c.loot(3, 8, 24);
        c.loot(-10, 8, 7);  c.loot(10, 8, 7);   c.loot(-12, 8, 15); c.loot(12, 8, 15); c.loot(-6, 8, 22);  c.loot(6, 8, 22);
        c.loot(-8, 8, 18);  c.loot(8, 8, 18);   c.loot(0, 8, 22);   c.loot(0, 1, 7);
    }

    private static void edoStreet(BuildingCanvas c) {
        c.foundation(Blocks.COBBLESTONE);
        c.clear(-21, 0, 0, 21, 17, 34);

        // 43x35 青石古街區：中央 9 格巷戰石道 (x=-4..4)＋南北雙朱紅大鳥居＋木造攤架
        c.floor(-21, 0, 0, 21, 34, Blocks.COBBLESTONE);
        c.box(-4, 0, 0, 4, 0, 34, Blocks.STONE_BRICKS);
        for (int tz : new int[] { 1, 33 }) {
            c.pillar(-4, 1, 6, tz, Blocks.RED_CONCRETE);
            c.pillar(4, 1, 6, tz, Blocks.RED_CONCRETE);
            c.box(-5, 6, tz, 5, 6, tz, Blocks.RED_CONCRETE);
            c.box(-6, 7, tz, 6, 7, tz, Blocks.BLACK_CONCRETE);
        }
        // 中央街道攤販與石燈籠掩體
        for (int sz : new int[] { 8, 17, 26 }) {
            c.tallCover(-2, sz, 0, sz, 1, Blocks.OAK_PLANKS, Blocks.OAK_SLAB);
            c.box(-2, 4, sz, 0, 4, sz, Blocks.RED_WOOL);
            c.pillar(3, 1, 2, sz, Blocks.STONE_BRICK_WALL);
            c.set(3, 3, sz, Blocks.OCHRE_FROGLIGHT);
        }

        // 左右對峙 6 棟二層和風町家 (W: x=-20..-6, E: x=6..20；z段: 2..11, 13..22, 24..33)
        for (int[] seg : new int[][] { { 2, 11 }, { 13, 22 }, { 24, 33 } }) {
            int z0 = seg[0], z1 = seg[1];
            machiyaShop(c, -20, -6, z0, z1, true);
            machiyaShop(c, 6, 20, z0, z1, false);
        }

        // 2 樓雙座跨街空中木橋 (z=11..12, 22..23, y=6)＋木欄杆自動相連
        for (int bz : new int[] { 11, 22 }) {
            c.box(-6, 6, bz, 6, 6, bz + 1, Blocks.SPRUCE_PLANKS);
            c.windowX(-6, 7, bz, 6, 7, Blocks.DARK_OAK_FENCE);
            c.windowX(-6, 7, bz + 1, 6, 7, Blocks.DARK_OAK_FENCE);
            c.air(-5, 7, bz); c.air(-5, 7, bz + 1);
            c.air(5, 7, bz);  c.air(5, 7, bz + 1);
        }

        // 48 個物資箱點位 (每棟上下共 7 × 6 = 42 + 街道攤位與天橋 6 = 48)
        for (int z0 : new int[] { 2, 13, 24 }) {
            c.loot(-16, 1, z0 + 2); c.loot(-12, 1, z0 + 6); c.loot(-18, 1, z0 + 7);
            c.loot(-16, 7, z0 + 2); c.loot(-12, 7, z0 + 6); c.loot(-9, 7, z0 + 2);
            c.loot(16, 1, z0 + 2);  c.loot(12, 1, z0 + 6);  c.loot(18, 1, z0 + 7);
            c.loot(16, 7, z0 + 2);  c.loot(12, 7, z0 + 6);  c.loot(9, 7, z0 + 2);
            c.loot(-8, 1, z0 + 4);  c.loot(8, 1, z0 + 4);
        }
        c.loot(0, 1, 10); c.loot(0, 1, 20); c.loot(0, 1, 29); c.loot(0, 1, 4);
        c.loot(0, 7, 11); c.loot(0, 7, 22);
    }

    private static void machiyaShop(BuildingCanvas c, int x0, int x1, int z0, int z1, boolean westSide) {
        int streetWallX = westSide ? x1 : x0;
        int outerWallX = westSide ? x0 : x1;
        c.box(x0, 0, z0, x1, 0, z1, Blocks.SPRUCE_PLANKS);
        c.walls(x0, 1, z0, x1, 5, z1, Blocks.WHITE_CONCRETE);
        c.box(x0, 6, z0, x1, 6, z1, Blocks.SPRUCE_PLANKS);
        c.walls(x0, 7, z0, x1, 11, z1, Blocks.WHITE_CONCRETE);
        c.cornerPillars(x0, 1, z0, x1, 11, z1, Blocks.DARK_OAK_LOG);
        c.pagodaRoof(x0, 12, z0, x1, z1, 4, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES);

        int zMid = (z0 + z1) / 2;
        c.doorwaySide(streetWallX, 1, zMid - 1, 3);
        c.doorwaySide(outerWallX, 1, zMid - 1, 2);
        c.windowZ(streetWallX, 2, z0 + 1, z0 + 2, 3, Blocks.WHITE_STAINED_GLASS_PANE);
        c.windowZ(streetWallX, 8, z0 + 2, z1 - 2, 9, Blocks.WHITE_STAINED_GLASS_PANE);
        // 2 樓通往跨街木橋門洞
        if (z0 == 2 || z0 == 13) c.doorway(westSide ? x1 - 3 : x0 + 1, 7, z1, 2);
        if (z0 == 13 || z0 == 24) c.doorway(westSide ? x1 - 3 : x0 + 1, 7, z0, 2);

        // 室內掩體 (櫃檯、書架、茶几與沙發，無木箱)
        int midX = (x0 + x1) / 2;
        c.counter(midX - 2, z0 + 3, midX + 2, z0 + 3, 1, Blocks.DARK_OAK_PLANKS);
        c.tallCover(midX - 2, z1 - 2, midX + 1, z1 - 2, 7, Blocks.BOOKSHELF, Blocks.SPRUCE_SLAB);
        c.sofaX(midX - 1, midX + 1, 7, z0 + 2, Blocks.OAK_STAIRS, Direction.NORTH);

        // 寬 2 格樓梯靠外側牆面 0->6
        int stX0 = westSide ? x0 + 1 : x1 - 2;
        c.staircaseZ(stX0, stX0 + 1, 0, 6, z0 + 2, 1, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS);
        c.ceilingLights(x0 + 3, x1 - 3, 5, z0 + 3, z1 - 3, 6, 4, Blocks.OCHRE_FROGLIGHT);
        c.ceilingLights(x0 + 3, x1 - 3, 11, z0 + 3, z1 - 3, 6, 4, Blocks.OCHRE_FROGLIGHT);
    }
}
