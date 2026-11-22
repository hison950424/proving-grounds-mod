package com.provinggrounds.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 檔案: SpawnPointRenderer.java
 * 目的: 畫出箱子生成點的方框（計畫書 B9，只在客戶端）。可以穿牆看到（關掉深度測試），亮黃色外框；
 *   只畫玩家周圍 spawn_view_range（預設 64）格內的點，點很多時也不掉幀（每幀只送一批線段）。
 *   點位由伺服器只送給按下顯示的那位管理員；登出時清除。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class SpawnPointRenderer {

    private static volatile List<BlockPos> points = List.of();
    private static volatile boolean show;

    private SpawnPointRenderer() {}

    public static void set(boolean visible, List<BlockPos> list) {
        show = visible;
        points = visible ? List.copyOf(list) : List.of();
    }

    public static void clear() {
        set(false, List.of());
        flashPos = null;
    }

    // 剛被刪除的生成點：閃紅框 3 秒（不管有沒有開顯示）
    private static volatile BlockPos flashPos;
    private static volatile long flashUntil;

    public static void flash(BlockPos pos) {
        flashPos = pos;
        flashUntil = System.currentTimeMillis() + 3000;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BlockPos fp = flashPos;
        long now = System.currentTimeMillis();
        if (fp != null && now > flashUntil) { flashPos = null; fp = null; }
        List<BlockPos> list = show ? points : List.of();
        if (list.isEmpty() && fp == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double range;
        try {
            range = PGConfig.SERVER.adminSpawnViewRange.get();
        } catch (IllegalStateException e) {
            range = 64;
        }
        double rangeSq = range * range;

        List<BlockPos> near = new java.util.ArrayList<>();
        for (BlockPos p : list) if (p.distToCenterSqr(cam) <= rangeSq) near.add(p);
        boolean blink = fp != null && (now / 250) % 2 == 0; // 每 0.25 秒閃一次
        if (near.isEmpty() && !blink) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (BlockPos p : near) {
            box(b, m, p.getX() + 0.05f, p.getY() + 0.05f, p.getZ() + 0.05f, p.getX() + 0.95f, p.getY() + 0.95f, p.getZ() + 0.95f, YELLOW);
        }
        if (blink) {
            // 刪除的點：紅色、稍微大一圈的方框
            box(b, m, fp.getX() - 0.1f, fp.getY() - 0.1f, fp.getZ() - 0.1f, fp.getX() + 1.1f, fp.getY() + 1.1f, fp.getZ() + 1.1f, RED);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static final float[] YELLOW = { 0.91f, 0.82f, 0.38f, 0.9f }; // #E8D060
    private static final float[] RED = { 1f, 0.2f, 0.2f, 1f };

    private static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float[] col) {
        float r = col[0], g = col[1], bl = col[2], a = col[3];
        float[][] c = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
        for (int[] e : edges) {
            b.vertex(m, c[e[0]][0], c[e[0]][1], c[e[0]][2]).color(r, g, bl, a).endVertex();
            b.vertex(m, c[e[1]][0], c[e[1]][1], c[e[1]][2]).color(r, g, bl, a).endVertex();
        }
    }
}
