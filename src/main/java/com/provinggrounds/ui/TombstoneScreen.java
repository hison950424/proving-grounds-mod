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
 * 目的: 墓碑畫面（只在客戶端）：左邊是自己的背包畫面（玩家模型、防具、副手、背包欄、特殊物品欄、背包、快捷欄，跟按 E 一樣），
 *   右邊是墓碑主人的背包畫面（同樣的排法；玩家模型的位置顯示墓碑主人名字）。空著的格子顯示淡淡的提示圖示。
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
        imageWidth = TombstoneMenu.W;
        imageHeight = TombstoneMenu.H;
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
        // 兩塊原版樣式的面板：左邊自己、右邊墓碑
        ChestSideLayout.panel(g, x, y, 176, TombstoneMenu.H);
        ChestSideLayout.panel(g, x + TombstoneMenu.TX, y, 176, TombstoneMenu.H);
        for (Slot s : menu.slots) slotFrame(g, x + s.x - 1, y + s.y - 1);
        // 自己的玩家模型（跟物品欄畫面一樣，會跟著滑鼠轉頭）；墓碑那邊同一個位置顯示主人名字
        g.fill(x + 25, y + 7, x + 76, y + 79, EDGE);
        if (minecraft != null && minecraft.player != null) {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 51, y + 75, 30,
                (float) (x + 51 - mouseX), (float) (y + 75 - 50 - mouseY), minecraft.player);
        }
        int tx = x + TombstoneMenu.TX;
        g.fill(tx + 25, y + 7, tx + 76, y + 79, 0xFF202020);
        g.drawCenteredString(font, "☠", tx + 50, y + 30, 0xFFA0A0A0);
        String owner = font.plainSubstrByWidth(title.getString(), 48);
        g.drawCenteredString(font, owner, tx + 50, y + 46, 0xFFFFFFFF);
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
        // 標題（墓碑主人）畫在右邊模型框裡，原版物品欄沒有「物品欄」字樣
    }
}
