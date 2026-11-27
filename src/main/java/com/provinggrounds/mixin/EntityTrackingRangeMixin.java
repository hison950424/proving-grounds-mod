package com.provinggrounds.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: EntityTrackingRangeMixin.java
 * 目的: 強制將玩家與 TACZ 子彈實體的伺服器／客戶端追蹤距離鎖定為至少 32 區塊（512 格），
 *       避免遠距離瞄準或超遠距狙擊時超過原版 64～128 格實體追蹤上限而看不到敵人或子彈。
 */
@Mixin(EntityType.class)
public abstract class EntityTrackingRangeMixin {

    private static final int MIN_TRACKING_CHUNKS = 32; // 32 區塊 = 512 格（Minecraft 原版法定上限）

    @Inject(method = { "clientTrackingRange()I", "m_20681_" },
            at = @At("RETURN"), cancellable = true, remap = false)
    private void provinggrounds$expandTrackingRange(CallbackInfoReturnable<Integer> cir) {
        EntityType<?> self = (EntityType<?>) (Object) this;
        if (self == EntityType.PLAYER || isTaczProjectile(self)) {
            if (cir.getReturnValue() < MIN_TRACKING_CHUNKS) {
                cir.setReturnValue(MIN_TRACKING_CHUNKS);
            }
        }
    }

    private static boolean isTaczProjectile(EntityType<?> type) {
        String id = type.getDescriptionId();
        return id != null && id.contains("tacz") && id.contains("bullet");
    }
}
