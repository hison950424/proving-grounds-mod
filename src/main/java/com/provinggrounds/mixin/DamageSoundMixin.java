package com.provinggrounds.mixin;

import com.provinggrounds.ui.ClientSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: DamageSoundMixin.java
 * 目的: 讓客戶端知道「現在播的受傷聲是哪一種傷害造成的」（只在客戶端）。
 *   客戶端收到受傷封包時呼叫 handleDamageEvent，裡面會先播受傷聲、之後才記下傷害來源，
 *   所以在方法開頭把傷害來源記到 ClientSounds，方法結束時清掉；ClientSounds 依此把圈外傷害的受傷聲調小。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（handleDamageEvent = m_269138_），不需要 refmap。
 */
@Mixin(LivingEntity.class)
public abstract class DamageSoundMixin {

    @Inject(method = {
        "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V",
        "m_269138_(Lnet/minecraft/world/damagesource/DamageSource;)V"
    }, at = @At("HEAD"), remap = false)
    private void provinggrounds$damageSoundStart(DamageSource source, CallbackInfo ci) {
        ClientSounds.currentDamage = source;
    }

    @Inject(method = {
        "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V",
        "m_269138_(Lnet/minecraft/world/damagesource/DamageSource;)V"
    }, at = @At("RETURN"), remap = false)
    private void provinggrounds$damageSoundEnd(DamageSource source, CallbackInfo ci) {
        ClientSounds.currentDamage = null;
    }
}
