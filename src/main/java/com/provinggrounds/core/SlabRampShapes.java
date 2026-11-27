package com.provinggrounds.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 檔案: SlabRampShapes.java
 * 目的: 預先建立半磚（SlabType.BOTTOM / TOP）與相鄰實心方塊高差過渡用的平滑斜坡碰撞箱：
 *   - 以 4 位元水平鄰居遮罩（N/E/S/W，共 16 種組合）於類別載入時快取，執行期為 O(1) 陣列查表。
 *   - 下半磚往低半格可行走地面（或相鄰完整方塊）會形成 0.0~0.5 格的平滑斜坡；
 *     上半磚與相鄰完整方塊緊鄰下半磚時會於 0.5~1.0 格形成對應斜坡，讓上下半磚如同走樓梯般平順。
 *   - 只於查詢時讀取 getBlockState / blocksMotion / getShape / Tag，絕不呼叫 getCollisionShape 以免遞迴。
 */
public final class SlabRampShapes {

    static final int BIT_N = 1;
    static final int BIT_E = 2;
    static final int BIT_S = 4;
    static final int BIT_W = 8;

    private static final VoxelShape[] BOTTOM_SHAPES = new VoxelShape[16];
    private static final VoxelShape[] TOP_SHAPES = new VoxelShape[16];
    private static final VoxelShape[] FULL_TOP_SHAPES = new VoxelShape[16];

    static {
        VoxelShape lowerHalf = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        for (int mask = 1; mask < 16; mask++) {
            BOTTOM_SHAPES[mask] = buildRamp(mask, 0.0);
            TOP_SHAPES[mask] = buildRamp(mask, 0.5);
            FULL_TOP_SHAPES[mask] = Shapes.or(lowerHalf, TOP_SHAPES[mask]).optimize();
        }
    }

    private SlabRampShapes() {}

    static VoxelShape bottomShape(int mask) {
        return BOTTOM_SHAPES[mask];
    }

    static VoxelShape topShape(int mask) {
        return TOP_SHAPES[mask];
    }

    static VoxelShape fullTopShape(int mask) {
        return FULL_TOP_SHAPES[mask];
    }

    /** 取得方塊在目前周圍環境下的半磚平滑斜坡碰撞箱；不需斜面過渡時回傳 null。 */
    public static VoxelShape getRampShape(BlockGetter level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null) return null;
        if (level.getBlockState(pos.above()).blocksMotion()) return null;

