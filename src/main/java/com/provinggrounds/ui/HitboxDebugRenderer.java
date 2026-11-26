package com.provinggrounds.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.Hitbox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 檔案: HitboxDebugRenderer.java
 * 目的: 管理員按 F3＋B 時，繪製精準命中判定的實際動畫部位框（只在客戶端；計畫書 10 升級）：
 *   - 頭（紅色）跟隨頭的偏航與俯仰角轉動；身體與四肢（黃色）跟隨走路、跑步、蹲下、攻擊與爬行動畫擺動。
 *   - 修復趴下（Pose.SWIMMING）時不顯示 hitbox 的問題：趴下時也會繪製平躺的 6 個部位框。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class HitboxDebugRenderer {

    private HitboxDebugRenderer() {}

    private static final int[][] EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7},
        {0, 2}, {1, 3}, {4, 6}, {5, 7},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.hasPermissions(2)
            || !mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        Matrix4f m = ps.last().pose();
        for (Player p : mc.level.players()) {
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (p.isSpectator() || p.isPassenger() || p.isFallFlying()) continue;
            Pose pose = p.getPose();
            // 計畫書 10：同時支援站立、蹲下、以及趴下（SWIMMING）的 F3+B 部位判定框
            if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.SWIMMING) continue;
            double x = Mth.lerp(pt, p.xo, p.getX()) - cam.x;
            double y = Mth.lerp(pt, p.yo, p.getY()) - cam.y;
            double z = Mth.lerp(pt, p.zo, p.getZ()) - cam.z;
            float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
            float headPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
            float walkPos = p.walkAnimation.position(pt);
            float walkSpeed = p.walkAnimation.speed(pt);
            float attackAnim = p.getAttackAnim(pt);
            List<Hitbox.PartBox> parts = Hitbox.computeParts(pose, p.getBbHeight(), bodyYaw, headYaw, headPitch, walkPos, walkSpeed, attackAnim);
            for (Hitbox.PartBox part : parts) {
                drawPart(lines, m, x, y, z, part, part.kind() == 5 ? 0xFF4040 : 0xFFE040);
            }
        }
        buf.endBatch(RenderType.lines());
    }

    private static void drawPart(VertexConsumer vc, Matrix4f m, double ox, double oy, double oz, Hitbox.PartBox p, int rgb) {
        double[][] c = new double[8][];
        int k = 0;
        for (double lx : new double[] { p.minX(), p.maxX() }) {
            for (double ly : new double[] { p.minY(), p.maxY() }) {
                for (double lz : new double[] { p.minZ(), p.maxZ() }) {
                    c[k++] = Hitbox.partToWorld(ox, oy, oz, p, lx, ly, lz);
                }
            }
        }
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int[] e : EDGES) {
            double[] a = c[e[0]], bb = c[e[1]];
            float nx = (float) (bb[0] - a[0]), ny = (float) (bb[1] - a[1]), nz = (float) (bb[2] - a[2]);
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len; ny /= len; nz /= len;
            vc.vertex(m, (float) a[0], (float) a[1], (float) a[2]).color(r, g, b, 255).normal(nx, ny, nz).endVertex();
            vc.vertex(m, (float) bb[0], (float) bb[1], (float) bb[2]).color(r, g, b, 255).normal(nx, ny, nz).endVertex();
        }
    }
}
