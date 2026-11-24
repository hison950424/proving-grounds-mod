package com.provinggrounds.modes.br;

import com.provinggrounds.config.PGConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 檔案: CirclePreset.java
 * 目的: 縮圈預設方案（快速 3 階段 / 標準 4 階段 / 慢速 5 階段）。
 *       各方案的階段半徑比例、間隔、圈的速度上限、開場額外等待都從 PGConfig 讀取（可熱重載），
 *       管理員也可以用 /pg circle 指令在遊戲裡修改。
 */
public enum CirclePreset {
    FAST("快速", "fast"),
    STANDARD("標準", "standard"),
    SLOW("慢速", "slow"),
    /** 自訂（計畫書 6 D2）：階段數、間隔、速度、開場等待、最後一圈大小都在大廳調整。 */
    CUSTOM("自訂", "custom");

    public final String displayName;
    /** 指令用名稱。 */
    public final String key;

    CirclePreset(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    public static CirclePreset byKey(String k) {
        for (CirclePreset p : values()) if (p.key.equalsIgnoreCase(k)) return p;
        return null;
    }

    public ForgeConfigSpec.ConfigValue<List<? extends Double>> ratiosValue() {
        PGConfig c = PGConfig.SERVER;
        return switch (this) {
            case FAST -> c.brFastRatios;
            case STANDARD -> c.brStandardRatios;
            case SLOW, CUSTOM -> c.brSlowRatios; // 自訂方案不用這個（ratios() 另外算）
        };
    }

    public ForgeConfigSpec.IntValue intervalValue() {
        PGConfig c = PGConfig.SERVER;
        return switch (this) {
            case FAST -> c.brFastInterval;
            case STANDARD -> c.brStandardInterval;
            case SLOW -> c.brSlowInterval;
            case CUSTOM -> c.brCustomInterval;
        };
    }

    public ForgeConfigSpec.DoubleValue speedValue() {
        PGConfig c = PGConfig.SERVER;
        return switch (this) {
            case FAST -> c.brFastSpeed;
            case STANDARD -> c.brStandardSpeed;
            case SLOW -> c.brSlowSpeed;
            case CUSTOM -> c.brCustomSpeed;
        };
    }

    public ForgeConfigSpec.IntValue firstWaitValue() {
        PGConfig c = PGConfig.SERVER;
        return switch (this) {
            case FAST -> c.brFastFirstWait;
            case STANDARD -> c.brStandardFirstWait;
            case SLOW -> c.brSlowFirstWait;
            case CUSTOM -> c.brCustomFirstWait;
        };
    }

    /** 各階段半徑 ÷ 地圖半徑，保證遞減。 */
    public double[] ratios() {
        if (this == CUSTOM) {
            // 自訂：n 階段，從整張地圖平滑縮到「最後一圈 %」（前面縮得多、後面縮得少）
            int n = PGConfig.SERVER.brCustomStages.get();
            double fin = PGConfig.SERVER.brCustomFinal.get() / 100.0;
            double[] out = new double[n];
            for (int i = 1; i <= n; i++) out[i - 1] = fin + (1 - fin) * Math.pow(1 - i / (double) n, 1.5);
            return out;
        }
        return ratiosValue().get().stream().mapToDouble(Number::doubleValue).boxed()
            .sorted((a, b) -> Double.compare(b, a))
            .mapToDouble(Double::doubleValue).toArray();
    }

    public int intervalSeconds() { return intervalValue().get(); }

    /** 圈移動速度上限是 BR 疾跑速度的幾倍。 */
    public double speedFactor() { return speedValue().get(); }

    /** 開場第一次縮圈前額外多等幾秒。 */
    public int firstWaitSeconds() { return firstWaitValue().get(); }

    /**
     * 估計一場的總時間（秒）：每階段 = 等待 + 縮圈（至少一個間隔；距離太遠時依速度上限拉長），
     * 中心平均偏移取最大可偏移距離的 2/3。
     */
    public double estimateSeconds(double mapRadius) {
        double speed = PGConfig.SERVER.brSprintSpeed() * speedFactor();
        double r = mapRadius, t = 0;
        double[] rs = ratios();
        for (int i = 0; i < rs.length; i++) {
            double to = mapRadius * rs[i];
            double travel = (r - to) * 5.0 / 3.0;
            t += intervalSeconds() + (i == 0 ? firstWaitSeconds() : 0) + Math.max(intervalSeconds(), travel / Math.max(0.01, speed));
            r = to;
        }
        return t;
    }
}
