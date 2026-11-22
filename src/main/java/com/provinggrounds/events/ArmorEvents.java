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
 * 我們的做法：
 *   - 只減「一般部分」，穿甲部分照樣全額打進去 → 穿甲率越高的槍，防彈越沒用。
 *   - 爆頭（head_body_split 開啟時）：只看頭盔，頭盔材質的全套數值整個套用。
 *     身體中彈：看胸甲、護腿、靴子三件，每件占 1/3。
 *   - 拿不到爆頭資訊時（例如 /damage 指令、TACZ 版本不同）：四件各占 1/4。
 *   - 爆頭倍率已經包含在傷害裡，防彈是在爆頭倍率之後才減。
 * LivingHurtEvent 在原版護甲計算之前觸發；原版護甲之後仍會再擋一般部分（TACZ 原本的行為）。
 */
public class ArmorEvents {

    private static final TagKey<DamageType> BULLETS =
        com.provinggrounds.compat.GunCompat.BULLET_DAMAGE;

    private static final EquipmentSlot[] BODY_SLOTS = { EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final EquipmentSlot[] ALL_SLOTS  = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /** 最近一次命中（同一 tick、同一顆子彈才算數）。key = 被打實體的 id。 */
    private record Hit(int bulletId, boolean headshot, long gameTime) {}
    private static final Map<Integer, Hit> HITS = new HashMap<>();

    /** TACZ 命中事件（扣血之前）呼叫，記下是否爆頭。 */
    public static void recordHit(Entity target, Entity bullet, boolean headshot) {
        long now = target.level().getGameTime();
        if (HITS.size() > 512) HITS.values().removeIf(h -> h.gameTime() != now); // 清掉舊紀錄
        HITS.put(target.getId(), new Hit(bullet.getId(), headshot, now));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        PGConfig cfg = PGConfig.SERVER;
        DamageSource source = event.getSource();
        if (!cfg.bulletproofEnabled.get() || !source.is(BULLETS)) return;
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return; // 穿甲部分：防彈擋不住
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        double reduction;
        Boolean headshot = cfg.bulletproofHeadBodySplit.get() ? lookupHeadshot(target, source) : null;
        if (headshot == null) {
            reduction = average(target, ALL_SLOTS, cfg);
        } else if (headshot) {
            reduction = slotReduction(target, EquipmentSlot.HEAD, cfg);
        } else {
            reduction = average(target, BODY_SLOTS, cfg);
        }
        if (reduction > 0) event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
    }

    /** 這次傷害是不是爆頭；沒有對應的命中紀錄就回傳 null。 */
    private static Boolean lookupHeadshot(LivingEntity target, DamageSource source) {
        Hit hit = HITS.get(target.getId());
        if (hit == null || hit.gameTime() != target.level().getGameTime()) return null;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getId() != hit.bulletId()) return null;
        return hit.headshot();
    }

    private static double average(LivingEntity target, EquipmentSlot[] slots, PGConfig cfg) {
        double sum = 0;
        for (EquipmentSlot slot : slots) sum += slotReduction(target, slot, cfg);
        return sum / slots.length;
    }

    private static double slotReduction(LivingEntity target, EquipmentSlot slot, PGConfig cfg) {
        ItemStack stack = target.getItemBySlot(slot);
        return stack.getItem() instanceof ArmorItem armor ? fullSetReduction(armor.getMaterial(), cfg) : 0;
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
