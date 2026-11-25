package com.provinggrounds.mixin;

import com.provinggrounds.ui.HudLayout;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 檔案: GuiGraphicsFillMixin.java
 * 目的: 計畫書 7 A1：HUD 元件的背景色塊透明度與文字分開調整（只在客戶端）。
 *   畫 HUD 元件時 HudLayout.fillAlphaMul 不是 1，所有色塊（GuiGraphics.fill 最後都會走到這個方法）的顏色透明度乘上它。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（fill(RenderType, x1, y1, x2, y2, z, color) = m_285795_），不需要 refmap。
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsFillMixin {

    @ModifyVariable(method = {
        "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V",
        "m_285795_(Lnet/minecraft/client/renderer/RenderType;IIIIII)V"
    }, at = @At("HEAD"), ordinal = 5, argsOnly = true, remap = false)
    private int provinggrounds$fillAlpha(int color) {
        float mul = HudLayout.fillAlphaMul;
        if (mul > 0.999f && mul < 1.001f) return color;
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * mul)));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
