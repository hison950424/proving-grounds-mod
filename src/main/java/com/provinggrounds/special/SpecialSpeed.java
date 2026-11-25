package com.provinggrounds.special;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.Circle;
import net.minecraft.server.level.ServerPlayer;

/**
 * 檔案: SpecialSpeed.java
 * 目的: 特殊物品對移動速度的加成（BRMovement 每 tick 呼叫；使用者確認：以 BR 基本速度 80% 為基準）。
 *   強心劑（原腎上腺素）、靜音靴：基本速度 × (1 + 加成)，不和拿刀的速度疊加（期間拿什麼都一樣；兩個都有取較大者；倒地不加成）。
 *   其他（隱形斗篷、護盾泡泡觸發後、圈外庇護在圈外時）：加上 基本速度 × 加成，可以和拿刀疊加。
 */
public final class SpecialSpeed {

    private SpecialSpeed() {}

    /**
     * @param base    BR 基本速度倍率（speed_multiplier，預設 0.8）
     * @param current 目前的倍率（拿刀時是 melee_speed_multiplier）
     */
    public static double multiplier(ServerPlayer p, double base, double current) {
        SpecialConfig s;
        try { s = SpecialConfig.get(); } catch (IllegalStateException e) { return current; }
        double shield = KnockbackShield.active(p) ? 1 - s.shieldSpeedPenalty.get() : 1; // 擊退盾舉著：移動速度 -25%
        // 強心劑、靜音靴（計畫書 7 C9／C10）：不和拿刀疊加，兩個都有時取較大者；倒地時不加成
        if (!BattleRoyaleMode.get().isDowned(p.getUUID())) {
            double fixed = 0;
            if (p.hasEffect(SpecialRegistry.ADRENALINE.get())) fixed = Math.max(fixed, s.adrenalineBonus.get());
            if (p.hasEffect(SpecialRegistry.SILENT.get())) fixed = Math.max(fixed, s.silentBonus.get());
            if (fixed > 0) return base * (1 + fixed) * shield;
        }
        double bonus = 0;
        if (p.hasEffect(SpecialRegistry.CLOAK.get())) bonus += base * s.cloakBonus.get();
        if (p.hasEffect(SpecialRegistry.BUBBLE_SPEED.get())) bonus += base * s.bubbleSpeedBonus.get();
        if (p.hasEffect(SpecialRegistry.ZONE_SHELTER.get())) {
            Circle c = BattleRoyaleMode.get().circle();
            if (c != null && c.started() && c.isOutside(p.getX(), p.getZ())) bonus += base * s.shelterBonus.get();
        }
        return (current + bonus) * shield;
    }
}
