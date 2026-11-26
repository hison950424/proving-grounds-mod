package com.provinggrounds.mixin;

import com.provinggrounds.special.SpecialHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: PlayerSkinMixin.java
 * 目的: 敵我不分（計畫書 9，只在客戶端）：受影響的玩家在彼此眼中全部變成一模一樣的史蒂夫。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（getSkinTextureLocation = m_108560_、getModelName = m_108564_）。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {

    @Inject(method = { "getSkinTextureLocation", "m_108560_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$steveSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (SpecialHooks.showAsSteve(Minecraft.getInstance().player, self)) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultSkin());
        }
    }

    @Inject(method = { "getModelName", "m_108564_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$steveModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (SpecialHooks.showAsSteve(Minecraft.getInstance().player, self)) {
            cir.setReturnValue("default");
        }
    }
}
