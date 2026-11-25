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
 *   佔點（計畫書 8）：長按 5 改成「特殊物品輪盤」（三個裝備＋一次性的額外特殊物品，選好放進特殊物品格），
 *   投擲物輪盤改成長按 6（選好一樣放到第 5 格）。
 *   輪盤是疊在遊戲畫面上的 HUD，不是畫面（Screen），所以按住時仍可用 WASD 移動；
 *   開啟時放開滑鼠游標（視角不會轉），關閉時再鎖回去。倒地、沒有該類物品、開著其他畫面時不開。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWheel {

    private static final int RADIUS = 52;

    /** 輪盤上的一種物品：代表的那一疊在第幾格（特殊物品輪盤 = 第幾個）、右下角的字、冷卻比例（0 = 可以用）。 */
    private record Entry(ItemStack stack, int slot, String text, float cooling) {
        Entry(ItemStack stack, int slot, int total) { this(stack, slot, String.valueOf(total), 0f); }
    }

    /** 三種輪盤：補品、投擲物、佔點的特殊物品。 */
    private static final int HEAL_W = 0, THROW_W = 1, SPECIAL_W = 2;
    private static final int[] holdTicks = new int[3];
    private static int openKind = -1;   // -1 = 沒開
    private static int openTarget = -1; // 開著的輪盤看的是哪個快捷欄按鍵（0～8）
    private static List<Entry> entries = List.of();
    private static int hovered = -1;

    private ClientWheel() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { close(false); return; }

        if (openKind >= 0) {
            KeyMapping key = mc.options.keyHotbarSlots[openTarget];
            if (mc.screen != null || !allowed(mc) || mc.mouseHandler.isMouseGrabbed()) { close(false); return; }
            // 輪盤開著時不能使用物品（不能邊選邊吃補品、邊丟投擲物）
            while (mc.options.keyUse.consumeClick()) { /* 丟掉 */ }
            mc.options.keyUse.setDown(false);
            if (mc.player.isUsingItem()) mc.player.stopUsingItem();
            entries = entriesOf(mc, openKind);
            if (entries.isEmpty()) { close(false); return; }
            if (!key.isDown()) close(true);
            return;
        }

        int threshold = Math.max(1, (int) Math.round(holdSeconds() * 20));
        for (int kind = 0; kind < 3; kind++) {
            int k = keyOf(kind);
            if (k < 0 || !mc.options.keyHotbarSlots[k].isDown()) { holdTicks[kind] = 0; continue; }
            holdTicks[kind]++;
            if (holdTicks[kind] == threshold && mc.screen == null && allowed(mc)) {
                List<Entry> list = entriesOf(mc, kind);
                if (!list.isEmpty()) {
                    entries = list;
                    openKind = kind;
                    openTarget = k;
                    hovered = -1;
                    mc.mouseHandler.releaseMouse(); // 游標放開（回到畫面中央），用來指向扇形
                    if (kind != SPECIAL_W) {
                        // 長按開輪盤時，取消手上正在使用的補品／投擲物（客戶端停止動作，伺服器也中斷，不會觸發丟出）
                        mc.player.stopUsingItem();
                        PGNetwork.sendToServer(new WheelSelectPacket(slotOf(kind), WheelSelectPacket.OPENED));
                    }
                }
            }
        }
    }

    /** 佔點的特殊物品裝備進行中（計畫書 8）。 */
    private static boolean domGear() {
        return com.provinggrounds.client.ClientDomGear.inMatch() && com.provinggrounds.client.ClientArenaState.get().kind() == 2;
    }

    /** 這種輪盤要長按哪個快捷欄按鍵（0～8；-1 = 沒有）。佔點：特殊物品 = 5、投擲物 = 6。 */
    private static int keyOf(int kind) {
        return switch (kind) {
            case HEAL_W -> HotbarRules.HEAL;
            case THROW_W -> domGear() ? HotbarRules.THROW + 1 : HotbarRules.THROW;
            default -> domGear() ? HotbarRules.THROW : -1;
        };
    }

    /** 選好的物品放到哪一格（補品第 4 格、投擲物第 5 格）。 */
    private static int slotOf(int kind) { return kind == HEAL_W ? HotbarRules.HEAL : HotbarRules.THROW; }

    private static List<Entry> entriesOf(Minecraft mc, int kind) {
        if (kind == SPECIAL_W) return specialEntries();
        return collect(mc.player.getInventory(), kind == HEAL_W ? HotbarRules.HEALING : HotbarRules.THROWABLE, slotOf(kind));
    }

    /** 特殊物品輪盤：三個裝備（冷卻中的變暗、顯示秒數）＋一次性的額外特殊物品。 */
    private static List<Entry> specialEntries() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < com.provinggrounds.special.DomGear.SLOTS; i++) {
            com.provinggrounds.special.SpecialType t = com.provinggrounds.client.ClientDomGear.gear(i);
            if (t == null) continue;
            int left = com.provinggrounds.client.ClientDomGear.cooldownLeft(i);
            float cool = left / (float) com.provinggrounds.client.ClientDomGear.cooldownTotal(i);
            out.add(new Entry(new ItemStack(com.provinggrounds.special.SpecialRegistry.item(t)), i, left > 0 ? (left + 19) / 20 + "s" : "", Math.min(1f, cool)));
        }
        List<int[]> ex = com.provinggrounds.client.ClientDomGear.extras();
        for (int i = 0; i < ex.size(); i++) {
            com.provinggrounds.special.SpecialType t = com.provinggrounds.client.ClientDomGear.extraType(ex.get(i));
            if (t != null) out.add(new Entry(new ItemStack(com.provinggrounds.special.SpecialRegistry.item(t)), com.provinggrounds.special.DomGear.SLOTS + i, "×1", 0f));
        }
        return out;
    }

    /** BR 進行中自己在地面存活（沒倒地），或團隊模式中（計畫書 6 C2），而且不是創造／旁觀模式時能開。 */
    private static boolean allowed(Minecraft mc) {
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;
        if (com.provinggrounds.special.ActionLock.brainwashed(mc.player)) return false; // 洗腦中不能切換（計畫書 6 E1）
        return ClientBRState.get().selfState() == ClientBRState.SELF_ALIVE
            || (com.provinggrounds.client.ClientArenaState.active() && mc.player.isAlive());
    }

    private static void close(boolean select) {
        if (openKind < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (select && hovered >= 0 && hovered < entries.size()) {
            if (openKind == SPECIAL_W) {
                PGNetwork.sendToServer(new com.provinggrounds.network.DomGearActionPacket(
                    com.provinggrounds.network.DomGearActionPacket.Action.SELECT, entries.get(hovered).slot(), 0));
            } else {
                PGNetwork.sendToServer(new WheelSelectPacket(slotOf(openKind), entries.get(hovered).slot()));
            }
        }
        openKind = -1;
        openTarget = -1;
        entries = List.of();
        hovered = -1;
        java.util.Arrays.fill(holdTicks, 0);
        if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
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
        if (openKind < 0 || entries.isEmpty()) return;
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
            if (e.cooling() > 0) { // 冷卻中：由下往上蓋一層暗色（剩越多蓋越多）
                int ch = Math.max(1, Math.round(16 * e.cooling()));
                g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 16 - ch, x + 16, y + 16, 0xA0000000);
            }
            g.renderItemDecorations(mc.font, e.stack(), x, y, e.text());
        }
        String title = openKind == SPECIAL_W ? "特殊物品" : Tr.t(openKind == HEAL_W ? "wheel.healing" : "wheel.throwable");
        g.drawCenteredString(mc.font, title, cx, cy - 4, 0xFFFFFF);
        if (hovered >= 0) {
            Entry h = entries.get(hovered);
            String name = h.stack().getHoverName().getString();
            if (openKind == SPECIAL_W) name += h.slot() >= com.provinggrounds.special.DomGear.SLOTS ? "（一次性）" : h.cooling() > 0 ? "（冷卻 " + h.text() + "）" : "";
            g.drawCenteredString(mc.font, name, cx, cy + RADIUS + 26, 0xFFFF80);
        }
    }
}
