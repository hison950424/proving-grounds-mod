package com.provinggrounds.data;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 檔案: BRMapPresets.java
 * 目的: 定義 5 張不同半徑（300 / 400 / 500 / 600 / 700 格）的程序化大逃殺地圖預設：
 *   - 每張地圖擁有鮮明區域分區、密集建築群配置、主題自然地景參數與復活祭壇預定點。
 *   - 僅含純資料與數學雜湊函式，不依賴 Minecraft 方塊登錄表，可直接由單元測試快速驗證。
 */
public final class BRMapPresets {

    public enum Theme { HARBOR, DESERT, ARCTIC, IMPERIAL, CONTINENT }

    public enum RegionBiome {
        TEMPERATE,
        HARBOR_COAST,
        DESERT_SAND,
        RED_CANYON,
        ARCTIC_SNOW,
        SAKURA_HIGHLAND,
        MEDIEVAL_VALLEY,
        INDUSTRIAL_YARD
    }

    public record DistrictDef(
        String name,
        int relX,
        int relZ,
        int elevation,
        RegionBiome biome,
        List<String> buildingIds
    ) {}

    public record AltarHint(int relX, int relZ) {}

    public record Preset(
        String key,
        String displayName,
        int radius,
        Theme theme,
        long seed,
        int waterLevel,
        List<DistrictDef> districts,
        List<AltarHint> altars
    ) {}

    private static final List<Preset> ALL = List.of(
        // 1. 300 格：緊湊海灣港城（4 大密集城區・17 棟建築・5 座祭壇）
        new Preset(
            "harbor", "鐵灣港城", 300, Theme.HARBOR, 600300L, 62,
            List.of(
                new DistrictDef("中央商務城區", 0, -25, 68, RegionBiome.TEMPERATE, List.of(
                    "mod_shopping_mall", "mod_hospital", "civ_bank_vault", "mod_news_studio", "mod_police_hq"
                )),
                new DistrictDef("南岸貨櫃港區", 0, 150, 65, RegionBiome.HARBOR_COAST, List.of(
                    "mar_cargo_freighter", "mar_lighthouse_pier", "ind_logistics_mega", "mar_destroyer_ship"
                )),
                new DistrictDef("東區重工車廠", 155, -20, 68, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "ind_auto_factory", "ind_train_depot", "ind_steel_foundry", "ind_power_plant"
                )),
                new DistrictDef("西區都會校園", -155, -20, 70, RegionBiome.TEMPERATE, List.of(
                    "civ_high_school", "civ_parking_garage", "mod_fire_station", "mod_tactical_hq"
                ))
            ),
            List.of(
                new AltarHint(0, 62),
                new AltarHint(110, 82),
                new AltarHint(-110, 82),
                new AltarHint(105, -145),
                new AltarHint(-105, -145)
            )
        ),

