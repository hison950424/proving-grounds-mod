package com.provinggrounds.special.client;

import com.provinggrounds.special.entity.DecoyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 檔案: DecoyRenderer.java
 * 目的: 殘影分身的外觀（只在客戶端）：用玩家模型，皮膚取自使用者（伺服器同步的 OWNER），
 *   並畫出複製來的護甲與手持物，名條顯示使用者的名字（跟真的玩家一樣）。
 */
public class DecoyRenderer extends LivingEntityRenderer<DecoyEntity, PlayerModel<DecoyEntity>> {

    public DecoyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity e) {
        UUID id = e.owner();
        if (id == null) return DefaultPlayerSkin.getDefaultSkin();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    @Override
    protected boolean shouldShowName(DecoyEntity e) {
        return false; // 分身一律不顯示頭頂 ID
    }
}
