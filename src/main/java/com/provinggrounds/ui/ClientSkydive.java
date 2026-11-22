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
 *   1. 按下跳躍鍵（預設空白鍵）的瞬間送 SkydiveKeyPacket：飛機上 → 跳出、鞘翅滑翔中 → 開傘（伺服器判斷）。
 *   2. 鞘翅滑翔完全是原版行為（速度、姿勢都不改）；離地 auto_chute_height 格時伺服器會自動開傘。
 *   3. 開傘後：固定下降速度，水平用移動鍵操控（上限 chute_horizontal）。開傘後不能回到鞘翅。
 *   玩家移動由客戶端決定，所以開傘的速度在客戶端每 tick 設定；數值來自伺服器設定（登入時同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSkydive {

    private ClientSkydive() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (mc.options.keyJump.consumeClick()) pressed = true; // 一律消耗掉，避免累積到下次跳傘才觸發
        if (!pressed || mc.player == null || mc.screen != null) return;
        byte self = ClientBRState.get().selfState();
        if (self == ClientBRState.SELF_PLANE || self == ClientBRState.SELF_FREEFALL) {
            PGNetwork.sendToServer(new SkydiveKeyPacket());
        }
    }

    /** 開傘中：固定下降速度，移動鍵控制水平（前後 = forwardImpulse，左右 = leftImpulse）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer p) || !p.level().isClientSide) return;
        if (ClientBRState.get().selfState() != ClientBRState.SELF_CHUTE) return;
        if (p.onGround() || p.isInWater()) return;
        double sink = 5, horiz = 8;
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
