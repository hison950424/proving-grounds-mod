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
 *       HUD overlay，以及按鍵：打開主選單／大地圖（預設 M）、趴下／站起來（預設 C）。
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

    /** 趴下／站起來（預設 C）。 */
    public static final KeyMapping PRONE = new KeyMapping(
        "key.provinggrounds.prone",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.provinggrounds");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("br_hud", HUD.OVERLAY);
        event.registerAboveAll("arena_hud", ArenaHud.OVERLAY); // 團隊死鬥／佔點／槍王之王
        event.registerAboveAll("item_wheel", ClientWheel.OVERLAY);
        event.registerAboveAll("compass", Compass.OVERLAY);
        event.registerAboveAll("damage_indicator", DamageIndicator.OVERLAY);
        event.registerAboveAll("interact_hint", ClientInteract.HINT_OVERLAY); // 準星下方的 F 互動提示
        event.registerAboveAll("kill_hud", KillHud.OVERLAY); // 擊殺圖示與擊殺紀錄
        event.registerAboveAll("arena_world_hint", ArenaWorldRenderer.OVERLAY); // 據點箭頭、敵方重生區提示
        event.registerAboveAll("scoreboard", ScoreboardPanel.OVERLAY); // 戰績面板（Caps Lock）

        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "special_hotbar", com.provinggrounds.special.client.SpecialHotbar.OVERLAY); // 特殊物品快捷欄
    }

    @SubscribeEvent
    public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.provinggrounds.registry.PGBlocks.STATS_BOARD_BE.get(), StatsBoardRenderer::new);
        // 特殊物品的實體（計畫書 4）
        event.registerEntityRenderer(com.provinggrounds.special.SpecialRegistry.MINE.get(), com.provinggrounds.special.client.MineRenderer::new);
        event.registerEntityRenderer(com.provinggrounds.special.SpecialRegistry.DECOY.get(), com.provinggrounds.special.client.DecoyRenderer::new);
        event.registerEntityRenderer(com.provinggrounds.special.SpecialRegistry.PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
            com.provinggrounds.registry.PGBlocks.TOMBSTONE_MENU.get(), TombstoneScreen::new)); // 墓碑畫面
        // 降落傘打開時手上的傘包改用看不見的模型（models/item/parachute.json 的 overrides）
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
            com.provinggrounds.registry.PGBlocks.PARACHUTE.get(),
            new net.minecraft.resources.ResourceLocation(com.provinggrounds.ProvingGroundsMod.MOD_ID, "open"),
            (stack, level, entity, seed) -> com.provinggrounds.item.ParachuteItem.isOpen(stack) ? 1f : 0f));
    }


    /** 額外模型：第一人稱的降落傘傘衣（沒有傘繩，不擋視野）。 */
    @SubscribeEvent
    public static void onRegisterModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(ParachuteRenderer.FP_MODEL);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(PRONE);
        event.register(com.provinggrounds.special.client.ClientSpecial.USE_KEY); // 使用特殊物品（預設 Q）
        event.register(ClientMovement.QUIET_WALK); // 靜走（預設 Shift）
        event.register(ClientInteract.INTERACT); // 互動（預設 F；計畫書 5）
        event.register(ScoreboardPanel.KEY); // 戰績面板（預設 Caps Lock）
    }
}
