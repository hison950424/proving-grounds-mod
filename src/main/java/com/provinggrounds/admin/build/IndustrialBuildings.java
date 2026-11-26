package com.provinggrounds.admin.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.provinggrounds.admin.build.BuildingStyle.INDUSTRIAL;

/**
 * 檔案: IndustrialBuildings.java
 * 目的: 重工業與物流園區風格 4 棟大型／超大型建築（全數無木箱、樓梯最後一階完整齊平、鐵欄／石牆／玻璃片自動連接）。
 */
final class IndustrialBuildings {

    private IndustrialBuildings() {}

    static void register(Consumer<BuildingBlueprint> out) {
        out.accept(new BuildingBlueprint("ind_logistics_mega", INDUSTRIAL, "超大型貨運物流中心",
            "3 卸貨碼頭巨倉＋4 座可鑽入交火的巨型貨櫃＋雙排重型鋼架料架＋環繞式高架鋼骨天橋與中央懸吊控制室（40 個物資點）",
            39, 29, 17, 40, IndustrialBuildings::logisticsMega));

        out.accept(new BuildingBlueprint("ind_steel_foundry", INDUSTRIAL, "重工業煉鋼鑄造廠",
            "3 層煉鋼重工廠＋雙高爐熔煉塔＋環形鐵柵貓道＋高壓配電控制室＋雙煙囪氧化銅冷卻管線天台（38 個物資點）",
            35, 29, 22, 38, IndustrialBuildings::steelFoundry));

        out.accept(new BuildingBlueprint("ind_power_plant", INDUSTRIAL, "渦輪發電廠主機房",
            "前院戶外變壓器圍籬陣地＋雙座超長發電渦輪巨機＋2 樓總控畫廊＋屋頂 3 座大型冷卻風扇群（36 個物資點）",
            31, 27, 19, 36, IndustrialBuildings::powerPlant));

        out.accept(new BuildingBlueprint("ind_oil_refinery", INDUSTRIAL, "重裝石油精煉廠園區",
            "45×35 煉油巨構：4 座巨型儲油槽（前 2 座內部可進入＋樓梯直上槽頂）、縱橫全場的 2 層氧化銅管線貓道、裝甲中控大樓與雙油罐車掩體（48 個物資點）",
            45, 35, 24, 48, IndustrialBuildings::oilRefinery));
    }

    private static void logisticsMega(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-19, 0, 0, 19, 17, 28);

        // 1 樓地面＋前庭卸貨月台 (z=0..2)＋巨倉牆體 (z=3..28)
        c.floor(-19, 0, 0, 19, 28, Blocks.POLISHED_ANDESITE);
        c.box(-18, 0, 3, 18, 0, 28, Blocks.SMOOTH_STONE);
        c.walls(-18, 1, 3, 18, 11, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.walls(-18, 1, 3, 18, 2, 28, Blocks.DEEPSLATE_BRICKS);
        c.box(-18, 5, 3, 18, 5, 28, Blocks.ORANGE_CONCRETE);
        c.walls(-18, 6, 3, 18, 11, 28, Blocks.LIGHT_GRAY_CONCRETE);
        c.air(-17, 6, 4); // restore inside if walls filled outer shell only
        c.clear(-17, 1, 4, 17, 11, 27);
        c.cornerPillars(-18, 1, 3, 18, 11, 28, Blocks.DEEPSLATE_TILES);

        // 3 座巨型裝卸碼頭大門＋後側大門
        c.doorway(-13, 1, 3, 4);
        c.doorway(-2, 1, 3, 5);
        c.doorway(10, 1, 3, 4);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-18, 1, 15, 4);
        c.doorwaySide(18, 1, 15, 4);
        // 高窗（鐵欄杆）
        c.windowX(-16, 8, 3, -4, 9, Blocks.IRON_BARS);
        c.windowX(4, 8, 3, 16, 9, Blocks.IRON_BARS);
        c.windowZ(-18, 8, 6, 13, 9, Blocks.IRON_BARS);
        c.windowZ(-18, 8, 19, 25, 9, Blocks.IRON_BARS);
        c.windowZ(18, 8, 6, 13, 9, Blocks.IRON_BARS);
        c.windowZ(18, 8, 19, 25, 9, Blocks.IRON_BARS);

