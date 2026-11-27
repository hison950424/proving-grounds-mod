package com.provinggrounds.ui;

import com.provinggrounds.data.BRMapGenerator;
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
 * 目的: 管理員控制台工具（右鍵開啟）。採三頁籤清晰分區：
 *   - 分頁 1「大逃殺地圖」：左側單選指定地圖（或全部 5 張），右側自由勾選重製區域（大區域／小區域／野區／小掩體）
 *     並提供先刪舊區域再重製、光照修復、重繪俯視圖與單張／全套新建地圖。
 *   - 分頁 2「團隊場地」：場地切換、生成／重建「黑曜地堡」、傳送與場地詳情。
 *   - 分頁 3「系統與工具」：方塊還原、重置回大廳、取消背景作業、重載設定、狀態查詢與領取全套管理工具。
 */
public class AdminPanelScreen extends Screen {

    private static final int BG       = 0xEB0F141E;
    private static final int PANEL    = 0xCC161E2C;
    private static final int BORDER   = 0xFF2A3344;
    private static final int ACCENT   = 0xFFE8D060;
    private static final int TEXT     = 0xFFEAECEF;
    private static final int DIM      = 0xFF9AA4B4;
    private static final int ROW_SEL  = 0x45E8D060;
    private static final int ROW_HOV  = 0x24FFFFFF;

    private enum Tab { BR_MAP, ARENA, TOOLS, SYSTEM }

    private final AdminPanelPacket info;
    private Tab tab = Tab.BR_MAP;
    /** -1 = 全部 5 張預設地圖；>= 0 = info.brMaps() 索引。 */
    private int selectedMapIdx;
    private int mapScroll = 0;
    private long deleteConfirmUntil = 0L;

    private boolean layerMajor = true;
    private boolean layerSmall = true;
    private boolean layerWild  = true;
    private boolean layerCover = true;

    private boolean campSquare;
    private int campRadius = 25;
    private boolean campEditBlue = false;
    /** 方形保護四邊長度（順序：北 -Z、南 +Z、西 -X、東 +X）。 */
    private final int[] campRed = { 25, 25, 25, 25 };
    private final int[] campBlue = { 25, 25, 25, 25 };

    private int left, top, panelW, panelH;
    private int bodyTop, bodyBottom, listX, listW, rightX, rightW;

    private AdminPanelScreen(AdminPanelPacket info) {
        super(Component.literal("PG 管理員控制台"));
        this.info = info;
        this.selectedMapIdx = info.brMaps().isEmpty() ? -1 : 0;
        // 解析目前啟用團隊場地的重生保護設定（"<c|s>:<radius>:<rN,rS,rW,rE>:<bN,bS,bW,bE>"）
        if (info.arg() != null && info.arg().contains(":")) {
            String[] p = info.arg().split(":");
            String sh = p[0].trim();
            this.campSquare = "s".equalsIgnoreCase(sh) || "square".equalsIgnoreCase(sh);
            try { this.campRadius = Math.max(2, Math.min(128, Integer.parseInt(p[1].trim()))); } catch (NumberFormatException ignored) {}
            parseSideCsv(p.length >= 3 ? p[2] : "", this.campRed, this.campRadius);
            parseSideCsv(p.length >= 4 ? p[3] : "", this.campBlue, this.campRadius);
        }
    }

    private static void parseSideCsv(String csv, int[] dst, int fallback) {
        int fb = Math.max(1, Math.min(128, fallback));
        for (int i = 0; i < 4; i++) dst[i] = fb;
        if (csv == null || csv.isBlank()) return;
        String[] s = csv.split(",");
        if (s.length < 4) return;
        for (int i = 0; i < 4; i++) {
            try { dst[i] = Math.max(1, Math.min(128, Integer.parseInt(s[i].trim()))); } catch (NumberFormatException ignored) {}
        }
    }

    private static String encodeSideCsv(int[] a) {
        return a[0] + "," + a[1] + "," + a[2] + "," + a[3];
    }

    public static void open(AdminPanelPacket info) {
        Minecraft.getInstance().setScreen(new AdminPanelScreen(info));
    }

