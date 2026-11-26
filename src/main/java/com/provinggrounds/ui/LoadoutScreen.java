package com.provinggrounds.ui;

import com.provinggrounds.client.ClientDomGear;
import com.provinggrounds.client.ClientLobbyData;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.modes.arena.ArenaClass;
import com.provinggrounds.network.DomGearActionPacket;
import com.provinggrounds.network.LobbyActionPacket;
import com.provinggrounds.network.LobbyStatePacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.special.DomGear;
import com.provinggrounds.special.SpecialType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: LoadoutScreen.java
 * 目的: 職業與特殊道具選擇介面（計畫書 10 改版）：
 *   - CLASS_PICK    ：大廳按「更改職業」打開，瀏覽九大職業（含血量、限定槍種、被動）並可返回大廳選單。
 *   - SPECIAL_PICK  ：大廳按「更改特殊道具」打開，瀏覽佔點可用特殊道具（含強度、加價、完整說明）並可返回。
 *   - CLASS_FORCED  ：團隊模式全員準備後強制跳出，全員按「確認職業」後進入下一階段（佔點選道具或 10 秒倒數）。
 *   - SPECIAL_FORCED：佔點開啟特殊道具時，全員選完職業後強制跳出，全員按「確認特殊道具」後進入 10 秒倒數。
 */
public final class LoadoutScreen extends Screen {

    public enum Mode { CLASS_PICK, SPECIAL_PICK, CLASS_FORCED, SPECIAL_FORCED }

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060,
        TEXT = 0xFFFFFFFF, DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, SEL = 0x45E8D060, HOVER = 0x24FFFFFF, CARD = 0xA010141C;
    private static final int CLASS_ROW_H = 19;
    private static final int SPECIAL_ROW_H = 16;

    private final Mode mode;
    @Nullable private final Screen parent;
    private int panelW, panelH, left, top;
    private int listX, listY, listW, listH, scroll;
    private int infoX, infoY, infoW, infoH;

    private int pickedClass = 0;
    private int pickedSpecialOrdinal = -1;
    private List<SpecialType> allowedSpecials = List.of();
    private boolean confirmedLocal = false;

    public LoadoutScreen(Mode mode, @Nullable Screen parent) {
        super(Component.literal(isClass(mode) ? "選擇職業" : "選擇特殊道具"));
        this.mode = mode;
        this.parent = parent;
        LobbyStatePacket lobby = ClientLobbyData.get();
        if (lobby != null && Minecraft.getInstance().player != null) {
            for (var e : lobby.players()) {
                if (e.id().equals(Minecraft.getInstance().player.getUUID())) {
                    pickedClass = e.cls();
                    confirmedLocal = isForced() && e.draftDone();
                    break;
                }
            }
        }
        SpecialType curSp = ClientDomGear.choice();
        allowedSpecials = DomGear.allowed();
        if (curSp != null && allowedSpecials.contains(curSp)) {
            pickedSpecialOrdinal = curSp.ordinal();
        } else if (!allowedSpecials.isEmpty()) {
            pickedSpecialOrdinal = allowedSpecials.get(0).ordinal();
        }
    }

    private static boolean isClass(Mode m) { return m == Mode.CLASS_PICK || m == Mode.CLASS_FORCED; }
    private boolean isForced() { return mode == Mode.CLASS_FORCED || mode == Mode.SPECIAL_FORCED; }

