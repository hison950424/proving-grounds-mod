package com.provinggrounds.data;

import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.data.BRMapLayout.PlacedBuilding;
import com.provinggrounds.data.BRMapLayout.Plan;
import com.provinggrounds.data.BRMapPresets.DistrictDef;
import com.provinggrounds.data.BRMapPresets.Preset;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 檔案: BRMapLayoutTest.java
 * 目的: 驗證 5 張區域密集主題地圖（300 / 400 / 500 / 600 / 700 格）的藍圖解析、
 *       建築零重疊密排、邊界內收、復活祭壇安全距離與地表高度齊平。
 */
class BRMapLayoutTest {

    @Test
    void fivePresetsCoverExpectedRadiiAndLookups() {
        List<Preset> all = BRMapPresets.all();
        assertEquals(5, all.size());
        assertEquals(List.of(300, 400, 500, 600, 700), all.stream().map(Preset::radius).toList());

        Set<String> keys = new HashSet<>();
        for (Preset p : all) {
            assertTrue(keys.add(p.key()));
            assertNotNull(BRMapPresets.byKeyOrSize(p.key()));
            assertNotNull(BRMapPresets.byKeyOrSize(String.valueOf(p.radius())));
            assertNotNull(BRMapPresets.byKeyOrSize(p.displayName()));
            for (DistrictDef d : p.districts()) {
                assertFalse(d.buildingIds().isEmpty(), "區域建築不可為空: " + d.name());
                for (String id : d.buildingIds()) {
                    assertNotNull(BuildingCatalog.byId(id), "找不到建築藍圖 ID: " + id);
                }
            }
        }
    }

    @Test
    void allFivePlansPackDenseBuildingsWithoutOverlapAndPlaceSafeAltars() {
        int[] expectedAltars = { 5, 7, 9, 11, 13 };
        List<Preset> all = BRMapPresets.all();
        for (int idx = 0; idx < all.size(); idx++) {
            Preset preset = all.get(idx);
            int expectedBuildings = preset.districts().stream().mapToInt(d -> d.buildingIds().size()).sum();
            Plan plan = BRMapLayout.buildPlan(preset, 5000, 5000);

            assertEquals(expectedBuildings, plan.buildings.size(),
                "地圖建築未能全部擺放成功: " + preset.key());
            assertEquals(expectedAltars[idx], plan.altars.size(),
                "復活祭壇數量不符: " + preset.key());

            double maxBuildingDist = preset.radius() - 12.0;
            double maxAltarDist = preset.radius() - 16.0;

            for (int i = 0; i < plan.buildings.size(); i++) {
                PlacedBuilding a = plan.buildings.get(i);
                // 四角皆在地圖半徑安全邊界內
                for (int cx : new int[] { a.minX(), a.maxX() }) {
                    for (int cz : new int[] { a.minZ(), a.maxZ() }) {
                        double r = Math.hypot(cx - plan.centerX, cz - plan.centerZ);
                        assertTrue(r <= maxBuildingDist,
                            preset.key() + " 建築超出邊界: " + a.bp().id() + " r=" + r);
                    }
                }
                // 建築基準點切齊所在城區地表高度
                assertEquals(a.y(), plan.surfaceY(a.originX(), a.originZ()),
                    preset.key() + " 建築高度未貼齊地面: " + a.bp().id());

                for (int j = i + 1; j < plan.buildings.size(); j++) {
                    PlacedBuilding b = plan.buildings.get(j);
                    assertFalse(a.intersectsWithGap(b.minX(), b.maxX(), b.minZ(), b.maxZ(), 2),
                        preset.key() + " 建築重疊: " + a.bp().id() + " vs " + b.bp().id());
                }
            }

            // 祭壇必須在圈內、不撞建築、地面高度與祭壇腳下齊平
            for (BlockPos altar : plan.altars) {
                double r = Math.hypot(altar.getX() - plan.centerX, altar.getZ() - plan.centerZ);
                assertTrue(r <= maxAltarDist, preset.key() + " 祭壇超出邊界: " + altar);
                assertEquals(altar.getY() - 1, plan.surfaceY(altar.getX(), altar.getZ()),
                    preset.key() + " 祭壇高度未貼齊廣場: " + altar);
                for (PlacedBuilding b : plan.buildings) {
                    boolean insideBuilding = altar.getX() >= b.minX() - 4 && altar.getX() <= b.maxX() + 4
                        && altar.getZ() >= b.minZ() - 4 && altar.getZ() <= b.maxZ() + 4;
                    assertFalse(insideBuilding, preset.key() + " 祭壇撞進建築範圍: " + altar + " / " + b.bp().id());
                }
            }
        }
    }
}
