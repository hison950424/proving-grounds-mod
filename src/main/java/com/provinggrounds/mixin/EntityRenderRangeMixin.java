package com.provinggrounds.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: EntityRenderRangeMixin.java
 * 目的: 客戶端強制讓 512 格（32 區塊）內的玩家與 TACZ 子彈實體保持渲染，搭配 EntityTrackingRangeMixin
 *       確保雙端超遠距交火與狙擊皆可正常看見目標與彈道。
 */
@Mixin(Entity.class)
public abstract class EntityRenderRangeMixin {

    private static final double MAX_RENDER_DIST_SQR = 512.0 * 512.0;

    @Inject(method = { "shouldRenderAtSqrDistance(D)Z", "m_6783_" },
            at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$expandRenderRange(double distSqr, CallbackInfoReturnable<Boolean> cir) {
        if (distSqr > MAX_RENDER_DIST_SQR) return;
        Entity self = (Entity) (Object) this;
        if (self instanceof Player || self.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")) {
            cir.setReturnValue(true);
        }
    }
}
