package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * 檔案: EdgeTitle.java
 * 目的: 提示不擋視野（計畫書 5 的 A1；只在客戶端）。
 *   原版的 /title（畫面中央的大字標題與副標題）一律不畫在中央，改成畫面上方邊緣的一行小字：
 *   半透明淡背景、最多顯示 3 秒、最後 0.5 秒淡出；新的標題直接覆蓋舊的，不堆疊。
 *   伺服器端所有發標題的地方（開局倒數、淘汰、扶起、勝負…）不用改，全部自動套用。
 *   動作欄（快捷欄上方的一行字）本來就在畫面下緣，維持原版。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class EdgeTitle {

    private static final long SHOW_MS = 3000, FADE_MS = 500;

    private static Field fTitle, fSubtitle, fTime;
    private static boolean failed;
    private static Component lastTitle, lastSub;
    private static long since;

    private EdgeTitle() {}

    private static boolean init() {
        if (fTitle != null) return true;
        if (failed) return false;
        try {
            fTime = ObfuscationReflectionHelper.findField(Gui.class, "f_93000_");
            fTitle = ObfuscationReflectionHelper.findField(Gui.class, "f_93001_");
            fSubtitle = ObfuscationReflectionHelper.findField(Gui.class, "f_93002_");
            return true;
        } catch (RuntimeException e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("Edge title unavailable, vanilla titles stay: {}", e.toString());
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTitle(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.TITLE_TEXT.type() || !init()) return;
        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        Component title, sub;
        int time;
        try {
            title = (Component) fTitle.get(mc.gui);
            sub = (Component) fSubtitle.get(mc.gui);
            time = fTime.getInt(mc.gui);
        } catch (IllegalAccessException e) {
            return;
        }
        if (time <= 0 || (title == null && sub == null)) { lastTitle = null; lastSub = null; return; }
        long now = System.currentTimeMillis();
        if (title != lastTitle || sub != lastSub) { lastTitle = title; lastSub = sub; since = now; }
        long age = now - since;
        if (age > SHOW_MS) return;
        float alpha = age > SHOW_MS - FADE_MS ? (SHOW_MS - age) / (float) FADE_MS : 1f;
        render(event.getGuiGraphics(), mc.font, title, sub, event.getWindow().getGuiScaledWidth(), alpha);
    }

    private static void render(GuiGraphics g, Font font, Component title, Component sub, int w, float alpha) {
        String a = title == null ? "" : title.getString(), b = sub == null ? "" : sub.getString();
        if (a.isEmpty() && b.isEmpty()) return;
        float scale = PGClientConfig.CLIENT.hudScale.get().floatValue();
        int y = 62; // 在團隊模式 HUD、指北針與圈外警告下面，離準星很遠
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (!HudLayout.push(g, HudLayout.El.TITLE, w, sh)) return; // 自訂位置、大小、開關（HUD 介面設定）
        int tw = Math.max(font.width(title == null ? Component.empty() : title), (int) (font.width(b) * 0.8f));
        int bg = ((int) (0x70 * alpha) << 24);
        int textA = Math.max(4, (int) (0xFF * alpha)) << 24;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y, 0);
        g.pose().scale(scale, scale, 1);
        int lines = (a.isEmpty() ? 0 : 1) + (b.isEmpty() ? 0 : 1);
        g.fill(-tw / 2 - 6, -3, tw / 2 + 6, lines * 10 + 1, bg);
        int ly = 0;
        if (!a.isEmpty()) {
            g.drawString(font, title, -font.width(title) / 2, ly, 0xFFFFFF | textA, true);
            ly += 10;
        }
        if (!b.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, ly, 0);
            g.pose().scale(0.8f, 0.8f, 1);
            g.drawString(font, sub, -font.width(sub) / 2, 0, 0xDDDDDD | textA, false);
            g.pose().popPose();
        }
        g.pose().popPose();
        HudLayout.pop(g);
    }
}
