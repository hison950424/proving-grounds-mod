package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 檔案: HUD.java
 * 目的: 遊戲中 overlay（只在客戶端執行）。CS2 / Valorant 深色風格。
 *   左上：存活人數 / 隊伍數（準備階段顯示登機倒數）
 *   右上：縮圈階段與倒數 / 飛行剩餘 / 結算倒數
 *   中上：在圈外時紅字警告與距離
 *   中下：跳傘提示、淘汰提示
 *   右下：擊殺提示（淡出）
 */
public final class HUD {

    // 配色（ARGB）
    private static final int BG        = 0xEB0F141E; // #0F141E，透明度 92%
    private static final int ACCENT    = 0xFFE8D060; // 金黃強調色
    private static final int TEXT      = 0xFFFFFFFF;
    private static final int TEXT_DIM  = 0xFFA0A8B8;
    private static final int DANGER    = 0xFFE04848;
    private static final int PAD = 6;

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, width, height);

    private HUD() {}

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        BRStatePacket s = ClientBRState.get();
        if (s.phase() == ClientBRState.PHASE_NONE) return;
        Font font = mc.font;

        renderTopLeft(g, font, s);
        renderTopRight(g, font, s, w);
        renderOutsideWarning(g, font, s, mc.player, w);
        renderHint(g, font, s, w, h);
        renderKillFeed(g, font, w, h);
    }

    // ── 左上 ────────────────────────────────────────────────────────────────

    private static void renderTopLeft(GuiGraphics g, Font font, BRStatePacket s) {
        int x = 8, y = 8;
        if (s.phase() == ClientBRState.PHASE_PREPARING) {
            panel(g, x, y, 120, 34);
            spaced(g, font, "BATTLE ROYALE", x + PAD + 2, y + PAD, ACCENT);
            g.drawString(font, "登機倒數 " + s.seconds() + " 秒", x + PAD + 2, y + PAD + 13, TEXT, false);
            return;
        }
        boolean cards = s.cardsHeld() > 0;
        panel(g, x, y, 96, cards ? 47 : 34);
        g.drawString(font, "存活", x + PAD + 2, y + PAD, TEXT_DIM, false);
        g.drawString(font, String.valueOf(s.alivePlayers()), x + PAD + 30, y + PAD, TEXT, false);
        g.drawString(font, "隊伍", x + PAD + 2, y + PAD + 13, TEXT_DIM, false);
        g.drawString(font, String.valueOf(s.aliveTeams()), x + PAD + 30, y + PAD + 13, TEXT, false);
        if (cards) {
            g.drawString(font, "復活牌", x + PAD + 2, y + PAD + 26, TEXT_DIM, false);
            g.drawString(font, "×" + s.cardsHeld(), x + PAD + 42, y + PAD + 26, ACCENT, false);
        }
    }

    // ── 右上 ────────────────────────────────────────────────────────────────

    private static void renderTopRight(GuiGraphics g, Font font, BRStatePacket s, int w) {
        String title, line;
        int lineColor = TEXT;
        switch (s.phase()) {
            case ClientBRState.PHASE_PLANE -> {
                title = "飛行中";
                line = "剩 " + s.seconds() + " 秒";
            }
            case ClientBRState.PHASE_ACTIVE -> {
                title = "第 " + s.stage() + " / " + s.totalStages() + " 階段";
                if (s.nextRadius() < 0) {
                    line = "最終圈";
                    lineColor = DANGER;
                } else if (s.shrinking()) {
                    line = "縮圈中 " + mmss(s.seconds());
                    lineColor = DANGER;
                } else {
                    line = mmss(s.seconds()) + " 後縮圈";
                }
            }
            case ClientBRState.PHASE_ENDING -> {
                title = "本局結束";
                line = s.seconds() + " 秒後返回大廳";
            }
            default -> { return; }
        }
        int boxW = Math.max(font.width(title), font.width(line)) + PAD * 2 + 4;
        int x = w - boxW - 8, y = 8;
        panel(g, x, y, boxW, 34);
        g.drawString(font, title, x + PAD + 2, y + PAD, ACCENT, false);
        g.drawString(font, line, x + PAD + 2, y + PAD + 13, lineColor, false);
    }

    // ── 中上：圈外警告 ──────────────────────────────────────────────────────

    private static void renderOutsideWarning(GuiGraphics g, Font font, BRStatePacket s, LocalPlayer player, int w) {
        if (s.phase() != ClientBRState.PHASE_ACTIVE || s.radius() <= 0) return;
        if (s.selfState() == ClientBRState.SELF_DEAD || s.selfState() == ClientBRState.SELF_NONE) return;
        double dist = Math.hypot(player.getX() - s.cx(), player.getZ() - s.cz()) - s.radius();
        if (dist <= 0) return;
        String msg = "圈外！距安全區 " + (int) Math.ceil(dist) + " 格";
        int boxW = font.width(msg) + PAD * 2;
        int x = (w - boxW) / 2, y = 8;
        // 閃爍的紅色邊條
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        g.fill(x, y, x + boxW, y + 20, BG);
        g.fill(x, y + 18, x + boxW, y + 20, blink ? DANGER : 0x80E04848);
        g.drawString(font, msg, x + PAD, y + 6, DANGER, false);
    }

    // ── 中下：提示 ──────────────────────────────────────────────────────────

    private static void renderHint(GuiGraphics g, Font font, BRStatePacket s, int w, int h) {
        String hint = switch (s.selfState()) {
            case ClientBRState.SELF_PLANE -> "按 Shift 跳傘";
            case ClientBRState.SELF_FREEFALL -> "自由落體｜按 Shift 開傘";
            case ClientBRState.SELF_CHUTE -> "已開傘";
            case ClientBRState.SELF_DOWNED -> "倒地中｜流血 " + mmss(s.timerSeconds()) + "｜等待隊友扶起";
            case ClientBRState.SELF_DEAD -> switch (s.timerKind()) {
                case ClientBRState.TIMER_CARD_GROUND -> s.timerSeconds() >= 0
                    ? "已淘汰｜復活牌剩 " + mmss(s.timerSeconds()) + "，等待隊友撿起"
                    : "已淘汰｜你的復活牌掉在地上，等待隊友撿起";
                case ClientBRState.TIMER_FINAL -> "已確定死亡｜觀戰中";
                case ClientBRState.TIMER_CARD_HELD -> "已淘汰｜隊友已撿起你的復活牌，等待祭壇復活";
                default -> "已淘汰｜觀戰中";
            };
            default -> null;
        };
        int y = h - 78;
        if (hint != null) {
            boolean downed = s.selfState() == ClientBRState.SELF_DOWNED;
            int color = downed ? DANGER : s.selfState() == ClientBRState.SELF_DEAD ? TEXT_DIM : TEXT;
            int boxW = font.width(hint) + PAD * 2 + 4;
            int x = (w - boxW) / 2;
            panel(g, x, y, boxW, 20);
            g.drawString(font, hint, x + PAD + 2, y + 6, color, false);
        }
        renderProgress(g, font, s, w, y - 26);
    }

    /** 讀條：扶起隊友 / 被扶起 / 啟動祭壇。 */
    private static void renderProgress(GuiGraphics g, Font font, BRStatePacket s, int w, int y) {
        String label = switch (s.progressKind()) {
            case ClientBRState.PROGRESS_REVIVING -> "扶起隊友中…";
            case ClientBRState.PROGRESS_BEING_REVIVED -> "隊友正在扶你起來…";
            case ClientBRState.PROGRESS_ALTAR -> "啟動祭壇中…";
            default -> null;
        };
        if (label == null) return;
        int boxW = 160, x = (w - boxW) / 2;
        panel(g, x, y, boxW, 22);
        g.drawString(font, label, x + PAD + 2, y + 3, TEXT, false);
        int barX = x + PAD + 2, barW = boxW - PAD * 2 - 4, barY = y + 15;
        float p = Math.max(0f, Math.min(1f, s.progress()));
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF2A3344);
        g.fill(barX, barY, barX + (int) (barW * p), barY + 3, ACCENT);
    }

    // ── 右下：擊殺提示 ──────────────────────────────────────────────────────

    private static void renderKillFeed(GuiGraphics g, Font font, int w, int h) {
        ClientBRState.KillEntry[] kills = ClientBRState.kills();
        long now = System.currentTimeMillis();
        int y = h - 60;
        for (ClientBRState.KillEntry k : kills) {
            long age = now - k.timeMs();
            float alpha = age > ClientBRState.KILL_SHOW_MS - 1000
                ? Math.max(0f, (ClientBRState.KILL_SHOW_MS - age) / 1000f) : 1f;
            if (alpha <= 0.05f) continue;
            KillFeedPacket d = k.data();
            String sep = d.killer().isEmpty() ? "☠ " : "  ▸  ";
            int wKiller = font.width(d.killer());
            int wSep = font.width(sep);
            int wVictim = font.width(d.victim());
            int boxW = wKiller + wSep + wVictim + PAD * 2;
            int x = w - boxW - 8;
            g.fill(x, y, x + boxW, y + 14, withAlpha(BG, alpha * 0.92f));
            int tx = x + PAD;
            if (!d.killer().isEmpty()) {
                g.drawString(font, d.killer(), tx, y + 3, withAlpha(0xFF000000 | d.killerColor(), alpha), false);
                tx += wKiller;
            }
            g.drawString(font, sep, tx, y + 3, withAlpha(TEXT_DIM, alpha), false);
            tx += wSep;
            g.drawString(font, d.victim(), tx, y + 3, withAlpha(0xFF000000 | d.victimColor(), alpha), false);
            y -= 16;
        }
    }

    // ── 繪圖小工具 ──────────────────────────────────────────────────────────

    // ── 血條（取代原版愛心，由 ClientForgeEvents 呼叫）

    private static final int HP_BG   = 0xC0101018;
    private static final int HP_HIGH = 0xFF4CC864; // 綠
    private static final int HP_MID  = 0xFFE8C040; // 黃
    private static final int HP_LOW  = 0xFFE04848; // 紅

    /**
     * 在原版愛心的位置（快捷欄左上方）畫數字血條：寬 81、高 9，中間顯示「目前 / 最大」。
     * @param leftHeight 原版左側元素目前的高度（離畫面底部的距離）
     */
    public static void renderHealthBar(GuiGraphics g, Font font, LocalPlayer player, int w, int h, int leftHeight) {
        int x = w / 2 - 91, y = h - leftHeight, bw = 81, bh = 9;
        float max = Math.max(1f, player.getMaxHealth());
        float hp = Math.max(0f, Math.min(player.getHealth(), max));
        float ratio = hp / max;
        int color = ratio > 0.5f ? HP_HIGH : ratio > 0.25f ? HP_MID : HP_LOW;
        if (ClientBRState.get().selfState() == ClientBRState.SELF_DOWNED) color = HP_LOW;

        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, HP_BG);
        g.fill(x, y, x + Math.round(bw * ratio), y + bh, color);
        if (player.getAbsorptionAmount() > 0) { // 吸收血量：上緣一條金線
            float abs = Math.min(1f, player.getAbsorptionAmount() / max);
            g.fill(x, y, x + Math.round(bw * abs), y + 1, ACCENT);
        }
        String text = (int) Math.ceil(hp) + " / " + (int) Math.ceil(max);
        g.drawString(font, text, x + (bw - font.width(text)) / 2, y + 1, TEXT, true);
    }

    /** 深色面板 + 左側金黃強調條。 */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + 2, y + h, ACCENT);
    }

    /** 字距模擬：逐字繪製，每字多 1 像素間距（Minecraft 無法換字體）。 */
    private static void spaced(GuiGraphics g, Font font, String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(font, ch, x, y, color, false);
            x += font.width(ch) + 1;
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        a = Math.max(a, 5); // 字型繪製時 alpha < 4 會被當成不透明，保留下限
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static String mmss(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
