package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.br.Backpack;
import com.provinggrounds.modes.br.BackpackSlot;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: BackpackOverlay.java
 * 目的: 背包系統的畫面（計畫書 B3，只在客戶端）：
 *   - 物品欄畫面：在副手格上方畫出「背包」欄的格子框；空的時候顯示淡淡的背包圖示。
 *   - 所有容器畫面（BR 參賽中）：主背包鎖定的格子蓋上灰色遮罩與鎖頭；快捷欄第 1～5 格空著時顯示該格的用途圖示。
 *   真正的限制在伺服器（SlotMixin、HotbarRules），這裡只負責顯示。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class BackpackOverlay {

    private BackpackOverlay() {}

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft() + BackpackSlot.X - 1, y = screen.getGuiTop() + BackpackSlot.Y - 1;
        // 原版格子的樣子：左上深色、右下白色、中間灰色
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && Backpack.container(mc.player).isEmpty()) {
            g.renderItem(new ItemStack(PGBlocks.BACKPACK_1.get()), x + 1, y + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xA08B8B8B); // 蓋一層讓圖示變淡
            g.pose().popPose();
        }
        // 特殊物品欄（背包欄正上方，計畫書 4 的 B1）
        int sx = screen.getGuiLeft() + com.provinggrounds.special.SpecialSlot.X - 1, sy = screen.getGuiTop() + com.provinggrounds.special.SpecialSlot.Y - 1;
        g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
        g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        if (mc.player != null && com.provinggrounds.special.SpecialSlot.get(mc.player).isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(gui("slot_special"), sx + 1, sy + 1, 0, 0, 16, 16, 16, 16);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Backpack.clientRulesActive) return;
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // 蓋在物品圖示上面
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (Slot s : screen.getMenu().slots) {
            if (!(s.container instanceof Inventory)) continue;
            int idx = s.getContainerSlot();
            if (Backpack.locked(mc.player, idx)) {
                // 鎖定格：灰色遮罩＋鎖頭
                g.fill(s.x, s.y, s.x + 16, s.y + 16, 0xC0303030);
                g.blit(ICON_LOCKED, s.x, s.y, 0, 0, 16, 16, 16, 16);
            } else if (idx >= 0 && idx < SLOT_ICONS.length && !s.hasItem()) {
                // 快捷欄第 1～5 格（專用格）空著時：顯示這格放什麼
                g.blit(SLOT_ICONS[idx], s.x, s.y, 0, 0, 16, 16, 16, 16);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private static net.minecraft.resources.ResourceLocation gui(String name) {
        return new net.minecraft.resources.ResourceLocation(ProvingGroundsMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** 快捷欄第 1～5 格的提示圖示（tools/make_slot_icons.ps1 產生）：槍、槍、近戰武器、補品、投擲物。 */
    private static final net.minecraft.resources.ResourceLocation[] SLOT_ICONS = {
        gui("slot_large"), gui("slot_large"), gui("slot_melee"), gui("slot_healing"), gui("slot_throwable") };
    private static final net.minecraft.resources.ResourceLocation ICON_LOCKED = gui("slot_locked");
}
