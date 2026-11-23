package com.provinggrounds.special;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;

/**
 * 檔案: SpecialLoot.java
 * 目的: 箱子裡的特殊物品（計畫書 4 的 B3）。四種箱子（低、中、高、空投）機率相同：每箱 special.chance（預設 10%）出現 1 件；
 *   出現時依 data/br/loot_tables/special.json 的權重決定是哪一件（強力物品權重低；改權重不用改程式）。
 */
public final class SpecialLoot {

    public static final ResourceLocation TABLE = new ResourceLocation("br", "special");

    private SpecialLoot() {}

    /** 箱子生成內容後呼叫：依機率放 1 件特殊物品到空格。 */
    public static void maybeAdd(Container crate, Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        SpecialConfig s;
        try { s = SpecialConfig.get(); } catch (IllegalStateException e) { return; }
        boolean airdrop = crate instanceof net.minecraft.world.level.block.entity.BlockEntity be
            && be.getBlockState().is(com.provinggrounds.registry.PGBlocks.CRATE_AIRDROP.get());
        double chance = airdrop ? s.airdropChance.get() : s.chance.get(); // 空投：高風險高回報，預設一定有
        if (!s.enabled.get() || sl.getRandom().nextDouble() >= chance) return;
        ItemStack pick = roll(sl);
        if (pick.isEmpty()) return;
        for (int i = 0; i < crate.getContainerSize(); i++) {
            if (crate.getItem(i).isEmpty()) {
                crate.setItem(i, pick);
                return;
            }
        }
    }

    /** 從權重表抽一件。 */
    public static ItemStack roll(ServerLevel level) {
        LootTable table = level.getServer().getLootData().getLootTable(TABLE);
        List<ItemStack> out = table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
        return out.isEmpty() ? ItemStack.EMPTY : out.get(0);
    }
}