        // 1 樓 4 座可鑽入藏身的巨型貨櫃（絕無木箱）
        walkableContainer(c, -15, 6, -8, 12, Blocks.RED_CONCRETE);
        walkableContainer(c, 8, 6, 15, 12, Blocks.BLUE_CONCRETE);
        walkableContainer(c, -15, 18, -8, 24, Blocks.YELLOW_CONCRETE);
        walkableContainer(c, 8, 18, 15, 24, Blocks.GREEN_CONCRETE);

        // 中央重型鋼架料架（鐵欄柱＋平滑石半磚層板）
        steelRack(c, -3, 9, 3, 15);
        steelRack(c, -3, 17, 3, 23);

        // 右後調度玻璃辦公室
        c.box(10, 1, 25, 17, 4, 27, Blocks.WHITE_CONCRETE);
        c.clear(11, 1, 26, 16, 3, 26);
        c.doorway(12, 1, 25, 2);
        c.windowX(11, 2, 25, 11, 3, Blocks.GLASS_PANE);
        c.deskUnit(15, 1, 26, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);

        // 2 樓環形高架天橋 (y=6)＋中央懸吊天橋與控台
        c.box(-17, 6, 4, -14, 6, 27, Blocks.DEEPSLATE_TILES);
        c.box(14, 6, 4, 17, 6, 27, Blocks.DEEPSLATE_TILES);
        c.box(-14, 6, 24, 14, 6, 27, Blocks.DEEPSLATE_TILES);
        c.box(-14, 6, 13, 14, 6, 16, Blocks.DEEPSLATE_TILES);
        // 天橋護欄（鐵欄杆自動相連）
        c.windowZ(-14, 7, 4, 13, 7, Blocks.IRON_BARS);
        c.windowZ(-14, 7, 16, 24, 7, Blocks.IRON_BARS);
        c.windowZ(14, 7, 4, 13, 7, Blocks.IRON_BARS);
        c.windowZ(14, 7, 16, 24, 7, Blocks.IRON_BARS);
        c.windowX(-14, 7, 13, 14, 7, Blocks.IRON_BARS);
        c.windowX(-14, 7, 16, 14, 7, Blocks.IRON_BARS);
        c.windowX(-14, 7, 24, 14, 7, Blocks.IRON_BARS);
        // 中央懸吊控制室掩體
        c.tallCover(-3, 14, 3, 14, 7, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB);
        c.serverRack(-4, 7, 15, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);
        c.serverRack(4, 7, 15, 3, Blocks.IRON_BLOCK, Blocks.SEA_LANTERN);

