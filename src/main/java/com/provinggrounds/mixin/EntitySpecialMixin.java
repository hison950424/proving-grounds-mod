package com.provinggrounds.mixin;

import com.provinggrounds.special.SpecialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: EntitySpecialMixin.java
 * 目的: 特殊物品需要改原版實體行為的地方（計畫書 4）：
 *   - 發光引擎：客戶端「這個實體現在要不要畫發光外框」（isCurrentlyGlowing = m_142038_）——
 *     伺服器只把發光清單送給看得到的人，所以「單向發光」別人看不到。
 *   - 腳步聲：玩家的腳步聲改由 Footsteps 播放（奔跑較大聲、靜走較小聲、不同材質差異加大）。
 *   - 靜音靴：有靜音效果的玩家，腳步、游泳、落水的聲音都不播（playStepSound = m_7355_、
 *     playCombinationStepSounds = m_276961_、playMuffledStepSound = m_280568_、playSwimSound = m_5625_、
 *     doWaterSplashEffect = m_5841_）。槍聲等其他聲音不受影響。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱，不需要 refmap。
 */
@Mixin(Entity.class)
public abstract class EntitySpecialMixin {

    @Inject(method = { "isCurrentlyGlowing", "m_142038_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$specialGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && SpecialHooks.CLIENT_GLOWING.contains(self.getId())) cir.setReturnValue(true);
    }

    @Inject(method = { "playStepSound", "m_7355_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentStep(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (SpecialHooks.silent((Entity) (Object) this)) ci.cancel();
        else if ((Object) this instanceof net.minecraft.world.entity.player.Player p && com.provinggrounds.core.Footsteps.play(p, pos, state)) ci.cancel(); // 奔跑／靜走的音量、材質差異
    }

    @Inject(method = { "playCombinationStepSounds", "m_276961_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentCombo(BlockState a, BlockState b, BlockPos pa, BlockPos pb, CallbackInfo ci) {
        if (SpecialHooks.silent((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = { "playMuffledStepSound", "m_280568_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentMuffled(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (SpecialHooks.silent((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = { "playSwimSound", "m_5625_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentSwim(float volume, CallbackInfo ci) {
        if (SpecialHooks.silent((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = { "doWaterSplashEffect", "m_5841_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$silentSplash(CallbackInfo ci) {
        if (SpecialHooks.silent((Entity) (Object) this)) ci.cancel();
    }
}
