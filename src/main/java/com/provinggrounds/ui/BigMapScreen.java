package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientLobbyData;
import com.provinggrounds.client.ClientMapData;
import com.provinggrounds.client.ClientMenuData;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.LobbyStatePacket;
import com.provinggrounds.network.MapTeleportPacket;
import com.provinggrounds.network.MenuDataPacket;
import com.provinggrounds.network.MenuRequestPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: BigMapScreen.java
 * 目的: 大地圖與大廳地圖預覽（只在客戶端使用）。遊戲中按 M 打開；大廳為大逃殺模式時也可從選單開啟預覽。
 *   顯示：地形俯視圖、進行中的安全區（白）／下一圈（淺藍虛線）／空投／隊友／敵人／自己；
 *         大廳預覽時顯示選定地圖的虛線邊界與地形（若玩家位在該地圖範圍內才顯示自己座標）。
 *   操作：滾輪縮放（以滑鼠位置為中心）、左鍵拖曳移動、一般玩家右鍵回到全圖；
 *         管理員右鍵可開啟選單選擇「傳送到此處（TP）」或「重設全圖視角」。
 */
public class BigMapScreen extends Screen {

    private static final int BG     = 0xEB0F141E;
    private static final int BORDER = 0xFF2A3344;
    private static final int TEXT_DIM = 0xFFA0A8B8;
    private static final int ACCENT = 0xFFE8D060;
    private static final int CTX_ROW_H = 18;
    private static final int CTX_PAD = 3;
    private static final int CTX_ROWS = 2;

    private int mapX, mapY, mapSize;
    private double viewX, viewZ, span;     // 目前看的範圍（世界座標）
    private double fullX, fullZ, fullSpan; // 全圖範圍
    private int seenMapId = Integer.MIN_VALUE;
    private long seenMapVer = Long.MIN_VALUE;
    private boolean dragging;

    private boolean ctxOpen;
    private int ctxX, ctxY, ctxW, ctxH;
    private int tpWorldX, tpWorldZ;

    public BigMapScreen() {
        super(Tr.c("map.screen"));
    }

    @Override
    protected void init() {
        mapSize = Math.max(64, Math.min(width - 20, height - 44));
        mapX = (width - mapSize) / 2;
        mapY = 28;
        ctxOpen = false;
        computeFull();
        if (span <= 0) resetView();

        addRenderableWidget(new PGButton(mapX, 5, 60, 18, Tr.c("map.menu"), true, false, () -> {
            PGNetwork.sendToServer(new MenuRequestPacket());
            MainMenuScreen.openLobbyView();
            Minecraft.getInstance().setScreen(new MainMenuScreen());
        }));
        // 比賽中也能調 HUD 版面（設定畫面關掉後回到大地圖）
        addRenderableWidget(new PGButton(mapX + 66, 5, 64, 18, Component.literal("HUD 設定"), false, false,
            () -> Minecraft.getInstance().setScreen(new HudEditScreen(this))));
        addRenderableWidget(new PGButton(mapX + mapSize - 60, 5, 60, 18, Tr.c("map.close"), false, false, this::onClose));
    }

