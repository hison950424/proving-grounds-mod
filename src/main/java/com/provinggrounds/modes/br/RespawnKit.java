package com.provinggrounds.modes.br;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.AmmoCategories;
import com.provinggrounds.compat.GunClass;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 檔案: RespawnKit.java
 * 目的: 祭壇復活後（落地時）發的基礎裝備（計畫書 B2）：皮革全套，加上設定 [battle_royale.respawn_kit].items 列出的物品
 *   （預設：P320 手槍、兩個 AI-2 小醫療包，沿用舊資料包的祭壇裝備）。槍會自動附一份它用的子彈（半組～一組）。
 *   物品經過 HotbarRules.insert，規則跟撿東西一樣（手槍第 3 格、補品第 4 格）。
 *   物品 ID 不寫死在程式裡（計畫書 C）：日後換成自製物品只改設定。找不到的物品略過並記在 log。
 */
public final class RespawnKit {

    private RespawnKit() {}

    public static void give(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        for (String spec : PGConfig.SERVER.respawnKitItems.get()) {
            ItemStack stack = com.provinggrounds.core.WeaponMode.convert(parse(spec), p.getRandom()); // 槍械模式：換成這一場的槍種
            if (stack.isEmpty()) continue;
            ItemStack ammo = GunCompat.classify(stack) != GunClass.NONE ? AmmoCategories.ammoFor(stack, p.getRandom()) : ItemStack.EMPTY;
            give(p, stack);
            if (!ammo.isEmpty()) give(p, ammo);
        }
    }

    /** 解析「物品ID{NBT} 數量」（同 /give 的寫法）；格式錯誤或物品不存在時回傳空物品。 */
    public static ItemStack parse(String spec) {
        String s = spec.trim();
        int count = 1;
        int space = s.lastIndexOf(' ');
        if (space > 0 && s.substring(space + 1).matches("\\d+")) {
            count = Math.max(1, Integer.parseInt(s.substring(space + 1)));
            s = s.substring(0, space).trim();
        }
        try {
            ItemParser.ItemResult r = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(), new StringReader(s));
            ItemStack stack = new ItemStack(r.item(), count);
            if (r.nbt() != null) stack.setTag(r.nbt().copy());
            return stack;
        } catch (CommandSyntaxException e) {
            ProvingGroundsMod.LOGGER.warn("respawn_kit: cannot parse '{}' ({}), skipped.", spec, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        HotbarRules.insert(p, stack);
        if (!stack.isEmpty()) HotbarRules.dropAtFeet(p, stack);
    }
}
