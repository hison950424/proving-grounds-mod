package com.provinggrounds.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.network.BRStatePacket;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 檔案: CircleRenderer.java
 * 目的: 在世界中畫出縮圈牆（只在客戶端執行）。
 *       顏色跟大地圖／小地圖一致：目前圈 = 半透明白牆；下一圈 = 較淡的淺藍矮牆。
 *       只畫攝影機附近 160 格內的牆面，避免大地圖時拖慢效能。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class CircleRenderer {

    private static final double VIEW_RANGE = 160;
    private static final double SEGMENT_LENGTH = 2.0;

    private CircleRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BRStatePacket s = ClientBRState.get();
        if (s.phase() != ClientBRState.PHASE_ACTIVE || s.radius() <= 0) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 目前圈：白色（同地圖的白色實線），不透明度 circle_wall_opacity（預設 15%）
        ring(buf, m, s.cx(), s.cz(), s.radius(), cam, cam.y - 48, cam.y + 96, 0xFF, 0xFF, 0xFF, alpha(true));
        // 下一圈：淺藍 #60C0FF（同地圖的淺藍虛線），不透明度 circle_next_opacity（預設 7.5%），矮一點
        if (s.nextRadius() > 0) {
            ring(buf, m, s.nextCx(), s.nextCz(), s.nextRadius(), cam, cam.y - 8, cam.y + 8, 0x60, 0xC0, 0xFF, alpha(false));
        }
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** 牆的不透明度（0～255），來自客戶端設定；改設定檔後立刻生效（Forge 會重新讀取）。 */
    private static int alpha(boolean current) {
        double v;
        try {
            v = current ? com.provinggrounds.config.PGClientConfig.CLIENT.circleWallOpacity.get()
                        : com.provinggrounds.config.PGClientConfig.CLIENT.circleNextOpacity.get();
        } catch (IllegalStateException e) {
            v = current ? 0.15 : 0.075;
        }
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    private static void ring(BufferBuilder buf, Matrix4f m, double cx, double cz, double r, Vec3 cam,
                             double yMin, double yMax, int red, int green, int blue, int alpha) {
        // 攝影機離圈邊太遠就不畫
        double camDist = Math.hypot(cam.x - cx, cam.z - cz);
        if (Math.abs(camDist - r) > VIEW_RANGE) return;

        int segments = (int) Math.max(32, Math.min(8192, Math.ceil(2 * Math.PI * r / SEGMENT_LENGTH)));
        double step = 2 * Math.PI / segments;
        double rangeSq = VIEW_RANGE * VIEW_RANGE;
        float y0 = (float) yMin, y1 = (float) yMax;

        for (int i = 0; i < segments; i++) {
            double a0 = i * step, a1 = a0 + step;
            double x0 = cx + Math.cos(a0) * r, z0 = cz + Math.sin(a0) * r;
            double x1 = cx + Math.cos(a1) * r, z1 = cz + Math.sin(a1) * r;
            double dx = x0 - cam.x, dz = z0 - cam.z;
            if (dx * dx + dz * dz > rangeSq) continue;
            buf.vertex(m, (float) x0, y0, (float) z0).color(red, green, blue, alpha).endVertex();
            buf.vertex(m, (float) x1, y0, (float) z1).color(red, green, blue, alpha).endVertex();
            buf.vertex(m, (float) x1, y1, (float) z1).color(red, green, blue, alpha).endVertex();
            buf.vertex(m, (float) x0, y1, (float) z0).color(red, green, blue, alpha).endVertex();
        }
    }
}