    @Override
    protected void init() {
        panelW = Math.min(470, width - 16);
        panelH = Math.min(314, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        bodyTop = top + 34;
        bodyBottom = top + panelH - 28;
        listX = left + 10;
        listW = Math.min(156, panelW / 3);
        rightX = listX + listW + 10;
        rightW = left + panelW - 10 - rightX;

        clearWidgets();

        // 頂部 4 個分頁按鈕 + 關閉
        int tabY = top + 8;
        addRenderableWidget(new PGButton(left + 10, tabY, 88, 18, Component.literal("大逃殺地圖"),
            tab == Tab.BR_MAP, false, () -> switchTab(Tab.BR_MAP)));
        addRenderableWidget(new PGButton(left + 102, tabY, 76, 18, Component.literal("團隊場地"),
            tab == Tab.ARENA, false, () -> switchTab(Tab.ARENA)));
        addRenderableWidget(new PGButton(left + 182, tabY, 76, 18, Component.literal("建圖工具"),
            tab == Tab.TOOLS, false, () -> switchTab(Tab.TOOLS)));
        addRenderableWidget(new PGButton(left + 262, tabY, 76, 18, Component.literal("系統維護"),
            tab == Tab.SYSTEM, false, () -> switchTab(Tab.SYSTEM)));
        addRenderableWidget(new PGButton(left + panelW - 64, tabY, 54, 18, Component.literal("關閉"),
            false, false, this::onClose));

        // 底部狀態列右側常駐「取消目前作業」按鈕（所有分頁皆可直接一鍵中止背景任務）
        boolean hasJob = info.jobStatus() != null && !info.jobStatus().isBlank() && !info.jobStatus().contains("閒置");
        addBtn(left + panelW - 96, bodyBottom + 7, 86, 16, "取消目前作業", hasJob, hasJob, () -> exec("br_cancel", ""));

        switch (tab) {
            case BR_MAP -> initBrTab();
            case ARENA -> initArenaTab();
            case TOOLS -> initToolsTab();
            case SYSTEM -> initSystemTab();
        }
    }

    private void switchTab(Tab next) {
        if (tab == next) return;
        tab = next;
        init();
    }

    // ── 分頁 1：大逃殺地圖 ──────────────────────────────────────────────────

    private void initBrTab() {
        int x = rightX + 8;
        int w = rightW - 16;
        int hw = (w - 6) / 2;

        // 1. 區域勾選（2×2 整齊排列）
        int y = bodyTop + 20;
        addToggle(x, y, hw, "大區域", layerMajor, () -> { layerMajor = !layerMajor; init(); });
        addToggle(x + hw + 6, y, hw, "小區域", layerSmall, () -> { layerSmall = !layerSmall; init(); });
        y += 22;
        addToggle(x, y, hw, "野區微據點", layerWild, () -> { layerWild = !layerWild; init(); });
        addToggle(x + hw + 6, y, hw, "小掩體", layerCover, () -> { layerCover = !layerCover; init(); });

        // 2. 重製操作（先刪舊區域再重做）
        y += 38;
        int mask = currentMask();
        boolean hasSelection = mask != 0 && (!info.brMaps().isEmpty());
        String targetArg = selectedMapIdx < 0
            ? "all:" + mask
            : info.brMaps().get(Math.min(selectedMapIdx, info.brMaps().size() - 1)).id() + ":" + mask;
        String regenLabel = selectedMapIdx < 0
            ? "重製全部 5 張圖（先刪後建）"
            : "重製 #" + currentMapId() + " 選定區域（先刪後建）";
        addBtn(x, y, w, 20, regenLabel, true, hasSelection, () -> exec("br_regen_layers", targetArg));

        y += 22;
        int tw = (w - 8) / 3;
        String relightAction = selectedMapIdx < 0 ? "br_relight" : "br_relight_one";
        String relightArg = selectedMapIdx < 0 ? "" : String.valueOf(currentMapId());
        addBtn(x, y, tw, 18, selectedMapIdx < 0 ? "修復全部光照" : "修復光照", false, !info.brMaps().isEmpty(),
            () -> exec(relightAction, relightArg));
        addBtn(x + tw + 4, y, tw, 18, "修復無形方塊", false, true,
            () -> exec("sync_chunks", selectedMapIdx < 0 ? "" : String.valueOf(currentMapId())));
        addBtn(x + (tw + 4) * 2, y, w - (tw + 4) * 2, 18, "重繪俯視圖", false, selectedMapIdx >= 0 && !info.brMaps().isEmpty(),
            () -> exec("br_render", String.valueOf(currentMapId())));

        y += 22;
        boolean hasSingleMap = selectedMapIdx >= 0 && !info.brMaps().isEmpty();
        addBtn(x, y, tw, 18, "查看大地圖", false, hasSingleMap, () -> {
            int id = currentMapId();
            PGNetwork.sendToServer(AdminPanelPacket.run("br_preview", String.valueOf(id)));
            Minecraft.getInstance().setScreen(new BigMapScreen(id));
        });
        addBtn(x + tw + 4, y, tw, 18, "傳送至此圖", false, hasSingleMap,
            () -> exec("br_tp", String.valueOf(currentMapId())));
        boolean confirming = hasSingleMap && System.currentTimeMillis() < deleteConfirmUntil;
        String delLabel = !hasSingleMap ? "刪除地圖"
            : confirming ? "⚠ 確認刪除"
            : "刪除 #" + currentMapId();
        addBtn(x + (tw + 4) * 2, y, w - (tw + 4) * 2, 18, delLabel, confirming, hasSingleMap, () -> {
            if (System.currentTimeMillis() < deleteConfirmUntil) {
                deleteConfirmUntil = 0L;
                exec("br_delete", String.valueOf(currentMapId()));
            } else {
                deleteConfirmUntil = System.currentTimeMillis() + 4000L;
                init();
            }
        });

        // 3. 新建地圖區
        y += 34;
        addBtn(x, y, w, 18, "一鍵新建全部 5 張大逃殺地圖", false, true, () -> exec("br_gen_all", ""));
        y += 22;
        int bw = (w - 16) / 5;
        String[] sizes = { "300", "400", "500", "600", "700" };
        for (int i = 0; i < sizes.length; i++) {
            String sz = sizes[i];
            addBtn(x + i * (bw + 4), y, bw, 16, sz + "格", false, true, () -> exec("br_gen_one", sz));
        }
    }

    // ── 分頁 2：團隊場地 ────────────────────────────────────────────────────

    private void initArenaTab() {
        int x = left + 18;
        int w = panelW - 36;
        int hw = (w - 8) / 2;

        // 場地切換列
        int y = bodyTop + 34;
        int col = 0;
        for (MenuDataPacket.ArenaMapEntry am : info.arenaMaps()) {
            boolean isActive = am.id().equals(info.activeArenaId());
            int bx = x + (col % 2) * (hw + 8);
            int by = y + (col / 2) * 24;
            addBtn(bx, by, hw, 20, (isActive ? "● " : "") + am.name(), isActive, !isActive,
                () -> exec("arena_select", am.id()));
            col++;
        }

        // 操作按鈕
        int rows = Math.max(1, (info.arenaMaps().size() + 1) / 2);
        int ay = y + rows * 24 + 18;
        addBtn(x, ay, hw, 18, "生成「黑曜地堡」室內地圖", true, true, () -> exec("arena_gen", ""));
        addBtn(x + hw + 8, ay, hw, 18, "原地重建「黑曜地堡」", false, true, () -> exec("arena_regen", ""));
        ay += 22;
        addBtn(x, ay, hw, 18, "傳送至目前團隊場地", false, true, () -> exec("arena_tp", info.activeArenaId()));
        addBtn(x + hw + 8, ay, hw, 18, "查看目前場地詳情", false, true, () -> exec("arena_info", ""));

        // 自訂目前場地重生點保護形狀與大小（圓形為半徑；方形支援紅／藍隊各自獨立調整北、南、西、東四邊）
        int cy = ay + 34;
        Runnable applyCamp = () -> exec("arena_camp",
            info.activeArenaId() + ":" + (campSquare ? "s" : "c") + ":" + campRadius
                + ":" + encodeSideCsv(campRed) + ":" + encodeSideCsv(campBlue));

        if (!campSquare) {
            int shapeW = 92;
            int stepW = 30;
            int applyW = 72;
            addBtn(x, cy, shapeW, 18, "形狀：圓形", false, true, () -> { campSquare = true; init(); });
            int sx = x + shapeW + 6;
            addBtn(sx, cy, stepW, 18, "-5", false, campRadius > 2, () -> { campRadius = Math.max(2, campRadius - 5); init(); });
            addBtn(sx + stepW + 3, cy, stepW, 18, "-1", false, campRadius > 2, () -> { campRadius = Math.max(2, campRadius - 1); init(); });
            int valW = Math.max(46, w - (shapeW + 6 + (stepW + 3) * 4 + applyW + 6));
            int vx = sx + (stepW + 3) * 2;
            addBtn(vx, cy, valW, 18, campRadius + " 格", false, false, () -> {});
            int px = vx + valW + 3;
            addBtn(px, cy, stepW, 18, "+1", false, campRadius < 128, () -> { campRadius = Math.min(128, campRadius + 1); init(); });
            addBtn(px + stepW + 3, cy, stepW, 18, "+5", false, campRadius < 128, () -> { campRadius = Math.min(128, campRadius + 5); init(); });
            addBtn(x + w - applyW, cy, applyW, 18, "套用設定", true, true, applyCamp);
        } else {
            int shapeW = 68;
            int teamW = 74;
            int mirW = 98;
            int applyW = Math.max(66, w - (shapeW + 4 + teamW + 4 + mirW + 4 + mirW + 4));
            String otherName = campEditBlue ? "紅" : "藍";
            int[] cur = campEditBlue ? campBlue : campRed;
            int[] other = campEditBlue ? campRed : campBlue;

            addBtn(x, cy, shapeW, 18, "形狀：方形", true, true, () -> { campSquare = false; init(); });
            addBtn(x + shapeW + 4, cy, teamW, 18, campEditBlue ? "編輯：藍隊" : "編輯：紅隊", true, true, () -> {
                campEditBlue = !campEditBlue;
                init();
            });
            addBtn(x + shapeW + 4 + teamW + 4, cy, mirW, 18, "東西鏡像→" + otherName, false, true, () -> {
                other[0] = cur[0]; other[1] = cur[1]; other[2] = cur[3]; other[3] = cur[2];
                init();
            });
            addBtn(x + shapeW + 4 + teamW + 4 + mirW + 4, cy, mirW, 18, "南北鏡像→" + otherName, false, true, () -> {
                other[0] = cur[1]; other[1] = cur[0]; other[2] = cur[2]; other[3] = cur[3];
                init();
            });
            addBtn(x + w - applyW, cy, applyW, 18, "套用設定", true, true, applyCamp);

            // 2×2 四邊微調（北 -Z、南 +Z、西 -X、東 +X）
            String[] sideNames = { "北 -Z", "南 +Z", "西 -X", "東 +X" };
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                int cx = x + (i % 2) * (hw + 8);
                int ry = cy + 22 + (i / 2) * 22;
                int val = cur[idx];
                int lblW = 46, b5 = 26, b1 = 24;
                int vw = Math.max(38, hw - (lblW + 3 + b5 + 2 + b1 + 2 + b1 + 2 + b5 + 2));
                addBtn(cx, ry, lblW, 18, sideNames[idx], false, false, () -> {});
                int bx = cx + lblW + 3;
                addBtn(bx, ry, b5, 18, "-5", false, val > 1, () -> { cur[idx] = Math.max(1, cur[idx] - 5); init(); });
                bx += b5 + 2;
                addBtn(bx, ry, b1, 18, "-1", false, val > 1, () -> { cur[idx] = Math.max(1, cur[idx] - 1); init(); });
                bx += b1 + 2;
                addBtn(bx, ry, vw, 18, val + "格", false, false, () -> {});
                bx += vw + 2;
                addBtn(bx, ry, b1, 18, "+1", false, val < 128, () -> { cur[idx] = Math.min(128, cur[idx] + 1); init(); });
                bx += b1 + 2;
                addBtn(bx, ry, b5, 18, "+5", false, val < 128, () -> { cur[idx] = Math.min(128, cur[idx] + 5); init(); });
            }
        }
    }