        // 2. 400 格：荒漠峽谷綠洲（5 大密集城區・22 棟建築・7 座祭壇）
        new Preset(
            "desert", "赤砂帝國", 400, Theme.DESERT, 600400L, 62,
            List.of(
                new DistrictDef("中央黃金綠洲", 0, 0, 67, RegionBiome.DESERT_SAND, List.of(
                    "des_oasis_palace", "des_bazaar_citadel", "des_caravanserai", "des_sunken_ruins"
                )),
                new DistrictDef("北區金字塔聖域", 0, -225, 74, RegionBiome.RED_CANYON, List.of(
                    "des_pyramid_temple", "des_sunken_ruins", "des_caravanserai", "med_fortified_abbey"
                )),
                new DistrictDef("東區荒漠油田", 230, 35, 70, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "ind_oil_refinery", "ind_power_plant", "ind_logistics_mega", "ind_water_treatment", "des_canyon_outpost"
                )),
                new DistrictDef("西區峽谷要塞", -230, 35, 76, RegionBiome.RED_CANYON, List.of(
                    "mod_prison_fortress", "des_canyon_outpost", "mod_tactical_hq", "med_crusader_castle"
                )),
                new DistrictDef("南區商旅城塞", 0, 230, 69, RegionBiome.DESERT_SAND, List.of(
                    "des_bazaar_citadel", "med_market_hall", "des_caravanserai", "civ_metro_station", "mod_fire_station"
                ))
            ),
            List.of(
                new AltarHint(0, 102),
                new AltarHint(0, -110),
                new AltarHint(135, -110),
                new AltarHint(-135, -110),
                new AltarHint(135, 145),
                new AltarHint(-135, 145),
                new AltarHint(-285, -125)
            )
        ),

        // 3. 500 格：極地冰川科研要塞（6 大密集城區・30 棟建築・9 座祭壇）
        new Preset(
            "arctic", "極光要塞", 500, Theme.ARCTIC, 600500L, 62,
            List.of(
                new DistrictDef("中央軌道研究城", 0, 0, 72, RegionBiome.ARCTIC_SNOW, List.of(
                    "sci_orbital_pad", "sci_mech_bay", "sci_research_complex", "sci_reactor_citadel", "mar_ice_observatory"
                )),
                new DistrictDef("北區極地裝甲總部", 0, -305, 78, RegionBiome.ARCTIC_SNOW, List.of(
                    "mar_arctic_base", "mod_tactical_hq", "sci_radar_array", "sci_cryo_vault", "mod_police_hq"
                )),
                new DistrictDef("東區星港機庫群", 310, -85, 72, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "sci_starport_hangar", "civ_airport_terminal", "sci_mech_bay", "ind_logistics_mega", "civ_parking_garage"
                )),
                new DistrictDef("東南能源重工區", 225, 250, 70, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "ind_oil_refinery", "ind_power_plant", "ind_steel_foundry", "ind_water_treatment", "sci_reactor_citadel"
                )),
                new DistrictDef("西區冷凍科研穹頂", -310, -85, 75, RegionBiome.ARCTIC_SNOW, List.of(
                    "mar_biodome_jungle", "mar_ice_observatory", "sci_cryo_vault", "sci_research_complex", "mod_hospital"
                )),
                new DistrictDef("西南鐵路監獄要塞", -225, 250, 73, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "mod_prison_fortress", "ind_train_depot", "civ_metro_station", "mod_fire_station", "sci_radar_array"
                ))
            ),
            List.of(
                new AltarHint(0, 125),
                new AltarHint(0, -135),
                new AltarHint(165, -175),
                new AltarHint(-165, -175),
                new AltarHint(200, 85),
                new AltarHint(-200, 85),
                new AltarHint(0, 325),
                new AltarHint(360, 110),
                new AltarHint(-360, 110)
            )
        ),

        // 4. 600 格：東西方古都山谷（7 大密集城區・38 棟建築・11 座祭壇）
        new Preset(
            "imperial", "緋櫻古城", 600, Theme.IMPERIAL, 600600L, 62,
            List.of(
                new DistrictDef("中央王都大教堂", 0, 0, 74, RegionBiome.MEDIEVAL_VALLEY, List.of(
                    "med_crusader_castle", "med_gothic_cathedral", "med_grand_theater", "med_market_hall", "civ_grand_library", "med_fortified_abbey"
                )),
                new DistrictDef("東區江戶天守城下町", 370, -35, 72, RegionBiome.SAKURA_HIGHLAND, List.of(
                    "jp_tenshukaku", "jp_edo_street", "jp_castle_barracks", "jp_dojo_compound", "jp_onsen_ryokan", "jp_shrine_complex"
                )),
                new DistrictDef("東北千本神域山寺", 235, -350, 82, RegionBiome.SAKURA_HIGHLAND, List.of(
                    "jp_grand_temple", "jp_shrine_complex", "jp_onsen_ryokan", "jp_dojo_compound", "jp_tenshukaku"
                )),
                new DistrictDef("東南武家港灣要塞", 275, 325, 67, RegionBiome.HARBOR_COAST, List.of(
                    "med_harbor_keep", "mar_lighthouse_pier", "jp_edo_street", "jp_castle_barracks", "med_market_hall"
                )),
                new DistrictDef("西區修道院王國市鎮", -375, -35, 73, RegionBiome.MEDIEVAL_VALLEY, List.of(
                    "med_fortified_abbey", "med_market_hall", "med_grand_theater", "med_crusader_castle", "civ_bank_vault", "civ_high_school"
                )),
                new DistrictDef("西北高地海防堡壘", -245, -345, 80, RegionBiome.MEDIEVAL_VALLEY, List.of(
                    "med_harbor_keep", "med_gothic_cathedral", "mod_prison_fortress", "ind_train_depot", "med_fortified_abbey"
                )),
                new DistrictDef("南區生態學園古鎮", -70, 385, 70, RegionBiome.TEMPERATE, List.of(
                    "mar_biodome_jungle", "civ_grand_library", "civ_metro_station", "med_market_hall", "jp_onsen_ryokan"
                ))
            ),
            List.of(
                new AltarHint(0, 145),
                new AltarHint(0, -155),
                new AltarHint(185, -165),
                new AltarHint(-185, -165),
                new AltarHint(185, 165),
                new AltarHint(-185, 165),
                new AltarHint(445, -210),
                new AltarHint(-445, -210),
                new AltarHint(425, 165),
                new AltarHint(-415, 225),
                new AltarHint(110, 445)
            )
        ),

        // 5. 700 格：八大風格全境群島（9 大密集城區・55 棟建築・13 座祭壇）
        new Preset(
            "continent", "萬象大陸", 700, Theme.CONTINENT, 600700L, 62,
            List.of(
                new DistrictDef("中央都會核心", 0, 0, 72, RegionBiome.TEMPERATE, List.of(
                    "mod_shopping_mall", "civ_grand_library", "civ_bank_vault", "mod_hospital", "mod_news_studio", "civ_metro_station"
                )),
                new DistrictDef("北區極地科幻禁區", 0, -455, 80, RegionBiome.ARCTIC_SNOW, List.of(
                    "sci_orbital_pad", "sci_mech_bay", "sci_research_complex", "sci_reactor_citadel", "sci_cryo_vault", "mar_ice_observatory"
                )),
                new DistrictDef("東北極地軍事星港", 385, -295, 76, RegionBiome.ARCTIC_SNOW, List.of(
                    "mar_arctic_base", "sci_starport_hangar", "sci_radar_array", "mod_tactical_hq", "civ_airport_terminal", "civ_parking_garage"
                )),
                new DistrictDef("東區緋櫻和風古都", 475, 40, 74, RegionBiome.SAKURA_HIGHLAND, List.of(
                    "jp_tenshukaku", "jp_edo_street", "jp_grand_temple", "jp_shrine_complex", "jp_onsen_ryokan", "jp_dojo_compound", "jp_castle_barracks"
                )),
                new DistrictDef("東南中世紀王城", 345, 370, 73, RegionBiome.MEDIEVAL_VALLEY, List.of(
                    "med_crusader_castle", "med_gothic_cathedral", "med_grand_theater", "med_harbor_keep", "med_fortified_abbey", "med_market_hall"
                )),
                new DistrictDef("南區遠洋深水軍港", -15, 475, 65, RegionBiome.HARBOR_COAST, List.of(
                    "mar_destroyer_ship", "mar_cargo_freighter", "mar_lighthouse_pier", "mar_biodome_jungle", "ind_logistics_mega", "mod_fire_station"
                )),
                new DistrictDef("西南重工業製造城", -375, 335, 70, RegionBiome.INDUSTRIAL_YARD, List.of(
                    "ind_oil_refinery", "ind_auto_factory", "ind_steel_foundry", "ind_power_plant", "ind_train_depot", "ind_water_treatment"
                )),
                new DistrictDef("西區赤砂荒漠王國", -475, -25, 72, RegionBiome.DESERT_SAND, List.of(
                    "des_pyramid_temple", "des_oasis_palace", "des_bazaar_citadel", "des_caravanserai", "des_canyon_outpost", "des_sunken_ruins"
                )),
                new DistrictDef("西北特警監獄學園", -355, -345, 75, RegionBiome.TEMPERATE, List.of(
                    "mod_prison_fortress", "mod_police_hq", "civ_high_school", "mod_tactical_hq", "civ_parking_garage", "mod_hospital"
                ))
            ),
            List.of(
                new AltarHint(0, 175),
                new AltarHint(0, -175),
                new AltarHint(185, 0),
                new AltarHint(-185, 0),
                new AltarHint(225, -225),
                new AltarHint(-225, -225),
                new AltarHint(225, 225),
                new AltarHint(-225, 225),
                new AltarHint(0, -295),
                new AltarHint(310, 485),
                new AltarHint(-310, 485),
                new AltarHint(525, -145),
                new AltarHint(-525, -185)
            )
        )
    );

    private BRMapPresets() {}

    public static List<Preset> all() {
        return ALL;
    }

    @Nullable
    public static Preset byKeyOrSize(@Nullable String token) {
        if (token == null || token.isBlank()) return null;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        for (Preset p : ALL) {
            if (p.key().equals(clean) || String.valueOf(p.radius()).equals(clean) || p.displayName().equals(token.trim())) {
                return p;
            }
        }
        return null;
    }

    /** 依距離最近的區域決定地表生態風格，帶微幅邊界擾動讓交界自然過渡。 */
    public static RegionBiome biomeAt(Preset preset, double relX, double relZ) {
        double bestDist = Double.MAX_VALUE;
        RegionBiome best = RegionBiome.TEMPERATE;
        double jitterX = (noise2D(relX * 0.015, relZ * 0.015, preset.seed() ^ 0x41B2L) - 0.5) * 42.0;
        double jitterZ = (noise2D(relX * 0.015 + 31.7, relZ * 0.015 - 19.3, preset.seed() ^ 0x9E37L) - 0.5) * 42.0;
        for (DistrictDef d : preset.districts()) {
            double dx = (relX + jitterX) - d.relX();
            double dz = (relZ + jitterZ) - d.relZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDist) {
                bestDist = distSq;
                best = d.biome();
            }
        }
        return best;
    }

    /** 確定性 2D 雜湊亂數（0.0 ~ 1.0）。 */
    public static double hash01(long x, long z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xBF58476D1CE4E5B9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    /** 雙線性平滑 2D Value Noise（0.0 ~ 1.0）。 */
    public static double noise2D(double x, double z, long seed) {
        long ix = (long) Math.floor(x);
        long iz = (long) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uz = fz * fz * (3.0 - 2.0 * fz);
        double a = hash01(ix, iz, seed);
        double b = hash01(ix + 1, iz, seed);
        double c = hash01(ix, iz + 1, seed);
        double d = hash01(ix + 1, iz + 1, seed);
        double ab = a + (b - a) * ux;
        double cd = c + (d - c) * ux;
        return ab + (cd - ab) * uz;
    }
}
