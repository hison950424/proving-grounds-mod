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
 * 目的: 墓碑的畫面。計畫書 7（使用者回饋）：左邊是自己的背包畫面（跟按 E 一樣：防具、副手、背包欄、特殊物品欄、背包、快捷欄），
 *   右邊是墓碑主人的背包畫面（同樣的排法，東西在他死前的位置）。
 *
 *   墓碑格子編號（TombstoneBlockEntity）：
 *     0～3 防具（頭、胸、腿、腳）｜4 副手｜5 背包｜6 特殊物品｜7～33 背包 27 格｜34～42 快捷欄 9 格
 *   畫面（寬 354、高 166）：左邊 0～176 是自己（格子位置跟原版物品欄一樣），右邊從 TX=178 開始是墓碑（同樣的相對位置）。
 *   自己那一側：先是背包 27＋快捷欄 9，最後是 ChestExtraSlots 的防具、副手、背包欄、特殊物品欄（副手照舊不能放）。
 *   防具格只收對應部位的防具、背包格只收背包、特殊物品格只收特殊物品，其他格子什麼都能放。
 *   BR 中的 Shift 點擊由 HotbarRules 照快捷欄規則處理；這裡的 quickMoveStack 只在規則沒啟用時用到。
 */
public class TombstoneMenu extends AbstractContainerMenu {

    public static final int SIZE = 43;
    public static final int ARMOR = 0, OFFHAND = 4, BACKPACK = 5, SPECIAL = 6, MAIN = 7, HOTBAR = 34;
    /** 右邊（墓碑）面板的起點；兩邊的格子相對位置都跟原版物品欄一樣。 */
    public static final int TX = 178, W = 354, H = 166;
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
            addSlot(new Slot(tomb, ARMOR + i, TX + 8, 8 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack s) { return Mob.getEquipmentSlotForItem(s) == es; }

                @Override
                public int getMaxStackSize() { return 1; }
            });
        }
        addSlot(new Slot(tomb, OFFHAND, TX + 77, 62));
        addSlot(new Slot(tomb, BACKPACK, TX + com.provinggrounds.modes.br.BackpackSlot.X, com.provinggrounds.modes.br.BackpackSlot.Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return s.getItem() instanceof BackpackItem; }
        });
        addSlot(new Slot(tomb, SPECIAL, TX + SpecialSlot.X, SpecialSlot.Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return SpecialSlot.isSpecial(s); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(tomb, MAIN + r * 9 + c, TX + 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(tomb, HOTBAR + c, TX + 8 + c * 18, 142));
        // 自己的物品欄（左邊，位置跟原版物品欄一樣）
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
        // 自己的防具、副手、背包欄、特殊物品欄（跟箱子畫面同一套，位置跟物品欄一樣）
        for (Slot s : ChestExtraSlots.create(inv)) addSlot(s);
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
