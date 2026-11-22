package com.provinggrounds.mixin;

import com.provinggrounds.core.PoseOverride;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: PlayerPoseMixin.java
 * 目的: 趴下／倒地趴地時強制游泳姿勢（模型趴下、第一人稱鏡頭變低、碰撞箱 0.6 格高）。
 *   為什麼用 Mixin：原本用 setForcedPose，但 TACZ 的匍匐功能每 tick 都把本地玩家的強制姿勢清成 null，
 *   沒有事件可以在「TACZ 清掉之後、原版決定姿勢之前」介入，只能在原版決定姿勢的方法開頭接手。
 *   方法名稱同時寫開發名稱（updatePlayerPose）與 SRG 名稱（m_7594_），不需要 refmap。
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {

    @Inject(method = { "updatePlayerPose", "m_7594_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$forceCrawl(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (PoseOverride.mustCrawl(self)) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
