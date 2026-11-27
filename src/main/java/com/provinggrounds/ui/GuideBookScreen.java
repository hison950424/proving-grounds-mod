package com.provinggrounds.ui;

import com.provinggrounds.item.AdminToolItem;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.SpecialRegistry;
import com.provinggrounds.special.SpecialType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 檔案: GuideBookScreen.java
 * 目的: 遊戲內「圖文教學＋道具圖鑑」互動介面。
 *   - 分頁 1（圖文教學）：5 大章節（大逃殺流程、團隊模式、戰術操作與快捷鍵、特殊道具機制、管理員工具），
 *     搭配真實物品圖示、按鍵徽章與戰術示意圖。
 *   - 分頁 2（道具圖鑑）：收錄本模組所有戰術裝備、方塊、62 種特殊道具與 11 種管理工具，
 *     支援分類篩選、中英文關鍵字搜尋與右側詳情卡片（放大圖示、取得來源、效果說明、使用提示）。
 */
public class GuideBookScreen extends Screen {

    private static final int BG         = 0xEB0F141E;
    private static final int PANEL      = 0xCC161E2C;
    private static final int CARD_BG    = 0xCC121926;
    private static final int BORDER     = 0xFF2A3344;
    private static final int ACCENT     = 0xFFE8D060;
    private static final int TEXT       = 0xFFEAECEF;
    private static final int TEXT_DIM   = 0xFF9AA4B4;
    private static final int ROW_SEL    = 0x45E8D060;
    private static final int ROW_HOV    = 0x26FFFFFF;

    private enum Tab { GUIDE, COMPENDIUM }

    private enum Category {
        ALL("全部", 0xFFE8D060),
        GEAR("裝備與方塊", 0xFF60C0FF),
        OFFENSE("進攻／陷阱", 0xFFFF6858),
        MOBILITY("偵查／機動", 0xFF58E098),
        DEFENSE("防禦／生存", 0xFF80C8FF),
        CHAOS("干擾／搞怪", 0xFFD880FF),
        ADMIN("管理工具", 0xFFFFB040);

        final String label;
        final int color;
        Category(String label, int color) { this.label = label; this.color = color; }
    }

    private enum VisualKind { ITEM, CIRCLE_DIAGRAM, KEY_BADGE, TEAM_BADGE, PROGRESS_BADGE }

    private record GuideCard(
        String title,
        String subtitle,
        List<String> lines,
        VisualKind visual,
        ItemStack icon,
        String badgeText,
        int accentColor
    ) {}

    private record GuideChapter(String title, String tag, int color, List<GuideCard> cards) {}

    private record ItemEntry(
        String id,
        String zhName,
        String enName,
        Category category,
        ItemStack icon,
        int color,
        String source,
        String desc,
        String tip,
        List<String> tags
    ) {}

    @Nullable private final Screen parent;
    private Tab tab = Tab.GUIDE;
    private Category category = Category.ALL;
    private int chapterIdx = 0;
    private int selectedItemIdx = 0;
    private int chapterScroll = 0;
    private int guideScroll = 0;
    private int listScroll = 0;
    private int detailScroll = 0;
    private String searchQuery = "";

    private int panelW, panelH, left, top;
    private int bodyTop, bodyBottom, leftColW, rightColX, rightColW;
    @Nullable private EditBox searchBox;

    private List<GuideChapter> chapters = List.of();
    private List<ItemEntry> allItems = List.of();
    private List<ItemEntry> filteredItems = List.of();

