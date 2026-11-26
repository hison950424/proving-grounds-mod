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
 * 目的: 團隊死鬥／佔點的 UI 商店（M 開啟，計畫書 9 改版）。
 *   - 取消局內更換職業（開局 10 秒由 LoadoutScreen 選角，準備期可點底部「選角」鈕回去調整）。
 *   - 依職業限定槍械分類顯示對應的槍械與可裝配件（自由人無限制且八折）。
 *   - 「可裝手上的槍」與「全部」配件都在同一個頁面中依配件種類（瞄具、槍口、握把…）分組顯示。
 *   - 底部「垃圾桶」：打開暫時箱子，關掉時刪除裡面的東西。
 */
public final class ShopScreen extends Screen {

    private static final int BG = 0xEB0F141E, BORDER = 0xFF2A3344, ACCENT = 0xFFE8D060, TEXT = 0xFFFFFFFF,
        DIM = 0xFFA0A8B8, GOOD = 0xFF4CC864, BAD = 0xFFE04848, HOVER = 0x30FFFFFF;
    private static final String TAB_TRANSFER = "轉帳", TAB_CLASS_INFO = "職業介紹";
    private static final String MAIN_GUNS = "槍械", MAIN_ATTACH = "配件", MAIN_GEAR = "近戰／投擲", MAIN_ARMOR = "防具", MAIN_OTHER = "其他";
    private static final List<String> GUN_CATS = List.of("手槍", "衝鋒槍", "散彈槍", "步槍", "機槍", "狙擊槍", "重武器");
    private static final List<String> ATTACH_SUBS = List.of("可裝手上的槍", "全部", "瞄具", "槍口", "握把", "槍托", "彈匣", "雷射", "特殊彈藥", "其他");
    private static final List<String> ATTACH_TYPES = List.of("瞄具", "槍口", "握把", "槍托", "彈匣", "雷射", "特殊彈藥", "其他");
    private static final List<String> GEAR_CATS = List.of("近戰", "投擲物");
    private static String sub = "";
    private static final int COMPAT_BG = 0x60FFB300;
    private static final int CELL_W = 118, CELL_H = 24;

    private static String tab = "";
    private static int amount = 50;
    private static final Map<Integer, ItemStack> ICONS = new HashMap<>();

    private int panelW, panelH, left, top, gridX, gridY, gridW, gridH, scroll;
    private int seen = -1;
    private final List<ArenaData.ShopItem> visible = new ArrayList<>();
    private final Map<Integer, String> headers = new HashMap<>();
    private final List<Integer> armorCols = new ArrayList<>();

    public ShopScreen() {
        super(Component.literal("商店"));
    }

    private ArenaClass myClass(ArenaStatePacket s) {
        return s.classes() ? ArenaClass.byOrdinal(s.classOrdinal()) : ArenaClass.FREELANCER;
    }

    @Override
    protected void init() {
        panelW = Math.min(460, width - 16);
        panelH = Math.min(280, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        seen = ClientArenaState.version();
        ArenaStatePacket s = ClientArenaState.get();
        ArenaClass cls = myClass(s);
        int by = top + panelH - 23;
        addRenderableWidget(new PGButton(left + 10, by, 58, 16, Component.literal("選單"), false, false, () -> {
            MainMenuScreen.openLobbyView();
            PGNetwork.sendToServer(new MenuRequestPacket());
            minecraft.setScreen(new MainMenuScreen());
        }));
        addRenderableWidget(new PGButton(left + 72, by, 60, 16, Component.literal("HUD 設定"), false, false,
            () -> minecraft.setScreen(new HudEditScreen(this))));
        addRenderableWidget(new PGButton(left + panelW - 70, by, 60, 16, Component.literal("關閉"), false, false, this::onClose));
        if (!s.shop()) return;
        addRenderableWidget(new PGButton(left + 136, by, 58, 16, Component.literal("垃圾桶"), false, false,
            () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.OPEN_TRASH, 0))));

        // 左欄：大分類（計畫書 10：新增只讀的「職業介紹」分頁，局內仍不可換職業）
        List<String> tabs = new ArrayList<>();
        if (ArenaData.shop().stream().anyMatch(i -> GUN_CATS.contains(i.category()) && cls.canBuyCategory(i.category()))) tabs.add(MAIN_GUNS);
        if (hasCat("配件")) tabs.add(MAIN_ATTACH);
        if (ArenaData.shop().stream().anyMatch(i -> GEAR_CATS.contains(i.category()))) tabs.add(MAIN_GEAR);
        if (hasCat("防具")) tabs.add(MAIN_ARMOR);
        if (ArenaData.shop().stream().anyMatch(i -> isOther(i.category()))) tabs.add(MAIN_OTHER);
        tabs.add(TAB_TRANSFER);
        if (s.classes()) tabs.add(TAB_CLASS_INFO);
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

