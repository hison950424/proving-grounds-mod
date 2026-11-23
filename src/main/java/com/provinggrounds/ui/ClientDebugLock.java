package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientDebugLock.java
 * 目的: F3 的功能只有管理員（權限等級 ≥ 2）能用（只在客戶端）。一般玩家：
 *   - F3 除錯畫面（座標、方向、區塊資訊、效能圖表）不顯示；
 *   - F3＋B 碰撞箱、F3＋G 區塊邊界一律關閉；
 *   - F3＋H 進階顯示（物品 ID、耐久數字）常駐關閉。
 *   原版的 F3 按鍵處理沒有事件可以取消，所以每個 tick 把這些開關改回關閉，並在畫面上擋掉除錯文字。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientDebugLock {

    private ClientDebugLock() {}

    private static boolean restricted(Minecraft mc) {
        LocalPlayer p = mc.player;
        return p != null && !p.hasPermissions(2);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!restricted(mc)) return;
        mc.options.renderDebug = false;
        mc.options.renderDebugCharts = false;
        mc.options.renderFpsChart = false;
        if (mc.options.advancedItemTooltips) {
            mc.options.advancedItemTooltips = false;
            mc.options.save();
        }
        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        // switchRenderChunkborder 是切換：回傳 true 代表現在開著，再切一次關掉
        if (mc.debugRenderer.switchRenderChunkborder()) mc.debugRenderer.switchRenderChunkborder();
    }

    /** 同一個 tick 內按下 F3 也不會閃出除錯文字。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.DEBUG_TEXT.type() && restricted(Minecraft.getInstance())) event.setCanceled(true);
    }
}
