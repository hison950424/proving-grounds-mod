package com.provinggrounds.ui;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 檔案: HUD.java
 * 目的: 遊戲中 overlay（只在客戶端執行）。CS2 / Valorant 深色風格。
 *   左上：小地圖（飛機、進行中），下方是縮圈階段與倒數 / 飛行剩餘 / 結算倒數（準備階段顯示登機倒數）
 *   右上：存活人數 / 隊伍數 / 復活牌，下方是擊殺資訊（最新的在最上面，淡出）
 *   中上：在圈外時紅字警告與距離
 *   中下：跳傘提示、淘汰提示
 */
public final class HUD {

    // 配色（ARGB）
    private static final int BG        = 0xEB0F141E; // #0F141E，透明度 92%
    private static final int ACCENT    = 0xFFE8D060; // 金黃強調色
    private static final int TEXT      = 0xFFFFFFFF;
    private static final int TEXT_DIM  = 0xFFA0A8B8;
    private static final int DANGER    = 0xFFE04848;
    private static final int PAD = 6;

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, width, height);

    private HUD() {}

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        BRStatePacket s = ClientBRState.get();
        if (s.phase() == ClientBRState.PHASE_NONE) return;
        Font font = mc.font;

        int minimapBottom = renderMinimap(g, font, s, mc);
        renderZone(g, font, s, minimapBottom);
        int aliveBottom = renderAlive(g, font, s, w);
        renderKillFeed(g, font, w, aliveBottom);
        renderOutsideWarning(g, font, s, mc.player, w);
        renderHint(g, font, s, w, h);
    }

    // ── 右上：存活人數 ──────────────────────────────────────────────────────

    /** 存活與隊伍指示器的縮放（原本的 65%，也就是縮小 35%）。 */
    private static final float ALIVE_SCALE = 0.65f;

    /** 右上角（貼齊）：存活人數 / 隊伍數 / 身上的復活牌。回傳下方可用的 y（擊殺資訊接在下面）。 */
    private static int renderAlive(GuiGraphics g, Font font, BRStatePacket s, int w) {
        if (s.phase() == ClientBRState.PHASE_PREPARING) return 8;
        boolean cards = s.cardsHeld() > 0;
        int boxW = 96, boxH = cards ? 47 : 34;
        // 以右上角為原點縮小，畫在 (-boxW, 0) ～ (0, boxH)，整塊貼齊螢幕右上角
        g.pose().pushPose();
        g.pose().translate(w, 0, 0);
        g.pose().scale(ALIVE_SCALE, ALIVE_SCALE, 1);
        int x = -boxW, y = 0;
        panel(g, x, y, boxW, boxH);
        g.drawString(font, Tr.t("hud.alive"), x + PAD + 2, y + PAD, TEXT_DIM, false);
        g.drawString(font, String.valueOf(s.alivePlayers()), x + PAD + 30, y + PAD, TEXT, false);
        g.drawString(font, Tr.t("hud.teams"), x + PAD + 2, y + PAD + 13, TEXT_DIM, false);
        g.drawString(font, String.valueOf(s.aliveTeams()), x + PAD + 30, y + PAD + 13, TEXT, false);
        if (cards) {
            g.drawString(font, Tr.t("hud.cards"), x + PAD + 2, y + PAD + 26, TEXT_DIM, false);
            g.drawString(font, "×" + s.cardsHeld(), x + PAD + 42, y + PAD + 26, ACCENT, false);
        }
        g.pose().popPose();
        return Math.round(boxH * ALIVE_SCALE) + 4;
    }

    // ── 左上：小地圖 ────────────────────────────────────────────────────────

    private static final int MINIMAP = 72;          // 小地圖邊長（GUI 像素；原本 96，縮小 25%）
    private static final double MINIMAP_SPAN = 192; // 小地圖顯示的範圍（格）

    /** 遊戲中（飛機、進行中）在左上角畫小地圖，北方朝上、自己在中間。回傳下方可用的 y。 */
    private static int renderMinimap(GuiGraphics g, Font font, BRStatePacket s, Minecraft mc) {
        if (s.phase() != ClientBRState.PHASE_PLANE && s.phase() != ClientBRState.PHASE_ACTIVE) return 8;
        net.minecraft.world.entity.Entity cam = mc.getCameraEntity();
        if (cam == null) return 8;
        int x = 2, y = 2; // 外框 2 像素：整塊貼齊螢幕左上角
        g.fill(x - 2, y - 2, x + MINIMAP + 2, y + MINIMAP + 2, BG);
        MapRender.View v = new MapRender.View(x, y, MINIMAP, MINIMAP,
            cam.getX() - MINIMAP_SPAN / 2, cam.getZ() - MINIMAP_SPAN / 2, MINIMAP_SPAN);
        g.enableScissor(x, y, x + MINIMAP, y + MINIMAP);
        MapRender.drawTerrain(g, v);
        if (s.nextRadius() >= 0) MapRender.drawCircle(g, v, s.nextCx(), s.nextCz(), s.nextRadius(), MapRender.NEXT_ZONE, 1, true);
        MapRender.drawCircle(g, v, s.cx(), s.cz(), s.radius(), MapRender.ZONE, 1, false);
        MapRender.drawMarkers(g, font, v, false);
        if (s.selfState() != ClientBRState.SELF_DEAD && s.selfState() != ClientBRState.SELF_NONE) {
            MapRender.drawSelf(g, font, v, cam.getX(), cam.getZ(), cam.getYRot());
        }
        g.disableScissor();
        String north = Tr.t("hud.north");
        g.drawString(font, north, x + (MINIMAP - font.width(north)) / 2, y + 2, ACCENT, true);
        return y + MINIMAP + 6;
    }

    /** 左上、小地圖下方：縮圈時間（準備階段顯示登機倒數）。 */
    private static void renderZone(GuiGraphics g, Font font, BRStatePacket s, int topY) {
        String title, line;
        int lineColor = TEXT;
        switch (s.phase()) {
            case ClientBRState.PHASE_PREPARING -> {
                title = Tr.t("mode.br");
                line = Tr.t("hud.boarding", s.seconds());
            }
            case ClientBRState.PHASE_PLANE -> {
                title = Tr.t("hud.flying");
                line = Tr.t("hud.remaining", s.seconds());
            }
            case ClientBRState.PHASE_ACTIVE -> {
                title = Tr.t("hud.stage", s.stage(), s.totalStages());
                if (s.nextRadius() < 0) {
                    line = Tr.t("hud.final_zone");
                    lineColor = DANGER;
                } else if (s.shrinking()) {
                    line = Tr.t("hud.shrinking", mmss(s.seconds()));
                    lineColor = DANGER;
                } else {
                    line = Tr.t("hud.shrink_in", mmss(s.seconds()));
                }
            }
            case ClientBRState.PHASE_ENDING -> {
                title = Tr.t("hud.ended");
                line = Tr.t("hud.return_in", s.seconds());
            }
            default -> { return; }
        }
        int boxW = Math.max(MINIMAP, Math.max(font.width(title), font.width(line)) + PAD * 2 + 4);
        int x = 0, y = topY; // 跟小地圖一樣貼齊左邊
        panel(g, x, y, boxW, 34);
        g.drawString(font, title, x + PAD + 2, y + PAD, ACCENT, false);
        g.drawString(font, line, x + PAD + 2, y + PAD + 13, lineColor, false);
    }

    // ── 中上：圈外警告 ──────────────────────────────────────────────────────

    private static void renderOutsideWarning(GuiGraphics g, Font font, BRStatePacket s, LocalPlayer player, int w) {
        if (s.phase() != ClientBRState.PHASE_ACTIVE || s.radius() < 0) return; // -1 = 沒有圈；最終圈半徑可以是 0
        if (s.selfState() == ClientBRState.SELF_DEAD || s.selfState() == ClientBRState.SELF_NONE) return;
        double dist = Math.hypot(player.getX() - s.cx(), player.getZ() - s.cz()) - s.radius();
        if (dist <= 0) return;
        String msg = Tr.t("hud.outside", (int) Math.ceil(dist));
        int boxW = font.width(msg) + PAD * 2;
        int x = (w - boxW) / 2, y = 8;
        // 閃爍的紅色邊條
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        g.fill(x, y, x + boxW, y + 20, BG);
        g.fill(x, y + 18, x + boxW, y + 20, blink ? DANGER : 0x80E04848);
        g.drawString(font, msg, x + PAD, y + 6, DANGER, false);
    }

    // ── 中下：提示 ──────────────────────────────────────────────────────────

    /** 登機的時間（客戶端時鐘），用來顯示「幾秒後可跳傘」。 */
    private static long planeSinceMs;
    private static int lastSelfState = -1;

    /** 登機後還要等幾秒才能跳（0 = 可以跳）。秒數來自伺服器同步的 board_lock_seconds。 */
    private static int boardLockLeft() {
        int lock;
        try { lock = com.provinggrounds.config.PGConfig.SERVER.boardLockSeconds.get(); }
        catch (IllegalStateException e) { lock = 5; }
        long left = lock * 1000L - (System.currentTimeMillis() - planeSinceMs);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    private static void renderHint(GuiGraphics g, Font font, BRStatePacket s, int w, int h) {
        if (s.selfState() != lastSelfState) {
            if (s.selfState() == ClientBRState.SELF_PLANE) planeSinceMs = System.currentTimeMillis();
            lastSelfState = s.selfState();
        }
        String hint = switch (s.selfState()) {
            case ClientBRState.SELF_PLANE -> boardLockLeft() > 0
                ? Tr.t("hud.hint.plane_locked", boardLockLeft())
                : Tr.t("hud.hint.plane", jumpKey());
            case ClientBRState.SELF_GLIDE -> Tr.t("hud.hint.glide", jumpKey());
            case ClientBRState.SELF_FREEFALL -> Tr.t("hud.hint.freefall", jumpKey());
            case ClientBRState.SELF_CHUTE -> Tr.t("hud.hint.chute", jumpKey());
            case ClientBRState.SELF_DOWNED -> Tr.t("hud.hint.downed", mmss(s.timerSeconds()));
            case ClientBRState.SELF_DEAD -> switch (s.timerKind()) {
                case ClientBRState.TIMER_CARD_GROUND -> s.timerSeconds() >= 0
                    ? Tr.t("hud.hint.card_ground", mmss(s.timerSeconds()))
                    : Tr.t("hud.hint.card_ground_nolimit");
                case ClientBRState.TIMER_FINAL -> Tr.t("hud.hint.final");
                case ClientBRState.TIMER_CARD_HELD -> Tr.t("hud.hint.card_held");
                default -> Tr.t("hud.hint.dead");
            };
            default -> null;
        };
        int y = h - 78;
        if (hint != null) {
            boolean downed = s.selfState() == ClientBRState.SELF_DOWNED;
            int color = downed ? DANGER : s.selfState() == ClientBRState.SELF_DEAD ? TEXT_DIM : TEXT;
            int boxW = font.width(hint) + PAD * 2 + 4;
            int x = (w - boxW) / 2;
            panel(g, x, y, boxW, 20);
            g.drawString(font, hint, x + PAD + 2, y + 6, color, false);
        }
        renderProgress(g, font, s, w, y - 26);
    }

    /** 讀條：扶起隊友 / 被扶起 / 啟動祭壇。 */
    private static void renderProgress(GuiGraphics g, Font font, BRStatePacket s, int w, int y) {
        String label = switch (s.progressKind()) {
            case ClientBRState.PROGRESS_REVIVING -> Tr.t("hud.progress.reviving");
            case ClientBRState.PROGRESS_BEING_REVIVED -> Tr.t("hud.progress.being_revived");
            case ClientBRState.PROGRESS_ALTAR -> Tr.t("hud.progress.altar");
            default -> null;
        };
        if (label == null) return;
        int boxW = 160, x = (w - boxW) / 2;
        panel(g, x, y, boxW, 22);
        g.drawString(font, label, x + PAD + 2, y + 3, TEXT, false);
        int barX = x + PAD + 2, barW = boxW - PAD * 2 - 4, barY = y + 15;
        float p = Math.max(0f, Math.min(1f, s.progress()));
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF2A3344);
        g.fill(barX, barY, barX + (int) (barW * p), barY + 3, ACCENT);
    }

    // ── 右上、存活人數下方：擊殺資訊（最新的在最上面） ──────────────────────

    private static void renderKillFeed(GuiGraphics g, Font font, int w, int topY) {
        ClientBRState.KillEntry[] kills = ClientBRState.kills();
        long now = System.currentTimeMillis();
        int y = topY;
        for (ClientBRState.KillEntry k : kills) {
            long age = now - k.timeMs();
            float alpha = age > ClientBRState.KILL_SHOW_MS - 1000
                ? Math.max(0f, (ClientBRState.KILL_SHOW_MS - age) / 1000f) : 1f;
            if (alpha <= 0.05f) continue;
            KillFeedPacket d = k.data();
            // 淘汰：「擊殺者 ▸ 被殺者」（非玩家致死：「☠ 被殺者」）；擊倒：「擊倒者 擊倒 被擊倒者」（非玩家：「倒地 被擊倒者」）
            String sep = d.knock()
                ? (d.killer().isEmpty() ? Tr.t("hud.feed.downed") + " " : " " + Tr.t("hud.feed.knock") + " ")
                : (d.killer().isEmpty() ? "☠ " : "  ▸  ");
            int wKiller = font.width(d.killer());
            int wSep = font.width(sep);
            int wVictim = font.width(d.victim());
            int boxW = wKiller + wSep + wVictim + PAD * 2;
            int x = w - boxW - 8;
            g.fill(x, y, x + boxW, y + 14, withAlpha(BG, alpha * 0.92f));
            int tx = x + PAD;
            if (!d.killer().isEmpty()) {
                g.drawString(font, d.killer(), tx, y + 3, withAlpha(0xFF000000 | d.killerColor(), alpha), false);
                tx += wKiller;
            }
            g.drawString(font, sep, tx, y + 3, withAlpha(TEXT_DIM, alpha), false);
            tx += wSep;
            g.drawString(font, d.victim(), tx, y + 3, withAlpha(0xFF000000 | d.victimColor(), alpha), false);
            y += 16;
        }
    }

    // ── 繪圖小工具 ──────────────────────────────────────────────────────────

    // ── 血條（取代原版愛心，由 ClientForgeEvents 呼叫）

    private static final int HP_BG   = 0xC0101018;
    private static final int HP_HIGH = 0xFF4CC864; // 綠
    private static final int HP_MID  = 0xFFE8C040; // 黃
    private static final int HP_LOW  = 0xFFE04848; // 紅
    private static final int HP_ABSORB = 0xFFF0D030; // 黃：額外生命值

    /**
     * 在原版愛心的位置（快捷欄左上方）畫數字血條：寬 81、高 9，中間顯示「目前 / 最大」。
     * @param leftHeight 原版左側元素目前的高度（離畫面底部的距離）
     */
    public static void renderHealthBar(GuiGraphics g, Font font, LocalPlayer player, int w, int h, int leftHeight) {
        int x = w / 2 - 91, y = h - leftHeight, bw = 81, bh = 9;
        float max = Math.max(1f, player.getMaxHealth());
        float hp = Math.max(0f, Math.min(player.getHealth(), max));
        float ratio = hp / max;
        int color = ratio > 0.5f ? HP_HIGH : ratio > 0.25f ? HP_MID : HP_LOW;
        if (ClientBRState.get().selfState() == ClientBRState.SELF_DOWNED) color = HP_LOW;

        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, HP_BG);
        g.fill(x, y, x + Math.round(bw * ratio), y + bh, color);
        float absorb = player.getAbsorptionAmount();
        if (absorb > 0) { // 額外生命值（護盾泡泡等）：黃色，接在血量後面；超出的部分從頭疊上去
            int start = Math.round(bw * ratio);
            int len = Math.round(bw * Math.min(1f, absorb / max));
            int end = Math.min(bw, start + len);
            g.fill(x + start, y, x + end, y + bh, HP_ABSORB);
            int over = start + len - bw;
            if (over > 0) g.fill(x, y, x + Math.min(bw, over), y + bh, HP_ABSORB);
        }
        String text = (int) Math.ceil(hp) + (absorb > 0 ? "+" + (int) Math.ceil(absorb) : "") + " / " + (int) Math.ceil(max);
        g.drawString(font, text, x + (bw - font.width(text)) / 2, y + 1, TEXT, true);
    }

    /** 深色面板 + 左側金黃強調條。 */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + 2, y + h, ACCENT);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        a = Math.max(a, 5); // 字型繪製時 alpha < 4 會被當成不透明，保留下限
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 跳躍鍵的名稱（玩家改過按鍵時跟著變，預設「空白鍵」）。 */
    private static String jumpKey() {
        return " " + Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString() + " ";
    }

    private static String mmss(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