    @Override
    protected void init() {
        allowedSpecials = DomGear.allowed();
        panelW = Math.min(480, width - 16);
        panelH = Math.min(285, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        listX = left + 10;
        listY = top + 32;
        listW = (int) (panelW * 0.48) - 12;
        listH = panelH - 64;

        infoX = listX + listW + 10;
        infoY = listY;
        infoW = left + panelW - 10 - infoX;
        infoH = listH;

        int by = top + panelH - 24;
        if (isForced()) {
            String confirmLabel = confirmedLocal ? "✔ 已確認（可修改再確認）" : (isClass(mode) ? "確認職業" : "確認特殊道具");
            addRenderableWidget(new PGButton(left + 10, by, 148, 16, Component.literal(confirmLabel), !confirmedLocal, false, this::confirmForced));
            addRenderableWidget(new PGButton(left + panelW - 92, by, 82, 16, Component.literal("取消準備"), false, false, () -> {
                PGNetwork.sendToServer(new LobbyActionPacket(LobbyManager.Action.TOGGLE_READY, 0));
                onClose();
            }));
        } else {
            addRenderableWidget(new PGButton(left + panelW - 86, by, 76, 16, Component.literal("完成並返回"), true, false, this::closeToParent));
        }
    }

    private void confirmForced() {
        if (isClass(mode)) {
            PGNetwork.sendToServer(new LobbyActionPacket(LobbyManager.Action.CONFIRM_CLASS, pickedClass));
        } else if (pickedSpecialOrdinal >= 0) {
            PGNetwork.sendToServer(new LobbyActionPacket(LobbyManager.Action.CONFIRM_SPECIAL, pickedSpecialOrdinal));
        }
        confirmedLocal = true;
        rebuildWidgets();
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isForced(); // 強制選角／選道具時請按確認或「取消準備」，避免誤關卡住流程
    }

    @Override
    public void tick() {
        LobbyStatePacket lobby = ClientLobbyData.get();
        if (isForced()) {
            if (lobby == null || !lobby.inLobby()) { onClose(); return; }
            int expected = mode == Mode.CLASS_FORCED ? LobbyManager.DRAFT_CLASS : LobbyManager.DRAFT_SPECIAL;
            if (lobby.draftPhase() != expected) {
                if (lobby.draftPhase() == LobbyManager.DRAFT_SPECIAL && minecraft != null) {
                    minecraft.setScreen(new LoadoutScreen(Mode.SPECIAL_FORCED, null));
                } else {
                    onClose();
                }
            }
        } else if (lobby != null && !lobby.inLobby()) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);

        String title = switch (mode) {
            case CLASS_PICK -> "更改職業（開局後無法更換）";
            case SPECIAL_PICK -> "更改佔點特殊道具（整場一種）";
            case CLASS_FORCED -> "全員已準備：請選擇本場職業";
            case SPECIAL_FORCED -> "佔點模式：請選擇本場特殊道具";
        };
        g.drawString(font, title, left + 10, top + 9, ACCENT, false);

        if (isForced()) {
            LobbyStatePacket lobby = ClientLobbyData.get();
            if (lobby != null) {
                long total = lobby.players().stream().filter(e -> !e.spectator()).count();
                long done = lobby.players().stream().filter(e -> !e.spectator() && e.draftDone()).count();
                String status = "已確認 " + done + " / " + total + " 人";
                g.drawString(font, status, left + panelW - 10 - font.width(status), top + 9, GOOD, false);
            }
        }
        g.fill(left + 10, top + 21, left + panelW - 10, top + 22, BORDER);

        if (isClass(mode)) {
            g.drawString(font, "職業清單（共 9 種）", listX, top + 23, DIM, false);
            g.drawString(font, "職業介紹", infoX, top + 23, DIM, false);
            renderClassList(g, mouseX, mouseY);
            renderClassDetail(g);
        } else {
            g.drawString(font, "可用特殊道具（" + allowedSpecials.size() + " 種）", listX, top + 23, DIM, false);
            g.drawString(font, "道具說明", infoX, top + 23, DIM, false);
            renderSpecialList(g, mouseX, mouseY);
            renderSpecialDetail(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderClassList(GuiGraphics g, int mouseX, int mouseY) {
        ArenaClass[] all = ArenaClass.values();
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, CARD);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < all.length; i++) {
            ArenaClass c = all[i];
            int ry = listY + i * CLASS_ROW_H - scroll;
            if (ry + CLASS_ROW_H < listY || ry > listY + listH) continue;
            boolean sel = i == pickedClass;
            boolean over = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + CLASS_ROW_H
                && mouseY >= listY && mouseY < listY + listH;
            g.fill(listX + 1, ry + 1, listX + listW - 1, ry + CLASS_ROW_H - 1, sel ? SEL : over ? HOVER : 0x12FFFFFF);
            if (sel) g.fill(listX + 1, ry + 1, listX + 3, ry + CLASS_ROW_H - 1, ACCENT);
            g.drawString(font, (sel ? "▸ " : "") + c.displayName, listX + 6, ry + 5, sel ? ACCENT : TEXT, false);
            String hp = c.maxHealth + " HP";
            int hpColor = c.maxHealth >= 110 ? 0xFF60E080 : c.maxHealth <= 80 ? 0xFFFF8070 : DIM;
            g.drawString(font, hp, listX + listW - 6 - font.width(hp), ry + 5, hpColor, false);
        }
        g.disableScissor();
    }

    private void renderClassDetail(GuiGraphics g) {
        ArenaClass c = ArenaClass.byOrdinal(pickedClass);
        g.fill(infoX - 1, infoY - 1, infoX + infoW + 1, infoY + infoH + 1, BORDER);
        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, CARD);
        int x = infoX + 8, y = infoY + 8, maxW = infoW - 16;
        g.drawString(font, c.displayName, x, y, ACCENT, false);
        String hp = "最大血量：" + c.maxHealth + " HP";
        g.drawString(font, hp, infoX + infoW - 8 - font.width(hp), y, GOOD, false);
        y += 14;
        g.fill(x, y, infoX + infoW - 8, y + 1, BORDER);
        y += 6;
        y = drawWrapped(g, "限定槍械：" + c.gunSummary(), x, y, maxW, 0xFF80D8FF);
        y += 4;
        y = drawWrapped(g, "基礎補品：2 個 AI-2 急救包（擊殺／助攻／重生補滿）", x, y, maxW, DIM);
        y += 6;
        g.drawString(font, "專屬特性與被動：", x, y, ACCENT, false);
        y += 11;
        y = drawWrapped(g, c.description, x, y, maxW, TEXT);
        if (isForced()) {
            y += 10;
            String note = confirmedLocal ? "✔ 你已確認選擇，正在等待其他玩家…" : "點左下角「確認職業」完成選擇";
            drawWrapped(g, note, x, y, maxW, confirmedLocal ? GOOD : ACCENT);
        }
    }

