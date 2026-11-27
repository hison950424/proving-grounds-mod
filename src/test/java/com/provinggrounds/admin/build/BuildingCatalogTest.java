package com.provinggrounds.admin.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 檔案: BuildingCatalogTest.java
 * 目的: 驗證建築總覽目錄數量（8 風格共 52 棟）、各風格數量、欄位合法性，
 *       以及所有建築原始碼完全不含木頭儲物箱／木桶。
 */
class BuildingCatalogTest {

    @Test
    void catalogHas52BuildingsWithExpectedStyleCounts() {
        List<BuildingBlueprint> all = BuildingCatalog.all();
        assertEquals(52, all.size(), "建築目錄應包含 52 棟建築");

        assertEquals(7, BuildingCatalog.byStyle(BuildingStyle.MODERN).size());
        assertEquals(7, BuildingCatalog.byStyle(BuildingStyle.INDUSTRIAL).size());
        assertEquals(7, BuildingCatalog.byStyle(BuildingStyle.JAPANESE).size());
        assertEquals(7, BuildingCatalog.byStyle(BuildingStyle.SCIFI).size());
        assertEquals(6, BuildingCatalog.byStyle(BuildingStyle.CIVIC).size());
        assertEquals(6, BuildingCatalog.byStyle(BuildingStyle.MEDIEVAL).size());
        assertEquals(6, BuildingCatalog.byStyle(BuildingStyle.DESERT).size());
        assertEquals(6, BuildingCatalog.byStyle(BuildingStyle.MARITIME).size());

        Set<String> ids = new HashSet<>();
        for (BuildingBlueprint bp : all) {
            assertFalse(bp.id().isBlank(), "建築 ID 不得空白");
            assertFalse(bp.name().isBlank(), "建築名稱不得空白");
            assertTrue(ids.add(bp.id()), "建築 ID 不得重複: " + bp.id());
            assertTrue(bp.width() >= 15 && bp.depth() >= 15 && bp.height() >= 10,
                    "建築尺寸異常: " + bp.id());
            assertTrue(bp.lootCount() >= 20, "建築物資點數異常: " + bp.id());
        }

        List<String> expectedNewIds = List.of(
                "mod_fire_station", "mod_police_hq", "mod_news_studio",
                "ind_train_depot", "ind_water_treatment", "ind_auto_factory",
                "jp_dojo_compound", "jp_shrine_complex", "jp_castle_barracks",
                "sci_radar_array", "sci_cryo_vault", "sci_orbital_pad",
                "civ_grand_library", "civ_metro_station", "civ_bank_vault",
                "med_fortified_abbey", "med_harbor_keep", "med_market_hall",
                "des_caravanserai", "des_canyon_outpost", "des_sunken_ruins",
                "mar_lighthouse_pier", "mar_cargo_freighter", "mar_ice_observatory"
        );
        for (String id : expectedNewIds) {
            assertTrue(ids.contains(id), "缺少新增建築: " + id);
        }
    }

    @Test
    void buildingSourcesContainNoWoodenChestsOrBarrels() throws IOException {
        Path buildDir = Paths.get("src", "main", "java", "com", "provinggrounds", "admin", "build");
        assertTrue(Files.isDirectory(buildDir), "找不到建築原始碼目錄: " + buildDir);
        try (Stream<Path> files = Files.list(buildDir)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty());
            for (Path file : javaFiles) {
                if (file.getFileName().toString().equals("BuildingCanvas.java")) {
                    continue;
                }
                String src = Files.readString(file);
                assertFalse(src.contains("Blocks.CHEST"), file + " 不得使用 Blocks.CHEST");
                assertFalse(src.contains("Blocks.TRAPPED_CHEST"), file + " 不得使用 Blocks.TRAPPED_CHEST");
                assertFalse(src.contains("Blocks.BARREL"), file + " 不得使用 Blocks.BARREL");
            }
        }
    }
}
