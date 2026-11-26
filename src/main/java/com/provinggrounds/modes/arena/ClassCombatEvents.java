package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.events.FriendlyFireEvents;
import com.provinggrounds.special.GlowManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClassCombatEvents.java
 * 目的: 九大職業的戰鬥被動（計畫書 9）：
 *   - 重裝兵（HEAVY）：正面受到的傷害 -10%（來源在面對方向 ±90° 內）。
 *   - 狙擊手（SNIPER）：用狙擊槍打中的敵人發光 0.5 秒（同隊可見）。
 *   - 工兵（ENGINEER）：自己造成的爆炸傷害 +20%，受到任何爆炸傷害減半（-50%）。
 *   - 刺客（ASSASSIN）：背刺傷害 ×3（MeleeReach 呼叫 isAssassin）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ClassCombatEvents {

    private ClassCombatEvents() {}

    /** 這位玩家在進行中的團隊模式裡是不是指定職業。 */
    public static boolean isClass(ServerPlayer p, ArenaClass cls) {
        ArenaMode m = ArenaManager.current();
        if (m == null || !m.usesClasses()) return false;
        ArenaMode.ArenaPlayer ap = m.player(p.getUUID());
        return ap != null && ap.cls == cls;
    }

    private static boolean isExplosion(DamageSource src) {
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypes.EXPLOSION) || src.is(DamageTypes.PLAYER_EXPLOSION)) return true;
        String id = src.getMsgId();
        return id != null && id.contains("explos");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0 || event.getEntity().level().isClientSide) return;
        ArenaMode m = ArenaManager.current();
        if (m == null || !m.usesClasses() || m.isEnding()) return;
        DamageSource src = event.getSource();
        float amount = event.getAmount();

        // 1. 工兵攻擊：自己造成的爆炸傷害 +20%
        if (isExplosion(src) && src.getEntity() instanceof ServerPlayer atk && isClass(atk, ArenaClass.ENGINEER)) {
            amount *= 1.20f;
        }

        // 2. 工兵防守：被炸只受一半傷害（-50%）
        if (isExplosion(src) && event.getEntity() instanceof ServerPlayer vic && isClass(vic, ArenaClass.ENGINEER)) {
            amount *= 0.50f;
        }

        // 3. 重裝兵防守：正面受到的傷害 -10%
        if (event.getEntity() instanceof ServerPlayer vic && isClass(vic, ArenaClass.HEAVY)) {
            Vec3 from = sourcePos(src);
            if (from != null && isFromFront(vic, from)) {
                amount *= 0.90f;
            }
        }

        // 4. 狙擊手攻擊：用狙擊槍打中的敵人發光 0.5 秒（同隊可見）
        if (src.is(GunCompat.BULLET_DAMAGE)
                && src.getEntity() instanceof ServerPlayer atk
                && event.getEntity() instanceof ServerPlayer vic
                && atk != vic
                && isClass(atk, ArenaClass.SNIPER)) {
            String gt = GunCompat.gunType(atk.getMainHandItem());
            if ("sniper".equals(gt)) {
                GlowManager.add(vic, GlowManager.Audience.TEAM, atk, false, 0.5, false);
            }
        }

        if (amount != event.getAmount()) event.setAmount(amount);
    }

    private static Vec3 sourcePos(DamageSource src) {
        Entity e = src.getDirectEntity();
        if (e != null) return e.position();
        e = src.getEntity();
        if (e != null) return e.position();
        return src.getSourcePosition();
    }

    /** 傷害來源在玩家面向的正面（水平夾角 ±90° 以內，dot >= 0）。 */
    private static boolean isFromFront(ServerPlayer victim, Vec3 from) {
        Vec3 face = Vec3.directionFromRotation(0, victim.getYHeadRot());
        Vec3 toSrc = new Vec3(from.x - victim.getX(), 0, from.z - victim.getZ());
        if (toSrc.lengthSqr() < 1e-6) return true;
        return face.dot(toSrc.normalize()) >= 0.0;
    }
}
