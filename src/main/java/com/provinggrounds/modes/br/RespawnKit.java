package com.provinggrounds.modes.br;

import com.provinggrounds.compat.AmmoCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 檔案: RespawnKit.java
 * 目的: 祭壇復活後（落地時）發的基礎裝備（計畫書 B2）：
 *   皮革全套、手槍 P320 進第 3 格、兩個小醫療包（lrtactical AI-2）進第 4 格（沿用舊資料包的祭壇裝備），
 *   另外附一份手槍子彈（半組～一組，放第 6～9 格），否則手槍沒辦法用。
 *   物品經過 HotbarRules.insert，規則跟撿東西一樣；沒裝 TACZ／lrtactical 時對應的物品略過。
 */
public final class RespawnKit {

    private static final String PISTOL_ID = "tacz:p320";
    private static final String MEDKIT_ID = "lrtactical:ai2";

    private RespawnKit() {}

    public static void give(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        ItemStack pistol = item("tacz:modern_kinetic_gun", 1);
        if (!pistol.isEmpty()) {
            CompoundTag tag = pistol.getOrCreateTag();
            tag.putString("GunId", PISTOL_ID);
            tag.putString("GunFireMode", "SEMI");
            ItemStack ammo = AmmoCategories.ammoFor(pistol, p.getRandom());
            give(p, pistol);
            if (!ammo.isEmpty()) give(p, ammo);
        }
        ItemStack medkit = item("lrtactical:consumable", 2);
        if (!medkit.isEmpty()) {
            medkit.getOrCreateTag().putString("ConsumableId", MEDKIT_ID);
            give(p, medkit);
        }
    }

    private static ItemStack item(String id, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        HotbarRules.insert(p, stack);
        if (!stack.isEmpty()) HotbarRules.dropAtFeet(p, stack);
    }
}
