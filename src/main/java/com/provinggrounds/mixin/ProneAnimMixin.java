package com.provinggrounds.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.provinggrounds.core.Hitbox;
import com.provinggrounds.core.WallClimb;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ProneAnimMixin.java
 * 目的: 重製陸地趴下／站起的人物身體旋轉動畫（只在客戶端）。
 *   - 原版 PlayerRenderer.setupRotations 在 isVisuallySwimming() 為 true 的第一幀就會立刻把人物
 *     往下平移 -1.0 格再慢慢旋轉，導致看起來像整個人先插進地板再趴下。
 *   - 這裡在「不在水中、未滑翔、未激流、未睡覺、且 swimAmount > 0」時接管旋轉：
 *     把貼地平移（Hitbox.PRONE_Y / PRONE_Z）放在 RX(-90°) 之前並套用平滑曲線（Hitbox.proneEase），
 *     讓人物以腳底為基準平順地趴到地面與起身，任何一幀都不會插進地板，且與 Hitbox 矩陣 100% 一致。
 */
@Mixin(PlayerRenderer.class)
public abstract class ProneAnimMixin {

    @Inject(method = {
        "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
        "m_7523_(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$smoothProneRotations(AbstractClientPlayer player, PoseStack poseStack,
                                                     float ageInTicks, float rotationYaw, float partialTicks,
                                                     CallbackInfo ci) {
        if (player.isInWater() || player.isFallFlying() || player.isAutoSpinAttack() || player.isSleeping()) return;
        float swim = player.getSwimAmount(partialTicks);
        float climb = swim <= 0.0001f ? WallClimb.progress(player, partialTicks) : 0.0f;
        if (swim <= 0.0001f && climb <= 0.0001f) return;

        // 1. 與原版 LivingEntityRenderer.setupRotations 一致的水平朝向與死亡傾倒
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - rotationYaw));
        if (player.deathTime > 0) {
            float f = ((float) player.deathTime + partialTicks - 1.0f) / 20.0f * 1.6f;
            f = Mth.sqrt(f);
            if (f > 1.0f) f = 1.0f;
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90.0f));
        }

        // 2. 貼地平滑趴下／站起，或翻爬前方台階／牆面前傾（與 Hitbox.computeParts 完全同步）
        if (swim > 0.0001f) {
            float e = Hitbox.proneEase(swim);
            poseStack.translate(0.0f, Hitbox.PRONE_Y * e, Hitbox.PRONE_Z * e);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f * e));
        } else {
            float w = Mth.sin(climb * (float) Math.PI);
            poseStack.translate(0.0f, Hitbox.CLIMB_Y * w, Hitbox.CLIMB_Z * w);
            poseStack.mulPose(Axis.XP.rotationDegrees(-Hitbox.CLIMB_LEAN_DEG * w));
        }
        ci.cancel();
    }
}
