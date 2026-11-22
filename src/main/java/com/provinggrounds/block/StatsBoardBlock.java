package com.provinggrounds.block;

import com.provinggrounds.modes.br.StatsBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * 檔案: StatsBoardBlock.java
 * 目的: 數據看板（計畫書 B5）。上方浮空顯示大逃殺累計數據（客戶端 StatsBoardRenderer 畫）。
 *   右鍵：在「個人數據」與「全服排行」之間切換；蹲下＋右鍵：切換排行的排序依據。
 *   切換只發生在按的那位玩家的客戶端（不送封包、不影響別人、不存檔）。
 *   只有 OP 或創造模式能擺放與破壞（StatsBoard 的事件檢查）。
 */
public class StatsBoardBlock extends BaseEntityBlock {

    public StatsBoardBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsBoardBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) {
            boolean sneak = player.isShiftKeyDown();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.provinggrounds.ui.ClientStatsBoard.onClick(sneak));
        } else {
            StatsBoard.markNear(player); // 確保看板前的人有最新的排行
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
