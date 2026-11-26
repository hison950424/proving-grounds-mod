package com.provinggrounds.mixin;

import com.provinggrounds.core.WallClimb;
import com.provinggrounds.ui.ClientSkydive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: EntityTurnMixin.java
 * 目的: 在滑鼠轉動套用到鏡頭的當下直接夾限（只在客戶端）：
 *   1. 攀爬柵欄／石牆期間限制左右轉頭角度（±45°）。
 *   2. 跳傘鞘翅滑翔期間限制最大抬頭角度（glide_pitch_up_max）。
 *   因為是在 Entity.turn 輸入層直接擋住超出的角度變化量，玩家感受到的是乾脆的「頂到牆／卡住」，不會有每 tick 被拉回來的橡皮筋感。
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {

    @Inject(method = { "turn(DD)V", "m_19884_(DD)V" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$clampCameraTurn(double yRotDelta, double xRotDelta, CallbackInfo ci) {
        LocalPlayer local = Minecraft.getInstance().player;
        if ((Object) this != local || local == null) return;

        float minPitch = ClientSkydive.glideMinPitch(local);
        float baseYaw = WallClimb.climbBaseYaw(local);
        if (Float.isNaN(minPitch) && Float.isNaN(baseYaw)) return;

        float dPitch = (float) xRotDelta * 0.15f;
        float dYaw = (float) yRotDelta * 0.15f;

        float pitchMin = Float.isNaN(minPitch) ? -90.0f : minPitch;
        float curPitch = local.getXRot();
        float newPitch = Mth.clamp(curPitch + dPitch, pitchMin, 90.0f);
        float appliedPitch = newPitch - curPitch;
        local.setXRot(newPitch);
        local.xRotO = Mth.clamp(local.xRotO + appliedPitch, pitchMin, 90.0f);

        if (!Float.isNaN(baseYaw)) {
            float curOff = Mth.wrapDegrees(local.getYRot() - baseYaw);
            float newOff = Mth.clamp(curOff + dYaw, -WallClimb.MAX_YAW_OFFSET, WallClimb.MAX_YAW_OFFSET);
            float appliedYaw = newOff - curOff;
            local.setYRot(local.getYRot() + appliedYaw);
            local.yRotO += appliedYaw;
        } else {
            local.setYRot(local.getYRot() + dYaw);
            local.yRotO += dYaw;
        }

        if (local.getVehicle() != null) {
            local.getVehicle().onPassengerTurned(local);
        }
        ci.cancel();
    }
}
