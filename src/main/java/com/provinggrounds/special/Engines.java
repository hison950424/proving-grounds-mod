package com.provinggrounds.special;

import com.provinggrounds.core.TeamManager;
import com.provinggrounds.special.entity.DecoyEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: Engines.java
 * 目的: 共用引擎（計畫書 4 的 B4）：衝擊波、震盪、額外生命值。物品只傳參數進來，不自己寫一套。
 *   衝擊波：把範圍內的人往外震飛，傷害從中心 center_damage 往外線性遞減到 0，再附帶震盪。預設連施放者與隊友都會受影響（friendly_fire）；擊退盾的衝撞例外。
 *   震盪：自訂效果 provinggrounds:concussion（沒有粒子），客戶端讓視角不受控制地晃動，快結束時線性減弱。
 *   額外生命值：用原版吸收生命值（不會隨時間消失），血條上畫黃色。
 */
public final class Engines {

    private Engines() {}

    /** 傷害來源：有施放者時算他造成的（擊殺紀錄正確），沒有時是一般傷害。 */
    public static DamageSource source(ServerLevel level, @Nullable ServerPlayer by) {
        return by != null ? level.damageSources().playerAttack(by) : level.damageSources().magic();
    }

    /** 同隊（含自己）。 */
    public static boolean ally(@Nullable ServerPlayer by, LivingEntity e) {
        if (by == null) return false;
        if (e == by) return true;
        if (e instanceof ServerPlayer p) return TeamManager.get().isSameTeam(p.getUUID(), by.getUUID());
        return false;
    }

    /** 範圍內的玩家（含分身，分身被衝擊波打到就消失）。 */
    public static List<LivingEntity> livingAround(ServerLevel level, Vec3 c, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
            e -> e.isAlive() && !e.isSpectator() && (e instanceof Player || e instanceof DecoyEntity) && e.position().distanceTo(c) <= r);
    }

    /** 衝擊波：用設定檔的預設值。 */
    public static void shockwave(ServerLevel level, Vec3 c, double radius, @Nullable ServerPlayer by) {
        SpecialConfig s = SpecialConfig.get();
        // 預設會影響自己與隊友（friendly_fire；擊退盾的衝撞另外呼叫、不影響自己）
        shockwave(level, c, radius, s.shockCenterDamage.get(), SpecialConfig.ticks(s.shockConcussSeconds), by, !s.friendlyFire.get());
    }

    /**
     * 衝擊波。
     * @param spareAllies true = 施放者與隊友不受影響
     */
    public static void shockwave(ServerLevel level, Vec3 c, double radius, double centerDamage, int concussTicks,
                                 @Nullable ServerPlayer by, boolean spareAllies) {
        double knock = SpecialConfig.get().shockKnockback.get();
        for (LivingEntity e : livingAround(level, c, radius)) {
            if (spareAllies && ally(by, e)) continue;
            com.provinggrounds.events.BulletHitEvents.allow(e); // 衝擊波是特別允許的擊退（要在造成傷害之前）
            double dist = e.position().distanceTo(c);
            double k = Math.max(0, 1 - dist / radius);
            if (centerDamage > 0 && k > 0) e.hurt(source(level, by), (float) (centerDamage * k));
            Vec3 dir = e.position().subtract(c);
            dir = new Vec3(dir.x, 0, dir.z);
            dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : dir.normalize();
            double power = knock * (0.4 + 0.6 * k);
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * power, 0.35 + 0.35 * k, dir.z * power));
            e.hurtMarked = true; // 玩家的移動由客戶端決定：要把速度送回去
            if (concussTicks > 0) concuss(e, concussTicks);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, radius * 0.3, 0.2, radius * 0.3, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.5, c.z, 12, radius * 0.5, 0.2, radius * 0.5, 0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.3, c.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.15);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    /** 震盪 ticks（沒有粒子；已經在震盪時取比較長的）。 */
    public static void concuss(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(SpecialRegistry.CONCUSSION.get(), ticks, 0, false, false, true));
    }

    /** 加額外生命值（原版吸收生命值，不會隨時間消失）。 */
    public static void addExtraHealth(LivingEntity e, float amount) {
        e.setAbsorptionAmount(e.getAbsorptionAmount() + amount);
    }

    /** 套用一個沒有粒子、會顯示圖示的效果。 */
    public static void effect(LivingEntity e, net.minecraft.world.effect.MobEffect effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, false, true));
    }
}
