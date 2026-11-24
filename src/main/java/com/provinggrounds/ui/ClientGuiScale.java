package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientGuiScale.java
 * 目的: 固定原版「介面大小」（GUI Scale）= 3（只在客戶端）。本模組所有畫面與 HUD 都以介面大小 3 排版，
 *   每秒檢查一次：玩家（或其他東西）改成別的值，就改回 3、存進 options.txt、重新排版畫面（resizeDisplay）。
 *   視窗太小、放不下 3 的時候，原版會拒絕 3：這時改用「能放下的最大值」，避免每秒一直重設。
 *   客戶端設定 [gui] force_gui_scale_v1 = false 可以關掉。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGuiScale {

    public static final int TARGET = 3;
    private static int tick;

    private ClientGuiScale() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.getWindow() == null) return;
        try {
            if (!PGClientConfig.CLIENT.forceGuiScale.get()) return;
        } catch (IllegalStateException e) {
            return; // 設定還沒載入
        }
        // calculateScale(0, …) = 「自動」時會用的值，也就是這個視窗能放下的最大介面大小
        int max = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
        int want = Math.max(1, Math.min(TARGET, max));
        if (mc.options.guiScale().get() == want) return;
        mc.options.guiScale().set(want);
        mc.options.save();
        mc.resizeDisplay();
    }
}
