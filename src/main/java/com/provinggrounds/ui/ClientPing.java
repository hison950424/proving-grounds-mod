package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.PingPacket;
import com.provinggrounds.network.PingSyncPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
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
 *   - 標點的位置畫一個記號，下面顯示「離自己幾公尺」（隊友的標點另外顯示是誰標的）；畫在畫面最上層，不會被方塊擋住。
 *   - 小地圖、大地圖上也會畫出標點（MapRender）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPing {

    public static final KeyMapping KEY = new KeyMapping("key.provinggrounds.ping", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.provinggrounds");

    private static final long SOLID_MS = 2000, SHOW_MS = 5000;
    private static final int SELF_COLOR = 0xFFE040, MATE_COLOR = ClientTeamColors.MATE; // 隊友一律藍色

    private record Ping(Vec3 pos, String name, boolean self, long start) {}
    private static final Map<UUID, Ping> PINGS = new HashMap<>();

    private ClientPing() {}

    public static void receive(PingSyncPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.player.getUUID().equals(p.owner());
        PINGS.put(p.owner(), new Ping(new Vec3(p.x(), p.y(), p.z()), p.name(), self, System.currentTimeMillis())); // 取代同一個人的舊標點
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), self ? 1.8f : 1.5f, 0.125f)); // 音量降低 75%（原本 0.5 → 0.125）
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

    /** 準星指著的位置：略過玩家與實體，直接從相機沿準星方向標記到方塊上（最遠 PingPacket.MAX_RANGE 格）。 */
    private static Vec3 aimed(Minecraft mc, LocalPlayer p) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        org.joml.Vector3f lookF = cam.getLookVector();
        Vec3 look = new Vec3(lookF.x(), lookF.y(), lookF.z());
        if (look.lengthSqr() < 1e-6) {
            eye = p.getEyePosition(mc.getFrameTime());
            look = p.getViewVector(mc.getFrameTime());
        }
        Vec3 end = eye.add(look.scale(PingPacket.MAX_RANGE - 1));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /** 這一幀每個標點在畫面上的位置（RenderLevelStageEvent 算，HUD 畫；畫在畫面最上層，所以不會被方塊擋住）。 */
    private record Screen(float x, float y, Ping ping) {}
    private static final java.util.List<Screen> SCREEN = new java.util.ArrayList<>();

    /** 地圖用：目前還在顯示的標點（世界座標、是不是自己的、不透明度 0～255）。 */
    public record MapPing(double x, double z, boolean self, int alpha) {}

    public static java.util.List<MapPing> mapPings() {
        long now = System.currentTimeMillis();
        java.util.List<MapPing> out = new java.util.ArrayList<>();
        for (Ping g : PINGS.values()) {
            long age = now - g.start();
            if (age <= SHOW_MS) out.add(new MapPing(g.pos().x, g.pos().z, g.self(), age < SOLID_MS ? 0xFF : 0x59));
        }
        return out;
    }

    /** 把標點的世界座標換成畫面座標（相機後面的不畫）。用 AFTER_TRANSLUCENT_BLOCKS 取得正確的相機視角矩陣（Forge 1.20.1 的 AFTER_LEVEL 傳的是投影矩陣）。 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        SCREEN.clear();
        if (PINGS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Matrix4f view = new org.joml.Matrix4f(event.getPoseStack().last().pose());
        org.joml.Matrix4f proj = new org.joml.Matrix4f(event.getProjectionMatrix());
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        for (Ping g : PINGS.values()) {
            org.joml.Vector4f v = new org.joml.Vector4f((float) (g.pos().x - cam.x), (float) (g.pos().y - cam.y), (float) (g.pos().z - cam.z), 1f);
            view.transform(v);
            proj.transform(v);
            if (v.w <= 0.01f) continue; // 在背後
            float nx = v.x / v.w, ny = v.y / v.w;
            SCREEN.add(new Screen((nx + 1f) / 2f * w, (1f - ny) / 2f * h, g));
        }
    }

    /** HUD：標點記號與距離（不受方塊遮擋）。 */
    public static final net.minecraftforge.client.gui.overlay.IGuiOverlay OVERLAY = (gui, g, partialTick, w, h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || SCREEN.isEmpty()) return;
        long now = System.currentTimeMillis();
        Font font = mc.font;
        boolean fof = mc.player.hasEffect(com.provinggrounds.special.SpecialRegistry.FRIEND_OR_FOE.get());
        for (Screen sc : SCREEN) {
            Ping p = sc.ping();
            if (fof && !p.self()) continue; // 敵我不分期間隱藏隊友標記
            long age = now - p.start();
            if (age > SHOW_MS) continue;
            int alpha = age < SOLID_MS ? 0xFF : 0x59; // 0～2 秒 100%、2～5 秒 35%
            int color = (alpha << 24) | (p.self() ? SELF_COLOR : MATE_COLOR);
            int x = Math.round(sc.x()), y = Math.round(sc.y());
            if (x < -20 || x > w + 20 || y < -20 || y > h + 20) continue;
            String mark = "◆";
            String text = Math.round(mc.player.position().distanceTo(p.pos())) + "m";
            if (!p.self()) text = p.name() + "  " + text;
            g.drawString(font, mark, x - font.width(mark) / 2, y - 9, color, true);
            g.fill(x - font.width(text) / 2 - 2, y + 1, x + font.width(text) / 2 + 2, y + 11, (int) (alpha / 255f * 0x70) << 24);
            g.drawString(font, text, x - font.width(text) / 2, y + 2, color, false);
        }
    };
}
