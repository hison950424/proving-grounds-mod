package com.provinggrounds.ui;

import com.provinggrounds.core.WallClimb;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 檔案: StairMovement.java
 * 目的: 樓梯移動手感調整（只在客戶端執行）：
 *   1. 依實際移動方向量測腳底前方 45° 斜坡幾何坡度 m = |dy| / PROBE_DIST，
 *      並以 1 / sqrt(1 + m^2) 縮放水平輸入向量：
 *      - 直上或直下 45° 樓梯（m = 1）：水平分量 × 0.7071，合成 3D 實際移動速度 = 平地 100%（不變快也不變慢）。
 *      - 斜向／橫向走樓梯或在轉角平台：依實際坡度精準補償，橫走與平地皆維持 1.0× 原速。
 *   2. 下樓梯時自動貼地降階（step-down），確保高速奔跑也不會飛越台階，每一階都穩穩踩實。
 *   3. 同步補足 3D 移動距離到 walkDist（與下樓時的 moveDist），讓鏡頭晃動與腳步聲節奏與平地一致。
 */
public final class StairMovement {

    /** 坡度探測前進距離（0.25 格剛好對齊 StairRampShapes 的 4 階 1/16 微台階，無量化抖動）。 */
    private static final double PROBE_DIST = 0.25;
    private static final double PROBE_UP = 0.35;
    private static final double PROBE_DOWN = 0.70;
    private static final double FEET_PROBE_HEIGHT = 0.50;
    private static final double MAX_RAMP_DY = PROBE_DIST + 0.0625;

    /** 下樓梯貼地檢查的最大垂直深度（涵蓋 0.5 格台階落差，不會吸住高處斷崖）。 */
    private static final double MAX_STEP_DOWN = 0.62;

    /** 腳底碰撞寬度的水平探針偏移（涵蓋邊緣踏步但窄於玩家寬度 0.30）。 */
    private static final double PROBE_HALF = 0.24;

    private static boolean wasOnGround;
    private static boolean wasOnStair;
    private static boolean wasJumping;
    private static double preX, preY, preZ;

    private StairMovement() {}

    private static boolean isEligible(LocalPlayer p) {
        if (p == null || p.isSpectator() || p.isPassenger() || p.getAbilities().flying) return false;
        if (p.isFallFlying() || p.isInWater() || p.isInLava()) return false;
        return !WallClimb.isClientClimbing(p.getId());
    }

    private static boolean isUprightStair(BlockState st) {
        return st.getBlock() instanceof StairBlock
            && st.hasProperty(StairBlock.HALF)
            && st.getValue(StairBlock.HALF) == Half.BOTTOM;
    }

    /** 檢查指定高度表面下方／所在處是否踩在正放樓梯方塊上。 */
    private static boolean isStairSurfaceAt(LocalPlayer p, double surfaceY) {
        Level level = p.level();
        double px = p.getX();
        double pz = p.getZ();
        double[] dx = { 0.0, -PROBE_HALF, PROBE_HALF, -PROBE_HALF, PROBE_HALF };
        double[] dz = { 0.0, -PROBE_HALF, -PROBE_HALF, PROBE_HALF, PROBE_HALF };
        double[] dy = { 0.05, -0.10 };
        for (double oy : dy) {
            for (int i = 0; i < dx.length; i++) {
                BlockPos pos = BlockPos.containing(px + dx[i], surfaceY + oy, pz + dz[i]);
                if (isUprightStair(level.getBlockState(pos))) return true;
            }
        }
        return false;
    }

    private static boolean isNearUprightStair(LocalPlayer p, double dirX, double dirZ) {
        if (isStairSurfaceAt(p, p.getY())) return true;
        Level level = p.level();
        double ax = p.getX() + dirX * 0.30;
        double az = p.getZ() + dirZ * 0.30;
        return isUprightStair(level.getBlockState(BlockPos.containing(ax, p.getY() + 0.05, az)))
            || isUprightStair(level.getBlockState(BlockPos.containing(ax, p.getY() - 0.15, az)));
    }