    private void renderSpecialList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, CARD);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < allowedSpecials.size(); i++) {
            SpecialType t = allowedSpecials.get(i);
            int ry = listY + i * SPECIAL_ROW_H - scroll;
            if (ry + SPECIAL_ROW_H < listY || ry > listY + listH) continue;
            boolean sel = t.ordinal() == pickedSpecialOrdinal;
            boolean over = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + SPECIAL_ROW_H
                && mouseY >= listY && mouseY < listY + listH;
            g.fill(listX + 1, ry + 1, listX + listW - 1, ry + SPECIAL_ROW_H - 1, sel ? SEL : over ? HOVER : 0x10FFFFFF);
            if (sel) g.fill(listX + 1, ry + 1, listX + 3, ry + SPECIAL_ROW_H - 1, ACCENT);
            int tier = DomGear.tier(t);
            String tag = "【" + DomGear.tierName(tier) + "】";
            int tagColor = tier == 3 ? 0xFFFF7070 : tier == 2 ? 0xFFE8D060 : 0xFF70D0FF;
            g.drawString(font, tag, listX + 5, ry + 4, tagColor, false);
            String right = DomGear.cooldownRounds(tier) + "回/+" + DomGear.markupPercent(tier) + "%";
            g.drawString(font, right, listX + listW - 5 - font.width(right), ry + 4, DIM, false);
            int nx = listX + 5 + font.width(tag);
            g.drawString(font, trim(t.zh, listW - (nx - listX) - font.width(right) - 7), nx, ry + 4, sel ? ACCENT : TEXT, false);
        }
        g.disableScissor();
    }

    private void renderSpecialDetail(GuiGraphics g) {
        g.fill(infoX - 1, infoY - 1, infoX + infoW + 1, infoY + infoH + 1, BORDER);
        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, CARD);
        SpecialType[] all = SpecialType.values();
        if (pickedSpecialOrdinal < 0 || pickedSpecialOrdinal >= all.length) return;
        SpecialType t = all[pickedSpecialOrdinal];
        int tier = DomGear.tier(t);
        int x = infoX + 8, y = infoY + 8, maxW = infoW - 16;
        g.drawString(font, trim(t.zh, maxW), x, y, ACCENT, false);
        y += 13;
        int tierColor = tier == 3 ? 0xFFFF7070 : tier == 2 ? 0xFFE8D060 : 0xFF70D0FF;
        g.drawString(font, "強度：" + DomGear.tierName(tier) + "｜" + DomGear.cooldownLabel(tier) + "｜商店 +" + DomGear.markupPercent(tier) + "%", x, y, tierColor, false);
        y += 13;
        g.fill(x, y, infoX + infoW - 8, y + 1, BORDER);
        y += 6;
        g.drawString(font, "道具效果：", x, y, DIM, false);
        y += 11;
        y = drawWrapped(g, t.desc, x, y, maxW, TEXT);
        y += 8;
        y = drawWrapped(g, "佔點規則：整場固定使用這一種（按 Q 使用），使用後依強度冷卻（弱 1／中 2／強 3 回合）。", x, y, maxW, DIM);
        if (isForced()) {
            y += 8;
            String note = confirmedLocal ? "✔ 你已確認選擇，正在等待其他玩家…" : "點左下角「確認特殊道具」完成選擇";
            drawWrapped(g, note, x, y, maxW, confirmedLocal ? GOOD : ACCENT);
        }
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
            if (isClass(mode)) {
                int idx = (int) ((my - listY + scroll) / CLASS_ROW_H);
                ArenaClass[] all = ArenaClass.values();
                if (idx >= 0 && idx < all.length) {
                    PGButton.playClick(1.0f);
                    pickedClass = idx;
                    if (mode == Mode.CLASS_PICK) {
                        PGNetwork.sendToServer(new LobbyActionPacket(LobbyManager.Action.SET_MY_CLASS, idx));
                    }
                    return true;
                }
            } else {
                int idx = (int) ((my - listY + scroll) / SPECIAL_ROW_H);
                if (idx >= 0 && idx < allowedSpecials.size()) {
                    PGButton.playClick(1.0f);
                    pickedSpecialOrdinal = allowedSpecials.get(idx).ordinal();
                    if (mode == Mode.SPECIAL_PICK) {
                        PGNetwork.sendToServer(new DomGearActionPacket(pickedSpecialOrdinal));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int rowH = isClass(mode) ? CLASS_ROW_H : SPECIAL_ROW_H;
            int total = isClass(mode) ? ArenaClass.values().length : allowedSpecials.size();
            int max = Math.max(0, total * rowH - listH);
            scroll = Math.max(0, Math.min(max, scroll - (int) (delta * rowH * 2)));
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
