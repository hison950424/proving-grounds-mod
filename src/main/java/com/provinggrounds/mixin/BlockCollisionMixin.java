package com.provinggrounds.mixin;

import com.provinggrounds.core.SlabRampShapes;
import com.provinggrounds.core.StairRampShapes;
import com.provinggrounds.special.SpecialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: BlockCollisionMixin.java
 * 目的: 雙端方塊碰撞箱調整：
 *   1. 無實體化（計畫書 9）：5 秒內可以直接穿過厚度 1 格的任何水平牆壁方塊（不穿地板、不穿基岩／屏障）。
 *   2. 正放樓梯（Half.BOTTOM）改為 45° 平滑斜坡碰撞箱（StairRampShapes），走樓梯時不抖動。
 *   3. 半磚（SlabBlock）與緊鄰下半磚的實心方塊對生物改為平滑斜坡過渡（SlabRampShapes），不影響子彈與掉落物判定。
 *   4. 柵欄、石牆與關閉中的柵欄門碰撞高度改為與一般方塊相同的 1 格（沿用外觀 VoxelShape），可直接跳上／跳過。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（getCollisionShape = m_60742_），不需要 refmap。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockCollisionMixin {

    /** 柵欄／石牆／關閉柵欄門的 1 格整齊高度碰撞箱快取（將外觀水平投影拉齊至 y = 0..1，避免矮牆 0.875 導致 1 格跳不上 2 格）。 */
    @Unique
    private static final Map<BlockState, VoxelShape> PROVINGGROUNDS_ONE_BLOCK_FENCE_WALL = new ConcurrentHashMap<>();

    @Unique
    private static VoxelShape provinggrounds$normalizeOneBlockHeight(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PROVINGGROUNDS_ONE_BLOCK_FENCE_WALL.computeIfAbsent(state, st -> {
            VoxelShape visual = st.getShape(level, pos, ctx);
            if (visual.isEmpty()) return visual;
            VoxelShape out = Shapes.empty();
            for (AABB box : visual.toAabbs()) {
                out = Shapes.or(out, Shapes.box(box.minX, 0.0, box.minZ, box.maxX, 1.0, box.maxZ));
            }
            return out.optimize();
        });
    }

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

        // 半磚與相鄰高差方塊只針對生物提供平滑斜坡過渡（保留子彈與空查詢的方正碰撞邊界）
        if (ctx instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof LivingEntity) {
            VoxelShape slabRamp = SlabRampShapes.getRampShape(level, pos, state);
            if (slabRamp != null) {
                cir.setReturnValue(slabRamp);
                return;
            }
        }

        // 柵欄、石牆與關閉的柵欄門碰撞高度統一為精確 1 格（單層或疊兩層皆為每格 1.0 高，可從 1 格牆順利跳上 2 格牆）
        if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)
                || (state.is(BlockTags.FENCE_GATES) && state.hasProperty(FenceGateBlock.OPEN) && !state.getValue(FenceGateBlock.OPEN))) {
            VoxelShape normalized = provinggrounds$normalizeOneBlockHeight(state, level, pos, ctx);
            if (!normalized.isEmpty()) {
                cir.setReturnValue(normalized);
            }
        }
    }

    /**
     * 緊鄰下半磚的實心方塊／雙層半磚已提供斜坡碰撞過渡（SlabRampShapes），必須如同原版樓梯回傳 isSuffocating = false；
     * 否則 LocalPlayer.moveTowardsClosestSpace() 會以整個方塊 X-Z 投影判定玩家腳部卡在實心方塊內，
     * 每 tick 施加 -0.1 反向推力把玩家推回半磚上而走不上去。
     */
    @Inject(method = { "isSuffocating(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
                       "m_60828_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$noSuffocateOnSlabRamp(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof BlockState state)) return;
        if (SlabRampShapes.isRampBlock(level, pos, state)) {
            cir.setReturnValue(false);
        }
    }
}
