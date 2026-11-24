package com.provinggrounds.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientArenaState;
import com.provinggrounds.config.PGClientConfig;
import com.provinggrounds.network.ArenaStatePacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 檔案: ArenaWorldRenderer.java
 * 目的: 團隊模式在世界裡畫的提示（只在客戶端；用頂點色直接畫，不放方塊或實體，不影響伺服器效能）。
 *   佔點（計畫書 5 的 I）：開放中的據點中心往上拉一道細的半透明光柱（穿牆可見），地面畫範圍框（站進去變粗），
 *     上方顯示字母與距離；據點在視野外時，畫面左右邊緣顯示指向它的小箭頭。
 *     顏色：無人＝白、紅隊領先＝紅、藍隊領先＝藍、爭奪中＝黃色閃爍。關掉 [hud] dom_beacon 就完全不畫。
 *   防蹲點（計畫書 5 的 H2）：接近敵方重生區（範圍外 10 格內）時，只在自己附近畫一段半透明紅色弧形牆；
 *     範圍外 5 格內在畫面上方小字提示「前方是敵方重生區」。關掉 [hud] anti_camp_wall 就不畫。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ArenaWorldRenderer {

    private static final float BEAM_W = 0.25f, BEAM_H = 160f;

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, w, h) -> renderScreen(g, w, h);

    private ArenaWorldRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ClientArenaState.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ArenaStatePacket s = ClientArenaState.get();
        if (s.markers().isEmpty()) return;
        var cfg = PGClientConfig.CLIENT;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-c.x, -c.y, -c.z);
        Matrix4f m = ps.last().pose();
        boolean flash = (System.currentTimeMillis() / 300) % 2 == 0;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        BufferBuilder b = Tesselator.getInstance().getBuilder();

        // 1. 防蹲點紅牆（有深度測試：被牆擋住就看不到，和縮圈牆一樣）
        if (cfg.antiCampWall.get()) {
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vec3 me = mc.player.position();
            for (ArenaStatePacket.Marker k : s.markers()) {
                if (k.kind() != 1) continue;
                double dx = me.x - k.x(), dz = me.z - k.z();
                double dist = Math.sqrt(dx * dx + dz * dz) - k.radius();
                if (dist > 10 || dist < -0.5) continue;
                float alpha = (float) (0.35 * (1 - Math.max(0, dist) / 10.0)) + 0.05f;
                double center = Math.atan2(dz, dx);
                int seg = 24;
                double span = Math.toRadians(40);
                for (int i = 0; i < seg; i++) {
                    double a0 = center - span + 2 * span * i / seg, a1 = center - span + 2 * span * (i + 1) / seg;
                    float fade = 1f - Math.abs(i + 0.5f - seg / 2f) / (seg / 2f); // 兩端淡出
                    quadV(b, m, k.x() + Math.cos(a0) * k.radius(), k.z() + Math.sin(a0) * k.radius(),
                        k.x() + Math.cos(a1) * k.radius(), k.z() + Math.sin(a1) * k.radius(),
                        k.y() - 2, k.y() + 6, k.color(), alpha * fade);
                }
            }
            BufferUploader.drawWithShader(b.end());
        }

        // 2. 佔點光柱與範圍框（穿牆可見）
        if (cfg.domBeacon.get()) {
            RenderSystem.disableDepthTest();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (ArenaStatePacket.Marker k : s.markers()) {
                if (k.kind() != 0) continue;
                int color = k.contested() && !flash ? 0xFFFFFF : k.color();
                double y0 = k.y() - 0.9;
                quadV(b, m, k.x() - BEAM_W, k.z(), k.x() + BEAM_W, k.z(), y0, y0 + BEAM_H, color, 0.28f);
                quadV(b, m, k.x(), k.z() - BEAM_W, k.x(), k.z() + BEAM_W, y0, y0 + BEAM_H, color, 0.28f);
                boolean inside = Math.abs(mc.player.getX() - k.x()) <= k.radius() + 0.5 && Math.abs(mc.player.getZ() - k.z()) <= k.radius() + 0.5;
                float edge = inside ? 0.25f : 0.1f;
                double r = k.radius() + 0.5;
                square(b, m, k.x(), y0 + 0.05, k.z(), r, edge, color, inside ? 0.6f : 0.4f);
            }
            BufferUploader.drawWithShader(b.end());
            RenderSystem.enableDepthTest();

            // 字母與距離（看板文字，穿牆可見）
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            Font font = mc.font;
            for (ArenaStatePacket.Marker k : s.markers()) {
                if (k.kind() != 0) continue;
                double dist = c.distanceTo(new Vec3(k.x(), k.y(), k.z()));
                String text = k.label() + " " + Math.round(dist) + "m";
                ps.pushPose();
                ps.translate(k.x(), k.y() + 3, k.z());
                ps.mulPose(cam.rotation());
                float sc = (float) (0.025 * Math.max(1, dist / 12));
                ps.scale(-sc, -sc, sc);
                font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFF000000 | k.color(), false, ps.last().pose(), buf,
                    Font.DisplayMode.SEE_THROUGH, 0x50000000, LightTexture.FULL_BRIGHT);
                ps.popPose();
            }
            buf.endBatch();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void quadV(BufferBuilder b, Matrix4f m, double x0, double z0, double x1, double z1, double y0, double y1, int rgb, float a) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, al = (int) (a * 255);
        b.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x1, (float) y0, (float) z1).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x0, (float) y1, (float) z0).color(r, g, bl, al).endVertex();
    }

    private static void quadH(BufferBuilder b, Matrix4f m, double x0, double z0, double x1, double z1, double y, int rgb, float a) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, al = (int) (a * 255);
        b.vertex(m, (float) x0, (float) y, (float) z0).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x1, (float) y, (float) z0).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x1, (float) y, (float) z1).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) x0, (float) y, (float) z1).color(r, g, bl, al).endVertex();
    }

    /** 地面方形外框（寬 w）。 */
    private static void square(BufferBuilder b, Matrix4f m, double cx, double y, double cz, double r, double w, int rgb, float a) {
        quadH(b, m, cx - r, cz - r, cx + r, cz - r + w, y, rgb, a);
        quadH(b, m, cx - r, cz + r - w, cx + r, cz + r, y, rgb, a);
        quadH(b, m, cx - r, cz - r + w, cx - r + w, cz + r - w, y, rgb, a);
        quadH(b, m, cx + r - w, cz - r + w, cx + r, cz + r - w, y, rgb, a);
    }

    // ── 畫面上的提示（在視野外的據點箭頭、接近敵方重生區的小字）──

    private static void renderScreen(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientArenaState.active()) return;
        ArenaStatePacket s = ClientArenaState.get();
        var cfg = PGClientConfig.CLIENT;
        Vec3 me = mc.player.position();
        float yaw = mc.player.getYRot();
        double halfFov = mc.options.fov().get() / 2.0 * (w / (double) Math.max(1, h)) * 0.9;
        for (ArenaStatePacket.Marker k : s.markers()) {
            if (k.kind() == 0 && cfg.domBeacon.get()) {
                double dx = k.x() - me.x, dz = k.z() - me.z;
                double target = Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft 的 yaw：0 = 南
                double diff = Mth.wrapDegrees(target - yaw);
                if (Math.abs(diff) < Math.min(80, halfFov)) continue; // 在視野內就看光柱
                boolean right = diff > 0;
                int x = right ? w - 14 : 6, y = h / 2 - 20;
                String txt = (right ? "" : "◀ ") + k.label() + (right ? " ▶" : "");
                g.drawString(mc.font, txt, right ? x - mc.font.width(txt) + 8 : x, y, 0xC0000000 | k.color(), true);
            } else if (k.kind() == 1 && cfg.antiCampWall.get()) {
                double dx = me.x - k.x(), dz = me.z - k.z();
                double dist = Math.sqrt(dx * dx + dz * dz) - k.radius();
                if (dist < 0 && Math.abs(me.y - k.y()) <= 8) { // 已經在敵方重生區裡：畫面四周紅色細框淡閃（不遮中間）
                    float pulse = 0.25f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                    int c = ((int) (pulse * 255) << 24) | 0xFF2020;
                    g.fill(0, 0, w, 3, c); g.fill(0, h - 3, w, h, c); g.fill(0, 3, 3, h - 3, c); g.fill(w - 3, 3, w, h - 3, c);
                    continue;
                }
                if (dist > 5) continue;

                String txt = "前方是敵方重生區";
                int tw = mc.font.width(txt);
                g.fill(w / 2 - tw / 2 - 4, 70, w / 2 + tw / 2 + 4, 81, 0x50000000);
                g.drawString(mc.font, txt, w / 2 - tw / 2, 72, 0xFFFF6060, false);
            }
        }
    }
}
