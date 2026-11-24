package com.provinggrounds.ui;

import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.config.PGClientConfig;
import com.provinggrounds.network.KillNoticePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 檔案: KillHud.java
 * 目的: 擊殺提示（只在客戶端；計畫書 5 的 E3，符合 A1 不擋視野）。
 *   (a) 擊殺圖示：自己擊殺時，準星下方（離準星 22 格以上）彈出很小的「╳ 擊殺」字樣 1.2 秒；
 *       爆頭金色、連殺 3 以上紅色並顯示「N 連殺」。
 *   (b) 擊殺紀錄：右上角「擊殺者 [武器圖示] 被擊殺者」，最多 5 行，約 5 秒淡出。
 *   擊殺音效：原版音效疊層（高音鈴＋經驗球＋短金屬聲），連殺數越高音越高；爆頭多一個更亮的鐘聲。
 *   全部可在客戶端設定 [hud] 關閉、調整大小與音量。
 */
public final class KillHud {

    private record Entry(KillNoticePacket data, long time) {}

    private static final long FEED_MS = 5000, ICON_MS = 1200;
    private static final Deque<Entry> FEED = new ArrayDeque<>();
    private static KillNoticePacket icon;
    private static long iconTime;

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, w, h) -> render(g, w, h);

    private KillHud() {}

    public static void receive(KillNoticePacket p) {
        long now = System.currentTimeMillis();
        if (p.feed()) {
            FEED.addFirst(new Entry(p, now));
            while (FEED.size() > 5) FEED.removeLast();
        }
        if (p.mine()) {
            icon = p;
            iconTime = now;
            playSound(p);
        }
    }

    public static void clear() { FEED.clear(); icon = null; }

    private static void playSound(KillNoticePacket p) {
        var cfg = PGClientConfig.CLIENT;
        if (!cfg.killSoundEnabled.get()) return;
        float vol = cfg.killSoundVolume.get().floatValue();
        if (vol <= 0) return;
        float pitch = Math.min(2f, 1.0f + 0.08f * Math.max(0, p.streak() - 1)); // 連殺越多音越高
        play(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, vol);
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, Math.min(2f, pitch + 0.3f), vol * 0.7f);
        play(SoundEvents.ANVIL_PLACE, 2f, vol * 0.12f);
        if (p.headshot()) play(SoundEvents.NOTE_BLOCK_CHIME.value(), Math.min(2f, pitch + 0.5f), vol);
    }

    private static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        long now = System.currentTimeMillis();
        float scale = PGClientConfig.CLIENT.hudScale.get().floatValue();
        // 開關、位置、大小：HudLayout（開關沿用 [hud] kill_icon / kill_feed）
        if (icon != null && now - iconTime < ICON_MS && HudLayout.push(g, HudLayout.El.KILL_ICON, w, h)) {
            renderIcon(g, mc.font, w, h, now, scale);
            HudLayout.pop(g);
        }
        if (HudLayout.push(g, HudLayout.El.KILL_FEED, w, h)) { renderFeed(g, mc.font, w, now, scale); HudLayout.pop(g); }
    }

    private static void renderIcon(GuiGraphics g, Font font, int w, int h, long now, float scale) {
        float t = (now - iconTime) / (float) ICON_MS;
        int alpha = (int) (255 * (t < 0.7f ? 1f : (1f - t) / 0.3f));
        if (alpha < 8) return;
        int color = icon.headshot() ? 0xFFD040 : icon.streak() >= 3 ? 0xFF5050 : 0xFFFFFF;
        String text = (icon.headshot() ? "爆頭" : "擊殺") + (icon.streak() >= 2 ? "  " + icon.streak() + " 連殺" : "");
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f + 24, 0);
        float pop = t < 0.1f ? 1.3f - 3f * t : 1f; // 彈出一下
        g.pose().scale(scale * 0.75f * pop, scale * 0.75f * pop, 1);
        int c = (alpha << 24) | color;
        for (int i = -3; i <= 3; i++) { // 小小的 ╳
            g.fill(i - 1, i - 1 - 10, i + 1, i + 1 - 10, c);
            g.fill(i - 1, -i - 1 - 10, i + 1, -i + 1 - 10, c);
        }
        g.drawString(font, text, -font.width(text) / 2, 0, c, true);
        g.pose().popPose();
    }

    private static void renderFeed(GuiGraphics g, Font font, int w, long now, float scale) {
        FEED.removeIf(e -> now - e.time() > FEED_MS);
        if (FEED.isEmpty()) return;
        int y = ClientArenaState.active() ? 4 : 40;
        g.pose().pushPose();
        g.pose().translate(w - 4, y, 0);
        g.pose().scale(scale * 0.85f, scale * 0.85f, 1);
        int ly = 0;
        for (Entry e : FEED) {
            KillNoticePacket d = e.data();
            long age = now - e.time();
            int alpha = (int) (255 * (age < FEED_MS - 800 ? 1f : (FEED_MS - age) / 800f));
            if (alpha < 8) continue;
            int a = alpha << 24;
            String hs = d.headshot() ? " ◎" : "";
            int kw = font.width(d.killer()), vw = font.width(d.victim() + hs);
            int iconW = d.weapon().isEmpty() ? 6 : 20;
            int total = kw + iconW + vw + 8;
            int x = -total;
            g.fill(x - 3, ly - 2, 2, ly + 14, (int) (0x60 * alpha / 255f) << 24);
            g.drawString(font, d.killer(), x, ly + 3, a | d.killerColor(), true);
            x += kw + 4;
            if (!d.weapon().isEmpty()) g.renderItem(d.weapon(), x, ly - 1);
            else g.drawString(font, "›", x, ly + 3, a | 0xAAAAAA, true);
            x += iconW;
            g.drawString(font, d.victim(), x, ly + 3, a | d.victimColor(), true);
            if (!hs.isEmpty()) g.drawString(font, hs, x + font.width(d.victim()), ly + 3, a | 0xFFD040, true);
            ly += 17;
        }
        g.pose().popPose();
    }
}
