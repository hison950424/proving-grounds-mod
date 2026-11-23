package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.br.Backpack;
import com.provinggrounds.modes.br.BackpackSlot;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.SpecialSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * 檔案: ClientCreativeSlots.java
 * 目的: 創造模式物品欄（「物品欄」分頁）也看得到並能使用背包欄與特殊物品欄（只在客戶端）。
 *   原版創造模式畫面會把物品欄的每一格包一層（SlotWrapper）重新排位置，但不認得我們加的兩格，
 *   結果擠到快捷欄上面去。這裡把它們換成擺在右邊盔甲欄旁邊的新包裝：特殊物品欄在上、背包欄在下（跟生存模式一樣的順序）。
 *   創造模式改這兩格時，伺服器端由 CreativeSlotMixin 接手。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCreativeSlots {

    /** 創造模式「物品欄」分頁裡的位置（右邊盔甲欄 108 的右邊）。 */
    private static final int X = 127, SPECIAL_Y = 6, BACKPACK_Y = 33;

    private static Constructor<?> wrapperCtor;
    private static Field targetField;
    private static boolean failed;

    private ClientCreativeSlots() {}

    private static boolean init() {
        if (wrapperCtor != null) return true;
        if (failed) return false;
        try {
            Class<?> c = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper");
            wrapperCtor = c.getDeclaredConstructor(Slot.class, int.class, int.class, int.class);
            wrapperCtor.setAccessible(true);
            targetField = ObfuscationReflectionHelper.findField(c, "f_98655_");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("Creative backpack/special slots unavailable: {}", e.toString());
            return false;
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !init()) return;
        var slots = screen.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (!wrapperCtor.getDeclaringClass().isInstance(s)) continue;
            Slot target;
            try { target = (Slot) targetField.get(s); } catch (IllegalAccessException e) { continue; }
            int y = target instanceof SpecialSlot.MenuSlot ? SPECIAL_Y : target instanceof BackpackSlot ? BACKPACK_Y : -1;
            if (y < 0 || (s.x == X && s.y == y)) continue;
            try {
                Slot moved = (Slot) wrapperCtor.newInstance(target, target.index, X, y);
                moved.index = i;
                slots.set(i, moved);
            } catch (ReflectiveOperationException e) {
                return;
            }
        }
    }

    /** 畫兩格的外框；空著時顯示淡淡的提示圖示。 */
    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen)) return;
        boolean found = false;
        for (Slot s : screen.getMenu().slots) if (s.x == X && (s.y == SPECIAL_Y || s.y == BACKPACK_Y)) { found = true; break; }
        if (!found) return; // 不在「物品欄」分頁
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        frame(g, left + X - 1, top + BACKPACK_Y - 1);
        if (Backpack.container(mc.player).isEmpty()) faded(g, new ItemStack(PGBlocks.BACKPACK_1.get()), left + X, top + BACKPACK_Y);
        if (com.provinggrounds.special.client.SpecialHotbar.modeOn()) frame(g, left + X - 1, top + SPECIAL_Y - 1);
    }

    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private static void faded(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + 16, y + 16, 0xA08B8B8B);
        g.pose().popPose();
    }
}
