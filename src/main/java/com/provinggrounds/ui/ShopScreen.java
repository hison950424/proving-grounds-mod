package com.provinggrounds.ui;

import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.modes.arena.ArenaClass;
import com.provinggrounds.modes.arena.ArenaData;
import com.provinggrounds.modes.br.RespawnKit;
import com.provinggrounds.network.ArenaActionPacket;
import com.provinggrounds.network.ArenaStatePacket;
import com.provinggrounds.network.MenuRequestPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 檔案: ShopScreen.java
 * 目的: 團隊死鬥／佔點的 UI 商店（M 開啟，取代舊版的書本商店）。
 *   左邊分類（手槍、衝鋒槍…配件、防具）＋「職業」＋「轉帳」；右邊是商品格，點一下購買。
 *   自由人顯示八折價。只能在自己的基地買（伺服器檢查，介面上也會提示）。
 *   槍王之王沒有商店：只顯示戰況與「選單」按鈕。
 */
public final class ShopScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF,
        DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, BAD = 0xFFE04848, HOVER = 0x30FFFFFF;
    private static final String TAB_CLASS = "職業", TAB_TRANSFER = "轉帳";
    private static final int CELL_W = 118, CELL_H = 24;

    private static String tab = "";
    private static int amount = 50;
    private static final Map<Integer, ItemStack> ICONS = new HashMap<>();

    private int panelW, panelH, left, top, gridX, gridY, gridW, gridH, scroll;
    private int seen = -1;
    private final List<ArenaData.ShopItem> visible = new ArrayList<>();

    public ShopScreen() {
        super(Component.literal("商店"));
    }

    @Override
    protected void init() {
        panelW = Math.min(460, width - 16);
        panelH = Math.min(280, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        seen = ClientArenaState.version();
        ArenaStatePacket s = ClientArenaState.get();
        int by = top + panelH - 23; // 介面大小 3 為基準：底部按鈕高 16
        addRenderableWidget(new PGButton(left + 10, by, 64, 16, Component.literal("選單"), false, false, () -> {
            MainMenuScreen.openLobbyView();
            PGNetwork.sendToServer(new MenuRequestPacket());
            minecraft.setScreen(new MainMenuScreen());
        }));
        addRenderableWidget(new PGButton(left + 80, by, 64, 16, Component.literal("HUD 設定"), false, false,
            () -> minecraft.setScreen(new HudEditScreen(this)))); // 比賽中也能調 HUD 版面
        addRenderableWidget(new PGButton(left + panelW - 74, by, 64, 16, Component.literal("關閉"), false, false, this::onClose));
        if (!s.shop()) return;

        List<String> tabs = new ArrayList<>(ArenaData.shopCategories());
        if (s.classes()) tabs.add(TAB_CLASS);
        tabs.add(TAB_TRANSFER);
        if (!tabs.contains(tab)) tab = tabs.get(0);
        int tx = left + 10, ty = top + 30;
        // 分類按鈕高 14、間距 4；分類太多放不下時自動縮小間距
        int tabPitch = Math.max(15, Math.min(18, (panelH - 30 - 32) / Math.max(1, tabs.size())));
        for (String t : tabs) {
            final String name = t;
            addRenderableWidget(new PGButton(tx, ty, 74, 14, Component.literal(t), t.equals(tab), false, () -> {
                tab = name;
                scroll = 0;
                rebuildWidgets();
            }));
            ty += tabPitch;
        }
        gridX = left + 92;
        gridY = top + 30;
        gridW = panelW - 102;
        gridH = panelH - 30 - 32;

        visible.clear();
        if (tab.equals(TAB_CLASS)) initClasses(s);
        else if (tab.equals(TAB_TRANSFER)) initTransfer(s);
        else for (ArenaData.ShopItem i : ArenaData.shop()) if (i.category().equals(tab)) visible.add(i);
    }

    private void initClasses(ArenaStatePacket s) {
        int y = gridY;
        for (ArenaClass c : ArenaClass.values()) {
            boolean mine = c.ordinal() == s.classOrdinal();
            PGButton b = new PGButton(gridX, y + 2, 90, 16, Component.literal(c.displayName), mine, false,
                () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.SET_CLASS, c.ordinal())));
            b.active = !mine && s.canShop();
            addRenderableWidget(b);
            y += 30;
        }
    }

    private void initTransfer(ArenaStatePacket s) {
        int x = gridX, y = gridY;
        int[] steps = { -500, -50, -5, 5, 50, 500 };
        for (int i = 0; i < steps.length; i++) {
            final int d = steps[i];
            int bx = i < 3 ? x + i * 34 : x + 160 + (i - 3) * 34;
            addRenderableWidget(new PGButton(bx, y, 32, 16, Component.literal((d > 0 ? "+" : "") + d), false, false, () -> {
                amount = Math.max(0, amount + d);
                rebuildWidgets();
            }));
        }
        addRenderableWidget(new PGButton(x + 266, y, 44, 16, Component.literal("全部"), amount == 0, false, () -> {
            amount = 0;
            rebuildWidgets();
        }));
        y += 26;
        for (ArenaStatePacket.Mate m : s.mates()) {
            PGButton b = new PGButton(x + gridW - 70, y, 64, 16, Component.literal("轉帳"), true, false,
                () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.TRANSFER, amount, m.id())));
            b.active = s.transferCooldown() <= 0 && s.money() > 0;
            addRenderableWidget(b);
            y += 20;
        }
    }

    @Override
    public void tick() {
        if (!ClientArenaState.active()) { onClose(); return; }
        if (seen != ClientArenaState.version()) {
            // 金錢、在不在基地、冷卻有變：重建按鈕（保留捲動位置）
            int keep = scroll;
            rebuildWidgets();
            scroll = keep;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ArenaStatePacket s = ClientArenaState.get();
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);
        g.drawString(font, s.title(), left + 10, top + 9, ACCENT, false);
        if (s.shop()) {
            String money = "$" + s.money();
            g.drawString(font, money, left + panelW - 10 - font.width(money), top + 9, GOOD, false);
            String where = s.canShop() ? "在基地內：可以購買" : "不在基地：回到自己的基地才能購買";
            g.drawString(font, where, left + panelW - 20 - font.width(money) - font.width(where), top + 9, s.canShop() ? DIM : BAD, false);
            if (tab.equals(TAB_CLASS)) renderClasses(g, s);
            else if (tab.equals(TAB_TRANSFER)) renderTransfer(g, s);
            else renderGrid(g, s, mouseX, mouseY);
        } else {
            int y = top + 32;
            for (var l : s.scores()) {
                g.drawString(font, l.label(), left + 14, y, 0xFF000000 | l.color(), false);
                String r = "第 " + Math.min(l.value(), l.target()) + "/" + l.target() + " 階　" + l.extra();
                g.drawString(font, r, left + panelW - 14 - font.width(r), y, TEXT, false);
                y += 12;
            }
            g.drawString(font, s.status(), left + 14, y + 6, DIM, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int cols() { return Math.max(1, gridW / CELL_W); }

    private void renderGrid(GuiGraphics g, ArenaStatePacket s, int mouseX, int mouseY) {
        boolean free = s.classOrdinal() == ArenaClass.FREELANCER.ordinal();
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int cols = cols();
        ArenaData.ShopItem hovered = null;
        for (int i = 0; i < visible.size(); i++) {
            ArenaData.ShopItem item = visible.get(i);
            int cx = gridX + (i % cols) * CELL_W, cy = gridY + (i / cols) * CELL_H - scroll;
            if (cy + CELL_H < gridY || cy > gridY + gridH) continue;
            boolean over = mouseX >= cx && mouseX < cx + CELL_W - 2 && mouseY >= cy && mouseY < cy + CELL_H - 2
                && mouseY >= gridY && mouseY < gridY + gridH;
            if (over) hovered = item;
            g.fill(cx, cy, cx + CELL_W - 2, cy + CELL_H - 2, over ? HOVER : 0x18FFFFFF);
            g.renderItem(icon(item), cx + 3, cy + 3);
            int price = free ? item.free() : item.price();
            g.drawString(font, trim(item.name(), CELL_W - 26), cx + 22, cy + 3, TEXT, false);
            g.drawString(font, "$" + price, cx + 22, cy + 13, s.money() >= price ? GOOD : BAD, false);
        }
        g.disableScissor();
        if (hovered != null) {
            int price = free ? hovered.free() : hovered.price();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.name()));
            tip.add(Component.literal("價格 $" + price + (free ? "（自由人八折）" : "")));
            tip.add(Component.literal(s.canShop() ? "點一下購買" : "回基地才能購買"));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void renderClasses(GuiGraphics g, ArenaStatePacket s) {
        int y = gridY;
        for (ArenaClass c : ArenaClass.values()) {
            g.drawString(font, "血量 " + c.maxHealth + "　" + c.description, gridX + 96, y + 2, TEXT, false);
            g.drawString(font, "起始槍 " + c.gun, gridX + 96, y + 12, DIM, false);
            y += 30;
        }
        if (!s.canShop()) g.drawString(font, "回到基地才能換職業", gridX, y + 4, BAD, false);
    }

    private void renderTransfer(GuiGraphics g, ArenaStatePacket s) {
        String amt = amount == 0 ? "全部" : "$" + amount;
        g.drawCenteredString(font, amt, gridX + 133, gridY + 4, ACCENT);
        int y = gridY + 26;
        if (s.mates().isEmpty()) g.drawString(font, "沒有隊友", gridX, y + 4, DIM, false);
        for (ArenaStatePacket.Mate m : s.mates()) {
            g.drawString(font, m.name(), gridX, y + 4, TEXT, false);
            g.drawString(font, "$" + m.money(), gridX + 120, y + 4, GOOD, false);
            y += 20;
        }
        if (s.transferCooldown() > 0) g.drawString(font, "轉帳冷卻 " + s.transferCooldown() + " 秒", gridX, y + 6, BAD, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!ClientArenaState.get().shop() || tab.equals(TAB_CLASS) || tab.equals(TAB_TRANSFER)) return false;
        if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridY + gridH) return false;
        int col = (int) ((mx - gridX) / CELL_W), row = (int) ((my - gridY + scroll) / CELL_H);
        if (col >= cols()) return false;
        int i = row * cols() + col;
        if (i < 0 || i >= visible.size()) return false;
        PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.BUY, visible.get(i).index()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int rows = (visible.size() + cols() - 1) / cols();
        int max = Math.max(0, rows * CELL_H - gridH);
        scroll = Math.max(0, Math.min(max, scroll - (int) (delta * CELL_H)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientModEvents.OPEN_MENU.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static ItemStack icon(ArenaData.ShopItem item) {
        return ICONS.computeIfAbsent(item.index(), k -> RespawnKit.parse(item.item()));
    }

    private String trim(String s, int w) {
        if (font.width(s) <= w) return s;
        while (s.length() > 1 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
