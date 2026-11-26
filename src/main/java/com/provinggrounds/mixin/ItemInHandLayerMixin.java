package com.provinggrounds.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.core.WallClimb;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ItemInHandLayerMixin.java
 * 目的: 第三人稱攀爬期間隱藏雙手所持物品（含槍械、近戰武器與補品），
 *   與第一人稱「先收起物品騰出雙手再爬牆」的演出一致（只在客戶端）。
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = {
        "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        "m_117184_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$hideWhileClimbing(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                                  HumanoidArm arm, PoseStack poseStack, MultiBufferSource buf,
                                                  int light, CallbackInfo ci) {
        if (entity instanceof Player p && WallClimb.isHandsStowed(p)) {
            ci.cancel();
        }
    }
}
