package com.provinggrounds.special.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: ShieldItemRenderer.java
 * 目的: 擊退盾的外觀（只在客戶端）：直接畫原版盾牌的模型（沒有旗幟圖案），
 *   物品模型 knockback_shield_held.json 繼承原版 item/shield_blocking 的擺放角度，所以拿在手上就是原版舉盾的樣子。
 */
public class ShieldItemRenderer extends BlockEntityWithoutLevelRenderer {

    private ShieldModel model;

    public ShieldItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (model == null) model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
        pose.pushPose();
        pose.scale(1.0f, -1.0f, -1.0f);
        Material mat = ModelBakery.NO_PATTERN_SHIELD;
        VertexConsumer vc = mat.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffers, model.renderType(mat.atlasLocation()), true, stack.hasFoil()));
        model.handle().render(pose, vc, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
        model.plate().render(pose, vc, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
        pose.popPose();
    }
}