    // ── 分頁 3：建圖工具（整合全部既有管理工具）────────────────────────────

    private void initToolsTab() {
        int x = left + 18;
        int w = panelW - 36;
        int tw = (w - 12) / 3;
        int hw = (w - 8) / 2;

        // 1. 浮空文字
        int y = bodyTop + 22;
        addBtn(x, y, tw, 20, "新增浮空文字", true, true, () -> exec("tool_float_text", ""));
        addBtn(x + tw + 6, y, tw, 20, "刪除最近文字", false, true, () -> exec("tool_text_eraser", ""));
        addBtn(x + (tw + 6) * 2, y, tw, 20, "復原刪除文字", false, true, () -> exec("tool_text_undo", ""));

        // 2. 物資生成點與地圖
        y += 42;
        addBtn(x, y, tw, 20, "顯示／隱藏物資點", true, true, () -> exec("tool_spawn_viewer", ""));
        addBtn(x + tw + 6, y, tw, 20, "轉換舊箱子標記", false, true, () -> exec("tool_crate_converter", ""));
        addBtn(x + (tw + 6) * 2, y, tw, 20, "繪製地圖俯視圖", false, true, () -> exec("tool_map_painter", ""));
        y += 24;
        addBtn(x, y, hw, 18, "領取：物資點放置工具", false, true, () -> exec("tool_give_one", "spawn_placer"));
        addBtn(x + hw + 8, y, hw, 18, "領取：物資點移除工具", false, true, () -> exec("tool_give_one", "spawn_remover"));

        // 3. 建築藍圖與復原
        y += 40;
        addBtn(x, y, tw, 20, "選擇建築藍圖", true, true, () -> exec("tool_building_pick", ""));
        addBtn(x + tw + 6, y, tw, 20, "復原最近建築", false, true, () -> exec("tool_building_undo", ""));
        addBtn(x + (tw + 6) * 2, y, tw, 20, "領取：建築放置器", false, true, () -> exec("tool_give_one", "building_placer"));
    }

