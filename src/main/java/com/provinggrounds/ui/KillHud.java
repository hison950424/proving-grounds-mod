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
            // 助攻不覆蓋剛跳出的擊殺／擊倒旗幟（0.6 秒內），但音效照常播放
            if (!p.assist() || icon == null || icon.assist() || now - iconTime > 600) {
                icon = p;
                iconTime = now;
            }
            playSound(p);
        }
    }

    public static void clear() { FEED.clear(); icon = null; }

    /**
     * 參考 Apex Legends 與三角洲行動（Delta Force）的擊殺／擊倒／助攻爽感與高度區別性：
     *   - 擊殺（KILL）：厚實低頻斬殺重擊＋雙層清脆高頻金屬叮噹（連殺音階上升，爆頭再加破甲銳音），音量飽滿清晰。
     *   - 倒地（KNOCK）：明顯碎盾破防聲＋紮實中低頻頓點（一聽就知道敵人倒地而非淘汰）。
     *   - 助攻（ASSIST）：輕快清亮高音雙響，不與擊殺／倒地混淆。
     */
    private static void playSound(KillNoticePacket p) {
        var cfg = PGClientConfig.CLIENT;
        if (!cfg.killSoundEnabled.get()) return;
        float baseVol = cfg.killSoundVolume.get().floatValue();
        if (baseVol <= 0) return;
        // 放大動態增益讓戰鬥槍聲中依然清晰爽快
        float vol = Math.min(1.6f, baseVol * 1.45f);

        if (p.assist()) {
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.55f, vol * 0.95f);
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.85f, vol * 0.75f);
            return;
        }
        if (p.knock()) {
            // 擊倒：破甲碎裂＋中頻頓點確認音
            play(SoundEvents.SHIELD_BREAK, 1.15f, vol * 0.75f);
            play(SoundEvents.GLASS_BREAK, 1.35f, vol * 0.65f);
            play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.95f, vol * 1.0f);
            play(SoundEvents.ARROW_HIT_PLAYER, 1.20f, vol * 0.85f);
            if (p.headshot()) {
                play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.70f, vol * 0.95f);
            }
            return;
        }
        // 擊殺：低頻重擊打底＋三角洲／Apex 風格高亮清脆鐘鈴＋金屬回響
        float pitch = Math.min(2.0f, 1.15f + 0.08f * Math.max(0, p.streak() - 1));
        play(SoundEvents.ANVIL_LAND, 1.65f, vol * 0.35f);
        play(SoundEvents.ARROW_HIT_PLAYER, Math.min(2.0f, pitch + 0.35f), vol * 1.0f);
        play(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, vol * 1.15f);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, Math.min(2.0f, pitch + 0.45f), vol * 1.1f);
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, Math.min(2.0f, pitch + 0.35f), vol * 0.85f);
        if (p.headshot()) {
            play(SoundEvents.GLASS_BREAK, 1.85f, vol * 0.60f);
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), Math.min(2.0f, pitch + 0.60f), vol * 1.15f);
        }
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

        boolean isAssist = icon.assist();
        boolean isKnock = icon.knock();
        int color = isAssist ? 0x58E0FF
            : isKnock ? (icon.headshot() ? 0xFFB830 : 0xFF8830)
            : (icon.headshot() ? 0xFFD840 : icon.streak() >= 3 ? 0xFF3838 : 0xFF5555);
        String text = isAssist ? "助攻"
            : isKnock ? (icon.headshot() ? "爆頭擊倒" : "擊倒")
            : (icon.headshot() ? "爆頭擊殺" : "擊殺") + (icon.streak() >= 2 ? "  " + icon.streak() + " 連殺" : "");

        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f + 26, 0);
        float pop = t < 0.12f ? 1.45f - 3.75f * t : 1f; // 俐落彈入
        g.pose().scale(scale * 0.80f * pop, scale * 0.80f * pop, 1);

        int c = (alpha << 24) | color;
        int bgA = (int) (alpha * 0.55f);
        int bg = (bgA << 24) | 0x0C1018;
        int tw = font.width(text);
        int bw = Math.max(36, tw + 14);

        // 戰術旗幟底帶＋頂部強調線（三種事件明顯區分形狀與配色）
        g.fill(-bw / 2, -2, bw / 2, 11, bg);
        g.fill(-bw / 2, -3, bw / 2, -2, c);

        if (isAssist) {
            // 助攻旗幟：青藍色十字星徽章（＋）
            g.fill(-5, -12, 6, -10, c);
            g.fill(-1, -16, 2, -6, c);
        } else if (isKnock) {
            // 擊倒旗幟：橘金色向下擊倒箭頭（▼）與雙側翼線
            g.fill(-6, -15, 7, -13, c);
            g.fill(-4, -13, 5, -11, c);
            g.fill(-2, -11, 3, -9, c);
            g.fill(-1, -9, 2, -7, c);
        } else {
            // 擊殺旗幟：鮮紅／金色雙刃交叉（╳）＋頂端王冠亮點
            for (int i = -4; i <= 4; i++) {
                g.fill(i - 1, i - 1 - 11, i + 1, i + 1 - 11, c);
                g.fill(i - 1, -i - 1 - 11, i + 1, -i + 1 - 11, c);
            }
            if (icon.headshot() || icon.streak() >= 2) {
                g.fill(-2, -18, 3, -16, (alpha << 24) | 0xFFE860);
            }
        }
        g.drawString(font, text, -tw / 2, 1, c, true);
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
            String hs = (d.headshot() ? " ◎" : "") + (d.knock() ? " 擊倒" : "");
            int kw = font.width(d.killer()), vw = font.width(d.victim() + hs);
            int iconW = d.weapon().isEmpty() ? 6 : 20;
            int total = kw + iconW + vw + 8;
            int x = -total;
            g.fill(x - 3, ly - 2, 2, ly + 14, (int) (0x60 * alpha / 255f) << 24);
            g.drawString(font, d.killer(), x, ly + 3, a | ClientTeamColors.colorFor(d.killer(), d.killerColor()), true);
            x += kw + 4;
            if (!d.weapon().isEmpty()) g.renderItem(d.weapon(), x, ly - 1);
            else g.drawString(font, "›", x, ly + 3, a | 0xAAAAAA, true);
            x += iconW;
            g.drawString(font, d.victim(), x, ly + 3, a | ClientTeamColors.colorFor(d.victim(), d.victimColor()), true);
            if (!hs.isEmpty()) g.drawString(font, hs, x + font.width(d.victim()), ly + 3, a | (d.knock() ? 0xC0C0C0 : 0xFFD040), true);
            ly += 17;
        }
        g.pose().popPose();
    }
}
