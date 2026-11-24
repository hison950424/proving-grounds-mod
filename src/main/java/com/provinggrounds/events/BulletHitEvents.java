package com.provinggrounds.events;

import com.provinggrounds.config.PGConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 檔案: BulletHitEvents.java
 * 目的: 擊退規則（伺服器端，設定 [combat] attack_knockback = false 時）：被任何攻擊打中都不會被擊退
 *   （子彈、近戰、槍托、爆炸彈、手榴彈、TNT、怪物…）。只有特別允許的才會推人：衝擊波、擊退盾衝撞、彈簧鞋
 *   （這些地方呼叫 allow，本 tick 內不擋）。
 *   擋的地方：
 *     1. LivingKnockBackEvent（原版的擊退，近戰、子彈、爆炸傷害都會觸發）→ 取消；
 *     2. 爆炸的推力：原版和 TACZ 用爆炸封包把推力送給玩家 → ExplosionHitPlayersMixin 讓推力清單是空的；
 *        伺服器端加在玩家身上的速度 → 這個 tick 結束時改回爆炸前的速度；
 *     3. 受傷後伺服器會把速度同步給客戶端（hurtMarked），那會變成被推一下 → 這個 tick 結束時取消同步。
 *   畫面抖動在客戶端處理（ClientForgeEvents）。
 */
public class BulletHitEvents {

    public static final TagKey<DamageType> BULLETS =
        com.provinggrounds.compat.GunCompat.BULLET_DAMAGE;

    /** 本 tick 允許被推的實體 id。 */
    private static final Set<Integer> ALLOWED = new HashSet<>();
    /** 本 tick 受傷或被爆炸波及的玩家 → 爆炸前的速度（沒被爆炸波及就是 null）。 */
    private static final Map<ServerPlayer, Vec3> HURT = new HashMap<>();

    /** 這個 tick 內允許這個實體被推（衝擊波、擊退盾、彈簧鞋）。 */
    public static void allow(Entity e) {
        if (!e.level().isClientSide) ALLOWED.add(e.getId());
    }

    public static boolean noKnockback() {
        try {
            return !PGConfig.SERVER.attackKnockback.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide || !noKnockback() || ALLOWED.contains(event.getEntity().getId())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAmount() > 0 && noKnockback()) HURT.putIfAbsent(p, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide || !noKnockback()) return;
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof ServerPlayer p && HURT.get(p) == null) HURT.put(p, p.getDeltaMovement()); // 記下爆炸前的速度
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<ServerPlayer, Vec3> e : HURT.entrySet()) {
            ServerPlayer p = e.getKey();
            if (ALLOWED.contains(p.getId())) continue;
            if (e.getValue() != null) p.setDeltaMovement(e.getValue()); // 爆炸推的速度改回去
            p.hurtMarked = false; // 不要把被打時的速度送給客戶端
        }
        HURT.clear();
        ALLOWED.clear();
    }
}