    // ── 分頁 4：系統維護 ────────────────────────────────────────────────────

    private void initSystemTab() {
        int x = left + 18;
        int w = panelW - 36;
        int hw = (w - 8) / 2;

        int y = bodyTop + 26;
        addBtn(x, y, hw, 22, "立即還原比賽破壞方塊", true, true, () -> exec("glass_restore", ""));
        addBtn(x + hw + 8, y, hw, 22, "重置遊戲狀態回大廳", false, true, () -> exec("reset", ""));
        y += 28;
        addBtn(x, y, w, 20, "修復附近無形方塊（重送已載入區塊）", false, true, () -> exec("sync_chunks", ""));

        y += 42;
        addBtn(x, y, hw, 22, "重載伺服器設定檔", false, true, () -> exec("reload", ""));
        addBtn(x + hw + 8, y, hw, 22, "查看模式與物資統計", false, true, () -> exec("status", ""));
        y += 28;
        addBtn(x, y, w, 22, "領取全套管理工具（含控制台）", true, true, () -> exec("give_tools", ""));
    }

    private void addToggle(int x, int y, int w, String label, boolean on, Runnable act) {
        String text = (on ? "[✓] " : "[ ] ") + label;
        addBtn(x, y, w, 18, text, on, true, act);
    }

    private void addBtn(int x, int y, int w, int h, String label, boolean accent, boolean active, Runnable act) {
        PGButton b = new PGButton(x, y, w, h, Component.literal(label), accent, false, act);
        b.active = active;
        addRenderableWidget(b);
    }

