package com.provinggrounds.ui;

import com.provinggrounds.network.AdminPanelPacket;
import com.provinggrounds.network.MapPickPacket;
import com.provinggrounds.network.MenuDataPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 檔案: AdminPanelScreen.java
 * 目的: 管理員控制台工具（右鍵開啟）。把常用地圖生成、光照修復、團隊地圖、方塊還原、重載等指令整合成一鍵按鈕。
 */
public class AdminPanelScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060,
        TEXT = 0xFFFFFFFF, DIM = 0xFFA0A8B8, BTN_H = 18;

    private final AdminPanelPacket info;
    private int selectedBrIdx;
    private int left, top, panelW, panelH;

    private AdminPanelScreen(AdminPanelPacket info) {
        super(Component.literal("PG 管理員控制台"));
        this.info = info;
    }

    public static void open(AdminPanelPacket info) {
        Minecraft.getInstance().setScreen(new AdminPanelScreen(info));
    }

    @Override
    protected void init() {
        panelW = Math.min(430, width - 20);
        panelH = Math.min(254, height - 20);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        int pad = 12;
        int colW = (panelW - pad * 4) / 3;
        int c1 = left + pad;
        int c2 = c1 + colW + pad;
        int c3 = c2 + colW + pad;

        // ── 第 1 欄：大逃殺地圖生成與光照 ──
        int y1 = top + 34;
        addBtn(c1, y1, colW, "一鍵生成 5 張 BR 地圖", true, () -> exec("br_gen_all", ""));
        y1 += 22;
        addBtn(c1, y1, colW, "原地升級重建 5 張圖", false, () -> exec("br_gen_regen", ""));
        y1 += 22;
        addBtn(c1, y1, colW, "修復 5 張圖地板陰影", true, () -> exec("br_relight", ""));
        y1 += 22;
        int hw = (colW - 4) / 2;
        addBtn(c1, y1, hw, "生成 300 格", false, () -> exec("br_gen_one", "300"));
        addBtn(c1 + hw + 4, y1, hw, "生成 400 格", false, () -> exec("br_gen_one", "400"));
        y1 += 20;
        addBtn(c1, y1, hw, "生成 500 格", false, () -> exec("br_gen_one", "500"));
        addBtn(c1 + hw + 4, y1, hw, "生成 600 格", false, () -> exec("br_gen_one", "600"));
        y1 += 20;
        addBtn(c1, y1, colW, "生成 700 格（萬象大陸）", false, () -> exec("br_gen_one", "700"));
        y1 += 22;
        addBtn(c1, y1, colW, "取消目前生成／修復任務", false, () -> exec("br_cancel", ""));

        // ── 第 2 欄：團隊室內地圖（黑曜地堡）＆俯視圖 ──
        int y2 = top + 34;
        addBtn(c2, y2, colW, "生成「黑曜地堡」團隊圖", true, () -> exec("arena_gen", ""));
        y2 += 22;
        addBtn(c2, y2, colW, "原地重建「黑曜地堡」", false, () -> exec("arena_regen", ""));
        y2 += 22;
        boolean hasBunker = info.arenaMaps().stream().anyMatch(m -> "obsidian_bunker".equals(m.id()));
        addBtn(c2, y2, colW, "切換場地：黑曜地堡", false, hasBunker, () -> exec("arena_select", "obsidian_bunker"));
        y2 += 22;
        addBtn(c2, y2, colW, "切換場地：經典場地", false, true, () -> exec("arena_select", "default"));
        y2 += 22;
        addBtn(c2, y2, colW, "傳送至目前團隊場地", false, () -> exec("arena_tp", info.activeArenaId()));
        y2 += 22;
        if (!info.brMaps().isEmpty()) {
            selectedBrIdx = Math.max(0, Math.min(selectedBrIdx, info.brMaps().size() - 1));
            MapPickPacket.Entry sel = info.brMaps().get(selectedBrIdx);
            String mLabel = "#" + sel.id() + " " + (sel.name().isBlank() ? "地圖" : sel.name()) + " ▾";
            addBtn(c2, y2, colW, mLabel, false, () -> {
                selectedBrIdx = (selectedBrIdx + 1) % info.brMaps().size();
                rebuildWidgets();
            });
            y2 += 20;
            addBtn(c2, y2, colW, "繪製選定地圖俯視圖", false, () -> exec("br_render", String.valueOf(sel.id())));
        } else {
            addBtn(c2, y2, colW, "尚未登記 BR 地圖", false, false, () -> {});
        }

        // ── 第 3 欄：對戰／方塊還原與系統管理 ──
        int y3 = top + 34;
        addBtn(c3, y3, colW, "立即還原比賽破壞方塊", true, () -> exec("glass_restore", ""));
        y3 += 22;
        addBtn(c3, y3, colW, "重置遊戲狀態回大廳", false, () -> exec("reset", ""));
        y3 += 22;
        addBtn(c3, y3, colW, "重載伺服器設定檔", false, () -> exec("reload", ""));
        y3 += 22;
        addBtn(c3, y3, colW, "查看模式與物資統計", false, () -> exec("status", ""));
        y3 += 22;
        addBtn(c3, y3, colW, "查看團隊場地詳情", false, () -> exec("arena_info", ""));
        y3 += 22;
        addBtn(c3, y3, colW, "領取全套管理工具", false, () -> exec("give_tools", ""));

        addRenderableWidget(new PGButton(left + panelW - 76, top + panelH - 24, 64, 16, Component.literal("關閉"), false, false, this::onClose));
    }

    private void addBtn(int x, int y, int w, String label, boolean accent, Runnable act) {
        addBtn(x, y, w, label, accent, true, act);
    }

    private void addBtn(int x, int y, int w, String label, boolean accent, boolean active, Runnable act) {
        PGButton b = new PGButton(x, y, w, BTN_H, Component.literal(label), accent, false, act);
        b.active = active;
        addRenderableWidget(b);
    }

    private void exec(String action, String arg) {
        PGNetwork.sendToServer(AdminPanelPacket.run(action, arg));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);
        g.drawString(font, "PG 管理員控制台", left + 12, top + 8, ACCENT, false);

        int pad = 12, colW = (panelW - pad * 4) / 3;
        g.drawString(font, "大逃殺生成／光照", left + pad, top + 22, DIM, false);
        g.drawString(font, "團隊場地／俯視圖", left + pad * 2 + colW, top + 22, DIM, false);
        g.drawString(font, "系統與還原指令", left + pad * 3 + colW * 2, top + 22, DIM, false);

        String activeLabel = info.arenaMaps().stream()
            .filter(a -> a.id().equals(info.activeArenaId()))
            .map(MenuDataPacket.ArenaMapEntry::name)
            .findFirst().orElse(info.activeArenaId());
        g.drawString(font, "團隊場地：" + activeLabel + "｜作業：" + info.jobStatus(), left + 12, top + panelH - 20, DIM, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
