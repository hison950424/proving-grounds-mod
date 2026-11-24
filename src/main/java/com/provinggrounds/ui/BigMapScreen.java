package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientMapData;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.MenuRequestPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * 檔案: BigMapScreen.java
 * 目的: 大地圖（只在客戶端使用）。遊戲中按 M 打開，再按 M 或 Esc 關閉。
 *   顯示：地形俯視圖、安全區（白）、下一圈（淺藍虛線）、空投、隊友、隊友看到的敵人、自己。
 *   操作：滾輪縮放（以滑鼠位置為中心）、左鍵拖曳移動、右鍵回到全圖。
 *   左上「選單」按鈕：打開主選單（管理員結束遊戲、觀戰者加入遊戲都在那裡）；「HUD 設定」：比賽中調整 HUD 位置與大小。
 */
public class BigMapScreen extends Screen {

    private static final int BG     = 0xEB0F141E;
    private static final int BORDER = 0xFF2A3344;
    private static final int TEXT_DIM = 0xFFA0A8B8;

    private int mapX, mapY, mapSize;
    private double viewX, viewZ, span;     // 目前看的範圍（世界座標）
    private double fullX, fullZ, fullSpan; // 全圖範圍
    private boolean dragging;

    public BigMapScreen() {
        super(Tr.c("map.screen"));
    }

    @Override
    protected void init() {
        mapSize = Math.max(64, Math.min(width - 20, height - 44));
        mapX = (width - mapSize) / 2;
        mapY = 28;
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

    /** 全圖範圍：有俯視圖用圖片範圍，沒有就用目前安全區外加一點。 */
    private void computeFull() {
        ClientMapData.Image img = MapRender.image();
        if (img != null) {
            fullX = img.minX();
            fullZ = img.minZ();
            fullSpan = (double) img.size() * img.scale();
        } else {
            BRStatePacket s = ClientBRState.get();
            double r = Math.max(64, s.radius() > 0 ? s.radius() * 1.1 : 256);
            fullX = s.cx() - r;
            fullZ = s.cz() - r;
            fullSpan = r * 2;
        }
    }

    private void resetView() {
        viewX = fullX;
        viewZ = fullZ;
        span = fullSpan;
    }

    // ── 操作 ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
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
        if (super.mouseClicked(mx, my, button)) return true;
        if (!inMap(mx, my)) return false;
        if (button == 1) { resetView(); return true; }
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
        // 圖片在打開地圖之後才收到：重新計算全圖範圍
        if (fullSpan <= 0 || (MapRender.image() != null && fullSpan != MapRender.image().size() * (double) MapRender.image().scale())) {
            computeFull();
            resetView();
        }
        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, BORDER);
        g.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, BG);

        MapRender.View v = new MapRender.View(mapX, mapY, mapSize, mapSize, viewX, viewZ, span);
        g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        MapRender.drawTerrain(g, v);
        BRStatePacket s = ClientBRState.get();
        if (s.nextRadius() >= 0) MapRender.drawCircle(g, v, s.nextCx(), s.nextCz(), s.nextRadius(), MapRender.NEXT_ZONE, 1, true);
        MapRender.drawCircle(g, v, s.cx(), s.cz(), s.radius(), MapRender.ZONE, 2, false);
        MapRender.drawMarkers(g, font, v, true);
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (cam != null && s.selfState() != ClientBRState.SELF_NONE && s.selfState() != ClientBRState.SELF_DEAD) {
            MapRender.drawSelf(g, font, v, cam.getX(), cam.getZ(), cam.getYRot());
        }
        g.disableScissor();

        // 標題、座標、說明
        String title = Tr.t("map.title") + (MapRender.image() == null ? Tr.t("map.no_image") : "");
        g.drawString(font, title, (width - font.width(title)) / 2, 10, 0xFFE8D060, false);
        String help = Tr.t("map.help", ClientModEvents.OPEN_MENU.getTranslatedKeyMessage().getString());
        if (inMap(mouseX, mouseY)) {
            int wx = (int) Math.floor(viewX + (mouseX - mapX) / (double) mapSize * span);
            int wz = (int) Math.floor(viewZ + (mouseY - mapY) / (double) mapSize * span);
            help = "X " + wx + "  Z " + wz + "｜" + help;
        }
        g.drawString(font, help, (width - font.width(help)) / 2, mapY + mapSize + 5, TEXT_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
