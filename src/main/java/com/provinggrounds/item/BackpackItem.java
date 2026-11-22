package com.provinggrounds.item;

import com.provinggrounds.modes.br.Backpack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: BackpackItem.java
 * 目的: 一級／二級／三級背包（計畫書 B3）。裝在物品欄畫面的「背包」欄才有作用，依等級解鎖主背包格數
 *       （預設 5／10／15 格，設定 [battle_royale.backpack]）。撿到更高等級時自動換上，舊的掉在腳邊。
 */
public class BackpackItem extends Item {

    private final int level;

    public BackpackItem(int level, Properties props) {
        super(props.stacksTo(1));
        this.level = level;
    }

    public int level() { return level; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("provinggrounds.backpack.tooltip", Backpack.slotsFor(level)).withStyle(ChatFormatting.GRAY));
    }
}