        List<String> subs = subsOf(tab, cls);
        if (!subs.isEmpty()) {
            if (!subs.contains(sub)) {
                if (tab.equals(MAIN_ATTACH)) {
                    boolean holdingGun = minecraft != null && minecraft.player != null && com.provinggrounds.compat.GunCompat.holdsGun(minecraft.player);
                    sub = holdingGun ? "可裝手上的槍" : "全部";
                } else {
                    sub = subs.get(0);
                }
            }
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
        if (tab.equals(TAB_TRANSFER)) initTransfer(s);
        else if (tab.equals(TAB_CLASS_INFO)) { /* 職業介紹：純展示，無額外按鈕 */ }
        else if (tab.equals(MAIN_ARMOR)) initArmor(s);
        else if (tab.equals(MAIN_ATTACH) && (sub.equals("全部") || sub.equals("可裝手上的槍"))) initAttachGroups(cls, sub.equals("可裝手上的槍"));
        else for (ArenaData.ShopItem i : ArenaData.shop()) if (matches(i, cls)) visible.add(i);
    }

    /** 配件分組顯示（同一個頁面依類型分組：瞄具、槍口、握把…；onlyHeld = 只顯示可裝手上那把槍的配件）。 */
    private void initAttachGroups(ArenaClass cls, boolean onlyHeld) {
        int cols = cols();
        for (String g : ATTACH_TYPES) {
            List<ArenaData.ShopItem> list = new ArrayList<>();
            for (ArenaData.ShopItem i : ArenaData.shop()) {
                if (!i.category().equals("配件") || !attachType(i).equals(g)) continue;
                if (onlyHeld) {
                    if (!compatible(i)) continue;
                } else {
                    if (!classAllowsAttach(i, cls)) continue;
                }
                list.add(i);
            }
            if (list.isEmpty()) continue;
            while (visible.size() % cols != 0) visible.add(null);
            headers.put(visible.size(), g + "（" + list.size() + "）");
            for (int k = 0; k < cols; k++) visible.add(null);
            visible.addAll(list);
        }
    }

    /** 這個配件能否裝在「這個職業能買的任一把商店槍（或手上正在拿的槍）」上。 */
    private boolean classAllowsAttach(ArenaData.ShopItem att, ArenaClass cls) {
        if (compatible(att)) return true;
        if (cls == ArenaClass.FREELANCER) return true;
        return CLASS_ATTACH_CACHE.computeIfAbsent(cls.ordinal() + ":" + att.index(), k -> {
            ItemStack attStack = icon(att);
            for (ArenaData.ShopItem gun : ArenaData.shop()) {
                if (!GUN_CATS.contains(gun.category()) || !cls.canBuyCategory(gun.category())) continue;
                if (com.provinggrounds.compat.GunCompat.canAttach(icon(gun), attStack)) return true;
            }
            return false;
        });
    }

    private static final Map<String, Boolean> CLASS_ATTACH_CACHE = new HashMap<>();

    private void initArmor(ArenaStatePacket s) {
        for (int m = 0; m < com.provinggrounds.modes.arena.ArenaMode.ARMOR_SETS.length; m++) {
            for (ArenaData.ShopItem i : com.provinggrounds.modes.arena.ArenaMode.armorSet(m)) if (i != null) { armorCols.add(m); break; }
        }
        int cols = cols();
        for (int k = 0; k < 4; k++) {
            for (int c = 0; c < cols; c++) visible.add(c < armorCols.size() ? com.provinggrounds.modes.arena.ArenaMode.armorSet(armorCols.get(c))[k] : null);
        }
        int setCount = visible.size();
        for (ArenaData.ShopItem i : ArenaData.shop()) {
            if (i.category().equals("防具") && !visible.subList(0, setCount).contains(i)) visible.add(i);
        }
        boolean free = s.classOrdinal() == ArenaClass.FREELANCER.ordinal();
        int cw = cellW();
        for (int c = 0; c < armorCols.size(); c++) {
            int set = armorCols.get(c), total = 0;
            for (ArenaData.ShopItem i : com.provinggrounds.modes.arena.ArenaMode.armorSet(set)) if (i != null) total += com.provinggrounds.client.ClientDomGear.price(free ? i.free() : i.price());
            boolean enough = s.money() >= total;
            String label = enough ? "整套 $" + total : "還差 $" + (total - s.money());
            PGButton b = new PGButton(gridX + c * cw, gridY + 4 * CELL_H + 2, cw - 2, 16, Component.literal(label), enough, false,
                () -> PGNetwork.sendToServer(new ArenaActionPacket(ArenaActionPacket.Action.BUY_SET, set)));
            b.active = enough && s.canShop();
            addRenderableWidget(b);
        }
        if (visible.size() > setCount) {
            List<ArenaData.ShopItem> rest = new ArrayList<>(visible.subList(setCount, visible.size()));
            visible.subList(setCount, visible.size()).clear();
            for (int k = 0; k < cols; k++) visible.add(null);
            visible.addAll(rest);
        }
    }