        Block block = state.getBlock();
        if (block instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)) {
            SlabType type = state.getValue(SlabBlock.TYPE);
            if (type == SlabType.BOTTOM) {
                int mask = bottomRampMask(level, pos);
                return mask == 0 ? null : BOTTOM_SHAPES[mask];
            }
            if (type == SlabType.TOP) {
                int mask = adjacentBottomSlabMask(level, pos);
                return mask == 0 ? null : TOP_SHAPES[mask];
            }
            // SlabType.DOUBLE 視為完整實心方塊處理
        }

        if (!isEligibleFullBlock(level, pos, state)) return null;
        int mask = adjacentBottomSlabMask(level, pos);
        return mask == 0 ? null : FULL_TOP_SHAPES[mask];
    }

    /** 檢查指定方塊目前是否具備半磚斜坡過渡形狀。 */
    public static boolean isRampBlock(BlockGetter level, BlockPos pos, BlockState state) {
        return getRampShape(level, pos, state) != null;
    }

    private static int bottomRampMask(BlockGetter level, BlockPos pos) {
        int mask = 0;
        if (isLowerWalkableFloor(level, pos, Direction.NORTH)) mask |= BIT_N;
        if (isLowerWalkableFloor(level, pos, Direction.EAST))  mask |= BIT_E;
        if (isLowerWalkableFloor(level, pos, Direction.SOUTH)) mask |= BIT_S;
        if (isLowerWalkableFloor(level, pos, Direction.WEST))  mask |= BIT_W;
        return mask;
    }

    private static int adjacentBottomSlabMask(BlockGetter level, BlockPos pos) {
        int mask = 0;
        if (isBottomSlab(level.getBlockState(pos.north()))) mask |= BIT_N;
        if (isBottomSlab(level.getBlockState(pos.east())))  mask |= BIT_E;
        if (isBottomSlab(level.getBlockState(pos.south()))) mask |= BIT_S;
        if (isBottomSlab(level.getBlockState(pos.west())))  mask |= BIT_W;
        return mask;
    }

    private static boolean isLowerWalkableFloor(BlockGetter level, BlockPos pos, Direction dir) {
        BlockPos sidePos = pos.relative(dir);
        BlockState side = level.getBlockState(sidePos);
        if (side.blocksMotion()) return false;

        BlockState below = level.getBlockState(sidePos.below());
        if (!below.blocksMotion()) return false;
        if (below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS) || below.is(BlockTags.FENCE_GATES)) {
            return false;
        }
        Block belowBlock = below.getBlock();
        if (belowBlock instanceof SlabBlock && below.hasProperty(SlabBlock.TYPE)
                && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return false; // 落差為完整 1 格而非 0.5 格
        }
        if (belowBlock instanceof StairBlock && below.hasProperty(StairBlock.HALF)
                && below.getValue(StairBlock.HALF) == Half.BOTTOM) {
            return below.getValue(StairBlock.FACING) == dir.getOpposite();
        }
        return true;
    }

    private static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean isEligibleFullBlock(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.blocksMotion()) return false;
        Block b = state.getBlock();
        if (b instanceof StairBlock
                || b instanceof FenceBlock
                || b instanceof WallBlock
                || b instanceof FenceGateBlock
                || b instanceof DoorBlock
                || b instanceof TrapDoorBlock
                || b instanceof SoulSandBlock
                || b instanceof MudBlock
                || b instanceof HoneyBlock
                || b instanceof PowderSnowBlock
                || b instanceof ScaffoldingBlock) {
            return false;
        }
        return Block.isShapeFullBlock(state.getShape(level, pos));
    }

    private static VoxelShape buildRamp(int mask, double yBase) {
        boolean n = (mask & BIT_N) != 0;
        boolean e = (mask & BIT_E) != 0;
        boolean s = (mask & BIT_S) != 0;
        boolean w = (mask & BIT_W) != 0;
        boolean hasX = w || e;
        boolean hasZ = n || s;

        if (hasX && !hasZ) {
            return buildAxisShape(w, e, 16, yBase, true).optimize();
        }
        if (hasZ && !hasX) {
            return buildAxisShape(n, s, 16, yBase, false).optimize();
        }
        VoxelShape xShape = buildAxisShape(w, e, 8, yBase, true);
        VoxelShape zShape = buildAxisShape(n, s, 8, yBase, false);
        return Shapes.join(xShape, zShape, BooleanOp.AND).optimize();
    }

    private static VoxelShape buildAxisShape(boolean lowNeg, boolean lowPos, int slices, double yBase, boolean isX) {
        if (!lowNeg && !lowPos) {
            return Shapes.box(0.0, yBase, 0.0, 1.0, yBase + 0.5, 1.0);
        }
        VoxelShape shape = Shapes.empty();
        int half = slices / 2;
        for (int i = 0; i < slices; i++) {
            double u0 = i / (double) slices;
            double u1 = (i + 1) / (double) slices;
            double dh;
            if (lowNeg && lowPos) {
                dh = (i < half ? (i + 1) : (slices - i)) * (0.5 / half);
            } else if (lowNeg) {
                dh = (i + 1) * (0.5 / slices);
            } else {
                dh = (slices - i) * (0.5 / slices);
            }
            VoxelShape slice = isX
                    ? Shapes.box(u0, yBase, 0.0, u1, yBase + dh, 1.0)
                    : Shapes.box(0.0, yBase, u0, 1.0, yBase + dh, u1);
            shape = Shapes.or(shape, slice);
        }
        return shape;
    }
}
