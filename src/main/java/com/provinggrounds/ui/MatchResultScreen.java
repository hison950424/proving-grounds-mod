package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.modes.br.MatchResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: MatchResultScreen.java
 * 目的: 「本場數據」畫面（計畫書 B4，只在客戶端）。
 *   上方：獲勝隊伍與獲勝玩家、本場時間；中間：亮點卡片（MVP、最高擊殺、最高助攻、最高傷害、最長存活、扶起、祭壇）；
 *   下方：全體玩家排行表（名次、玩家、隊伍、擊殺、助攻、傷害、存活、XP），自己那一列特別標示，離線的人標「離線」。
 *   結算時自動開啟，回到大廳（BR 狀態變成「無」）時自動關閉，不擋住傳送；Esc 或「關閉」按鈕也能關。
 *   不暫停遊戲、沒有輸入框，所以開著時仍可用 WASD 移動（A11）。客戶端保留最近一場（/pg result 也會再送一次）。
 */
public class MatchResultScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF,
        TEXT_DIM = 0xFFA0A8B8, SELF = 0x60E8D060, CARD = 0xFF1A2130, WIN = 0xFF4CC864;
    private static final int ROW_H = 11;

    @Nullable private static MatchResult last;

    private final MatchResult result;
    private final boolean closeWithGame; // 結算時開的：BR 狀態回到「無」就自動關
    private int scroll;
    private int left, top, w, h, tableTop, visibleRows;

    private MatchResultScreen(MatchResult result, boolean closeWithGame) {
        super(Component.literal("本場數據"));
        this.result = result;
        this.closeWithGame = closeWithGame;
    }

    /** 收到結算資料：保存起來，open = true 時開啟畫面（開著其他畫面時不搶，改提示用 /pg result）。 */
    public static void receive(MatchResult result, boolean open) {
        last = result;
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof MatchResultScreen) {
            mc.setScreen(new MatchResultScreen(result, ClientBRState.active()));
        }
    }

    @Nullable
    public static MatchResult last() { return last; }

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(320, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(new PGButton(left + w - 58, top + h - 22, 50, 16, Component.literal("關閉"), false, false, this::onClose));
    }

    @Override
    public void tick() {
        if (closeWithGame && !ClientBRState.active()) onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, result.rows().size() - visibleRows);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Font f = font;
        g.fill(left - 1, top - 1, left + w + 1, top + h + 1, BORDER);
        g.fill(left, top, left + w, top + h, BG);

        // 上方：結果與獲勝玩家
        g.drawCenteredString(f, Component.literal(result.winnerLabel()), width / 2, top + 8, ACCENT);
        String winners = result.winnerNames().isEmpty() ? "沒有獲勝者" : "獲勝玩家：" + String.join("、", result.winnerNames());
        g.drawCenteredString(f, Component.literal(trim(f, winners, w - 20)), width / 2, top + 20, TEXT);
        g.drawCenteredString(f, Component.literal("本場時間 " + MatchResult.mmss(result.durationSeconds())), width / 2, top + 31, TEXT_DIM);

        // 中間：亮點卡片（每列 4 張）
        List<MatchResult.Highlight> hl = result.highlights();
        int cols = 4, gap = 4, cardW = (w - 16 - gap * (cols - 1)) / cols, cardH = 30;
        int cardsTop = top + 44;
        for (int i = 0; i < hl.size(); i++) {
            int cx = left + 8 + (i % cols) * (cardW + gap), cy = cardsTop + (i / cols) * (cardH + gap);
            MatchResult.Highlight x = hl.get(i);
            g.fill(cx, cy, cx + cardW, cy + cardH, CARD);
            g.drawString(f, x.title(), cx + 4, cy + 3, TEXT_DIM, false);
            g.drawString(f, trim(f, x.names(), cardW - 8), cx + 4, cy + 12, x.names().equals("無") ? TEXT_DIM : TEXT, false);
            if (!x.value().isEmpty()) g.drawString(f, x.value(), cx + 4, cy + 21, ACCENT, false);
        }
        int rowsOfCards = (hl.size() + cols - 1) / cols;

        // 下方：排行表
        tableTop = cardsTop + rowsOfCards * (cardH + gap) + 4;
        int[] colX = colX();
        String[] head = { "名次", "玩家", "隊伍", "擊殺", "助攻", "傷害", "存活", "XP" };
        for (int i = 0; i < head.length; i++) g.drawString(f, head[i], colX[i], tableTop, TEXT_DIM, false);
        g.fill(left + 6, tableTop + 10, left + w - 6, tableTop + 11, BORDER);

        List<MatchResult.Row> rows = result.sortedRows();
        visibleRows = Math.max(1, (top + h - 26 - (tableTop + 13)) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            MatchResult.Row r = rows.get(scroll + i);
            int y = tableTop + 13 + i * ROW_H;
            if (r.id().equals(self)) g.fill(left + 6, y - 1, left + w - 6, y + ROW_H - 1, SELF);
            int color = r.offline() ? TEXT_DIM : TEXT;
            g.drawString(f, String.valueOf(r.place()), colX[0], y, r.winner() ? WIN : color, false);
            g.drawString(f, trim(f, r.name() + (r.offline() ? "（離線）" : ""), colX[2] - colX[1] - 4), colX[1], y, color, false);
            g.drawString(f, trim(f, r.team(), colX[3] - colX[2] - 4), colX[2], y, 0xFF000000 | r.color(), false);
            g.drawString(f, String.valueOf(r.kills()), colX[3], y, color, false);
            g.drawString(f, String.valueOf(r.assists()), colX[4], y, color, false);
            g.drawString(f, String.valueOf(r.damage()), colX[5], y, color, false);
            g.drawString(f, MatchResult.mmss(r.aliveSeconds()), colX[6], y, color, false);
            g.drawString(f, "+" + r.xp(), colX[7], y, color, false);
        }
        if (rows.size() > visibleRows) {
            g.drawString(f, String.format("滾輪捲動（%d／%d）", Math.min(rows.size(), scroll + visibleRows), rows.size()),
                left + 8, top + h - 18, TEXT_DIM, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private int[] colX() {
        // 名次、玩家（寬）、隊伍、擊殺、助攻、傷害、存活、XP
        double[] frac = { 0, 0.08, 0.38, 0.52, 0.61, 0.70, 0.80, 0.90 };
        int[] x = new int[frac.length];
        for (int i = 0; i < frac.length; i++) x[i] = left + 8 + (int) ((w - 16) * frac[i]);
        return x;
    }

    private static String trim(Font f, String s, int maxW) {
        if (f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, Math.max(0, maxW - f.width("…"))) + "…";
    }
}
