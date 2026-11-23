package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: OffhandEvents.java
 * 目的: 任何時候、所有玩家都不能主動把物品放進副手（計畫書 4 的 A6）。
 *   1. F 鍵（主副手交換）：一律取消。
 *   2. 物品欄裡點副手格、拖曳：SlotMixin 讓副手格 mayPlace 回傳 false。
 *   3. 物品欄裡游標指著格子按 F：ContainerMenuMixin 取消。
 *   4. 其他繞過的方式（創造模式物品欄、指令等）：每 tick 檢查，副手有東西就移回背包（放不下就丟在腳下）。
 *   只有系統能放副手：系統放的物品帶 NBT 標記 SYSTEM_TAG（例如之後的不死圖騰），這種物品不會被移走。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class OffhandEvents {

    /** 系統放進副手的物品帶這個 NBT 標記（布林值 true）。 */
    public static final String SYSTEM_TAG = "pg_system_offhand";

    private OffhandEvents() {}

    /** 系統把物品放進副手（會加上標記，不會被移走）。 */
    public static void setSystemOffhand(ServerPlayer p, ItemStack stack) {
        if (!stack.isEmpty()) stack.getOrCreateTag().putBoolean(SYSTEM_TAG, true);
        p.getInventory().offhand.set(0, stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Inventory inv = p.getInventory();
        ItemStack off = inv.offhand.get(0);
        if (off.isEmpty() || (off.hasTag() && off.getTag().getBoolean(SYSTEM_TAG))) return;
        inv.offhand.set(0, ItemStack.EMPTY);
        if (!inv.add(off)) p.drop(off, false);
        p.inventoryMenu.broadcastChanges();
    }
}
