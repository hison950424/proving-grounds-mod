package com.provinggrounds.block;

import com.provinggrounds.modes.br.Tombstones;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 檔案: TombstoneBlockEntity.java
 * 目的: 墓碑的內容（54 格，夠放身上 41 格加背包欄）與墓主名稱。用原版箱子畫面（6 列）打開。
 *   關掉畫面時如果裡面已經空了，墓碑就自動移除。
 */
public class TombstoneBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SIZE = 54;

    private String owner = "";
    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            TombstoneBlockEntity.this.setChanged();
        }

        @Override
        public void stopOpen(Player player) {
            super.stopOpen(player);
            removeIfEmpty();
        }
    };

    public TombstoneBlockEntity(BlockPos pos, BlockState state) {
        super(PGBlocks.TOMBSTONE_BE.get(), pos, state);
    }

    public void setOwner(String name) {
        owner = name;
        setChanged();
    }

    /** 放進一個物品（墓碑建立時用）；放不下回傳 false。 */
    public boolean add(ItemStack stack) {
        for (int i = 0; i < SIZE; i++) {
            if (items.getItem(i).isEmpty()) {
                items.setItem(i, stack);
                return true;
            }
        }
        return false;
    }

    private void removeIfEmpty() {
        if (level == null || level.isClientSide || !items.isEmpty()) return;
        level.setBlockAndUpdate(worldPosition, Blocks.AIR.defaultBlockState());
        Tombstones.forget(level, worldPosition);
    }

    /** 清除（一局結束時）：內容直接消失。 */
    public void wipe() {
        items.clearContent();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(owner.isEmpty() ? "墓碑" : owner + " 的墓碑");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return ChestMenu.sixRows(id, inv, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("owner", owner);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.getString("owner");
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        for (int i = 0; i < SIZE; i++) items.setItem(i, list.get(i));
    }
}
