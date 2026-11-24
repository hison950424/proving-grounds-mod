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

/**
 * 檔案: HitboxDebugRenderer.java
 * 目的: 管理員按 F3＋B 時，除了原版的碰撞箱，另外畫出「精準命中判定」的實際部位框（只在客戶端）：
 *   兩腳、身體、兩手跟著身體方向轉（黃色），頭跟著頭的方向轉（紅色）；用的是伺服器判定同一份尺寸（Hitbox.parts）。
 *   伺服器另外有 hitbox_tolerance（每個部位往外放寬），這裡畫的是放寬前的大小。趴下、游泳等姿勢照原版碰撞箱，不另外畫。
 *   一般玩家本來就不能開 F3＋B（ClientDebugLock），所以只有管理員看得到。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class HitboxDebugRenderer {

    private HitboxDebugRenderer() {}

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
            if (pose != Pose.STANDING && pose != Pose.CROUCHING) continue;
            double x = Mth.lerp(pt, p.xo, p.getX()) - cam.x, y = Mth.lerp(pt, p.yo, p.getY()) - cam.y, z = Mth.lerp(pt, p.zo, p.getZ()) - cam.z;
            double bodyYaw = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
            double headYaw = Math.toRadians(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot));
            double[][] parts = Hitbox.parts(pose == Pose.CROUCHING);
            double px = Hitbox.pixel(), hs = Hitbox.heightScale(p.getBbHeight());
            for (int i = 0; i < parts.length; i++) {
                boolean head = i == parts.length - 1;
                box(lines, m, x, y, z, head ? headYaw : bodyYaw, parts[i], px, hs, head ? 0xFF4040 : 0xFFE040);
            }
        }
        buf.endBatch(RenderType.lines());
    }

    /** 畫一個跟著 yaw 轉的部位框（12 條邊）。部位座標：x 往右、y 往上、z 往前；面向 = (−sin yaw, cos yaw)。 */
    private static void box(VertexConsumer vc, Matrix4f m, double ox, double oy, double oz, double yaw, double[] part,
                            double px, double hs, int rgb) {
        double x0 = (part[0] - part[3]) * px, x1 = (part[0] + part[3]) * px;
        double y0 = part[1] * px * hs, y1 = (part[1] + part[4]) * px * hs;
        double z0 = (part[2] - part[5]) * px, z1 = (part[2] + part[5]) * px;
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double[][] c = new double[8][];
        int k = 0;
        for (double lx : new double[] { x0, x1 }) for (double ly : new double[] { y0, y1 }) for (double lz : new double[] { z0, z1 }) {
            // 右 = (−cos, −sin)、前 = (−sin, cos)
            c[k++] = new double[] { ox + lx * -cos + lz * -sin, oy + ly, oz + lx * -sin + lz * cos };
        }
        int[][] edges = { {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7} };
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int[] e : edges) {
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