    private int cellW() {
        if (tab.equals(MAIN_ARMOR) && !armorCols.isEmpty()) return Math.max(70, gridW / Math.max(1, armorCols.size()));
        return CELL_W;
    }

    private static boolean hasCat(String c) {
        return ArenaData.shop().stream().anyMatch(i -> i.category().equals(c));
    }

    private static boolean isOther(String c) {
        return !GUN_CATS.contains(c) && !GEAR_CATS.contains(c) && !c.equals("配件") && !c.equals("防具") && !c.equals("其他槍械");
    }

    private static List<String> subsOf(String main, ArenaClass cls) {
        if (main.equals(MAIN_GUNS)) return GUN_CATS.stream().filter(c -> cls.canBuyCategory(c) && hasCat(c)).toList();
        if (main.equals(MAIN_ATTACH)) return ATTACH_SUBS;
        if (main.equals(MAIN_GEAR)) return GEAR_CATS.stream().filter(ShopScreen::hasCat).toList();
        return List.of();
    }

    private boolean matches(ArenaData.ShopItem i, ArenaClass cls) {
        String c = i.category();
        return switch (tab) {
            case MAIN_GUNS -> c.equals(sub) && cls.canBuyCategory(c);
            case MAIN_GEAR -> c.equals(sub);
            case MAIN_ARMOR -> c.equals("防具");
            case MAIN_OTHER -> isOther(c);
            case MAIN_ATTACH -> c.equals("配件") && switch (sub) {
                case "全部" -> classAllowsAttach(i, cls);
                case "可裝手上的槍" -> compatible(i);
                default -> attachType(i).equals(sub) && classAllowsAttach(i, cls);
            };
            default -> false;
        };
    }

