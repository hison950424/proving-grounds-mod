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
                int idx = ((int) my - bodyTop - 4) / 30;
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
        // 左欄章節清單
        g.fill(lx - 1, bodyTop - 1, lx + leftColW + 1, bodyBottom + 1, BORDER);
        g.fill(lx, bodyTop, lx + leftColW, bodyBottom, PANEL);

        for (int i = 0; i < chapters.size(); i++) {
            GuideChapter ch = chapters.get(i);
            int ry = bodyTop + 4 + i * 30;
            boolean sel = i == chapterIdx;
            boolean hov = mouseX >= lx && mouseX < lx + leftColW && mouseY >= ry && mouseY < ry + 26;
            if (sel) {
                g.fill(lx + 2, ry, lx + leftColW - 2, ry + 26, ROW_SEL);
                g.fill(lx + 2, ry, lx + 4, ry + 26, ch.color());
            } else if (hov) {
                g.fill(lx + 2, ry, lx + leftColW - 2, ry + 26, ROW_HOV);
            }
            g.drawString(font, ch.title(), lx + 9, ry + 4, sel ? ACCENT : TEXT, false);
            g.drawString(font, ch.tag(), lx + 9, ry + 15, TEXT_DIM, false);
        }

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
        String menuKey = ClientModEvents.OPEN_MENU.getTranslatedKeyMessage().getString();
        String proneKey = ClientModEvents.PRONE.getTranslatedKeyMessage().getString();
        String quietKey = ClientMovement.QUIET_WALK.getTranslatedKeyMessage().getString();
        String sneakKey = Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage().getString();

        List<GuideChapter> list = new ArrayList<>();

        list.add(new GuideChapter("大逃殺流程", "跳傘・搜刮・縮圈・吃雞", 0xFFE8D060, List.of(
            new GuideCard("1. 準備與跳傘降落", "開局階段", List.of(
                "在大廳按 " + menuKey + " 開啟主選單並點擊「準備」，全員準備後自動倒數開局。",
                "登機後選擇落點跳傘，空中按方向鍵滑翔控制落點，接近地面按空白鍵展開降落傘安全落地。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.PARACHUTE.get()), "", 0xFF60C0FF),
            new GuideCard("2. 搜刮物資箱與背包管理", "裝備養成", List.of(
                "建築物與野區據點藏有普通／中階／高階與空投物資箱，靠近按 F 即可搜刮。",
                "取得一～三級背包會擴充可用欄位；在箱子或墓碑介面替換背包時，舊背包會自動放進對方介面中。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.BACKPACK_3.get()), "", 0xFFE8D060),
            new GuideCard("3. 安全區縮圈與大地圖", "戰術推進", List.of(
                "白圈為目前安全區（平滑收縮），藍色虛線為下一階段安全區，圈外會持續扣血。",
                "按大地圖鍵可檢視整張地圖、區域名稱、復活祭壇位置、空投與隊友標點。"
            ), VisualKind.CIRCLE_DIAGRAM, ItemStack.EMPTY, "", 0xFF60C0FF),
            new GuideCard("4. 倒地救援與祭壇復活", "團隊協作", List.of(
                "隊友倒地時靠近按 F 即可扶起；若隊友遭淘汰會掉落「復活牌」。",
                "撿起復活牌前往地圖上紫色十字標記的「復活祭壇」互動，就能讓隊友重新跳傘歸隊。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.ALTAR_ITEM.get()), "", 0xFFB070FF)
        )));

        list.add(new GuideChapter("團隊模式玩法", "死鬥・佔點・槍王", 0xFFFF7058, List.of(
            new GuideCard("團隊死鬥（TDM）", "率先達成擊殺目標", List.of(
                "紅藍兩隊對抗，率先達到目標擊殺數或時間結束時擊殺較高的一方獲勝。",
                "開局前可選職業：突擊兵擊殺回血、工兵擊殺刷新手榴彈；每累計 3 殺（或連死 3 次保底）獲得特殊道具。"
            ), VisualKind.TEAM_BADGE, ItemStack.EMPTY, "", 0xFFFF7058),
            new GuideCard("據點爭奪（DOM）", "搶佔 A / B / C 據點", List.of(
                "佔領場上 A、B、C 據點持續累積分數，率先達成目標分數獲勝。",
                "開啟特殊道具模式時，開局前每位玩家可挑選專屬特殊道具搭配職業打陣地戰。"
            ), VisualKind.ITEM, new ItemStack(Items.BEACON), "", 0xFF60C0FF),
            new GuideCard("槍王之王（GK）", "一路升級武器奪冠", List.of(
                "每擊殺一定數量自動切換下一把武器，一路完成全武器階梯即可獲勝。",
                "管理員可在大廳的「場地」選單自由切換經典場地或一鍵生成的「黑曜地堡」室內地圖。"
            ), VisualKind.ITEM, new ItemStack(Items.GOLDEN_SWORD), "", 0xFFE8D060)
        )));

        list.add(new GuideChapter("操作與快捷鍵", "移動・介面・輸入法", 0xFF58E098, List.of(
            new GuideCard("常用核心按鍵", "可於按鍵設定自訂", List.of(
                "主選單：" + menuKey + "｜靜走（壓低腳步聲）：" + quietKey + "｜蹲下：" + sneakKey + "｜趴下：" + proneKey,
                "開箱／扶隊友／祭壇互動：F｜使用特殊道具：Q｜標點：滑鼠中鍵或 X。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, menuKey, 0xFF58E098),
            new GuideCard("開啟介面時持續移動", "搜刮不怕變活靶", List.of(
                "開啟箱子、背包、選單或大地圖時，仍可用方向鍵移動、跳躍，也能直接按 " + quietKey + " 靜走、" + sneakKey + " 蹲下或按 " + proneKey + " 趴下。",
                "正在聊天或輸入框打字時會自動暫停接管，不影響輸入文字。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, proneKey, 0xFF60C0FF),
            new GuideCard("自動鎖定英文輸入法 & HUD 設定", "防誤觸保護", List.of(
                "Windows 系統下，非打字時會自動鎖定英文輸入法，按 Shift／Ctrl 不再誤觸中文輸入法（可在 client 設定關閉）。",
                "主選單「HUD 介面設定」可自由拖曳與縮放各 HUD 元件，左側設定面板也可隨時收起預覽。"
            ), VisualKind.KEY_BADGE, ItemStack.EMPTY, "EN", 0xFFE8D060)
        )));

        list.add(new GuideChapter("特殊道具機制", "62 種戰術技能", 0xFFD880FF, List.of(
            new GuideCard("專屬欄位與觸發方式", "預設按 Q 使用", List.of(
                "特殊道具只能放入物品欄上方的「特殊物品欄」（1 格），按 Q 鍵即時施放，用完即消耗。",
                "點擊本頁上方的「道具圖鑑」即可搜尋並檢視全部 62 種特殊道具的詳細效果！"
            ), VisualKind.ITEM, new ItemStack(SpecialRegistry.item(SpecialType.FLARE_GUN)), "", 0xFFD880FF),
            new GuideCard("進階特性標籤", "次數・反彈・領域", List.of(
                "【可用 2 次】臨時雪牆、彈簧鞋、殘影分身、所有地雷皆可使用兩次。",
                "【反彈牆壁】臨時雪牆、風暴彈、毒氣彈、定身球、磁人手榴彈撞牆可反彈 1 次，落地立即觸發。"
            ), VisualKind.ITEM, new ItemStack(SpecialRegistry.item(SpecialType.SNOW_WALL)), "", 0xFF80E0FF)
        )));

        list.add(new GuideChapter("管理員建圖工具", "一鍵建圖・進度條", 0xFFFFB040, List.of(
            new GuideCard("管理員控制台工具", "免背指令一鍵操作", List.of(
                "透過創造模式「Proving Grounds」分頁或 /pg give tool admin_panel 取得「管理員控制台」。",
                "右鍵即可一鍵生成 5 張大逃殺地圖、原地升級、修復陰影光照、生成黑曜地堡、重繪俯視圖或重載設定。"
            ), VisualKind.ITEM, new ItemStack(PGBlocks.ADMIN_TOOLS.get(AdminToolItem.Tool.ADMIN_PANEL).get()), "", 0xFFFFB040),
            new GuideCard("大地圖命名與背景進度條", "視覺化監控", List.of(
                "管理員在大地圖上按右鍵可傳送至指定座標，並可替大區域新增、修改或刪除區域名稱（亦可用 /pg map label）。",
                "所有非瞬間完成的建圖、光照修復、俯視圖繪製與掃描任務，畫面上方皆會即時顯示目前階段與進度條。"
            ), VisualKind.PROGRESS_BADGE, ItemStack.EMPTY, "", 0xFFE8D060)
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
