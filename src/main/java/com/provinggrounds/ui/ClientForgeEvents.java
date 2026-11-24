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
 *       按下主選單鍵 → 大廳開主選單、遊戲中開大地圖；離開伺服器時清掉快取。
 *       按下趴下鍵 → 請伺服器切換趴下／站起；替趴下與倒地的玩家套用趴姿，並把趴下／站起的動畫放慢一倍。
 *       BR 中：原版愛心換成數字血條、隱藏飢餓條。中彈不晃畫面。
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
            if (com.provinggrounds.client.ClientArenaState.active()) {
                // 團隊死鬥／佔點／槍王之王：M 開商店（槍王之王沒有商店，顯示戰況與選單按鈕）
                mc.setScreen(new ShopScreen());
            } else if (phase == ClientBRState.PHASE_PLANE || phase == ClientBRState.PHASE_ACTIVE) {
                // 遊戲中：M 開大地圖（大地圖左上有「選單」按鈕）
                mc.setScreen(new BigMapScreen());
            } else {
                // 大廳（含開局前的準備倒數、結算）：M 開主選單
                PGNetwork.sendToServer(new MenuRequestPacket());
                mc.setScreen(new MainMenuScreen());
            }
        }
        while (ClientModEvents.PRONE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            // 本地先擋一次冷卻（避免連按灌封包）；真正的判斷在伺服器
            long now = System.currentTimeMillis();
            if (now - lastProneRequest < 250) continue;
            lastProneRequest = now;
            PGNetwork.sendToServer(new com.provinggrounds.network.ProneTogglePacket());
        }
        updateCrawlPoses(mc);
        slowProneAnimation(mc);
    }

    private static long lastProneRequest = 0;

    // ── 趴下／站起來的動畫放慢一倍 ───────────────────────────────────────────
    // 原版每 tick 把「游泳程度」往目標移動 0.09（約 11 tick 完成），鏡頭高度每 tick 移動剩餘距離的一半。
    // 這裡在每 tick 結束時把這一步的變化量砍半，動畫時間就變成約兩倍。欄位是 private，用反射（SRG 名稱）存取。
    // 找不到欄位（例如之後換 Minecraft 版本）時是 null，動畫維持原版速度，不會讓整個客戶端事件崩潰
    @javax.annotation.Nullable private static final java.lang.reflect.Field SWIM_AMOUNT =
        findField(net.minecraft.world.entity.LivingEntity.class, "f_20931_");
    @javax.annotation.Nullable private static final java.lang.reflect.Field SWIM_AMOUNT_O =
        findField(net.minecraft.world.entity.LivingEntity.class, "f_20932_");
    @javax.annotation.Nullable private static final java.lang.reflect.Field EYE_HEIGHT =
        findField(net.minecraft.client.Camera.class, "f_90562_");
    @javax.annotation.Nullable private static final java.lang.reflect.Field EYE_HEIGHT_O =
        findField(net.minecraft.client.Camera.class, "f_90563_");

    @javax.annotation.Nullable
    private static java.lang.reflect.Field findField(Class<?> type, String srgName) {
        try {
            return net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(type, srgName);
        } catch (RuntimeException e) {
            ProvingGroundsMod.LOGGER.warn("Prone animation: field {} not found; using vanilla speed.", srgName);
            return null;
        }
    }

    private static void slowProneAnimation(Minecraft mc) {
        if (mc.level == null || SWIM_AMOUNT == null || SWIM_AMOUNT_O == null || EYE_HEIGHT == null || EYE_HEIGHT_O == null) return;
        try {
            for (Player p : mc.level.players()) {
                if (!com.provinggrounds.client.ClientProneData.inTransition(p.getUUID())) continue;
                float o = SWIM_AMOUNT_O.getFloat(p), cur = SWIM_AMOUNT.getFloat(p);
                SWIM_AMOUNT.setFloat(p, o + (cur - o) * 0.5f);
            }
            if (mc.player != null && com.provinggrounds.client.ClientProneData.inTransition(mc.player.getUUID())
                    && mc.getCameraEntity() == mc.player) {
                net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
                float o = EYE_HEIGHT_O.getFloat(cam), cur = EYE_HEIGHT.getFloat(cam);
                EYE_HEIGHT.setFloat(cam, o + (cur - o) * 0.5f);
            }
        } catch (IllegalAccessException ignored) {
            // 取不到就用原版速度
        }
    }

    /**
     * 任何時候（大廳、BR、觀戰）：
     *   經驗值條與等級數字 → 不畫。
     *   愛心 → 改畫數字血條（同一個位置，依實際最大血量比例）。
     *   飢餓條 → 不畫（大廳與 BR 都不使用飢餓度，伺服器一直保持吃飽，可以一直跑）。
     *   護甲條 → 從左邊（血條上方）移到右邊原本飢餓條的位置。
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
        } else if (event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
            event.setCanceled(true); // 改在飢餓條的位置畫（見下面 FOOD_LEVEL）
        } else if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            event.setCanceled(true);
            if (mc.gui instanceof ForgeGui gui && gui.shouldDrawSurvivalElements() && mc.player.getArmorValue() > 0) {
                renderArmorRight(event.getGuiGraphics(), mc.player.getArmorValue(),
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), gui.rightHeight);
                gui.rightHeight += 10;
            }
        } else if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            event.setCanceled(true);
            if (mc.gui instanceof ForgeGui gui && gui.shouldDrawSurvivalElements()) {
                HUD.renderHealthBar(event.getGuiGraphics(), mc.font, mc.player, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), gui.leftHeight);
                gui.leftHeight += 10;
            }
        }
    }

    private static final net.minecraft.resources.ResourceLocation ICONS = new net.minecraft.resources.ResourceLocation("textures/gui/icons.png");

    /** 護甲條畫在右邊（原本飢餓條的位置），跟飢餓條一樣由右往左排；圖示用原版的護甲圖示。 */
    private static void renderArmorRight(net.minecraft.client.gui.GuiGraphics g, int armor, int width, int height, int rightHeight) {
        int right = width / 2 + 91, y = height - rightHeight;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int u = i * 2 + 1 < armor ? 34 : (i * 2 + 1 == armor ? 25 : 16); // 滿、半格、空
            g.blit(ICONS, x, y, u, 9, 9, 9);
        }
    }

    /**
     * 中彈不晃畫面：原版受傷時用 hurtTime 讓鏡頭歪一下。每一幀畫面前檢查，
     * 如果最近一次受傷是 TACZ 子彈造成的，就把 hurtTime 歸零（設定 [combat] bullet_hurt_shake = false 時）。
     * 圈外傷害（原版「世界邊界外」傷害類型）一律不晃，避免每秒扣血都像被擊退。
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.hurtTime <= 0) return;
        net.minecraft.world.damagesource.DamageSource src = p.getLastDamageSource();
        if (src == null) return;
        if (src.is(net.minecraft.world.damagesource.DamageTypes.OUTSIDE_BORDER)) { p.hurtTime = 0; return; }
        if (!src.is(com.provinggrounds.events.BulletHitEvents.BULLETS)) return;
        boolean shake;
        try {
            shake = com.provinggrounds.config.PGConfig.SERVER.bulletHurtShake.get();
        } catch (IllegalStateException e) {
            shake = false; // 設定還沒從伺服器同步過來：用預設（不晃）
        }
        if (!shake) p.hurtTime = 0;
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
        Set<UUID> crawling = new HashSet<>(ClientBRState.get().crawling()); // 倒地趴下
        crawling.addAll(com.provinggrounds.client.ClientProneData.all());    // 自己按鍵趴下
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
        com.provinggrounds.client.ClientArenaState.clear();
        KillHud.clear();
        com.provinggrounds.client.ClientProneData.clear();
        ClientStatsBoard.clear();
        SpawnPointRenderer.clear();
        FORCED_CRAWL.clear();
    }

    /** 客戶端指令 /pgwalk：說明開著介面時會不會接管移動鍵、不接管的原因（只顯示給自己）。 */
    @SubscribeEvent
    public static void onClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("pgwalk").executes(ctx -> {
            for (String line : UiWalk.diagnose()) {
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
            }
            return 1;
        }));
    }
}
