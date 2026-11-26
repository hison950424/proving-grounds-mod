package com.provinggrounds.mixin;

import com.provinggrounds.special.SpecialHooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: LivingRendererMixin.java
 * 目的: 無實體化（計畫書 9，只在客戶端）：讓進入無實體化的玩家以半透明幽靈狀態繪製。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（isBodyVisible = m_5933_）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin {

    @Inject(method = { "isBodyVisible", "m_5933_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$etherealGhost(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpecialHooks.isEthereal(entity)) cir.setReturnValue(false);
    }
}
