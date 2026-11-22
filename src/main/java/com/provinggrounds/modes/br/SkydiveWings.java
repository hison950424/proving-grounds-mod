package com.provinggrounds.modes.br;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 檔案: SkydiveWings.java
 * 目的: 跳傘翼裝（計畫書 B1）。跳出飛機時穿在胸甲欄，落地、淘汰、還原時移除。
 *   用原版鞘翅物品做外觀（原版只替「原版鞘翅」畫翅膀），加上：
 *     - 標籤 pg_skydive：辨識用，任何地方發現都會被清掉，不會殘留
 *     - 綁定詛咒：生存／冒險模式無法從裝備欄取下
 *     - 耐久設成快壞（剩 1）：原版鞘翅滑翔的條件是耐久 > 1，所以按空白鍵不會觸發原版滑翔，
 *       速度完全由我們的公式（ClientSkydive）決定
 *     - 不可破壞：不會因為耐久真的壞掉
 */
public final class SkydiveWings {

    public static final String TAG = "pg_skydive";

    private SkydiveWings() {}

    public static ItemStack create() {
        ItemStack s = new ItemStack(Items.ELYTRA);
        s.enchant(Enchantments.BINDING_CURSE, 1);
        s.setDamageValue(s.getMaxDamage() - 1);
        CompoundTag tag = s.getOrCreateTag();
        tag.putBoolean(TAG, true);
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 127);
        s.setHoverName(Component.literal("跳傘翼裝").withStyle(ChatFormatting.AQUA));
        return s;
    }

    public static boolean isWings(ItemStack s) {
        return !s.isEmpty() && s.getTag() != null && s.getTag().getBoolean(TAG);
    }

    /** 穿上翼裝。胸甲欄原本有東西（例如管理員手動給的）就先放回背包，放不下掉在腳邊。 */
    public static void equip(ServerPlayer p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty() && !isWings(chest)) {
            ItemStack moved = chest.copy();
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            if (!p.getInventory().add(moved) && !moved.isEmpty()) p.drop(moved, false);
        }
        p.setItemSlot(EquipmentSlot.CHEST, create());
    }

    /** 移除身上（含背包、游標）所有翼裝。 */
    public static void removeAll(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isWings(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (isWings(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }
}
