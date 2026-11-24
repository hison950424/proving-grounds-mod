package com.provinggrounds.ui;

import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: HudEditScreen.java
 * 目的: 「HUD 介面設定」畫面（只在客戶端；從主選單首頁進入）。
 *   畫面上每個 HUD 元件都用一個半透明方框＋範例文字預覽，在原本的位置、原本的大小：
 *     左鍵拖曳 = 移動（靠近畫面正中央時自動對齊）、滑鼠滾輪 = 放大縮小、右鍵 = 顯示／隱藏、方向鍵 = 微調 1 格（Shift 5 格）。
 *   側邊面板：
 *     「版面」分頁：每個元件一列 —— 名稱（點一下選取）、顯示／隱藏、－ 大小 ＋、重設。
 *     「其他」分頁：點狀準心、擊殺音效與音量、受擊方向指示、佔點光柱、防蹲點紅牆、戰績面板開法、提示文字大小。
 *     底部：全部重設、面板換邊（面板擋住元件時用）、完成。
 *   版面存在 config/provinggrounds-hud.json；其他設定直接改客戶端設定檔（provinggrounds-client.toml）。
 */
public class HudEditScreen extends Screen {

    private static final int BG = 0xE00F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF,
        DIM = 0xFFA0A8B8, OFF = 0xFF6A7384;
    private static final int PW = 214, ROW = 18, BH = 14;
    private static final String[] GROUPS = { "大逃殺", "團隊模式", "通用" };

    // 記住上次的分頁、捲動、面板位置（同一次遊戲啟動期間）
    private static int tab;
    private static int scroll;
    private static boolean panelRight;

    @Nullable private final Screen parent;
    private int px, py, ph, listTop, listBottom;
    @Nullable private HudLayout.El selected, dragging;
    private int grabX, grabY;
    /** 「版面」分頁可點的名稱區（選取元件用）。 */
    private final List<Object[]> labelHits = new ArrayList<>();
    /** 要畫的文字（分組標題、名稱、數值）。 */
    private final List<Object[]> texts = new ArrayList<>();

