package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.PingPacket;
import com.provinggrounds.network.PingSyncPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: ClientPing.java
 * 目的: 標點（使用者回饋；只在客戶端載入）。
 *   - 按標點鍵（預設 X，可以在按鍵設定改）：準星指著的位置（方塊或實體）送給伺服器，伺服器轉給自己與同隊的人。
 *   - 標點顯示 5 秒：0～2 秒不透明度 100%、2～5 秒 35%，之後消失；同一個人再標一次會取代上一個標點。
 *   - 標點的位置畫一個記號，下面顯示「離自己幾公尺」（隊友的標點另外顯示是誰標的）；隔著牆也看得到，遠處會放大一點方便看。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPing {

    public static final KeyMapping KEY = new KeyMapping("key.provinggrounds.ping", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.provinggrounds");

    private static final long SOLID_MS = 2000, SHOW_MS = 5000;
    private static final int SELF_COLOR = 0xFFE040, MATE_COLOR = 0x60D0FF;

    private record Ping(Vec3 pos, String name, boolean self, long start) {}
    private static final Map<UUID, Ping> PINGS = new HashMap<>();

    private ClientPing() {}

    public static void receive(PingSyncPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.player.getUUID().equals(p.owner());
        PINGS.put(p.owner(), new Ping(new Vec3(p.x(), p.y(), p.z()), p.name(), self, System.currentTimeMillis())); // 取代同一個人的舊標點
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), self ? 1.8f : 1.5f, 0.5f));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { PINGS.clear(); return; }
        long now = System.currentTimeMillis();
        PINGS.values().removeIf(g -> now - g.start() > SHOW_MS);
        while (KEY.consumeClick()) {
            if (mc.screen != null || p.isSpectator()) continue;
            Vec3 at = aimed(mc, p);
            if (at != null) PGNetwork.sendToServer(new PingPacket(at.x, at.y, at.z));
        }
    }

    /** 準星指著的位置：近的實體（原版準星判定）優先，否則往前找方塊（最遠 PingPacket.MAX_RANGE 格）。 */
    private static Vec3 aimed(Minecraft mc, LocalPlayer p) {
        if (mc.hitResult instanceof EntityHitResult eh) return eh.getEntity().position().add(0, eh.getEntity().getBbHeight() * 0.6, 0);
        HitResult hit = p.pick(PingPacket.MAX_RANGE - 1, 1f, false);
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || PINGS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (Ping g : PINGS.values()) {
            long age = now - g.start();
            if (age > SHOW_MS) continue;
            int alpha = age < SOLID_MS ? 0xFF : 0x59; // 0～2 秒 100%、2～5 秒 35%
            int color = (alpha << 24) | (g.self() ? SELF_COLOR : MATE_COLOR);
            double dist = mc.player.position().distanceTo(g.pos());
            double scale = 0.025 * Math.max(1.0, cam.distanceTo(g.pos()) / 8.0); // 遠處放大，看起來大小差不多
            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            pose.mulPose(event.getCamera().rotation());
            pose.scale((float) -scale, (float) -scale, (float) scale);
            var m = pose.last().pose();
            String mark = "◆";
            String text = Math.round(dist) + "m";
            if (!g.self()) text = g.name() + "  " + text;
            font.drawInBatch(mark, -font.width(mark) / 2f, -10, color, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            int bg = (int) ((alpha / 255f) * 0x60) << 24;
            font.drawInBatch(text, -font.width(text) / 2f, 1, color, false, m, buf, Font.DisplayMode.SEE_THROUGH, bg, 0xF000F0);
            pose.popPose();
        }
        buf.endBatch();
    }
}
