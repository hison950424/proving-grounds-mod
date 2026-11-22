package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunClass;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGClientConfig;
import com.provinggrounds.modes.br.Backpack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: CompatHighlight.java
 * 目的: 配件相容指示器（第三份計畫書 D，只在客戶端）。在任何容器畫面（物品欄、箱子、空投、墓碑…）中：
 *   滑鼠停在槍上 → 畫面中所有「能裝在這把槍上的配件」格子背景變成半透明亮橘黃色；
 *   滑鼠停在配件上 → 所有「能裝這個配件的槍」格子變色。
 *   滑鼠停的那一格、空格、背包鎖定格不上色；子彈、補品、防具等都不會觸發也不會被標示。
 *   選做：滑鼠停在空格、游標上拿著槍或配件時，改用游標上的物品當來源。
 *   相容判斷只透過槍械適配層 GunCompat.isAttachment／canAttach（TACZ 自己的相容資料，不寫死對照表）。
 *   畫在 ContainerScreenEvent.Render.Background（格子背景之後、物品圖示之前），所以不會蓋住物品、數量與耐久條。
 *   只在「來源物品、滑鼠所在格子或畫面內容（menu 的 stateId）」改變時重新計算，每幀只畫色塊。
 *   客戶端設定：compat_highlight_enabled、compat_highlight_color、compat_highlight_opacity。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class CompatHighlight {

    private static List<Slot> matches = List.of();
    private static int cacheSlot = Integer.MIN_VALUE, cacheState = -1;
    private static ItemStack cacheSource = ItemStack.EMPTY;
    private static Object cacheMenu;

    private CompatHighlight() {}

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        int color;
        try {
            if (!PGClientConfig.CLIENT.compatHighlightEnabled.get()) return;
            color = argb(PGClientConfig.CLIENT.compatHighlightColor.get(), PGClientConfig.CLIENT.compatHighlightOpacity.get());
        } catch (IllegalStateException e) {
            color = 0x66FFB300;
        }
        Slot hovered = screen.getSlotUnderMouse();
        ItemStack source = hovered != null && hovered.hasItem() ? hovered.getItem() : screen.getMenu().getCarried();
        int slotKey = hovered != null && hovered.hasItem() ? hovered.index : -1;
        int state = screen.getMenu().getStateId();
        if (screen.getMenu() != cacheMenu || slotKey != cacheSlot || state != cacheState || !ItemStack.matches(source, cacheSource)) {
            cacheMenu = screen.getMenu();
            cacheSlot = slotKey;
            cacheState = state;
            cacheSource = source.copy();
            matches = compute(screen, hovered, source);
        }
        if (matches.isEmpty()) return;
        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (Slot s : matches) {
            if (!s.isActive()) continue;
            g.fill(left + s.x, top + s.y, left + s.x + 16, top + s.y + 16, color);
        }
    }

    /** 關掉畫面時清掉暫存。 */
    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        matches = List.of();
        cacheMenu = null;
        cacheSource = ItemStack.EMPTY;
    }

    private static List<Slot> compute(AbstractContainerScreen<?> screen, Slot hovered, ItemStack source) {
        if (source.isEmpty()) return List.of();
        boolean sourceIsGun = GunCompat.classify(source) != GunClass.NONE;
        boolean sourceIsAttachment = !sourceIsGun && GunCompat.isAttachment(source);
        if (!sourceIsGun && !sourceIsAttachment) return List.of();
        Minecraft mc = Minecraft.getInstance();
        List<Slot> out = new ArrayList<>();
        for (Slot s : screen.getMenu().slots) {
            if (s == hovered || !s.hasItem()) continue;
            if (s.container instanceof Inventory && mc.player != null && Backpack.locked(mc.player, s.getContainerSlot())) continue;
            ItemStack other = s.getItem();
            boolean hit = sourceIsGun
                ? GunCompat.isAttachment(other) && GunCompat.canAttach(source, other)
                : GunCompat.classify(other) != GunClass.NONE && GunCompat.canAttach(other, source);
            if (hit) out.add(s);
        }
        return out;
    }

    /** "#FFB300" ＋ 不透明度 → ARGB；格式錯誤時用預設顏色。 */
    private static int argb(String hex, double opacity) {
        int rgb = 0xFFB300;
        try {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            rgb = Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            // 用預設亮橘黃
        }
        int a = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        return (a << 24) | rgb;
    }
}
