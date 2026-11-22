package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 檔案: Compass.java
 * 目的: 大逃殺指北針（第三份計畫書 B，只在客戶端）：畫面上方中央的水平方位條，隨玩家面向的方向左右捲動。
 *   - 顯示面向左右各 90 度（共 180 度）；每 15 度一個小刻度，每 30 度標示角度數字，
 *     北（360）、東（90）、南（180）、西（270）用文字取代數字，北用強調色。
 *   - 沒有底色，貼齊螢幕最上方；方位條下方有固定的小三角形，三角形下面顯示目前面向的角度（1～360，正北顯示 360，不會出現 0）。
 *   - 方位角 = (yaw + 180) mod 360（原版 yaw 0 = 朝南 +Z，180 = 朝北 −Z），跟小地圖一致（小地圖上方是 −Z）。
 *     視角用 partialTick 內插，轉動時平滑捲動。
 *   - 只在 BR 進行中顯示（含飛機上、跳傘、落地後）；大廳、觀察者、開著大地圖時不畫。
 *   - 客戶端設定：compass_enabled、compass_scale、compass_y_offset。
 *   - 所有文字預先建立，每幀只畫固定數量的線與文字；面向角度是客戶端本來就知道的資料，不需要封包。
 */
public final class Compass {

    private static final int TICK = 0xE0FFFFFF, TICK_DIM = 0xB0C0C8D0, SHADOW = 0x80000000;
    private static final int ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFFC0C8D0;
    /** 方位條寬度（GUI 像素，縮放前）＝ 180 度；高度（刻度＋文字）。原本 200×24，寬減 15%、高減 25%。 */
    private static final int BAR_W = 170, BAR_H = 18;
    private static final float PX_PER_DEG = BAR_W / 180f;

    /** 每 30 度的標籤（0、30、…、330）；0／90／180／270 換成北東南西。 */
    private static final String[] LABELS = new String[12];
    /** 中央角度數字 1～360（索引 = 角度）。 */
    private static final String[] NUMBERS = new String[361];

    static {
        for (int i = 0; i < 12; i++) LABELS[i] = String.valueOf(i * 30);
        LABELS[0] = "北";
        LABELS[3] = "東";
        LABELS[6] = "南";
        LABELS[9] = "西";
        for (int i = 1; i <= 360; i++) NUMBERS[i] = String.valueOf(i);
    }

    private Compass() {}

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, partialTick, width);

    private static void render(GuiGraphics g, float partial, int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!ClientBRState.active() || mc.screen instanceof BigMapScreen) return;
        byte phase = ClientBRState.get().phase();
        if (phase != ClientBRState.PHASE_PLANE && phase != ClientBRState.PHASE_ACTIVE) return;
        double scale = 1;
        int yOffset = 0;
        try {
            if (!PGClientConfig.CLIENT.compassEnabled.get()) return;
            scale = PGClientConfig.CLIENT.compassScale.get();
            yOffset = PGClientConfig.CLIENT.compassYOffset.get();
        } catch (IllegalStateException ignored) {
            // 設定還沒載入：用預設值
        }
        // 畫面太窄（或 GUI 縮放很大）時自動縮小，避免蓋到左上的小地圖（右邊緣約 x = 92）與右上的存活人數
        double maxScale = (width - 2 * 96.0) / (BAR_W + 8);
        if (maxScale < scale) scale = Math.max(0.4, maxScale);
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float yaw = Mth.lerp(partial, cam.yRotO, cam.getYRot());
        double bearing = ((yaw + 180.0) % 360 + 360) % 360; // 0～360，北 = 0

        Font font = mc.font;
        g.pose().pushPose();
        g.pose().translate(width / 2.0, yOffset, 0); // 貼齊螢幕最上方
        g.pose().scale((float) scale, (float) scale, 1);

        // 方位條（沒有底色；刻度與文字都有陰影，在亮的天空也看得清楚）
        int barY = 1;
        int first = (int) Math.ceil((bearing - 90) / 15.0) * 15;
        for (int d = first; d <= bearing + 90; d += 15) {
            int xi = Math.round((float) ((d - bearing) * PX_PER_DEG));
            int deg = ((d % 360) + 360) % 360;
            boolean major = deg % 30 == 0;
            int tickH = major ? 4 : 2;
            g.fill(xi + 1, barY + 1, xi + 2, barY + tickH + 1, SHADOW);
            g.fill(xi, barY, xi + 1, barY + tickH, major ? TICK : TICK_DIM);
            if (major) {
                String label = LABELS[deg / 30];
                int color = deg == 0 ? ACCENT : (deg % 90 == 0 ? TEXT : TEXT_DIM);
                g.drawString(font, label, xi - font.width(label) / 2 + 1, barY + 6, color, true);
            }
        }

        // 下方：向上的小三角形（指著方位條的中央）與目前面向的角度（1～360）
        int triY = barY + BAR_H - 1;
        for (int i = 0; i < 4; i++) g.fill(-i, triY + i, i + 1, triY + i + 1, ACCENT);
        int shown = (int) Math.round(bearing) % 360;
        String num = NUMBERS[shown == 0 ? 360 : shown];
        g.drawString(font, num, -font.width(num) / 2 + 1, triY + 5, ACCENT, true);
        g.pose().popPose();
    }
}
