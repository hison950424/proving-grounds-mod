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
            PGNetwork.sendToServer(new MenuRequestPacket());
            mc.setScreen(new MainMenuScreen());
        }
        updateCrawlPoses(mc);
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
        ClientBRState.clear();
        FORCED_CRAWL.clear();
    }
}