    private int currentMask() {
        int m = 0;
        if (layerMajor) m |= BRMapGenerator.LAYER_MAJOR;
        if (layerSmall) m |= BRMapGenerator.LAYER_SMALL;
        if (layerWild)  m |= BRMapGenerator.LAYER_WILD;
        if (layerCover) m |= BRMapGenerator.LAYER_COVER;
        return m;
    }

    private int currentMapId() {
        if (selectedMapIdx < 0 || selectedMapIdx >= info.brMaps().size()) return -1;
        return info.brMaps().get(selectedMapIdx).id();
    }

    private void exec(String action, String arg) {
        PGNetwork.sendToServer(AdminPanelPacket.run(action, arg));
        onClose();
    }

    // ── 滑鼠互動（左側地圖清單選取）────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == Tab.BR_MAP && button == 0 && mx >= listX && mx < listX + listW && my >= bodyTop + 18 && my < bodyBottom) {
            int row = ((int) my - (bodyTop + 20) + mapScroll) / 24;
            int totalRows = info.brMaps().size() + (info.brMaps().isEmpty() ? 0 : 1); // 最後一列為「全部 5 張預設」
            if (row >= 0 && row < totalRows) {
                selectedMapIdx = (row == info.brMaps().size()) ? -1 : row;
                deleteConfirmUntil = 0L;
                PGButton.playClick(1.0f);
                init();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == Tab.BR_MAP && mx >= listX && mx < listX + listW && my >= bodyTop + 18 && my < bodyBottom) {
            int totalH = (info.brMaps().size() + 1) * 24;
            int viewH = bodyBottom - (bodyTop + 20);
            int maxScroll = Math.max(0, totalH - viewH);
            mapScroll = Math.max(0, Math.min(maxScroll, mapScroll + (delta > 0 ? -20 : 20)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);
        g.fill(left, top + 29, left + panelW, top + 30, BORDER);
        g.fill(left, bodyBottom + 4, left + panelW, bodyBottom + 5, BORDER);

        switch (tab) {
            case BR_MAP -> renderBrTab(g, mouseX, mouseY);
            case ARENA -> renderArenaTab(g);
            case TOOLS -> renderToolsTab(g);
            case SYSTEM -> renderSystemTab(g);
        }

        // 底部狀態列（右側預留給常駐「取消目前作業」按鈕）
        String status = font.plainSubstrByWidth("目前作業：" + info.jobStatus(), panelW - 116);
        g.drawString(font, status, left + 12, bodyBottom + 11, DIM, false);
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderBrTab(GuiGraphics g, int mouseX, int mouseY) {
        // 左欄：地圖清單
        g.fill(listX - 1, bodyTop - 1, listX + listW + 1, bodyBottom + 1, BORDER);
        g.fill(listX, bodyTop, listX + listW, bodyBottom, PANEL);
        g.drawString(font, "選擇重製地圖", listX + 6, bodyTop + 5, ACCENT, false);
        g.fill(listX + 4, bodyTop + 16, listX + listW - 4, bodyTop + 17, BORDER);

        g.enableScissor(listX + 1, bodyTop + 18, listX + listW - 1, bodyBottom - 1);
        if (info.brMaps().isEmpty()) {
            g.drawString(font, "尚未登記地圖", listX + 8, bodyTop + 26, DIM, false);
            g.drawString(font, "請於右側新建", listX + 8, bodyTop + 38, DIM, false);
        } else {
            int totalRows = info.brMaps().size() + 1;
            for (int i = 0; i < totalRows; i++) {
                int ry = bodyTop + 20 + i * 24 - mapScroll;
                if (ry + 24 < bodyTop + 18 || ry > bodyBottom) continue;
                boolean isAll = i == info.brMaps().size();
                boolean sel = isAll ? (selectedMapIdx == -1) : (selectedMapIdx == i);
                boolean hov = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + 22;
                if (sel) {
                    g.fill(listX + 2, ry, listX + listW - 2, ry + 22, ROW_SEL);
                    g.fill(listX + 2, ry, listX + 4, ry + 22, ACCENT);
                } else if (hov) {
                    g.fill(listX + 2, ry, listX + listW - 2, ry + 22, ROW_HOV);
                }
                if (isAll) {
                    g.drawString(font, "全部 5 張預設地圖", listX + 8, ry + 3, sel ? ACCENT : TEXT, false);
                    g.drawString(font, "依序批次重製", listX + 8, ry + 13, DIM, false);
                } else {
                    MapPickPacket.Entry m = info.brMaps().get(i);
                    String title = font.plainSubstrByWidth("#" + m.id() + " " + (m.name().isBlank() ? "未命名" : m.name()), listW - 14);
                    g.drawString(font, title, listX + 8, ry + 3, sel ? ACCENT : TEXT, false);
                    g.drawString(font, "物資點 " + m.points() + " 個", listX + 8, ry + 13, DIM, false);
                }
            }
        }
        g.disableScissor();

        // 右欄：區域勾選與操作
        g.fill(rightX - 1, bodyTop - 1, rightX + rightW + 1, bodyBottom + 1, BORDER);
        g.fill(rightX, bodyTop, rightX + rightW, bodyBottom, PANEL);

        g.drawString(font, "1. 勾選要重製的區域（會先刪除舊區域再重做）", rightX + 8, bodyTop + 6, ACCENT, false);
        g.drawString(font, "2. 執行重製／維護／刪除", rightX + 8, bodyTop + 66, DIM, false);
        g.fill(rightX + 8, bodyTop + 146, rightX + rightW - 8, bodyTop + 147, BORDER);
        g.drawString(font, "3. 從零新建地圖", rightX + 8, bodyTop + 151, DIM, false);
    }

    private void renderArenaTab(GuiGraphics g) {
        int x = left + 10;
        int w = panelW - 20;
        g.fill(x - 1, bodyTop - 1, x + w + 1, bodyBottom + 1, BORDER);
        g.fill(x, bodyTop, x + w, bodyBottom, PANEL);

        String activeLabel = info.arenaMaps().stream()
            .filter(a -> a.id().equals(info.activeArenaId()))
            .map(MenuDataPacket.ArenaMapEntry::name)
            .findFirst().orElse(info.activeArenaId());
        g.drawString(font, "目前團隊場地：" + activeLabel, x + 8, bodyTop + 8, ACCENT, false);
        g.drawString(font, "點選切換場地：", x + 8, bodyTop + 22, DIM, false);

        int rows = Math.max(1, (info.arenaMaps().size() + 1) / 2);
        int sepY = bodyTop + 34 + rows * 24 + 4;
        g.fill(x + 8, sepY, x + w - 8, sepY + 1, BORDER);
        g.drawString(font, "黑曜地堡與場地操作", x + 8, sepY + 4, DIM, false);

        int sepY2 = sepY + 58;
        g.fill(x + 8, sepY2, x + w - 8, sepY2 + 1, BORDER);
        g.drawString(font, "目前場地重生點保護自訂（每張地圖獨立儲存）", x + 8, sepY2 + 5, ACCENT, false);
        if (campSquare) {
            int sumY = sepY2 + 84;
            if (sumY + 10 < bodyBottom) {
                String summary = String.format("紅隊：北%d 南%d 西%d 東%d  ｜  藍隊：北%d 南%d 西%d 東%d",
                    campRed[0], campRed[1], campRed[2], campRed[3],
                    campBlue[0], campBlue[1], campBlue[2], campBlue[3]);
                g.drawString(font, summary, x + 8, sumY, DIM, false);
            }
        }
    }

    private void renderToolsTab(GuiGraphics g) {
        int x = left + 10;
        int w = panelW - 20;
        g.fill(x - 1, bodyTop - 1, x + w + 1, bodyBottom + 1, BORDER);
        g.fill(x, bodyTop, x + w, bodyBottom, PANEL);

        g.drawString(font, "浮空文字工具", x + 8, bodyTop + 8, ACCENT, false);
        g.fill(x + 8, bodyTop + 48, x + w - 8, bodyTop + 49, BORDER);
        g.drawString(font, "物資生成點與地圖工具（需準星指方塊者可單獨領取）", x + 8, bodyTop + 52, ACCENT, false);
        g.fill(x + 8, bodyTop + 112, x + w - 8, bodyTop + 113, BORDER);
        g.drawString(font, "建築生成與復原工具", x + 8, bodyTop + 116, ACCENT, false);
    }

    private void renderSystemTab(GuiGraphics g) {
        int x = left + 10;
        int w = panelW - 20;
        g.fill(x - 1, bodyTop - 1, x + w + 1, bodyBottom + 1, BORDER);
        g.fill(x, bodyTop, x + w, bodyBottom, PANEL);

        g.drawString(font, "對戰與背景任務維護", x + 8, bodyTop + 10, ACCENT, false);
        g.fill(x + 8, bodyTop + 84, x + w - 8, bodyTop + 85, BORDER);
        g.drawString(font, "伺服器設定與管理工具", x + 8, bodyTop + 92, ACCENT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