    /** 量測玩家腳底在移動方向上的幾何坡度，回傳讓 3D 實際移動速度維持等於平地的水平縮放倍率。 */
    private static double computeSlopeSpeedScale(LocalPlayer p, double dirX, double dirZ) {
        if (!p.onGround() || !isNearUprightStair(p, dirX, dirZ)) return 1.0;

        AABB b = p.getBoundingBox();
        AABB feetBox = new AABB(b.minX, b.minY, b.minZ, b.maxX, b.minY + FEET_PROBE_HEIGHT, b.maxZ);
        AABB shiftedUp = feetBox.move(dirX * PROBE_DIST, PROBE_UP, dirZ * PROBE_DIST);
        if (!p.level().noCollision(p, shiftedUp)) return 1.0;

        Vec3 drop = Entity.collideBoundingBox(
            p, new Vec3(0.0, -PROBE_DOWN, 0.0), shiftedUp, p.level(), List.of());
        if (drop.y <= -(PROBE_DOWN - 0.002)) return 1.0;

        double dy = PROBE_UP + drop.y;
        if (Math.abs(dy) < 0.01 || Math.abs(dy) > MAX_RAMP_DY) return 1.0;

        double slope = Math.min(Math.abs(dy) / PROBE_DIST, 1.0);
        return 1.0 / Math.sqrt(1.0 + slope * slope);
    }

    /** travel() 進入前記錄位置與地面／樓梯狀態，並依移動方向實際坡度補償水平輸入使 3D 速度與平地相同。 */
    public static Vec3 beforeTravelAndScale(LocalPlayer p, Vec3 travelVector) {
        preX = p.getX();
        preY = p.getY();
        preZ = p.getZ();

        if (!isEligible(p)) {
            wasOnGround = false;
            wasOnStair = false;
            wasJumping = false;
            return travelVector;
        }
        wasOnGround = p.onGround();
        wasOnStair = isStairSurfaceAt(p, p.getY());
        wasJumping = p.input.jumping;

        if (!wasOnGround) return travelVector;

        double hx = travelVector.x;
        double hz = travelVector.z;
        double horizSq = hx * hx + hz * hz;
        if (horizSq <= 1.0e-8) return travelVector;

        double hLen = Math.sqrt(horizSq);
        double nx = hx / hLen;
        double nz = hz / hLen;
        float rad = p.getYRot() * ((float) Math.PI / 180.0F);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double dirX = nx * cos - nz * sin;
        double dirZ = nz * cos + nx * sin;

        double scale = computeSlopeSpeedScale(p, dirX, dirZ);
        if (scale >= 0.9999) return travelVector;

        if (horizSq > 1.0) {
            hx = nx;
            hz = nz;
        }
        return new Vec3(hx * scale, travelVector.y, hz * scale);
    }

    /** travel() 結束後檢查下樓梯落差貼地踏實，並同步 3D 移動距離以維持平地一致的晃動與腳步聲節奏。 */
    public static void afterTravel(LocalPlayer p) {
        if (!isEligible(p)) return;

        boolean snappedDown = false;
        if (wasOnGround && !wasJumping && !p.input.jumping && !p.onGround()) {
            Vec3 dm = p.getDeltaMovement();
            if (dm.y <= 0.01) {
                Vec3 snap = Entity.collideBoundingBox(
                    p, new Vec3(0.0, -MAX_STEP_DOWN, 0.0), p.getBoundingBox(), p.level(), List.of());
                if (snap.y < -1.0e-4 && snap.y > -(MAX_STEP_DOWN - 0.002)
                        && (wasOnStair || isStairSurfaceAt(p, p.getY() + snap.y))) {
                    p.setPos(p.getX(), p.getY() + snap.y, p.getZ());
                    double vx = dm.x;
                    double vz = dm.z;
                    double hSpd = Math.hypot(vx, vz);
                    if (hSpd > 0.38) {
                        double cap = 0.38 / hSpd;
                        vx *= cap;
                        vz *= cap;
                    }
                    p.setDeltaMovement(vx, 0.0, vz);
                    p.setOnGround(true);
                    p.fallDistance = 0.0f;
                    snappedDown = true;
                }
            }
        }

        if ((wasOnStair || snappedDown) && p.onGround()) {
            double dx = p.getX() - preX;
            double dy = p.getY() - preY;
            double dz = p.getZ() - preZ;
            if (Math.abs(dy) > 0.005 && Math.abs(dy) <= MAX_STEP_DOWN) {
                double horiz = Math.hypot(dx, dz);
                double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float extra = (float) ((dist3d - horiz) * 0.6);
                if (extra > 0.0f) {
                    p.walkDist += extra;
                    if (snappedDown) {
                        p.moveDist += extra;
                    }
                }
            }
        }
    }
}
