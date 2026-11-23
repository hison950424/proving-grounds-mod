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

    @Inject(method = { "playBlockFallSound", "m_21229_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentFall(CallbackInfo ci) {
        if (SpecialHooks.silent((LivingEntity) (Object) this)) ci.cancel();
    }
}
