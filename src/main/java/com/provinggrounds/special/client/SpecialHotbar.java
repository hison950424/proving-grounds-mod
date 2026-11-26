package com.provinggrounds.special.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.provinggrounds.client.ClientLobbyData;
import com.provinggrounds.network.LobbyStatePacket;
import com.provinggrounds.special.SpecialRegistry;
import com.provinggrounds.special.SpecialSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 檔案: SpecialHotbar.java
 * 目的: 特殊物品快捷欄（只在客戶端）：特殊物品模式開啟時，原版快捷欄右邊（隔一小段距離）多畫一格，
 *   顯示特殊物品欄裡的物品；格子上方用小字提示要按什麼鍵：使用、擊退盾「衝撞」、傳送珠飛行中「傳送」。
 *   只是顯示，物品實際放在特殊物品欄（物品欄畫面裡背包欄上方那格）。
 */
public final class SpecialHotbar {

    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    private SpecialHotbar() {}

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, width, height);

    /** 傳送珠是否還在飛（伺服器用 SpecialFxPacket 通知）。 */
    static volatile boolean pearlFlying;

    public static boolean modeOn() {
        LobbyStatePacket lobby = ClientLobbyData.get();
        return lobby == null || lobby.special();
    }

    /** 特殊物品格裡是佔點的整場特殊物品時的剩餘冷卻回合數（0 = 可用）。 */
    private static int domCooldown(ItemStack stack) {
        if (!com.provinggrounds.special.DomGear.isGearStack(stack)) return 0;
        int cd = com.provinggrounds.special.DomGear.cooldownLeft(stack);
        if (cd > 0) return cd;
        return com.provinggrounds.client.ClientDomGear.used() ? 1 : 0;
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator() || !modeOn()) return;
        if (!com.provinggrounds.ui.HudLayout.push(g, com.provinggrounds.ui.HudLayout.El.SPECIAL, w, h)) return; // 自訂位置、大小（HUD 介面設定）
        try { draw(g, mc, p, w, h); } finally { com.provinggrounds.ui.HudLayout.pop(g); }
    }

    private static void draw(GuiGraphics g, Minecraft mc, LocalPlayer p, int w, int h) {
        int x = w / 2 + 91 + 8, y = h - 22; // 原版快捷欄右邊，隔 8 像素
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, 0, 0, 21, 22);       // 一格的左邊框與格子
        g.blit(WIDGETS, x + 21, y, 181, 0, 1, 22); // 右邊框
        RenderSystem.disableBlend();
        ItemStack stack = SpecialSlot.get(p);
        Font font = mc.font;
        int cd = domCooldown(stack);
        boolean used = cd > 0;
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 3, y + 3);
            g.renderItemDecorations(font, stack, x + 3, y + 3);
            if (used) g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 3, y + 3, x + 19, y + 19, 0xA0000000); // 佔點：冷卻中
        }
        String key = ClientSpecial.USE_KEY.getTranslatedKeyMessage().getString();
        String hint = null;
        if (holdingShield(p)) hint = "[" + key + "] 衝撞";
        else if (pearlFlying) hint = "[" + key + "] 傳送";
        else if (cd == 1) hint = "下回合可用";
        else if (cd > 1) hint = "冷卻剩 " + cd + " 回合";
        else if (!stack.isEmpty()) hint = "[" + key + "] 使用";
        if (hint == null) return;
        g.pose().pushPose();
        float s = 0.75f;
        g.pose().scale(s, s, 1);
        int tw = font.width(hint);
        float cx = (x + 11) / s, ty = (y - 9) / s;
        int tx = Math.round(cx - tw / 2f);
        g.fill(tx - 2, Math.round(ty) - 1, tx + tw + 2, Math.round(ty) + 9, 0x80000000);
        g.drawString(font, hint, tx, Math.round(ty), 0xFFFFE070, false);
        g.pose().popPose();
    }

    private static boolean holdingShield(LocalPlayer p) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(SpecialRegistry.SHIELD_HELD.get())) return true;
        return false;
    }
}
