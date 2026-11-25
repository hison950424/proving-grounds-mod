package com.provinggrounds.mixin;

import com.provinggrounds.ui.ChestSideLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ContainerScreenBgMixin.java
 * 目的: 計畫書 7 A4：箱子畫面改成「背包＋箱子」兩塊（ChestSideLayout）時，取消原版的箱子背景貼圖，改畫我們自己的面板（只在客戶端）。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（renderBg = m_7286_），不需要 refmap。
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenBgMixin {

    @Inject(method = {
        "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        "m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$sideLayoutBg(GuiGraphics g, float partial, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerScreen self = (ContainerScreen) (Object) this;
        if (!ChestSideLayout.active(self)) return;
        ChestSideLayout.drawBackground(self, g, mouseX, mouseY);
        ci.cancel();
    }
}
