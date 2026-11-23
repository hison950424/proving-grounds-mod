package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientLobbyData;
import com.provinggrounds.client.ClientMenuData;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.CirclePreset;
import com.provinggrounds.network.LobbyActionPacket;
import com.provinggrounds.network.LobbyStatePacket;
import com.provinggrounds.network.MenuDataPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: MainMenuScreen.java
 * 目的: 主選單（只在客戶端使用）。按 M 打開。CS2 / Valorant 深色風格。
 *   首頁：大逃殺（大按鈕）、團隊死鬥／據點爭奪／槍戰對決（即將推出，先停用）、底部戰績欄。
 *   大廳頁（所有人都看得到，資料由伺服器 LobbyStatePacket 即時同步）：
 *     左欄：地圖、縮圈、一般／競技、分隊方式、隊伍數、自動分隊、全部移出隊伍（只有管理員能改）
 *     右欄：各隊名單（✔ = 已準備）。點隊伍標題 = 加入那一隊；
 *           管理員把玩家名字拖到另一隊（含孤狼、未分隊、觀戰）即可換隊；或先點名字選取、再點隊伍標題。
 *     底部：準備（登機倒數中可「取消準備」退出本局）／加入遊戲、開始（遊戲中管理員看到「結束遊戲」，按兩次才執行）、強制開始／取消倒數、返回。
 *   所有操作都送 LobbyActionPacket，權限由伺服器檢查。
 */
public class MainMenuScreen extends Screen {

    private static final int BG       = 0xEB0F141E; // #0F141E，透明度 92%
    private static final int BORDER   = 0xFF2A3344;
    private static final int ACCENT   = 0xFFE8D060;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A8B8;
    private static final int WARN     = 0xFFE0A048;
    private static final int READY    = 0xFF4CC864;
    private static final int SELECT   = 0x60E8D060;
    private static final int ROW_H = 11;

    private enum View { HOME, LOBBY }

    // 記住上次的畫面與選取（同一次遊戲啟動期間）
    private static View view = View.HOME;
    @Nullable private static UUID selected;
    /** 「結束遊戲」第一次按下後，這個時間點之前再按一次才會真的結束。 */
    private static long endConfirmUntil = 0;
    private boolean wasConfirming;

    private int panelW, panelH, left, top;
    private int listX, listY, listW, listH, scroll;
    private final List<Row> rows = new ArrayList<>();
    private int seenMenu = -1, seenLobby = -1;

    /** 名單的一列：隊伍標題（entry 為 null）或玩家。team：1.. 隊伍、0 孤狼、-1 未分隊、-2 觀戰。 */
    private record Row(int team, String label, int color, @Nullable LobbyStatePacket.Entry entry) {}

    public MainMenuScreen() {
        super(Tr.c("menu.screen"));
    }

    /** 從大地圖的「選單」按鈕打開時，直接顯示大廳頁（結束遊戲、加入遊戲在這裡）。 */
    public static void openLobbyView() {
        view = View.LOBBY;
    }

