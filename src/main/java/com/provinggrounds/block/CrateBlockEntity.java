package com.provinggrounds.block;

import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 檔案: CrateBlockEntity.java
 * 目的: BR 戰利品箱的方塊實體。27 格容器，帶 loot table 欄位；
 *       玩家第一次打開時才依 loot table 生成內容（原版行為），關閉後保留。
 */
public class CrateBlockEntity extends RandomizableContainerBlockEntity {

    public static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(PGBlocks.CRATE_BE.get(), pos, state);
    }

    /** 清空內容且不觸發 loot table 生成（每場開始/結束清理用）。 */
    public void wipe() {
        setLootTable(null, 0L);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> list) { items = list; }

    @Override
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return ChestMenu.threeRows(id, inv, this);
    }

    @Override
    public int getContainerSize() { return SIZE; }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }
}
