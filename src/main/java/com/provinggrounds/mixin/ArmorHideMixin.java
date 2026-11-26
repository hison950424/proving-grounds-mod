package com.provinggrounds.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.special.SpecialHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ArmorHideMixin.java
 * 目的: 敵我不分（計畫書 9，只在客戶端）：變成史蒂夫時不畫身上的防具，避免靠防具認出誰是誰（防具減傷效果照常）。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（render = m_6494_）。
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorHideMixin {

    @Inject(method = { "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
                       "m_6494_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$hideDisguisedArmor(PoseStack pose, MultiBufferSource buf, int light, LivingEntity entity,
                                                   float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (SpecialHooks.showAsSteve(Minecraft.getInstance().player, entity)) ci.cancel();
    }
}
