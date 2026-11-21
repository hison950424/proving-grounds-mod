package com.provinggrounds.modes.br;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 檔案: RespawnKit.java
 * 目的: 祭壇復活後發的基礎裝備。
 *       目前：皮革全套。
 *       待確認（使用者說之後再做，子彈系統要大改）：手槍型號、彈藥量、兩個小醫療包。
 *       確認後在 give() 的 TODO 處加上即可。
 */
public final class RespawnKit {

    private RespawnKit() {}

    public static void give(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        // TODO（待確認）：手槍 + 彈藥 + 兩個小醫療包
    }
}
