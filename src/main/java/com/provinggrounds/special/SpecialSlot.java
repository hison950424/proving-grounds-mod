package com.provinggrounds.special;

import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SpecialSlotSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: SpecialSlot.java
 * 目的: 特殊物品欄（計畫書 4 的 B1）：物品欄畫面裡背包欄的正上方，只有 1 格，只接受 provinggrounds:special 標籤的物品。
 *   - 內容存在玩家資料（persistentData 的 pg_special），重新登入、重生都保留（做法同 Backpack）。
 *   - 其他所有格子（快捷欄、背包、副手、箱子）在 BR 中都不收特殊物品（HotbarRules 的 SPECIAL 分類）。
 *   - 內容一變就送 SpecialSlotSyncPacket，開著箱子時客戶端的格子也會立刻更新。
 */
public final class SpecialSlot {

    public static final String DATA_TAG = "pg_special";
    /** 物品欄畫面裡的位置：背包欄（77, 44）的正上方。 */
    public static final int X = 77, Y = 26;

    private static final Map<UUID, SimpleContainer> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, SimpleContainer> CLIENT = new ConcurrentHashMap<>();

    private SpecialSlot() {}

    public static boolean isSpecial(ItemStack s) {
        return !s.isEmpty() && s.is(SpecialRegistry.SPECIAL);
    }

    public static SimpleContainer container(Player p) {
        boolean client = p.level().isClientSide;
        return (client ? CLIENT : SERVER).computeIfAbsent(p.getUUID(), id -> create(id, client));
    }

    public static ItemStack get(Player p) { return container(p).getItem(0); }

    public static void set(ServerPlayer p, ItemStack s) { container(p).setItem(0, s); }

    private static SimpleContainer create(UUID id, boolean client) {
        SimpleContainer c = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() { return 1; }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) { return isSpecial(stack); }
        };
        if (!client) c.addListener(changed -> save(id, changed.getItem(0)));
        return c;
    }

    private static void save(UUID id, ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p == null) return;
        if (stack.isEmpty()) p.getPersistentData().remove(DATA_TAG);
        else p.getPersistentData().put(DATA_TAG, stack.save(new CompoundTag()));
        PGNetwork.sendTo(p, new SpecialSlotSyncPacket(stack.copy()));
    }

    public static void load(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(DATA_TAG);
        container(p).setItem(0, tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag));
    }

    public static void unload(Player p) {
        (p.level().isClientSide ? CLIENT : SERVER).remove(p.getUUID());
    }

    public static void clearClient() { CLIENT.clear(); }

    public static void copyData(Player from, Player to) {
        if (from.getPersistentData().contains(DATA_TAG)) to.getPersistentData().put(DATA_TAG, from.getPersistentData().getCompound(DATA_TAG).copy());
    }

    public static void clear(ServerPlayer p) { container(p).setItem(0, ItemStack.EMPTY); }

    /** 放進特殊物品欄（從 stack 拿一個）。欄位已經有東西時回傳 false。 */
    public static boolean tryInsert(ServerPlayer p, ItemStack stack) {
        if (!isSpecial(stack) || !get(p).isEmpty()) return false;
        container(p).setItem(0, stack.split(1));
        return true;
    }

    /** 物品欄畫面裡的那一格。 */
    public static class MenuSlot extends Slot {
        public MenuSlot(Container c) { super(c, 0, X, Y); }

        @Override
        public boolean mayPlace(ItemStack stack) { return isSpecial(stack); }

        @Override
        public int getMaxStackSize() { return 1; }
    }
}
