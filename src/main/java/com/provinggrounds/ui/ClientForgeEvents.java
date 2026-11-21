package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientMenuData;
import com.provinggrounds.network.MenuRequestPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: ClientForgeEvents.java
 * 目的: 客戶端遊戲中事件（只在客戶端載入）：
 *       按下主選單鍵 → 向伺服器要資料並打開主選單；離開伺服器時清掉快取。
 *       BR 中：原版愛心換成數字血條、隱藏飢餓條。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            byte phase = ClientBRState.get().phase();
            if (phase == ClientBRState.PHASE_PLANE || phase == ClientBRState.PHASE_ACTIVE) {
                // 遊戲中：M 開大地圖（大地圖左上有「選單」按鈕）
                mc.setScreen(new BigMapScreen());
            } else {
                // 大廳（含開局前的準備倒數、結算）：M 開主選單
                PGNetwork.sendToServer(new MenuRequestPacket());
                mc.setScreen(new MainMenuScreen());
            }
        }
        updateCrawlPoses(mc);
    }

    /**
     * BR 進行中（自己不是觀察者）時：
     *   愛心 → 改畫數字血條（同一個位置），並把左側高度往上推，讓護甲條畫在血條上方而不是重疊。
     *   飢餓條 → 不畫（BR 不使用飢餓度）。
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || !ClientBRState.active()) return;
        if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            event.setCanceled(true);
        } else if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            event.setCanceled(true);
            if (mc.gui instanceof ForgeGui gui && gui.shouldDrawSurvivalElements()) {
                HUD.renderHealthBar(event.getGuiGraphics(), mc.font, mc.player, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), gui.leftHeight);
                gui.leftHeight += 10;
            }
        }
    }

    /** 我們強制趴下的玩家（之後要還原）。 */
    private static final Set<UUID> FORCED_CRAWL = new HashSet<>();

    /**
     * 倒地趴下：姿勢每 tick 由各端自己計算，伺服器設的強制姿勢不會同步過來，
     * 所以客戶端也要對自己與看得到的倒地玩家設定 setForcedPose(SWIMMING)。
     */
    private static void updateCrawlPoses(Minecraft mc) {
        if (mc.level == null) {
            FORCED_CRAWL.clear();
            return;
        }
        Set<UUID> crawling = new HashSet<>(ClientBRState.get().crawling());
        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            if (crawling.contains(id)) {
                if (p.getForcedPose() != Pose.SWIMMING) p.setForcedPose(Pose.SWIMMING);
                FORCED_CRAWL.add(id);
            } else if (FORCED_CRAWL.remove(id)) {
                p.setForcedPose(null);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMenuData.clear();
        com.provinggrounds.client.ClientLobbyData.clear();
        com.provinggrounds.client.ClientMapData.clear();
        ClientBRState.clear();
        FORCED_CRAWL.clear();
    }
}
