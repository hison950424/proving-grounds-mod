package com.provinggrounds.core;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 檔案: SlabRampShapesTest.java
 * 目的: 驗證半磚平滑斜坡形狀表的遮罩組合、單側漸升高度與斜率幾何一致性。
 */
class SlabRampShapesTest {

    private static double sampleSurfaceY(VoxelShape shape, double x, double z) {
        double eps = 0.01;
        AABB probe = new AABB(x - eps, 0.0, z - eps, x + eps, 1.0, z + eps);
        // collide(Axis.Y, probe, -1.0) 從上方往下落不會有直接取單列碰撞盒直覺，改用沿 XZ 切分或最大交集高度：
        // 直接把 probe 高度設為 0..2，以 Shapes.join 取出該點位欄位的高程上界
        double low = 0.0;
        double high = 1.0;
        for (int iter = 0; iter < 14; iter++) {
            double mid = 0.5 * (low + high);
            AABB slice = new AABB(x - eps, mid, z - eps, x + eps, 1.5, z + eps);
            boolean hits = shape.toAabbs().stream().anyMatch(b -> b.intersects(slice));
            if (hits) low = mid;
            else high = mid;
        }
        return low;
    }

    @Test
    void allNonZeroMasksAreValidAndBounded() {
        assertNull(SlabRampShapes.bottomShape(0));
        assertNull(SlabRampShapes.topShape(0));
        assertNull(SlabRampShapes.fullTopShape(0));

        for (int mask = 1; mask < 16; mask++) {
            VoxelShape bot = SlabRampShapes.bottomShape(mask);
            VoxelShape top = SlabRampShapes.topShape(mask);
            VoxelShape full = SlabRampShapes.fullTopShape(mask);

            assertFalse(bot.isEmpty(), "bottomShape 空形狀 mask=" + mask);
            assertFalse(top.isEmpty(), "topShape 空形狀 mask=" + mask);
            assertFalse(full.isEmpty(), "fullTopShape 空形狀 mask=" + mask);

            assertEquals(0.0, bot.min(Direction.Axis.Y), 1e-6);
            assertEquals(0.5, bot.max(Direction.Axis.Y), 1e-6);

            assertEquals(0.5, top.min(Direction.Axis.Y), 1e-6);
            assertEquals(1.0, top.max(Direction.Axis.Y), 1e-6);

            assertEquals(0.0, full.min(Direction.Axis.Y), 1e-6);
            assertEquals(1.0, full.max(Direction.Axis.Y), 1e-6);
        }
    }

    @Test
    void singleSideWestRampRisesMonotonicallyWithTopLandingPlatform() {
        VoxelShape bot = SlabRampShapes.bottomShape(SlabRampShapes.BIT_W);
        VoxelShape top = SlabRampShapes.topShape(SlabRampShapes.BIT_W);
        VoxelShape full = SlabRampShapes.fullTopShape(SlabRampShapes.BIT_W);

        double prevBot = 0.0;
        double prevTop = 0.5;
        for (int i = 0; i < 16; i++) {
            double x = (i + 0.5) / 16.0;
            double hb = sampleSurfaceY(bot, x, 0.5);
            double ht = sampleSurfaceY(top, x, 0.5);
            double hf = sampleSurfaceY(full, x, 0.5);
            double expectedBot = i < 8 ? (i + 1) * (0.5 / 8.0) : 0.5;
            assertEquals(expectedBot, hb, 0.002, "bottom x=" + x);
            assertEquals(0.5 + expectedBot, ht, 0.002, "top x=" + x);
            assertEquals(ht, hf, 0.002, "fullTop x=" + x);
            assertTrue(hb >= prevBot && (hb - prevBot) <= 0.0625 + 0.003);
            assertTrue(ht >= prevTop && (ht - prevTop) <= 0.0625 + 0.003);
            prevBot = hb;
            prevTop = ht;
        }

        // 玩家碰撞箱半寬 0.3 格：中心到 x=0.7（前緣剛好接觸下一格 x=1.0）時必須已站上完整 0.5 / 1.0 高度，避免上 1 格方塊或連續兩階半磚卡住
        assertEquals(0.5, sampleSurfaceY(bot, 0.7, 0.5), 0.002);
        assertEquals(1.0, sampleSurfaceY(top, 0.7, 0.5), 0.002);
        assertEquals(1.0, sampleSurfaceY(full, 0.7, 0.5), 0.002);
    }

    @Test
    void oppositeSidesRampPeaksAtCenter() {
        int maskWE = SlabRampShapes.BIT_W | SlabRampShapes.BIT_E;
        VoxelShape bot = SlabRampShapes.bottomShape(maskWE);
        VoxelShape top = SlabRampShapes.topShape(maskWE);
        VoxelShape full = SlabRampShapes.fullTopShape(maskWE);

        assertEquals(0.5, sampleSurfaceY(bot, 0.5, 0.5), 0.002);
        assertEquals(1.0, sampleSurfaceY(top, 0.5, 0.5), 0.002);
        assertEquals(1.0, sampleSurfaceY(full, 0.5, 0.5), 0.002);
        assertTrue(sampleSurfaceY(bot, 0.03, 0.5) <= 0.13);
        assertTrue(sampleSurfaceY(bot, 0.97, 0.5) <= 0.13);
    }

    @Test
    void cornerRampIsLowAtCornerAndFullHeightOpposite() {
        int maskNW = SlabRampShapes.BIT_N | SlabRampShapes.BIT_W;
        VoxelShape bot = SlabRampShapes.bottomShape(maskNW);
        VoxelShape top = SlabRampShapes.topShape(maskNW);

        assertTrue(sampleSurfaceY(bot, 0.05, 0.05) <= 0.125 + 0.002);
        assertEquals(0.5, sampleSurfaceY(bot, 0.95, 0.95), 0.002);

        assertTrue(sampleSurfaceY(top, 0.05, 0.05) <= 0.625 + 0.002);
        assertEquals(1.0, sampleSurfaceY(top, 0.95, 0.95), 0.002);
    }
}
