package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.BRMovement;
import com.provinggrounds.modes.br.Circle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 檔案: SpecialSpeed.java
 * 目的: 特殊物品對移動速度的加成（BRMovement 每 tick 呼叫；使用者確認：以 BR 基本速度 80% 為基準）。
 *   強心劑（原腎上腺素）、靜音靴：基本速度 × (1 + 加成)，不和拿刀的速度疊加（期間拿什麼都一樣；兩個都有取較大者；倒地不加成）。
 *   其他（隱形斗篷、護盾泡泡觸發後、圈外庇護在圈外時）：加上 基本速度 × 加成，可以和拿刀疊加。
 *   大逃殺以外（大廳、團隊死鬥、佔點、槍王之王）沒有 BR 速度修改：改用自己的修改（基本速度 = 原版 100%），
 *   每 tick 檢查（使用者回饋：靜音靴有時候不會加速——就是在大逃殺以外的時候）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class SpecialSpeed {

    private static final UUID OUTSIDE_BR_ID = UUID.fromString("3c9a7e21-5d4b-4f08-b6e2-8a1f0c7d5e93");

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
        if (!BattleRoyaleMode.get().isDowned(p.getUUID()) && !com.provinggrounds.modes.arena.ArenaManager.isDowned(p.getUUID())) {
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

    /** 大逃殺以外的特殊物品加速（有 BR 速度修改時由 BRMovement 處理，這裡就拿掉自己的）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        double want = BRMovement.hasBrSpeed(p) ? 0 : multiplier(p, 1.0, 1.0) - 1.0;
        AttributeModifier cur = attr.getModifier(OUTSIDE_BR_ID);
        if (Math.abs(want) < 1e-6) {
            if (cur != null) attr.removeModifier(OUTSIDE_BR_ID);
            return;
        }
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        attr.removeModifier(OUTSIDE_BR_ID);
        attr.addTransientModifier(new AttributeModifier(OUTSIDE_BR_ID, "pg_special_speed", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
