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
 *   左邊大分類（職業、槍械、配件、近戰／投擲、防具、其他、轉帳），格子上方一排小分類（例如槍械 → 手槍、步槍…；
 *   配件 → 可裝手上的槍、瞄具、槍口…）；右邊是商品格，點一下購買。手上槍可以裝的配件格子背景是橘黃色。
 *   底部「垃圾桶」：打開一個暫時的箱子，放進去的東西在關掉時刪除。
 *   自由人顯示八折價。只能在自己的基地買（伺服器檢查，介面上也會提示）。
 *   槍王之王沒有商店：只顯示戰況與「選單」按鈕。
 */
public final class ShopScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF,
        DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, BAD = 0xFFE04848, HOVER = 0x30FFFFFF;
    private static final String TAB_CLASS = "職業", TAB_TRANSFER = "轉帳";
    /** 大分類（左欄）→ 小分類（格子上方一排）。計畫書 6 B7：先分大方向再細分，畫面不再一長串。 */
    private static final String MAIN_GUNS = "槍械", MAIN_ATTACH = "配件", MAIN_GEAR = "近戰／投擲", MAIN_ARMOR = "防具", MAIN_OTHER = "其他";
    private static final List<String> GUN_CATS = List.of("手槍", "衝鋒槍", "散彈槍", "步槍", "機槍", "狙擊槍", "重武器", "其他槍械");
    private static final List<String> ATTACH_SUBS = List.of("全部", "可裝手上的槍", "瞄具", "槍口", "握把", "槍托", "彈匣", "雷射", "特殊彈藥", "其他");
    private static final List<String> GEAR_CATS = List.of("近戰", "投擲物");
    private static String sub = "";
    /** 手上槍可以裝的配件：格子背景改成這個顏色（計畫書 6 B6）。 */
    private static final int COMPAT_BG = 0x60FFB300;
    private static final int CELL_W = 118, CELL_H = 24;

    private static String tab = "";
    private static int amount = 50;
    private static final Map<Integer, ItemStack> ICONS = new HashMap<>();

    private int panelW, panelH, left, top, gridX, gridY, gridW, gridH, scroll;
    private int seen = -1;
    /** 商品格（null = 空格：配件分組的標題列、防具沒有的部位）。 */
    private final List<ArenaData.ShopItem> visible = new ArrayList<>();
    /** 配件「全部」的分組標題：格子編號（一列的開頭）→ 組名（計畫書 7 A7）。 */
    private final Map<Integer, String> headers = new HashMap<>();
    /** 防具頁顯示的材質（ArenaMode.ARMOR_SETS 的順序；只放商店裡有的）。 */
    private final List<Integer> armorCols = new ArrayList<>();

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
        // 垃圾桶（計畫書 6 B8）：把身上的東西丟進去，關掉垃圾桶畫面時才真的刪掉
        addRenderableWidget(new PGButton(left + 150, by, 64, 16, Component.literal("垃圾桶"), false, false,
            () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.OPEN_TRASH, 0))));

        // 左欄：大分類
        List<String> tabs = new ArrayList<>();
        if (s.classes()) tabs.add(TAB_CLASS);
        if (ArenaData.shop().stream().anyMatch(i -> GUN_CATS.contains(i.category()))) tabs.add(MAIN_GUNS);
        if (hasCat("配件")) tabs.add(MAIN_ATTACH);
        if (ArenaData.shop().stream().anyMatch(i -> GEAR_CATS.contains(i.category()))) tabs.add(MAIN_GEAR);
        if (hasCat("防具")) tabs.add(MAIN_ARMOR);
        if (ArenaData.shop().stream().anyMatch(i -> isOther(i.category()))) tabs.add(MAIN_OTHER);
        tabs.add(TAB_TRANSFER);
        if (!tabs.contains(tab)) tab = tabs.contains(MAIN_GUNS) ? MAIN_GUNS : tabs.get(0);
        int tx = left + 10, ty = top + 30;
        for (String t : tabs) {
            final String name = t;
            addRenderableWidget(new PGButton(tx, ty, 74, 14, Component.literal(t), t.equals(tab), false, () -> {
                tab = name;
                sub = "";
                scroll = 0;
                rebuildWidgets();
            }));
            ty += 18;
        }
        gridX = left + 92;
        gridY = top + 30;
        gridW = panelW - 102;

        // 小分類：格子上方一排（放不下自動換行）
        List<String> subs = subsOf(tab);
        if (!subs.isEmpty()) {
            if (!subs.contains(sub)) sub = subs.get(0);
            int sx = gridX, sy = gridY;
            for (String t : subs) {
                int w = font.width(t) + 10;
                if (sx + w > gridX + gridW) { sx = gridX; sy += 16; }
                final String name = t;
                addRenderableWidget(new PGButton(sx, sy, w, 14, Component.literal(t), t.equals(sub), false, () -> {
                    sub = name;
                    scroll = 0;
                    rebuildWidgets();
                }));
                sx += w + 3;
            }
            gridY = sy + 18;
        }
        gridH = top + panelH - 32 - gridY;

        visible.clear();
        headers.clear();
        armorCols.clear();
        if (tab.equals(TAB_CLASS)) initClasses(s);
        else if (tab.equals(TAB_TRANSFER)) initTransfer(s);
        else if (tab.equals(MAIN_ARMOR)) initArmor(s);
        else if (tab.equals(MAIN_ATTACH) && sub.equals("全部")) initAttachGroups();
        else for (ArenaData.ShopItem i : ArenaData.shop()) if (matches(i)) visible.add(i);
    }

    /** 配件「全部」：同類型排在一起，每組從新的一列開始、上面一列是組名（計畫書 7 A7）。 */
    private void initAttachGroups() {
        int cols = cols();
        for (String g : ATTACH_SUBS.subList(2, ATTACH_SUBS.size())) {
            List<ArenaData.ShopItem> list = new ArrayList<>();
            for (ArenaData.ShopItem i : ArenaData.shop()) if (i.category().equals("配件") && attachType(i).equals(g)) list.add(i);
            if (list.isEmpty()) continue;
            while (visible.size() % cols != 0) visible.add(null);
            headers.put(visible.size(), g + "（" + list.size() + "）");
            for (int k = 0; k < cols; k++) visible.add(null);
            visible.addAll(list);
        }
    }

    /** 防具（計畫書 7 A8）：由左到右是材質（照強度）、由上到下是 帽、胸甲、褲、靴；每一欄下面有「購買整套」。 */
    private void initArmor(ArenaStatePacket s) {
        for (int m = 0; m < com.provinggrounds.modes.arena.ArenaMode.ARMOR_SETS.length; m++) {
            for (ArenaData.ShopItem i : com.provinggrounds.modes.arena.ArenaMode.armorSet(m)) if (i != null) { armorCols.add(m); break; }
        }
        int cols = cols();
        for (int k = 0; k < 4; k++) {
            for (int c = 0; c < cols; c++) visible.add(c < armorCols.size() ? com.provinggrounds.modes.arena.ArenaMode.armorSet(armorCols.get(c))[k] : null);
        }
        // 不屬於套裝的防具（例如海龜殼）：放在最後
        int setCount = visible.size();
        for (ArenaData.ShopItem i : ArenaData.shop()) {
            if (i.category().equals("防具") && !visible.subList(0, setCount).contains(i)) visible.add(i);
        }
        boolean free = s.classOrdinal() == ArenaClass.FREELANCER.ordinal();
        int cw = cellW();
        for (int c = 0; c < armorCols.size(); c++) {
            int set = armorCols.get(c), total = 0;
            for (ArenaData.ShopItem i : com.provinggrounds.modes.arena.ArenaMode.armorSet(set)) if (i != null) total += free ? i.free() : i.price();
            boolean enough = s.money() >= total;
            String label = enough ? "整套 $" + total : "還差 $" + (total - s.money());
            PGButton b = new PGButton(gridX + c * cw, gridY + 4 * CELL_H + 2, cw - 2, 16, Component.literal(label), enough, false,
                () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.BUY_SET, set)));
            b.active = enough && s.canShop();
            addRenderableWidget(b);
        }
        // 套裝以外的防具往下排：留出「購買整套」那一列
        if (visible.size() > setCount) {
            List<ArenaData.ShopItem> rest = new ArrayList<>(visible.subList(setCount, visible.size()));
            visible.subList(setCount, visible.size()).clear();
            for (int k = 0; k < cols; k++) visible.add(null);
            visible.addAll(rest);
        }
    }

    /** 格子寬度：防具頁要放得下所有材質，其他頁固定。 */
    private int cellW() {
        if (tab.equals(MAIN_ARMOR) && !armorCols.isEmpty()) return Math.max(70, gridW / Math.max(1, armorCols.size()));
        return CELL_W;
    }

    private static boolean hasCat(String c) {
        return ArenaData.shop().stream().anyMatch(i -> i.category().equals(c));
    }

    private static boolean isOther(String c) {
        return !GUN_CATS.contains(c) && !GEAR_CATS.contains(c) && !c.equals("配件") && !c.equals("防具");
    }

    private static List<String> subsOf(String main) {
        if (main.equals(MAIN_GUNS)) return GUN_CATS.stream().filter(ShopScreen::hasCat).toList();
        if (main.equals(MAIN_ATTACH)) return ATTACH_SUBS;
        if (main.equals(MAIN_GEAR)) return GEAR_CATS.stream().filter(ShopScreen::hasCat).toList();
        return List.of();
    }

    /** 這個商品在目前的大分類／小分類裡嗎。 */
    private boolean matches(ArenaData.ShopItem i) {
        String c = i.category();
        return switch (tab) {
            case MAIN_GUNS, MAIN_GEAR -> c.equals(sub);
            case MAIN_ARMOR -> c.equals("防具");
            case MAIN_OTHER -> isOther(c);
            case MAIN_ATTACH -> c.equals("配件") && switch (sub) {
                case "全部" -> true;
                case "可裝手上的槍" -> compatible(i);
                default -> attachType(i).equals(sub);
            };
            default -> false;
        };
    }

    /** 配件的種類：看配件 ID 裡的關鍵字（TACZ 沒有給分類，這樣最穩）。 */
    private static String attachType(ArenaData.ShopItem i) {
        if (!i.atype().isEmpty()) return i.atype(); // 槍械包裡的類型（tools/sort_shop_attachments.py）
        String s = i.item().toLowerCase(java.util.Locale.ROOT);
        if (s.contains("ammo_mod")) return "特殊彈藥";
        if (s.contains("scope") || s.contains("sight")) return "瞄具";
        if (s.contains("silencer") || s.contains("muzzle") || s.contains("flash_hider") || s.contains("bayonet") || s.contains("barrel")) return "槍口";
        if (s.contains("grip")) return "握把";
        if (s.contains("stock")) return "槍托";
        if (s.contains("mag")) return "彈匣";
        if (s.contains("laser")) return "雷射";
        return "其他";
    }

    /** 這個配件能不能裝在手上的槍（透過槍械適配層，TACZ 自己的相容資料）。 */
    private boolean compatible(ArenaData.ShopItem i) {
        if (minecraft == null || minecraft.player == null) return false;
        if (!com.provinggrounds.compat.GunCompat.holdsGun(minecraft.player)) return false;
        ItemStack gun = minecraft.player.getMainHandItem();
        return COMPAT_CACHE.computeIfAbsent(i.index(), k -> com.provinggrounds.compat.GunCompat.canAttach(gun, icon(i)));
    }

    /** 相容結果快取（手上的槍換了就重新算）。 */
    private static final Map<Integer, Boolean> COMPAT_CACHE = new HashMap<>();
    private static ItemStack compatGun = ItemStack.EMPTY;

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
        if (minecraft != null && minecraft.player != null && !ItemStack.isSameItemSameTags(minecraft.player.getMainHandItem(), compatGun)) {
            compatGun = minecraft.player.getMainHandItem().copy();
            COMPAT_CACHE.clear();
            if (tab.equals(MAIN_ATTACH) && sub.equals("可裝手上的槍")) { rebuildWidgets(); return; }
        }
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

    private int cols() {
        if (tab.equals(MAIN_ARMOR) && !armorCols.isEmpty()) return armorCols.size();
        return Math.max(1, gridW / CELL_W);
    }

    private void renderGrid(GuiGraphics g, ArenaStatePacket s, int mouseX, int mouseY) {
        boolean free = s.classOrdinal() == ArenaClass.FREELANCER.ordinal();
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int cols = cols();
        ArenaData.ShopItem hovered = null;
        int cw = cellW();
        for (int i = 0; i < visible.size(); i++) {
            ArenaData.ShopItem item = visible.get(i);
            int cx = gridX + (i % cols) * cw, cy = gridY + (i / cols) * CELL_H - scroll;
            if (cy + CELL_H < gridY || cy > gridY + gridH) continue;
            if (item == null) {
                String h = headers.get(i);
                if (h != null) { // 配件分組標題（計畫書 7 A7）
                    g.drawString(font, h, cx + 2, cy + 10, ACCENT, false);
                    g.fill(cx + 4 + font.width(h), cy + 14, gridX + gridW - 4, cy + 15, 0x40FFFFFF);
                }
                continue;
            }
            boolean over = mouseX >= cx && mouseX < cx + cw - 2 && mouseY >= cy && mouseY < cy + CELL_H - 2
                && mouseY >= gridY && mouseY < gridY + gridH;
            if (over) hovered = item;
            g.fill(cx, cy, cx + cw - 2, cy + CELL_H - 2, over ? HOVER : 0x18FFFFFF);
            if (item.category().equals("配件") && compatible(item)) g.fill(cx, cy, cx + cw - 2, cy + CELL_H - 2, COMPAT_BG); // 手上的槍可以裝
            g.renderItem(icon(item), cx + 3, cy + 3);
            int price = free ? item.free() : item.price();
            g.drawString(font, trim(item.name(), cw - 26), cx + 22, cy + 3, TEXT, false);
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
        int col = (int) ((mx - gridX) / cellW()), row = (int) ((my - gridY + scroll) / CELL_H);
        if (col >= cols()) return false;
        int i = row * cols() + col;
        if (i < 0 || i >= visible.size() || visible.get(i) == null) return false;
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
