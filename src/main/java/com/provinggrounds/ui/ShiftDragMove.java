package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * 檔案: ShiftDragMove.java
 * 目的: 容器畫面中按住 Shift ＋ 滑鼠左鍵掃過格子，游標經過的每一格物品都會快速移動（計畫書 A12，只在客戶端）。
 *   等同於對每一格做一次 Shift＋左鍵；同一次拖移中同一格只處理一次；每 tick 最多處理 4 格，避免大量封包。
 *   游標上拿著物品時不作用（那是原版的拖曳分堆）；不按 Shift 時完全是原版行為。
 *   搬移結果一律由伺服器照原本的規則決定（容器、背包鎖定格、快捷欄規則都照常生效）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ShiftDragMove {

    private static final int MAX_PER_TICK = 4;
    private static final Set<Integer> visited = new HashSet<>();
    private static int budget = MAX_PER_TICK;
    private static boolean dragging;

    private ShiftDragMove() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        budget = MAX_PER_TICK;
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!(mc.screen instanceof AbstractContainerScreen<?>) || !left || !Screen.hasShiftDown()) {
            dragging = false;
            visited.clear();
        }
    }

    /** 按下的那一格由原版 Shift＋左鍵處理，這裡只記下來，拖移時不再重複搬。 */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasShiftDown()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        visited.clear();
        dragging = true;
        Slot slot = slotAt(screen, event.getMouseX(), event.getMouseY());
        if (slot != null) visited.add(slot.index);
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!dragging || event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasShiftDown()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !screen.getMenu().getCarried().isEmpty()) return;
        Slot slot = slotAt(screen, event.getMouseX(), event.getMouseY());
        if (slot == null || !slot.hasItem() || !slot.mayPickup(mc.player) || !visited.add(slot.index)) return;
        if (budget <= 0) { visited.remove(slot.index); return; } // 這個 tick 額度用完，下次經過再處理
        budget--;
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mx, double my) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (Slot s : screen.getMenu().slots) {
            if (!s.isActive()) continue;
            int x = left + s.x, y = top + s.y;
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return s;
        }
        return null;
    }
}
