package com.provinggrounds.data;

import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.data.BRMapLayout.PlacedBuilding;
import com.provinggrounds.data.BRMapLayout.Plan;
import com.provinggrounds.data.BRMapLayout.SmallBuilding;
import com.provinggrounds.data.BRMapPresets.DistrictDef;
import com.provinggrounds.data.BRMapPresets.Preset;
import com.provinggrounds.modes.br.Circle;
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
 * 目的: 驗證決賽圈半徑（11.5 格）以及 5 張三層高密度主題地圖（300 / 400 / 500 / 600 / 700 格）的
 *       大小城區建築零重疊、邊界內收、小房區與野區微據點／掩體數量、復活祭壇安全距離與地表高度齊平。
 */
class BRMapLayoutTest {

    @Test
    void finalCircleRadiusIsExpandedTo11_5() {
        assertEquals(11.5, Circle.FINAL_RADIUS, 1e-6);
    }

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
    void allFivePlansPackDenseThreeTierStructuresWithoutOverlap() {
        int[] expectedAltars = { 5, 7, 9, 11, 13 };
        int[] minCompounds = { 4, 8, 12, 16, 22 };
        int[] minCovers = { 80, 140, 240, 340, 450 };

        List<Preset> all = BRMapPresets.all();
        for (int idx = 0; idx < all.size(); idx++) {
            Preset preset = all.get(idx);
            int expectedMajors = preset.districts().stream().mapToInt(d -> d.buildingIds().size()).sum();
            Plan plan = BRMapLayout.buildPlan(preset, 5000, 5000);

            assertEquals(expectedMajors, plan.buildings.size(),
                "大城區建築未能全部擺放成功: " + preset.key());
            assertEquals(expectedAltars[idx], plan.altars.size(),
                "復活祭壇數量不符: " + preset.key());
            assertTrue(plan.compounds.size() >= minCompounds[idx],
                preset.key() + " 小房區過少: " + plan.compounds.size());
            assertTrue(plan.smallBuildings.size() >= plan.compounds.size() * 2,
                preset.key() + " 小房區建物過少: " + plan.smallBuildings.size());
            assertTrue(plan.microPois.size() >= 4,
                preset.key() + " 野區微據點過少: " + plan.microPois.size());
            assertTrue(plan.covers.size() >= minCovers[idx],
                preset.key() + " 野外掩體過少: " + plan.covers.size());

            double maxBuildingDist = preset.radius() - 12.0;
            double maxAltarDist = preset.radius() - 16.0;

            for (int i = 0; i < plan.buildings.size(); i++) {
                PlacedBuilding a = plan.buildings.get(i);
                assertInsideCircle(preset.key(), a.bp().id(), a.minX(), a.maxX(), a.minZ(), a.maxZ(), plan.centerX, plan.centerZ, maxBuildingDist);
                assertEquals(a.y(), plan.surfaceY(a.originX(), a.originZ()),
                    preset.key() + " 大建築高度未貼齊地面: " + a.bp().id());

                for (int j = i + 1; j < plan.buildings.size(); j++) {
                    PlacedBuilding b = plan.buildings.get(j);
                    assertFalse(a.intersectsWithGap(b.minX(), b.maxX(), b.minZ(), b.maxZ(), 2),
                        preset.key() + " 大建築重疊: " + a.bp().id() + " vs " + b.bp().id());
                }
            }

            for (int i = 0; i < plan.smallBuildings.size(); i++) {
                SmallBuilding s = plan.smallBuildings.get(i);
                assertInsideCircle(preset.key(), s.kind().name(), s.minX(), s.maxX(), s.minZ(), s.maxZ(), plan.centerX, plan.centerZ, maxBuildingDist);
                assertEquals(s.y(), plan.surfaceY(s.originX(), s.originZ()),
                    preset.key() + " 小房區建築高度未貼齊地面: " + s.kind());

                for (PlacedBuilding m : plan.buildings) {
                    assertFalse(s.intersectsWithGap(m.minX(), m.maxX(), m.minZ(), m.maxZ(), 2),
                        preset.key() + " 小房區撞進大建築: " + s.kind() + " vs " + m.bp().id());
                }
                for (int j = i + 1; j < plan.smallBuildings.size(); j++) {
                    SmallBuilding s2 = plan.smallBuildings.get(j);
                    assertFalse(s.intersectsWithGap(s2.minX(), s2.maxX(), s2.minZ(), s2.maxZ(), 2),
                        preset.key() + " 小房區彼此重疊: " + s.kind());
                }
            }

            for (BlockPos altar : plan.altars) {
                double r = Math.hypot(altar.getX() - plan.centerX, altar.getZ() - plan.centerZ);
                assertTrue(r <= maxAltarDist, preset.key() + " 祭壇超出邊界: " + altar);
                assertEquals(altar.getY() - 1, plan.surfaceY(altar.getX(), altar.getZ()),
                    preset.key() + " 祭壇高度未貼齊廣場: " + altar);
                for (PlacedBuilding b : plan.buildings) {
                    assertFalse(altar.getX() >= b.minX() - 4 && altar.getX() <= b.maxX() + 4
                            && altar.getZ() >= b.minZ() - 4 && altar.getZ() <= b.maxZ() + 4,
                        preset.key() + " 祭壇撞進大建築: " + altar + " / " + b.bp().id());
                }
                for (SmallBuilding s : plan.smallBuildings) {
                    assertFalse(altar.getX() >= s.minX() - 4 && altar.getX() <= s.maxX() + 4
                            && altar.getZ() >= s.minZ() - 4 && altar.getZ() <= s.maxZ() + 4,
                        preset.key() + " 祭壇撞進小房區: " + altar + " / " + s.kind());
                }
            }
        }
    }

    private static void assertInsideCircle(String key, String name, int minX, int maxX, int minZ, int maxZ,
                                           int cx, int cz, double maxR) {
        for (int x : new int[] { minX, maxX }) {
            for (int z : new int[] { minZ, maxZ }) {
                double r = Math.hypot(x - cx, z - cz);
                assertTrue(r <= maxR, key + " 建築超出邊界: " + name + " r=" + r);
            }
        }
    }
}
