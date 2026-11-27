package com.provinggrounds.ui;

import com.provinggrounds.network.MapLabelActionPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 檔案: RegionLabelScreen.java
 * 目的: 管理員在大地圖右鍵選擇「命名／修改區域」時開啟的輸入畫面。
 */
public class RegionLabelScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, DIM = 0xFFA0A8B8;

    @Nullable private final Screen parent;
    private final int mapId;
    private final double worldX, worldZ;
    private final String initialName;
    private EditBox box;
    private int left, top, boxW = 240, boxH = 92;

    public RegionLabelScreen(@Nullable Screen parent, int mapId, double worldX, double worldZ, String initialName) {
        super(Component.literal("命名大區域"));
        this.parent = parent;
        this.mapId = mapId;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    protected void init() {
        left = (width - boxW) / 2;
        top = (height - boxH) / 2;
        box = new EditBox(font, left + 12, top + 34, boxW - 24, 18, Component.literal("區域名稱"));
        box.setMaxLength(24);
        box.setValue(initialName);
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(new PGButton(left + 12, top + 62, 102, 18, Component.literal("確定儲存"), true, false, this::submit));
        addRenderableWidget(new PGButton(left + boxW - 114, top + 62, 102, 18, Component.literal("取消"), false, false, this::onClose));
    }

    private void submit() {
        String text = box.getValue().trim();
        if (!text.isEmpty()) {
            PGNetwork.sendToServer(new MapLabelActionPacket(mapId, false, worldX, worldZ, text));
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + boxW + 1, top + boxH + 1, BORDER);
        g.fill(left, top, left + boxW, top + boxH, BG);
        g.drawString(font, "命名大區域", left + 12, top + 9, ACCENT, false);
        g.drawString(font, String.format("座標 X %.0f  Z %.0f", worldX, worldZ), left + 12, top + 21, DIM, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
