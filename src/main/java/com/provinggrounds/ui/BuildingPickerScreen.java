package com.provinggrounds.ui;

import com.provinggrounds.admin.build.BuildingBlueprint;
import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.admin.build.BuildingStyle;
import com.provinggrounds.network.BuildingPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 檔案: BuildingPickerScreen.java
 * 目的: 「建築生成工具」的建築風格與款式選擇介面（只在客戶端）：
 *   - 上方 2 列 × 4 欄共 8 個風格分頁（現代軍事／工業倉庫／和風木造／廢土科幻／都市地標／歐式古城／沙漠戰區／海港極地）。
 *   - 左側建築清單（名稱、佔地尺寸、物資點數），點擊可即時切換右側預覽說明。
 *   - 底部「選取此建築」會寫入工具 NBT 並關閉選單；也提供「復原上一棟」按鈕一鍵還原。
 */
public final class BuildingPickerScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060,
        TEXT = 0xFFFFFFFF, DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, SEL = 0x45E8D060, HOVER = 0x24FFFFFF, CARD = 0xA010141C;
    private static final int ROW_H = 18;

    private BuildingStyle currentStyle;
    private String selectedId;
    private final int undoCount;

    private int panelW, panelH, left, top;
    private int listX, listY, listW, listH, scroll;
    private int infoX, infoY, infoW, infoH;
    private List<BuildingBlueprint> items = List.of();

    private BuildingPickerScreen(String initialId, int undoCount) {
        super(Component.literal("選擇建築款式（大逃殺地圖擴建）"));
        BuildingBlueprint initial = BuildingCatalog.byId(initialId);
        if (initial == null) initial = BuildingCatalog.first();
        this.currentStyle = initial.style();
        this.selectedId = initial.id();
        this.undoCount = undoCount;
    }

    public static void open(String initialId, int undoCount) {
        Minecraft.getInstance().setScreen(new BuildingPickerScreen(initialId, undoCount));
    }

    @Override
    protected void init() {
        panelW = Math.min(540, width - 16);
        panelH = Math.min(324, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        // 上方 2 列 × 4 欄共 8 個風格分頁按鈕
        BuildingStyle[] styles = BuildingStyle.values();
        int cols = 4;
        int tabGap = 4;
        int tabW = (panelW - 20 - tabGap * (cols - 1)) / cols;
        for (int i = 0; i < styles.length; i++) {
            BuildingStyle st = styles[i];
            int count = BuildingCatalog.byStyle(st).size();
            boolean active = st == currentStyle;
            int col = i % cols;
            int row = i / cols;
            int tabX = left + 10 + col * (tabW + tabGap);
            int tabY = top + 23 + row * 18;
            addRenderableWidget(new PGButton(
                tabX, tabY, tabW, 16,
                Component.literal(st.displayName + " (" + count + ")"),
                active, false,
                () -> switchStyle(st)
            ));
        }

        items = BuildingCatalog.byStyle(currentStyle);
        listX = left + 10;
        listY = top + 72;
        listW = (int) (panelW * 0.48) - 12;
        listH = panelH - 102;

        infoX = listX + listW + 10;
        infoY = listY;
        infoW = left + panelW - 10 - infoX;
        infoH = listH;

        int by = top + panelH - 24;
        addRenderableWidget(new PGButton(left + 10, by, 118, 16, Component.literal("✔ 選取此建築"), true, false, this::confirm));
        addRenderableWidget(new PGButton(left + 134, by, 126, 16,
            Component.literal("↺ 復原上一棟 (" + undoCount + ")"), false, false, this::undoLast));
        addRenderableWidget(new PGButton(left + panelW - 72, by, 62, 16, Component.literal("關閉"), false, false, this::onClose));
    }

    private void switchStyle(BuildingStyle st) {
        if (currentStyle == st) return;
        currentStyle = st;
        scroll = 0;
        List<BuildingBlueprint> list = BuildingCatalog.byStyle(st);
        if (!list.isEmpty()) selectedId = list.get(0).id();
        rebuildWidgets();
    }

    private void confirm() {
        PGNetwork.sendToServer(BuildingPacket.select(selectedId));
        onClose();
    }

    private void undoLast() {
        if (undoCount <= 0) {
            PGButton.playDeny();
            return;
        }
        PGNetwork.sendToServer(BuildingPacket.undo());
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);

        g.drawString(font, "建築生成工具 — 選擇風格與款式（共 " + BuildingCatalog.all().size() + " 棟）", left + 10, top + 8, ACCENT, false);
        g.fill(left + 10, top + 19, left + panelW - 10, top + 20, BORDER);

        g.drawString(font, currentStyle.displayName + "（" + currentStyle.mapHint + "）", listX, top + 61, currentStyle.color, false);
        g.drawString(font, "建築介紹", infoX, top + 61, DIM, false);

        renderList(g, mouseX, mouseY);
        renderDetail(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, CARD);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < items.size(); i++) {
            BuildingBlueprint bp = items.get(i);
            int ry = listY + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            boolean sel = bp.id().equals(selectedId);
            boolean over = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H
                && mouseY >= listY && mouseY < listY + listH;
            g.fill(listX + 1, ry + 1, listX + listW - 1, ry + ROW_H - 1, sel ? SEL : over ? HOVER : 0x12FFFFFF);
            if (sel) g.fill(listX + 1, ry + 1, listX + 3, ry + ROW_H - 1, ACCENT);
            String meta = bp.sizeText() + " · " + bp.lootCount() + "箱";
            int mw = font.width(meta);
            g.drawString(font, meta, listX + listW - 5 - mw, ry + 5, DIM, false);
            g.drawString(font, trim((sel ? "▸ " : "") + bp.name(), listW - mw - 14), listX + 5, ry + 5, sel ? ACCENT : TEXT, false);
        }
        g.disableScissor();
    }

    private void renderDetail(GuiGraphics g) {
        g.fill(infoX - 1, infoY - 1, infoX + infoW + 1, infoY + infoH + 1, BORDER);
        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, CARD);
        BuildingBlueprint bp = BuildingCatalog.byId(selectedId);
        if (bp == null) return;
        int x = infoX + 8, y = infoY + 8, maxW = infoW - 16;
        g.drawString(font, trim(bp.name(), maxW), x, y, ACCENT, false);
        y += 13;
        g.drawString(font, "風格：" + bp.style().displayName + "　｜　佔地：" + bp.sizeText(), x, y, bp.style().color, false);
        y += 12;
        g.drawString(font, "自動登記物資箱生成點：" + bp.lootCount() + " 個", x, y, GOOD, false);
        y += 13;
        g.fill(x, y, infoX + infoW - 8, y + 1, BORDER);
        y += 6;
        g.drawString(font, "建築構造與戰術特色：", x, y, DIM, false);
        y += 11;
        y = drawWrapped(g, bp.desc(), x, y, maxW, TEXT);
        y += 8;
        g.fill(x, y, infoX + infoW - 8, y + 1, BORDER);
        y += 6;
        y = drawWrapped(g, "操作說明：", x, y, maxW, ACCENT);
        y = drawWrapped(g, "• 手持工具會顯示青色 3D 佔地預覽框與金色正門方向。", x, y, maxW, DIM);
        y = drawWrapped(g, "• 右鍵直接放置（依你面對方向轉向、自動貼地補地基）。", x, y, maxW, DIM);
        drawWrapped(g, "• 放完記得對該地圖按「地圖繪畫工具」更新小地圖圖示。", x, y, maxW, DIM);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        for (var line : font.split(Component.literal(text), maxW)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + scroll) / ROW_H);
            if (idx >= 0 && idx < items.size()) {
                PGButton.playClick(1.0f);
                selectedId = items.get(idx).id();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int max = Math.max(0, items.size() * ROW_H - listH);
            scroll = Math.max(0, Math.min(max, scroll - (int) (delta * ROW_H * 2)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String trim(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
