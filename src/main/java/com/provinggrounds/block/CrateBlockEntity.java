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

    // ── 開啟狀態（蓋子貼圖）：目前有幾個玩家開著這個箱子 ──────────────────────
    private int openers;

    @Override
    public void startOpen(net.minecraft.world.entity.player.Player player) {
        if (player.isSpectator()) return;
        if (openers++ == 0) setOpen(true);
    }

    @Override
    public void stopOpen(net.minecraft.world.entity.player.Player player) {
        if (player.isSpectator()) return;
        if (--openers <= 0) {
            openers = 0;
            setOpen(false);
            if (level != null) level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BARREL_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    private void setOpen(boolean open) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(CrateBlock.OPEN) && state.getValue(CrateBlock.OPEN) != open) {
            level.setBlock(worldPosition, state.setValue(CrateBlock.OPEN, open), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** 清空內容且不觸發 loot table 生成（每場開始/結束清理用）。 */
    public void wipe() {
        setLootTable(null, 0L);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        setChanged();
    }

    /**
     * 依 loot table 生成內容後：
     *   1. 合併相同物品（原版生成會把一組拆散到很多格，例如 30 發子彈變成十幾格 1 發）；
     *   2. 箱子裡每把槍再配一份對應子彈（半組～一組）。
     */
    @Override
    public void unpackLootTable(@javax.annotation.Nullable net.minecraft.world.entity.player.Player player) {
        boolean generating = lootTable != null && level != null && !level.isClientSide;
        super.unpackLootTable(player);
        if (generating) {
            mergeStacks();
            com.provinggrounds.compat.AmmoCategories.addAmmoForGuns(this, level.getRandom());
            mergeStacks();
            setChanged();
        }
    }

    /** 把相同物品（含 NBT）合併成盡量少的格子。 */
    private void mergeStacks() {
        for (int i = 0; i < items.size(); i++) {
            ItemStack a = items.get(i);
            if (a.isEmpty() || !a.isStackable()) continue;
            for (int j = i + 1; j < items.size() && a.getCount() < a.getMaxStackSize(); j++) {
                ItemStack b = items.get(j);
                if (b.isEmpty() || !ItemStack.isSameItemSameTags(a, b)) continue;
                int move = Math.min(b.getCount(), a.getMaxStackSize() - a.getCount());
                a.grow(move);
                b.shrink(move);
                if (b.isEmpty()) items.set(j, ItemStack.EMPTY);
            }
        }
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
