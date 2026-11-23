package com.provinggrounds.special.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.special.entity.MineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 檔案: MineRenderer.java
 * 目的: 地雷的外觀（只在客戶端）：只有放置者與隊友看得到，其他人什麼都不畫。
 *   用原版地毯當外觀（不需要另外做材質）：偵查 = 藍、地雷 = 棕、燃燒 = 橘、震爆 = 白，縮小成 0.6 格。
 */
public class MineRenderer extends EntityRenderer<MineEntity> {

    public MineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0;
    }

    @Override
    public void render(MineEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!e.visibleTo(Minecraft.getInstance().player)) return;
        BlockState state = switch (e.mineType()) {
            case RECON_MINE -> Blocks.BLUE_CARPET.defaultBlockState();
            case FIRE_MINE -> Blocks.ORANGE_CARPET.defaultBlockState();
            case SHOCK_MINE -> Blocks.WHITE_CARPET.defaultBlockState();
            default -> Blocks.BROWN_CARPET.defaultBlockState();
        };
        pose.pushPose();
        pose.scale(0.6f, 1.5f, 0.6f);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    public boolean shouldRender(MineEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.visibleTo(Minecraft.getInstance().player) && super.shouldRender(e, f, x, y, z);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(MineEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
