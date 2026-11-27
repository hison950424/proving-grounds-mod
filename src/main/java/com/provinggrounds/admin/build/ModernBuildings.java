package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.MODERN;

/**
 * 檔案: ModernBuildings.java
 * 目的: 現代軍事與都市風格 7 棟大型／超大型建築（均無木箱、無電梯、樓梯最後一階完整齊平、玻璃片／鐵欄／石牆自動連接、物資點緊貼掩體）。
 */
final class ModernBuildings {

    private ModernBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("mod_tactical_hq", MODERN, "聯合作戰指揮中心",
            "3 層裝甲指揮大樓＋天台停機坪：1 樓軍械整備室與戰地急救區、2 樓雷達機房與簡報廳、3 樓長官辦公室與狙擊露台、雙寬樓梯與單座電梯（36 個物資點）",
            31, 27, 22, 36, ModernBuildings::tacticalHq));

        out.accept(new BuildingBlueprint("mod_shopping_mall", MODERN, "都會大型購物中心",
            "3 層挑空中庭超大型商場：中庭綠植圓環、精品／3C／咖啡／超市多間獨立商鋪、2 樓美食街與書局迷宮、3 樓雙廳影城與採光天窗（42 個物資點）",
            37, 29, 19, 42, ModernBuildings::shoppingMall));

        out.accept(new BuildingBlueprint("mod_hospital", MODERN, "綜合戰地醫院大樓",
            "3 層大型現代醫院＋屋頂急救停機坪：1 樓掛號大廳／急診／藥局、2 樓中央護理站與 6 間獨立雙人病房、3 樓無菌手術室與 ICU 觀測長廊（36 個物資點）",
            29, 25, 19, 36, ModernBuildings::hospital));

        out.accept(new BuildingBlueprint("mod_prison_fortress", MODERN, "高戒備聯邦監獄要塞",
            "45×35 超大型要塞監獄：放風運動操場與沙包哨口、東西兩翼雙層共 12 間鐵柵牢房、中央 3 層圓形監視塔、大型犯人食堂與天台四角碉堡（48 個物資點）",
            45, 35, 20, 48, ModernBuildings::prisonFortress));

        out.accept(new BuildingBlueprint("mod_fire_station", MODERN, "消防救災指揮分隊",
            "35×29 兩層半消防總部：1 樓雙寬車庫停放 2 台實體紅色消防車、裝備間與調度櫃檯，2 樓雙人消防員寢室／餐廳／體能訓練房，3 樓屋頂通訊瞭望台（38 個物資點）",
            35, 29, 18, 38, ModernBuildings::fireStation));

        out.accept(new BuildingBlueprint("mod_police_hq", MODERN, "都會特警局與靶場",
            "37×31 兩層半特警局：1 樓報案大廳、東翼 4 間鐵柵拘留室、西翼隔板室內射擊靶場，2 樓戰術簡報室與證物伺服器機房，天台裝甲掩體與雙寬樓梯（42 個物資點）",
            37, 31, 19, 42, ModernBuildings::policeHq));

        out.accept(new BuildingBlueprint("mod_news_studio", MODERN, "電視廣播新聞大樓",
            "33×29 3 層媒體中心：1 樓挑空大型新聞攝影棚（綠幕牆＋主播台＋燈架掩體），2 樓環形導播玻璃機房與剪輯室，3 樓新聞編輯部與屋頂衛星轉播陣列（38 個物資點）",
            33, 29, 19, 38, ModernBuildings::newsStudio));
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

        c.bunkerCoverL(-8, 1, 20, 2, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(8, 1, 20, -2, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(-5, 7, 19, 2, 1, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(5, 7, 19, -2, 1, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);

        // 36 個物資箱點位（貼近牆面／櫃檯／掩體）
        c.loot(-12, 1, 2);   c.loot(12, 1, 2);
        c.loot(-5, 1, 5);    c.loot(5, 1, 5);    c.loot(-3, 1, 8);   c.loot(-5, 1, 21);
        c.loot(-10, 1, 8);   c.loot(-10, 1, 15); c.loot(10, 1, 8);   c.loot(10, 1, 13);
        c.loot(-8, 1, 21);   c.loot(8, 1, 21);
        c.loot(-11, 7, 6);   c.loot(-7, 7, 13);  c.loot(10, 7, 7);   c.loot(10, 7, 15);
        c.loot(-5, 7, 8);    c.loot(5, 7, 8);    c.loot(-6, 7, 20);  c.loot(6, 7, 20);
        c.loot(-5, 7, 18);   c.loot(5, 7, 18);
        c.loot(-9, 13, 6);   c.loot(0, 13, 4);   c.loot(-3, 13, 4);  c.loot(9, 13, 7);
        c.loot(-8, 13, 14);  c.loot(8, 13, 14);  c.loot(-4, 13, 17); c.loot(-7, 13, 17); c.loot(7, 13, 17);
        c.loot(-10, 19, 5);  c.loot(10, 19, 5);  c.loot(10, 19, 25); c.loot(-6, 19, 21); c.loot(0, 19, 25);
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
        // 取消電梯，改以寬雙樓梯與天台掩體提升交火節奏
        c.parapet(-17, 19, 3, 17, 28, Blocks.STONE_BRICK_WALL, true);
        c.tallCover(-12, 8, -9, 8, 19, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(9, 8, 12, 8, 19, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.ceilingLights(-15, 15, 5, 5, 26, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 11, 5, 26, 6, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-15, 15, 17, 5, 26, 6, 7, Blocks.SEA_LANTERN);
        c.bunkerCoverL(-4, 1, 24, 2, -2, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(4, 1, 24, -2, -2, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_STONE_SLAB);

        // 42 個物資箱點位（貼近牆面／貨架／專櫃）
        c.loot(-8, 1, 9);   c.loot(-15, 1, 11); c.loot(-12, 1, 20); c.loot(-15, 1, 22);
        c.loot(8, 1, 9);    c.loot(15, 1, 8);   c.loot(12, 1, 20);  c.loot(15, 1, 23);
        c.loot(-6, 1, 5);   c.loot(6, 1, 5);    c.loot(-4, 1, 25);  c.loot(4, 1, 25);
        c.loot(-3, 1, 12);  c.loot(3, 1, 18);
        c.loot(-2, 7, 25);  c.loot(2, 7, 25);   c.loot(-5, 7, 25);  c.loot(5, 7, 25);
        c.loot(-12, 7, 8);  c.loot(-12, 7, 12); c.loot(-12, 7, 16); c.loot(-7, 7, 14);
        c.loot(13, 7, 10);  c.loot(13, 7, 18);  c.loot(7, 7, 14);   c.loot(-3, 7, 23);
        c.loot(-9, 7, 7);   c.loot(9, 7, 7);
        c.loot(0, 13, 5);   c.loot(-4, 13, 7);  c.loot(4, 13, 7);   c.loot(-4, 13, 23);
        c.loot(-8, 13, 12); c.loot(-8, 13, 18); c.loot(8, 13, 12);  c.loot(8, 13, 18);
        c.loot(-9, 13, 10); c.loot(9, 13, 10);
        c.loot(-12, 19, 9); c.loot(-16, 19, 20); c.loot(12, 19, 9); c.loot(16, 19, 20);
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
        // 天台紅十字停機坪與女兒牆（無電梯）
        c.parapet(-13, 19, 3, 13, 24, Blocks.STONE_BRICK_WALL, true);
        c.box(-4, 18, 8, 4, 18, 16, Blocks.WHITE_CONCRETE);
        c.box(-1, 18, 9, 1, 18, 15, Blocks.RED_CONCRETE);
        c.box(-3, 18, 11, 3, 18, 13, Blocks.RED_CONCRETE);
        c.set(-4, 19, 8, Blocks.SEA_LANTERN); c.set(4, 19, 8, Blocks.SEA_LANTERN);
        c.set(-4, 19, 16, Blocks.SEA_LANTERN); c.set(4, 19, 16, Blocks.SEA_LANTERN);
        c.bunkerCoverL(-8, 19, 12, 2, 2, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(8, 19, 12, -2, 2, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);

        c.ceilingLights(-11, 11, 5, 5, 22, 5, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-11, 11, 11, 5, 22, 5, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-11, 11, 17, 5, 22, 5, 6, Blocks.SEA_LANTERN);

        // 36 個物資箱點位（貼近櫃檯／隔屏／牆邊）
        c.loot(-3, 1, 9);  c.loot(3, 1, 9);   c.loot(-5, 1, 20); c.loot(-5, 1, 14);
        c.loot(-9, 1, 5);  c.loot(-9, 1, 12); c.loot(-12, 1, 19); c.loot(10, 1, 5); c.loot(10, 1, 15);
        c.loot(-6, 1, 18); c.loot(6, 1, 18);
        c.loot(-3, 7, 14); c.loot(-4, 7, 8);  c.loot(-3, 7, 11);  c.loot(3, 7, 11); c.loot(-4, 7, 21);
        c.loot(-9, 7, 7);  c.loot(-9, 7, 13); c.loot(-9, 7, 19);  c.loot(9, 7, 7);  c.loot(9, 7, 13); c.loot(9, 7, 19);
        c.loot(-7, 13, 7); c.loot(7, 13, 7);  c.loot(-9, 13, 16); c.loot(9, 13, 16); c.loot(-4, 13, 9); c.loot(-4, 13, 15);
        c.loot(-3, 13, 23); c.loot(3, 13, 23);
        c.loot(-8, 19, 4); c.loot(8, 19, 4);  c.loot(-8, 19, 23); c.loot(8, 19, 23);
        c.loot(0, 19, 4);  c.loot(0, 19, 23);
    }

    private static void prisonFortress(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-22, 0, 0, 22, 20, 34);

        // 45x35 監獄圍牆＋前庭放風操場 (z=0..11)＋兩層牢房主堡 (z=12..33)
        c.floor(-22, 0, 0, 22, 34, Blocks.POLISHED_ANDESITE);
        c.walls(-22, 1, 0, 22, 5, 34, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-22, 6, 0, 22, 34, Blocks.IRON_BARS, false);
        c.doorway(-2, 1, 0, 5);
        c.doorway(-2, 1, 34, 5);
        c.doorwaySide(-22, 1, 6, 3);
        c.doorwaySide(22, 1, 6, 3);

        // 放風操場掩體＋籃球半場＋哨亭
        c.box(7, 0, 2, 19, 0, 10, Blocks.TERRACOTTA);
        c.pillar(18, 1, 4, 6, Blocks.IRON_BLOCK);
        c.set(17, 4, 6, Blocks.ORANGE_CONCRETE);
        c.bunkerCoverL(-16, 1, 5, 3, 3, Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB);
        c.bunkerCoverL(5, 1, 4, 2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.walls(-20, 1, 8, -14, 4, 11, Blocks.GRAY_CONCRETE);
        c.box(-20, 5, 8, -14, 5, 11, Blocks.DEEPSLATE_TILES);
        c.doorway(-18, 1, 8, 2);

        // 主樓結構 (x=-20..20, z=12..33; 樓板 y=0, 6, 12, 17)
        c.box(-20, 0, 12, 20, 0, 33, Blocks.SMOOTH_STONE);
        c.walls(-20, 1, 12, 20, 5, 33, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-20, 6, 12, 20, 6, 33, Blocks.POLISHED_ANDESITE);
        c.walls(-20, 7, 12, 20, 11, 33, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-20, 12, 12, 20, 12, 33, Blocks.POLISHED_ANDESITE);
        c.walls(-20, 13, 12, 20, 16, 33, Blocks.GRAY_CONCRETE);
        c.box(-20, 17, 12, 20, 17, 33, Blocks.DEEPSLATE_TILES);
        c.cornerPillars(-20, 1, 12, 20, 17, 33, Blocks.DEEPSLATE_BRICKS);

        // 主樓大門與鐵柵高窗
        c.doorway(-2, 1, 12, 5);
        c.doubleDoor(-1, 1, 12, Blocks.IRON_DOOR);
        c.doorway(-2, 1, 33, 5);
        c.doorwaySide(-20, 1, 22, 3);
        c.doorwaySide(20, 1, 22, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-17, fy, 12, -7, fy + 2, Blocks.IRON_BARS);
            c.windowX(7, fy, 12, 17, fy + 2, Blocks.IRON_BARS);
            c.windowZ(-20, fy, 14, 20, fy + 1, Blocks.IRON_BARS);
            c.windowZ(20, fy, 14, 20, fy + 1, Blocks.IRON_BARS);
        }

        // 中央挑空監視大廳 (x=-6..6, z=14..23)＋3 層圓形監視塔底座
        c.box(-5, 6, 14, 5, 11, 23, Blocks.AIR);
        c.box(-6, 7, 13, 6, 7, 24, Blocks.IRON_BARS);
        c.box(-5, 7, 14, 5, 7, 23, Blocks.AIR);
        c.walls(-2, 1, 17, 2, 11, 21, Blocks.DEEPSLATE_BRICKS);
        c.doorway(-1, 1, 17, 2);
        c.windowX(-1, 8, 17, 1, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(-1, 8, 21, 1, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);

        // 1F & 2F 東西兩翼共 12 間鐵柵牢房 (zBase = 13, 17, 21)
        for (int fy : new int[] { 1, 7 }) {
            for (int zBase : new int[] { 13, 17, 21 }) {
                c.box(-19, fy, zBase + 3, -11, fy + 4, zBase + 3, Blocks.STONE_BRICKS);
                c.box(-11, fy, zBase, -11, fy + 4, zBase + 3, Blocks.IRON_BARS);
                c.doorwaySide(-11, fy, zBase + 1, 2);
                c.bedUnit(-19, zBase + 1, -17, zBase + 1, fy, Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL);

                c.box(11, fy, zBase + 3, 19, fy + 4, zBase + 3, Blocks.STONE_BRICKS);
                c.box(11, fy, zBase, 11, fy + 4, zBase + 3, Blocks.IRON_BARS);
                c.doorwaySide(11, fy, zBase + 1, 2);
                c.bedUnit(17, zBase + 1, 19, zBase + 1, fy, Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL);
            }
        }

        // 1F 北側犯人食堂 (z=26..32)＋2F 軍械洗衣房 (z=26..32)
        c.box(-18, 1, 25, 18, 5, 25, Blocks.GRAY_CONCRETE);
        c.doorway(-1, 1, 25, 3);
        c.doorway(-13, 1, 25, 2);
        c.doorway(12, 1, 25, 2);
        for (int x : new int[] { -14, -7, 7, 14 }) {
            c.counter(x - 1, 27, x + 1, 30, 1, Blocks.SMOOTH_STONE);
            c.sofaZ(x - 2, 1, 27, 30, Blocks.OAK_STAIRS, Direction.EAST);
        }
        c.counter(-5, 31, 5, 31, 1, Blocks.IRON_BLOCK);
        // 2F 北側警備裝備庫
        c.box(-18, 7, 25, 18, 11, 25, Blocks.GRAY_CONCRETE);
        c.doorway(-1, 7, 25, 3);
        c.tallCover(-15, 28, -9, 28, 7, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(9, 28, 15, 28, 7, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.serverRack(-4, 7, 30, 3, Blocks.BLAST_FURNACE, Blocks.SEA_LANTERN);
        c.serverRack(4, 7, 30, 3, Blocks.BLAST_FURNACE, Blocks.SEA_LANTERN);

        // 3F 警衛長官層 (y=13..16)
        c.bunkerCoverL(-14, 13, 17, 3, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(14, 13, 17, -3, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.deskUnit(-12, 13, 28, Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.deskUnit(12, 13, 28, Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.box(-4, 13, 28, 4, 15, 28, Blocks.BOOKSHELF);

        // 寬 2 格樓梯 (無電梯)：0->6->12->17
        c.staircaseZ(-9, -8, 0, 6, 15, 1, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
        c.staircaseZ(8, 9, 0, 6, 15, 1, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
        c.staircaseZ(-9, -8, 6, 12, 22, -1, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
        c.staircaseZ(8, 9, 6, 12, 22, -1, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
        c.staircaseZ(-2, -1, 12, 17, 20, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);

        // 屋頂女兒牆與四角碉堡哨塔 (y=17..20)
        c.parapet(-20, 18, 12, 20, 33, Blocks.DEEPSLATE_BRICK_WALL, true);
        for (int[] corner : new int[][] { { -19, 13 }, { 15, 13 }, { -19, 28 }, { 15, 28 } }) {
            c.walls(corner[0], 18, corner[1], corner[0] + 4, 19, corner[1] + 4, Blocks.GRAY_CONCRETE);
            c.box(corner[0], 20, corner[1], corner[0] + 4, 20, corner[1] + 4, Blocks.DEEPSLATE_BRICKS);
            c.doorway(corner[0] + 1, 18, corner[1], 2);
            c.doorway(corner[0] + 1, 18, corner[1] + 4, 2);
        }

        c.ceilingLights(-18, 18, 5, 14, 31, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-18, 18, 11, 14, 31, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-18, 18, 16, 14, 31, 6, 6, Blocks.SEA_LANTERN);

        // 48 個物資箱點位（貼近牢門柵欄／監視塔／長桌與掩體）
        c.loot(-17, 1, 7);  c.loot(-13, 1, 5); c.loot(12, 1, 6);   c.loot(4, 1, 6);
        c.loot(-12, 1, 14); c.loot(-12, 1, 18); c.loot(-12, 1, 22);
        c.loot(12, 1, 14);  c.loot(12, 1, 18);  c.loot(12, 1, 22);
        c.loot(-5, 1, 15);  c.loot(5, 1, 15);   c.loot(-10, 1, 28); c.loot(0, 1, 30);   c.loot(10, 1, 28); c.loot(-3, 1, 19);
        c.loot(-12, 7, 14); c.loot(-12, 7, 18); c.loot(-12, 7, 22);
        c.loot(12, 7, 14);  c.loot(12, 7, 18);  c.loot(12, 7, 22);
        c.loot(-12, 7, 29); c.loot(12, 7, 29);  c.loot(0, 7, 26);   c.loot(-3, 7, 19);  c.loot(3, 7, 19);
        c.loot(-16, 13, 15); c.loot(16, 13, 15); c.loot(-16, 13, 21); c.loot(16, 13, 21);
        c.loot(-14, 13, 29); c.loot(14, 13, 29); c.loot(-6, 13, 19); c.loot(6, 13, 19);
        c.loot(-3, 13, 27); c.loot(3, 13, 27);  c.loot(-8, 13, 29); c.loot(8, 13, 29);
        c.loot(-18, 18, 15); c.loot(18, 18, 15); c.loot(-18, 18, 30); c.loot(18, 18, 30);
        c.loot(-4, 18, 13); c.loot(-10, 18, 22); c.loot(10, 18, 22); c.loot(-6, 18, 32); c.loot(6, 18, 32);
    }

    private static void fireStation(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-17, 0, 0, 17, 18, 28);

        c.floor(-17, 0, 0, 17, 28, Blocks.POLISHED_ANDESITE);
        c.box(-16, 0, 4, 16, 0, 28, Blocks.SMOOTH_STONE);
        c.walls(-16, 1, 4, 16, 5, 28, Blocks.BRICKS);
        c.box(-16, 6, 4, 16, 6, 28, Blocks.SMOOTH_STONE);
        c.walls(-16, 7, 4, 16, 11, 28, Blocks.WHITE_CONCRETE);
        c.box(-16, 12, 4, 16, 12, 28, Blocks.DEEPSLATE_TILES);
        c.parapet(-16, 13, 4, 16, 28, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-16, 1, 4, 16, 12, 28, Blocks.DEEPSLATE_BRICKS);

        // 雙寬消防車出庫大門 + 四面側後門 + 窗
        c.doorway(-12, 1, 4, 5);
        c.doorway(-4, 1, 4, 5);
        c.doorway(8, 1, 4, 3);
        c.doorway(-1, 1, 28, 4);
        c.doorwaySide(-16, 1, 16, 3);
        c.doorwaySide(16, 1, 16, 3);
        c.windowX(-14, 8, 4, -5, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(5, 8, 4, 14, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowZ(-16, 8, 7, 13, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowZ(16, 8, 7, 13, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.tallCover(5, 1, 7, 1, 1, Blocks.RED_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.tallCover(11, 1, 13, 1, 1, Blocks.RED_CONCRETE, Blocks.STONE_BRICK_SLAB);

        // 1F：西區消防車庫（2 台實體紅色消防車）＋東區調度台與裝備間
        c.parkedCar(-10, 1, 12, Blocks.RED_CONCRETE, true);
        c.parkedCar(-2, 1, 12, Blocks.RED_CONCRETE, true);
        c.box(4, 1, 5, 4, 5, 22, Blocks.GRAY_CONCRETE);
        c.doorwaySide(4, 1, 10, 3);
        c.doorwaySide(4, 1, 18, 3);
        c.counter(6, 7, 13, 7, 1, Blocks.RED_CONCRETE);
        c.serverRack(14, 1, 10, 3, Blocks.BLAST_FURNACE, Blocks.SEA_LANTERN);
        c.serverRack(14, 1, 14, 3, Blocks.ANVIL, Blocks.IRON_BLOCK);
        c.tallCover(7, 15, 12, 15, 1, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(-12, 1, 23, 2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(12, 1, 23, -2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 2F：消防員雙人寢室／餐廳＋體能訓練室
        c.box(0, 7, 6, 0, 11, 23, Blocks.WHITE_CONCRETE);
        c.doorwaySide(0, 7, 11, 3);
        c.doorwaySide(0, 7, 19, 3);
        for (int z : new int[] { 7, 11, 15 }) {
            c.bedUnit(-14, z, -12, z + 1, 7, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        }
        c.counter(4, 8, 10, 8, 7, Blocks.DARK_OAK_PLANKS);
        c.sofaX(4, 10, 7, 10, Blocks.OAK_STAIRS, Direction.NORTH);
        c.tallCover(3, 17, 9, 17, 7, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE_SLAB);

        // 天台通訊瞭望台（y=13..18）
        c.walls(-4, 13, 18, 4, 16, 25, Blocks.BRICKS);
        c.box(-4, 17, 18, 4, 17, 25, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-4, 18, 18, 4, 25, Blocks.IRON_BARS, false);
        c.doorway(-1, 13, 18, 2);
        c.doorway(-1, 13, 25, 2);
        c.tallCover(-12, 9, -8, 9, 13, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(8, 9, 12, 9, 13, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 雙寬 2 格樓梯 (無電梯)：0->6->12 + 屋頂外梯 12->17
        c.staircaseZ(-15, -14, 0, 6, 19, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(14, 15, 0, 6, 19, 1, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS);
        c.staircaseZ(-15, -14, 6, 12, 23, -1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);
        c.staircaseZ(2, 3, 12, 17, 19, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);

        c.ceilingLights(-13, 13, 5, 6, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-13, 13, 11, 6, 26, 6, 6, Blocks.SEA_LANTERN);

        // 38 個物資箱點位（全部貼近車體／牆面／床鋪／掩體）
        c.loot(-14, 1, 6);  c.loot(14, 1, 6);  c.loot(-12, 1, 12); c.loot(-7, 1, 12);
        c.loot(2, 1, 12);   c.loot(-10, 1, 16); c.loot(-2, 1, 16); c.loot(6, 1, 8);
        c.loot(13, 1, 11);  c.loot(13, 1, 15); c.loot(10, 1, 21);  c.loot(-11, 1, 22);
        c.loot(11, 1, 22);  c.loot(-6, 1, 27); c.loot(6, 1, 27);   c.loot(6, 1, 2);
        c.loot(12, 1, 2);
        c.loot(-11, 7, 7);  c.loot(-11, 7, 12); c.loot(-11, 7, 16); c.loot(-2, 7, 8);
        c.loot(-2, 7, 21);  c.loot(4, 7, 7);    c.loot(11, 7, 8);   c.loot(7, 7, 16);
        c.loot(14, 7, 12);  c.loot(14, 7, 25);  c.loot(-9, 7, 26);  c.loot(3, 7, 26);
        c.loot(-13, 13, 6); c.loot(13, 13, 6);  c.loot(-10, 13, 10); c.loot(10, 13, 10);
        c.loot(-14, 13, 25); c.loot(14, 13, 25); c.loot(-3, 13, 20); c.loot(3, 13, 20);
        c.loot(0, 18, 22);
    }

    private static void policeHq(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-18, 0, 0, 18, 19, 30);

        c.floor(-18, 0, 0, 18, 30, Blocks.POLISHED_ANDESITE);
        c.box(-17, 0, 3, 17, 0, 30, Blocks.SMOOTH_STONE);
        c.walls(-17, 1, 3, 17, 5, 30, Blocks.GRAY_CONCRETE);
        c.box(-17, 6, 3, 17, 6, 30, Blocks.SMOOTH_STONE);
        c.walls(-17, 7, 3, 17, 11, 30, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-17, 12, 3, 17, 12, 30, Blocks.DEEPSLATE_TILES);
        c.parapet(-17, 13, 3, 17, 30, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.cornerPillars(-17, 1, 3, 17, 12, 30, Blocks.DEEPSLATE_BRICKS);

        c.pillar(-5, 1, 5, 0, Blocks.DEEPSLATE_BRICKS);
        c.pillar(5, 1, 5, 0, Blocks.DEEPSLATE_BRICKS);
        c.box(-6, 5, 0, 6, 5, 2, Blocks.BLUE_CONCRETE);
        c.doorway(-1, 1, 3, 3);
        c.doubleDoor(-1, 1, 3, Blocks.IRON_DOOR);
        c.doorway(-1, 1, 30, 3);
        c.doorwaySide(-17, 1, 15, 3);
        c.doorwaySide(17, 1, 15, 3);
        for (int fy : new int[] { 2, 8 }) {
            c.windowX(-15, fy, 3, -7, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowX(7, fy, 3, 15, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(-17, fy, 6, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
            c.windowZ(17, fy, 6, 12, fy + 2, Blocks.CYAN_STAINED_GLASS_PANE);
        }

        // 1F：報案櫃檯＋西翼室內射擊靶場＋東翼 4 間鐵柵拘留室
        c.counter(-4, 7, 4, 7, 1, Blocks.BLUE_CONCRETE);
        c.box(-7, 1, 5, -7, 5, 22, Blocks.GRAY_CONCRETE);
        c.doorwaySide(-7, 1, 12, 3);
        for (int z : new int[] { 9, 14, 19 }) {
            c.tallCover(-14, z, -9, z, 1, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        }
        c.box(-15, 1, 21, -9, 3, 21, Blocks.ORANGE_CONCRETE);
        c.box(7, 1, 5, 7, 5, 22, Blocks.GRAY_CONCRETE);
        c.doorwaySide(7, 1, 12, 3);
        for (int zBase : new int[] { 6, 11, 16 }) {
            c.box(9, 1, zBase + 3, 16, 5, zBase + 3, Blocks.STONE_BRICKS);
            c.box(9, 1, zBase, 9, 5, zBase + 3, Blocks.IRON_BARS);
            c.doorwaySide(9, 1, zBase + 1, 2);
            c.bedUnit(14, zBase + 1, 16, zBase + 1, 1, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL);
        }
        c.bunkerCoverL(-4, 1, 25, 2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(4, 1, 25, -2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);

        // 2F：作戰簡報廳＋證物機櫃室
        c.counter(-5, 9, 5, 9, 7, Blocks.DARK_OAK_PLANKS);
        c.sofaX(-4, 4, 7, 12, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.sofaX(-4, 4, 7, 15, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        for (int z : new int[] { 8, 13, 18 }) {
            c.serverRack(-12, 7, z, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
            c.serverRack(12, 7, z, 3, Blocks.OBSERVER, Blocks.BLAST_FURNACE);
        }
        c.tallCover(-5, 21, 5, 21, 7, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 屋頂通訊機房與掩體
        c.walls(-5, 13, 19, 5, 16, 26, Blocks.GRAY_CONCRETE);
        c.box(-5, 17, 19, 5, 17, 26, Blocks.DEEPSLATE_TILES);
        c.doorway(-1, 13, 19, 3);
        c.pillar(0, 18, 19, 23, Blocks.IRON_BARS);
        c.tallCover(-12, 8, -7, 8, 13, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(7, 8, 12, 8, 13, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);

        c.staircaseZ(-16, -15, 0, 6, 17, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(15, 16, 0, 6, 17, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-16, -15, 6, 12, 23, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(15, 16, 6, 12, 23, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);

        c.ceilingLights(-14, 14, 5, 5, 28, 7, 7, Blocks.SEA_LANTERN);
        c.ceilingLights(-14, 14, 11, 5, 28, 7, 7, Blocks.SEA_LANTERN);

        // 42 個物資箱點位（貼近櫃檯／靶牆／牢籠／機櫃／掩體）
        c.loot(-12, 1, 2);  c.loot(12, 1, 2);   c.loot(-3, 1, 8);  c.loot(3, 1, 8);
        c.loot(-12, 1, 8);  c.loot(-12, 1, 13); c.loot(-12, 1, 18); c.loot(-8, 1, 20);
        c.loot(12, 1, 7);   c.loot(12, 1, 12);  c.loot(12, 1, 17);  c.loot(8, 1, 21);
        c.loot(-5, 1, 24);  c.loot(5, 1, 24);   c.loot(0, 1, 29);   c.loot(-16, 1, 5);
        c.loot(-12, 7, 9);  c.loot(-12, 7, 14); c.loot(-12, 7, 19); c.loot(12, 7, 9);
        c.loot(12, 7, 14);  c.loot(12, 7, 19);  c.loot(-3, 7, 8);   c.loot(3, 7, 8);
        c.loot(-4, 7, 13);  c.loot(4, 7, 16);   c.loot(-4, 7, 22);  c.loot(4, 7, 22);
        c.loot(-9, 7, 29);  c.loot(9, 7, 29);
        c.loot(-14, 13, 5); c.loot(14, 13, 5);  c.loot(-10, 13, 9); c.loot(10, 13, 9);
        c.loot(-16, 13, 16); c.loot(16, 13, 16); c.loot(-14, 13, 28); c.loot(14, 13, 28);
        c.loot(-3, 13, 21); c.loot(3, 13, 21);  c.loot(0, 13, 27);  c.loot(0, 18, 21);
    }

    private static void newsStudio(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-16, 0, 0, 16, 19, 28);

        c.floor(-16, 0, 0, 16, 28, Blocks.POLISHED_ANDESITE);
        c.box(-15, 0, 3, 15, 0, 28, Blocks.SMOOTH_QUARTZ);
        c.walls(-15, 1, 3, 15, 5, 28, Blocks.WHITE_CONCRETE);
        c.box(-15, 6, 3, 15, 6, 28, Blocks.SMOOTH_STONE);
        c.walls(-15, 7, 3, 15, 11, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-15, 12, 3, 15, 12, 28, Blocks.SMOOTH_STONE);
        c.walls(-15, 13, 3, 15, 16, 28, Blocks.WHITE_CONCRETE);
        c.box(-15, 17, 3, 15, 17, 28, Blocks.DEEPSLATE_TILES);
        c.parapet(-15, 18, 3, 15, 28, Blocks.STONE_BRICK_WALL, true);
        c.cornerPillars(-15, 1, 3, 15, 17, 28, Blocks.DEEPSLATE_BRICKS);

        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-15, 1, 15, 3);
        c.doorwaySide(15, 1, 15, 3);
        for (int fy : new int[] { 2, 8, 14 }) {
            c.windowX(-13, fy, 3, -5, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowX(5, fy, 3, 13, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(-15, fy, 6, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(15, fy, 6, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        }

        // 挑空攝影棚 (x=-6..6, z=10..22)：綠幕牆＋主播台＋燈架掩體
        c.box(-6, 6, 10, 6, 11, 22, Blocks.AIR);
        c.box(-7, 7, 9, 7, 7, 23, Blocks.CYAN_STAINED_GLASS_PANE);
        c.box(-6, 7, 10, 6, 7, 22, Blocks.AIR);
        c.box(-5, 1, 21, 5, 5, 21, Blocks.LIME_CONCRETE); // 綠幕攝影背景牆
        c.counter(-3, 16, 3, 17, 1, Blocks.BLUE_CONCRETE); // 主播台
        c.pillar(-5, 1, 4, 13, Blocks.IRON_BLOCK); c.set(-5, 5, 13, Blocks.SEA_LANTERN);
        c.pillar(5, 1, 4, 13, Blocks.IRON_BLOCK);  c.set(5, 5, 13, Blocks.SEA_LANTERN);
        c.counter(-4, 6, 4, 6, 1, Blocks.QUARTZ_BLOCK);

        // 2F 導播機房＋剪輯室
        for (int z : new int[] { 10, 16, 20 }) {
            c.serverRack(-11, 7, z, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
            c.serverRack(11, 7, z, 3, Blocks.OBSERVER, Blocks.REDSTONE_LAMP);
        }
        c.counter(-5, 24, 5, 24, 7, Blocks.POLISHED_DEEPSLATE);

        // 3F 編輯部門＋屋頂轉播碟
        c.deskUnit(-10, 13, 8, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(-10, 13, 14, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(10, 13, 8, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.deskUnit(10, 13, 14, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        c.tallCover(-4, 12, 4, 12, 13, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(-4, 21, 4, 21, 13, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.pillar(0, 18, 19, 15, Blocks.IRON_BARS);
        c.box(-2, 18, 14, 2, 18, 16, Blocks.IRON_BLOCK);
        c.tallCover(-11, 8, -7, 8, 18, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(7, 8, 11, 8, 18, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 雙寬 2 格樓梯 (無電梯)：0->6->12->17
        c.staircaseZ(-14, -13, 0, 6, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(13, 14, 0, 6, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-14, -13, 6, 12, 23, -1, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(13, 14, 12, 17, 17, 1, Blocks.QUARTZ_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);

        c.ceilingLights(-12, 12, 5, 5, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 11, 5, 26, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 16, 5, 26, 6, 6, Blocks.SEA_LANTERN);

        // 38 個物資箱點位（全部貼近綠幕／主播台／機櫃／辦公桌／女兒牆）
        c.loot(-4, 1, 7);   c.loot(4, 1, 7);   c.loot(-12, 1, 6);  c.loot(12, 1, 6);
        c.loot(-4, 1, 13);  c.loot(4, 1, 13);  c.loot(-3, 1, 15);  c.loot(3, 1, 15);
        c.loot(-4, 1, 20);  c.loot(4, 1, 20);  c.loot(0, 1, 22);   c.loot(-11, 1, 26); c.loot(11, 1, 26);
        c.loot(-10, 7, 10); c.loot(10, 7, 10); c.loot(-10, 7, 16); c.loot(10, 7, 16);
        c.loot(-10, 7, 20); c.loot(10, 7, 20); c.loot(-5, 7, 25);  c.loot(5, 7, 25);
        c.loot(0, 7, 8);
        c.loot(-10, 13, 9); c.loot(10, 13, 9); c.loot(-10, 13, 15); c.loot(10, 13, 15);
        c.loot(-4, 13, 13); c.loot(4, 13, 13); c.loot(-4, 13, 20); c.loot(4, 13, 20);
        c.loot(0, 13, 26);  c.loot(-13, 13, 26); c.loot(13, 13, 26);
        c.loot(-10, 18, 9); c.loot(10, 18, 9); c.loot(-12, 18, 24); c.loot(12, 18, 24);
        c.loot(0, 18, 17);
    }
}
