package com.provinggrounds.modes.br;

import com.provinggrounds.config.PGConfig;

import java.util.List;

/**
 * 檔案: CirclePreset.java
 * 目的: 縮圈預設方案（快速 3 階段 / 標準 4 階段 / 慢速 5 階段）。
 *       各階段半徑比例與間隔都從 PGConfig 讀取，可熱重載。
 */
public enum CirclePreset {
    FAST("快速"),
    STANDARD("標準"),
    SLOW("慢速");

    public final String displayName;

    CirclePreset(String displayName) {
        this.displayName = displayName;
    }

    /** 各階段半徑 ÷ 地圖半徑，保證遞減。 */
    public double[] ratios() {
        PGConfig c = PGConfig.SERVER;
        List<? extends Double> list = switch (this) {
            case FAST -> c.brFastRatios.get();
            case STANDARD -> c.brStandardRatios.get();
            case SLOW -> c.brSlowRatios.get();
        };
        return list.stream().mapToDouble(Number::doubleValue).boxed()
            .sorted((a, b) -> Double.compare(b, a))
            .mapToDouble(Double::doubleValue).toArray();
    }

    public int intervalSeconds() {
        PGConfig c = PGConfig.SERVER;
        return switch (this) {
            case FAST -> c.brFastInterval.get();
            case STANDARD -> c.brStandardInterval.get();
            case SLOW -> c.brSlowInterval.get();
        };
    }
}
