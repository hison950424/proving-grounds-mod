package com.provinggrounds.special.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.network.GlowSyncPacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SpecialFxPacket;
import com.provinggrounds.network.SpecialUsePacket;
import com.provinggrounds.special.ActionLock;
import com.provinggrounds.special.SpecialHooks;
import com.provinggrounds.special.SpecialRegistry;
import com.provinggrounds.special.SpecialSlot;
import com.provinggrounds.special.entity.DecoyEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 檔案: ClientSpecial.java
 * 目的: 特殊物品系統的客戶端（只在客戶端載入）：
 *   - 「使用特殊物品」鍵（預設 Q，可在按鍵設定修改）→ 送 SpecialUsePacket，伺服器驗證後使用。
 *   - 動作鎖定的輸入層：倒地、隱形斗篷時，左右鍵在客戶端就先擋掉（手感乾淨；伺服器另外擋）。
 *   - 發光：GlowSyncPacket 的清單 → 畫發光外框（EntitySpecialMixin）與地圖紅點（MapRender）；
 *           殘影分身對使用者與隊友帶淡色輪廓。
 *   - 震盪：視角不受控制地上下左右晃動，快結束時線性減弱。
 *   - 毒氣：畫面像素化（原版 bits 後處理）、咳嗽（視角先上揚再往下）、失去方向感（強制往某方向移動）。
 *   - 雷達脈衝：一圈往外擴散的環。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpecial {

    public static final KeyMapping USE_KEY = new KeyMapping("key.provinggrounds.special", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.provinggrounds");

    private static final Set<Integer> GLOW_IDS = new HashSet<>();
    private static volatile List<GlowSyncPacket.Entry> mapTargets = List.of();

    private static int coughTicks = -1;
    private static int disorientDir, disorientTicks;
    private static long radarStart = -1;
    private static Vec3 radarCenter = Vec3.ZERO;
    private static int radarRadius;
    private static boolean gasShader;
    private static float shakeYaw, shakePitch;

    private ClientSpecial() {}

    public static void clear() {
        GLOW_IDS.clear();
        SpecialHooks.CLIENT_GLOWING.clear();
        mapTargets = List.of();
        coughTicks = -1;
        disorientTicks = 0;
        SpecialHotbar.pearlFlying = false;
        radarStart = -1;
    }

    public static List<GlowSyncPacket.Entry> mapTargets() {
        return mapTargets;
    }

    // ── 封包 ─────────────────────────────────────────────────────────────────

    public static void applySlot(ItemStack stack) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) SpecialSlot.container(p).setItem(0, stack);
    }

    public static void applyGlow(List<GlowSyncPacket.Entry> entries) {
        GLOW_IDS.clear();
        List<GlowSyncPacket.Entry> map = new ArrayList<>();
        for (GlowSyncPacket.Entry e : entries) {
            GLOW_IDS.add(e.entityId());
            if (e.map()) map.add(e);
        }
        mapTargets = List.copyOf(map);
        rebuildGlowing();
    }

    public static void applyFx(SpecialFxPacket fx) {
        switch (fx.kind()) {
            case SpecialFxPacket.COUGH -> coughTicks = 0;
            case SpecialFxPacket.PEARL -> SpecialHotbar.pearlFlying = fx.a() != 0;
            case SpecialFxPacket.DISORIENT -> { disorientDir = fx.a(); disorientTicks = fx.b(); }
            case SpecialFxPacket.RADAR -> {
                radarStart = System.currentTimeMillis();
                radarCenter = new Vec3(fx.x(), fx.y(), fx.z());
                radarRadius = fx.a();
            }
            default -> {}
        }
    }

    private static void rebuildGlowing() {
        Set<Integer> all = new HashSet<>(GLOW_IDS);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof DecoyEntity d && d.allyOf(mc.player)) all.add(d.getId()); // 分身：自己與隊友看到淡色輪廓
            }
        }
        SpecialHooks.CLIENT_GLOWING.retainAll(all);
        SpecialHooks.CLIENT_GLOWING.addAll(all);
    }

    // ── 每 tick ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { if (gasShader) stopGasShader(mc); clear(); return; }
        while (USE_KEY.consumeClick()) {
            if (mc.screen == null) PGNetwork.sendToServer(new SpecialUsePacket());
        }
        if (p.tickCount % 10 == 0) rebuildGlowing();
        tickConcussion(p);
        tickCough(p);
        if (disorientTicks > 0) disorientTicks--;
        // 毒氣：畫面像素化
        boolean gassed = p.hasEffect(SpecialRegistry.GASSED.get());
        if (gassed && !gasShader) startGasShader(mc);
        else if (!gassed && gasShader) stopGasShader(mc);
    }

    /** 震盪：視角不受控制地晃動（真的改變視角，會影響瞄準），快結束時線性減弱。 */
    private static void tickConcussion(LocalPlayer p) {
        MobEffectInstance e = p.getEffect(SpecialRegistry.CONCUSSION.get());
        if (e == null) { shakeYaw = shakePitch = 0; return; }
        double strength;
        try { strength = com.provinggrounds.config.PGConfig.SERVER.special.concussStrength.get(); }
        catch (IllegalStateException ex) { strength = 6; }
        float fade = Math.min(1f, e.getDuration() / 60f); // 最後 3 秒線性減弱
        float t = p.tickCount;
        float targetYaw = (float) (strength * fade * (Math.sin(t * 0.21) * 0.6 + Math.sin(t * 0.53 + 1.3) * 0.4));
        float targetPitch = (float) (strength * fade * 0.6 * (Math.sin(t * 0.17 + 0.7) * 0.6 + Math.sin(t * 0.61) * 0.4));
        // 每 tick 只套用「變化量」，玩家自己的滑鼠操作仍然有效
        p.setYRot(p.getYRot() + (targetYaw - shakeYaw));
        p.setXRot(Math.max(-90, Math.min(90, p.getXRot() + (targetPitch - shakePitch))));
        shakeYaw = targetYaw;
        shakePitch = targetPitch;
    }

    /** 咳嗽：前 4 tick 視角微微上揚，接著 6 tick 劇烈往下。 */
    private static void tickCough(LocalPlayer p) {
        if (coughTicks < 0) return;
        float d = coughTicks < 4 ? -1.2f : coughTicks < 10 ? 3.5f : 0;
        p.setXRot(Math.max(-90, Math.min(90, p.getXRot() + d)));
        if (++coughTicks >= 10) coughTicks = -1;
    }

    /** 失去方向感：覆寫移動輸入（0 前、1 後、2 左、3 右）。 */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (disorientTicks <= 0) return;
        var in = event.getInput();
        in.up = disorientDir == 0;
        in.down = disorientDir == 1;
        in.left = disorientDir == 2;
        in.right = disorientDir == 3;
        in.forwardImpulse = disorientDir == 0 ? 1 : disorientDir == 1 ? -1 : 0;
        in.leftImpulse = disorientDir == 2 ? 1 : disorientDir == 3 ? -1 : 0;
    }

    /** 動作鎖定：倒地、隱形斗篷時，在客戶端就先擋掉左右鍵（伺服器也會擋）。 */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !ActionLock.blocksActions(p)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ── 毒氣的像素化畫面（原版 shaders/post/bits.json） ────────────────────

    private static final ResourceLocation BITS = new ResourceLocation("shaders/post/bits.json");
    private static Method loadEffect;

    private static void startGasShader(Minecraft mc) {
        try {
            if (loadEffect == null) loadEffect = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "m_109128_", ResourceLocation.class);
            loadEffect.invoke(mc.gameRenderer, BITS);
            gasShader = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            gasShader = true; // 失敗就不再嘗試（只是少了模糊效果）
            ProvingGroundsMod.LOGGER.warn("Gas blur shader unavailable: {}", e.toString());
        }
    }

    private static void stopGasShader(Minecraft mc) {
        gasShader = false;
        PostChain cur = mc.gameRenderer.currentEffect();
        if (cur != null && cur.getName().contains("bits")) mc.gameRenderer.shutdownEffect();
    }

    // ── 雷達脈衝的擴散環 ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || radarStart < 0) return;
        float t = (System.currentTimeMillis() - radarStart) / 1500f;
        if (t >= 1) { radarStart = -1; return; }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double r = radarRadius * t;
        int alpha = (int) (200 * (1 - t));
        int seg = 96;
        float y0 = (float) radarCenter.y + 0.1f, y1 = y0 + 1.2f;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) (radarCenter.x + Math.cos(a0) * r), z0 = (float) (radarCenter.z + Math.sin(a0) * r);
            float x1 = (float) (radarCenter.x + Math.cos(a1) * r), z1 = (float) (radarCenter.z + Math.sin(a1) * r);
            buf.vertex(m, x0, y0, z0).color(64, 240, 160, alpha).endVertex();
            buf.vertex(m, x1, y0, z1).color(64, 240, 160, alpha).endVertex();
            buf.vertex(m, x1, y1, z1).color(64, 240, 160, 0).endVertex();
            buf.vertex(m, x0, y1, z0).color(64, 240, 160, 0).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
