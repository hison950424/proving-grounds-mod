package com.provinggrounds.events;

import com.provinggrounds.config.PGConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 檔案: ArmorEvents.java
 * 目的: 防彈。被 TACZ 子彈打中時，依身上原版防具材質額外減傷（伺服器端）。
 *
 * TACZ 的子彈傷害流程（已確認 1.1.8）：
 *   1. 算出傷害（距離衰減 × 爆頭倍率），觸發 EntityHurtByGunEvent.Pre（帶爆頭判定）。
 *   2. 依槍的穿甲率拆成兩次扣血：
 *        一般部分 = 傷害 × (1 − 穿甲率)，傷害類型 tacz:bullet / tacz:bullet_void
 *        穿甲部分 = 傷害 × 穿甲率，      傷害類型 tacz:bullet_ignore_armor / tacz:bullet_void_ignore_armor
 *
 * 我們的做法（使用者回饋：只算被打中部位的那一件，不把全身相加）：
 *   - 只處理「一般部分」，穿甲部分照樣全額打進去 → 穿甲率越高的槍，防彈越沒用。
 *   - 被打中的部位（Hitbox.armorSlotHit）：頭 → 頭盔、身體與手 → 胸甲、腳上半 → 護腿、腳下 1/3 → 靴子。
 *     判斷不出來時：爆頭 → 頭盔，其他 → 胸甲。
 *   - 減傷只看那一件：先減該材質的防彈比例（bulletproof_*），再用那一件自己的護甲值與韌性算原版護甲減傷。
 *     原版「全身護甲加總」的減傷與四件一起扣耐久，對子彈不再套用（mixin/ArmorAbsorbMixin）。
 *   - 耐久只扣那一件（跟原版一樣：每 4 點傷害扣 1，至少 1）。
 *   - 爆頭倍率已經包含在傷害裡，防彈是在爆頭倍率之後才減。
 */
public class ArmorEvents {

    private static final TagKey<DamageType> BULLETS =
        com.provinggrounds.compat.GunCompat.BULLET_DAMAGE;

    /** 最近一次命中（同一 tick、同一顆子彈才算數）。key = 被打實體的 id。 */
    private record Hit(int bulletId, boolean headshot, long gameTime, @javax.annotation.Nullable EquipmentSlot slot) {}
    private static final Map<Integer, Hit> HITS = new HashMap<>();

    /** TACZ 命中事件（扣血之前）呼叫，記下是否爆頭。 */
    public static void recordHit(Entity target, Entity bullet, boolean headshot) {
        long now = target.level().getGameTime();
        if (HITS.size() > 512) HITS.values().removeIf(h -> h.gameTime() != now); // 清掉舊紀錄
        net.minecraft.world.phys.Vec3 v = bullet.getDeltaMovement();
        EquipmentSlot slot = com.provinggrounds.core.Hitbox.armorSlotHit(target, bullet.position().subtract(v), bullet.position().add(v));
        HITS.put(target.getId(), new Hit(bullet.getId(), headshot, now, slot));
    }

    /** 子彈的一般部分：原版的全身護甲減傷不套用，改由這裡只算被打中的那一件（ArmorAbsorbMixin 呼叫）。 */
    public static boolean handlesArmor(DamageSource source) {
        try {
            return PGConfig.SERVER.bulletproofEnabled.get() && source.is(BULLETS) && !source.is(DamageTypeTags.BYPASSES_ARMOR);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        PGConfig cfg = PGConfig.SERVER;
        DamageSource source = event.getSource();
        if (!handlesArmor(source)) return; // 穿甲部分、非子彈：照原版
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        EquipmentSlot slot = lookupSlot(target, source);
        if (slot == null) {
            Boolean headshot = lookupHeadshot(target, source);
            slot = Boolean.TRUE.equals(headshot) ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
        }
        ItemStack piece = target.getItemBySlot(slot);
        if (!(piece.getItem() instanceof ArmorItem armor)) return; // 那個部位沒穿：不減傷
        float amount = event.getAmount();
        float reduced = (float) (amount * (1.0 - fullSetReduction(armor.getMaterial(), cfg)));
        reduced = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(reduced, armor.getDefense(), armor.getToughness());
        event.setAmount(Math.max(0, reduced));
        // 耐久只扣被打中的那一件
        if (!piece.isEmpty() && amount > 0) {
            int dmg = Math.max(1, (int) (amount / 4.0f));
            final EquipmentSlot s = slot;
            piece.hurtAndBreak(dmg, target, e -> e.broadcastBreakEvent(s));
        }
    }

    /** 這次傷害打中的部位；沒有紀錄或判斷不出來回傳 null。 */
    @javax.annotation.Nullable
    private static EquipmentSlot lookupSlot(LivingEntity target, DamageSource source) {
        Hit hit = HITS.get(target.getId());
        if (hit == null || hit.gameTime() != target.level().getGameTime()) return null;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getId() != hit.bulletId()) return null;
        return hit.slot();
    }

    /** 給擊殺提示用：這次傷害是不是爆頭（沒有紀錄回傳 null）。 */
    public static Boolean wasHeadshot(LivingEntity target, DamageSource source) { return lookupHeadshot(target, source); }

    /** 這次傷害是不是爆頭；沒有對應的命中紀錄就回傳 null。 */
    private static Boolean lookupHeadshot(LivingEntity target, DamageSource source) {
        Hit hit = HITS.get(target.getId());
        if (hit == null || hit.gameTime() != target.level().getGameTime()) return null;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getId() != hit.bulletId()) return null;
        return hit.headshot();
    }

    /** 該材質「穿全套」的減傷比例；其他模組的防具不在表上就是 0。 */
    private static double fullSetReduction(ArmorMaterial m, PGConfig cfg) {
        if (m == ArmorMaterials.LEATHER)   return cfg.bulletproofLeather.get();
        if (m == ArmorMaterials.CHAIN)     return cfg.bulletproofChainmail.get();
        if (m == ArmorMaterials.IRON)      return cfg.bulletproofIron.get();
        if (m == ArmorMaterials.GOLD)      return cfg.bulletproofGold.get();
        if (m == ArmorMaterials.DIAMOND)   return cfg.bulletproofDiamond.get();
        if (m == ArmorMaterials.NETHERITE) return cfg.bulletproofNetherite.get();
        if (m == ArmorMaterials.TURTLE)    return cfg.bulletproofTurtle.get();
        return 0;
    }
}