    private boolean isAdmin() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.player != null && mc.player.hasPermissions(2))
            || (ClientMenuData.get() != null && ClientMenuData.get().isOp());
    }

    private boolean isInActiveBR() {
        byte phase = ClientBRState.get().phase();
        return phase == ClientBRState.PHASE_PLANE || phase == ClientBRState.PHASE_ACTIVE;
    }

    @Nullable
    private MenuDataPacket.MapEntry currentLobbyMap() {
        LobbyStatePacket lobby = ClientLobbyData.get();
        MenuDataPacket menu = ClientMenuData.get();
        if (lobby == null || menu == null || menu.maps().isEmpty()) return null;
        for (MenuDataPacket.MapEntry m : menu.maps()) {
            if (m.id() == lobby.mapId()) return m;
        }
        return menu.maps().get(0);
    }

    /** 全圖範圍：有俯視圖用圖片範圍，沒有時大廳預覽用選定地圖半徑、比賽中用目前安全區。 */
    private void computeFull() {
        ClientMapData.Image img = MapRender.image();
        if (img != null) {
            fullX = img.minX();
            fullZ = img.minZ();
            fullSpan = (double) img.size() * img.scale();
            seenMapId = img.mapId();
            seenMapVer = img.version();
            return;
        }
        MenuDataPacket.MapEntry lobbyMap = isInActiveBR() ? null : currentLobbyMap();
        if (lobbyMap != null && lobbyMap.radius() > 0) {
            double r = Math.max(64, lobbyMap.radius() * 1.1);
            fullX = lobbyMap.cx() - r;
            fullZ = lobbyMap.cz() - r;
            fullSpan = r * 2;
            seenMapId = lobbyMap.id();
            seenMapVer = 0L;
            return;
        }
        BRStatePacket s = ClientBRState.get();
        double r = Math.max(64, s.radius() > 0 ? s.radius() * 1.1 : 256);
        fullX = s.cx() - r;
        fullZ = s.cz() - r;
        fullSpan = r * 2;
        seenMapId = -1;
        seenMapVer = 0L;
    }

    private void resetView() {
        viewX = fullX;
        viewZ = fullZ;
        span = fullSpan;
    }

    private void openContextMenu(double mx, double my) {
        tpWorldX = (int) Math.floor(viewX + (mx - mapX) / (double) mapSize * span);
        tpWorldZ = (int) Math.floor(viewZ + (my - mapY) / (double) mapSize * span);
        String tpLabel = Tr.t("map.tp_here") + " (" + tpWorldX + ", " + tpWorldZ + ")";
        String resetLabel = Tr.t("map.reset_view");
        ctxW = Math.max(font.width(tpLabel), font.width(resetLabel)) + 16;
        ctxH = CTX_ROWS * CTX_ROW_H + CTX_PAD * 2;
        ctxX = Math.max(4, Math.min(width - ctxW - 4, (int) mx));
        ctxY = Math.max(4, Math.min(height - ctxH - 4, (int) my));
        ctxOpen = true;
    }

    // ── 操作 ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        ctxOpen = false;
        if (!inMap(mx, my)) return super.mouseScrolled(mx, my, delta);
        double wx = viewX + (mx - mapX) / mapSize * span, wz = viewZ + (my - mapY) / mapSize * span;
        double newSpan = Math.max(48, Math.min(fullSpan, span * (delta > 0 ? 0.8 : 1.25)));
        viewX = wx - (mx - mapX) / mapSize * newSpan;
        viewZ = wz - (my - mapY) / mapSize * newSpan;
        span = newSpan;
        clampView();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ctxOpen) {
            if (button == 0 && mx >= ctxX && mx < ctxX + ctxW && my >= ctxY && my < ctxY + ctxH) {
                int row = ((int) my - ctxY - CTX_PAD) / CTX_ROW_H;
                ctxOpen = false;
                if (row == 0) {
                    PGButton.playClick(1.0f);
                    PGNetwork.sendToServer(new MapTeleportPacket(tpWorldX, tpWorldZ));
                    onClose();
                    return true;
                }
                if (row == 1) {
                    PGButton.playClick(1.0f);
                    resetView();
                    return true;
                }
                return true;
            }
            if (button == 1 && inMap(mx, my) && isAdmin()) {
                openContextMenu(mx, my);
                return true;
            }
            ctxOpen = false;
            if (super.mouseClicked(mx, my, button)) return true;
            return inMap(mx, my);
        }

        if (super.mouseClicked(mx, my, button)) return true;
        if (!inMap(mx, my)) return false;
        if (button == 1) {
            if (isAdmin()) openContextMenu(mx, my);
            else resetView();
            return true;
        }
        if (button == 0) { dragging = true; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            viewX -= dx / mapSize * span;
            viewZ -= dy / mapSize * span;
            clampView();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void clampView() {
        viewX = Math.max(fullX, Math.min(fullX + fullSpan - span, viewX));
        viewZ = Math.max(fullZ, Math.min(fullZ + fullSpan - span, viewZ));
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapSize && my >= mapY && my < mapY + mapSize;
    }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        boolean activeBR = isInActiveBR();
        MenuDataPacket.MapEntry lobbyMap = activeBR ? null : currentLobbyMap();
        ClientMapData.Image curImg = MapRender.image();
        int curId = curImg != null ? curImg.mapId() : (lobbyMap != null ? lobbyMap.id() : -1);
        long curVer = curImg != null ? curImg.version() : 0L;
        if (fullSpan <= 0 || curId != seenMapId || curVer != seenMapVer) {
            computeFull();
            resetView();
        }

        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, BORDER);
        g.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, BG);

        MapRender.View v = new MapRender.View(mapX, mapY, mapSize, mapSize, viewX, viewZ, span);
        g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        MapRender.drawTerrain(g, v);
        BRStatePacket s = ClientBRState.get();
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (activeBR) {
            if (s.nextRadius() >= 0) MapRender.drawCircle(g, v, s.nextCx(), s.nextCz(), s.nextRadius(), MapRender.NEXT_ZONE, 2 * 0.65f, true); // 下一圈 = 白圈粗度的 65%（計畫書 6 A11）
            ClientBRState.SmoothCircle sc = ClientBRState.smoothCircle();
            MapRender.drawCircle(g, v, sc.cx(), sc.cz(), sc.radius(), MapRender.ZONE, 2, false);
            MapRender.drawMarkers(g, font, v, true);
            if (cam != null && s.selfState() != ClientBRState.SELF_NONE && s.selfState() != ClientBRState.SELF_DEAD) {
                MapRender.drawSelf(g, font, v, cam.getX(), cam.getZ(), cam.getYRot());
            }
        } else {
            if (lobbyMap != null && lobbyMap.radius() > 0) {
                MapRender.drawCircle(g, v, lobbyMap.cx(), lobbyMap.cz(), lobbyMap.radius(), MapRender.ZONE, 1.5f, true);
            }
            if (cam != null && cam.getX() >= fullX && cam.getX() <= fullX + fullSpan
                    && cam.getZ() >= fullZ && cam.getZ() <= fullZ + fullSpan) {
                MapRender.drawSelf(g, font, v, cam.getX(), cam.getZ(), cam.getYRot());
            }
        }
        if (ctxOpen) {
            int px = (int) Math.round(v.px(tpWorldX + 0.5));
            int pz = (int) Math.round(v.pz(tpWorldZ + 0.5));
            int ox = px - font.width("◆") / 2;
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                g.drawString(font, "◆", ox + d[0], pz - 4 + d[1], 0xFF000000, false);
            }
            g.drawString(font, "◆", ox, pz - 4, ACCENT, false);
        }
        g.disableScissor();

        // 標題、座標、說明
        String mainTitle = !activeBR && lobbyMap != null ? Tr.t("map.preview_title", lobbyMap.name()) : Tr.t("map.title");
        String noImgNote = curImg == null ? Tr.t("map.no_image").trim() : null;
        renderMapTitle(g, mainTitle, noImgNote);
        String keyLabel = ClientModEvents.OPEN_MENU.getTranslatedKeyMessage().getString();
        String help = Tr.t(isAdmin() ? "map.help_admin" : "map.help", keyLabel);
        if (inMap(mouseX, mouseY)) {
            int wx = ctxOpen ? tpWorldX : (int) Math.floor(viewX + (mouseX - mapX) / (double) mapSize * span);
            int wz = ctxOpen ? tpWorldZ : (int) Math.floor(viewZ + (mouseY - mapY) / (double) mapSize * span);
            help = "X " + wx + "  Z " + wz + "｜" + help;
        }
        g.drawString(font, help, (width - font.width(help)) / 2, mapY + mapSize + 5, TEXT_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);

        if (ctxOpen) renderContextMenu(g, mouseX, mouseY);
    }

    private void renderMapTitle(GuiGraphics g, String mainTitle, @Nullable String subTitle) {
        int sideSpace = mapX - 14;
        if (sideSpace >= 64) {
            int maxTextW = sideSpace - 12;
            List<FormattedCharSequence> mainLines = font.split(Component.literal(mainTitle), maxTextW);
            List<FormattedCharSequence> subLines = (subTitle != null && !subTitle.isEmpty())
                ? font.split(Component.literal(subTitle), maxTextW) : List.of();
            int maxLineW = 0;
            for (FormattedCharSequence line : mainLines) maxLineW = Math.max(maxLineW, font.width(line));
            for (FormattedCharSequence line : subLines) maxLineW = Math.max(maxLineW, font.width(line));
            int boxW = Math.min(sideSpace, maxLineW + 12);
            int totalLines = mainLines.size() + subLines.size();
            int boxH = totalLines * 11 + 8;
            int bx = Math.max(6, mapX - 8 - boxW);
            int by = mapY;
            g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, BORDER);
            g.fill(bx, by, bx + boxW, by + boxH, BG);
            int ty = by + 5;
            for (FormattedCharSequence line : mainLines) {
                g.drawString(font, line, bx + 6, ty, ACCENT, false);
                ty += 11;
            }
            for (FormattedCharSequence line : subLines) {
                g.drawString(font, line, bx + 6, ty, TEXT_DIM, false);
                ty += 11;
            }
            return;
        }

        String fullTitle = mainTitle + (subTitle != null && !subTitle.isEmpty() ? " " + subTitle : "");
        int gapLeft = mapX + 136;
        int gapRight = mapX + mapSize - 66;
        int gapW = gapRight - gapLeft;
        if (gapW >= 24) {
            String clipped = font.width(fullTitle) > gapW
                ? font.plainSubstrByWidth(fullTitle, Math.max(0, gapW - font.width("…"))) + "…"
                : fullTitle;
            int tx = gapLeft + (gapW - font.width(clipped)) / 2;
            g.drawString(font, clipped, tx, 10, ACCENT, false);
        } else {
            int maxW = Math.max(16, mapSize - 12);
            String clipped = font.width(fullTitle) > maxW
                ? font.plainSubstrByWidth(fullTitle, Math.max(0, maxW - font.width("…"))) + "…"
                : fullTitle;
            int pillW = font.width(clipped) + 8;
            int bx = mapX + 4;
            int by = mapY + 4;
            g.fill(bx, by, bx + pillW, by + 13, 0xC00F141E);
            g.drawString(font, clipped, bx + 4, by + 3, ACCENT, false);
        }
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(ctxX - 1, ctxY - 1, ctxX + ctxW + 1, ctxY + ctxH + 1, BORDER);
        g.fill(ctxX, ctxY, ctxX + ctxW, ctxY + ctxH, 0xF5121824);
        String[] labels = {
            Tr.t("map.tp_here") + " (" + tpWorldX + ", " + tpWorldZ + ")",
            Tr.t("map.reset_view")
        };
        for (int i = 0; i < CTX_ROWS; i++) {
            int ry = ctxY + CTX_PAD + i * CTX_ROW_H;
            boolean hovered = mouseX >= ctxX && mouseX < ctxX + ctxW && mouseY >= ry && mouseY < ry + CTX_ROW_H;
            if (hovered) g.fill(ctxX + 2, ry, ctxX + ctxW - 2, ry + CTX_ROW_H, 0x40E8D060);
            int color = i == 0 ? (hovered ? 0xFFFFEA80 : ACCENT) : (hovered ? 0xFFFFFFFF : TEXT_DIM);
            g.drawString(font, labels[i], ctxX + 8, ry + 5, color, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && ctxOpen) {
            ctxOpen = false;
            return true;
        }
        if (ClientModEvents.OPEN_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
