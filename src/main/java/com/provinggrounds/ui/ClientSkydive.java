package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SkydiveKeyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientSkydive.java
 * 目的: 跳傘的客戶端部分（只在客戶端執行）。
 *   1. 按下跳躍鍵（預設空白鍵）的瞬間送 SkydiveKeyPacket（伺服器判斷）：飛機上 → 跳出、鞘翅滑翔 → 開傘、
 *      開傘 → 自由落體、自由落體 → 開傘（後兩者可以反覆切換）。
 *   2. 鞘翅滑翔用原版行為，只限制視角從正下方最多往上抬 glide_pitch_up_max 度（預設 85，也就是最高只到水平線下 5 度）。
 *      離地 auto_chute_height 格時伺服器會自動開傘。自由落體完全是原版掉落。
 *   3. 開傘中：固定下降速度，水平用移動鍵操控（上限 chute_horizontal）。開傘後就回不到鞘翅。
 *   玩家移動由客戶端決定，所以開傘的速度在客戶端每 tick 設定；數值來自伺服器設定（登入時同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSkydive {

    private ClientSkydive() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // 坐上飛機時原版會在快捷欄上方顯示「按下 蹲下 離開」，但飛機上要按空白鍵跳、不能蹲下離開：
        // 搭機期間每 tick 清掉那一行（我們自己的「按 空白鍵 跳傘」提示是 HUD 另外畫的，不受影響）
        if (mc.player != null && mc.player.isPassenger() && ClientBRState.get().selfState() == ClientBRState.SELF_PLANE) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
        }
        boolean pressed = false;
        while (mc.options.keyJump.consumeClick()) pressed = true; // 一律消耗掉，避免累積到下次跳傘才觸發
        if (!pressed || mc.player == null || mc.screen != null) return;
        byte self = ClientBRState.get().selfState();
        if (self == ClientBRState.SELF_PLANE || self == ClientBRState.SELF_GLIDE
                || self == ClientBRState.SELF_FREEFALL || self == ClientBRState.SELF_CHUTE) {
            PGNetwork.sendToServer(new SkydiveKeyPacket());
        }
    }

    /** 鞘翅滑翔中：視角從正下方最多往上抬 glide_pitch_up_max 度（鞘翅本身的速度、操作都不改）。 */
    private static void limitGlide(LocalPlayer p) {
        if (!p.isFallFlying() || ClientBRState.get().selfState() != ClientBRState.SELF_GLIDE) return;
        double pitchUp = 85;
        try {
            pitchUp = PGConfig.SERVER.glidePitchUpMax.get();
        } catch (IllegalStateException ignored) {
            // 設定還沒同步過來：用預設值
        }
        // 原版的俯仰角：90 = 正下方、0 = 水平、-90 = 正上方。設定是「從正下方往上抬幾度」，所以最高能看到的角度 = 90 − 設定值
        float minPitch = (float) (90 - pitchUp);
        if (p.getXRot() < minPitch) p.setXRot(minPitch);
        if (p.xRotO < minPitch) p.xRotO = minPitch;
    }

    /** 開傘中：固定下降速度，移動鍵控制水平（前後 = forwardImpulse，左右 = leftImpulse）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer p) || !p.level().isClientSide) return;
        limitGlide(p);
        if (event.phase != TickEvent.Phase.START) return;
        if (ClientBRState.get().selfState() != ClientBRState.SELF_CHUTE) return;
        if (p.onGround() || p.isInWater()) return;
        double sink = 5, horiz = 12;
        try {
            sink = PGConfig.SERVER.chuteSink.get();
            horiz = PGConfig.SERVER.chuteHorizontal.get();
        } catch (IllegalStateException ignored) {
            // 設定還沒同步過來：用預設值
        }
        float yawRad = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad); // 面向的水平方向
        double fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
        double vx = fx * fwd + fz * left, vz = fz * fwd - fx * left; // 左方 = 面向逆時針轉 90 度
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len > 1) { vx /= len; vz /= len; }
        p.setDeltaMovement(vx * horiz / 20.0, -sink / 20.0, vz * horiz / 20.0);
        p.fallDistance = 0;
    }
}
