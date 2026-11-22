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
 *   - 所有容器畫面：主背包鎖定的格子蓋上灰色遮罩。
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
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Backpack.clientRulesActive) return;
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // 蓋在物品圖示上面
        for (Slot s : screen.getMenu().slots) {
            if (s.container instanceof Inventory && Backpack.locked(mc.player, s.getContainerSlot())) {
                g.fill(s.x, s.y, s.x + 16, s.y + 16, 0xC0303030);
            }
        }
        g.pose().popPose();
    }
}
