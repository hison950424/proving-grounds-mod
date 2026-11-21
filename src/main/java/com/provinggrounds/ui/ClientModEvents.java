package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 檔案: ClientModEvents.java
 * 目的: 客戶端啟動時的註冊（只在客戶端載入）：
 *       HUD overlay，以及按鍵：打開主選單／大地圖（預設 M）、趴下／站起來（預設 Z）。
 *       都可以在 選項 → 控制 → 按鍵設定 修改。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.provinggrounds.menu",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_M,
        "key.categories.provinggrounds");

    /** 趴下／站起來（預設 Z）。 */
    public static final KeyMapping PRONE = new KeyMapping(
        "key.provinggrounds.prone",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        "key.categories.provinggrounds");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("br_hud", HUD.OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(PRONE);
    }
}
