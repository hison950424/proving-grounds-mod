package com.provinggrounds.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 檔案: CrateBlock.java
 * 目的: BR 戰利品箱方塊（木＝低階、鐵＝中階、金＝高階、空投）。
 *       四種外觀共用同一個方塊實體類型，階級由放置時設定的 loot table 決定。
 *       open 狀態：有玩家打開時為 true（蓋子打開的貼圖），全部關閉後回到 false，同原版木桶。
 */
public class CrateBlock extends BaseEntityBlock {

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty OPEN =
        net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;

    public CrateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrateBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
            if (crate.isFake() && player instanceof net.minecraft.server.level.ServerPlayer sp && level instanceof net.minecraft.server.level.ServerLevel sl) {
                if (com.provinggrounds.special.ActionLock.blocksActions(sp)) return InteractionResult.FAIL;
                com.provinggrounds.special.SpecialUse.triggerFakeCrate(sl, pos, sp, crate.fakeOwner()); // 假箱：觸發陷阱並銷毀
                return InteractionResult.CONSUME;
            }
            player.openMenu(crate);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
            // 大逃殺物資箱被破壞時不掉落內容物，直接清空並更新鄰近紅石輸出
            crate.wipe();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
