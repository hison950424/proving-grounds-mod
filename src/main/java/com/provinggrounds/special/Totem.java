package com.provinggrounds.special;

import com.provinggrounds.events.OffhandEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 檔案: Totem.java
 * 目的: 不死圖騰（計畫書 4 的 23 號）：使用後 totem_seconds（15 秒）內副手有原版不死圖騰，時間到銷毀；
 *   圖騰被觸發（致命傷）時原版會自己用掉。副手由系統設定（OffhandEvents.setSystemOffhand），不受「不能放副手」限制。
 *   原版的圖騰判定在死亡之前，所以會擋掉倒地：致命傷被圖騰擋下就不進倒地、也不死亡。
 *   時間用效果 provinggrounds:totem_held 計算（沒有粒子，會顯示圖示）。
 */
public final class Totem {

    private Totem() {}

    public static void give(ServerPlayer p) {
        OffhandEvents.setSystemOffhand(p, new ItemStack(Items.TOTEM_OF_UNDYING));
        Engines.effect(p, SpecialRegistry.TOTEM_HELD.get(), SpecialConfig.ticks(SpecialConfig.get().totemSeconds), 0);
    }

    /** 每 tick：時間到 → 拿掉圖騰；圖騰被用掉 → 拿掉效果。 */
    public static void tick(ServerPlayer p) {
        boolean has = p.hasEffect(SpecialRegistry.TOTEM_HELD.get());
        ItemStack off = p.getInventory().offhand.get(0);
        boolean holding = off.is(Items.TOTEM_OF_UNDYING) && off.hasTag() && off.getTag().getBoolean(OffhandEvents.SYSTEM_TAG);
        if (has && !holding) p.removeEffect(SpecialRegistry.TOTEM_HELD.get());
        else if (!has && holding) remove(p);
    }

    public static void remove(ServerPlayer p) {
        ItemStack off = p.getInventory().offhand.get(0);
        if (off.is(Items.TOTEM_OF_UNDYING) && off.hasTag() && off.getTag().getBoolean(OffhandEvents.SYSTEM_TAG)) {
            p.getInventory().offhand.set(0, ItemStack.EMPTY);
            p.inventoryMenu.broadcastChanges();
        }
    }
}
