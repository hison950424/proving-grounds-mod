package com.provinggrounds.mixin;

import com.provinggrounds.network.MeleeGlassPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: MeleeGlassMixin.java
 * 目的: 讓 lrtactical 每把近戰武器在「各自真正的揮刀命中幀」打破準星指到的玻璃／玻璃片／鐵欄杆。
 *   lrtactical 每次左／右鍵揮刀都會先檢查該武器的拔刀時間與冷卻，並等待該武器的攻擊延遲（getAttackDelay）
 *   後才送出 CMeleeAttackRequest，由伺服器端 CombatProperties.postAttack 呼叫 IMeleeWeapon.attack。
 *   掛勾在這裡即可自動跟著每把刀不同的輕／重擊與連段時機破玻璃，不再共用固定冷卻。
 *   沒裝 lrtactical 時直接略過（@Pseudo、required = false、require = 0）。
 */
@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.capability.CombatProperties", remap = false)
public abstract class MeleeGlassMixin {

    @Shadow(remap = false)
    @Final
    private Player entity;

    @Inject(
        method = "postAttack(Lme/xjqsh/lrtactical/api/melee/MeleeAction;ILjava/util/List;)V",
        at = @At(
            value = "INVOKE",
            target = "Lme/xjqsh/lrtactical/api/item/IMeleeWeapon;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lme/xjqsh/lrtactical/api/melee/MeleeAction;Ljava/util/List;I)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void provinggrounds$breakGlassOnMeleeHit(CallbackInfo ci) {
        if (this.entity instanceof ServerPlayer sp) {
            MeleeGlassPacket.tryBreakGlassAtAim(sp);
        }
    }
}
