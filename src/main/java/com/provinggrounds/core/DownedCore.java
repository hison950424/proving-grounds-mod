package com.provinggrounds.core;

import com.provinggrounds.modes.br.BRMovement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 檔案: DownedCore.java
 * 目的: 倒地系統的共用部分（計畫書 7 E）。大逃殺（BattleRoyaleMode）與團隊模式倒地（arena/ArenaDowned）都用這裡，
 *   不各自寫一份：
 *     - applyDowned：倒地瞬間要做的事（停止使用物品、關掉容器、停止衝刺、滅火、血量設成倒地血量、倒地緩速）。
 *     - clearDowned：解除倒地（姿勢、緩速）。
 *     - aimsAtEntity：準心是否對著某個實體（扶人用：視線碰到稍微放大的碰撞箱、中間沒有方塊擋住；計畫書 6 A19）。
 *   規則（能不能倒地、流血、扶起時間與血量）沿用大逃殺的設定（br_* 的 downed／revive／bleed 數值）。
 */
public final class DownedCore {

    private DownedCore() {}

    public static void applyDowned(ServerPlayer victim, float health) {
        victim.stopUsingItem();
        victim.closeContainer();
        victim.setSprinting(false);
        victim.clearFire();
        victim.setHealth(Math.min(health, victim.getMaxHealth()));
        BRMovement.applyDowned(victim);
    }

    public static void clearDowned(ServerPlayer p) {
        p.setForcedPose(null);
        BRMovement.removeDowned(p);
    }

    public static boolean aimsAtEntity(ServerPlayer p, Entity target, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range + 1.5));
        Optional<Vec3> hit = target.getBoundingBox().inflate(0.35).clip(eye, end);
        if (hit.isEmpty()) return false;
        BlockHitResult bh = p.level().clip(new ClipContext(eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return bh.getType() == HitResult.Type.MISS;
    }
}
