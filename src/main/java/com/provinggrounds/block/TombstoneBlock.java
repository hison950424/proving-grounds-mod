package com.provinggrounds.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 檔案: TombstoneBlock.java
 * 目的: 墓碑。玩家在 BR 被淘汰時不噴裝，身上所有物品（含背包欄）收進死亡位置的墓碑。
 *   任何人右鍵都能打開查看並拿取（像箱子一樣，拿東西時一樣套用快捷欄規則）；拿空後墓碑自動消失。
 *   一局結束時（含強制停止、當機後的下一局）全部清除，見 modes/br/Tombstones。
 *   不會掉落物品，也不能被破壞（硬度 -1、爆炸抗性很高、BlockProtection 的爆炸也排除方塊實體）。
 *   受重力（使用者回饋）：下面沒有方塊時往下掉，掉到第一個能站的方塊上，裡面的物品跟著搬過去。
 */
public class TombstoneBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 0, 1, 15, 2, 15),   // 底座
        Block.box(3, 2, 6, 13, 14, 10)); // 碑身

    public TombstoneBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        level.scheduleTick(pos, this, 2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (from.equals(pos.below())) level.scheduleTick(pos, this, 2);
    }

    private static boolean canFallInto(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        BlockPos to = pos;
        while (to.getY() > level.getMinBuildHeight() && canFallInto(level, to.below())) to = to.below();
        if (to.equals(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag data = be != null ? be.saveWithoutMetadata() : null;
        level.removeBlockEntity(pos);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(to, state, Block.UPDATE_ALL);
        BlockEntity nbe = level.getBlockEntity(to);
        if (nbe != null && data != null) { nbe.load(data); nbe.setChanged(); }
        com.provinggrounds.modes.br.Tombstones.moved(level, pos, to);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TombstoneBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity t && !player.isSpectator()) player.openMenu(t);
        return InteractionResult.CONSUME;
    }
}
