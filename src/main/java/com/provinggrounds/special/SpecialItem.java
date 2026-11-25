package com.provinggrounds.special;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: SpecialItem.java
 * 目的: 特殊物品（計畫書 4 的 B 章）。只能放在特殊物品欄（物品欄上方那 1 格），按「使用特殊物品」鍵（預設 Q）使用，用完就消失。
 *   物品本身沒有右鍵功能，效果全部在 SpecialUse。
 */
public class SpecialItem extends Item {

    private final SpecialType type;

    public SpecialItem(SpecialType type, Properties props) {
        super(props);
        this.type = type;
    }

    public SpecialType type() { return type; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.provinggrounds." + type.id + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.provinggrounds.special_use").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** 剩餘使用次數的 NBT（計畫書 7 C11：臨時雪牆、地雷可以用 2 次）。 */
    public static final String USES_TAG = "pg_uses";

    public static int usesLeft(ItemStack stack) {
        SpecialType t = typeOf(stack);
        if (t == null || !t.twoUses()) return 1;
        return stack.getTag() != null && stack.getTag().contains(USES_TAG) ? Math.max(1, stack.getTag().getInt(USES_TAG)) : 2;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = super.getName(stack);
        if (!type.twoUses()) return base;
        return base.copy().append(Component.literal(" " + usesLeft(stack) + "／2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return type.twoUses() && usesLeft(stack) < 2; }

    @Override
    public int getBarWidth(ItemStack stack) { return Math.round(13f * usesLeft(stack) / 2f); }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55FF55; }

    @Nullable
    public static SpecialType typeOf(ItemStack stack) {
        return stack.getItem() instanceof SpecialItem s ? s.type : null;
    }
}
