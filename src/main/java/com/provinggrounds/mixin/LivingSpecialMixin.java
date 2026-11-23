package com.provinggrounds.mixin;

import com.provinggrounds.special.SpecialHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: LivingSpecialMixin.java
 * 目的: 靜音靴：有靜音效果的玩家落地時不播落地聲（playBlockFallSound = m_21229_）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingSpecialMixin {

    /**
     * 槍械平衡：子彈傷害不吃原版護甲值（getDamageAfterArmorAbsorb = m_21161_），只算 ArmorEvents 的防彈係數，
     * 穿什麼甲慢多少對每把槍都一致（設定 bullet_ignores_vanilla_armor）。護甲耐久照原版扣（每件 傷害 ÷ 4，至少 1）。
     */
    @Inject(method = { "getDamageAfterArmorAbsorb", "m_21161_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$bulletIgnoresArmor(net.minecraft.world.damagesource.DamageSource source, float amount,
                                                   org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.server.level.ServerPlayer p) || !source.is(com.provinggrounds.compat.GunCompat.BULLET_DAMAGE)) return;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) return; // 穿甲部分本來就不吃護甲：照原版
        try {
            if (!com.provinggrounds.config.PGConfig.SERVER.bulletIgnoresVanillaArmor.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        int wear = (int) Math.max(1, amount / 4.0f);
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
            net.minecraft.world.item.ItemStack armor = p.getItemBySlot(slot);
            if (armor.getItem() instanceof net.minecraft.world.item.ArmorItem) armor.hurtAndBreak(wear, p, e -> e.broadcastBreakEvent(slot));
        }
        cir.setReturnValue(amount);
    }

    @Inject(method = { "playBlockFallSound", "m_21229_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentFall(CallbackInfo ci) {
        if (SpecialHooks.silent((LivingEntity) (Object) this)) ci.cancel();
    }
}
