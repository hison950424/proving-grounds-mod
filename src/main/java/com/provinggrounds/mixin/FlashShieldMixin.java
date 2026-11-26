package com.provinggrounds.mixin;

import com.provinggrounds.special.FlashShieldTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: FlashShieldMixin.java
 * 目的: lrtactical 閃光盾的閃光效果時間減半（計畫書 6 A21）。
 *   閃光盾用完（finishUsingItem = m_5922_）時會對自己與周圍的人加上「致盲」「耳鳴」效果，時間寫死在 lrtactical 裡。
 *   這裡在方法開頭打開 FlashShieldTweaks 的記錄、結束時把這段期間加上的效果換成一半時間。
 *   lrtactical 沒裝時這個 mixin 直接略過（@Pseudo、required = false）。方法名稱同時寫開發名稱與 SRG 名稱。
 */
@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.item.FlashShieldItem", remap = false)
public abstract class FlashShieldMixin {

    @Inject(method = {
        "finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
        "m_5922_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
    }, at = @At("HEAD"), remap = false, require = 0)
    private void provinggrounds$flashStart(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                           net.minecraft.world.entity.LivingEntity user, CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (!level.isClientSide) FlashShieldTweaks.begin();
    }

    @Inject(method = {
        "finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
        "m_5922_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
    }, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void provinggrounds$flashEnd(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                         net.minecraft.world.entity.LivingEntity user, CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (!level.isClientSide) {
            FlashShieldTweaks.end();
            if (FlashShieldTweaks.shouldDestroyAfterFlash(user, stack)) {
                stack.shrink(stack.getCount());
                cir.setReturnValue(net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }
}
