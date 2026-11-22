package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.modes.br.HotbarRules;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.WheelSelectPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: ClientWheel.java
 * 目的: 補品／投擲物快捷輪盤（計畫書 B2，只在客戶端）。
 *   長按「快捷欄 4」開補品輪盤、長按「快捷欄 5」開投擲物輪盤（跟著玩家的按鍵設定，不寫死數字鍵）；
 *   長按門檻 wheel_hold_seconds（預設 0.25 秒），短按維持原版選格。
 *   輪盤列出身上（快捷欄與背包）該類別的每一種物品與總數。按住時用滑鼠指向，放開按鍵就選取，
 *   送 WheelSelectPacket 給伺服器，由伺服器驗證並搬到第 4／5 格。
 *   輪盤是疊在遊戲畫面上的 HUD，不是畫面（Screen），所以按住時仍可用 WASD 移動；
 *   開啟時放開滑鼠游標（視角不會轉），關閉時再鎖回去。倒地、沒有該類物品、開著其他畫面時不開。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWheel {

    private static final int RADIUS = 52;

    /** 輪盤上的一種物品：代表的那一疊在第幾格、總數。 */
    private record Entry(ItemStack stack, int slot, int total) {}

    private static final int[] holdTicks = new int[2];
    private static int openTarget = -1; // 3 或 4；-1 = 沒開
    private static List<Entry> entries = List.of();
    private static int hovered = -1;

    private ClientWheel() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { close(false); return; }

        if (openTarget >= 0) {
            KeyMapping key = mc.options.keyHotbarSlots[openTarget];
            if (mc.screen != null || !allowed(mc) || mc.mouseHandler.isMouseGrabbed()) { close(false); return; }
            entries = collect(mc.player.getInventory(), tagOf(openTarget), openTarget);
            if (entries.isEmpty()) { close(false); return; }
            if (!key.isDown()) close(true);
            return;
        }

        int threshold = Math.max(1, (int) Math.round(holdSeconds() * 20));
        for (int i = 0; i < 2; i++) {
            int target = HotbarRules.HEAL + i;
            if (!mc.options.keyHotbarSlots[target].isDown()) { holdTicks[i] = 0; continue; }
            holdTicks[i]++;
            if (holdTicks[i] == threshold && mc.screen == null && allowed(mc)) {
                List<Entry> list = collect(mc.player.getInventory(), tagOf(target), target);
                if (!list.isEmpty()) {
                    entries = list;
                    openTarget = target;
                    hovered = -1;
                    mc.mouseHandler.releaseMouse(); // 游標放開（回到畫面中央），用來指向扇形
                }
            }
        }
    }

    /** 只有 BR 進行中、自己在地面存活（沒倒地）且不是創造模式時能開。 */
    private static boolean allowed(Minecraft mc) {
        return ClientBRState.get().selfState() == ClientBRState.SELF_ALIVE && !mc.player.isCreative() && !mc.player.isSpectator();
    }

    private static void close(boolean select) {
        if (openTarget < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (select && hovered >= 0 && hovered < entries.size()) {
            PGNetwork.sendToServer(new WheelSelectPacket(openTarget, entries.get(hovered).slot()));
        }
        openTarget = -1;
        entries = List.of();
        hovered = -1;
        holdTicks[0] = holdTicks[1] = 0;
        if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }

    private static TagKey<Item> tagOf(int target) {
        return target == HotbarRules.HEAL ? HotbarRules.HEALING : HotbarRules.THROWABLE;
    }

    /** 身上第 0～35 格裡這一類的每一種物品（同物品同 NBT 算一種）。代表格優先用專用格本身。 */
    private static List<Entry> collect(Inventory inv, TagKey<Item> tag, int target) {
        List<ItemStack> kinds = new ArrayList<>();
        List<int[]> info = new ArrayList<>(); // [代表格, 總數]
        for (int i = 0; i <= 35; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(tag)) continue;
            int k = -1;
            for (int j = 0; j < kinds.size(); j++) if (ItemStack.isSameItemSameTags(kinds.get(j), s)) { k = j; break; }
            if (k < 0) { kinds.add(s); info.add(new int[] { i, s.getCount() }); }
            else {
                info.get(k)[1] += s.getCount();
                if (i == target) info.get(k)[0] = i;
            }
        }
        List<Entry> out = new ArrayList<>();
        for (int j = 0; j < kinds.size(); j++) out.add(new Entry(kinds.get(j), info.get(j)[0], info.get(j)[1]));
        return out;
    }

    private static double holdSeconds() {
        try {
            return PGConfig.SERVER.wheelHoldSeconds.get();
        } catch (IllegalStateException e) {
            return 0.25;
        }
    }

    // ── 畫面 ────────────────────────────────────────────────────────────────

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, width, height);

    private static void render(GuiGraphics g, int width, int height) {
        if (openTarget < 0 || entries.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2, cy = height / 2;
        double mx = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();

        int n = entries.size();
        double step = Math.PI * 2 / n;
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy < 14 * 14) {
            hovered = -1;
        } else {
            double a = Math.atan2(dy, dx) + Math.PI / 2 + step / 2; // 第 0 個在正上方
            a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            hovered = Math.min(n - 1, (int) (a / step));
        }

        g.fill(cx - RADIUS - 22, cy - RADIUS - 22, cx + RADIUS + 22, cy + RADIUS + 22, 0x70000000);
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + step * i;
            int x = cx + (int) Math.round(Math.cos(ang) * RADIUS) - 8;
            int y = cy + (int) Math.round(Math.sin(ang) * RADIUS) - 8;
            boolean hot = i == hovered;
            g.fill(x - 4, y - 4, x + 20, y + 20, hot ? 0xC0FFFFFF : 0x90303030);
            Entry e = entries.get(i);
            g.renderItem(e.stack(), x, y);
            g.renderItemDecorations(mc.font, e.stack(), x, y, String.valueOf(e.total()));
        }
        String title = Tr.t(openTarget == HotbarRules.HEAL ? "wheel.healing" : "wheel.throwable");
        g.drawCenteredString(mc.font, title, cx, cy - 4, 0xFFFFFF);
        if (hovered >= 0) {
            g.drawCenteredString(mc.font, entries.get(hovered).stack().getHoverName(), cx, cy + RADIUS + 26, 0xFFFF80);
        }
    }
}
