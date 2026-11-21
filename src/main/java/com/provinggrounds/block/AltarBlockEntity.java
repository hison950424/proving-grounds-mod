package com.provinggrounds.block;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashSet;
import java.util.Set;

/**
 * 檔案: AltarBlockEntity.java
 * 目的: 祭壇下半部的方塊實體，自己管理冷卻。
 *       冷卻記錄「屬於哪一局」（BR 的 game id），新的一局開始時 id 不同 → 自動視為就緒，
 *       所以不用找出地圖上所有祭壇去重設，未載入的祭壇也一樣。
 *       已載入的祭壇座標登記在 LOADED（只在伺服器端），給「蹲下啟動」找附近祭壇用。
 */
public class AltarBlockEntity extends BlockEntity {

    /** 伺服器端已載入的祭壇（下半部座標）。只在伺服器執行緒存取。 */
    public static final Set<BlockPos> LOADED = new HashSet<>();

    private long cooldownGame = 0;
    private long readyAt = 0;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(PGBlocks.ALTAR_BE.get(), pos, state);
    }

    // ── 冷卻 ────────────────────────────────────────────────────────────────

    public boolean isReady() {
        if (level == null) return false;
        return cooldownGame != BattleRoyaleMode.get().currentGameId() || level.getGameTime() >= readyAt;
    }

    /** 剩餘冷卻秒數（就緒時為 0）。 */
    public int cooldownSecondsLeft() {
        if (level == null || isReady()) return 0;
        return (int) ((readyAt - level.getGameTime() + 19) / 20);
    }

    public void startCooldown(int ticks) {
        if (level == null) return;
        cooldownGame = BattleRoyaleMode.get().currentGameId();
        readyAt = level.getGameTime() + ticks;
        setChanged();
        syncState(false);
    }

    /** 每秒把方塊狀態 ready 對齊實際冷卻（切換材質、亮度與粒子）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        boolean ready = be.isReady();
        if (state.getValue(AltarBlock.READY) != ready) be.syncState(ready);
    }

    private void syncState(boolean ready) {
        if (level == null) return;
        BlockPos pos = getBlockPos();
        BlockState lower = level.getBlockState(pos);
        BlockState upper = level.getBlockState(pos.above());
        if (lower.getBlock() instanceof AltarBlock) {
            level.setBlock(pos, lower.setValue(AltarBlock.READY, ready), Block.UPDATE_CLIENTS);
        }
        if (upper.getBlock() instanceof AltarBlock && upper.getValue(AltarBlock.HALF) == DoubleBlockHalf.UPPER) {
            level.setBlock(pos.above(), upper.setValue(AltarBlock.READY, ready), Block.UPDATE_CLIENTS);
        }
    }

    // ── 登記 / 存檔 ─────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        // 只登記主世界（BR 只在主世界進行），避免其他維度同座標的祭壇被誤認
        if (level != null && !level.isClientSide && level.dimension() == Level.OVERWORLD) {
            LOADED.add(getBlockPos().immutable());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide && level.dimension() == Level.OVERWORLD) LOADED.remove(getBlockPos());
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide && level.dimension() == Level.OVERWORLD) LOADED.remove(getBlockPos());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cooldownGame = tag.getLong("CooldownGame");
        readyAt = tag.getLong("ReadyAt");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("CooldownGame", cooldownGame);
        tag.putLong("ReadyAt", readyAt);
    }
}