    private static String attachType(ArenaData.ShopItem i) {
        if (!i.atype().isEmpty()) return i.atype();
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

    private boolean compatible(ArenaData.ShopItem i) {
        if (minecraft == null || minecraft.player == null) return false;
        if (!com.provinggrounds.compat.GunCompat.holdsGun(minecraft.player)) return false;
        ItemStack gun = minecraft.player.getMainHandItem();
        return COMPAT_CACHE.computeIfAbsent(i.index(), k -> com.provinggrounds.compat.GunCompat.canAttach(gun, icon(i)));
    }

    private static final Map<Integer, Boolean> COMPAT_CACHE = new HashMap<>();
    private static ItemStack compatGun = ItemStack.EMPTY;

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
            if (tab.equals(MAIN_ATTACH)) { rebuildWidgets(); return; }
        }
        if (!ClientArenaState.active()) { onClose(); return; }
        if (seen != ClientArenaState.version()) {
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
        String header = s.title() + (s.classes() ? "・" + myClass(s).displayName : "");
        g.drawString(font, header, left + 10, top + 9, ACCENT, false);
        if (s.shop()) {
            String money = "$" + s.money();
            g.drawString(font, money, left + panelW - 10 - font.width(money), top + 9, GOOD, false);
            String where = s.canShop() ? "在基地內：可以購買" : "不在基地：回到自己的基地才能購買";
            g.drawString(font, where, left + panelW - 20 - font.width(money) - font.width(where), top + 9, s.canShop() ? DIM : BAD, false);
            if (tab.equals(TAB_TRANSFER)) renderTransfer(g, s);
            else if (tab.equals(TAB_CLASS_INFO)) renderClassInfo(g, s);
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
        if (visible.isEmpty() && tab.equals(MAIN_ATTACH) && sub.equals("可裝手上的槍")) {
            g.drawString(font, "請先手持一把槍，這裡會依類型列出所有可裝配件", gridX + 6, gridY + 8, DIM, false);
        }
        for (int i = 0; i < visible.size(); i++) {
            ArenaData.ShopItem item = visible.get(i);
            int cx = gridX + (i % cols) * cw, cy = gridY + (i / cols) * CELL_H - scroll;
            if (cy + CELL_H < gridY || cy > gridY + gridH) continue;
            if (item == null) {
                String h = headers.get(i);
                if (h != null) {
                    g.drawString(font, h, cx + 2, cy + 10, ACCENT, false);
                    g.fill(cx + 4 + font.width(h), cy + 14, gridX + gridW - 4, cy + 15, 0x40FFFFFF);
                }
                continue;
            }
            boolean over = mouseX >= cx && mouseX < cx + cw - 2 && mouseY >= cy && mouseY < cy + CELL_H - 2
                && mouseY >= gridY && mouseY < gridY + gridH;
            if (over) hovered = item;
            g.fill(cx, cy, cx + cw - 2, cy + CELL_H - 2, over ? HOVER : 0x18FFFFFF);
            if (item.category().equals("配件") && compatible(item)) g.fill(cx, cy, cx + cw - 2, cy + CELL_H - 2, COMPAT_BG);
            g.renderItem(icon(item), cx + 3, cy + 3);
            int price = com.provinggrounds.client.ClientDomGear.price(free ? item.free() : item.price());
            g.drawString(font, trim(item.name(), cw - 26), cx + 22, cy + 3, TEXT, false);
            g.drawString(font, "$" + price, cx + 22, cy + 13, s.money() >= price ? GOOD : BAD, false);
        }
        g.disableScissor();
        if (hovered != null) {
            int base = free ? hovered.free() : hovered.price();
            int price = com.provinggrounds.client.ClientDomGear.price(base);
            var activeGear = com.provinggrounds.client.ClientDomGear.activeGear();
            int pct = com.provinggrounds.special.DomGear.markupPercent(activeGear);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.name()));
            tip.add(Component.literal("價格 $" + price + (free ? "（自由人八折）" : "")
                + (price != base && pct > 0 ? "（特殊道具 +" + pct + "%）" : "")));
            tip.add(Component.literal(s.canShop() ? "點一下購買" : "回基地才能購買"));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
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

    /** 職業介紹分頁（計畫書 10）：上方高亮顯示自己的職業詳情，下方列出全部 9 種職業供查閱（局內不可更換）。 */
    private void renderClassInfo(GuiGraphics g, ArenaStatePacket s) {
        ArenaClass mine = myClass(s);
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int y = gridY - scroll;
        // 目前職業卡片
        int cardH = 54;
        g.fill(gridX, y, gridX + gridW - 2, y + cardH, 0x35E8D060);
        g.fill(gridX, y, gridX + 2, y + cardH, ACCENT);
        g.drawString(font, "目前職業：" + mine.displayName + "　（血量 " + mine.maxHealth + " HP）", gridX + 6, y + 4, ACCENT, false);
        g.drawString(font, "限定槍械：" + mine.gunSummary(), gridX + 6, y + 16, 0xFF80D8FF, false);
        g.drawString(font, "補品：2 個 AI-2 急救包（擊殺／助攻／重生補滿）", gridX + 6, y + 28, DIM, false);
        g.drawString(font, trim("特性：" + mine.description, gridW - 14), gridX + 6, y + 40, TEXT, false);
        y += cardH + 8;

        g.drawString(font, "全部職業一覽（局內無法更換）", gridX + 2, y, DIM, false);
        y += 12;
        for (ArenaClass c : ArenaClass.values()) {
            boolean isMe = c == mine;
            int rh = 24;
            g.fill(gridX, y, gridX + gridW - 2, y + rh - 2, isMe ? 0x28E8D060 : 0x16FFFFFF);
            String head = (isMe ? "✔ " : "") + c.displayName + "（" + c.maxHealth + " HP）";
            g.drawString(font, head, gridX + 5, y + 3, isMe ? ACCENT : TEXT, false);
            String guns = trim(c.gunSummary(), gridW - font.width(head) - 18);
            g.drawString(font, guns, gridX + gridW - 6 - font.width(guns), y + 3, GOOD, false);
            g.drawString(font, trim(c.description, gridW - 12), gridX + 5, y + 13, DIM, false);
            y += rh;
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!ClientArenaState.get().shop() || tab.equals(TAB_TRANSFER) || tab.equals(TAB_CLASS_INFO)) return false;
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
        if (tab.equals(TAB_CLASS_INFO)) {
            int totalH = 54 + 8 + 12 + ArenaClass.values().length * 24;
            int max = Math.max(0, totalH - gridH);
            scroll = Math.max(0, Math.min(max, scroll - (int) (delta * 24)));
            return true;
        }
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