    @Override
    protected void init() {
        panelW = Math.min(420, width - 16);
        panelH = Math.min(256, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        seenMenu = ClientMenuData.version();
        seenLobby = ClientLobbyData.version();
        if (view == View.HOME) initHome(); else initLobby();
    }

    // ── 首頁 ────────────────────────────────────────────────────────────────

    private void initHome() {
        int x = left + 14, w = panelW - 28;
        addRenderableWidget(new PGButton(x, top + 36, w, 36, Tr.c("mode.br"), true, false,
            () -> switchTo(View.LOBBY)));
        addModeStub(x, top + 80, w, Tr.t("mode.tdm"));
        addModeStub(x, top + 104, w, Tr.t("mode.dom"));
        addModeStub(x, top + 128, w, Tr.t("mode.gk"));
    }

    private void addModeStub(int x, int y, int w, String name) {
        PGButton b = new PGButton(x, y, w, 20, Component.literal(name), false, false, () -> {}).withTag(Tr.t("mode.coming"));
        b.active = false;
        addRenderableWidget(b);
    }

    // ── 大廳頁 ──────────────────────────────────────────────────────────────

    private void initLobby() {
        MenuDataPacket menu = ClientMenuData.get();
        LobbyStatePacket lobby = ClientLobbyData.get();
        boolean op = menu != null && menu.isOp();
        boolean canEdit = op && lobby != null && lobby.inLobby();
        LobbyStatePacket.Entry me = self(lobby);

        // 左欄：設定
        int sx = left + 12, sw = 150, y = top + 40;
        List<MenuDataPacket.MapEntry> maps = menu != null ? menu.maps() : List.of();
        MenuDataPacket.MapEntry map = findMap(maps, lobby != null ? lobby.mapId() : -1);
        String mapLabel = map == null ? Tr.t("lobby.map_none") : Tr.t("lobby.map", map.id(), map.name());
        setting(sx, y, sw, mapLabel, canEdit && maps.size() > 1, () -> {
            int i = maps.indexOf(map);
            send(LobbyManager.Action.SET_MAP, maps.get((i + 1) % maps.size()).id());
        });
        y += 20;
        CirclePreset preset = lobby != null ? CirclePreset.values()[lobby.presetOrdinal()] : CirclePreset.STANDARD;
        setting(sx, y, sw, Tr.t("lobby.preset", preset.displayName), canEdit,
            () -> send(LobbyManager.Action.SET_PRESET, (preset.ordinal() + 1) % CirclePreset.values().length));
        y += 20;
        BattleRoyaleMode.Variant variant = lobby != null ? BattleRoyaleMode.Variant.values()[lobby.variantOrdinal()] : BattleRoyaleMode.Variant.NORMAL;
        setting(sx, y, sw, Tr.t("lobby.variant", variant.displayName), canEdit,
            () -> send(LobbyManager.Action.SET_VARIANT, (variant.ordinal() + 1) % BattleRoyaleMode.Variant.values().length));
        y += 20;
        boolean special = lobby == null || lobby.special();
        setting(sx, y, sw, Tr.t(special ? "lobby.special_on" : "lobby.special_off"), canEdit,
            () -> send(LobbyManager.Action.SET_SPECIAL, special ? 0 : 1));
        y += 20;
        LobbyManager.TeamMode teamMode = lobby != null ? LobbyManager.TeamMode.values()[lobby.teamModeOrdinal()] : LobbyManager.TeamMode.FREE;
        setting(sx, y, sw, Tr.t("lobby.team_mode", teamMode.displayName), canEdit,
            () -> send(LobbyManager.Action.SET_TEAM_MODE, (teamMode.ordinal() + 1) % LobbyManager.TeamMode.values().length));
        y += 20;
        int teamCount = lobby != null ? lobby.teamCount() : 4;
        setting(sx, y, sw - 44, Tr.t("lobby.team_count", teamCount), false, () -> {});
        setting(sx + sw - 42, y, 20, "－", canEdit && teamCount > 1, () -> send(LobbyManager.Action.SET_TEAM_COUNT, teamCount - 1));
        setting(sx + sw - 20, y, 20, "＋", canEdit && teamCount < LobbyManager.MAX_TEAMS, () -> send(LobbyManager.Action.SET_TEAM_COUNT, teamCount + 1));
        y += 20;
        int half = (sw - 2) / 2;
        setting(sx, y, half, Tr.t("lobby.auto_balance"), canEdit, () -> send(LobbyManager.Action.AUTO_BALANCE, 0));
        setting(sx + sw - half, y, half, Tr.t("lobby.clear_teams"), canEdit, () -> send(LobbyManager.Action.CLEAR_TEAMS, 0));
        y += 24;

        // 管理員：選取玩家後可以取消選取（移動玩家改用拖移或「點玩家 → 點隊伍標題」）
        LobbyStatePacket.Entry sel = find(lobby, selected);
        if (op && sel != null) {
            setting(sx, y, sw, Tr.t("lobby.deselect"), true, () -> { selected = null; rebuildWidgets(); });
        }

        // 右欄：名單
        listX = sx + sw + 10;
        listY = top + 40;
        listW = left + panelW - 12 - listX;
        listH = panelH - 40 - 46;
        buildRows(lobby);

        // 底部按鈕
        int by = top + panelH - 30, bw = (panelW - 24 - 18) / 4, bx = left + 12;
        boolean inLobby = lobby != null && lobby.inLobby();
        if (me != null && me.spectator()) {
            PGButton join = new PGButton(bx, by, bw, 20,
                Tr.c(me.joinPending() ? "lobby.join_pending" : inLobby ? "lobby.join_game" : "lobby.join_after"), true, false,
                () -> send(LobbyManager.Action.JOIN_GAME, 0));
            join.active = !me.joinPending();
            addRenderableWidget(join);
        } else {
            // 登機倒數期間（已開局、還沒上飛機）也能按「取消準備」退出這一局
            boolean preparing = ClientBRState.get().phase() == ClientBRState.PHASE_PREPARING;
            boolean ready = me != null && (me.ready() || preparing);
            PGButton r = new PGButton(bx, by, bw, 20, Tr.c(ready ? "lobby.unready" : "lobby.ready"), !ready, false,
                () -> send(LobbyManager.Action.TOGGLE_READY, 0));
            r.active = (inLobby || preparing) && me != null;
            addRenderableWidget(r);
        }
        boolean counting = lobby != null && lobby.countdownSeconds() >= 0;
        if (op && lobby != null && !inLobby) {
            // 遊戲進行中：開始鍵換成「結束遊戲」，要按兩次（3 秒內）才會執行
            boolean confirming = System.currentTimeMillis() < endConfirmUntil;
            PGButton end = new PGButton(bx + (bw + 6), by, bw, 20, Tr.c(confirming ? "lobby.end_confirm" : "lobby.end"), true, false, () -> {
                if (System.currentTimeMillis() < endConfirmUntil) {
                    endConfirmUntil = 0;
                    send(LobbyManager.Action.END_GAME, 0);
                } else {
                    endConfirmUntil = System.currentTimeMillis() + 3000;
                }
                rebuildWidgets();
            });
            addRenderableWidget(end);
        } else {
            PGButton start = new PGButton(bx + (bw + 6), by, bw, 20, Tr.c("lobby.start"), true, false,
                () -> send(LobbyManager.Action.START, 0));
            start.active = canEdit && !counting;
            addRenderableWidget(start);
        }
        PGButton force = new PGButton(bx + 2 * (bw + 6), by, bw, 20, Tr.c(counting ? "lobby.cancel_countdown" : "lobby.force_start"), false, false,
            () -> send(counting ? LobbyManager.Action.CANCEL_COUNTDOWN : LobbyManager.Action.FORCE_START, 0));
        force.active = canEdit;
        addRenderableWidget(force);
        addRenderableWidget(new PGButton(bx + 3 * (bw + 6), by, bw, 20, Tr.c("common.back"), false, false,
            () -> switchTo(View.HOME)));
    }

    private void setting(int x, int y, int w, String label, boolean active, Runnable action) {
        PGButton b = new PGButton(x, y, w, 18, Component.literal(label), false, false, action);
        b.active = active;
        addRenderableWidget(b);
    }

    /** 依隊伍排好名單：1..N 隊 → 孤狼 → 未分隊 → 觀戰。 */
    private void buildRows(@Nullable LobbyStatePacket lobby) {
        rows.clear();
        if (lobby == null) return;
        for (int t = 1; t <= lobby.teamCount(); t++) addGroup(lobby, t);
        addGroup(lobby, 0);
        List<LobbyStatePacket.Entry> unassigned = lobby.players().stream().filter(e -> !e.spectator() && e.team() < 0).toList();
        if (!unassigned.isEmpty()) {
            rows.add(new Row(-1, Tr.t("lobby.unassigned"), TEXT_DIM, null));
            for (LobbyStatePacket.Entry e : unassigned) rows.add(new Row(-1, e.name(), TEXT, e));
        }
        List<LobbyStatePacket.Entry> specs = lobby.players().stream().filter(LobbyStatePacket.Entry::spectator).toList();
        rows.add(new Row(-2, Tr.t("lobby.spectators", specs.size()), TEXT_DIM, null));
        for (LobbyStatePacket.Entry e : specs) rows.add(new Row(-2, e.name(), TEXT_DIM, e));
        scroll = Math.max(0, Math.min(scroll, rows.size() - listH / ROW_H));
    }

    private void addGroup(LobbyStatePacket lobby, int teamId) {
        TeamManager.Team t = TeamManager.Team.of(teamId);
        List<LobbyStatePacket.Entry> members = lobby.players().stream()
            .filter(e -> !e.spectator() && e.team() == teamId).toList();
        Integer c = t.color.getColor();
        int color = 0xFF000000 | (c != null ? c : 0xFFFFFF);
        rows.add(new Row(teamId, Tr.t("lobby.team_header", t.displayName, members.size()), color, null));
        for (LobbyStatePacket.Entry e : members) rows.add(new Row(teamId, e.name(), TEXT, e));
    }

    // ── 點擊名單 ────────────────────────────────────────────────────────────

    // 管理員拖移玩家：按下玩家名字時記下，滑鼠移動超過 3 像素才算拖移，放開時看落在哪一隊
    @Nullable private UUID dragId;
    @Nullable private String dragName;
    private double dragStartX, dragStartY;
    private boolean dragging;

    @Nullable
    private Row rowAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return null;
        int i = scroll + (int) ((my - listY) / ROW_H);
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (view == View.LOBBY && button == 0) {
            Row row = rowAt(mx, my);
            if (row != null) {
                MenuDataPacket menu = ClientMenuData.get();
                if (row.entry() != null && menu != null && menu.isOp()) {
                    // 先不動作：放開時才決定是「點擊選取」還是「拖移換隊」
                    dragId = row.entry().id();
                    dragName = row.entry().name();
                    dragStartX = mx;
                    dragStartY = my;
                    dragging = false;
                } else {
                    onRowClicked(row);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragId != null && button == 0) {
            if (!dragging && Math.hypot(mx - dragStartX, my - dragStartY) > 3) dragging = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragId != null && button == 0) {
            UUID id = dragId;
            boolean wasDragging = dragging;
            dragId = null;
            dragging = false;
            if (wasDragging) {
                Row target = rowAt(mx, my);
                if (target != null) moveSelected(id, target.team());
                else PGButton.playDeny();
            } else {
                // 沒有拖移 = 點擊：選取／取消選取
                PGButton.playClick(1.0f);
                selected = id.equals(selected) ? null : id;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** 管理員把玩家移到某一隊：1.. 隊伍、0 孤狼、-1 未分隊、-2 觀戰。 */
    private void moveSelected(UUID id, int team) {
        LobbyStatePacket.Entry e = find(ClientLobbyData.get(), id);
        if (e == null) { PGButton.playDeny(); return; }
        if (team == -2) {
            if (e.spectator()) { PGButton.playDeny(); return; }
            send(LobbyManager.Action.SET_SPECTATOR, 1, id);
        } else {
            if (!e.spectator() && e.team() == team) { PGButton.playDeny(); return; } // 已經在這一隊
            send(LobbyManager.Action.SET_PLAYER_TEAM, team, id);
        }
        PGButton.playClick(1.0f);
        selected = null;
        rebuildWidgets();
    }

    private void onRowClicked(Row row) {
        MenuDataPacket menu = ClientMenuData.get();
        boolean op = menu != null && menu.isOp();
        if (row.entry() != null) {
            PGButton.playDeny(); // 一般玩家不能選取別人（管理員的點擊在 mouseReleased 處理）
            return;
        }
        // 點隊伍標題
        LobbyStatePacket lobby = ClientLobbyData.get();
        LobbyStatePacket.Entry sel = find(lobby, selected);
        LobbyStatePacket.Entry me = self(lobby);
        boolean free = lobby != null && LobbyManager.TeamMode.values()[lobby.teamModeOrdinal()] == LobbyManager.TeamMode.FREE;
        if (op && sel != null) {
            moveSelected(sel.id(), row.team()); // 管理員：先點玩家、再點隊伍標題（拖移的替代方式）
        } else if (row.team() == -2) {
            PGButton.playDeny();
        } else if (row.team() >= 0) {
            if ((free || op) && me != null && !me.spectator() && lobby.inLobby()) {
                PGButton.playClick(1.0f);
                send(LobbyManager.Action.JOIN_TEAM, row.team());
            } else {
                PGButton.playDeny(); // 管理員分隊模式、觀戰中或遊戲進行中：不能自己換隊
            }
        } else {
            PGButton.playDeny(); // 「未分隊」標題不能點
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (view == View.LOBBY && mx >= listX && mx < listX + listW) {
            int max = Math.max(0, rows.size() - listH / ROW_H);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ── 小工具 ──────────────────────────────────────────────────────────────

    private static void send(LobbyManager.Action action, int arg) {
        PGNetwork.sendToServer(new LobbyActionPacket(action, arg));
    }

    private static void send(LobbyManager.Action action, int arg, UUID target) {
        PGNetwork.sendToServer(new LobbyActionPacket(action, arg, target));
    }

    @Nullable
    private static MenuDataPacket.MapEntry findMap(List<MenuDataPacket.MapEntry> maps, int id) {
        for (MenuDataPacket.MapEntry m : maps) if (m.id() == id) return m;
        return maps.isEmpty() ? null : maps.get(0);
    }

    @Nullable
    private static LobbyStatePacket.Entry find(@Nullable LobbyStatePacket lobby, @Nullable UUID id) {
        if (lobby == null || id == null) return null;
        for (LobbyStatePacket.Entry e : lobby.players()) if (e.id().equals(id)) return e;
        return null;
    }

    @Nullable
    private static LobbyStatePacket.Entry self(@Nullable LobbyStatePacket lobby) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : find(lobby, mc.player.getUUID());
    }

    private void switchTo(View v) {
        view = v;
        rebuildWidgets();
    }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + panelW, top + panelH, BG);

        // 標題列
        g.drawString(font, Tr.t("menu.header"), left + 12, top + 10, ACCENT, false);
        MenuDataPacket menu = ClientMenuData.get();
        String status = menu != null ? menu.status() : "…";
        g.drawString(font, status, left + panelW - font.width(status) - 12, top + 10, TEXT_DIM, false);
        // 右下角顯示模組版本（F1）
        String ver = "v" + com.provinggrounds.ProvingGroundsMod.version();
        g.drawString(font, ver, left + panelW - font.width(ver) - 6, top + panelH - 10, 0xFF6A7384, false);
        g.fill(left + 10, top + 24, left + panelW - 10, top + 25, BORDER);

        if (view == View.HOME) renderHomeFooter(g, menu); else renderLobby(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHomeFooter(GuiGraphics g, @Nullable MenuDataPacket data) {
        int footY = top + panelH - 30;
        g.fill(left + 10, footY, left + panelW - 10, footY + 1, BORDER);
        String stats = data == null ? Tr.t("stats.loading")
            : Tr.t("stats.summary", String.format("%.2f", data.kd()), data.level(), data.xp(), data.nextLevelXp());
        g.drawString(font, stats, left + 14, footY + 8, TEXT, false);
        if (data != null) {
            String sub = Tr.t("stats.detail", data.kills(), data.deaths(), data.assists(), data.wins());
            g.drawString(font, sub, left + 14, footY + 18, TEXT_DIM, false);
        }
    }

    private void renderLobby(GuiGraphics g, int mouseX, int mouseY) {
        LobbyStatePacket lobby = ClientLobbyData.get();
        MenuDataPacket menu = ClientMenuData.get();
        boolean op = menu != null && menu.isOp();
        g.drawString(font, Tr.t("mode.br"), left + 14, top + 29, TEXT_DIM, false);

        // 名單
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, 0xC0101018);
        int visible = listH / ROW_H;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = listY + i * ROW_H;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H;
            if (row.entry() == null) {
                if (hover && row.team() != -1) g.fill(listX, ry, listX + listW, ry + ROW_H, 0x30FFFFFF);
                g.drawString(font, row.label(), listX + 4, ry + 2, row.color(), false);
            } else {
                LobbyStatePacket.Entry e = row.entry();
                if (e.id().equals(selected)) g.fill(listX, ry, listX + listW, ry + ROW_H, SELECT);
                else if (hover && op) g.fill(listX, ry, listX + listW, ry + ROW_H, 0x20FFFFFF);
                if (!e.spectator()) g.drawString(font, e.ready() ? "✔" : "·", listX + 12, ry + 2, e.ready() ? READY : TEXT_DIM, false);
                String name = e.name() + (e.op() ? Tr.t("lobby.admin_tag") : "") + (e.joinPending() ? Tr.t("lobby.pending_tag") : "");
                g.drawString(font, name, listX + 22, ry + 2, row.color(), false);
            }
        }
        if (rows.size() > visible) {
            String more = (scroll + visible < rows.size()) ? Tr.t("lobby.more") : "▲";
            g.drawString(font, more, listX + listW - font.width(more) - 3, listY + listH - 9, TEXT_DIM, false);
        }

        // 拖移中：標出要放進的那一隊，名字跟著游標
        if (dragging && dragName != null) {
            Row target = rowAt(mouseX, mouseY);
            if (target != null) {
                for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
                    if (rows.get(scroll + i).team() == target.team()) {
                        int ry = listY + i * ROW_H;
                        g.fill(listX, ry, listX + listW, ry + ROW_H, 0x30E8D060);
                    }
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(mouseX + 6, mouseY - 2, mouseX + 10 + font.width(dragName), mouseY + 10, 0xE0161C28);
            g.drawString(font, dragName, mouseX + 8, mouseY, ACCENT, false);
            g.pose().popPose();
        }

        // 名單下方提示（靠右，在名單正下方）
        String hint = lobbyHint(lobby, menu);
        if (hint != null) g.drawString(font, hint, left + panelW - 12 - font.width(hint), top + panelH - 42, WARN, false);
    }

    @Nullable
    private String lobbyHint(@Nullable LobbyStatePacket lobby, @Nullable MenuDataPacket menu) {
        if (lobby == null || menu == null) return Tr.t("lobby.loading");
        if (lobby.countdownSeconds() >= 0) return Tr.t("lobby.countdown", lobby.countdownSeconds());
        if (!lobby.inLobby()) return Tr.t("lobby.in_game", menu.status());
        long total = lobby.players().stream().filter(e -> !e.spectator()).count();
        long ready = lobby.players().stream().filter(e -> !e.spectator() && e.ready()).count();
        String base = Tr.t("lobby.ready_count", ready, total);
        if (menu.isOp()) {
            return base + (selected != null ? Tr.t("lobby.hint_admin_selected") : Tr.t("lobby.hint_admin"));
        }
        LobbyManager.TeamMode mode = LobbyManager.TeamMode.values()[lobby.teamModeOrdinal()];
        return base + (mode == LobbyManager.TeamMode.FREE ? Tr.t("lobby.hint_free") : Tr.t("lobby.hint_assign"));
    }

    @Override
    public void tick() {
        // 伺服器資料到了（或更新了）就重建按鈕，讓設定與按鈕狀態正確
        boolean confirming = System.currentTimeMillis() < endConfirmUntil;
        if (ClientMenuData.version() != seenMenu || ClientLobbyData.version() != seenLobby || confirming != wasConfirming) {
            wasConfirming = confirming; // 確認時間過了，按鈕文字要變回「結束遊戲」
            rebuildWidgets();
        }
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