    public HudEditScreen(@Nullable Screen parent) {
        super(Component.literal("HUD 介面設定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ph = Math.min(height - 16, 330);
        px = panelRight ? width - PW - 8 : 8;
        py = (height - ph) / 2;
        listTop = py + 42;
        listBottom = py + ph - 30;
        labelHits.clear();
        texts.clear();

        int tw = (PW - 20 - 6) / 2;
        addRenderableWidget(new PGButton(px + 10, py + 20, tw, BH, Component.literal("版面"), tab == 0, false, () -> switchTab(0)));
        addRenderableWidget(new PGButton(px + 16 + tw, py + 20, tw, BH, Component.literal("其他"), tab == 1, false, () -> switchTab(1)));

        if (tab == 0) initLayout(); else initOther();

        int bw = (PW - 20 - 12) / 3, by = py + ph - 22;
        addRenderableWidget(new PGButton(px + 10, by, bw, BH, Component.literal("全部重設"), false, false, () -> {
            HudLayout.resetAll();
            rebuildWidgets();
        }));
        addRenderableWidget(new PGButton(px + 16 + bw, by, bw, BH, Component.literal("面板換邊"), false, false, () -> {
            panelRight = !panelRight;
            rebuildWidgets();
        }));
        addRenderableWidget(new PGButton(px + 22 + 2 * bw, by, bw, BH, Component.literal("完成"), true, false, this::onClose));
    }

    private void switchTab(int t) {
        tab = t;
        scroll = 0;
        rebuildWidgets();
    }

    /** 可捲動清單的一列：在可視範圍內就回傳 y，否則回傳 -1。 */
    private int rowY(int index) {
        int y = listTop + (index - scroll) * ROW;
        return index < scroll || y + ROW > listBottom ? -1 : y;
    }

    private int visibleRows() { return Math.max(1, (listBottom - listTop) / ROW); }

    // ── 版面分頁 ────────────────────────────────────────────────────────────

    private int layoutRowCount() { return HudLayout.El.values().length + GROUPS.length; }

    private void initLayout() {
        scroll = Math.max(0, Math.min(scroll, layoutRowCount() - visibleRows()));
        int i = 0, group = -1;
        for (HudLayout.El e : HudLayout.El.values()) {
            if (e.group != group) {
                group = e.group;
                int y = rowY(i++);
                if (y >= 0) texts.add(new Object[] { px + 10, y + 4, GROUPS[group], ACCENT });
            }
            int y = rowY(i++);
            if (y < 0) continue;
            boolean on = HudLayout.enabled(e);
            texts.add(new Object[] { px + 10, y + 4, e.label, e == selected ? ACCENT : on ? TEXT : OFF });
            labelHits.add(new Object[] { e, px + 8, y, 74 });
            int x = px + 84;
            addRenderableWidget(btn(x, y, 30, on ? "顯示" : "隱藏", on, () -> { HudLayout.setEnabled(e, !HudLayout.enabled(e)); rebuildWidgets(); }));
            x += 33;
            PGButton minus = btn(x, y, 14, "－", false, () -> step(e, -0.1f));
            minus.active = HudLayout.scale(e) > HudLayout.MIN_SCALE;
            addRenderableWidget(minus);
            texts.add(new Object[] { x + 15 + 1, y + 4, Math.round(HudLayout.scale(e) * 100) + "%", on ? TEXT : OFF, 28 });
            x += 15 + 28;
            PGButton plus = btn(x, y, 14, "＋", false, () -> step(e, 0.1f));
            plus.active = HudLayout.scale(e) < HudLayout.MAX_SCALE;
            addRenderableWidget(plus);
            x += 17;
            PGButton reset = btn(x, y, PW - 10 - (x - px), "重設", false, () -> { HudLayout.reset(e); rebuildWidgets(); });
            reset.active = !HudLayout.isDefault(e) || !on;
            addRenderableWidget(reset);
        }
    }

    private void step(HudLayout.El e, float d) {
        HudLayout.setScale(e, HudLayout.scale(e) + d, width, height);
        selected = e;
        rebuildWidgets();
    }

    // ── 其他分頁 ────────────────────────────────────────────────────────────

    private int otherRows;

    private void initOther() {
        PGClientConfig c = PGClientConfig.CLIENT;
        int[] i = { 0 };
        toggle(i, "點狀準心", c.crosshairDot);
        toggle(i, "擊殺音效", c.killSoundEnabled);
        stepper(i, "擊殺音量", c.killSoundVolume, 0.1, 0.0, 1.0, true);
        toggle(i, "受擊方向指示", c.damageIndicatorEnabled);
        stepper(i, "受擊顯示秒數", c.damageIndicatorSeconds, 0.5, 0.5, 5.0, false);
        toggle(i, "佔點光柱與距離", c.domBeacon);
        toggle(i, "敵方重生區紅牆", c.antiCampWall);
        int y = rowY(i[0]++);
        if (y >= 0) {
            boolean t = c.scoreboardToggle.get();
            texts.add(new Object[] { px + 10, y + 4, "戰績面板", TEXT });
            addRenderableWidget(btn(px + PW - 10 - 80, y, 80, t ? "按一下開關" : "按住顯示", false, () -> {
                c.scoreboardToggle.set(!c.scoreboardToggle.get());
                c.scoreboardToggle.save();
                rebuildWidgets();
            }));
        }
        stepper(i, "提示文字大小", c.hudScale, 0.1, 0.5, 2.0, true);
        otherRows = i[0];
        int ny = rowY(i[0]++);
        if (ny >= 0) texts.add(new Object[] { px + 10, ny + 4, "版面（位置、大小）在「版面」分頁", DIM });
    }

    private void toggle(int[] i, String label, ForgeConfigSpec.BooleanValue v) {
        int y = rowY(i[0]++);
        if (y < 0) return;
        boolean on = v.get();
        texts.add(new Object[] { px + 10, y + 4, label, TEXT });
        addRenderableWidget(btn(px + PW - 10 - 40, y, 40, on ? "開" : "關", on, () -> {
            v.set(!v.get());
            v.save();
            rebuildWidgets();
        }));
    }

    private void stepper(int[] i, String label, ForgeConfigSpec.DoubleValue v, double step, double min, double max, boolean percent) {
        int y = rowY(i[0]++);
        if (y < 0) return;
        double now = v.get();
        texts.add(new Object[] { px + 10, y + 4, label, TEXT });
        int x = px + PW - 10 - 76;
        PGButton minus = btn(x, y, 14, "－", false, () -> setDouble(v, now - step, min, max));
        minus.active = now > min + 1e-6;
        addRenderableWidget(minus);
        String shown = percent ? Math.round(now * 100) + "%" : String.format("%.1f 秒", now);
        texts.add(new Object[] { x + 16, y + 4, shown, TEXT, 44 });
        PGButton plus = btn(x + 62, y, 14, "＋", false, () -> setDouble(v, now + step, min, max));
        plus.active = now < max - 1e-6;
        addRenderableWidget(plus);
    }

    private void setDouble(ForgeConfigSpec.DoubleValue v, double value, double min, double max) {
        v.set(Math.round(Math.max(min, Math.min(max, value)) * 100) / 100.0);
        v.save();
        rebuildWidgets();
    }

    private static PGButton btn(int x, int y, int w, String label, boolean accent, Runnable action) {
        return new PGButton(x, y, w, BH, Component.literal(label), accent, false, action);
    }

    // ── 滑鼠與鍵盤 ──────────────────────────────────────────────────────────

    private boolean inPanel(double mx, double my) {
        return mx >= px && mx < px + PW && my >= py && my < py + ph;
    }

    @Nullable
    private HudLayout.El elementAt(double mx, double my) {
        // 選取中的元件優先（重疊時好抓）
        if (selected != null && hit(selected, mx, my)) return selected;
        HudLayout.El[] all = HudLayout.El.values();
        for (int i = all.length - 1; i >= 0; i--) if (hit(all[i], mx, my)) return all[i];
        return null;
    }

    private boolean hit(HudLayout.El e, double mx, double my) {
        HudLayout.Box b = HudLayout.box(e, width, height);
        return mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (inPanel(mx, my)) {
            if (button == 0 && tab == 0) {
                for (Object[] h : labelHits) {
                    int x = (int) h[1], y = (int) h[2], w = (int) h[3];
                    if (mx >= x && mx < x + w && my >= y && my < y + BH) {
                        selected = (HudLayout.El) h[0];
                        PGButton.playClick(1.0f);
                        rebuildWidgets();
                        return true;
                    }
                }
            }
            return true;
        }
        HudLayout.El e = elementAt(mx, my);
        if (e == null) {
            selected = null;
            rebuildWidgets();
            return false;
        }
        if (button == 1) {
            HudLayout.setEnabled(e, !HudLayout.enabled(e));
            PGButton.playClick(1.0f);
        } else if (button == 0) {
            HudLayout.Box b = HudLayout.box(e, width, height);
            dragging = e;
            grabX = (int) mx - b.x();
            grabY = (int) my - b.y();
        }
        selected = e;
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            HudLayout.Box b = HudLayout.box(dragging, width, height);
            int x = (int) mx - grabX, y = (int) my - grabY;
            // 靠近畫面正中央（4 格內）時自動對齊
            int cx = x + b.w() / 2;
            if (Math.abs(cx - width / 2) <= 4) x = width / 2 - b.w() / 2;
            HudLayout.moveTo(dragging, x, y, width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null && button == 0) {
            dragging = null;
            rebuildWidgets(); // 「重設」按鈕的狀態可能變了
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inPanel(mx, my)) {
            int max = Math.max(0, (tab == 0 ? layoutRowCount() : otherRows + 1) - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            rebuildWidgets();
            return true;
        }
        HudLayout.El e = elementAt(mx, my);
        if (e != null) {
            step(e, delta > 0 ? 0.05f : -0.05f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (selected != null) {
            int d = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
            int dx = key == GLFW.GLFW_KEY_LEFT ? -d : key == GLFW.GLFW_KEY_RIGHT ? d : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -d : key == GLFW.GLFW_KEY_DOWN ? d : 0;
            if (dx != 0 || dy != 0) {
                HudLayout.Box b = HudLayout.box(selected, width, height);
                HudLayout.moveTo(selected, b.x() + dx, b.y() + dy, width, height);
                rebuildWidgets();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        HudLayout.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        HudLayout.save(); // 直接被別的畫面取代時也要存
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 不畫全螢幕暗底：要看得到後面真正的 HUD；只把畫面稍微壓暗，方框比較清楚
        g.fill(0, 0, width, height, 0x40000000);
        // 畫面正中央的對齊線（拖曳時）
        if (dragging != null) g.fill(width / 2, 0, width / 2 + 1, height, 0x40E8D060);
        HudLayout.El hover = inPanel(mouseX, mouseY) ? null : elementAt(mouseX, mouseY);
        for (HudLayout.El e : HudLayout.El.values()) drawElement(g, e, e == selected || e == dragging, e == hover);

        // 側邊面板
        g.fill(px - 1, py - 1, px + PW + 1, py + ph + 1, BORDER);
        g.fill(px, py, px + PW, py + ph, BG);
        g.drawString(font, "HUD 介面設定", px + 10, py + 7, ACCENT, false);
        g.fill(px + 8, py + 38, px + PW - 8, py + 39, BORDER);
        g.fill(px + 8, listBottom + 2, px + PW - 8, listBottom + 3, BORDER);
        for (Object[] t : texts) {
            String s = (String) t[2];
            int x = (int) t[0];
            if (t.length > 4) x += ((int) t[4] - font.width(s)) / 2; // 置中在指定寬度
            g.drawString(font, s, x, (int) t[1], (int) t[3], false);
        }
        int total = tab == 0 ? layoutRowCount() : otherRows + 1;
        if (total > visibleRows()) {
            // 捲軸
            int trackH = listBottom - listTop, barH = Math.max(10, trackH * visibleRows() / total);
            int barY = listTop + (trackH - barH) * scroll / Math.max(1, total - visibleRows());
            g.fill(px + PW - 4, listTop, px + PW - 2, listBottom, 0x40FFFFFF);
            g.fill(px + PW - 4, barY, px + PW - 2, barY + barH, ACCENT);
        }
        super.render(g, mouseX, mouseY, partial);

        // 下方操作說明（面板另一側的畫面底部）
        String help = "拖曳移動｜滾輪縮放｜右鍵顯示／隱藏｜方向鍵微調";
        int hx = panelRight ? 8 : width - font.width(help) - 8;
        g.drawString(font, help, hx, height - 12, DIM, true);
    }

    private void drawElement(GuiGraphics g, HudLayout.El e, boolean selectedNow, boolean hover) {
        HudLayout.Box b = HudLayout.box(e, width, height);
        boolean on = HudLayout.enabled(e);
        int fill = !on ? 0x30000000 : selectedNow ? 0x70202A3A : hover ? 0x60202A3A : 0x50101820;
        int edge = selectedNow ? ACCENT : hover ? 0xFFFFFFFF : on ? 0xA0A0A8B8 : 0x606A7384;
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), fill);
        g.renderOutline(b.x(), b.y(), b.w(), b.h(), edge);

        // 範例內容：以元件自己的縮放畫，大小跟真正的 HUD 一樣
        float s = HudLayout.scale(e);
        String[] lines = sample(e);
        g.enableScissor(b.x(), b.y(), b.x() + b.w(), b.y() + b.h());
        g.pose().pushPose();
        g.pose().translate(b.x(), b.y(), 0);
        g.pose().scale(s, s, 1);
        int innerW = Math.round(b.w() / s), innerH = Math.round(b.h() / s);
        int lh = 10, startY = Math.max(2, (innerH - lines.length * lh) / 2 + 1);
        int color = on ? 0xE0FFFFFF : 0x806A7384;
        for (int i = 0; i < lines.length; i++) {
            int tw = font.width(lines[i]);
            g.drawString(font, lines[i], (innerW - tw) / 2, startY + i * lh, color, false);
        }
        g.pose().popPose();
        g.disableScissor();

        // 名稱與大小（方框外面上方；放不下就放在方框裡的下方）
        if (selectedNow || hover || !on) {
            String tag = e.label + "  " + Math.round(s * 100) + "%" + (on ? "" : "（隱藏）");
            int ty = b.y() - 10 >= 0 ? b.y() - 10 : b.y() + b.h() + 2;
            int tx = Math.max(0, Math.min(width - font.width(tag), b.x()));
            g.fill(tx - 1, ty - 1, tx + font.width(tag) + 1, ty + 9, 0xC0000000);
            g.drawString(font, tag, tx, ty, selectedNow ? ACCENT : on ? TEXT : OFF, false);
        }
    }

    private static String[] sample(HudLayout.El e) {
        return switch (e) {
            case MINIMAP -> new String[] { "北", "", "小地圖", "" };
            case ZONE -> new String[] { "第 2/6 圈", "1:23 後縮圈" };
            case ALIVE -> new String[] { "存活 24", "隊伍 8" };
            case BR_FEED -> new String[] { "玩家A ▸ 玩家B", "玩家C 擊倒 玩家D", "☠ 玩家E" };
            case COMPASS -> new String[] { "西 ·  北  · 東", "▲ 360" };
            case OUTSIDE -> new String[] { "在安全區外 32 格" };
            case HINT -> new String[] { "扶起隊友 ■■■■□□", "按 空白鍵 打開降落傘" };
            case ARENA_SCORE -> new String[] { "紅 32    3:41    藍 28", "▬▬▬▬      ▬▬▬" };
            case KILL_FEED -> new String[] { "玩家A ⌐ 玩家B", "玩家C ⌐ 玩家D ◎" };
            case WALLET -> new String[] { "$1200    突擊兵", "擊殺 3　死亡 1", "[M] 打開商店" };
            case SCOREBOARD -> new String[] { "戰績      殺  死", "玩家A      5   2", "玩家B      3   4", "（按住 Caps Lock）" };
            case KILL_ICON -> new String[] { "╳", "擊殺" };
            case INTERACT -> new String[] { "F 開啟" };
            case TITLE -> new String[] { "上方標題（倒數、淘汰…）" };
            case SPECIAL -> new String[] { "[Q]", "▢" };
        };
    }
}
