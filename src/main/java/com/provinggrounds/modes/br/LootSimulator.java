package com.provinggrounds.modes.br;

import com.provinggrounds.compat.AmmoCategories;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 檔案: LootSimulator.java
 * 目的: /pg loot simulate <階級> <次數>（計畫書 A8）：模擬開 N 個箱子，在聊天欄列出
 *   平均每箱的槍、配件、醫療、投擲物、防具、背包、其他件數，以及各類彈藥出現的機率與平均數量。
 *   「主 pool 件數」= 彈藥以外的物品件數（每抽一次得一件），可用來確認抽取次數設定（2～5 次 → 平均 3.5）。
 *   另外照實際規則（每把槍附半組～一組對應子彈）算一次「槍附贈的子彈」。
 */
public final class LootSimulator {

    private LootSimulator() {}

    public static int run(CommandSourceStack src, String tier, int count) {
        ServerLevel level = src.getLevel();
        ResourceLocation id = new ResourceLocation("br", "crates/" + tier);
        LootTable table = level.getServer().getLootData().getLootTable(id);
        if (table == LootTable.EMPTY) {
            src.sendFailure(Component.literal("[PG] 找不到 loot table " + id + "（可用：low、mid、high、airdrop）"));
            return 0;
        }
        long start = System.currentTimeMillis();
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (String k : new String[] { "槍", "配件", "醫療", "投擲物", "近戰", "防具", "背包", "其他" }) kinds.put(k, 0);
        Map<String, int[]> ammo = new LinkedHashMap<>(); // 類別 → {出現次數, 總發數, 最少, 最多}
        int mainItems = 0, bonusAmmo = 0, bonusStacks = 0;

        for (int i = 0; i < count; i++) {
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, src.getPosition()).create(LootContextParamSets.CHEST);
            List<ItemStack> items = table.getRandomItems(params);
            SimpleContainer box = new SimpleContainer(27);
            for (ItemStack s : items) {
                String k = kindOf(s);
                if (k.equals("彈藥")) {
                    String cat = s.getTag() != null ? s.getTag().getString("AmmoId").replace("provinggrounds:", "") : "?";
                    int[] a = ammo.computeIfAbsent(cat, x -> new int[] { 0, 0, Integer.MAX_VALUE, 0 });
                    a[0]++; a[1] += s.getCount(); a[2] = Math.min(a[2], s.getCount()); a[3] = Math.max(a[3], s.getCount());
                } else {
                    kinds.merge(k, 1, Integer::sum);
                    mainItems++;
                    if (k.equals("槍")) box.addItem(s.copy());
                }
            }
            // 每把槍附贈的子彈（與箱子實際開啟時相同的規則）
            AmmoCategories.addAmmoForGuns(box, level.getRandom());
            for (int j = 0; j < box.getContainerSize(); j++) {
                ItemStack s = box.getItem(j);
                if (kindOf(s).equals("彈藥")) { bonusStacks++; bonusAmmo += s.getCount(); }
            }
        }

        say(src, String.format("模擬 %s 箱 %d 個（耗時 %d 毫秒）：主 pool 平均每箱 %.2f 件", tierName(tier), count,
            System.currentTimeMillis() - start, mainItems / (double) count), ChatFormatting.GOLD);
        StringBuilder sb = new StringBuilder("  平均每箱：");
        kinds.forEach((k, v) -> sb.append(String.format("%s %.2f  ", k, v / (double) count)));
        say(src, sb.toString().trim(), ChatFormatting.WHITE);
        if (ammo.isEmpty()) say(src, "  彈藥 pool：沒有抽到任何彈藥", ChatFormatting.GRAY);
        ammo.forEach((cat, a) -> say(src, String.format("  %s：平均每箱 %.2f 組、每組 %d～%d 發、平均 %.1f 發",
            ammoName(cat), a[0] / (double) count, a[2], a[3], a[1] / (double) a[0]), ChatFormatting.AQUA));
        say(src, String.format("  槍附贈的子彈：平均每箱 %.2f 組、%.1f 發", bonusStacks / (double) count, bonusAmmo / (double) count),
            ChatFormatting.GRAY);
        return count;
    }

    private static String kindOf(ItemStack s) {
        if (s.isEmpty()) return "其他";
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        if (id.equals("tacz:ammo")) return "彈藥";
        if (id.equals("tacz:modern_kinetic_gun")) return "槍";
        if (id.equals("tacz:attachment")) return "配件";
        if (id.equals("lrtactical:consumable")) return "醫療";
        if (id.equals("lrtactical:throwable")) return "投擲物";
        if (id.equals("lrtactical:melee")) return "近戰";
        if (s.getItem() instanceof ArmorItem) return "防具";
        if (id.endsWith("_shulker_box")) return "背包";
        return "其他";
    }

    private static String ammoName(String cat) {
        return switch (cat) {
            case "light" -> "輕型彈藥";
            case "medium" -> "中型彈藥";
            case "heavy" -> "重型彈藥";
            case "shotgun" -> "霰彈";
            case "heavy_weapon" -> "重火力彈藥";
            default -> cat;
        };
    }

    private static String tierName(String tier) {
        return switch (tier) {
            case "low" -> "木（低階）";
            case "mid" -> "鐵（中階）";
            case "high" -> "金（高階）";
            case "airdrop" -> "空投";
            default -> tier;
        };
    }

    private static void say(CommandSourceStack src, String msg, ChatFormatting color) {
        src.sendSuccess(() -> Component.literal("[PG] " + msg).withStyle(color), false);
    }
}
