package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: MeleeEvents.java
 * 目的: 移除 lrtactical 近戰武器自帶的移動速度加成。
 *   lrtactical 的近戰武器資料（例如 data/lrtactical/index/melee/dagger.json、CS2 刀具包）裡寫了
 *   "generic.movement_speed": +20%，拿在手上會跑得更快。這裡在原版讀取物品屬性時，
 *   把近戰武器（物品標籤 provinggrounds:melee）的移動速度屬性拿掉，攻擊力等其他屬性不動。
 *   拿近戰武器時的移動速度改由 BRMovement（設定 melee_speed_multiplier，預設原版 100%）決定。
 *   伺服器與客戶端都會執行（屬性兩邊各自計算）。不用改槍械包的資料檔，所有刀具包一起生效。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class MeleeEvents {

    private MeleeEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        if (event.getItemStack().is(GunCompat.MELEE)) event.removeAttribute(Attributes.MOVEMENT_SPEED);
    }
}
