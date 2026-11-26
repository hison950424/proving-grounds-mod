package com.provinggrounds.mixin;

import com.provinggrounds.core.WallClimb;
import com.provinggrounds.ui.StairMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: StairMovementMixin.java
 * 目的: 接管本地玩家在樓梯與攀爬時的移動計算（只在客戶端）：
 *   - 攀爬期間於 travel HEAD 直接執行平順曲線並取消原版重力／碰撞擠壓，徹底消除第一人稱視角抖動。
 *   - 樓梯上將水平移動向量降至 70%，travel 結束後執行下樓梯自動降階貼地。
 */
@Mixin(LivingEntity.class)
public abstract class StairMovementMixin {

    @Inject(method = {
        "travel(Lnet/minecraft/world/phys/Vec3;)V",
        "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$wallClimbTravel(Vec3 travelVector, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer p && p == Minecraft.getInstance().player
                && WallClimb.stepLocalTravel(p)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = {
        "travel(Lnet/minecraft/world/phys/Vec3;)V",
        "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private Vec3 provinggrounds$scaleStairTravel(Vec3 travelVector) {
        if ((Object) this instanceof LocalPlayer p && p == Minecraft.getInstance().player) {
            return StairMovement.beforeTravelAndScale(p, travelVector);
        }
        return travelVector;
    }

    @Inject(method = {
        "travel(Lnet/minecraft/world/phys/Vec3;)V",
        "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"
    }, at = @At("TAIL"), remap = false)
    private void provinggrounds$stepDownStairs(Vec3 travelVector, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer p && p == Minecraft.getInstance().player) {
            StairMovement.afterTravel(p);
        }
    }
}
