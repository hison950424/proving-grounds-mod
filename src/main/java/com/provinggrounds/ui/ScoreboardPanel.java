package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.config.PGClientConfig;
import com.provinggrounds.network.ArenaStatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 檔案: ScoreboardPanel.java
 * 目的: 戰績面板（只在客戶端；計畫書 5 的 E2）。團隊死鬥／佔點／槍王之王中，按住「戰績」鍵（預設 Caps Lock）
 *   在畫面右側顯示所有人的擊殺、死亡（團隊死鬥、佔點另外顯示金錢），放開就關閉。
 *   Caps Lock 是鎖定鍵：判斷用 GLFW 的按下／放開事件（KeyMapping.isDown），不看大小寫鎖定燈號。
 *   如果某台電腦放開事件不可靠，可把 [hud] scoreboard_toggle 設成 true：改成按一下開、再按一下關（不會自動淡出），
 *   或在按鍵設定把「戰績」改綁到普通按鍵。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ScoreboardPanel {

    public static final KeyMapping KEY = new KeyMapping("key.provinggrounds.scoreboard", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.provinggrounds");

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, w, h) -> render(g, w, h);

    private static boolean toggled;

    private ScoreboardPanel() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (KEY.consumeClick()) if (PGClientConfig.CLIENT.scoreboardToggle.get()) toggled = !toggled;
        if (!ClientArenaState.active()) toggled = false;
    }

    private static boolean showing() {
        return PGClientConfig.CLIENT.scoreboardToggle.get() ? toggled : KEY.isDown();
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !ClientArenaState.active() || !showing()) return;
        ArenaStatePacket s = ClientArenaState.get();
        Font font = mc.font;
        boolean money = s.board().stream().anyMatch(r -> r.money() >= 0);
        int rowH = 10, pw = money ? 190 : 150;
        int ph = 16 + s.board().size() * rowH;
        float scale = PGClientConfig.CLIENT.hudScale.get().floatValue();
        if (!HudLayout.enabled(HudLayout.El.SCOREBOARD)) return; // 位置、大小可在「HUD 介面設定」自訂
        // 面板高度跟人數有關：以自訂方框的右邊、垂直中心為準
        HudLayout.Box box = HudLayout.box(HudLayout.El.SCOREBOARD, w, h);
        scale *= HudLayout.scale(HudLayout.El.SCOREBOARD);
        float right = box.x() + box.w(), midY = box.y() + box.h() / 2f;
        float alpha = HudLayout.alpha(HudLayout.El.SCOREBOARD);
        if (alpha < 0.999f) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha); // 透明度（HUD 介面設定）
        g.pose().pushPose();
        g.pose().translate(right - pw * scale, Math.max(4, Math.min(h - ph * scale - 4, midY - ph * scale / 2)), 0);
        g.pose().scale(scale, scale, 1);
        g.fill(0, 0, pw, ph, 0xA00F141E);
        int cK = money ? pw - 82 : pw - 44, cD = money ? pw - 58 : pw - 20, cM = pw - 6;
        g.drawString(font, "戰績", 6, 4, 0xFFE8D060, false);
        g.drawString(font, "殺", cK, 4, 0xFFA0A8B8, false);
        g.drawString(font, "死", cD, 4, 0xFFA0A8B8, false);
        if (money) g.drawString(font, "金錢", cM - font.width("金錢"), 4, 0xFFA0A8B8, false);
        int y = 15;
        String me = mc.player.getGameProfile().getName();
        for (ArenaStatePacket.Row r : s.board()) {
            if (r.name().equals(me)) g.fill(2, y - 1, pw - 2, y + rowH - 1, 0x30FFFFFF);
            int nameColor = r.online() ? 0xFF000000 | r.color() : 0xFF808080;
            String name = r.online() ? r.name() : r.name() + "（離線）";
            g.drawString(font, font.plainSubstrByWidth(name, cK - 10), 6, y, nameColor, false);
            g.drawString(font, String.valueOf(r.kills()), cK, y, 0xFFFFFFFF, false);
            g.drawString(font, String.valueOf(r.deaths()), cD, y, 0xFFFFFFFF, false);
            if (money && r.money() >= 0) {
                String m = "$" + r.money();
                g.drawString(font, m, cM - font.width(m), y, 0xFF4CC864, false);
            }
            y += rowH;
        }
        g.pose().popPose();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