    public GuideBookScreen(@Nullable Screen parent) {
        super(Component.literal("圖文教學與道具圖鑑"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(540, width - 16);
        panelH = Math.min(340, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        if (allItems.isEmpty()) allItems = buildCompendium();
        chapters = buildChapters();
        rebuildFiltered();

        clearWidgets();

        // 頂部分頁與返回按鈕
        int tabY = top + 8;
        addRenderableWidget(new PGButton(left + 10, tabY, 102, 18, Component.literal("圖文教學"),
            tab == Tab.GUIDE, false, () -> {
                if (tab != Tab.GUIDE) {
                    tab = Tab.GUIDE;
                    guideScroll = 0;
                    init();
                }
            }));
        addRenderableWidget(new PGButton(left + 116, tabY, 126, 18,
            Component.literal("道具圖鑑 (" + allItems.size() + ")"),
            tab == Tab.COMPENDIUM, false, () -> {
                if (tab != Tab.COMPENDIUM) {
                    tab = Tab.COMPENDIUM;
                    listScroll = 0;
                    detailScroll = 0;
                    init();
                }
            }));
        addRenderableWidget(new PGButton(left + panelW - 66, tabY, 56, 18, Component.literal("返回"),
            false, false, this::onClose));

        searchBox = null;
        if (tab == Tab.GUIDE) {
            bodyTop = top + 34;
            bodyBottom = top + panelH - 10;
            leftColW = Math.min(138, panelW / 4 + 8);
            rightColX = left + 10 + leftColW + 8;
            rightColW = left + panelW - 10 - rightColX;
        } else {
            // 分類篩選列 + 搜尋框
            int catY = top + 32;
            int cx = left + 10;
            for (Category c : Category.values()) {
                int bw = Math.max(40, font.width(c.label) + 12);
                if (cx + bw > left + panelW - 118) break;
                addRenderableWidget(new PGButton(cx, catY, bw, 16, Component.literal(c.label),
                    category == c, false, () -> {
                        category = c;
                        listScroll = 0;
                        selectedItemIdx = 0;
                        detailScroll = 0;
                        rebuildFiltered();
                        init();
                    }));
                cx += bw + 4;
            }

            int sw = Math.max(84, left + panelW - 10 - Math.max(cx + 4, left + panelW - 114));
            int sx = left + panelW - 10 - sw;
            searchBox = new EditBox(font, sx, catY, sw, 16, Component.literal("搜尋道具"));
            searchBox.setMaxLength(24);
            searchBox.setValue(searchQuery);
            searchBox.setHint(Component.literal("搜尋名稱／效果…"));
            searchBox.setResponder(q -> {
                searchQuery = q;
                listScroll = 0;
                selectedItemIdx = 0;
                detailScroll = 0;
                rebuildFiltered();
            });
            addRenderableWidget(searchBox);

            bodyTop = top + 54;
            bodyBottom = top + panelH - 10;
            leftColW = Math.min(176, panelW / 3 + 10);
            rightColX = left + 10 + leftColW + 8;
            rightColW = left + panelW - 10 - rightColX;
        }
    }

    private void rebuildFiltered() {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<ItemEntry> out = new ArrayList<>();
        for (ItemEntry e : allItems) {
            if (category != Category.ALL && e.category() != category) continue;
            if (!q.isEmpty()) {
                boolean match = e.zhName().toLowerCase(Locale.ROOT).contains(q)
                    || e.enName().toLowerCase(Locale.ROOT).contains(q)
                    || e.desc().toLowerCase(Locale.ROOT).contains(q)
                    || e.source().toLowerCase(Locale.ROOT).contains(q)
                    || e.id().toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
            }
            out.add(e);
        }
        filteredItems = out;
        if (selectedItemIdx >= filteredItems.size()) selectedItemIdx = Math.max(0, filteredItems.size() - 1);
    }

    // ── 滑鼠與鍵盤互動 ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        int lx = left + 10;
        if (mx >= lx && mx < lx + leftColW && my >= bodyTop && my < bodyBottom) {
            if (tab == Tab.GUIDE) {
                int idx = ((int) my - bodyTop - 4 + chapterScroll) / 28;
                if (idx >= 0 && idx < chapters.size()) {
                    chapterIdx = idx;
                    guideScroll = 0;
                    PGButton.playClick(1.0f);
                    return true;
                }
            } else {
                int idx = ((int) my - bodyTop - 4 + listScroll) / 24;
                if (idx >= 0 && idx < filteredItems.size()) {
                    selectedItemIdx = idx;
                    detailScroll = 0;
                    PGButton.playClick(1.0f);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = delta > 0 ? -24 : 24;
        if (tab == Tab.GUIDE) {
            int lx = left + 10;
            if (mx >= lx && mx < lx + leftColW && my >= bodyTop && my < bodyBottom) {
                int maxChScroll = Math.max(0, chapters.size() * 28 - (bodyBottom - bodyTop - 8));
                chapterScroll = Math.max(0, Math.min(maxChScroll, chapterScroll + step));
                return true;
            }
            if (mx >= rightColX && mx < rightColX + rightColW && my >= bodyTop && my < bodyBottom) {
                guideScroll = Math.max(0, guideScroll + step);
                return true;
            }
        } else {
            int lx = left + 10;
            if (mx >= lx && mx < lx + leftColW && my >= bodyTop && my < bodyBottom) {
                int maxScroll = Math.max(0, filteredItems.size() * 24 - (bodyBottom - bodyTop - 8));
                listScroll = Math.max(0, Math.min(maxScroll, listScroll + step));
                return true;
            }
            if (mx >= rightColX && mx < rightColX + rightColW && my >= bodyTop && my < bodyBottom) {
                detailScroll = Math.max(0, detailScroll + step);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 主面板底框
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);
        g.fill(left, top + 29, left + panelW, top + 30, BORDER);

        if (tab == Tab.GUIDE) {
            renderGuide(g, mouseX, mouseY);
        } else {
            renderCompendium(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderGuide(GuiGraphics g, int mouseX, int mouseY) {
        int lx = left + 10;
        // 左欄章節清單（支援捲動）
        g.fill(lx - 1, bodyTop - 1, lx + leftColW + 1, bodyBottom + 1, BORDER);
        g.fill(lx, bodyTop, lx + leftColW, bodyBottom, PANEL);

        int chViewH = bodyBottom - bodyTop - 8;
        int maxChScroll = Math.max(0, chapters.size() * 28 - chViewH);
        if (chapterScroll > maxChScroll) chapterScroll = maxChScroll;

        g.enableScissor(lx + 1, bodyTop + 1, lx + leftColW - 1, bodyBottom - 1);
        for (int i = 0; i < chapters.size(); i++) {
            GuideChapter ch = chapters.get(i);
            int ry = bodyTop + 4 + i * 28 - chapterScroll;
            if (ry + 25 < bodyTop || ry > bodyBottom) continue;
            boolean sel = i == chapterIdx;
            boolean hov = mouseX >= lx && mouseX < lx + leftColW && mouseY >= ry && mouseY < ry + 25;
            if (sel) {
                g.fill(lx + 2, ry, lx + leftColW - 2, ry + 25, ROW_SEL);
                g.fill(lx + 2, ry, lx + 4, ry + 25, ch.color());
            } else if (hov) {
                g.fill(lx + 2, ry, lx + leftColW - 2, ry + 25, ROW_HOV);
            }
            g.drawString(font, ch.title(), lx + 8, ry + 3, sel ? ACCENT : TEXT, false);
            g.drawString(font, ch.tag(), lx + 8, ry + 14, TEXT_DIM, false);
        }
        g.disableScissor();
        drawScrollbar(g, lx + leftColW - 4, bodyTop + 3, bodyBottom - bodyTop - 6, chapterScroll, maxChScroll);

        // 右欄圖文卡片區
        g.fill(rightColX - 1, bodyTop - 1, rightColX + rightColW + 1, bodyBottom + 1, BORDER);
        g.fill(rightColX, bodyTop, rightColX + rightColW, bodyBottom, PANEL);

        GuideChapter cur = chapters.get(Math.max(0, Math.min(chapterIdx, chapters.size() - 1)));
        int viewH = bodyBottom - bodyTop - 8;
        int contentW = rightColW - 16;

        // 預先計算總高度以限制捲動
        int totalH = 22;
        for (GuideCard card : cur.cards()) {
            totalH += measureCardHeight(card, contentW) + 8;
        }
        int maxScroll = Math.max(0, totalH - viewH);
        if (guideScroll > maxScroll) guideScroll = maxScroll;

        g.enableScissor(rightColX + 1, bodyTop + 1, rightColX + rightColW - 1, bodyBottom - 1);
        int y = bodyTop + 6 - guideScroll;
        g.drawString(font, cur.title() + " — " + cur.tag(), rightColX + 8, y, cur.color(), false);
        y += 16;

        for (GuideCard card : cur.cards()) {
            int ch = measureCardHeight(card, contentW);
            renderGuideCard(g, card, rightColX + 6, y, contentW, ch);
            y += ch + 8;
        }
        g.disableScissor();

        drawScrollbar(g, rightColX + rightColW - 4, bodyTop + 3, bodyBottom - bodyTop - 6, guideScroll, maxScroll);
    }

    private int measureCardHeight(GuideCard card, int cardW) {
        int textW = Math.max(80, cardW - 56);
        int linesH = 0;
        for (String line : card.lines()) {
            linesH += font.split(Component.literal(line), textW).size() * 11 + 2;
        }
        return Math.max(52, 24 + linesH + 6);
    }

    private void renderGuideCard(GuiGraphics g, GuideCard card, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, CARD_BG);
        g.fill(x, y, x + 2, y + h, card.accentColor());
        g.fill(x, y + h - 1, x + w, y + h, 0x40FFFFFF);

        // 左側 36×36 圖示框
        int ix = x + 8, iy = y + 8;
        g.fill(ix - 1, iy - 1, ix + 37, iy + 37, BORDER);
        g.fill(ix, iy, ix + 36, iy + 36, 0xFF0C111A);

        switch (card.visual()) {
            case ITEM -> {
                g.pose().pushPose();
                g.pose().translate(ix + 2, iy + 2, 0);
                g.pose().scale(2.0f, 2.0f, 1.0f);
                g.renderItem(card.icon(), 0, 0);
                g.pose().popPose();
            }
            case CIRCLE_DIAGRAM -> {
                // 示意白圈＋藍虛線下一圈
                drawRing(g, ix + 18, iy + 18, 14, 0xFFFFFFFF);
                drawRing(g, ix + 20, iy + 16, 8, 0xFF60C0FF);
                g.fill(ix + 19, iy + 15, ix + 21, iy + 17, 0xFF3CE05A);
            }
            case KEY_BADGE -> {
                g.fill(ix + 4, iy + 8, ix + 32, iy + 28, 0xFF232C3D);
                g.fill(ix + 4, iy + 26, ix + 32, iy + 28, card.accentColor());
                int tw = font.width(card.badgeText());
                g.drawString(font, card.badgeText(), ix + 18 - tw / 2, iy + 14, ACCENT, false);
            }
            case TEAM_BADGE -> {
                g.fill(ix + 4, iy + 6, ix + 17, iy + 30, 0xCCCF3A3A);
                g.fill(ix + 19, iy + 6, ix + 32, iy + 30, 0xCC3A78CF);
                g.drawString(font, "VS", ix + 12, iy + 14, 0xFFFFFFFF, true);
            }
            case PROGRESS_BADGE -> {
                g.fill(ix + 4, iy + 14, ix + 32, iy + 22, 0xFF202A3A);
                g.fill(ix + 5, iy + 15, ix + 25, iy + 21, 0xFFE8D060);
                g.drawString(font, "72%", ix + 9, iy + 4, TEXT, false);
            }
        }

        // 右側文字
        int tx = x + 52;
        int textW = Math.max(80, w - 58);
        g.drawString(font, card.title(), tx, y + 6, card.accentColor(), false);
        if (!card.subtitle().isEmpty()) {
            int sw = font.width(card.subtitle());
            g.drawString(font, card.subtitle(), x + w - 8 - sw, y + 6, TEXT_DIM, false);
        }
        int ty = y + 20;
        for (String line : card.lines()) {
            for (FormattedCharSequence seq : font.split(Component.literal(line), textW)) {
                g.drawString(font, seq, tx, ty, TEXT, false);
                ty += 11;
            }
            ty += 2;
        }
    }

    private static void drawRing(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int a = 0; a < 36; a++) {
            double rad = a * Math.PI * 2.0 / 36.0;
            int px = cx + (int) Math.round(Math.cos(rad) * r);
            int py = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void renderCompendium(GuiGraphics g, int mouseX, int mouseY) {
        int lx = left + 10;
        int viewH = bodyBottom - bodyTop - 8;

        // 左欄道具清單
        g.fill(lx - 1, bodyTop - 1, lx + leftColW + 1, bodyBottom + 1, BORDER);
        g.fill(lx, bodyTop, lx + leftColW, bodyBottom, PANEL);

        int maxListScroll = Math.max(0, filteredItems.size() * 24 - viewH);
        if (listScroll > maxListScroll) listScroll = maxListScroll;

        g.enableScissor(lx + 1, bodyTop + 1, lx + leftColW - 1, bodyBottom - 1);
        if (filteredItems.isEmpty()) {
            g.drawString(font, "找不到符合條件的道具", lx + 10, bodyTop + 12, TEXT_DIM, false);
        } else {
            for (int i = 0; i < filteredItems.size(); i++) {
                int ry = bodyTop + 4 + i * 24 - listScroll;
                if (ry + 24 < bodyTop || ry > bodyBottom) continue;
                ItemEntry e = filteredItems.get(i);
                boolean sel = i == selectedItemIdx;
                boolean hov = mouseX >= lx && mouseX < lx + leftColW && mouseY >= ry && mouseY < ry + 22;
                if (sel) {
                    g.fill(lx + 2, ry, lx + leftColW - 6, ry + 22, ROW_SEL);
                    g.fill(lx + 2, ry, lx + 4, ry + 22, e.category().color);
                } else if (hov) {
                    g.fill(lx + 2, ry, lx + leftColW - 6, ry + 22, ROW_HOV);
                }
                g.renderItem(e.icon(), lx + 6, ry + 3);
                String name = font.plainSubstrByWidth(e.zhName(), leftColW - 34);
                g.drawString(font, name, lx + 26, ry + 3, sel ? ACCENT : TEXT, false);
                g.drawString(font, e.category().label, lx + 26, ry + 13, e.category().color, false);
            }
        }
        g.disableScissor();
        drawScrollbar(g, lx + leftColW - 4, bodyTop + 3, bodyBottom - bodyTop - 6, listScroll, maxListScroll);

        // 右欄詳情卡片
        g.fill(rightColX - 1, bodyTop - 1, rightColX + rightColW + 1, bodyBottom + 1, BORDER);
        g.fill(rightColX, bodyTop, rightColX + rightColW, bodyBottom, PANEL);

        if (filteredItems.isEmpty()) return;
        ItemEntry item = filteredItems.get(Math.max(0, Math.min(selectedItemIdx, filteredItems.size() - 1)));
        int pad = 10;
        int innerW = rightColW - pad * 2 - 4;

        // 計算內容高度
        int contentH = 62;
        contentH += 14 + font.split(Component.literal(item.source()), innerW).size() * 11 + 8;
        contentH += 14 + font.split(Component.literal(item.desc()), innerW).size() * 11 + 8;
        if (!item.tip().isEmpty()) {
            contentH += 14 + font.split(Component.literal(item.tip()), innerW).size() * 11 + 8;
        }
        int maxDetailScroll = Math.max(0, contentH - viewH);
        if (detailScroll > maxDetailScroll) detailScroll = maxDetailScroll;

        g.enableScissor(rightColX + 1, bodyTop + 1, rightColX + rightColW - 1, bodyBottom - 1);
        int x = rightColX + pad;
        int y = bodyTop + pad - detailScroll;

        // 放大 2 倍圖示框
        g.fill(x - 1, y - 1, x + 41, y + 41, item.category().color);
        g.fill(x, y, x + 40, y + 40, 0xFF0C111A);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.renderItem(item.icon(), 0, 0);
        g.pose().popPose();

        // 名稱與標籤
        int nx = x + 48;
        g.drawString(font, item.zhName(), nx, y + 2, ACCENT, false);
        g.drawString(font, item.enName(), nx, y + 14, TEXT_DIM, false);

        int tagX = nx;
        int tagY = y + 26;
        for (String tg : item.tags()) {
            int tw = font.width(tg) + 8;
            if (tagX + tw > rightColX + rightColW - 8) break;
            g.fill(tagX, tagY, tagX + tw, tagY + 13, 0x35FFFFFF);
            g.drawString(font, tg, tagX + 4, tagY + 3, item.category().color, false);
            tagX += tw + 4;
        }

        y += 50;
        g.fill(x, y, x + innerW, y + 1, BORDER);
        y += 8;

        y = drawDetailSection(g, x, y, innerW, "取得方式", Component.literal(item.source()), 0xFF60C0FF, TEXT);
        y = drawDetailSection(g, x, y, innerW, "效果與功能（綠＝正面｜黃＝異常｜紅＝負面）",
            com.provinggrounds.special.SpecialItem.coloredDesc(item.desc()), ACCENT, TEXT);
        if (!item.tip().isEmpty()) {
            drawDetailSection(g, x, y, innerW, "戰術提示", Component.literal(item.tip()), 0xFF58E098, TEXT);
        }

        g.disableScissor();
        drawScrollbar(g, rightColX + rightColW - 4, bodyTop + 3, bodyBottom - bodyTop - 6, detailScroll, maxDetailScroll);
    }

    private int drawDetailSection(GuiGraphics g, int x, int y, int w, String header, Component body, int headerColor, int fallbackColor) {
        g.drawString(font, "▸ " + header, x, y, headerColor, false);
        y += 13;
        for (FormattedCharSequence seq : font.split(body, w)) {
            g.drawString(font, seq, x + 4, y, fallbackColor, false);
            y += 11;
        }
        return y + 8;
    }

    private static void drawScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll) {
        if (maxScroll <= 0 || h <= 10) return;
        g.fill(x, y, x + 2, y + h, 0x30FFFFFF);
        int thumbH = Math.max(16, (int) ((long) h * h / (h + maxScroll)));
        int thumbY = y + (int) ((long) (h - thumbH) * scroll / maxScroll);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, ACCENT);
    }

    // ── 資料建構：圖文教學 ──────────────────────────────────────────────────

    private List<GuideChapter> buildChapters() {
        var opts = Minecraft.getInstance().options;
        String menuKey = ClientModEvents.OPEN_MENU.getTranslatedKeyMessage().getString();
        String proneKey = ClientModEvents.PRONE.getTranslatedKeyMessage().getString();
        String quietKey = ClientMovement.QUIET_WALK.getTranslatedKeyMessage().getString();
        String sneakKey = opts.keyShift.getTranslatedKeyMessage().getString();
        String jumpKey = opts.keyJump.getTranslatedKeyMessage().getString();
        String invKey = opts.keyInventory.getTranslatedKeyMessage().getString();
        String dropKey = opts.keyDrop.getTranslatedKeyMessage().getString();
        String interactKey = ClientInteract.INTERACT.getTranslatedKeyMessage().getString();
        String specialKey = com.provinggrounds.special.client.ClientSpecial.USE_KEY.getTranslatedKeyMessage().getString();
        String pingKey = ClientPing.KEY.getTranslatedKeyMessage().getString();
        String boardKey = ScoreboardPanel.KEY.getTranslatedKeyMessage().getString();
        String playerListKey = opts.keyPlayerList.getTranslatedKeyMessage().getString();

        List<GuideChapter> list = new ArrayList<>();

        // 1. 按鍵總覽：移動・姿態・爬牆・跳傘
        list.add(new GuideChapter("1. 移動與姿態按鍵", "移動・爬牆・跳傘・電梯", 0xFF58E098, List.of(
            new GuideCard("基礎移動與自動奔跑", "不需再按疾跑鍵", List.of(
                "• W / A / S / D：前後左右移動；按住 W 往前會自動奔跑，開鏡／開槍／靜走／蹲下／趴下時自動暫停奔跑。",
                "• " + quietKey + "（靜走）：按住放慢腳步並大幅降低腳步聲，適合近距離摸點。",
                "• " + sneakKey + "（蹲下）：壓低身形、縮小受彈面積並穩定瞄準。",
                "• " + proneKey + "（趴下）：切換趴地姿勢（極低輪廓與命中框），再按一次或跳躍即可起身。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, quietKey, 0xFF58E098),
            new GuideCard("翻牆攀爬（大逃殺限定）", "精準 1.5 ～ 3.0 格", List.of(
                "• 跳躍（" + jumpKey + "）離地後，面對前方 1.5 ～ 3.0 格高的牆面或平台再按一次 " + jumpKey + "，即可雙手撐起翻上頂部。",
                "• 1 格高方塊不會誤觸發攀爬（直接跳上去即可）；柵欄與石牆（含兩層堆疊）碰撞高度皆為精確每格 1 格。",
                "• 攀爬中途按 S / A / D 可隨時取消並向該方向退開，結束後會自動重新拔出原本主手武器。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, jumpKey + "×2", 0xFF60C0FF),
            new GuideCard("跳傘、電梯與平滑斜坡", "地形快速移動", List.of(
                "• 跳傘（" + jumpKey + "）：登機鎖定結束後按 " + jumpKey + " 跳出飛機並進入鞘翅滑翔；空中再按 " + jumpKey + " 開傘／收傘，落地自動收起。",
                "• 電梯方塊：站上電梯按 " + jumpKey + " 傳送至正上方電梯，按 " + sneakKey + " 傳送至正下方電梯。",
                "• 樓梯與半磚：正放樓梯、半磚與相鄰 1 格實心方塊皆具備平滑斜坡碰撞箱，直接平順走上不抖動不卡牆。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.PARACHUTE.get()), "", 0xFFE8D060)
        )));

        // 2. 按鍵總覽：戰鬥・槍械・快捷欄
        list.add(new GuideChapter("2. 戰鬥與槍械按鍵", "射擊・改裝・專用格", 0xFFFF7058, List.of(
            new GuideCard("射擊、開鏡與戰術動作", "奔跑即時開火", List.of(
                "• 滑鼠左鍵（開火）：奔跑中按左鍵可零延遲立即開火，並瞬間將槍口由奔跑歪斜姿勢擺正朝前。",
                "• 滑鼠右鍵（開鏡瞄準）：奔跑中開鏡會強制切換為走路模式以確保射擊穩定。",
                "• R（換彈）｜B（切換射擊模式：單發／連發／全自動）｜V（切換倍鏡倍率）。",
                "• Z（槍械改裝）：開啟配件介面裝卸瞄具／彈匣／槍口等配件；Y（槍托近戰）；" + dropKey + "（丟棄手上物品）。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, "R / Z", 0xFFFF7058),
            new GuideCard("快捷欄 1 ～ 9 專用格規則", "防誤放＋自動補位", List.of(
                "• 數字鍵 1、2：主副槍械專用格（所有槍械含手槍皆放這兩格，最多攜帶 2 把槍）。",
                "• 數字鍵 3：近戰武器專用格（匕首／戰術刀等，拔刀移動速度更快）。",
                "• 數字鍵 4（補品）／ 5（投擲物）：專屬快捷格，用完自動從背包補位；長按 4 或 5 可開啟輪盤快速切換種類。",
                "• 數字鍵 6 ～ 9：一般物資格（跳傘期間第 9 格固定為降落傘）；背包滿時按 Z 卸配件絕不會塞進第 1～5 專用格。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, "1 - 9", 0xFFE8D060),
            new GuideCard("互動鍵（" + interactKey + "）與特殊道具（" + specialKey + "）", "開箱・救援・技能", List.of(
                "• " + interactKey + "（互動鍵）：準星指著物資箱開箱、對倒地隊友扶起、對復活祭壇復活隊友。",
                "• 團隊模式倒地放棄：在團隊死鬥／佔點倒地時，長按 " + interactKey + " 3 秒可選擇直接放棄死亡並進入重生。",
                "• " + specialKey + "（使用特殊道具）：即時施放「特殊物品欄」（物品欄上方獨立 1 格）內的戰術技能道具。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, interactKey + " / " + specialKey, 0xFFD880FF)
        )));

        // 3. 按鍵總覽：介面・地圖・溝通・防誤觸
        list.add(new GuideChapter("3. 介面與資訊按鍵", "選單・地圖・標點・HUD", 0xFF60C0FF, List.of(
            new GuideCard("選單、背包與戰績畫面", "核心介面入口", List.of(
                "• " + menuKey + "（主選單／商店）：大廳時開啟模式與分隊選單；團隊死鬥／佔點位於基地內時開啟武器防具商店。",
                "• " + invKey + "（物品欄／背包）：檢視背包欄位、裝備護甲與上方「特殊物品欄」，支援 Shift 快速搬移。",
                "• " + boardKey + "（戰績面板）：查看本場各隊存活／擊殺／助攻／經濟或陣亡統計。",
                "• " + playerListKey + "（玩家列表）：原版線上玩家名單；輸入 /pg rank 可隨時查詢自己的四種競技排位段位。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, menuKey, 0xFF60C0FF),
            new GuideCard("大地圖與戰術標點", "團隊情報共享", List.of(
                "• 大地圖（大逃殺內按大地圖鍵或主選單預覽）：檢視整張地圖、區域名稱、白圈／藍圈、紫色復活祭壇與空投落點。",
                "• " + pingKey + " 或 滑鼠中鍵（戰術標點）：在準星指向位置建立標記，隊友畫面與大地圖會同步顯示方向與距離。"
            ), VisualKind.CIRCLE_DIAGRAM, ItemStack.EMPTY, "", 0xFF60C0FF),
            new GuideCard("介面中移動、輸入法鎖定與 HUD", "操作手感保護", List.of(
                "• 開啟箱子、背包、選單或大地圖時，仍可用 WASD、" + jumpKey + "、" + quietKey + "、" + sneakKey + "、" + proneKey + " 持續移動閃避（打字時自動暫停）。",
                "• Windows 非打字狀態自動鎖定英文輸入法，按 Shift／Ctrl 不再誤切中文輸入法（可於客戶端設定關閉）。",
                "• 主選單「HUD 介面設定」可自由拖曳與縮放各 HUD 元件位置與大小。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, "EN", 0xFFE8D060)
        )));

        // 4. 大逃殺玩法（一般／競技排位）
        list.add(new GuideChapter("4. 大逃殺與競技排位", "一般・競技・段位積分", 0xFFE8D060, List.of(
            new GuideCard("標準流程：跳傘 → 搜刮 → 縮圈 → 吃雞", "基本規則", List.of(
                "• 大廳按 " + menuKey + " 點「準備」，全員準備後進入倒數登機；跳傘後搜刮普通／中階／高階／空投物資箱。",
                "• 升級 1～3 級背包可解鎖更多物品欄；物資箱被破壞或炸毀時不會掉落箱內物品。玻璃可被子彈或近戰打破。",
                "• 白圈為目前安全區，藍虛線為下一圈，圈外持續扣毒傷；最後存活的隊伍／玩家獲勝。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.BACKPACK_3.get()), "", 0xFFE8D060),
            new GuideCard("倒地救援與復活祭壇", "50 HP・0.5 HP/秒", List.of(
                "• 隊伍尚有存活成員時受致命傷會進入倒地（50 HP、每秒流血 0.5 HP），隊友靠近按 " + interactKey + " 即可扶起。",
                "• 淘汰後現場掉落「復活牌」（不佔背包格），隊友撿起後帶至大地圖紫色十字「復活祭壇」按 " + interactKey + " 即可空降歸隊。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.ALTAR_ITEM.get()), "", 0xFFB070FF),
            new GuideCard("競技模式規則鎖定與四種排位", "單／雙／三／四排獨立積分", List.of(
                "• 競技模式固定：慢速縮圈、友軍誤傷開、全部爆炸破壞方塊、僅出純戰術型特殊道具（排除整人與過誇張道具）、每人限復活 1 次。",
                "• 嚴格滿員開局：單排禁組隊；雙／三／四排每隊必須剛好滿 2／3／4 人；孤狼死亡或全隊淘汰後直接返大廳（防報點）。",
                "• 7 大段位（青銅～戰神，含 1 場降段保護）：≥4 隊為 100% 正常積分，3 隊 75%、2 隊 50%；輸入 /pg rank 查看四排位戰績。"
            ), VisualKind.TEAM_BADGE, ItemStack.EMPTY, "", 0xFFFF7058)
        )));

        // 5. 團隊模式玩法（死鬥・佔點・槍王）
        list.add(new GuideChapter("5. 團隊模式玩法", "死鬥・佔點・槍王之王", 0xFFFF9048, List.of(
            new GuideCard("團隊死鬥（TDM）", "20% 前／30% 中／50% 後", List.of(
                "• 紅藍兩隊對抗，率先達標擊殺數或時間結束分高者勝；階段倍率為 ×1（前期 20%）→ ×2（中期 30%）→ ×4（後期 50%）。",
                "• 擊殺、助攻（50%）、擊倒（25%）皆有金錢收入；特殊道具改為死亡重生送 1 個，擊殺充能 1 格／助攻 0.5 格滿 2 格再送 1 個。",
                "• 玻璃可被打破，每塊玻璃於 10 秒後獨立自動復原；敵方重生保護區在二樓也可看到紅色邊界牆。"
            ), VisualKind.TEAM_BADGE, ItemStack.EMPTY, "", 0xFFFF7058),
            new GuideCard("據點爭奪（DOM）", "回合制搶佔 A / B / C", List.of(
                "• 每回合準備期於基地按 " + menuKey + " 購買裝備，開戰後搶佔開放據點或殲滅敵隊得分；死亡清空身上裝備（保留整場特殊道具）。",
                "• 擊殺 $60／助攻 $30／擊倒 $15／首殺 +$120／站點每秒 +$5，搭配回合勝敗與連敗補償金錢；被打破的玻璃每回合自動復原。"
            ), VisualKind.ITEM, new ItemStack(Items.BEACON), "", 0xFF60C0FF),
            new GuideCard("槍王之王（GK）與倒地放棄", "武器階梯＋長按 " + interactKey, List.of(
                "• 槍王之王：累計擊殺自動切換下一階段武器，率先走完所有武器階梯奪冠。",
                "• 團隊模式開啟倒地時，倒地血量 50 HP（0.5 HP/秒），可長按 " + interactKey + " 3 秒放棄並直接死亡進入重生（擊殺仍計給擊倒者）。"
            ), VisualKind.ITEM, new ItemStack(Items.GOLDEN_SWORD), "", 0xFFE8D060)
        )));

        // 6. 團隊九大職業詳解
        list.add(new GuideChapter("6. 團隊九大職業", "前排・輸出・偵查・輔助", 0xFF80C8FF, List.of(
            new GuideCard("全能與突擊前排", "自由人・突擊兵・重裝兵", List.of(
                "• 自由人（100 HP）：商店 9 折、全槍械解鎖，配裝最彈性。",
                "• 突擊兵（90 HP）：移動速度 +12%、擊殺回復 20 HP（限步槍／手槍）。",
                "• 重裝兵（125 HP）：正面受傷害 -15%、移動速度 -10%（限機槍／散彈／手槍）。"
            ), VisualKind.ITEM, new ItemStack(Items.DIAMOND_CHESTPLATE), "", 0xFF60C0FF),
            new GuideCard("精準、偵查與突襲", "狙擊手・偵察兵・刺客", List.of(
                "• 狙擊手（80 HP）：擊殺／助攻／擊倒獲得金錢 +30%，快速發育買高階狙擊與倍鏡（限狙擊／手槍）。",
                "• 偵察兵（100 HP）：移速 +10%、擊中敵人發光 0.5 秒、每 15 秒自動發光最近敵人 1 秒（限步槍／衝鋒／手槍）。",
                "• 刺客（70 HP）：拔刀移速 +30%、背刺傷害 ×3、走路聲音 -50%（限衝鋒／手槍，自帶匕首）。"
            ), VisualKind.ITEM, new ItemStack(Items.SPYGLASS), "", 0xFF58E098),
            new GuideCard("爆破、醫療與補給", "工兵・醫護兵・支援兵", List.of(
                "• 工兵（100 HP）：自帶雙雷且擊殺刷新，爆炸傷害 +20%、受炸傷害 -50%（限重武／散彈／手槍）。",
                "• 醫護兵（100 HP）：6 格光環每秒回隊友 2 HP、用補品快 25% 且 6 格隊友共享 50% 回血、扶人 2 秒（限衝鋒／散彈／手槍）。",
                "• 支援兵（100 HP）：投擲物上限 4 個（其餘職業 3 個）、重生送 1 雷、陣亡清空；每 20 秒自補 1 雷＋1 補品，6 格隊友補 1 雷或 1 補品。"
            ), VisualKind.ITEM, new ItemStack(Items.GOLDEN_APPLE), "", 0xFFE8D060)
        )));

        // 7. 特殊道具、護甲與槍械射程
        list.add(new GuideChapter("7. 道具・護甲・射程", "62 種技能・抗彈・32 區塊", 0xFFD880FF, List.of(
            new GuideCard("特殊道具與特性標籤", "預設按 " + specialKey + " 施放", List.of(
                "• 特殊道具專用「特殊物品欄」（1 格），按 " + specialKey + " 即時施放；點擊本頁上方「道具圖鑑」可搜尋全部 62 種道具。",
                "• 【可用 2 次】雪牆、彈簧鞋、殘影分身、所有地雷皆可用兩次；【反彈牆壁】雪牆／風暴彈／毒氣彈／定身球／磁人雷撞牆反彈 1 次。",
                "• 一般大逃殺開放全道具池，競技大逃殺僅出純戰術型道具池。"
            ), VisualKind.ITEM, new ItemStack(SpecialRegistry.item(SpecialType.FLARE_GUN)), "", 0xFFD880FF),
            new GuideCard("護甲抗彈率（v5 平衡）", "中高階甲差距明確", List.of(
                "• 皮革／海龜甲：8%｜鎖鏈／金甲：12%｜鐵甲：18%｜鑽石甲：28%｜獄髓甲：36%。",
                "• 擊倒／擊殺／助攻皆有專屬不同樣式的戰術旗幟與高亮音效，方便即時判斷戰果。"
            ), VisualKind.ITEM, new ItemStack(Items.NETHERITE_CHESTPLATE), "", 0xFF80C8FF),
            new GuideCard("遠距視野與各槍種射程", "雙端 32 區塊（512 格）", List.of(
                "• 伺服器與客戶端玩家及子彈追蹤渲染上限鎖定為 32 區塊（512 格），搭配原生距離傷害衰減與下墜。",
                "• 最遠射程：狙擊槍／重武器 512 格｜機槍 256 格｜步槍 220 格｜衝鋒槍 120 格｜手槍 80 格｜散彈槍 48 格。"
            ), VisualKind.ITEM, new ItemStack(Items.BOW), "", 0xFFFFB040)
        )));

        // 8. 管理員建圖與維護工具
        list.add(new GuideChapter("8. 管理員工具與指令", "控制台・場地・維護", 0xFFFFB040, List.of(
            new GuideCard("管理員控制台工具", "免背指令一鍵操作", List.of(
                "• 透過創造模式「Proving Grounds」分頁或 /pg give tool admin_panel 取得「管理員控制台」。",
                "• 右鍵開啟 4 分頁面板：大逃殺地圖（新建／分區重製／獨立光照修復／獨立無形方塊修復）、團隊場地、建圖工具與系統維護。",
                "• 底部常駐「取消目前作業」按鈕，可隨時中止背景建圖、光照或掃描任務。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.ADMIN_TOOLS.get(AdminToolItem.Tool.ADMIN_PANEL).get()), "", 0xFFFFB040),
            new GuideCard("團隊場地與紅藍重生區自訂", "圓形／四邊獨立方形", List.of(
                "• 控制台「團隊場地」分頁可切換經典場地、一鍵生成或重建「黑曜地堡」室內地圖。",
                "• 支援每張地圖獨立設定重生保護形狀：圓形（半徑）或方形（紅隊／藍隊各自獨立調整北／南／西／東四邊，附東西／南北鏡像按鈕）。"
            ), VisualKind.PROGRESS_BADGE, ItemStack.EMPTY, "", 0xFFE8D060),
            new GuideCard("常用管理與查詢指令", "快速參考", List.of(
                "• /pg rank [玩家]：查詢單／雙／三／四排四種競技排位段位與積分。",
                "• /pg result：重新開啟最近一場大逃殺的完整結算數據。",
                "• /pg map label：新增／修改／刪除大地圖區域名稱；大地圖上管理員按右鍵可直接傳送。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, "/pg", 0xFF60C0FF)
        )));

        return list;
    }

    // ── 資料建構：道具圖鑑 ──────────────────────────────────────────────────

    private static List<ItemEntry> buildCompendium() {
        List<ItemEntry> out = new ArrayList<>();

        // 1. 基礎戰術裝備與方塊
        out.add(new ItemEntry("backpack_1", "一級背包", "Level 1 Backpack", Category.GEAR,
            new ItemStack(PGBlocks.BACKPACK_1.get()), 0xFF60C0FF,
            "大逃殺各類物資箱（普通～高階）",
            "提供基礎額外背包欄位容量，讓你攜帶更多彈藥、醫療品與配件。",
            "撿到更高等級背包時會自動替換；在其他介面替換時舊背包會放回該介面。",
            List.of("擴充容量", "大逃殺")));
        out.add(new ItemEntry("backpack_2", "二級背包", "Level 2 Backpack", Category.GEAR,
            new ItemStack(PGBlocks.BACKPACK_2.get()), 0xFF60C0FF,
            "大逃殺中階／高階物資箱",
            "提供中階背包容量，兼顧多把武器備彈與投擲物攜帶需求。",
            "建議中期前務必升級至二級以上背包。",
            List.of("擴充容量", "中階物資")));
        out.add(new ItemEntry("backpack_3", "三級背包", "Level 3 Backpack", Category.GEAR,
            new ItemStack(PGBlocks.BACKPACK_3.get()), 0xFFD880FF,
            "大逃殺高階物資箱／空投箱",
            "最高容量戰術背包，完整解鎖所有物品欄格位。",
            "決賽圈必備，可囤積足夠煙霧彈、手榴彈與醫療包。",
            List.of("最大容量", "高階物資")));
        out.add(new ItemEntry("parachute", "降落傘", "Parachute", Category.GEAR,
            new ItemStack(PGBlocks.PARACHUTE.get()), 0xFF60C0FF,
            "大逃殺開局跳傘／重新跳傘器自動配發",
            "跳傘期間固定於快捷欄第 9 格，按空白鍵展開傘衣減緩墜落並滑翔降落。",
            "接近屋頂或目標區域時提早開傘可精準落點。",
            List.of("跳傘", "自動配發")));
        out.add(new ItemEntry("revive_card", "復活牌", "Revive Card", Category.GEAR,
            new ItemStack(PGBlocks.REVIVE_CARD.get()), 0xFFB070FF,
            "大逃殺隊友遭淘汰後於現場掉落",
            "撿起後自動記錄於身上（不佔背包格），帶至任一座復活祭壇互動即可復活該隊友。",
            "復活牌不會被敵人搶走，但請留意祭壇附近的埋伏。",
            List.of("團隊復活", "不佔格子")));
        out.add(new ItemEntry("altar", "復活祭壇", "Revival Altar", Category.GEAR,
            new ItemStack(PGBlocks.ALTAR_ITEM.get()), 0xFFB070FF,
            "散佈於大逃殺地圖各區（大地圖與預覽圖以紫色十字標示）",
            "攜帶隊友復活牌對祭壇按 F 互動，即可讓隊友從高空重新跳傘返場；使用後進入冷卻。",
            "大廳預覽地圖也能先看祭壇分布，規劃開局跳傘路線！",
            List.of("復活點", "地圖可見")));
        out.add(new ItemEntry("crate_low", "普通物資箱", "Common Crate", Category.GEAR,
            new ItemStack(PGBlocks.CRATE_LOW.get()), 0xFFA07040,
            "大逃殺地圖各建築與野區",
            "基礎物資箱，內含常見槍械、彈藥、基礎防具與醫療品。",
            "按 F 快速開啟；小心別開到外觀相似的「假的普通箱」！",
            List.of("物資箱", "基礎")));
        out.add(new ItemEntry("crate_mid", "中階物資箱", "Mid-Tier Crate", Category.GEAR,
            new ItemStack(PGBlocks.CRATE_MID.get()), 0xFF8090A0,
            "大逃殺城區建築與小聚落",
            "中階物資箱，較容易開出步槍、倍鏡、二級防具與特殊道具。",
            "大型建築的二樓與倉庫常見中階箱。",
            List.of("物資箱", "中階")));
        out.add(new ItemEntry("crate_high", "高階物資箱", "High-Tier Crate", Category.GEAR,
            new ItemStack(PGBlocks.CRATE_HIGH.get()), 0xFFE0B030,
            "大逃殺大城區核心建築",
            "高階物資箱，高機率產出強力武器、三級裝備與稀有特殊道具。",
            "通常位於熱門大城區，開箱前先確認周圍腳步聲。",
            List.of("物資箱", "高階")));
        out.add(new ItemEntry("crate_airdrop", "空投物資箱", "Airdrop Crate", Category.GEAR,
            new ItemStack(PGBlocks.CRATE_AIRDROP.get()), 0xFFFFD040,
            "對局定時空投／使用信號槍召喚",
            "頂級物資箱，落地時有顯眼標記並顯示於大地圖，內含頂配武器與高階防具。",
            "先丟煙霧彈掩護再舔空投最安全。",
            List.of("空投", "頂級物資")));
        out.add(new ItemEntry("elevator", "電梯方塊", "Elevator Block", Category.GEAR,
            new ItemStack(PGBlocks.ELEVATOR_ITEM.get()), 0xFF90A8C0,
            "地圖內高樓或建築配置",
            "站在電梯方塊上按跳躍鍵可傳送至正上方電梯，按蹲下鍵可傳送至正下方電梯。",
            "上下樓移動迅速，但落地可能遇到守點敵人。",
            List.of("垂直移動", "機關方塊")));
        out.add(new ItemEntry("stats_board", "數據看板", "Stats Board", Category.GEAR,
            new ItemStack(PGBlocks.STATS_BOARD_ITEM.get()), 0xFFE8D060,
            "大廳展示（管理員放置）",
            "即時顯示伺服器玩家的擊殺、勝場、等級與戰績排行。",
            "只有 OP 或創造模式可以放置與破壞。",
            List.of("戰績展示", "大廳方塊")));

        // 2. 62 種特殊道具（直接從 SpecialType 動態讀取，永不過時）
        for (SpecialType t : SpecialType.values()) {
            Category cat = classifySpecial(t);
            List<String> tags = new ArrayList<>();
            tags.add("按 Q 使用");
            if (t.twoUses()) tags.add("可用 2 次");
            if (t.isBounceBall()) tags.add("可反彈牆壁");
            if (t.isMine()) tags.add("地雷陷阱");
            if (t.isFakeCrate()) tags.add("偽裝陷阱");

            String tip = buildSpecialTip(t);
            out.add(new ItemEntry(
                t.id,
                t.zh,
                t.en,
                cat,
                new ItemStack(SpecialRegistry.item(t)),
                0xFF000000 | t.color,
                "大逃殺物資箱／空投｜團隊死鬥（每 3 殺或 3 連死保底）｜佔點模式開局自選｜槍王擊殺掉落",
                t.desc,
                tip,
                tags
            ));
        }

        // 3. 擊退盾（手持型態）
        out.add(new ItemEntry("knockback_shield_held", "擊退盾（舉盾型態）", "Knockback Shield (Held)", Category.DEFENSE,
            new ItemStack(SpecialRegistry.SHIELD_HELD.get()), 0xFF80C8FF,
            "使用「擊退盾」特殊道具後自動裝備於主手",
            "耐久 500 的重型戰術盾牌，可格擋正面火力；再次按 Q 向前衝撞釋放衝擊波。",
            "狹窄走道（如黑曜地堡）突破防線非常強勢。",
            List.of("格擋", "衝撞")));

        // 4. 11 種管理員工具（直接從 AdminToolItem.Tool 動態讀取）
        for (AdminToolItem.Tool tool : AdminToolItem.Tool.values()) {
            ItemStack stack = new ItemStack(PGBlocks.ADMIN_TOOLS.get(tool).get());
            String zh = stack.getHoverName().getString();
            out.add(new ItemEntry(
                tool.id,
                zh,
                tool.id,
                Category.ADMIN,
                stack,
                0xFFFFB040,
                "創造模式「Proving Grounds」分頁｜/pg give tool " + tool.id,
                tool.hint,
                "僅限 OP 管理員或創造模式使用，不會出現在戰利品箱中。",
                List.of("OP 專用", "右鍵使用")
            ));
        }

        return out;
    }

    private static Category classifySpecial(SpecialType t) {
        return switch (t) {
            case BLIND_ORB, KNOCKBACK_SHIELD, WIND_BOMB, FAKE_CRATE_LOW, FAKE_CRATE_MID, FAKE_CRATE_HIGH,
                 RECON_MINE, LAND_MINE, FIRE_MINE, SHOCK_MINE, GAS_GRENADE, ROOT_ORB, FIRE_DRILL,
                 THOR_HAMMER, THOR_HAMMER_PLUS, MAGNET_GRENADE -> Category.OFFENSE;
            case ADRENALINE, INVIS_CLOAK, SPRING_SHOES, ENEMY_SCANNER_BASIC, ENEMY_SCANNER, ENEMY_SCANNER_PLUS,
                 BLAST_SCANNER_BASIC, BLAST_SCANNER, BLAST_SCANNER_PLUS, WARP_PEARL, SILENT_BOOTS,
                 RADAR_PULSE, DECOY, RECALL, RECALL_PLUS, RESKYDIVE, RESKYDIVE_GROUP, RESKYDIVE_TARGET,
                 ETHEREAL -> Category.MOBILITY;
            case SNOW_WALL, FLARE_GUN, SMOKE_BURST, TOTEM, SHIELD_BUBBLE, FIRST_AID_SHOT,
                 ARMOR_REPAIR, ARMOR_REPAIR_PLUS, ZONE_SHELTER, MIMIC -> Category.DEFENSE;
            default -> Category.CHAOS;
        };
    }

    private static String buildSpecialTip(SpecialType t) {
        if (t.isMine()) return "可放置於門廊、樓梯轉角或物資箱旁，共可使用 2 次。";
        if (t.isFakeCrate()) return "放在熱門房區或空投附近最容易誘敵上鉤。";
        if (t.isBounceBall()) return "善用牆壁反彈 1 次的特性，不用露頭就能把球丟進轉角或房間內！";
        if (t == SpecialType.FIRST_AID_SHOT) return "集「瞬扶隊友、倒地自救、即時補血」三合一，建議留到關鍵交火時機。";
        if (t == SpecialType.TOTEM || t == SpecialType.SHIELD_BUBBLE) return "開戰前或進房衝鋒前先開啟，可大幅提高對槍容錯率。";
        if (t == SpecialType.RECALL || t == SpecialType.RECALL_PLUS) return "先在安全掩體按下記錄，再大膽探點或偷襲，20 秒內可隨時按 Q 閃回。";
        if (t == SpecialType.FLARE_GUN) return "召喚空投會廣播通知全場，建議選有掩體的地形再發射。";
        if (t == SpecialType.DOMAIN_EXPANSION) return "展開後 15 秒內無法出入，記得提前確認自己帶了近戰武器！";
        if (t == SpecialType.ETHEREAL) return "可直接穿過厚度 1 格的牆壁撤退或繞背，但持續 5 秒內無法攻擊。";
        return "放入物品欄上方的特殊物品欄後，隨時按 Q 鍵即可觸發。";
    }
}
