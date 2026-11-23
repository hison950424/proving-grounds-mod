package com.provinggrounds.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: EntitySprintMixin.java
 * 目的: 隱形的玩家跑步時不產生腳下的跑步粒子（計畫書 4 的 A2），否則別人看粒子就知道他在哪。
 *   原版每個實體每 tick 問 canSpawnSprintParticle()，回傳 true 才噴粒子；這裡對隱形玩家一律回傳 false。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（canSpawnSprintParticle = m_5843_），不需要 refmap。
 *   腳步聲不受影響。
 */
@Mixin(Entity.class)
public abstract class EntitySprintMixin {

    @Inject(method = { "canSpawnSprintParticle", "m_5843_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$noSprintParticleWhenInvisible(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player && self.isInvisible()) cir.setReturnValue(false);
    }
}
