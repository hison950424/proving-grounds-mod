package com.provinggrounds.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 檔案: HudLayout.java
 * 目的: HUD 自訂版面（只在客戶端）。每個 HUD 元件可以：開關、放大縮小（50%～200%）、拖到別的位置。
 *   設定存在每位玩家自己的 config/provinggrounds-hud.json（不會從伺服器同步），在主選單「HUD 介面設定」調整。
 *   每個元件有一個「預設方框」（以介面大小 3 為基準的原本位置）與「縮放支點」（靠近的螢幕邊，例如右上角的元件以右上角縮放），
 *   繪製時用 push() 把整個元件平移＋縮放；位置會自動夾在螢幕內，換解析度也不會跑出畫面。
 *   已經有舊設定開關的元件（指北針、擊殺圖示、擊殺紀錄、互動提示）沿用舊的設定值，兩邊同步。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HudLayout {

    // ── 原版藥水效果圖示（計畫書 6 B4）：畫之前套用位置、大小、透明度，關掉就不畫 ──

    private static boolean effectsPushed;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onOverlayPre(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.POTION_ICONS.type()) return;
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        if (!enabled(El.EFFECTS)) { event.setCanceled(true); return; }
        apply(event.getGuiGraphics(), El.EFFECTS, w, h);
        effectsPushed = true;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onOverlayPost(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.POTION_ICONS.type() || !effectsPushed) return;
        effectsPushed = false;
        pop(event.getGuiGraphics());
    }

    /** 預設方框：x、y、寬、高（GUI 像素，縮放前）。 */
    public record Box(int x, int y, int w, int h) {}

    /** 縮放支點在方框裡的位置（0 = 左／上，0.5 = 中，1 = 右／下）。 */
    private enum Pivot {
        TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0), CENTER(0.5f, 0.5f), RIGHT(1, 0.5f),
        BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);
        final float fx, fy;
        Pivot(float fx, float fy) { this.fx = fx; this.fy = fy; }
    }

    private interface BoxFn { Box at(int sw, int sh); }

    /** 可以自訂的 HUD 元件。group：0 = 大逃殺、1 = 團隊模式、2 = 通用。 */
    public enum El {
        MINIMAP("小地圖", 0, Pivot.TOP_LEFT, (w, h) -> new Box(0, 0, 76, 76)),
        ZONE("縮圈資訊", 0, Pivot.TOP_LEFT, (w, h) -> new Box(0, 82, 110, 34)),
        ALIVE("存活人數", 0, Pivot.TOP_RIGHT, (w, h) -> new Box(w - 62, 0, 62, 31)),
        BR_FEED("淘汰訊息", 0, Pivot.TOP_RIGHT, (w, h) -> new Box(w - 168, 36, 160, 46)),
        COMPASS("指北針", 0, Pivot.TOP_CENTER, (w, h) -> new Box(w / 2 - 88, 0, 176, 33)),
        OUTSIDE("圈外警告", 0, Pivot.TOP_CENTER, (w, h) -> new Box(w / 2 - 80, 36, 160, 20)),
        HINT("跳傘／倒地提示", 0, Pivot.BOTTOM_CENTER, (w, h) -> new Box(w / 2 - 90, h - 104, 180, 46)),
        ARENA_SCORE("比分", 1, Pivot.TOP_CENTER, (w, h) -> new Box(w / 2 - 130, 4, 260, 50)),
        KILL_FEED("擊殺紀錄", 1, Pivot.TOP_RIGHT, (w, h) -> new Box(w - 140, ClientArenaState.active() ? 4 : 40, 136, 72)),
        WALLET("金錢與戰績", 1, Pivot.BOTTOM_RIGHT, (w, h) -> new Box(w - 124, h - 64, 120, 39)),
        SCOREBOARD("戰績面板", 2, Pivot.RIGHT, (w, h) -> new Box(w - 196, h / 2 - 60, 190, 120)),
        KILL_ICON("擊殺圖示", 2, Pivot.CENTER, (w, h) -> new Box(w / 2 - 36, h / 2 + 12, 72, 20)),
        INTERACT("互動提示", 2, Pivot.TOP_CENTER, (w, h) -> new Box(w / 2 - 40, h / 2 + 11, 80, 10)),
        TITLE("上方標題", 2, Pivot.TOP_CENTER, (w, h) -> new Box(w / 2 - 100, 58, 200, 22)),
        SPECIAL("特殊物品格", 2, Pivot.BOTTOM_CENTER, (w, h) -> new Box(w / 2 + 99, h - 32, 22, 32)),
        /** 原版藥水效果圖示（右上角；計畫書 6 B4）。 */
        EFFECTS("藥水效果圖示", 2, Pivot.TOP_RIGHT, (w, h) -> new Box(w - 126, 0, 126, 52));

        public final String label;
        public final int group;
        private final Pivot pivot;
        private final BoxFn box;

        El(String label, int group, Pivot pivot, BoxFn box) {
            this.label = label;
            this.group = group;
            this.pivot = pivot;
            this.box = box;
        }

        public Box defaultBox(int sw, int sh) { return box.at(sw, sh); }
    }

    /** 每個元件的自訂值。 */
    private static final class Entry {
        boolean enabled = true;
        float scale = 1f;
        float alpha = 1f;   // 文字（含圖示）不透明度（計畫書 6 B3；設定檔欄位 alpha）
        float bgAlpha = 1f; // 背景色塊不透明度（計畫書 7 A1；設定檔欄位 bg_alpha，舊檔沒有時 = alpha）
        int dx, dy;
    }

    public static final float MIN_ALPHA = 0.2f;

    public static float alpha(El e) {
        ensureLoaded();
        return ENTRIES.get(e).alpha;
    }

    public static void setAlpha(El e, float a) {
        ensureLoaded();
        ENTRIES.get(e).alpha = Math.round(Math.max(MIN_ALPHA, Math.min(1f, a)) * 20) / 20f;
    }

    /** 背景色塊的不透明度（0 = 完全透明；計畫書 7 A1）。 */
    public static float bgAlpha(El e) {
        ensureLoaded();
        return ENTRIES.get(e).bgAlpha;
    }

    public static void setBgAlpha(El e, float a) {
        ensureLoaded();
        ENTRIES.get(e).bgAlpha = Math.round(Math.max(0f, Math.min(1f, a)) * 20) / 20f;
    }

    /**
     * 畫色塊時要乘上的透明度倍率（GuiGraphicsFillMixin 讀）。文字與圖示用 setShaderColor 的透明度，
     * 色塊也會被它乘到，所以色塊另外乘上「背景 ÷ 文字」，最後色塊 = 背景透明度。1 = 不調整。
     */
    public static float fillAlphaMul = 1f;

    /** 開始畫一個元件：套用文字／背景透明度（畫完呼叫 endAlpha）。 */
    public static void beginAlpha(GuiGraphics g, El e) {
        float a = alpha(e), b = bgAlpha(e);
        if (a >= 0.999f && b >= 0.999f) return;
        g.flush(); // 之前畫的東西照原本的透明度送出
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, a);
        fillAlphaMul = b / Math.max(0.01f, a);
    }

    public static void endAlpha(GuiGraphics g) {
        g.flush(); // 這個元件畫的東西用它自己的透明度送出，再恢復
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        fillAlphaMul = 1f;
    }

    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2f;
    private static final Map<El, Entry> ENTRIES = new EnumMap<>(El.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;

    static {
        for (El e : El.values()) ENTRIES.put(e, new Entry());
    }

    private HudLayout() {}

    /** 沿用舊設定開關的元件（null = 開關存在 json）。 */
    @Nullable
    private static ForgeConfigSpec.BooleanValue legacyToggle(El e) {
        PGClientConfig c = PGClientConfig.CLIENT;
        return switch (e) {
            case COMPASS -> c.compassEnabled;
            case KILL_ICON -> c.killIconEnabled;
            case KILL_FEED -> c.killFeedEnabled;
            case INTERACT -> c.interactHint;
            default -> null;
        };
    }

    // ── 讀取 ────────────────────────────────────────────────────────────────

    public static boolean enabled(El e) {
        ensureLoaded();
        ForgeConfigSpec.BooleanValue legacy = legacyToggle(e);
        if (legacy != null) {
            try { return legacy.get(); } catch (IllegalStateException ignored) { return true; }
        }
        return ENTRIES.get(e).enabled;
    }

    public static float scale(El e) {
        ensureLoaded();
        return ENTRIES.get(e).scale;
    }

    /** 元件目前在螢幕上的方框（套用位置與縮放、夾在螢幕內）。 */
    public static Box box(El e, int sw, int sh) {
        ensureLoaded();
        Entry en = ENTRIES.get(e);
        Box b = e.defaultBox(sw, sh);
        float s = en.scale;
        float px = b.x() + b.w() * e.pivot.fx, py = b.y() + b.h() * e.pivot.fy;
        int w = Math.round(b.w() * s), h = Math.round(b.h() * s);
        int x = Math.round(px + en.dx + (b.x() - px) * s);
        int y = Math.round(py + en.dy + (b.y() - py) * s);
        x = Math.max(0, Math.min(sw - w, x));
        y = Math.max(0, Math.min(sh - h, y));
        return new Box(x, y, w, h);
    }

    /**
     * 開始畫一個元件：關閉時回傳 false（不用畫、也不用 pop）；開啟時套用平移＋縮放，畫完要呼叫 pop()。
     * 元件本身照原本的座標畫就好。
     */
    public static boolean push(GuiGraphics g, El e, int sw, int sh) {
        if (!enabled(e)) return false;
        apply(g, e, sw, sh);
        return true;
    }

    /** 不看開關、一定套用位置與縮放（畫完要呼叫 pop()）。 */
    public static void apply(GuiGraphics g, El e, int sw, int sh) {
        Box def = e.defaultBox(sw, sh);
        Box now = box(e, sw, sh);
        float s = scale(e);
        g.pose().pushPose();
        g.pose().translate(now.x() - def.x() * s, now.y() - def.y() * s, 0);
        g.pose().scale(s, s, 1);
        beginAlpha(g, e);
    }

    public static void pop(GuiGraphics g) {
        endAlpha(g);
        g.pose().popPose();
    }

    // ── 修改（設定畫面用）───────────────────────────────────────────────────

    public static void setEnabled(El e, boolean on) {
        ensureLoaded();
        ForgeConfigSpec.BooleanValue legacy = legacyToggle(e);
        if (legacy != null) {
            legacy.set(on);
            legacy.save();
        } else {
            ENTRIES.get(e).enabled = on;
        }
    }

    public static void setScale(El e, float s, int sw, int sh) {
        ensureLoaded();
        Entry en = ENTRIES.get(e);
        en.scale = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)) * 20) / 20f; // 每格 5%
        clamp(e, sw, sh); // 放大後碰到螢幕邊被推回來時，把位置記成推回後的樣子
    }

    /** 把元件移到指定的左上角位置（會夾在螢幕內）。 */
    public static void moveTo(El e, int x, int y, int sw, int sh) {
        ensureLoaded();
        Entry en = ENTRIES.get(e);
        Box b = e.defaultBox(sw, sh);
        float s = en.scale;
        float px = b.x() + b.w() * e.pivot.fx, py = b.y() + b.h() * e.pivot.fy;
        int w = Math.round(b.w() * s), h = Math.round(b.h() * s);
        x = Math.max(0, Math.min(sw - w, x));
        y = Math.max(0, Math.min(sh - h, y));
        en.dx = Math.round(x - px - (b.x() - px) * s);
        en.dy = Math.round(y - py - (b.y() - py) * s);
    }

    private static void clamp(El e, int sw, int sh) {
        Box b = box(e, sw, sh);
        moveTo(e, b.x(), b.y(), sw, sh);
    }

    public static void reset(El e) {
        ensureLoaded();
        Entry en = ENTRIES.get(e);
        en.scale = 1f;
        en.alpha = 1f;
        en.bgAlpha = 1f;
        en.dx = 0;
        en.dy = 0;
        setEnabled(e, true);
    }

    public static boolean isDefault(El e) {
        Entry en = ENTRIES.get(e);
        return en.scale == 1f && en.alpha == 1f && en.bgAlpha == 1f && en.dx == 0 && en.dy == 0;
    }

    public static void resetAll() {
        for (El e : El.values()) reset(e);
    }

    // ── 存檔 ────────────────────────────────────────────────────────────────

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("provinggrounds-hud.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (El e : El.values()) {
                if (!root.has(e.name())) continue;
                JsonObject o = root.getAsJsonObject(e.name());
                Entry en = ENTRIES.get(e);
                if (o.has("enabled")) en.enabled = o.get("enabled").getAsBoolean();
                if (o.has("scale")) en.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, o.get("scale").getAsFloat()));
                if (o.has("alpha")) en.alpha = Math.max(MIN_ALPHA, Math.min(1f, o.get("alpha").getAsFloat()));
                // 計畫書 7 A1：舊檔只有一個透明度 → 背景也用同一個值
                en.bgAlpha = o.has("bg_alpha") ? Math.max(0f, Math.min(1f, o.get("bg_alpha").getAsFloat())) : en.alpha;
                if (o.has("dx")) en.dx = o.get("dx").getAsInt();
                if (o.has("dy")) en.dy = o.get("dy").getAsInt();
            }
        } catch (Exception ex) {
            ProvingGroundsMod.LOGGER.warn("HUD 版面設定讀取失敗，改用預設：{}", ex.toString());
        }
    }

    public static void save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        for (El e : El.values()) {
            Entry en = ENTRIES.get(e);
            JsonObject o = new JsonObject();
            o.addProperty("enabled", en.enabled);
            o.addProperty("scale", en.scale);
            o.addProperty("alpha", en.alpha);
            o.addProperty("bg_alpha", en.bgAlpha);
            o.addProperty("dx", en.dx);
            o.addProperty("dy", en.dy);
            root.add(e.name(), o);
        }
        try {
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            ProvingGroundsMod.LOGGER.warn("HUD 版面設定存檔失敗：{}", ex.toString());
        }
    }

    /** 讓呼叫端少寫 try/catch 的小工具。 */
    static <T> T cfg(Supplier<T> get, T fallback) {
        try { return get.get(); } catch (IllegalStateException e) { return fallback; }
    }
}
