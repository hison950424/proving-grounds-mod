package com.provinggrounds.ui;

import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.modes.arena.ArenaClass;
import com.provinggrounds.modes.arena.ArenaMode;
import com.provinggrounds.network.ArenaStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * 檔案: ArenaHud.java
 * 目的: 團隊死鬥／佔點／槍王之王的 HUD（只在客戶端）。
 *   中上：模式標題、計時、兩隊分數條（槍王之王是各隊階段列表）、狀態列（佔點進度）。
 *   右下：金錢、職業、擊殺／死亡，在基地時提示「M 商店」。
 */
public final class ArenaHud {

    private static final int BG = 0xC80F141E, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF, DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864;

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, width, height);

    private ArenaHud() {}

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientArenaState.active()) return;
        ArenaStatePacket s = ClientArenaState.get();
        Font font = mc.font;
        if (s.kind() == 3) renderList(g, font, s, w);
        else renderVersus(g, font, s, w);
        renderWallet(g, font, s, w, h);
    }

    /** 兩隊對戰：紅 [分數條] 計時 [分數條] 藍。 */
    private static void renderVersus(GuiGraphics g, Font font, ArenaStatePacket s, int w) {
        int boxW = 260, x = (w - boxW) / 2, y = 4;
        g.fill(x, y, x + boxW, y + 36, BG);
        String timer = s.seconds() >= 0 ? clock(s.seconds()) : "";
        g.drawCenteredString(font, s.title(), w / 2, y + 3, ACCENT);
        g.drawCenteredString(font, timer, w / 2, y + 14, TEXT);
        g.drawCenteredString(font, s.timerLabel(), w / 2, y + 25, DIM);
        List<ArenaMode.ScoreLine> sc = s.scores();
        if (sc.size() >= 2) {
            side(g, font, sc.get(0), x + 4, y + 4, 90, false);
            side(g, font, sc.get(1), x + boxW - 94, y + 4, 90, true);
        }
        if (!s.status().isEmpty()) {
            int sw = font.width(s.status()) + 12;
            g.fill((w - sw) / 2, y + 38, (w + sw) / 2, y + 50, BG);
            g.drawCenteredString(font, s.status(), w / 2, y + 40, TEXT);
        }
    }

    private static void side(GuiGraphics g, Font font, ArenaMode.ScoreLine line, int x, int y, int width, boolean right) {
        int color = 0xFF000000 | line.color();
        String head = line.label() + " " + line.value();
        if (right) g.drawString(font, head, x + width - font.width(head), y, color, false);
        else g.drawString(font, head, x, y, color, false);
        int barY = y + 11;
        g.fill(x, barY, x + width, barY + 4, 0xFF2A3344);
        int fill = line.target() <= 0 ? 0 : Math.min(width, width * line.value() / line.target());
        if (right) g.fill(x + width - fill, barY, x + width, barY + 4, color);
        else g.fill(x, barY, x + fill, barY + 4, color);
        String extra = line.extra().isEmpty() ? "目標 " + line.target() : line.extra() + "・目標 " + line.target();
        if (right) g.drawString(font, extra, x + width - font.width(extra), barY + 7, DIM, false);
        else g.drawString(font, extra, x, barY + 7, DIM, false);
    }

    /** 槍王之王：各隊階段。 */
    private static void renderList(GuiGraphics g, Font font, ArenaStatePacket s, int w) {
        int boxW = 200, x = (w - boxW) / 2, y = 4;
        List<ArenaMode.ScoreLine> sc = s.scores();
        int boxH = 16 + sc.size() * 11;
        g.fill(x, y, x + boxW, y + boxH, BG);
        g.drawCenteredString(font, s.title(), w / 2, y + 3, ACCENT);
        for (int i = 0; i < sc.size(); i++) {
            ArenaMode.ScoreLine l = sc.get(i);
            int ly = y + 15 + i * 11;
            g.drawString(font, l.label(), x + 6, ly, 0xFF000000 | l.color(), false);
            String right = "第 " + Math.min(l.value(), l.target()) + "/" + l.target() + " 階　" + l.extra();
            g.drawString(font, right, x + boxW - 6 - font.width(right), ly, TEXT, false);
        }
        if (!s.status().isEmpty()) {
            int sw = font.width(s.status()) + 12;
            g.fill((w - sw) / 2, y + boxH + 2, (w + sw) / 2, y + boxH + 14, BG);
            g.drawCenteredString(font, s.status(), w / 2, y + boxH + 4, TEXT);
        }
    }

    /** 右下：金錢、職業、K/D。 */
    private static void renderWallet(GuiGraphics g, Font font, ArenaStatePacket s, int w, int h) {
        int boxW = 120, x = w - boxW - 4, y = h - 64;
        int lines = s.shop() ? 3 : 1;
        g.fill(x, y, x + boxW, y + 6 + lines * 11, BG);
        int ly = y + 4;
        if (s.shop()) {
            g.drawString(font, "$" + s.money(), x + 6, ly, GOOD, false);
            if (s.classes()) {
                String c = ArenaClass.byOrdinal(s.classOrdinal()).displayName;
                g.drawString(font, c, x + boxW - 6 - font.width(c), ly, DIM, false);
            }
            ly += 11;
        }
        g.drawString(font, "擊殺 " + s.kills() + "　死亡 " + s.deaths(), x + 6, ly, TEXT, false);
        ly += 11;
        if (s.shop()) g.drawString(font, s.canShop() ? "[M] 打開商店" : "回基地才能購買", x + 6, ly, s.canShop() ? ACCENT : DIM, false);
    }

    static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
