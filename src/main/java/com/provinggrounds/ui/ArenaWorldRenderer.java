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
 *   佔點（計畫書 5 的 I；計畫書 6 C4 改版）：不畫光柱，只畫佔領範圍的邊框（穿牆可見、面向鏡頭，站多遠看起來都一樣粗；站進去變粗），
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

        // 1. 防蹲點紅牆（有深度測試：被牆擋住就看不到；高度覆蓋 y-4 ~ y+20 讓二樓／高台也清楚可見；kind=1 圓形、kind=2 方形）
        if (cfg.antiCampWall.get()) {
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vec3 me = mc.player.position();
            for (ArenaStatePacket.Marker k : s.markers()) {
                if (k.kind() != 1 && k.kind() != 2) continue;
                double y0 = k.y() - 4, y1 = k.y() + 20;
                double dx = me.x - k.x(), dz = me.z - k.z();
                if (k.kind() == 1) {
                    double dist = Math.sqrt(dx * dx + dz * dz) - k.radius();
                    if (dist > 10 || dist < -0.5) continue;
                    float alpha = (float) (0.35 * (1 - Math.max(0, dist) / 10.0)) + 0.08f;
                    double center = Math.atan2(dz, dx);
                    int seg = 24;
                    double span = Math.toRadians(45);
                    for (int i = 0; i < seg; i++) {
                        double a0 = center - span + 2 * span * i / seg, a1 = center - span + 2 * span * (i + 1) / seg;
                        float fade = 1f - Math.abs(i + 0.5f - seg / 2f) / (seg / 2f); // 兩端淡出
                        quadV(b, m, k.x() + Math.cos(a0) * k.radius(), k.z() + Math.sin(a0) * k.radius(),
                            k.x() + Math.cos(a1) * k.radius(), k.z() + Math.sin(a1) * k.radius(),
                            y0, y1, k.color(), alpha * fade);
                    }
                } else {
                    // 方形保護區（支援四邊獨立長度 北,-Z / 南,+Z / 西,-X / 東,+X，依邊界距離 10 格內漸顯）
                    double[] bnd = parseBoxBounds(k.label(), k.radius());
                    double bn = bnd[0], bs = bnd[1], bw = bnd[2], be = bnd[3];
                    double dist = Math.max(Math.max(-bw - dx, dx - be), Math.max(-bn - dz, dz - bs));
                    if (dist > 10 || dist < -0.5) continue;
                    float alpha = (float) (0.32 * (1 - Math.max(0, dist) / 10.0)) + 0.08f;
                    double x0 = k.x() - bw, x1 = k.x() + be, z0 = k.z() - bn, z1 = k.z() + bs;
                    quadV(b, m, x0, z0, x1, z0, y0, y1, k.color(), alpha);
                    quadV(b, m, x1, z0, x1, z1, y0, y1, k.color(), alpha);
                    quadV(b, m, x1, z1, x0, z1, y0, y1, k.color(), alpha);
                    quadV(b, m, x0, z1, x0, z0, y0, y1, k.color(), alpha);
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
                double y0 = k.y() - 0.9 + 0.05;
                // 計畫書 6 C4：不畫光柱，只畫佔領範圍的邊框；邊框一律面向鏡頭、寬度跟距離成正比 → 站多遠看起來都一樣粗
                boolean inside = Math.abs(mc.player.getX() - k.x()) <= k.radius() + 0.5 && Math.abs(mc.player.getZ() - k.z()) <= k.radius() + 0.5;
                double r = k.radius() + 0.5;
                double px = inside ? 0.009 : 0.006; // 邊框粗細（弧度，約 5～8 個螢幕像素）
                float a = inside ? 0.75f : 0.55f;
                double[][] corners = { { k.x() - r, k.z() - r }, { k.x() + r, k.z() - r }, { k.x() + r, k.z() + r }, { k.x() - r, k.z() + r } };
                for (int e = 0; e < 4; e++) {
                    double[] p0 = corners[e], p1 = corners[(e + 1) % 4];
                    int seg = 16; // 分段：每段用自己到鏡頭的距離算寬度
                    for (int i = 0; i < seg; i++) {
                        double t0 = i / (double) seg, t1 = (i + 1) / (double) seg;
                        ribbon(b, m, c, p0[0] + (p1[0] - p0[0]) * t0, y0, p0[1] + (p1[1] - p0[1]) * t0,
                            p0[0] + (p1[0] - p0[0]) * t1, y0, p0[1] + (p1[1] - p0[1]) * t1, px, color, a);
                    }
                }
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

    /**
     * 一段面向鏡頭的線（像畫筆畫的線）：寬度 = 這段到鏡頭的距離 × px，所以在螢幕上的粗細固定。
     * 寬的方向 = 線的方向 × 視線方向（外積），永遠朝著鏡頭攤開。
     */
    private static void ribbon(BufferBuilder b, Matrix4f m, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1,
                               double px, int rgb, float a) {
        double mx = (x0 + x1) / 2 - cam.x, my = (y0 + y1) / 2 - cam.y, mz = (z0 + z1) / 2 - cam.z;
        double dist = Math.sqrt(mx * mx + my * my + mz * mz);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx; // 線方向 × 視線
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6 || dist < 1e-3) return;
        double half = dist * px / 2 / len;
        sx *= half; sy *= half; sz *= half;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, al = (int) (a * 255);
        b.vertex(m, (float) (x0 - sx), (float) (y0 - sy), (float) (z0 - sz)).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) (x1 - sx), (float) (y1 - sy), (float) (z1 - sz)).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) (x1 + sx), (float) (y1 + sy), (float) (z1 + sz)).color(r, g, bl, al).endVertex();
        b.vertex(m, (float) (x0 + sx), (float) (y0 + sy), (float) (z0 + sz)).color(r, g, bl, al).endVertex();
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
            } else if ((k.kind() == 1 || k.kind() == 2) && cfg.antiCampWall.get()) {
                double dx = me.x - k.x(), dz = me.z - k.z();
                double dist;
                if (k.kind() == 2) {
                    double[] bnd = parseBoxBounds(k.label(), k.radius());
                    dist = Math.max(Math.max(-bnd[2] - dx, dx - bnd[3]), Math.max(-bnd[0] - dz, dz - bnd[1]));
                } else {
                    dist = Math.sqrt(dx * dx + dz * dz) - k.radius();
                }
                if (dist < 0 && Math.abs(me.y - k.y()) <= 16) { // 已經在敵方重生區裡（含二樓）：畫面四周紅色細框淡閃
                    float pulse = 0.25f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                    int c = ((int) (pulse * 255) << 24) | 0xFF2020;
                    g.fill(0, 0, w, 3, c); g.fill(0, h - 3, w, h, c); g.fill(0, 3, 3, h - 3, c); g.fill(w - 3, 3, w, h - 3, c);
                    continue;
                }
                if (dist > 5 || Math.abs(me.y - k.y()) > 16) continue;

                String txt = "前方是敵方重生區";
                int tw = mc.font.width(txt);
                g.fill(w / 2 - tw / 2 - 4, 70, w / 2 + tw / 2 + 4, 81, 0x50000000);
                g.drawString(mc.font, txt, w / 2 - tw / 2, 72, 0xFFFF6060, false);
            }
        }
    }

    /** 解析方形防蹲點標記的「北,南,西,東」四邊長度（未提供則全部退回 fallback）。 */
    private static double[] parseBoxBounds(String label, double fallback) {
        if (label != null && !label.isBlank()) {
            String[] p = label.split(",");
            if (p.length >= 4) {
                try {
                    return new double[] {
                        Double.parseDouble(p[0].trim()),
                        Double.parseDouble(p[1].trim()),
                        Double.parseDouble(p[2].trim()),
                        Double.parseDouble(p[3].trim())
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return new double[] { fallback, fallback, fallback, fallback };
    }
}
