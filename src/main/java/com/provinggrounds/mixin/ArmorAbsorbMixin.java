package com.provinggrounds.mixin;

import com.provinggrounds.events.ArmorEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: ArmorAbsorbMixin.java
 * 目的: 子彈傷害不用原版的「全身護甲加總」減傷，也不扣全身四件的耐久（使用者回饋）。
 *   子彈的減傷與耐久改由 ArmorEvents 只看「被打中的那個部位」那一件防具處理。
 *   其他傷害（爆炸、近戰、摔落…）照原版。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（getDamageAfterArmorAbsorb = m_21161_），不需要 refmap。
 */
@Mixin(LivingEntity.class)
public abstract class ArmorAbsorbMixin {

    @Inject(method = {
        "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
        "m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$perPartArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (ArmorEvents.handlesArmor(source)) cir.setReturnValue(amount);
    }
}
