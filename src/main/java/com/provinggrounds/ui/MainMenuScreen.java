package com.provinggrounds.ui;

import com.provinggrounds.client.ClientMenuData;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.CirclePreset;
import com.provinggrounds.network.MenuDataPacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.StartBRPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: MainMenuScreen.java
 * 目的: 主選單（只在客戶端使用）。按 M 打開。CS2 / Valorant 深色風格。
 *   首頁：BATTLE ROYALE（大按鈕）、TDM / DOMINATION / GUNFIGHT（Phase 2，先停用）、底部戰績欄。
 *   BR 設定頁：地圖、縮圈方案、一般/競技，「開始」只有管理員可按（伺服器端會再檢查權限）。
 */
public class MainMenuScreen extends Screen {

    private static final int PANEL_W = 280, PANEL_H = 196;
    private static final int BG       = 0xEB0F141E; // #0F141E，透明度 92%
    private static final int BORDER   = 0xFF2A3344;
    private static final int ACCENT   = 0xFFE8D060;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A8B8;
    private static final int WARN     = 0xFFE0A048;

    private enum View { HOME, BR_SETUP }

    // 記住上次的選擇（同一次遊戲啟動期間）
    private static View view = View.HOME;
    private static int mapIndex = 0;
    private static CirclePreset preset = CirclePreset.STANDARD;
    private static BattleRoyaleMode.Variant variant = BattleRoyaleMode.Variant.NORMAL;

    private int left, top;
    private int seenVersion = -1;

    public MainMenuScreen() {
        super(Component.literal("Proving Grounds"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        seenVersion = ClientMenuData.version();
        if (view == View.HOME) initHome(); else initBRSetup();
    }

    // ── 首頁 ────────────────────────────────────────────────────────────────

    private void initHome() {
        int x = left + 14, w = PANEL_W - 28;
        addRenderableWidget(new PGButton(x, top + 36, w, 36, Component.literal("Battle Royale"), true, true,
            () -> switchTo(View.BR_SETUP)));
        addModeStub(x, top + 80, w, "Team Deathmatch");
        addModeStub(x, top + 104, w, "Domination");
        addModeStub(x, top + 128, w, "Gunfight");
    }

    private void addModeStub(int x, int y, int w, String name) {
        PGButton b = new PGButton(x, y, w, 20, Component.literal(name), false, true, () -> {}).withTag("PHASE 2");
        b.active = false;
        addRenderableWidget(b);
    }

    // ── BR 設定頁 ───────────────────────────────────────────────────────────

    private void initBRSetup() {
        MenuDataPacket data = ClientMenuData.get();
        int x = left + 14, w = PANEL_W - 28;

        List<MenuDataPacket.MapEntry> maps = data != null ? data.maps() : List.of();
        if (!maps.isEmpty()) mapIndex = Math.floorMod(mapIndex, maps.size());
        MenuDataPacket.MapEntry map = maps.isEmpty() ? null : maps.get(mapIndex);

        String mapLabel = data == null ? "地圖：載入中…"
            : map == null ? "地圖：尚無地圖（請用 /pg map set 登記）"
            : String.format("地圖：#%d %s（點位 %d）", map.id(), map.name(), map.lootPoints());
        PGButton mapBtn = new PGButton(x, top + 40, w, 20, Component.literal(mapLabel), false, false, () -> {
            mapIndex++;
            rebuildWidgets();
        });
        mapBtn.active = maps.size() > 1;
        addRenderableWidget(mapBtn);

        addRenderableWidget(new PGButton(x, top + 64, w, 20, Component.literal("縮圈：" + preset.displayName), false, false, () -> {
            CirclePreset[] all = CirclePreset.values();
            preset = all[(preset.ordinal() + 1) % all.length];
            rebuildWidgets();
        }));

        addRenderableWidget(new PGButton(x, top + 88, w, 20, Component.literal("模式：" + variant.displayName), false, false, () -> {
            BattleRoyaleMode.Variant[] all = BattleRoyaleMode.Variant.values();
            variant = all[(variant.ordinal() + 1) % all.length];
            rebuildWidgets();
        }));

        int half = (w - 6) / 2;
        PGButton start = new PGButton(x, top + 134, half, 22, Component.literal("Start"), true, true, () -> {
            if (map == null) return;
            PGNetwork.sendToServer(new StartBRPacket(map.id(), preset.ordinal(), variant.ordinal()));
            onClose();
        });
        start.active = data != null && data.isOp() && data.inLobby() && map != null;
        addRenderableWidget(start);

        addRenderableWidget(new PGButton(x + half + 6, top + 134, half, 22, Component.literal("Back"), false, true,
            () -> switchTo(View.HOME)));
    }

    /** BR 設定頁開始鍵上方的提示文字。 */
    @Nullable
    private String setupHint(@Nullable MenuDataPacket data) {
        if (data == null) return "正在向伺服器取得資料…";
        if (!data.isOp()) return "只有管理員可以開始遊戲";
        if (!data.inLobby()) return "目前有遊戲進行中：" + data.status();
        if (data.maps().isEmpty()) return null;
        MenuDataPacket.MapEntry map = data.maps().get(Math.floorMod(mapIndex, data.maps().size()));
        if (map.lootPoints() == 0) return "提醒：這張地圖還沒有箱子點位（/pg loot add）";
        return null;
    }

    private void switchTo(View v) {
        view = v;
        rebuildWidgets();
    }

    // ── 繪製 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        // 面板
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, BORDER);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, BG);

        // 標題列
        PGButton.drawSpaced(g, font, "▸ PROVING GROUNDS", left + 12, top + 10, ACCENT);
        MenuDataPacket data = ClientMenuData.get();
        String status = data != null ? data.status() : "…";
        g.drawString(font, status, left + PANEL_W - font.width(status) - 12, top + 10, TEXT_DIM, false);
        g.fill(left + 10, top + 24, left + PANEL_W - 10, top + 25, BORDER);

        if (view == View.BR_SETUP) {
            PGButton.drawSpaced(g, font, "BATTLE ROYALE", left + 14, top + 29, TEXT_DIM);
            String hint = setupHint(data);
            if (hint != null) {
                g.drawString(font, hint, left + (PANEL_W - font.width(hint)) / 2, top + 118, WARN, false);
            }
        }

        // 底部戰績欄
        int footY = top + PANEL_H - 30;
        g.fill(left + 10, footY, left + PANEL_W - 10, footY + 1, BORDER);
        String stats = data == null ? "你的統計：載入中…"
            : String.format("你的統計：K/D %.2f  Lv.%d  XP %d / %d", data.kd(), data.level(), data.xp(), data.nextLevelXp());
        g.drawString(font, stats, left + 14, footY + 8, TEXT, false);
        if (data != null) {
            String sub = String.format("擊殺 %d  死亡 %d  助攻 %d  勝場 %d", data.kills(), data.deaths(), data.assists(), data.wins());
            g.drawString(font, sub, left + 14, footY + 18, TEXT_DIM, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        // 伺服器資料到了（或更新了）就重建按鈕，讓地圖名稱與開始鍵狀態正確
        if (ClientMenuData.version() != seenVersion) rebuildWidgets();
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
