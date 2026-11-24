package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
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
 * 目的: 計畫書 6 B5：打開箱子（原版箱子、木桶、終界箱、我們的物資箱等所有 ChestMenu 畫面）時，
 *   改成左邊箱子、右邊自己的物品欄（上面 3 排背包、下面快捷欄，跟物品欄畫面一樣的排法）。
 *   做法：畫面初始化後用反射改格子座標（Slot.x／y 是 final：SRG f_40220_／f_40221_）與畫面寬高，
 *   背景由 ContainerScreenBgMixin 取消原版貼圖後呼叫 drawBackground 自己畫（原版格子樣式的色塊）。
 *   BackpackOverlay、CompatHighlight 等用的是格子座標，會跟著移動。畫面太窄放不下時維持原版排法。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ChestSideLayout {

    private ChestSideLayout() {}

    /** 右邊面板的起點（左邊面板寬 LEFT_W）。 */
    private static final int LEFT_W = 176, GAP = 2, RIGHT_X = LEFT_W + GAP, RIGHT_W = 176;
    private static final int TOTAL_W = RIGHT_X + RIGHT_W;

    private static final Set<AbstractContainerScreen<?>> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private static Field slotX, slotY, imageWidth, imageHeight, leftPos, topPos, titleX, titleY, invX, invY;
    private static boolean failed;

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
            ProvingGroundsMod.LOGGER.warn("Chest side layout unavailable: {}", e.toString());
            return false;
        }
    }

    public static boolean active(AbstractContainerScreen<?> screen) {
        return ACTIVE.contains(screen);
    }

    /** 箱子排數（3 或 6 排；其他大小也可以）。 */
    private static int rows(ChestMenu menu) { return menu.getRowCount(); }

    private static int height(int rows) { return Math.max(18 + rows * 18, 18 + 3 * 18 + 4 + 18) + 7; }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ContainerScreen cs) || cs.getClass() != ContainerScreen.class) return;
        ChestMenu menu = cs.getMenu();
        if (!fields()) return;
        int rows = rows(menu);
        int w = TOTAL_W, h = height(rows);
        if (cs.width < w + 8 || cs.height < h + 8) { ACTIVE.remove(cs); return; } // 放不下：原版排法
        try {
            int chestSlots = rows * 9;
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                int x, y;
                if (i < chestSlots) {
                    x = 8 + (i % 9) * 18;
                    y = 18 + (i / 9) * 18;
                } else {
                    int j = i - chestSlots;
                    if (j < 27) { x = RIGHT_X + 8 + (j % 9) * 18; y = 18 + (j / 9) * 18; }
                    else { x = RIGHT_X + 8 + (j - 27) * 18; y = 18 + 3 * 18 + 4; }
                }
                slotX.setInt(s, x);
                slotY.setInt(s, y);
            }
            imageWidth.setInt(cs, w);
            imageHeight.setInt(cs, h);
            leftPos.setInt(cs, (cs.width - w) / 2);
            topPos.setInt(cs, (cs.height - h) / 2);
            titleX.setInt(cs, 8);
            titleY.setInt(cs, 6);
            invX.setInt(cs, RIGHT_X + 8);
            invY.setInt(cs, 6);
            ACTIVE.add(cs);
        } catch (IllegalAccessException | RuntimeException e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("Chest side layout failed: {}", e.toString());
        }
    }

    /** 背景：兩塊原版樣式的面板＋每個格子的凹槽。 */
    public static void drawBackground(AbstractContainerScreen<?> screen, GuiGraphics g) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop(), h = screen.getYSize();
        panel(g, left, top, LEFT_W, h);
        panel(g, left + RIGHT_X, top, RIGHT_W, h);
        for (Slot s : screen.getMenu().slots) {
            int x = left + s.x - 1, y = top + s.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF373737);
            g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
    }

    /** 原版介面面板：黑框、左上亮邊、右下暗邊、淺灰底。 */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }
}
