package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.br.HotbarRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: HotbarEvents.java
 * 目的: 快捷欄規則（計畫書 B2）的 Forge 事件入口：
 *   - 地面撿取：取消原版撿取，改用 HotbarRules.insert 照規則放（放不下的留在地上）
 *   - F 鍵（不開畫面時）交換主手與副手：會讓槍進副手、或讓不對的物品進專用格時取消
 *   - 每半秒整理一次（/give、關閉畫面時游標上的物品被塞回背包等其他入口的保險）
 *   容器畫面內的點擊由 ContainerMenuMixin 處理。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class HotbarEvents {

    private HotbarEvents() {}

    // 優先度要比 BREvents.onPickup（HIGH，處理復活牌與倒地）低：被它取消的撿取不會再進來，復活牌不會被放進背包
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !HotbarRules.active(p)) return;
        event.setCanceled(true);
        HotbarRules.pickup(p, event.getItem());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !HotbarRules.active(p)) return;
        int selected = p.getInventory().selected;
        if (!HotbarRules.allowedAt(p, HotbarRules.OFFHAND, event.getItemSwappedToOffHand())
                || !HotbarRules.allowedAt(p, selected, event.getItemSwappedToMainHand())) {
            event.setCanceled(true);
            HotbarRules.message(p, "這個物品不能換到副手／這一格");
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        HotbarRules.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!HotbarRules.active(p)) { HotbarRules.forgetRefill(p.getUUID()); return; }
        HotbarRules.autoRefill(p); // 每 tick：第 4、5 格用完自動補（要即時）
        if (p.tickCount % 10 == 0) HotbarRules.normalize(p);
    }
}
