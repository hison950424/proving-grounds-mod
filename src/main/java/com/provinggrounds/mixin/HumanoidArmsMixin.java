package com.provinggrounds.mixin;

import com.provinggrounds.item.ParachuteItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: HumanoidArmsMixin.java
 * 目的: 降落傘打開時雙手舉高抓住傘繩（只在客戶端）。原版每一幀算好人物姿勢（setupAnim）之後，
 *   如果這個玩家手上拿著打開的降落傘，就把兩隻手臂改成舉過頭頂、稍微往外張。
 *   玩家模型的袖子（第二層皮膚）會在這之後照手臂的角度複製，所以袖子也會跟著舉起來。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（setupAnim = m_6973_），不需要 refmap。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidArmsMixin {

    @Inject(method = {
        "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
        "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
    }, at = @At("TAIL"), remap = false)
    private void provinggrounds$parachuteArms(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                              float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player p) || !ParachuteItem.isOpen(p.getMainHandItem())) return;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        float sway = (float) Math.sin(ageInTicks * 0.08f) * 0.05f; // 輕微晃動
        m.rightArm.xRot = (float) Math.PI + 0.15f + sway;
        m.leftArm.xRot = (float) Math.PI + 0.15f - sway;
        m.rightArm.yRot = 0f;
        m.leftArm.yRot = 0f;
        m.rightArm.zRot = 0.35f;
        m.leftArm.zRot = -0.35f;
    }
}
