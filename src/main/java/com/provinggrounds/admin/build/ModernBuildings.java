package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.MODERN;

/**
 * 檔案: ModernBuildings.java
 * 目的: 現代軍事與都市風格 3 棟大型／超大型建築（均無木箱、樓梯最後一階完整齊平、玻璃片／鐵欄／石牆自動連接）。
 */
final class ModernBuildings {

    private ModernBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("mod_tactical_hq", MODERN, "聯合作戰指揮中心",
            "3 層裝甲指揮大樓＋天台停機坪：1 樓軍械整備室與戰地急救區、2 樓雷達機房與簡報廳、3 樓長官辦公室與狙擊露台、雙寬樓梯與電梯井（30 個物資點）",
            31, 27, 22, 30, ModernBuildings::tacticalHq));

        out.accept(new BuildingBlueprint("mod_shopping_mall", MODERN, "都會大型購物中心",
            "3 層挑空中庭超大型商場：中庭綠植圓環、精品／3C／咖啡／超市多間獨立商鋪、2 樓美食街與書局迷宮、3 樓雙廳影城與採光天窗（36 個物資點）",
            37, 29, 19, 36, ModernBuildings::shoppingMall));

        out.accept(new BuildingBlueprint("mod_hospital", MODERN, "綜合戰地醫院大樓",
            "3 層大型現代醫院＋屋頂急救停機坪：1 樓掛號大廳／急診／藥局、2 樓中央護理站與 6 間獨立雙人病房、3 樓無菌手術室與 ICU 觀測長廊（28 個物資點）",
            29, 25, 19, 28, ModernBuildings::hospital));
    }

    private static void tacticalHq(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-15, 0, 0, 15, 22, 26);

        // 1 樓地面與前庭沙包掩體陣地 (lz = 0..2 防禦廣場, 主樓 lz = 3..26)
        c.floor(-15, 0, 0, 15, 26, Blocks.POLISHED_ANDESITE);
        c.box(-14, 0, 3, 14, 0, 26, Blocks.SMOOTH_STONE);
        c.box(-5, 0, 0, 5, 0, 2, Blocks.STONE_BRICKS);
        c.tallCover(-8, 1, -5, 1, 1, Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.tallCover(5, 1, 8, 1, 1, Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.box(-13, 1, 1, -11, 1, 1, Blocks.POLISHED_ANDESITE);
        c.box(11, 1, 1, 13, 1, 1, Blocks.POLISHED_ANDESITE);

        // 主樓 1..3 樓外牆與地板 (y=0, 6, 12, 18)
        c.walls(-14, 1, 3, 14, 5, 26, Blocks.GRAY_CONCRETE);
        c.box(-14, 6, 3, 14, 6, 26, Blocks.SMOOTH_STONE);
        c.walls(-14, 7, 3, 14, 11, 26, Blocks.WHITE_CONCRETE);
        c.box(-14, 12, 3, 14, 12, 26, Blocks.SMOOTH_STONE);
        c.walls(-14, 13, 3, 14, 17, 26, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-14, 18, 3, 14, 18, 26, Blocks.DEEPSLATE_TILES);

        // 結構柱與正面門廊雨棚
        c.cornerPillars(-14, 1, 3, 14, 18, 26, Blocks.DEEPSLATE_BRICKS);
        c.pillar(-5, 1, 18, 3, Blocks.DEEPSLATE_BRICKS);
        c.pillar(5, 1, 18, 3, Blocks.DEEPSLATE_BRICKS);
        c.pillar(-5, 1, 5, 1, Blocks.IRON_BLOCK);
        c.pillar(5, 1, 5, 1, Blocks.IRON_BLOCK);
        c.box(-6, 5, 0, 6, 5, 2, Blocks.SMOOTH_STONE_SLAB);

        // 四面大門與大片淡灰玻璃窗
        c.doorway(-1, 1, 3, 3);
        c.doubleDoor(-1, 1, 3, Blocks.IRON_DOOR);
        c.doorway(-1, 1, 26, 3);
        c.doorwaySide(-14, 1, 13, 3);
        c.doorwaySide(14, 1, 13, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-12, fy, 3, -6, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowX(6, fy, 3, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowX(-12, fy, 26, -6, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowX(6, fy, 26, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(-14, fy, 6, 11, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(-14, fy, 17, 23, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(14, fy, 6, 11, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(14, fy, 17, 23, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        }

        // 3 樓前方狙擊露台（內退至 z=6 形成露台掩體）
        c.box(-4, 13, 3, 4, 17, 3, Blocks.AIR);
        c.box(-4, 13, 3, 4, 13, 3, Blocks.STONE_BRICK_WALL);
        c.walls(-4, 13, 6, 4, 17, 6, Blocks.LIGHT_GRAY_CONCRETE);
        c.doorway(-1, 13, 6, 3);
        c.windowX(-3, 14, 6, -2, 16, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(2, 14, 6, 3, 16, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);

        // === 1 樓內裝：中庭沙盤＋安檢櫃檯＋西側軍械室＋東側急救室 ===
        c.counter(-3, 7, 3, 7, 1, Blocks.DEEPSLATE_BRICKS);
        c.counter(-2, 12, 2, 14, 1, Blocks.POLISHED_DEEPSLATE);
        c.box(-1, 2, 13, 1, 2, 13, Blocks.GREEN_CARPET);
        c.pillar(-4, 1, 18, 10, Blocks.DEEPSLATE_BRICKS);
        c.pillar(4, 1, 18, 10, Blocks.DEEPSLATE_BRICKS);
        c.pillar(-4, 1, 18, 17, Blocks.DEEPSLATE_BRICKS);
        c.pillar(4, 1, 18, 17, Blocks.DEEPSLATE_BRICKS);

        // 西側軍械整備室 (x=-13..-6, z=6..18)
        c.box(-6, 1, 5, -6, 5, 19, Blocks.DEEPSLATE_BRICKS);
        c.doorwaySide(-6, 1, 11, 3);
        c.windowZ(-6, 2, 6, 9, 4, Blocks.IRON_BARS);
        c.counter(-12, 6, -8, 6, 1, Blocks.ANVIL);
        c.counter(-12, 9, -9, 9, 1, Blocks.SMITHING_TABLE);
        c.tallCover(-11, 14, -8, 14, 1, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE_SLAB);
        c.serverRack(-12, 1, 17, 3, Blocks.BLAST_FURNACE, Blocks.IRON_BLOCK);

        // 東側戰地急救室 (x=6..13, z=6..18)
        c.box(6, 1, 5, 6, 5, 19, Blocks.WHITE_CONCRETE);
        c.doorwaySide(6, 1, 11, 3);
        c.windowZ(6, 2, 6, 9, 4, Blocks.CYAN_STAINED_GLASS_PANE);
        c.bedUnit(11, 6, 13, 7, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(11, 10, 13, 11, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(11, 15, 13, 16, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.tallCover(8, 8, 9, 8, 1, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ_SLAB);
        c.box(8, 1, 15, 9, 3, 15, Blocks.BOOKSHELF);

        // === 2 樓：挑空中庭玻璃護欄＋雷達機房＋作戰簡報廳 ===
        c.box(-3, 6, 10, 3, 6, 17, Blocks.AIR);
        c.box(-4, 7, 10, 4, 7, 17, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.box(-3, 7, 11, 3, 7, 16, Blocks.AIR);
        // 西側雷達伺服器機房
        c.box(-6, 7, 5, -6, 11, 18, Blocks.GRAY_CONCRETE);
        c.doorwaySide(-6, 7, 12, 3);
        for (int z : new int[] { 7, 10, 15 }) {
            c.serverRack(-12, 7, z, 3, Blocks.DEEPSLATE_TILES, Blocks.SEA_LANTERN);
            c.serverRack(-10, 7, z, 3, Blocks.OBSERVER, Blocks.BLAST_FURNACE);
            c.serverRack(-8, 7, z, 2, Blocks.IRON_BLOCK, Blocks.REDSTONE_LAMP);
        }
        // 東側作戰簡報廳
        c.box(6, 7, 5, 6, 11, 18, Blocks.WHITE_CONCRETE);
        c.doorwaySide(6, 7, 12, 3);
        c.counter(9, 8, 11, 14, 7, Blocks.DARK_OAK_PLANKS);
        c.sofaZ(8, 7, 9, 13, Blocks.DARK_OAK_STAIRS, Direction.EAST);
        c.sofaZ(12, 7, 9, 13, Blocks.DARK_OAK_STAIRS, Direction.WEST);
        c.tallCover(8, 16, 12, 16, 7, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_SLAB);

        // === 3 樓：指揮官辦公室＋戰術會議大廳 ===
        c.box(-3, 12, 10, 3, 12, 17, Blocks.AIR);
        c.box(-4, 13, 10, 4, 13, 17, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.box(-3, 13, 11, 3, 13, 16, Blocks.AIR);
        c.deskUnit(-10, 13, 7, Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.box(-12, 13, 10, -8, 15, 10, Blocks.BOOKSHELF);
        c.sofaX(-12, -8, 13, 13, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
        c.tallCover(-12, 16, -7, 16, 13, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.deskUnit(9, 13, 8, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(11, 13, 11, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.tallCover(7, 15, 12, 15, 13, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ_SLAB);

        // === 寬 2 格轉折樓梯（1F->2F->3F->天台）＋電梯直井 ===
        c.staircaseZ(-13, -12, 0, 6, 18, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-10, -9, 6, 12, 23, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-13, -12, 12, 18, 18, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        // 樓梯間玻璃護欄防止墜落
        c.windowZ(-11, 7, 18, 24, 7, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowZ(-11, 13, 17, 23, 13, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowZ(-11, 19, 18, 24, 19, Blocks.IRON_BARS);
        c.elevator(12, 22, 0, 6, 12, 18);

        // === 天台：白色 H 停機坪＋四角信號燈＋天線塔與裝甲小屋 ===
        c.parapet(-14, 19, 3, 14, 26, Blocks.DEEPSLATE_BRICK_WALL, true);
        // 停機坪框與 H 標線
        c.box(-4, 18, 8, 4, 18, 16, Blocks.GRAY_CONCRETE);
        c.box(-2, 18, 10, -2, 18, 14, Blocks.WHITE_CONCRETE);
        c.box(2, 18, 10, 2, 18, 14, Blocks.WHITE_CONCRETE);
        c.box(-1, 18, 12, 1, 18, 12, Blocks.WHITE_CONCRETE);
        c.set(-4, 19, 8, Blocks.SEA_LANTERN); c.set(4, 19, 8, Blocks.SEA_LANTERN);
        c.set(-4, 19, 16, Blocks.SEA_LANTERN); c.set(4, 19, 16, Blocks.SEA_LANTERN);
        // 天台裝甲掩體小屋與雷達塔
        c.walls(7, 19, 18, 13, 21, 24, Blocks.GRAY_CONCRETE);
        c.box(7, 22, 18, 13, 22, 24, Blocks.DEEPSLATE_TILES);
        c.doorwaySide(7, 19, 20, 2);
        c.pillar(10, 23, 26, 21, Blocks.IRON_BARS);
        c.set(10, 27, 21, Blocks.SEA_LANTERN);
        c.tallCover(-12, 6, -8, 6, 19, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(8, 6, 12, 6, 19, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 室內照明
        c.ceilingLights(-12, 12, 5, 5, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 11, 5, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 17, 7, 24, 6, 6, Blocks.SEA_LANTERN);

        // 30 個物資箱點位
        c.loot(-12, 1, 2);   c.loot(12, 1, 2);
        c.loot(-5, 1, 5);    c.loot(5, 1, 5);    c.loot(0, 1, 9);    c.loot(0, 1, 21);
        c.loot(-10, 1, 8);   c.loot(-10, 1, 15); c.loot(10, 1, 8);   c.loot(10, 1, 13);
        c.loot(-11, 7, 6);   c.loot(-7, 7, 13);  c.loot(10, 7, 7);   c.loot(10, 7, 15);
        c.loot(-5, 7, 8);    c.loot(5, 7, 8);    c.loot(-4, 7, 21);  c.loot(4, 7, 21);
        c.loot(-9, 13, 6);   c.loot(0, 13, 4);   c.loot(-3, 13, 4);  c.loot(9, 13, 7);
        c.loot(-8, 13, 14);  c.loot(8, 13, 14);  c.loot(0, 13, 21);
        c.loot(-10, 19, 5);  c.loot(10, 19, 5);  c.loot(10, 19, 21); c.loot(-6, 19, 21); c.loot(0, 19, 22);
    }

    private static void shoppingMall(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-18, 0, 0, 18, 19, 28);

        // 廣場階梯入口＋外牆＋3 層樓板 (y=0, 6, 12, 18)
        c.floor(-18, 0, 0, 18, 28, Blocks.POLISHED_ANDESITE);
        c.box(-17, 0, 3, 17, 0, 28, Blocks.QUARTZ_BLOCK);
        c.walls(-17, 1, 3, 17, 5, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 6, 3, 17, 6, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 7, 3, 17, 11, 28, Blocks.WHITE_CONCRETE);
        c.box(-17, 12, 3, 17, 12, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-17, 13, 3, 17, 17, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-17, 18, 3, 17, 18, 28, Blocks.GRAY_CONCRETE);
        c.cornerPillars(-17, 1, 3, 17, 18, 28, Blocks.GRAY_CONCRETE);

        // 前門大雨棚＋雙層玻璃幕牆＋多出入口
        c.pillar(-6, 1, 5, 1, Blocks.SMOOTH_QUARTZ);
        c.pillar(6, 1, 5, 1, Blocks.SMOOTH_QUARTZ);
        c.box(-7, 5, 0, 7, 5, 2, Blocks.ORANGE_CONCRETE);
        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-17, 1, 10, 3);
        c.doorwaySide(-17, 1, 20, 3);
        c.doorwaySide(17, 1, 10, 3);
        c.doorwaySide(17, 1, 20, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-15, fy, 3, -7, fy + 2, Blocks.GLASS_PANE);
            c.windowX(7, fy, 3, 15, fy + 2, Blocks.GLASS_PANE);
            c.windowX(-15, fy, 28, -5, fy + 2, Blocks.GLASS_PANE);
            c.windowX(5, fy, 28, 15, fy + 2, Blocks.GLASS_PANE);
            c.windowZ(-17, fy, 5, 9, fy + 2, Blocks.GLASS_PANE);
            c.windowZ(17, fy, 5, 9, fy + 2, Blocks.GLASS_PANE);
        }

        // 中央挑空中庭（2F、3F 與天窗開洞，z=10..20, x=-5..5）
        c.box(-5, 6, 10, 5, 6, 20, Blocks.AIR);
        c.box(-5, 12, 10, 5, 12, 20, Blocks.AIR);
        c.box(-6, 7, 9, 6, 7, 21, Blocks.GLASS_PANE);
        c.box(-5, 7, 10, 5, 7, 20, Blocks.AIR);
        c.box(-6, 13, 9, 6, 13, 21, Blocks.GLASS_PANE);
        c.box(-5, 13, 10, 5, 13, 20, Blocks.AIR);
        // 屋頂大面積採光天窗
        c.box(-6, 18, 9, 6, 18, 21, Blocks.LIGHT_BLUE_STAINED_GLASS);

        // === 1 樓：中庭花圃圓環＋四角大型獨立專櫃 ===
        c.planter(-3, 13, 3, 17, 1, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES);
        c.sofaX(-2, 2, 1, 11, Blocks.OAK_STAIRS, Direction.SOUTH);
        c.sofaX(-2, 2, 1, 19, Blocks.OAK_STAIRS, Direction.NORTH);
        // SW 精品服飾區
        c.box(-7, 1, 5, -7, 5, 13, Blocks.PINK_CONCRETE);
        c.doorwaySide(-7, 1, 8, 3);
        c.windowZ(-7, 2, 5, 7, 4, Blocks.GLASS_PANE);
        c.counter(-15, 7, -10, 7, 1, Blocks.QUARTZ_BLOCK);
        c.tallCover(-14, 11, -10, 11, 1, Blocks.OAK_PLANKS, Blocks.OAK_SLAB);
        // NW 3C 電器展售區
        c.box(-7, 1, 17, -7, 5, 26, Blocks.BLACK_CONCRETE);
        c.doorwaySide(-7, 1, 20, 3);
        c.counter(-15, 19, -10, 19, 1, Blocks.DEEPSLATE_TILES);
        c.serverRack(-15, 1, 24, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        c.serverRack(-12, 1, 24, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        // SE 咖啡廳＋卡座掩體
        c.box(7, 1, 5, 7, 5, 13, Blocks.SPRUCE_PLANKS);
        c.doorwaySide(7, 1, 8, 3);
        c.counter(9, 7, 14, 7, 1, Blocks.DARK_OAK_PLANKS);
        c.sofaX(10, 14, 1, 10, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        c.sofaX(10, 14, 1, 12, Blocks.SPRUCE_STAIRS, Direction.NORTH);
        // NE 超市貨架巷道
        c.box(7, 1, 17, 7, 5, 26, Blocks.LIGHT_BLUE_CONCRETE);
        c.doorwaySide(7, 1, 20, 3);
        c.tallCover(10, 19, 15, 19, 1, Blocks.BOOKSHELF, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(10, 22, 15, 22, 1, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ_SLAB);
        c.tallCover(10, 25, 15, 25, 1, Blocks.BOOKSHELF, Blocks.SMOOTH_STONE_SLAB);

        // === 2 樓：美食廣場＋西側書局迷宮＋東側休憩廳 ===
        c.counter(-5, 24, 5, 24, 7, Blocks.ORANGE_TERRACOTTA);
        c.deskUnit(-3, 7, 22, Blocks.SMOOTH_QUARTZ, Blocks.OAK_STAIRS, Direction.NORTH);
        c.deskUnit(3, 7, 22, Blocks.SMOOTH_QUARTZ, Blocks.OAK_STAIRS, Direction.NORTH);
        for (int z : new int[] { 7, 11, 15, 19 }) {
            c.box(-15, 7, z, -10, 8, z, Blocks.BOOKSHELF);
        }
        c.sofaZ(14, 7, 8, 12, Blocks.DARK_OAK_STAIRS, Direction.WEST);
        c.sofaZ(14, 7, 16, 20, Blocks.DARK_OAK_STAIRS, Direction.WEST);
        c.tallCover(9, 14, 11, 14, 7, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_SLAB);

        // === 3 樓：影城大廳＋雙放映廳高低階梯 ===
        c.counter(-4, 6, 4, 6, 13, Blocks.RED_CONCRETE);
        c.box(-7, 13, 8, -7, 17, 26, Blocks.BLACK_CONCRETE);
        c.box(7, 13, 8, 7, 17, 26, Blocks.BLACK_CONCRETE);
        c.doorwaySide(-7, 13, 14, 3);
        c.doorwaySide(7, 13, 14, 3);
        // 西／東兩間階梯座席放映廳
        for (int row = 0; row < 3; row++) {
            c.box(-15, 13 + row, 21 + row * 2, -9, 13 + row, 22 + row * 2, Blocks.RED_WOOL);
            c.box(9, 13 + row, 21 + row * 2, 15, 13 + row, 22 + row * 2, Blocks.BLUE_WOOL);
        }

        // === 對稱雙側寬 2 格樓梯 + 電梯井 ===
        c.staircaseZ(-16, -15, 0, 6, 12, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(15, 16, 0, 6, 12, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-16, -15, 6, 12, 22, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(15, 16, 6, 12, 22, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-5, -4, 12, 18, 22, 1, Blocks.QUARTZ_STAIRS, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_QUARTZ);
        c.elevator(0, 26, 0, 6, 12, 18);

        // 屋頂矮牆射擊掩體＋照明
        c.parapet(-17, 19, 3, 17, 28, Blocks.STONE_BRICK_WALL, true);
        c.tallCover(-12, 8, -9, 8, 19, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(9, 8, 12, 8, 19, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.ceilingLights(-15, 15, 5, 5, 26, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 11, 5, 26, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 17, 5, 26, 6, 7, Blocks.SEA_LANTERN);

        // 36 個物資箱點位
        c.loot(-12, 1, 9);  c.loot(-15, 1, 11); c.loot(-12, 1, 21); c.loot(-15, 1, 22);
        c.loot(12, 1, 9);   c.loot(15, 1, 8);   c.loot(12, 1, 21);  c.loot(15, 1, 23);
        c.loot(-4, 1, 5);   c.loot(4, 1, 5);    c.loot(-4, 1, 25);  c.loot(4, 1, 25);
        c.loot(-2, 7, 25);  c.loot(2, 7, 25);   c.loot(-8, 7, 25);  c.loot(8, 7, 25);
        c.loot(-12, 7, 9);  c.loot(-12, 7, 13); c.loot(-12, 7, 17); c.loot(-8, 7, 14);
        c.loot(11, 7, 10);  c.loot(11, 7, 18);  c.loot(8, 7, 14);   c.loot(0, 7, 6);
        c.loot(0, 13, 5);   c.loot(-4, 13, 7);  c.loot(4, 13, 7);   c.loot(0, 13, 24);
        c.loot(-12, 13, 12); c.loot(-12, 13, 18); c.loot(12, 13, 12); c.loot(12, 13, 18);
        c.loot(-12, 19, 10); c.loot(-12, 19, 20); c.loot(12, 19, 10); c.loot(12, 19, 20);
    }

    private static void hospital(BuildingCanvas c) {
        c.foundation(Blocks.STONE_BRICKS);
        c.clear(-14, 0, 0, 14, 19, 24);

        // 3 層白色醫院＋紅青醫療識別帶
        c.floor(-14, 0, 0, 14, 24, Blocks.SMOOTH_STONE);
        c.box(-13, 0, 3, 13, 0, 24, Blocks.WHITE_CONCRETE);
        c.walls(-13, 1, 3, 13, 5, 24, Blocks.WHITE_CONCRETE);
        c.box(-13, 6, 3, 13, 6, 24, Blocks.SMOOTH_QUARTZ);
        c.walls(-13, 7, 3, 13, 11, 24, Blocks.WHITE_CONCRETE);
        c.box(-13, 12, 3, 13, 12, 24, Blocks.SMOOTH_QUARTZ);
        c.walls(-13, 13, 3, 13, 17, 24, Blocks.WHITE_CONCRETE);
        c.box(-13, 18, 3, 13, 18, 24, Blocks.LIGHT_GRAY_CONCRETE);
        c.cornerPillars(-13, 1, 3, 13, 18, 24, Blocks.CYAN_TERRACOTTA);

        // 急診雨棚＋正面醒目紅十字
        c.pillar(-4, 1, 5, 0, Blocks.WHITE_CONCRETE);
        c.pillar(4, 1, 5, 0, Blocks.WHITE_CONCRETE);
        c.box(-5, 5, 0, 5, 5, 2, Blocks.RED_CONCRETE);
        c.box(-1, 14, 3, 1, 16, 3, Blocks.RED_CONCRETE);
        c.set(0, 15, 3, Blocks.RED_CONCRETE);
        c.set(-1, 14, 3, Blocks.WHITE_CONCRETE); c.set(1, 14, 3, Blocks.WHITE_CONCRETE);
        c.set(-1, 16, 3, Blocks.WHITE_CONCRETE); c.set(1, 16, 3, Blocks.WHITE_CONCRETE);

        // 四面大門與青色玻璃片幕牆
        c.doorway(-1, 1, 3, 3);
        c.doubleDoor(-1, 1, 3, Blocks.IRON_DOOR);
        c.doorway(-1, 1, 24, 3);
        c.doorwaySide(-13, 1, 12, 3);
        c.doorwaySide(13, 1, 12, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-11, fy, 3, -5, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(5, fy, 3, 11, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-13, fy, 5, 10, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-13, fy, 16, 22, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(13, fy, 5, 10, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(13, fy, 16, 22, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // === 1 樓：中央掛號櫃檯＋候診區＋急診區＋藥局 ===
        c.counter(-3, 7, 3, 8, 1, Blocks.CYAN_CONCRETE);
        c.sofaX(-4, 4, 1, 5, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        // 西翼急診病床與隔屏
        c.box(-6, 1, 5, -6, 5, 17, Blocks.WHITE_CONCRETE);
        c.doorwaySide(-6, 1, 10, 3);
        c.bedUnit(-12, 5, -10, 6, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(-12, 9, -10, 10, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.bedUnit(-12, 14, -10, 15, 1, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        c.tallCover(-8, 7, -8, 14, 1, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ_SLAB);
        // 東翼藥局配藥櫃檯
        c.box(6, 1, 5, 6, 5, 17, Blocks.WHITE_CONCRETE);
        c.doorwaySide(6, 1, 10, 3);
        c.windowZ(6, 2, 6, 8, 4, Blocks.GLASS_PANE);
        c.box(11, 1, 6, 12, 3, 15, Blocks.BOOKSHELF);
        c.counter(8, 7, 9, 13, 1, Blocks.QUARTZ_BLOCK);

        // === 2 樓：中央護理站＋左右共 6 間雙人病房 ===
        c.counter(-2, 12, 2, 14, 7, Blocks.CYAN_CONCRETE);
        for (int zBase : new int[] { 5, 11, 17 }) {
            // 西側病房 3 間
            c.box(-12, 7, zBase + 4, -5, 11, zBase + 4, Blocks.WHITE_CONCRETE);
            c.box(-5, 7, zBase, -5, 11, zBase + 4, Blocks.WHITE_CONCRETE);
            c.doorwaySide(-5, 7, zBase + 1, 2);
            c.bedUnit(-12, zBase + 1, -10, zBase + 1, 7, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL);
            c.bedUnit(-12, zBase + 3, -10, zBase + 3, 7, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL);
            // 東側病房 3 間
            c.box(5, 7, zBase + 4, 12, 11, zBase + 4, Blocks.WHITE_CONCRETE);
            c.box(5, 7, zBase, 5, 11, zBase + 4, Blocks.WHITE_CONCRETE);
            c.doorwaySide(5, 7, zBase + 1, 2);
            c.bedUnit(10, zBase + 1, 12, zBase + 1, 7, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL);
            c.bedUnit(10, zBase + 3, 12, zBase + 3, 7, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL);
        }

        // === 3 樓：兩座無菌手術室＋ICU 機櫃長廊 ===
        c.box(-5, 13, 6, -5, 17, 17, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(5, 13, 6, 5, 17, 17, Blocks.CYAN_STAINED_GLASS_PANE);
        c.doorwaySide(-5, 13, 10, 3);
        c.doorwaySide(5, 13, 10, 3);
        c.counter(-11, 9, -8, 12, 13, Blocks.IRON_BLOCK);
        c.counter(8, 9, 11, 12, 13, Blocks.IRON_BLOCK);
        c.serverRack(-12, 13, 15, 3, Blocks.QUARTZ_BLOCK, Blocks.SEA_LANTERN);
        c.serverRack(12, 13, 15, 3, Blocks.QUARTZ_BLOCK, Blocks.SEA_LANTERN);

        // === 寬 2 格樓梯＋電梯 ===
        c.staircaseZ(-12, -11, 0, 6, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(11, 12, 6, 12, 23, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        c.staircaseZ(-12, -11, 12, 18, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.LIGHT_GRAY_CONCRETE, Blocks.QUARTZ_BLOCK);
        c.windowZ(-10, 7, 17, 23, 7, Blocks.CYAN_STAINED_GLASS_PANE);
        c.windowZ(-10, 19, 17, 23, 19, Blocks.STONE_BRICK_WALL);
        c.elevator(0, 22, 0, 6, 12, 18);

        // 天台紅十字停機坪與女兒牆
        c.parapet(-13, 19, 3, 13, 24, Blocks.STONE_BRICK_WALL, true);
        c.box(-4, 18, 8, 4, 18, 16, Blocks.WHITE_CONCRETE);
        c.box(-1, 18, 9, 1, 18, 15, Blocks.RED_CONCRETE);
        c.box(-3, 18, 11, 3, 18, 13, Blocks.RED_CONCRETE);
        c.set(-4, 19, 8, Blocks.SEA_LANTERN); c.set(4, 19, 8, Blocks.SEA_LANTERN);
        c.set(-4, 19, 16, Blocks.SEA_LANTERN); c.set(4, 19, 16, Blocks.SEA_LANTERN);

        c.ceilingLights(-11, 11, 5, 5, 22, 5, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-11, 11, 11, 5, 22, 5, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-11, 11, 17, 5, 22, 5, 6, Blocks.SEA_LANTERN);

        // 28 個物資箱點位
        c.loot(-3, 1, 9);  c.loot(3, 1, 9);   c.loot(0, 1, 20);
        c.loot(-9, 1, 5);  c.loot(-9, 1, 12); c.loot(-11, 1, 19); c.loot(9, 1, 5);  c.loot(9, 1, 15);
        c.loot(0, 7, 15);  c.loot(0, 7, 9);   c.loot(-3, 7, 11);  c.loot(3, 7, 11);
        c.loot(-8, 7, 7);  c.loot(-8, 7, 13); c.loot(-8, 7, 19);  c.loot(8, 7, 7);  c.loot(8, 7, 13);  c.loot(8, 7, 19);
        c.loot(-9, 13, 7); c.loot(9, 13, 7);  c.loot(-9, 13, 16); c.loot(9, 13, 16); c.loot(0, 13, 9);  c.loot(0, 13, 15);
        c.loot(-8, 19, 6); c.loot(8, 19, 6);  c.loot(-8, 19, 18); c.loot(8, 19, 18);
    }
}
