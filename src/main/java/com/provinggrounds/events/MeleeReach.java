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

    /** 點到方框最近一點的距離（點在方框裡 = 0）。 */
    private static double distanceToBox(Vec3 p, AABB b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
