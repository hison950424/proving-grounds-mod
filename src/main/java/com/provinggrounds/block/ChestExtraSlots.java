package com.provinggrounds.block;

import com.mojang.datafixers.util.Pair;
import com.provinggrounds.modes.br.Backpack;
import com.provinggrounds.modes.br.BackpackSlot;
import com.provinggrounds.special.SpecialSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;

/**
 * 檔案: ChestExtraSlots.java
 * 目的: 計畫書 7 A4：打開箱子（ChestMenu）時，背包那一側要跟單純開物品欄一樣，有防具 4 格、副手、背包欄、特殊物品欄。
 *   ChestMenuMixin 在原版格子（箱子 + 背包 27 + 快捷欄 9）之後依序加上：頭、胸、腿、腳、副手、背包欄、特殊物品欄，
 *   原本的格子編號都不變。格子在畫面上的位置由客戶端 ChestSideLayout 決定（這裡的座標只是初始值）。
 *   副手格照第四份計畫書的規則：玩家不能放（SlotMixin 擋 Inventory 第 40 格）。
 */
public final class ChestExtraSlots {

    private ChestExtraSlots() {}

    /** 加在箱子畫面最後的格子數（防具 4、副手 1、背包 1、特殊 1）。 */
    public static final int COUNT = 7;

    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final ResourceLocation[] EMPTY = {
        InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
        InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS };

    /** 依序建立要加的格子。 */
    public static List<Slot> create(Inventory inv) {
        Player owner = inv.player;
        Slot[] out = new Slot[COUNT];
        for (int i = 0; i < 4; i++) out[i] = new ArmorSlot(inv, owner, ARMOR[i], 39 - i, EMPTY[i], 8, 8 + i * 18);
        out[4] = new Slot(inv, 40, 77, 62) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        };
        out[5] = new BackpackSlot(Backpack.container(owner));
        out[6] = new SpecialSlot.MenuSlot(SpecialSlot.container(owner));
        return List.of(out);
    }

    /** 防具格（跟原版物品欄的防具格一樣：只能放對應部位、綁定詛咒拿不下來）。 */
    private static final class ArmorSlot extends Slot {
        private final Player owner;
        private final EquipmentSlot slot;
        private final ResourceLocation icon;

        ArmorSlot(Inventory inv, Player owner, EquipmentSlot slot, int index, ResourceLocation icon, int x, int y) {
            super(inv, index, x, y);
            this.owner = owner;
            this.slot = slot;
            this.icon = icon;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            ItemStack old = getItem();
            super.setByPlayer(stack);
            owner.onEquipItem(slot, old, stack);
        }

        @Override
        public int getMaxStackSize() { return 1; }

        @Override
        public boolean mayPlace(ItemStack stack) { return stack.canEquip(slot, owner); }

        @Override
        public boolean mayPickup(Player p) {
            ItemStack s = getItem();
            return (s.isEmpty() || p.isCreative() || !EnchantmentHelper.hasBindingCurse(s)) && super.mayPickup(p);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }
}
