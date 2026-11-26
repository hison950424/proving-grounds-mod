package com.provinggrounds.mixin;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.Hitbox;
import com.provinggrounds.core.WallClimb;
import com.provinggrounds.network.ChuteSyncPacket;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: HumanoidArmsMixin.java
 * 目的: 玩家四肢動畫覆寫（只在客戶端，priority = 1500 確保在原版與 TACZ 第三人稱持槍之後執行）：
 *   1. 陸地趴下過渡與爬行動畫（swimAmount > 0 且不在水中）：取代原版在水裡的蛙式划水動作，
 *      直接套用 Hitbox.computeLimbPose() 算出的平滑姿勢（含趴下持槍瞄準、趴下低姿匍匐），
 *      讓畫面上的四肢、第二層皮膚（袖子／褲管）與伺服器／F3+B 的 Hitbox 100% 同步。
 *   2. 降落傘打開時雙手舉高抓住傘繩。
 */
@Mixin(value = HumanoidModel.class, priority = 1500)
public abstract class HumanoidArmsMixin {

    @Inject(method = {
        "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
        "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
    }, at = @At("TAIL"), remap = false)
    private void provinggrounds$customPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                           float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player p)) return;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        boolean chuteOpen = ChuteSyncPacket.isOpen(p.getId()) && !p.onGround() && !p.isInWater();

        // 陸地趴下／站起過渡、爬行，或翻爬柵欄／石牆：直接使用與 Hitbox 相同的算式，徹底消除與 Hitbox 偏差
        float pt = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float climb = (!p.isInWater() && !p.isFallFlying() && !p.isSleeping())
            ? WallClimb.progress(p, pt) : 0.0f;
        if (!p.isInWater() && !p.isFallFlying() && !p.isSleeping() && (m.swimAmount > 0.0001f || climb > 0.0001f)) {
            boolean stowed = WallClimb.isHandsStowed(p, pt);
            boolean holdsGun = !stowed && GunCompat.holdsGun(p);
            float aim = holdsGun ? GunCompat.aimProgress(p) : 0.0f;
            UseAnim mainUse = (!stowed && p.isUsingItem() && p.getUsedItemHand() == InteractionHand.MAIN_HAND)
                ? p.getUseItem().getUseAnimation() : UseAnim.NONE;
            UseAnim offUse = (!stowed && p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND)
                ? p.getUseItem().getUseAnimation() : UseAnim.NONE;

            Hitbox.PoseInput input = new Hitbox.PoseInput(
                m.crouching,
                false,
                m.swimAmount,
                netHeadYaw,
                headPitch,
                limbSwing,
                limbSwingAmount,
                m.attackTime,
                p.getMainArm() == HumanoidArm.RIGHT,
                holdsGun,
                aim,
                !stowed && !p.getMainHandItem().isEmpty(),
                !stowed && !p.getOffhandItem().isEmpty(),
                mainUse,
                offUse,
                chuteOpen,
                climb
            );
            Hitbox.LimbPose pose = Hitbox.computeLimbPose(input);
            copyToPart(pose.head, m.head);
            copyToPart(pose.body, m.body);
            copyToPart(pose.rightArm, m.rightArm);
            copyToPart(pose.leftArm, m.leftArm);
            copyToPart(pose.rightLeg, m.rightLeg);
            copyToPart(pose.leftLeg, m.leftLeg);
            return;
        }

        // 開傘中：雙手舉高抓傘繩（帶輕微風吹晃動）
        if (!chuteOpen) return;
        float sway = (float) Math.sin(ageInTicks * 0.08f) * 0.05f;
        m.rightArm.xRot = (float) Math.PI + 0.15f + sway;
        m.leftArm.xRot = (float) Math.PI + 0.15f - sway;
        m.rightArm.yRot = 0f;
        m.leftArm.yRot = 0f;
        m.rightArm.zRot = 0.35f;
        m.leftArm.zRot = -0.35f;
    }

    private static void copyToPart(Hitbox.PartTransform src, ModelPart dst) {
        dst.x = src.x;
        dst.y = src.y;
        dst.z = src.z;
        dst.xRot = src.xRot;
        dst.yRot = src.yRot;
        dst.zRot = src.zRot;
    }
}
