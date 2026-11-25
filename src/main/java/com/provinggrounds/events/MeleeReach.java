package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: MeleeReach.java
 * 目的: 刀子的攻擊距離（使用者回饋：比原本少 0.25 格）。lrtactical 的每把刀都寫死最遠 2 格（物品資料的 max_range），
 *   這裡在伺服器擋：拿著近戰武器、直接打（不是丟出去的東西）時，從攻擊者眼睛到對方身體最近一點的距離超過
 *   melee_reach_v1（預設 1.75 格）就取消這次傷害。只會縮短，不會讓本來打不到的變打得到。
 *   傷害平衡（使用者回饋）：lrtactical 每把刀都是左鍵 40、右鍵 400（倍率 10），這裡改成固定的
 *   melee_light_damage_v1（預設 20）與 melee_heavy_damage_v1（預設 55）。原始傷害 ≥ 100 的算右鍵重擊。之後照原版護甲計算。
 *   背刺（使用者回饋）：從背後打（對方面向的反方向左右 60 度內）傷害 × melee_backstab_multiplier_v1（預設 2）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class MeleeReach {

    private MeleeReach() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getSource().getDirectEntity() != attacker) return;
        if (!attacker.getMainHandItem().is(GunCompat.MELEE)) return;
        double reach;
        try { reach = PGConfig.SERVER.meleeReach.get(); } catch (IllegalStateException e) { return; }
        LivingEntity target = event.getEntity();
        if (distanceToBox(attacker.getEyePosition(), target.getBoundingBox()) > reach) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getSource().getDirectEntity() != attacker) return;
        if (!attacker.getMainHandItem().is(GunCompat.MELEE)) return;
        try {
            boolean heavy = event.getAmount() >= 100f; // 右鍵重擊是左鍵的 10 倍
            double dmg = heavy ? PGConfig.SERVER.meleeHeavyDamage.get() : PGConfig.SERVER.meleeLightDamage.get();
            if (behind(attacker, event.getEntity())) dmg *= PGConfig.SERVER.meleeBackstabMultiplier.get(); // 背刺（使用者回饋）
            event.setAmount((float) dmg);
        } catch (IllegalStateException ignored) {
            // 設定還沒載入：照原本
        }
    }

    /** 攻擊者在對方背後：從對方指向攻擊者的水平方向，和對方面向的反方向相差 60 度以內。 */
    private static boolean behind(ServerPlayer attacker, LivingEntity target) {
        Vec3 face = Vec3.directionFromRotation(0, target.getYHeadRot());
        Vec3 toAttacker = new Vec3(attacker.getX() - target.getX(), 0, attacker.getZ() - target.getZ());
        if (toAttacker.lengthSqr() < 1e-6) return false;
        return face.dot(toAttacker.normalize()) <= -0.5; // cos(120°)
    }

    /** 點到方框最近一點的距離（點在方框裡 = 0）。 */
    private static double distanceToBox(Vec3 p, AABB b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
