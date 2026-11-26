package com.provinggrounds.ui;

import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.client.ClientDomGear;
import com.provinggrounds.modes.arena.ArenaClass;
import com.provinggrounds.network.ArenaActionPacket;
import com.provinggrounds.network.ArenaStatePacket;
import com.provinggrounds.network.DomGearActionPacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.special.DomGear;
import com.provinggrounds.special.SpecialType;
import com.provinggrounds.special.client.SpecialHotbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 檔案: LoadoutScreen.java
 * 目的: 開局 10 秒準備期間自動跳出的「選擇職業與特殊道具」介面（計畫書 9）。
 *   - 左欄：九大職業清單，顯示每個職業的限定槍械與被動效果；點擊立即切換（伺服器只在準備期間接受）。
 *   - 右欄（特殊道具模式開啟時顯示）：
 *       佔點（kind == 2）：從可用特殊道具清單中挑選本場使用的一種（顯示強度與商店加價）。
 *       團隊死鬥（kind == 1）：說明團隊死鬥特殊道具獲取規則（開局隨機、每 3 殺／連死 3 次獲得）。
 *   - 倒數結束（loadoutOpen 變成 false）後自動關閉，整場鎖定職業與特殊道具。
 */
public final class LoadoutScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060,
        TEXT = 0xFFFFFFFF, DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, SEL = 0x40E8D060, HOVER = 0x24FFFFFF;
    private static final int CLASS_ROW_H = 23;
    private static final int SPECIAL_ROW_H = 16;

    private int panelW, panelH, left, top;
    private int clsX, clsY, clsW, clsH, clsScroll;
    private int spX, spY, spW, spH, spScroll;
    private int seenArena = -1, seenGear = -1;
    private List<SpecialType> allowedSpecials = List.of();

    public LoadoutScreen() {
        super(Component.literal("選擇職業與特殊道具"));
    }

    @Override
    protected void init() {
        ArenaStatePacket s = ClientArenaState.get();
        seenArena = ClientArenaState.version();
        seenGear = ClientDomGear.version();
        boolean showSpecial = SpecialHotbar.modeOn();
        boolean domPick = showSpecial && s.kind() == 2;
        allowedSpecials = domPick ? DomGear.allowed() : List.of();

        panelW = Math.min(showSpecial ? 480 : 320, width - 16);
        panelH = Math.min(290, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        clsX = left + 10;
        clsY = top + 34;
        clsW = showSpecial ? (int) (panelW * 0.56) - 14 : panelW - 20;
        clsH = panelH - 66;

        spX = clsX + clsW + 10;
        spY = top + 34;
        spW = left + panelW - 10 - spX;
        spH = clsH;

        int by = top + panelH - 24;
        addRenderableWidget(new PGButton(left + 10, by, 108, 16, Component.literal("確認並打開商店"), true, false, () -> {
            if (minecraft != null) minecraft.setScreen(new ShopScreen());
        }));
        addRenderableWidget(new PGButton(left + panelW - 74, by, 64, 16, Component.literal("關閉"), false, false, this::onClose));
    }

    @Override
    public void tick() {
        ArenaStatePacket s = ClientArenaState.get();
        if (!ClientArenaState.active() || !s.loadoutOpen()) {
            onClose();
            return;
        }
        if (seenArena != ClientArenaState.version() || seenGear != ClientDomGear.version()) {
            seenArena = ClientArenaState.version();
            seenGear = ClientDomGear.version();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ArenaStatePacket s = ClientArenaState.get();
        boolean showSpecial = SpecialHotbar.modeOn();
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);

        String title = showSpecial ? "開局準備：選擇職業與特殊道具" : "開局準備：選擇職業";
        g.drawString(font, title, left + 10, top + 9, ACCENT, false);
        String timer = "剩餘 " + Math.max(0, s.seconds()) + " 秒（開始後無法更換）";
        g.drawString(font, timer, left + panelW - 10 - font.width(timer), top + 9, GOOD, false);
        g.fill(left + 10, top + 21, left + panelW - 10, top + 22, BORDER);

        // 左欄標題
        g.drawString(font, "職業（共 9 種）", clsX, top + 24, DIM, false);
        renderClasses(g, s, mouseX, mouseY);

        if (showSpecial) {
            if (s.kind() == 2) {
                g.drawString(font, "特殊道具（整場一種）", spX, top + 24, DIM, false);
                renderSpecialList(g, mouseX, mouseY);
            } else {
                g.drawString(font, "特殊道具規則", spX, top + 24, DIM, false);
                renderTdmSpecialInfo(g);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderClasses(GuiGraphics g, ArenaStatePacket s, int mouseX, int mouseY) {
        ArenaClass[] all = ArenaClass.values();
        g.fill(clsX - 1, clsY - 1, clsX + clsW + 1, clsY + clsH + 1, BORDER);
        g.fill(clsX, clsY, clsX + clsW, clsY + clsH, 0xA010141C);
        g.enableScissor(clsX, clsY, clsX + clsW, clsY + clsH);
        for (int i = 0; i < all.length; i++) {
            ArenaClass c = all[i];
            int ry = clsY + i * CLASS_ROW_H - clsScroll;
            if (ry + CLASS_ROW_H < clsY || ry > clsY + clsH) continue;
            boolean mine = c.ordinal() == s.classOrdinal();
            boolean over = mouseX >= clsX && mouseX < clsX + clsW && mouseY >= ry && mouseY < ry + CLASS_ROW_H
                && mouseY >= clsY && mouseY < clsY + clsH;
            g.fill(clsX + 1, ry + 1, clsX + clsW - 1, ry + CLASS_ROW_H - 1, mine ? SEL : over ? HOVER : 0x12FFFFFF);
            if (mine) g.fill(clsX + 1, ry + 1, clsX + 3, ry + CLASS_ROW_H - 1, ACCENT);
            String head = (mine ? "✔ " : "") + c.displayName;
            g.drawString(font, head, clsX + 6, ry + 3, mine ? ACCENT : TEXT, false);
            String guns = trim(c.gunSummary(), clsW - 68);
            g.drawString(font, guns, clsX + clsW - 5 - font.width(guns), ry + 3, GOOD, false);
            g.drawString(font, trim(c.description, clsW - 12), clsX + 6, ry + 13, DIM, false);
        }
        g.disableScissor();
    }

    private void renderSpecialList(GuiGraphics g, int mouseX, int mouseY) {
        SpecialType cur = ClientDomGear.choice();
        g.fill(spX - 1, spY - 1, spX + spW + 1, spY + spH + 1, BORDER);
        g.fill(spX, spY, spX + spW, spY + spH, 0xA010141C);
        g.enableScissor(spX, spY, spX + spW, spY + spH);
        SpecialType hovered = null;
        for (int i = 0; i < allowedSpecials.size(); i++) {
            SpecialType t = allowedSpecials.get(i);
            int ry = spY + i * SPECIAL_ROW_H - spScroll;
            if (ry + SPECIAL_ROW_H < spY || ry > spY + spH) continue;
            boolean mine = t == cur;
            boolean over = mouseX >= spX && mouseX < spX + spW && mouseY >= ry && mouseY < ry + SPECIAL_ROW_H
                && mouseY >= spY && mouseY < spY + spH;
            if (over) hovered = t;
            g.fill(spX + 1, ry + 1, spX + spW - 1, ry + SPECIAL_ROW_H - 1, mine ? SEL : over ? HOVER : 0x10FFFFFF);
            int tier = DomGear.tier(t);
            String tag = "【" + DomGear.tierName(tier) + "】";
            int tagColor = tier == 3 ? 0xFFFF7070 : tier == 2 ? 0xFFE8D060 : 0xFF70D0FF;
            g.drawString(font, tag, spX + 4, ry + 4, tagColor, false);
            String right = "+" + DomGear.markupPercent(tier) + "%";
            g.drawString(font, right, spX + spW - 4 - font.width(right), ry + 4, DIM, false);
            int nameX = spX + 4 + font.width(tag);
            g.drawString(font, trim(t.zh, spW - (nameX - spX) - font.width(right) - 6), nameX, ry + 4, mine ? ACCENT : TEXT, false);
        }
        g.disableScissor();
        if (hovered != null) {
            int tier = DomGear.tier(hovered);
            g.renderComponentTooltip(font, List.of(
                Component.literal(hovered.zh + "（" + DomGear.tierName(tier) + "・商店 +" + DomGear.markupPercent(tier) + "%）"),
                Component.literal(hovered.desc)
            ), mouseX, mouseY);
        }
    }

    private void renderTdmSpecialInfo(GuiGraphics g) {
        g.fill(spX - 1, spY - 1, spX + spW + 1, spY + spH + 1, BORDER);
        g.fill(spX, spY, spX + spW, spY + spH, 0xA010141C);
        int y = spY + 8;
        for (String line : List.of(
            "團隊死鬥・特殊道具規則：",
            "",
            "• 開局免費發 1 個隨機特殊道具",
            "• 之後每累計擊殺 3 人獲得 1 個",
            "• 連死 3 次未獲得時保底 1 個",
            "",
            "按 Q 使用特殊道具！"
        )) {
            g.drawString(font, trim(line, spW - 12), spX + 6, y, line.startsWith("•") ? TEXT : line.isEmpty() ? DIM : ACCENT, false);
            y += 12;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        if (mx >= clsX && mx < clsX + clsW && my >= clsY && my < clsY + clsH) {
            int idx = (int) ((my - clsY + clsScroll) / CLASS_ROW_H);
            ArenaClass[] all = ArenaClass.values();
            if (idx >= 0 && idx < all.length) {
                PGButton.playClick(1.0f);
                PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.SET_CLASS, all[idx].ordinal()));
                return true;
            }
        }
        if (!allowedSpecials.isEmpty() && mx >= spX && mx < spX + spW && my >= spY && my < spY + spH) {
            int idx = (int) ((my - spY + spScroll) / SPECIAL_ROW_H);
            if (idx >= 0 && idx < allowedSpecials.size()) {
                PGButton.playClick(1.0f);
                PGNetwork.sendToServer(new DomGearActionPacket(allowedSpecials.get(idx).ordinal()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= clsX && mx < clsX + clsW && my >= clsY && my < clsY + clsH) {
            int max = Math.max(0, ArenaClass.values().length * CLASS_ROW_H - clsH);
            clsScroll = Math.max(0, Math.min(max, clsScroll - (int) (delta * CLASS_ROW_H)));
            return true;
        }
        if (!allowedSpecials.isEmpty() && mx >= spX && mx < spX + spW && my >= spY && my < spY + spH) {
            int max = Math.max(0, allowedSpecials.size() * SPECIAL_ROW_H - spH);
            spScroll = Math.max(0, Math.min(max, spScroll - (int) (delta * SPECIAL_ROW_H * 2)));
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
