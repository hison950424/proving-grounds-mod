package com.provinggrounds.core;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 檔案: StairRampShapes.java
 * 目的: 預先建立正放樓梯（Half.BOTTOM）的 45° 平滑斜坡碰撞形狀（16 階微階梯，雙端與所有實體共用）：
 *   - 涵蓋 STRAIGHT、INNER_LEFT、INNER_RIGHT、OUTER_LEFT、OUTER_RIGHT 與四個水平方向（共 20 種狀態），
 *     在類別載入時一次計算快取，執行期為 O(1) 陣列查表。
 *   - 讓玩家與實體上下樓梯如同走過平順 45° 斜坡（每步僅 0.0625 格），不再因 0.5 格直角台階而劇烈上下跳動。
 */
public final class StairRampShapes {

    private static final int SLICES = 16;
    private static final VoxelShape[] STRAIGHTS = new VoxelShape[4];
    private static final VoxelShape[] SHAPES = new VoxelShape[StairsShape.values().length * 4];

    static {
        for (int dirIdx = 0; dirIdx < 4; dirIdx++) {
            STRAIGHTS[dirIdx] = buildStraight(Direction.from2DDataValue(dirIdx));
        }
        for (StairsShape shape : StairsShape.values()) {
            for (int dirIdx = 0; dirIdx < 4; dirIdx++) {
                Direction facing = Direction.from2DDataValue(dirIdx);
                VoxelShape base = STRAIGHTS[dirIdx];
                VoxelShape combined = switch (shape) {
                    case STRAIGHT -> base;
                    case INNER_LEFT -> Shapes.or(base, STRAIGHTS[facing.getCounterClockWise().get2DDataValue()]);
                    case INNER_RIGHT -> Shapes.or(base, STRAIGHTS[facing.getClockWise().get2DDataValue()]);
                    case OUTER_LEFT -> Shapes.join(base, STRAIGHTS[facing.getCounterClockWise().get2DDataValue()], BooleanOp.AND);
                    case OUTER_RIGHT -> Shapes.join(base, STRAIGHTS[facing.getClockWise().get2DDataValue()], BooleanOp.AND);
                };
                SHAPES[shape.ordinal() * 4 + dirIdx] = combined.optimize();
            }
        }
    }

    private StairRampShapes() {}

    /** O(1) 取得對應正放樓梯狀態的 45° 斜坡 VoxelShape。 */
    public static VoxelShape forState(BlockState state) {
        int idx = state.getValue(StairBlock.SHAPE).ordinal() * 4 + state.getValue(StairBlock.FACING).get2DDataValue();
        return SHAPES[idx];
    }

    private static VoxelShape buildStraight(Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < SLICES; i++) {
            double h = (i + 1) * (16.0 / SLICES);
            double a = i * (16.0 / SLICES);
            double b = (i + 1) * (16.0 / SLICES);
            double revA = (SLICES - 1 - i) * (16.0 / SLICES);
            double revB = (SLICES - i) * (16.0 / SLICES);
            VoxelShape slice = switch (facing) {
                case EAST  -> Block.box(a, 0.0, 0.0, b, h, 16.0);
                case WEST  -> Block.box(revA, 0.0, 0.0, revB, h, 16.0);
                case SOUTH -> Block.box(0.0, 0.0, a, 16.0, h, b);
                case NORTH -> Block.box(0.0, 0.0, revA, 16.0, h, revB);
                default -> Shapes.empty();
            };
            shape = Shapes.or(shape, slice);
        }
        return shape;
    }
}
