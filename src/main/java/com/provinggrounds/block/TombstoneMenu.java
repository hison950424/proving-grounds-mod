package com.provinggrounds.block;

import com.provinggrounds.item.BackpackItem;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.SpecialSlot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: TombstoneMenu.java
 * 目的: 墓碑的畫面（跟物品欄一樣的分區）。上半是墓碑（死者身上的東西，位置跟他死前一樣），下半是自己的物品欄。
 *
 *   墓碑格子編號（TombstoneBlockEntity）：
 *     0～3 防具（頭、胸、腿、腳）｜4 副手｜5 背包｜6 特殊物品｜7～33 背包 27 格｜34～42 快捷欄 9 格
 *   畫面位置（寬 176、高 208）：
 *     第一列 y=18：防具 x=8～62、副手 x=88、背包 x=134、特殊物品 x=152
 *     墓碑的背包 y=40～76、墓碑的快捷欄 y=98；自己的物品欄 y=126～162、自己的快捷欄 y=184
 *   防具格只收對應部位的防具、背包格只收背包、特殊物品格只收特殊物品，其他格子什麼都能放。
 *   BR 中的 Shift 點擊由 HotbarRules 照快捷欄規則處理；這裡的 quickMoveStack 只在規則沒啟用時用到。
 */
public class TombstoneMenu extends AbstractContainerMenu {

    public static final int SIZE = 43;
    public static final int ARMOR = 0, OFFHAND = 4, BACKPACK = 5, SPECIAL = 6, MAIN = 7, HOTBAR = 34;
    public static final int ROW_Y = 18, MAIN_Y = 40, HOTBAR_Y = 98, INV_Y = 126, INV_HOTBAR_Y = 184;
    public static final int OFFHAND_X = 88, BACKPACK_X = 134, SPECIAL_X = 152;
    private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private final Container tomb;

    /** 客戶端（由 MenuType 建立）。 */
    public TombstoneMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(TombstoneBlockEntity.SIZE));
    }

    public TombstoneMenu(int id, Inventory inv, Container tomb) {
        super(PGBlocks.TOMBSTONE_MENU.get(), id);
        this.tomb = tomb;
        tomb.startOpen(inv.player);
        for (int i = 0; i < 4; i++) {
            EquipmentSlot es = ARMOR_SLOTS[i];
            addSlot(new Slot(tomb, ARMOR + i, 8 + i * 18, ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack s) { return Mob.getEquipmentSlotForItem(s) == es; }

                @Override
                public int getMaxStackSize() { return 1; }
            });
        }
        addSlot(new Slot(tomb, OFFHAND, OFFHAND_X, ROW_Y));
        addSlot(new Slot(tomb, BACKPACK, BACKPACK_X, ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return s.getItem() instanceof BackpackItem; }
        });
        addSlot(new Slot(tomb, SPECIAL, SPECIAL_X, ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return SpecialSlot.isSpecial(s); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(tomb, MAIN + r * 9 + c, 8 + c * 18, MAIN_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(tomb, HOTBAR + c, 8 + c * 18, HOTBAR_Y));
        // 自己的物品欄
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_HOTBAR_Y));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY; // 墓碑 → 自己
        } else if (!moveItemStackTo(stack, MAIN, SIZE, false)) {
            return ItemStack.EMPTY; // 自己 → 墓碑的背包、快捷欄
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return tomb.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        tomb.stopOpen(player);
    }
}
