package com.provinggrounds.mixin;

import com.provinggrounds.events.BulletHitEvents;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

/**
 * 檔案: ExplosionHitPlayersMixin.java
 * 目的: 爆炸不擊退玩家（BulletHitEvents 的擊退規則）。原版和 TACZ 的爆炸用 getHitPlayers() 取得
 *   「每個玩家被推多少」放進爆炸封包，客戶端照著推人；這裡讓它回傳空的，推力就是 0（爆炸傷害照舊）。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（getHitPlayers = m_46078_），不需要 refmap。
 */
@Mixin(Explosion.class)
public abstract class ExplosionHitPlayersMixin {

    @Inject(method = { "getHitPlayers", "m_46078_" }, at = @At("RETURN"), cancellable = true, remap = false)
    private void provinggrounds$noExplosionPush(CallbackInfoReturnable<Map<?, ?>> cir) {
        if (BulletHitEvents.noKnockback()) cir.setReturnValue(Collections.emptyMap());
    }
}
