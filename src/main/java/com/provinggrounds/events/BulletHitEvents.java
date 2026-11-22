package com.provinggrounds.events;

import com.provinggrounds.config.PGConfig;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 檔案: BulletHitEvents.java
 * 目的: 被 TACZ 子彈打中時不會被擊退（伺服器端，設定 [combat] bullet_knockback = false）。
 *   原版受傷流程：先觸發 LivingAttackEvent（帶傷害來源）→ 扣血 → 擊退（LivingKnockBackEvent，不帶來源）。
 *   所以在 LivingAttackEvent 記下「這個實體這個 tick 被子彈打」，同一 tick 的擊退就取消。
 *   畫面抖動在客戶端處理（ClientForgeEvents）。
 */
public class BulletHitEvents {

    public static final TagKey<DamageType> BULLETS =
        com.provinggrounds.compat.GunCompat.BULLET_DAMAGE;

    /** 實體 id → 最後被子彈打中的遊戲時間。 */
    private final Map<Integer, Long> lastBulletHit = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || !event.getSource().is(BULLETS)) return;
        long now = event.getEntity().level().getGameTime();
        if (lastBulletHit.size() > 512) lastBulletHit.values().removeIf(t -> t != now); // 清掉舊紀錄
        lastBulletHit.put(event.getEntity().getId(), now);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide || PGConfig.SERVER.bulletKnockback.get()) return;
        Long t = lastBulletHit.get(event.getEntity().getId());
        if (t != null && t == event.getEntity().level().getGameTime()) event.setCanceled(true);
    }
}
