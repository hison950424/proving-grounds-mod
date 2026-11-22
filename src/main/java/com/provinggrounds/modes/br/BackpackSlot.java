package com.provinggrounds.modes.br;

import com.provinggrounds.item.BackpackItem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: BackpackSlot.java
 * 目的: 物品欄畫面裡的「背包」欄（計畫書 B3），位置在副手格正上方。只接受 BackpackItem，一次一個。
 */
public class BackpackSlot extends Slot {

    public static final int X = 77, Y = 44;

    public BackpackSlot(Container container) {
        super(container, 0, X, Y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
