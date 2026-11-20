package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientModEvents.java
 * 目的: 客戶端啟動時的註冊（只在客戶端載入）：把 HUD 註冊成遊戲畫面上的 overlay。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("br_hud", HUD.OVERLAY);
    }
}
