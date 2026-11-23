package com.provinggrounds.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.TombstoneMenu;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: TombstoneScreen.java
 * 目的: 墓碑畫面（只在客戶端）：跟物品欄一樣的分區。
 *   上半：死者的防具（頭、胸、腿、腳）、副手、背包、特殊物品，下面是死者的背包 27 格與快捷欄 9 格；
 *   下半：自己的物品欄與快捷欄。空著的格子顯示淡淡的提示圖示（防具、背包、特殊物品）。
 *   沒有用材質圖，背景與格子框直接用色塊畫（原版物品欄的配色）。
 */
public class TombstoneScreen extends AbstractContainerScreen<TombstoneMenu> {

    private static final int BG = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, EDGE = 0xFF000000;
    private static final ResourceLocation[] ARMOR_ICONS = {
        new ResourceLocation("textures/item/empty_armor_slot_helmet.png"),
        new ResourceLocation("textures/item/empty_armor_slot_chestplate.png"),
        new ResourceLocation("textures/item/empty_armor_slot_leggings.png"),
        new ResourceLocation("textures/item/empty_armor_slot_boots.png") };
    private static final ResourceLocation OFFHAND_ICON = new ResourceLocation("textures/item/empty_armor_slot_shield.png");
    private static final ResourceLocation SPECIAL_ICON = new ResourceLocation(ProvingGroundsMod.MOD_ID, "textures/gui/slot_special.png");

    public TombstoneScreen(TombstoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 208;
        inventoryLabelY = TombstoneMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // 外框：跟原版物品欄一樣的立體灰框
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        g.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, DARK);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, BG);
        // 墓碑與自己物品欄之間的分隔線
        int sep = y + TombstoneMenu.HOTBAR_Y + 20;
        g.fill(x + 6, sep, x + imageWidth - 6, sep + 1, DARK);
        // 墓碑背包與快捷欄之間空一點（跟原版一樣）
        for (Slot s : menu.slots) slotFrame(g, x + s.x - 1, y + s.y - 1);
        // 空格提示圖示
        RenderSystem.enableBlend();
        for (int i = 0; i < 4; i++) emptyIcon(g, TombstoneMenu.ARMOR + i, ARMOR_ICONS[i], x, y);
        emptyIcon(g, TombstoneMenu.OFFHAND, OFFHAND_ICON, x, y);
        emptyIcon(g, TombstoneMenu.SPECIAL, SPECIAL_ICON, x, y);
        RenderSystem.disableBlend();
        Slot pack = menu.slots.get(TombstoneMenu.BACKPACK);
        if (!pack.hasItem()) {
            int px = x + pack.x, py = y + pack.y;
            g.renderItem(new ItemStack(PGBlocks.BACKPACK_1.get()), px, py);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(px, py, px + 16, py + 16, 0xA08B8B8B);
            g.pose().popPose();
        }
    }

    private void emptyIcon(GuiGraphics g, int index, ResourceLocation icon, int x, int y) {
        Slot s = menu.slots.get(index);
        if (!s.hasItem()) g.blit(icon, x + s.x, y + s.y, 0, 0, 16, 16, 16, 16);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, Component.literal("背包"), TombstoneMenu.BACKPACK_X - 26, TombstoneMenu.ROW_Y + 5, 0x606060, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
