package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: GunBalance.java
 * 目的: 槍械傷害平衡（所有模式通用，伺服器端）。目標：整體 TTK 約 2.5 秒（穿全套鐵甲、近距離、全部打身體）。
 *
 *   不改槍械包的檔案：子彈命中、扣血之前（槍械適配層的傷害調整），依每把槍自己的射速重新決定每發傷害：
 *     全自動／半自動槍：沒穿甲時 TTK = 類型目標（步槍、衝鋒槍、機槍 auto_ttk；手槍 pistol_ttk）
 *       → 需要的發數 n = floor(TTK × 每秒發數) + 1，每發傷害 = 最大血量 ÷ n（多一點點，確保 n 發剛好打死）。
 *       所以射速快的槍每發傷害低、射速慢的每發高，但同類型打死人的時間差不多；手感差異（射速、後座、彈匣）保留。
 *     栓動狙擊槍（射速 ≤ sniper_bolt_rpm）：打身體 sniper_body_shots 發（預設 2），爆頭倍率至少 sniper_head_multiplier（一發爆頭）。
 *     霰彈槍：每一發（所有彈丸加起來）shotgun_shot_damage，近距離 2 發。
 *   距離減傷（使用者回饋：預設關閉 distance_falloff_v1，遠近傷害一樣；槍械包自己的減傷照樣先還原）。開著時改用我們自己的：
 *     步槍、機槍：rifle_falloff_start 格內全額，到 rifle_falloff_end 格降到 rifle_falloff_min，之後維持。
 *     衝鋒槍、手槍：smg_falloff_*；霰彈槍：shotgun_falloff_*；狙擊槍：不減傷。
 *   護甲：子彈不再吃原版護甲值（資料檔把子彈傷害加進 minecraft:bypasses_armor），只算 ArmorEvents 的防彈係數，
 *     穿什麼甲慢多少對每把槍都一致。
 */
public final class GunBalance {

    /** 槍的原始數值（由槍械適配層讀出）。 */
    public record Stats(String type, int rpm, float damage, int bulletAmount, float headMultiplier,
                        List<float[]> falloff, int magazine) {}

    /** 一把槍算好的結果：每發（每顆彈丸）的倍率、爆頭倍率（0 = 不改）、距離減傷類型。 */
    private record Plan(double multiplier, float headMultiplier, String falloffKind, List<float[]> taczFalloff, float rawDamage) {}

    private static final Map<ResourceLocation, Plan> CACHE = new ConcurrentHashMap<>();

    private GunBalance() {}

    public static void clearCache() { CACHE.clear(); }

    public static boolean enabled() {
        try { return PGConfig.SERVER.gunBalanceEnabled.get(); } catch (IllegalStateException e) { return false; }
    }

    /** 調整後的傷害（扣血前、爆頭倍率之前）。 */
    public static float adjust(ResourceLocation gunId, @Nullable Stats stats, float baseAmount, double distance) {
        Plan plan = plan(gunId, stats);
        if (plan == null) return baseAmount;
        // 先把槍械包自己的距離減傷還原，再套我們的
        float taczAt = taczFalloff(plan.taczFalloff(), distance, plan.rawDamage());
        double undo = taczAt > 0.01f ? plan.rawDamage() / taczAt : 1.0;
        return (float) (baseAmount * undo * plan.multiplier() * ourFalloff(plan.falloffKind(), distance));
    }

    /** 爆頭倍率（0 = 不改）。 */
    public static float headMultiplier(ResourceLocation gunId, @Nullable Stats stats) {
        Plan plan = plan(gunId, stats);
        return plan == null ? 0 : plan.headMultiplier();
    }

    @Nullable
    private static Plan plan(ResourceLocation gunId, @Nullable Stats s) {
        Plan cached = CACHE.get(gunId);
        if (cached != null) return cached;
        if (s == null || s.damage() <= 0) return null;
        PGConfig c = PGConfig.SERVER;
        double hp = c.brMaxHealth.get();
        double rpm = Math.max(1, s.rpm());
        String type = s.type() == null ? "rifle" : s.type();
        double target;            // 每發（所有彈丸加起來）要打多少
        float head = 0;
        String kind;
        switch (type) {
            case "shotgun" -> {
                target = c.shotgunShotDamage.get();
                kind = "shotgun";
            }
            case "sniper" -> {
                if (rpm <= c.sniperBoltRpm.get()) {
                    target = hp / c.sniperBodyShots.get() + 0.5;
                    head = (float) Math.max(s.headMultiplier(), c.sniperHeadMultiplier.get());
                } else {
                    target = perShot(hp, c.autoTtk.get(), rpm, s.magazine()); // 半自動狙擊（射手步槍）：照步槍算
                }
                kind = "sniper";
            }
            case "pistol" -> {
                target = perShot(hp, c.pistolTtk.get(), rpm, s.magazine());
                kind = "smg";
            }
            case "smg" -> {
                target = perShot(hp, c.autoTtk.get(), rpm, s.magazine());
                kind = "smg";
            }
            default -> { // rifle、mg、其他
                target = perShot(hp, c.autoTtk.get(), rpm, s.magazine());
                kind = "rifle";
            }
        }
        // TACZ 的 damage 是一發的總傷害（霰彈會平均分到每顆彈丸），所以倍率 = 目標 ÷ 原始
        Plan p = new Plan(target / s.damage(), head, kind, s.falloff(), s.damage());
        CACHE.put(gunId, p);
        return p;
    }

    /** 想在 ttk 秒打死 hp 的每發傷害；需要的發數不超過彈匣容量（一個彈匣一定打得死一個人）。 */
    private static double perShot(double hp, double ttk, double rpm, int magazine) {
        int n = (int) Math.floor(ttk * rpm / 60.0) + 1;
        if (magazine >= 5) n = Math.min(n, magazine); // 單發、雙管這類小彈匣的槍不套用（不然一發就死）
        return hp / n + 0.01;
    }

    /** 槍械包自己的距離減傷（分段常數：第一個「距離 ≥ 目前距離」的段）。 */
    private static float taczFalloff(List<float[]> table, double dist, float raw) {
        if (table == null || table.isEmpty()) return raw;
        for (float[] seg : table) if (dist <= seg[0]) return seg[1];
        return table.get(table.size() - 1)[1];
    }

    /** 我們的距離減傷（1 = 全額）。 */
    private static double ourFalloff(String kind, double dist) {
        PGConfig c = PGConfig.SERVER;
        if (!c.gunDistanceFalloff.get()) return 1.0; // 使用者回饋：預設不再有距離減傷
        double start, end, min;
        switch (kind) {
            case "sniper" -> { return 1.0; }
            case "shotgun" -> { start = c.shotgunFalloffStart.get(); end = c.shotgunFalloffEnd.get(); min = c.shotgunFalloffMin.get(); }
            case "smg" -> { start = c.smgFalloffStart.get(); end = c.smgFalloffEnd.get(); min = c.smgFalloffMin.get(); }
            default -> { start = c.rifleFalloffStart.get(); end = c.rifleFalloffEnd.get(); min = c.rifleFalloffMin.get(); }
        }
        if (dist <= start) return 1.0;
        if (dist >= end) return min;
        return 1.0 - (1.0 - min) * (dist - start) / (end - start);
    }
}
