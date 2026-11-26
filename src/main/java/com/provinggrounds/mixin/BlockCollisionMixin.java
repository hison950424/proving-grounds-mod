package com.provinggrounds.mixin;

import com.provinggrounds.core.StairRampShapes;
import com.provinggrounds.special.SpecialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: BlockCollisionMixin.java
 * 目的: 雙端方塊碰撞箱調整：
 *   1. 無實體化（計畫書 9）：5 秒內可以直接穿過厚度 1 格的任何水平牆壁方塊（不穿地板、不穿基岩／屏障）。
 *   2. 正放樓梯（Half.BOTTOM）改為 45° 平滑斜坡碰撞箱（StairRampShapes），走樓梯時不抖動。
 *   3. 柵欄、石牆與關閉中的柵欄門碰撞高度改為與一般方塊相同的 1 格（沿用外觀 VoxelShape），可直接跳上／跳過。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（getCollisionShape = m_60742_），不需要 refmap。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockCollisionMixin {

    @Inject(method = { "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
                       "m_60742_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$customCollision(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (ctx instanceof EntityCollisionContext ecc) {
            Entity e = ecc.getEntity();
            if (e != null && SpecialHooks.isEthereal(e)) {
                BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
                if (!self.is(Blocks.BEDROCK) && !self.is(Blocks.BARRIER)) {
                    int feetY = (int) Math.floor(e.getY());
                    if (pos.getY() >= feetY && pos.getY() <= feetY + 2) { // 不穿腳下地板，只穿身體高度的牆
                        BlockPos pPos = e.blockPosition();
                        int dx = pos.getX() - pPos.getX();
                        int dz = pos.getZ() - pPos.getZ();
                        if (dx == 0 && dz == 0) {
                            boolean ew = !level.getBlockState(pos.west()).blocksMotion() && !level.getBlockState(pos.east()).blocksMotion();
                            boolean ns = !level.getBlockState(pos.north()).blocksMotion() && !level.getBlockState(pos.south()).blocksMotion();
                            if (ew || ns) cir.setReturnValue(Shapes.empty());
                            return;
                        }
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                            BlockPos beyond = pos.offset(Integer.signum(dx), 0, Integer.signum(dz));
                            if (!level.getBlockState(beyond).blocksMotion()) {
                                cir.setReturnValue(Shapes.empty());
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (!((Object) this instanceof BlockState state)) return;

        // 正放樓梯改為 45° 斜坡碰撞箱
        if (state.getBlock() instanceof StairBlock && state.hasProperty(StairBlock.HALF) && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            cir.setReturnValue(StairRampShapes.forState(state));
            return;
        }

        // 柵欄、石牆與關閉的柵欄門碰撞高度改為與 1 格方塊相同（沿用最高 1 格的外觀碰撞範圍）
        if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)
                || (state.is(BlockTags.FENCE_GATES) && state.hasProperty(FenceGateBlock.OPEN) && !state.getValue(FenceGateBlock.OPEN))) {
            VoxelShape visual = state.getShape(level, pos, ctx);
            if (!visual.isEmpty()) {
                cir.setReturnValue(visual);
            }
        }
    }
}
