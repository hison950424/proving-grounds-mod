package com.provinggrounds.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientProneData;
import com.provinggrounds.compat.ClientGunAim;
import com.provinggrounds.core.WallClimb;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.WallClimbPacket;
import com.provinggrounds.special.client.ClientPlan9Specials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 檔案: ClientWallClimb.java
 * 目的: 客戶端「跳躍後再按空白鍵翻爬方塊」輸入偵測、第一人稱雙手撐起與切槍／切刀動畫控制（只在客戶端）：
 *   1. 僅在離地跳躍狀態（!p.onGround()）且非跳傘／趴下／開介面時按下空白鍵 → 偵測前方 ±45° 內不高於頭頂的方塊，觸發翻爬。
 *   2. 攀爬期間於 RenderHandEvent 先向下收起物品並隱藏手持模型，改繪製第一人稱雙手伸前抓牆再撐起的動作。
 *   3. 翻上平台瞬間重置原版舉起高度並重播 TACZ 槍械／lrtactical 近戰刀械切出動畫（重新切刀畫面）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWallClimb {

    static {
        WallClimb.setLocalFinishHandler(p -> {
            if (p instanceof LocalPlayer lp) {
                triggerEquipRaise(Minecraft.getInstance(), lp);
            }
        });
        // 只有大逃殺裡、自己落地存活時能翻爬（大廳、團隊死鬥、佔點、槍王之王按空白鍵只是普通跳躍）
        WallClimb.setClientModeAllowed(p ->
            com.provinggrounds.client.ClientBRState.get().selfState() == com.provinggrounds.client.ClientBRState.SELF_ALIVE);
    }

    private static boolean jumpWas;
    private static Field fMainHeight, fOMainHeight, fOffHeight, fOOffHeight;
    private static boolean heightFieldsReady, heightFieldsFailed;
    private static Method mRenderPlayerArm;
    private static boolean armMethodReady, armMethodFailed;

    private ClientWallClimb() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer p) || p != mc.player) return;

        boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !jumpWas;
        jumpWas = jumpDown;

        var in = event.getInput();
        if (WallClimb.isClientClimbing(p.getId())) {
            in.forwardImpulse = 0.0f;
            in.leftImpulse = 0.0f;
            in.jumping = false;
            in.shiftKeyDown = false;
            if (p.isUsingItem()) p.stopUsingItem();
            return;
        }

        if (!jumpPressed || mc.screen != null || p.onGround()) return;
        if (isSkydiveActive(p)) return;
        if (ClientProneData.isProne(p.getUUID()) || ClientBRState.get().crawling().contains(p.getUUID())) return;

        WallClimb.ClimbTarget target = WallClimb.findClimbTarget(p);
        if (target == null) return;

        in.jumping = false;
        WallClimb.startClientLocal(p, target);
        ClientGunAim.cancelForClimb(p);
        PGNetwork.sendToServer(new WallClimbPacket(target.wallPos(), target.yaw()));
    }

    private static boolean isSkydiveActive(LocalPlayer p) {
        byte s = ClientBRState.get().selfState();
        if (s == ClientBRState.SELF_PLANE || s == ClientBRState.SELF_GLIDE
                || s == ClientBRState.SELF_FREEFALL || s == ClientBRState.SELF_CHUTE) {
            return true;
        }
        return ClientPlan9Specials.freeSkydiveState(p.getUUID()) != 0;
    }

    /** 攀爬中第一人稱：前 2 tick 收起手上物品／刀／槍，之後隱藏物品並繪製雙手前抓＋撐起手臂動畫。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !WallClimb.isClientClimbing(p.getId())) return;

        float pt = event.getPartialTick();
        if (!WallClimb.isHandsStowed(p, pt)) {
            float stow = WallClimb.stowProgress(p, pt);
            if (stow > 0.0f) {
                event.getPoseStack().translate(0.0, -stow * 0.85, 0.0);
            }
            return;
        }

        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND || p.isInvisible()) return;
        renderClimbArms(mc.gameRenderer.itemInHandRenderer, event.getPoseStack(), event.getMultiBufferSource(),
            event.getPackedLight(), WallClimb.progress(p, pt));
    }

    private static void renderClimbArms(ItemInHandRenderer renderer, PoseStack ps, MultiBufferSource buf, int light, float t) {
        if (!ensureArmMethod()) return;
        drawSingleClimbArm(renderer, ps, buf, light, t, HumanoidArm.LEFT, -1.0f);
        drawSingleClimbArm(renderer, ps, buf, light, t, HumanoidArm.RIGHT, 1.0f);
    }

    private static void drawSingleClimbArm(ItemInHandRenderer renderer, PoseStack ps, MultiBufferSource buf,
                                           int light, float t, HumanoidArm arm, float side) {
        float yOff;
        float zOff;
        float xInward;
        float pitchDeg;
        if (t < 0.32f) {
            float u = smooth(t / 0.32f);
            yOff = Mth.lerp(u, -0.45f, 0.28f);
            zOff = Mth.lerp(u, 0.12f, -0.22f);
            xInward = Mth.lerp(u, 0.04f, -0.10f);
            pitchDeg = Mth.lerp(u, -14.0f, 20.0f);
        } else if (t < 0.85f) {
            float u = smooth((t - 0.32f) / 0.53f);
            yOff = Mth.lerp(u, 0.28f, -0.34f);
            zOff = Mth.lerp(u, -0.22f, -0.06f);
            xInward = -0.10f - Mth.sin(u * (float) Math.PI) * 0.04f;
            pitchDeg = Mth.lerp(u, 20.0f, 44.0f);
        } else {
            float u = smooth((t - 0.85f) / 0.15f);
            yOff = Mth.lerp(u, -0.34f, -0.78f);
            zOff = Mth.lerp(u, -0.06f, 0.14f);
            xInward = Mth.lerp(u, -0.10f, 0.02f);
            pitchDeg = Mth.lerp(u, 44.0f, 10.0f);
        }
        float stagger = side * Mth.sin(t * ((float) Math.PI * 2.0f)) * 0.03f;

        ps.pushPose();
        ps.translate(side * xInward, yOff + stagger, zOff - stagger);
        ps.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        ps.mulPose(Axis.YP.rotationDegrees(side * -10.0f));
        ps.mulPose(Axis.ZP.rotationDegrees(side * -8.0f));
        try {
            mRenderPlayerArm.invoke(renderer, ps, buf, light, 0.0f, 0.0f, arm);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        ps.popPose();
    }

    private static float smooth(float v) {
        float c = Mth.clamp(v, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            jumpWas = false;
            return;
        }
        WallClimb.tickRemoteClients(mc.player != null ? mc.player.getId() : -1);
    }

    private static void triggerEquipRaise(Minecraft mc, LocalPlayer p) {
        resetVanillaHandHeight(mc, p);
        ClientGunAim.triggerItemDraw(p);
    }

    private static void resetVanillaHandHeight(Minecraft mc, LocalPlayer p) {
        if (!ensureHeightFields()) return;
        ItemInHandRenderer renderer = mc.gameRenderer.itemInHandRenderer;
        try {
            if (!p.getMainHandItem().isEmpty()) {
                fMainHeight.setFloat(renderer, 0.0f);
                fOMainHeight.setFloat(renderer, 0.0f);
                renderer.itemUsed(InteractionHand.MAIN_HAND);
            }
            if (!p.getOffhandItem().isEmpty()) {
                fOffHeight.setFloat(renderer, 0.0f);
                fOOffHeight.setFloat(renderer, 0.0f);
                renderer.itemUsed(InteractionHand.OFF_HAND);
            }
        } catch (IllegalAccessException | RuntimeException ignored) {}
    }

    private static boolean ensureHeightFields() {
        if (heightFieldsReady) return true;
        if (heightFieldsFailed) return false;
        try {
            fMainHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109302_");
            fOMainHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109303_");
            fOffHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109304_");
            fOOffHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109305_");
            heightFieldsReady = true;
            return true;
        } catch (RuntimeException e) {
            heightFieldsFailed = true;
            ProvingGroundsMod.LOGGER.warn("ItemInHandRenderer height fields unavailable: {}", e.toString());
            return false;
        }
    }

    private static boolean ensureArmMethod() {
        if (armMethodReady) return true;
        if (armMethodFailed) return false;
        try {
            mRenderPlayerArm = ObfuscationReflectionHelper.findMethod(
                ItemInHandRenderer.class, "m_109346_",
                PoseStack.class, MultiBufferSource.class, int.class, float.class, float.class, HumanoidArm.class);
            armMethodReady = true;
            return true;
        } catch (RuntimeException e) {
            armMethodFailed = true;
            ProvingGroundsMod.LOGGER.warn("ItemInHandRenderer.renderPlayerArm unavailable: {}", e.toString());
            return false;
        }
    }
}
