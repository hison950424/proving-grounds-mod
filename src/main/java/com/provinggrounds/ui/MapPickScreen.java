package com.provinggrounds.ui;

import com.provinggrounds.item.AdminToolItem;
import com.provinggrounds.network.MapPickPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 檔案: MapPickScreen.java
 * 目的: 箱子轉換工具、地圖繪畫工具的「選擇地圖」畫面（只在客戶端）。
 *   列出已登記的地圖（編號、名稱、目前生成點數），點一張地圖選取，按「確認」才執行，「取消」關閉。
 *   真正的權限檢查與執行在伺服器（AdminTools.runMapTool）。
 */
public class MapPickScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, DIM = 0xFFA0A8B8, SELECT = 0x60E8D060;
    private static final int ROW_H = 22;

    private final AdminToolItem.Tool tool;
    private final List<MapPickPacket.Entry> maps;
    private int selected = -1;
    private int left, top, w, h, rows, scroll;

    private MapPickScreen(AdminToolItem.Tool tool, List<MapPickPacket.Entry> maps) {
        super(Component.literal(tool == AdminToolItem.Tool.MAP_PAINTER ? "地圖繪畫" : "箱子轉換"));
        this.tool = tool;
        this.maps = List.copyOf(maps);
    }

    public static void open(AdminToolItem.Tool tool, List<MapPickPacket.Entry> maps) {
        Minecraft.getInstance().setScreen(new MapPickScreen(tool, maps));
    }

    @Override
    protected void init() {
        w = Math.min(300, width - 20);
        rows = Math.min(maps.size(), Math.max(1, (height - 110) / ROW_H));
        scroll = Math.max(0, Math.min(scroll, maps.size() - rows)); // 地圖太多放不下：滾輪捲動
        h = 52 + rows * ROW_H + 30;
        left = (width - w) / 2;
        top = (height - h) / 2;
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            MapPickPacket.Entry e = maps.get(idx);
            String label = "#" + e.id() + (e.name().isEmpty() ? "" : "  " + e.name()) + "（生成點 " + e.points() + " 個）";
            addRenderableWidget(new PGButton(left + 10, top + 44 + i * ROW_H, w - 20, 18, Component.literal(label), false, false,
                () -> selected = idx));
        }
        addRenderableWidget(new PGButton(left + w - 136, top + h - 24, 60, 16, Component.literal("確認"), true, false, this::confirm));
        addRenderableWidget(new PGButton(left + w - 70, top + h - 24, 60, 16, Component.literal("取消"), false, false, this::onClose));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int next = Math.max(0, Math.min(maps.size() - rows, scroll - (int) Math.signum(delta)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    private void confirm() {
        if (selected < 0) {
            PGButton.playDeny();
            return;
        }
        PGNetwork.sendToServer(MapPickPacket.choose(tool, maps.get(selected).id()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(left - 1, top - 1, left + w + 1, top + h + 1, BORDER);
        g.fill(left, top, left + w, top + h, BG);
        g.drawString(font, title.getString() + "：選擇地圖後按確認", left + 10, top + 8, ACCENT, false);
        String hint = tool == AdminToolItem.Tool.MAP_PAINTER
            ? "重新繪製這張地圖的大地圖／小地圖圖片（需要一點時間）"
            : "把這張地圖範圍內舊資料包的箱子標記轉換成生成點";
        g.drawString(font, hint, left + 10, top + 22, DIM, false);
        if (maps.size() > rows) {
            String more = (scroll + 1) + "～" + (scroll + rows) + "／" + maps.size() + "（滾輪捲動）";
            g.drawString(font, more, left + 10, top + h - 20, DIM, false);
        }
        if (selected >= scroll && selected < scroll + rows) {
            int y = top + 44 + (selected - scroll) * ROW_H;
            g.fill(left + 8, y - 2, left + w - 8, y + 20, SELECT);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