        // 對稱雙側寬 2 格樓梯 (1F -> 2F天橋, 在鋪完天橋後呼叫)
        c.staircaseZ(-17, -16, 0, 6, 6, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(16, 17, 0, 6, 6, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);

        // 工業人字大屋頂 (y=12..17)
        c.box(-18, 12, 3, 18, 12, 28, Blocks.DEEPSLATE_BRICKS);
        c.box(-17, 12, 4, 17, 12, 27, Blocks.AIR); // 只留樑柱，內部維持挑高
        for (int z = 5; z <= 26; z += 5) {
            c.box(-17, 12, z, 17, 12, z, Blocks.DEEPSLATE_TILES);
        }
        c.gableRoofZ(-18, 12, 3, 18, 28, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.GRAY_CONCRETE);
        c.ceilingLights(-15, 15, 11, 6, 25, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-16, 16, 5, 6, 26, 8, 8, Blocks.SHROOMLIGHT);

        c.bunkerCoverL(-6, 1, 24, 2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(6, 1, 24, -2, -2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);

        // 40 個物資箱點位
        c.loot(-13, 1, 9);  c.loot(-10, 1, 9);  c.loot(10, 1, 9);   c.loot(13, 1, 9);
        c.loot(-13, 1, 21); c.loot(-10, 1, 21); c.loot(10, 1, 21);  c.loot(13, 1, 21);
        c.loot(-6, 1, 10);  c.loot(6, 1, 10);   c.loot(-6, 1, 20);  c.loot(6, 1, 20);
        c.loot(0, 1, 8);    c.loot(0, 1, 16);   c.loot(0, 1, 25);   c.loot(-16, 1, 15);
        c.loot(-12, 1, 4);  c.loot(12, 1, 4);   c.loot(14, 1, 26);  c.loot(11, 1, 26);
        c.loot(-16, 1, 6);  c.loot(16, 1, 6);   c.loot(-6, 1, 24);  c.loot(6, 1, 24);
        c.loot(-16, 7, 15); c.loot(-16, 7, 21); c.loot(-16, 7, 25);
        c.loot(16, 7, 15);  c.loot(16, 7, 21);  c.loot(16, 7, 25);
        c.loot(-9, 7, 15);  c.loot(-6, 7, 15);  c.loot(0, 7, 15);   c.loot(6, 7, 15);  c.loot(9, 7, 15);
        c.loot(-8, 7, 26);  c.loot(0, 7, 26);   c.loot(8, 7, 26);   c.loot(-16, 7, 10); c.loot(16, 7, 10);
    }

    private static void walkableContainer(BuildingCanvas c, int x0, int z0, int x1, int z1, Block color) {
        c.box(x0, 1, z0, x1, 4, z1, color);
        c.clear(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1);
        int midX = (x0 + x1) / 2;
        int midZ = (z0 + z1) / 2;
        c.doorway(midX - 1, 1, z0, 2);
        c.doorway(midX - 1, 1, z1, 2);
        c.set(x0 + 1, 1, midZ, Blocks.QUARTZ_BLOCK);
        c.lantern(midX, 3, midZ, true);
    }

    private static void steelRack(BuildingCanvas c, int x0, int z0, int x1, int z1) {
        c.box(x0, 1, z0, x0, 4, z0, Blocks.IRON_BARS);
        c.box(x1, 1, z0, x1, 4, z0, Blocks.IRON_BARS);
        c.box(x0, 1, z1, x0, 4, z1, Blocks.IRON_BARS);
        c.box(x1, 1, z1, x1, 4, z1, Blocks.IRON_BARS);
        c.box(x0 + 1, 2, z0, x1 - 1, 2, z1, Blocks.SMOOTH_STONE_SLAB);
        c.box(x0 + 1, 4, z0, x1 - 1, 4, z1, Blocks.SMOOTH_STONE_SLAB);
    }

    private static void steelFoundry(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-17, 0, 0, 17, 22, 28);

        c.floor(-17, 0, 0, 17, 28, Blocks.DEEPSLATE_TILES);
        c.box(-16, 0, 3, 16, 0, 28, Blocks.POLISHED_DEEPSLATE);
        c.walls(-16, 1, 3, 16, 15, 28, Blocks.BRICKS);
        c.walls(-16, 1, 3, 16, 3, 28, Blocks.DEEPSLATE_BRICKS);
        c.clear(-15, 1, 4, 15, 15, 27);
        c.cornerPillars(-16, 1, 3, 16, 16, 28, Blocks.DEEPSLATE_BRICKS);

        c.doorway(-2, 1, 3, 5);
        c.doorway(-2, 1, 28, 5);
        c.doorwaySide(-16, 1, 14, 4);
        c.doorwaySide(16, 1, 14, 4);
        for (int fy : new int[] { 7, 13 }) {
            c.windowX(-14, fy, 3, -5, fy + 1, Blocks.IRON_BARS);
            c.windowX(5, fy, 3, 14, fy + 1, Blocks.IRON_BARS);
            c.windowZ(-16, fy, 6, 12, fy + 1, Blocks.IRON_BARS);
            c.windowZ(16, fy, 6, 12, fy + 1, Blocks.IRON_BARS);
        }

        // 1 樓雙座高爐熔煉塔＋設備掩體
        furnaceTower(c, -9, 13);
        furnaceTower(c, 9, 13);
        c.tallCover(-4, 8, 4, 8, 1, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.tallCover(-4, 19, 4, 19, 1, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.counter(-14, 8, -13, 11, 1, Blocks.ANVIL);
        c.counter(13, 8, 14, 11, 1, Blocks.SMITHING_TABLE);

        // 2 樓環形鐵柵貓道 (y=6)＋北翼高壓配電控制室
        c.box(-15, 6, 4, -12, 6, 27, Blocks.DEEPSLATE_BRICKS);
        c.box(12, 6, 4, 15, 6, 27, Blocks.DEEPSLATE_BRICKS);
        c.box(-12, 6, 4, 12, 6, 7, Blocks.DEEPSLATE_BRICKS);
        c.box(-12, 6, 21, 12, 6, 27, Blocks.DEEPSLATE_BRICKS);
        c.windowX(-12, 7, 7, 12, 7, Blocks.IRON_BARS);
        c.windowX(-12, 7, 21, 12, 7, Blocks.IRON_BARS);
        c.windowZ(-12, 7, 7, 21, 7, Blocks.IRON_BARS);
        c.windowZ(12, 7, 7, 21, 7, Blocks.IRON_BARS);
        // 配電控制機櫃群
        for (int x : new int[] { -8, -4, 0, 4, 8 }) {
            c.serverRack(x, 7, 25, 3, Blocks.BLAST_FURNACE, Blocks.REDSTONE_LAMP);
        }
        c.counter(-6, 22, 6, 22, 7, Blocks.POLISHED_DEEPSLATE);

        // 3 樓＆屋頂冷卻管線平台 (y=12 / y=16)
        c.box(-15, 12, 4, 15, 12, 27, Blocks.DEEPSLATE_TILES);
        c.box(-6, 12, 10, 6, 12, 17, Blocks.AIR); // 3 樓開天窗俯瞰 1 樓雙高爐
        c.box(-7, 13, 9, 7, 13, 18, Blocks.IRON_BARS);
        c.box(-6, 13, 10, 6, 13, 17, Blocks.AIR);
        c.tallCover(-13, 10, -9, 10, 13, Blocks.OXIDIZED_COPPER, Blocks.OXIDIZED_CUT_COPPER_SLAB);
        c.tallCover(9, 10, 13, 10, 13, Blocks.OXIDIZED_COPPER, Blocks.OXIDIZED_CUT_COPPER_SLAB);

        // 屋頂 (y=16)＋雙巨型高爐煙囪直衝 y=22
        c.box(-16, 16, 3, 16, 16, 28, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-16, 17, 3, 16, 28, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.box(-12, 17, 11, -7, 22, 16, Blocks.BRICKS);
        c.clear(-11, 17, 12, -8, 22, 15);
        c.box(7, 17, 11, 12, 22, 16, Blocks.BRICKS);
        c.clear(8, 17, 12, 11, 22, 15);
        // 屋頂銅管線掩體
        c.box(-5, 17, 6, 5, 18, 7, Blocks.OXIDIZED_COPPER);
        c.box(-5, 17, 21, 5, 18, 22, Blocks.OXIDIZED_COPPER);

        // 雙寬樓梯 + 電梯
        c.staircaseZ(-15, -14, 0, 6, 17, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.staircaseZ(14, 15, 0, 6, 17, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.staircaseZ(-15, -14, 6, 12, 22, -1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);
        c.staircaseZ(14, 15, 12, 16, 21, -1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        c.bunkerCoverL(-12, 1, 18, 2, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.bunkerCoverL(12, 1, 18, -2, 2, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);

        c.ceilingLights(-13, 13, 5, 5, 25, 6, 6, Blocks.SHROOMLIGHT);
        c.ceilingLights(-13, 13, 11, 5, 25, 6, 6, Blocks.SHROOMLIGHT);
        c.ceilingLights(-13, 13, 15, 5, 25, 6, 6, Blocks.SEA_LANTERN);

        // 38 個物資箱點位
        c.loot(-13, 1, 13); c.loot(-5, 1, 13); c.loot(5, 1, 13);  c.loot(13, 1, 13);
        c.loot(0, 1, 10);   c.loot(0, 1, 22);  c.loot(-10, 1, 6); c.loot(10, 1, 6);
        c.loot(-10, 1, 22); c.loot(10, 1, 22); c.loot(-13, 1, 19); c.loot(13, 1, 19);
        c.loot(-14, 7, 9);  c.loot(14, 7, 9);  c.loot(-14, 7, 14); c.loot(14, 7, 14);
        c.loot(-10, 7, 5);  c.loot(0, 7, 5);   c.loot(10, 7, 5);
        c.loot(-6, 7, 23);  c.loot(6, 7, 23);  c.loot(0, 7, 24);  c.loot(-14, 7, 24); c.loot(14, 7, 24);
        c.loot(-10, 13, 8); c.loot(10, 13, 8); c.loot(-10, 13, 19); c.loot(10, 13, 19);
        c.loot(0, 13, 6);   c.loot(0, 13, 22); c.loot(0, 13, 25);
        c.loot(0, 17, 9);   c.loot(0, 17, 19); c.loot(-9, 17, 9);  c.loot(9, 17, 9);
        c.loot(-9, 17, 19); c.loot(9, 17, 19); c.loot(-4, 17, 13);
    }

    private static void furnaceTower(BuildingCanvas c, int cx, int cz) {
        c.box(cx - 2, 1, cz - 2, cx + 2, 11, cz + 2, Blocks.DEEPSLATE_BRICKS);
        c.box(cx - 1, 2, cz - 1, cx + 1, 3, cz + 1, Blocks.MAGMA_BLOCK);
        c.windowX(cx - 1, 2, cz - 2, cx + 1, 3, Blocks.IRON_BARS);
        c.windowX(cx - 1, 2, cz + 2, cx + 1, 3, Blocks.IRON_BARS);
        c.box(cx - 1, 1, cz - 3, cx + 1, 1, cz - 3, Blocks.BLAST_FURNACE);
    }

    private static void powerPlant(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-15, 0, 0, 15, 19, 26);

        // 前院變壓器圍籬區 (z=0..4)＋主發電廠 (z=5..26)
        c.floor(-15, 0, 0, 15, 26, Blocks.POLISHED_ANDESITE);
        c.box(-14, 0, 5, 14, 0, 26, Blocks.SMOOTH_STONE);
        // 前院左右變壓器陣地
        c.box(-12, 1, 1, -7, 1, 3, Blocks.STONE_BRICK_WALL);
        c.box(-11, 1, 2, -8, 3, 2, Blocks.OXIDIZED_COPPER);
        c.box(7, 1, 1, 12, 1, 3, Blocks.STONE_BRICK_WALL);
        c.box(8, 1, 2, 11, 3, 2, Blocks.OXIDIZED_COPPER);

        // 主樓外牆 (y=0, 6, 12, 16)
        c.walls(-14, 1, 5, 14, 11, 26, Blocks.GRAY_CONCRETE);
        c.walls(-14, 1, 5, 14, 1, 26, Blocks.YELLOW_CONCRETE);
        c.clear(-13, 1, 6, 13, 11, 25);
        c.box(-14, 12, 5, 14, 12, 26, Blocks.DEEPSLATE_TILES);
        c.walls(-14, 13, 5, 14, 15, 26, Blocks.LIGHT_GRAY_CONCRETE);
        c.box(-14, 16, 5, 14, 16, 26, Blocks.DEEPSLATE_BRICKS);
        c.cornerPillars(-14, 1, 5, 14, 16, 26, Blocks.DEEPSLATE_BRICKS);

        c.doorway(-2, 1, 5, 5);
        c.doorway(-2, 1, 26, 5);
        c.doorwaySide(-14, 1, 14, 3);
        c.doorwaySide(14, 1, 14, 3);
        for (int fy : new int[] { 3, 8 }) {
            c.windowX(-12, fy, 5, -5, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowX(5, fy, 5, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(-14, fy, 7, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(-14, fy, 18, 24, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(14, fy, 7, 12, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
            c.windowZ(14, fy, 18, 24, fy + 2, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        }

        // 1 樓雙座巨型渦輪機組
        turbineUnit(c, -6, 9, 20);
        turbineUnit(c, 6, 9, 20);
        c.tallCover(-2, 14, 2, 14, 1, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(-12, 10, -10, 10, 1, Blocks.IRON_BLOCK, Blocks.STONE_BRICK_SLAB);
        c.tallCover(10, 10, 12, 10, 1, Blocks.IRON_BLOCK, Blocks.STONE_BRICK_SLAB);

        // 2 樓懸空玻璃總控中心 (y=6)
        c.box(-13, 6, 6, 13, 6, 10, Blocks.DEEPSLATE_TILES);
        c.box(-13, 6, 21, 13, 6, 25, Blocks.DEEPSLATE_TILES);
        c.box(-13, 6, 10, -10, 6, 21, Blocks.DEEPSLATE_TILES);
        c.box(10, 6, 10, 13, 6, 21, Blocks.DEEPSLATE_TILES);
        c.windowX(-10, 7, 10, 10, 7, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(-10, 7, 21, 10, 7, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowZ(-10, 7, 10, 21, 7, Blocks.IRON_BARS);
        c.windowZ(10, 7, 10, 21, 7, Blocks.IRON_BARS);
        // 總控台與機櫃
        c.counter(-6, 7, 6, 7, 7, Blocks.BLACK_CONCRETE);
        c.deskUnit(-4, 7, 8, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
        c.deskUnit(4, 7, 8, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
        for (int x : new int[] { -8, -4, 0, 4, 8 }) {
            c.serverRack(x, 7, 24, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        }

        // 3 樓屋頂 3 座大型冷卻風扇 (y=17..19)＋機器室內機房 (y=13..15)
        c.tallCover(-8, 14, -4, 14, 13, Blocks.IRON_BLOCK, Blocks.SMOOTH_STONE_SLAB);
        c.tallCover(4, 14, 8, 14, 13, Blocks.IRON_BLOCK, Blocks.SMOOTH_STONE_SLAB);
        c.parapet(-14, 17, 5, 14, 26, Blocks.STONE_BRICK_WALL, true);
        for (int fx : new int[] { -8, 0, 8 }) {
            c.box(fx - 2, 17, 13, fx + 2, 19, 17, Blocks.DEEPSLATE_BRICKS);
            c.clear(fx - 1, 17, 14, fx + 1, 19, 16);
            c.box(fx - 1, 17, 14, fx + 1, 17, 16, Blocks.IRON_BARS);
        }

        // 雙寬樓梯（無電梯）
        c.staircaseZ(-13, -12, 0, 6, 15, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(12, 13, 0, 6, 15, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-13, -12, 6, 12, 22, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(12, 13, 12, 16, 19, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.bunkerCoverL(-11, 1, 22, 2, -2, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(11, 1, 22, -2, -2, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICK_SLAB);

        c.ceilingLights(-12, 12, 5, 7, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 11, 7, 24, 6, 6, Blocks.SEA_LANTERN);
        c.ceilingLights(-12, 12, 15, 7, 24, 6, 6, Blocks.SEA_LANTERN);

        // 36 個物資箱點位
        c.loot(-9, 1, 3);   c.loot(9, 1, 3);    c.loot(-5, 1, 2);   c.loot(5, 1, 2);
        c.loot(0, 1, 11);   c.loot(0, 1, 19);   c.loot(0, 1, 15);   c.loot(-11, 1, 10); c.loot(-11, 1, 15);
        c.loot(11, 1, 10);  c.loot(11, 1, 15);  c.loot(-4, 1, 24);  c.loot(4, 1, 24);
        c.loot(-11, 1, 22); c.loot(11, 1, 22);
        c.loot(-6, 7, 8);   c.loot(6, 7, 8);    c.loot(0, 7, 9);    c.loot(-12, 7, 11);
        c.loot(12, 7, 11);  c.loot(-12, 7, 16); c.loot(12, 7, 16);  c.loot(0, 7, 22);
        c.loot(-12, 7, 23); c.loot(12, 7, 23);
        c.loot(-6, 13, 10); c.loot(6, 13, 10);  c.loot(-6, 13, 21); c.loot(6, 13, 21);
        c.loot(-4, 17, 15); c.loot(4, 17, 15);  c.loot(-8, 17, 10); c.loot(8, 17, 10);
        c.loot(-8, 17, 21); c.loot(8, 17, 21);  c.loot(0, 17, 10);
    }

    private static void oilRefinery(BuildingCanvas c) {
        c.foundation(Blocks.DEEPSLATE_BRICKS);
        c.clear(-22, 0, 0, 22, 24, 34);

        // 45x35 工業煉油廠園區底板＋前側油罐裝載車棚 (z=0..5)
        c.floor(-22, 0, 0, 22, 34, Blocks.POLISHED_ANDESITE);
        c.parapet(-22, 1, 0, 22, 34, Blocks.DEEPSLATE_BRICK_WALL, false);
        c.doorway(-3, 1, 0, 7);
        c.doorwaySide(-22, 1, 18, 4);
        c.doorwaySide(22, 1, 18, 4);
        // 裝載車棚＋2 台實體油罐卡車掩體
        c.pillar(-18, 1, 5, 1, Blocks.YELLOW_CONCRETE);
        c.pillar(18, 1, 5, 1, Blocks.YELLOW_CONCRETE);
        c.box(-19, 5, 1, 19, 5, 5, Blocks.GRAY_CONCRETE);
        c.parkedCar(-12, 1, 3, Blocks.YELLOW_CONCRETE, false);
        c.parkedCar(12, 1, 3, Blocks.RED_CONCRETE, false);
        c.bunkerCoverL(-6, 1, 7, 2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        c.bunkerCoverL(6, 1, 7, -2, 2, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);

        // 4 座大型儲油槽 (半徑 4，高度 y=1..15，頂部 y=16 附鐵欄安全圍欄)
        oilTank(c, -14, 12, true);
        oilTank(c, 14, 12, true);
        oilTank(c, -14, 27, false);
        oilTank(c, 14, 27, false);

        // 中央 2 層裝甲煉油中控大樓 (x=-6..6, z=13..24, 樓板 y=0, 6, 12)
        c.walls(-6, 1, 13, 6, 11, 24, Blocks.GRAY_CONCRETE);
        c.clear(-5, 1, 14, 5, 11, 23);
        c.box(-6, 6, 13, 6, 6, 24, Blocks.DEEPSLATE_TILES);
        c.box(-6, 12, 13, 6, 12, 24, Blocks.DEEPSLATE_BRICKS);
        c.parapet(-6, 13, 13, 6, 24, Blocks.DEEPSLATE_BRICK_WALL, true);
        c.doorway(-1, 1, 13, 3);
        c.doorway(-1, 1, 24, 3);
        c.doorwaySide(-6, 1, 18, 3);
        c.doorwaySide(6, 1, 18, 3);
        c.doorwaySide(-6, 7, 18, 3);
        c.doorwaySide(6, 7, 18, 3);
        c.windowX(-4, 2, 13, -3, 4, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(3, 2, 13, 4, 4, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(-4, 8, 13, -2, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        c.windowX(2, 8, 13, 4, 10, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        // 中控室機櫃與控制台
        c.counter(-4, 16, 4, 16, 1, Blocks.POLISHED_DEEPSLATE);
        c.serverRack(-4, 1, 22, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.serverRack(4, 1, 22, 3, Blocks.OBSERVER, Blocks.SEA_LANTERN);
        c.deskUnit(-3, 7, 16, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.deskUnit(3, 7, 16, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        c.serverRack(0, 7, 22, 3, Blocks.BLAST_FURNACE, Blocks.REDSTONE_LAMP);

        // 縱橫全場的 2F 與 3F 氧化銅高架管線鋼橋（連結中控樓與 4 座儲油槽）
        c.box(-10, 6, 17, -6, 6, 19, Blocks.DEEPSLATE_TILES);
        c.box(6, 6, 17, 10, 6, 19, Blocks.DEEPSLATE_TILES);
        c.box(-15, 6, 16, -13, 6, 23, Blocks.DEEPSLATE_TILES);
        c.box(13, 6, 16, 15, 6, 23, Blocks.DEEPSLATE_TILES);
        c.box(-14, 12, 17, -6, 12, 19, Blocks.OXIDIZED_COPPER);
        c.box(6, 12, 17, 14, 12, 19, Blocks.OXIDIZED_COPPER);
        c.box(-15, 14, 16, -13, 14, 23, Blocks.OXIDIZED_COPPER);
        c.box(13, 14, 16, 15, 14, 23, Blocks.OXIDIZED_COPPER);
        c.windowX(-10, 7, 17, -6, 7, Blocks.IRON_BARS);
        c.windowX(6, 7, 17, 10, 7, Blocks.IRON_BARS);

        // 高聳煉油裂解塔 (後方中央 z=28..32, 高度 y=24)
        c.box(-3, 1, 28, 3, 21, 32, Blocks.DEEPSLATE_BRICKS);
        c.box(-2, 22, 29, 2, 24, 31, Blocks.IRON_BARS);
        c.set(0, 24, 30, Blocks.SEA_LANTERN);

        // 多組寬 2 格樓梯 (無電梯)：中控樓 0->6->12 + 儲油槽登頂樓梯 0->6->15
        c.staircaseZ(-5, -4, 0, 6, 18, 1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(4, 5, 6, 12, 22, -1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        c.staircaseZ(-20, -19, 0, 6, 10, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);
        c.staircaseZ(19, 20, 0, 6, 10, 1, Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS);
        c.staircaseZ(-20, -19, 6, 15, 19, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);
        c.staircaseZ(19, 20, 6, 15, 19, 1, Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS);

        c.ceilingLights(-5, 5, 5, 14, 23, 5, 5, Blocks.SEA_LANTERN);
        c.ceilingLights(-5, 5, 11, 14, 23, 5, 5, Blocks.SEA_LANTERN);

        // 48 個物資箱點位
        c.loot(-16, 1, 3);  c.loot(-8, 1, 3);   c.loot(8, 1, 3);    c.loot(16, 1, 3);
        c.loot(-14, 1, 11); c.loot(-14, 1, 13); c.loot(14, 1, 11);  c.loot(14, 1, 13);
        c.loot(-9, 1, 12);  c.loot(9, 1, 12);   c.loot(-9, 1, 27);  c.loot(9, 1, 27);
        c.loot(-3, 1, 15);  c.loot(3, 1, 15);   c.loot(0, 1, 20);   c.loot(-4, 1, 26);  c.loot(4, 1, 26);  c.loot(0, 1, 8);
        c.loot(-18, 1, 20); c.loot(18, 1, 20);
        c.loot(-14, 7, 19); c.loot(14, 7, 19);  c.loot(-8, 7, 18);  c.loot(8, 7, 18);
        c.loot(-3, 7, 15);  c.loot(3, 7, 15);   c.loot(0, 7, 20);   c.loot(-2, 7, 22);
        c.loot(-18, 7, 16); c.loot(18, 7, 16);
        c.loot(-3, 13, 15); c.loot(3, 13, 15);  c.loot(0, 13, 21);  c.loot(-10, 13, 18); c.loot(10, 13, 18);
        c.loot(-14, 16, 10); c.loot(-14, 16, 14); c.loot(14, 16, 10); c.loot(14, 16, 14);
        c.loot(-14, 16, 25); c.loot(-14, 16, 29); c.loot(14, 16, 25); c.loot(14, 16, 29);
        c.loot(-14, 15, 19); c.loot(14, 15, 19);  c.loot(0, 13, 18);  c.loot(-4, 1, 31);  c.loot(4, 1, 31);
    }

    private static void oilTank(BuildingCanvas c, int cx, int cz, boolean hollowBase) {
        c.box(cx - 4, 1, cz - 4, cx + 4, 15, cz + 4, Blocks.WHITE_CONCRETE);
        c.box(cx - 4, 8, cz - 4, cx + 4, 9, cz + 4, Blocks.ORANGE_CONCRETE);
        c.parapet(cx - 4, 16, cz - 4, cx + 4, cz + 4, Blocks.IRON_BARS, false);
        if (hollowBase) {
            c.clear(cx - 3, 1, cz - 3, cx + 3, 5, cz + 3);
            c.doorway(cx - 1, 1, cz - 4, 3);
            c.doorway(cx - 1, 1, cz + 4, 3);
            c.doorwaySide(cx - 4, 1, cz - 1, 3);
            c.doorwaySide(cx + 4, 1, cz - 1, 3);
            c.lantern(cx, 5, cz, true);
        }
    }

    private static void turbineUnit(BuildingCanvas c, int cx, int z0, int z1) {
        c.box(cx - 2, 1, z0, cx + 2, 2, z1, Blocks.DEEPSLATE_BRICKS);
        c.box(cx - 1, 3, z0 + 1, cx + 1, 3, z1 - 1, Blocks.IRON_BLOCK);
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            c.box(cx - 2, 2, z, cx + 2, 3, z, Blocks.OXIDIZED_COPPER);
            c.set(cx, 3, z, Blocks.SEA_LANTERN);
        }
    }
}
