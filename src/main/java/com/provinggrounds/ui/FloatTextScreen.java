package com.provinggrounds.ui;

import com.provinggrounds.admin.FloatText;
import com.provinggrounds.network.FloatTextPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 檔案: FloatTextScreen.java
 * 目的: 浮空文字的「輸入文字」畫面（計畫書 B6，只在客戶端）：一個輸入框、「確認」與「取消」。
 *   單行、最多 100 字，支援 & 色碼（例如 &6 金色）。輸入框用原版 EditBox：可用輸入法打中文，也可以 Ctrl+V 貼上。
 *   畫面有輸入框，所以 A11 的 WASD 移動不會接管（UiWalk 遇到有焦點的輸入框會停用）。按 Enter 等於確認。
 *   伺服器收到後會重新檢查權限與長度。
 */
public class FloatTextScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, DIM = 0xFFA0A8B8;

    private EditBox box;

    private FloatTextScreen() {
        super(Component.literal("建立浮空文字"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FloatTextScreen());
    }

    @Override
    protected void init() {
        int w = Math.min(320, width - 20), x = (width - w) / 2, y = height / 2 - 30;
        box = new EditBox(font, x + 10, y + 24, w - 20, 18, Component.literal("文字"));
        box.setMaxLength(FloatText.MAX_LENGTH);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new PGButton(x + w - 140, y + 50, 60, 18, Component.literal("確認"), true, false, this::confirm));
        addRenderableWidget(new PGButton(x + w - 74, y + 50, 64, 18, Component.literal("取消"), false, false, this::onClose));
    }

    private void confirm() {
        String text = box.getValue();
        if (text.isBlank()) {
            PGButton.playDeny();
            return;
        }
        PGNetwork.sendToServer(new FloatTextPacket(text, false));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int w = Math.min(320, width - 20), x = (width - w) / 2, y = height / 2 - 30;
        g.fill(x - 1, y - 1, x + w + 1, y + 76, BORDER);
        g.fill(x, y, x + w, y + 75, BG);
        g.drawString(font, "建立浮空文字（在你目前的位置）", x + 10, y + 8, ACCENT, false);
        g.drawString(font, "& 色碼：&6 金、&c 紅、&a 綠、&b 藍、&l 粗體", x + 10, y + 56, DIM, false);
        g.drawString(font, box.getValue().length() + "／" + FloatText.MAX_LENGTH, x + 10, y + 66, DIM, false);
        super.render(g, mouseX, mouseY, partial);
    }
}
