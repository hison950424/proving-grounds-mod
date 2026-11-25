package com.provinggrounds.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * 檔案: Dropdown.java
 * 目的: 下拉選單（使用者回饋：以前「點一下換下一個」的設定按鈕，改成點一下展開全部選項、選一個）。
 *   畫面用法：按鈕的動作呼叫 Dropdown.open(...)；畫面的 render 最後呼叫 render（畫在最上層）；
 *   mouseClicked／mouseScrolled／keyPressed 先交給它（展開中會吃掉點擊）。一次只會有一個下拉選單展開。
 *   選項太多時最多顯示 MAX_ROWS 列，可以用滾輪捲動。
 */
public final class Dropdown {

    private static final int ROW_H = 13, MAX_ROWS = 10;
    private static final int BG = 0xF0141A26, BORDER = 0xFFE8D060, HOVER = 0x50FFFFFF, CURRENT = 0xFFE8D060, TEXT = 0xFFFFFFFF;

    private static Dropdown open;

    private final int x, y, w;
    private final List<String> options;
    private final int current;
    private final IntConsumer pick;
    private int scroll;

    private Dropdown(int x, int y, int w, List<String> options, int current, IntConsumer pick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.options = options;
        this.current = current;
        this.pick = pick;
        if (current >= MAX_ROWS) scroll = Math.min(current - MAX_ROWS / 2, options.size() - MAX_ROWS);
    }

    /** 在 (x, y) 下方展開（y = 按鈕底部）；再按一次同一個位置就收起來。 */
    public static void open(int x, int y, int w, List<String> options, int current, IntConsumer pick) {
        if (open != null && open.x == x && open.y == y) { open = null; return; }
        open = new Dropdown(x, y, w, options, current, pick);
    }

    public static boolean isOpen() { return open != null; }

    public static void close() { open = null; }

    private int rows() { return Math.min(MAX_ROWS, options.size()); }

    /** 畫面太矮時往上展開。 */
    private int top(int screenH) {
        int h = rows() * ROW_H + 2;
        return y + h > screenH - 2 ? Math.max(2, y - h - 16) : y;
    }

    public static void render(GuiGraphics g, Font font, int mouseX, int mouseY, int screenH) {
        Dropdown d = open;
        if (d == null) return;
        int top = d.top(screenH), h = d.rows() * ROW_H + 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // 蓋在其他按鈕與物品上面
        g.fill(d.x - 1, top - 1, d.x + d.w + 1, top + h + 1, BORDER);
        g.fill(d.x, top, d.x + d.w, top + h, BG);
        for (int r = 0; r < d.rows(); r++) {
            int i = r + d.scroll;
            if (i >= d.options.size()) break;
            int ry = top + 1 + r * ROW_H;
            boolean over = mouseX >= d.x && mouseX < d.x + d.w && mouseY >= ry && mouseY < ry + ROW_H;
            if (over) g.fill(d.x, ry, d.x + d.w, ry + ROW_H, HOVER);
            String s = font.plainSubstrByWidth(d.options.get(i), d.w - 8);
            g.drawString(font, s, d.x + 4, ry + 3, i == d.current ? CURRENT : TEXT, false);
        }
        if (d.options.size() > MAX_ROWS) { // 捲軸
            int barH = Math.max(8, h * MAX_ROWS / d.options.size());
            int barY = top + (h - barH) * d.scroll / Math.max(1, d.options.size() - MAX_ROWS);
            g.fill(d.x + d.w - 2, barY, d.x + d.w, barY + barH, BORDER);
        }
        g.pose().popPose();
    }

    /** 展開中：點選項就選、點外面就收起來；回傳 true = 這次點擊被吃掉。 */
    public static boolean mouseClicked(double mx, double my, int screenH) {
        Dropdown d = open;
        if (d == null) return false;
        int top = d.top(screenH);
        open = null;
        if (mx >= d.x && mx < d.x + d.w && my >= top && my < top + d.rows() * ROW_H + 2) {
            int i = (int) ((my - top - 1) / ROW_H) + d.scroll;
            if (i >= 0 && i < d.options.size() && i != d.current) {
                PGButton.playClick(1.0f);
                d.pick.accept(i);
            }
        }
        return true;
    }

    public static boolean mouseScrolled(double delta) {
        Dropdown d = open;
        if (d == null) return false;
        d.scroll = Math.max(0, Math.min(Math.max(0, d.options.size() - MAX_ROWS), d.scroll - (int) Math.signum(delta)));
        return true;
    }
}
