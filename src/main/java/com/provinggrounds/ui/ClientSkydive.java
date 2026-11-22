package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SkydiveKeyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * 檔案: ClientSkydive.java
 * 目的: 跳傘的客戶端部分（計畫書 B1，只在客戶端執行）。
 *   1. 按下跳躍鍵（預設空白鍵）的瞬間送 SkydiveKeyPacket：飛機上 → 跳出、自由落體 → 開傘、開傘 → 收傘（伺服器判斷與冷卻）。
 *   2. 自由落體：視角俯角限制在設定範圍（預設 0～90 度，不能抬到水平以上）；
 *      速度由公式決定，不用原版物理：下沉速度 = 最低 + (最大 − 最低) × 俯角／90，水平速度 = 最大水平 × (1 − 俯角／90)，
 *      水平方向跟著視角。任何角度都一定往下掉，不能滑翔。
 *   3. 開傘：固定下降速度，水平用移動鍵操控（上限 chute_horizontal）。
 *   4. 自由落體中的玩家（包含別人）畫成頭朝視角方向的姿勢（原版只在鞘翅滑翔時這樣畫）。
 *   玩家移動由客戶端決定，所以速度在客戶端每 tick 設定；數值來自伺服器設定（登入時同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSkydive {

    private ClientSkydive() {}

    // ── 1. 空白鍵 ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (mc.options.keyJump.consumeClick()) pressed = true; // 一律消耗掉，避免累積
        if (!pressed || mc.player == null || mc.screen != null) return;
        byte self = ClientBRState.get().selfState();
        if (self == ClientBRState.SELF_PLANE || self == ClientBRState.SELF_FREEFALL || self == ClientBRState.SELF_CHUTE) {
            PGNetwork.sendToServer(new SkydiveKeyPacket());
        }
    }

    // ── 2、3. 速度與視角 ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer p) || !p.level().isClientSide) return;
        byte self = ClientBRState.get().selfState();
        if (self != ClientBRState.SELF_FREEFALL && self != ClientBRState.SELF_CHUTE) return;
        if (p.onGround() || p.isInWater()) return;
        double pitchMin = cfg(0), pitchMax = cfg(1), maxSink = cfg(2), minSink = cfg(3), maxHoriz = cfg(4), chuteSink = cfg(5), chuteHoriz = cfg(6);
        float yawRad = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad); // 面向的水平方向

        if (self == ClientBRState.SELF_FREEFALL) {
            // 俯角限制：不能抬到水平以上
            float pitch = (float) Mth.clamp(p.getXRot(), pitchMin, pitchMax);
            p.setXRot(pitch);
            double t = Mth.clamp(pitch / 90.0, 0, 1);
            double sink = minSink + (maxSink - minSink) * t;
            double horiz = maxHoriz * (1 - t);
            p.setDeltaMovement(fx * horiz / 20.0, -sink / 20.0, fz * horiz / 20.0);
        } else {
            // 開傘：移動鍵控制水平（前後 = forwardImpulse，左右 = leftImpulse）
            double fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
            double lx = fz, lz = -fx; // 左方 = 面向逆時針轉 90 度
            double vx = fx * fwd + lx * left, vz = fz * fwd + lz * left;
            double len = Math.sqrt(vx * vx + vz * vz);
            if (len > 1) { vx /= len; vz /= len; }
            p.setDeltaMovement(vx * chuteHoriz / 20.0, -chuteSink / 20.0, vz * chuteHoriz / 20.0);
        }
        p.fallDistance = 0;
    }

    /** 讀伺服器同步過來的跳傘設定；還沒載入時用預設值。 */
    private static double cfg(int i) {
        try {
            PGConfig c = PGConfig.SERVER;
            return switch (i) {
                case 0 -> c.skydivePitchMin.get();
                case 1 -> c.skydivePitchMax.get();
                case 2 -> c.freefallMaxSink.get();
                case 3 -> c.freefallMinSink.get();
                case 4 -> c.freefallMaxHorizontal.get();
                case 5 -> c.chuteSink.get();
                default -> c.chuteHorizontal.get();
            };
        } catch (IllegalStateException e) {
            return new double[] { 0, 90, 30, 10, 15, 5, 8 }[i];
        }
    }

    // ── 4. 頭朝下的姿勢 ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!ClientBRState.get().diving().contains(p.getUUID())) return;
        float partial = event.getPartialTick();
        float yaw = Mth.lerp(partial, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
        float pitch = Mth.lerp(partial, p.xRotO, p.getXRot());
        // 以身體中心為軸，往面向的方向倒：俯角 0 = 趴平（頭朝前），俯角 90 = 頭朝正下方
        float ax = Mth.cos(yaw), az = Mth.sin(yaw);
        float half = p.getBbHeight() / 2f;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0, half, 0);
        event.getPoseStack().mulPose(new Quaternionf().rotateAxis((90f + pitch) * Mth.DEG_TO_RAD, ax, 0, az));
        event.getPoseStack().translate(0, -half, 0);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (ClientBRState.get().diving().contains(event.getEntity().getUUID())) event.getPoseStack().popPose();
    }
}
