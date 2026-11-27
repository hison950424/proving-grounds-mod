package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.modes.br.HotbarRules;
import com.provinggrounds.special.GlowManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: ClassCombatEvents.java
 * 目的: 九大職業的戰鬥與補品被動：
 *   - 重裝兵（HEAVY）：正面受到的傷害 -15%（來源在面對方向 ±90° 內）。
 *   - 偵察兵（RECON）：擊中敵人使其發光 0.5 秒（同隊可見）。
 *   - 工兵（ENGINEER）：自己造成的爆炸傷害 +20%，受到任何爆炸傷害減半（-50%）。
 *   - 刺客（ASSASSIN）：背刺傷害 ×3（MeleeReach 呼叫 isAssassin）。
 *   - 醫護兵（MEDIC）：使用補品時間 -25%，且使用補品後 6 格內隊友同步回復醫護兵回血量的 50%。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ClassCombatEvents {

    private static final Map<UUID, Float> MEDIC_PRE_HP = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> MEDIC_SHARE_UNTIL = new ConcurrentHashMap<>();

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

        // 3. 重裝兵防守：正面受到的傷害 -15%
        if (event.getEntity() instanceof ServerPlayer vic && isClass(vic, ArenaClass.HEAVY)) {
            Vec3 from = sourcePos(src);
            if (from != null && isFromFront(vic, from)) {
                amount *= 0.85f;
            }
        }

        // 4. 偵察兵攻擊：擊中敵人使其發光 0.5 秒（同隊可見）
        if (src.getEntity() instanceof ServerPlayer atk
                && event.getEntity() instanceof ServerPlayer vic
                && atk != vic
                && isClass(atk, ArenaClass.RECON)) {
            ArenaMode.ArenaPlayer ap = m.player(atk.getUUID());
            ArenaMode.ArenaPlayer vp = m.player(vic.getUUID());
            if (ap != null && vp != null && !m.sameSide(ap, vp)) {
                GlowManager.add(vic, GlowManager.Audience.TEAM, atk, false, 0.5, false);
            }
        }

        if (amount != event.getAmount()) event.setAmount(amount);
    }

    /** 醫護兵：使用補品時間 -25%（並記錄使用前血量，供結束時共享 50% 回復量給 6 格內隊友）。 */
    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isClass(p, ArenaClass.MEDIC)) return;
        if (!isHealingItem(event.getItem())) return;
        int dur = event.getDuration();
        if (dur > 1) {
            event.setDuration(Math.max(1, (int) Math.round(dur * 0.75)));
        }
        MEDIC_PRE_HP.put(p.getUUID(), p.getHealth());
    }

    /** 醫護兵：使用補品完成時，將本次實際回血量的 50% 同步回復給 6 格內存活隊友。 */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isClass(p, ArenaClass.MEDIC)) return;
        if (!isHealingItem(event.getItem())) return;
        Float pre = MEDIC_PRE_HP.remove(p.getUUID());
        float gained = pre != null ? (p.getHealth() - pre) : 0f;
        if (gained > 0.5f) {
            shareMedicHeal(p, gained * 0.5f);
        } else {
            // 部分補品在 Finish 後同 tick 才呼叫 heal()，開 3 tick 視窗捕捉該次治療事件
            MEDIC_SHARE_UNTIL.put(p.getUUID(), p.tickCount + 3);
        }
    }

    @SubscribeEvent
    public static void onMedicHeal(LivingHealEvent event) {
        if (event.getAmount() <= 0.5f || !(event.getEntity() instanceof ServerPlayer p) || !isClass(p, ArenaClass.MEDIC)) return;
        Integer until = MEDIC_SHARE_UNTIL.get(p.getUUID());
        if (until == null) return;
        if (p.tickCount > until) {
            MEDIC_SHARE_UNTIL.remove(p.getUUID());
            return;
        }
        MEDIC_SHARE_UNTIL.remove(p.getUUID());
        shareMedicHeal(p, event.getAmount() * 0.5f);
    }

    private static boolean isHealingItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(HotbarRules.HEALING)
            || (stack.getItem() instanceof net.minecraft.world.item.PotionItem && stack.hasTag() && stack.getTag().getBoolean("PgArenaKit"));
    }

    private static void shareMedicHeal(ServerPlayer medic, float amount) {
        ArenaMode m = ArenaManager.current();
        if (m == null || !m.usesClasses() || m.isEnding() || amount <= 0.1f) return;
        ArenaMode.ArenaPlayer ap = m.player(medic.getUUID());
        if (ap == null) return;
        ServerLevel sl = medic.serverLevel();
        for (ServerPlayer mate : m.online()) {
            ArenaMode.ArenaPlayer ma = m.player(mate.getUUID());
            if (mate == medic || ma == null || !m.sameSide(ap, ma) || !mate.isAlive() || mate.isSpectator()) continue;
            if (m.downedEnabled && m.downed.isDowned(mate.getUUID())) continue;
            if (mate.distanceToSqr(medic) <= 36.0 && mate.getHealth() < mate.getMaxHealth()) {
                mate.heal(amount);
                sl.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.8, mate.getZ(), 3, 0.25, 0.15, 0.25, 0);
            }
        }
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
