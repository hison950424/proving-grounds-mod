package com.provinggrounds.events;

import com.provinggrounds.config.PGConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 檔案: ArmorEvents.java
 * 目的: 防彈。被 TACZ 子彈打中時，依身上原版防具材質額外減傷（伺服器端）。
 *       設定的數值是「全套」減傷，每件占 1/4，部分穿戴就按件數比例。
 *       用傷害類型標籤 tacz:bullets 判斷，不直接引用 TACZ 的程式，沒裝 TACZ 也不會出錯。
 *       LivingHurtEvent 在原版護甲計算之前觸發，所以子彈的穿甲與非穿甲部分都會被減到。
 */
public class ArmorEvents {

    private static final TagKey<DamageType> BULLETS =
        TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullets"));

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        PGConfig cfg = PGConfig.SERVER;
        if (!cfg.bulletproofEnabled.get() || !event.getSource().is(BULLETS)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        double reduction = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor) {
                reduction += fullSetReduction(armor.getMaterial(), cfg) / 4.0;
            }
        }
        if (reduction > 0) event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
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
