package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.br.Backpack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: BackpackEvents.java
 * 目的: 背包欄（計畫書 B3）的存取：登入時從玩家資料讀回（最高優先度，要在 BR 的登入處理之前），
 *       登出時從記憶體移除，重生時把資料複製到新的玩家物件。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class BackpackEvents {

    private BackpackEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) { Backpack.load(p); com.provinggrounds.special.SpecialSlot.load(p); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Backpack.unload(event.getEntity());
        com.provinggrounds.special.SpecialSlot.unload(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Backpack.copyData(event.getOriginal(), event.getEntity());
        com.provinggrounds.special.SpecialSlot.copyData(event.getOriginal(), event.getEntity());
    }
}
