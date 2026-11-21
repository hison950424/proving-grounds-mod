package com.provinggrounds.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 檔案: PGButton.java
 * 目的: CS2 / Valorant 深色風格按鈕（只在客戶端使用）。
 *       2px 圓角（削角模擬）、hover 時邊框亮白、點擊時縮放回饋、字距模擬、可掛右側小標籤。
 */
public class PGButton extends AbstractButton {

    private static final int BG          = 0xE0161C28;
    private static final int BG_HOVER    = 0xF0222A3A;
    private static final int BG_DISABLED = 0xC0121620;
    private static final int BORDER      = 0xFF2A3344;
    private static final int BORDER_HOVER = 0xFFFFFFFF;
    private static final int ACCENT      = 0xFFE8D060;
    private static final int TEXT        = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFF6A7384;
    private static final long PRESS_MS = 140;

    private final Runnable action;
    private final boolean accent;
    private final boolean spaced;
    @Nullable private String tag;
    private long pressedAt = 0;

    /**
     * @param accent  true = 左側金黃強調條（主要按鈕）
     * @param spaced  true = 英文全大寫加字距
     */
    public PGButton(int x, int y, int w, int h, Component label, boolean accent, boolean spaced, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
        this.accent = accent;
        this.spaced = spaced;
    }

    public PGButton withTag(@Nullable String tag) {
        this.tag = tag;
        return this;
    }

    @Override
    public void onPress() {
        pressedAt = Util.getMillis();
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHoveredOrFocused();

        // 點擊縮放回饋：按下後 140ms 內從 0.95 回到 1.0
        long since = Util.getMillis() - pressedAt;
        float scale = since < PRESS_MS ? 0.95f + 0.05f * (since / (float) PRESS_MS) : 1f;
        g.pose().pushPose();
        if (scale < 1f) {
            float cx = x + w / 2f, cy = y + h / 2f;
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-cx, -cy, 0);
        }

        int bg = !active ? BG_DISABLED : hover ? BG_HOVER : BG;
        int border = hover ? BORDER_HOVER : BORDER;
        // 削角模擬 2px 圓角：邊框外框 + 內部填色都避開四個角
        roundedRect(g, x, y, w, h, border);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, bg);
        if (accent && active) g.fill(x + 1, y + 2, x + 3, y + h - 2, ACCENT);

        String text = getMessage().getString();
        if (spaced) text = text.toUpperCase();
        int textW = spaced ? spacedWidth(font, text) : font.width(text);
        int tx = x + (w - textW) / 2;
        int ty = y + (h - 8) / 2;
        int color = active ? TEXT : TEXT_DIM;
        if (spaced) drawSpaced(g, font, text, tx, ty, color); else g.drawString(font, text, tx, ty, color, false);

        if (tag != null) {
            int tagW = font.width(tag);
            g.drawString(font, tag, x + w - tagW - 6, ty, active ? ACCENT : TEXT_DIM, false);
        }
        g.pose().popPose();
    }

    private static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static int spacedWidth(Font font, String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) w += font.width(String.valueOf(text.charAt(i))) + 1;
        return Math.max(0, w - 1);
    }

    static void drawSpaced(GuiGraphics g, Font font, String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(font, ch, x, y, color, false);
            x += font.width(ch) + 1;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
