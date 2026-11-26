package com.provinggrounds.mixin;

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
 * 目的: 接管本地玩家在樓梯上的移動計算（只在客戶端）：
 *   - travel 進入時將樓梯上的水平移動向量降至 70%（不影響疾跑判定與 FOV）。
 *   - travel 結束後執行下樓梯自動降階貼地，確保每一階台階都踩實、上下樓梯速度感一致。
 */
@Mixin(LivingEntity.class)
public abstract class StairMovementMixin {

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
