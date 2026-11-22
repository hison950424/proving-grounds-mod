package com.provinggrounds.block;

import com.provinggrounds.modes.br.StatsBoard;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 檔案: StatsBoardBlockEntity.java
 * 目的: 數據看板的方塊實體（計畫書 B5）。本身不存資料，只用來：
 *   伺服器端登記看板位置（決定排行榜要同步給哪些附近的玩家）；客戶端讓 StatsBoardRenderer 畫浮空文字。
 */
public class StatsBoardBlockEntity extends BlockEntity {

    public StatsBoardBlockEntity(BlockPos pos, BlockState state) {
        super(PGBlocks.STATS_BOARD_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) StatsBoard.register(level.dimension(), worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) StatsBoard.unregister(level.dimension(), worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) StatsBoard.unregister(level.dimension(), worldPosition);
    }

    /** 文字畫在方塊上方，視野判定範圍要往上加高，否則方塊不在畫面內時文字會消失。 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 5, 0).inflate(2, 0, 2);
    }
}
