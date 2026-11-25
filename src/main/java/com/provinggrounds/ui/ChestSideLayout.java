package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.ChestExtraSlots;
import com.provinggrounds.modes.br.Backpack;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.SpecialSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 檔案: ChestSideLayout.java
 * 目的: 計畫書 7 A4（取代計畫書 6 B5）：打開箱子（所有 ChestMenu 畫面：箱子、木桶、物資箱、終界箱…）時，
 *   左邊是跟單純開物品欄一模一樣的背包（玩家模型、防具、背包 27 格＋快捷欄、副手、背包欄、特殊物品欄），右邊是箱子。
 *   兩塊的位置可以在「HUD 介面設定」的「箱子畫面」拖曳（HudLayout.El.CHEST_INV／CHEST_BOX，存在同一個設定檔）。
 *   防具等額外格子由 ChestMenuMixin 加在最後（ChestExtraSlots）。
 *   做法：畫面初始化後用反射改格子座標（Slot.x／y 是 final：SRG f_40220_／f_40221_）與畫面範圍，
 *   背景由 ContainerScreenBgMixin 取消原版貼圖後呼叫 drawBackground 自己畫。畫面太窄放不下兩塊時維持原版排法（額外格子藏起來）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ChestSideLayout {

    private ChestSideLayout() {}

    /** 背包面板（跟原版物品欄一樣大）與箱子面板的寬度。 */
    public static final int INV_W = 176, INV_H = 166, BOX_W = 176;
    /** HUD 介面設定裡箱子方框的高度（以 6 排的大箱子為準）。 */
    public static final int BOX_EDIT_H = boxHeight(6);
    private static final int HIDDEN = -10000;

    private static final Set<AbstractContainerScreen<?>> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    /** 畫面上兩塊面板的左上角（相對於 leftPos／topPos）。 */
    private static int invOx, invOy, boxOx, boxOy;

    private static Field slotX, slotY, imageWidth, imageHeight, leftPos, topPos, titleX, titleY, invX, invY;
    private static boolean failed;

    public static int boxHeight(int rows) { return 18 + rows * 18 + 7; }

    private static boolean fields() {
        if (failed) return false;
        if (slotX != null) return true;
        try {
            slotX = ObfuscationReflectionHelper.findField(Slot.class, "f_40220_");
            slotY = ObfuscationReflectionHelper.findField(Slot.class, "f_40221_");
            Class<?> s = AbstractContainerScreen.class;
            imageWidth = ObfuscationReflectionHelper.findField(s, "f_97726_");
            imageHeight = ObfuscationReflectionHelper.findField(s, "f_97727_");
            titleX = ObfuscationReflectionHelper.findField(s, "f_97728_");
            titleY = ObfuscationReflectionHelper.findField(s, "f_97729_");
            invX = ObfuscationReflectionHelper.findField(s, "f_97730_");
            invY = ObfuscationReflectionHelper.findField(s, "f_97731_");
            leftPos = ObfuscationReflectionHelper.findField(s, "f_97735_");
            topPos = ObfuscationReflectionHelper.findField(s, "f_97736_");
            return true;
        } catch (RuntimeException e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("Chest layout unavailable: {}", e.toString());
            return false;
        }
    }

    public static boolean active(AbstractContainerScreen<?> screen) {
        return ACTIVE.contains(screen);
    }

    private static void place(Slot s, int x, int y) throws IllegalAccessException {
        slotX.setInt(s, x);
        slotY.setInt(s, y);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ContainerScreen cs) || cs.getClass() != ContainerScreen.class) return;
        ChestMenu menu = cs.getMenu();
        if (!fields()) return;
        int rows = menu.getRowCount();
        int chestSlots = rows * 9, extraStart = chestSlots + 36;
        boolean hasExtra = menu.slots.size() >= extraStart + ChestExtraSlots.COUNT;
        try {
            int sw = cs.width, sh = cs.height;
            boolean fits = sw >= INV_W + BOX_W + 8 && sh >= INV_H + 4;
            if (!fits || !hasExtra) { // 放不下：原版排法，額外格子藏起來
                for (int i = extraStart; i < menu.slots.size(); i++) place(menu.slots.get(i), HIDDEN, HIDDEN);
                ACTIVE.remove(cs);
                return;
            }
            HudLayout.Box ib = HudLayout.box(HudLayout.El.CHEST_INV, sw, sh);
            HudLayout.Box bb = HudLayout.box(HudLayout.El.CHEST_BOX, sw, sh);
            int bh = boxHeight(rows);
            int bx = bb.x(), by = Math.min(bb.y(), sh - bh);
            int left = Math.min(ib.x(), bx), top = Math.min(ib.y(), by);
            int right = Math.max(ib.x() + INV_W, bx + BOX_W), bottom = Math.max(ib.y() + INV_H, by + bh);
            invOx = ib.x() - left; invOy = ib.y() - top;
            boxOx = bx - left; boxOy = by - top;

            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                if (i < chestSlots) {
                    place(s, boxOx + 8 + (i % 9) * 18, boxOy + 18 + (i / 9) * 18);
                } else if (i < extraStart) {
                    int j = i - chestSlots;
                    if (j < 27) place(s, invOx + 8 + (j % 9) * 18, invOy + 84 + (j / 9) * 18);
                    else place(s, invOx + 8 + (j - 27) * 18, invOy + 142);
                } else {
                    int k = i - extraStart; // 頭、胸、腿、腳、副手、背包欄、特殊物品欄
                    if (k < 4) place(s, invOx + 8, invOy + 8 + k * 18);
                    else if (k == 4) place(s, invOx + 77, invOy + 62);
                    else if (k == 5) place(s, invOx + com.provinggrounds.modes.br.BackpackSlot.X, invOy + com.provinggrounds.modes.br.BackpackSlot.Y);
                    else if (k == 6) {
                        if (com.provinggrounds.special.client.SpecialHotbar.modeOn()) place(s, invOx + SpecialSlot.X, invOy + SpecialSlot.Y);
                        else place(s, HIDDEN, HIDDEN); // 特殊物品模式關閉：跟物品欄畫面一樣不顯示
                    } else place(s, HIDDEN, HIDDEN);
                }
            }
            imageWidth.setInt(cs, right - left);
            imageHeight.setInt(cs, bottom - top);
            leftPos.setInt(cs, left);
            topPos.setInt(cs, top);
            titleX.setInt(cs, boxOx + 8);
            titleY.setInt(cs, boxOy + 6);
            invX.setInt(cs, HIDDEN); // 原版物品欄畫面沒有「物品欄」字樣
            invY.setInt(cs, HIDDEN);
            ACTIVE.add(cs);
        } catch (IllegalAccessException | RuntimeException e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("Chest layout failed: {}", e.toString());
        }
    }

    private static final ResourceLocation SLOT_SPECIAL = new ResourceLocation(ProvingGroundsMod.MOD_ID, "textures/gui/slot_special.png");

    /** 背景：背包面板（玩家模型的黑框、格子凹槽）與箱子面板。 */
    public static void drawBackground(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        ChestMenu menu = (ChestMenu) screen.getMenu();
        int ix = left + invOx, iy = top + invOy;
        panel(g, ix, iy, INV_W, INV_H);
        panel(g, left + boxOx, top + boxOy, BOX_W, boxHeight(menu.getRowCount()));
        for (Slot s : menu.slots) {
            if (s.x <= HIDDEN / 2) continue;
            int x = left + s.x - 1, y = top + s.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF373737);
            g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
        // 玩家模型（跟物品欄畫面同一個位置與大小，會跟著滑鼠轉頭）
        g.fill(ix + 25, iy + 7, ix + 76, iy + 79, 0xFF000000);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, ix + 51, iy + 75, 30,
                (float) (ix + 51 - mouseX), (float) (iy + 75 - 50 - mouseY), mc.player);
            // 背包欄、特殊物品欄空著時的提示圖示（跟物品欄畫面一樣）
            int extraStart = menu.getRowCount() * 9 + 36;
            if (menu.slots.size() >= extraStart + ChestExtraSlots.COUNT) {
                Slot pack = menu.slots.get(extraStart + 5), special = menu.slots.get(extraStart + 6);
                if (!pack.hasItem() && Backpack.container(mc.player).isEmpty()) {
                    g.renderItem(new ItemStack(PGBlocks.BACKPACK_1.get()), left + pack.x, top + pack.y);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(left + pack.x, top + pack.y, left + pack.x + 16, top + pack.y + 16, 0xA08B8B8B);
                    g.pose().popPose();
                }
                if (special.x > HIDDEN / 2 && !special.hasItem()) {
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    g.blit(SLOT_SPECIAL, left + special.x, top + special.y, 0, 0, 16, 16, 16, 16);
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                }
            }
        }
    }

    /** 原版介面面板：黑框、左上亮邊、右下暗邊、淺灰底。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }
}
