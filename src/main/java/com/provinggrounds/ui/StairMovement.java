package com.provinggrounds.ui;

import com.provinggrounds.core.WallClimb;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 檔案: StairMovement.java
 * 目的: 樓梯移動手感調整（只在客戶端執行）：
 *   1. 上／下樓梯（踩在正放樓梯方塊表面時）將水平移動減速 30%（乘以 0.70），
 *      在 LivingEntity.travel 套用輸入向量縮放，保留奔跑狀態與 FOV 不閃爍。
 *   2. 下樓梯時自動貼地降階（step-down），確保高速奔跑也不會飛越台階，
 *      每一階都穩穩踩實並維持 onGround = true，使上樓梯與下樓梯速度感完全一致。
 */
public final class StairMovement {

    /** 上下樓梯時的速度倍率（減少 30% = 原速度的 70%）。 */
    public static final double STAIR_SPEED_MULT = 0.70;

    /** 下樓梯貼地檢查的最大垂直深度（涵蓋 0.5 格台階落差，不會吸住高處斷崖）。 */
    private static final double MAX_STEP_DOWN = 0.62;

    /** 腳底碰撞寬度的水平探針偏移（涵蓋邊緣踏步但窄於玩家寬度 0.30）。 */
    private static final double PROBE_HALF = 0.24;

    private static boolean wasOnGround;
    private static boolean wasOnStair;
    private static boolean wasJumping;

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

    /** 目前玩家是否踩在正放樓梯上（含空中躍過樓梯台階上方時避免跳躍繞過減速）。 */
    public static boolean isStandingOnStair(LocalPlayer p) {
        if (isStairSurfaceAt(p, p.getY())) return true;
        if (!p.onGround()) {
            Level level = p.level();
            BlockPos feet = BlockPos.containing(p.getX(), p.getY() + 0.05, p.getZ());
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && isUprightStair(level.getBlockState(feet.below()))) {
                return true;
            }
        }
        return false;
    }

    /** travel() 進入前記錄地面／樓梯狀態，並在樓梯上把水平輸入向量降至 70%。 */
    public static Vec3 beforeTravelAndScale(LocalPlayer p, Vec3 travelVector) {
        if (!isEligible(p)) {
            wasOnGround = false;
            wasOnStair = false;
            wasJumping = false;
            return travelVector;
        }
        wasOnGround = p.onGround();
        wasOnStair = isStandingOnStair(p);
        wasJumping = p.input.jumping;

        if (!wasOnStair) return travelVector;

        double hx = travelVector.x;
        double hz = travelVector.z;
        double horizSq = hx * hx + hz * hz;
        if (horizSq <= 1.0e-8) return travelVector;
        if (horizSq > 1.0) {
            double inv = 1.0 / Math.sqrt(horizSq);
            hx *= inv;
            hz *= inv;
        }
        return new Vec3(hx * STAIR_SPEED_MULT, travelVector.y, hz * STAIR_SPEED_MULT);
    }

    /** travel() 結束後檢查下樓梯落差，若剛走離台階就貼地踏上每一格階梯。 */
    public static void afterTravel(LocalPlayer p) {
        if (!isEligible(p) || !wasOnGround || wasJumping || p.input.jumping || p.onGround()) return;
        Vec3 dm = p.getDeltaMovement();
        if (dm.y > 0.01) return;

        Vec3 snap = Entity.collideBoundingBox(
            p, new Vec3(0.0, -MAX_STEP_DOWN, 0.0), p.getBoundingBox(), p.level(), List.of());
        if (snap.y >= -1.0e-4 || snap.y <= -(MAX_STEP_DOWN - 0.002)) return;
        if (!wasOnStair && !isStairSurfaceAt(p, p.getY() + snap.y)) return;

        p.setPos(p.getX(), p.getY() + snap.y, p.getZ());
        double vx = dm.x;
        double vz = dm.z;
        double hSpd = Math.hypot(vx, vz);
        if (hSpd > 0.38) {
            double scale = 0.38 / hSpd;
            vx *= scale;
            vz *= scale;
        }
        p.setDeltaMovement(vx, 0.0, vz);
        p.setOnGround(true);
        p.fallDistance = 0.0f;
    }
}
