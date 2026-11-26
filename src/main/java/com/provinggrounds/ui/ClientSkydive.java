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
 *   2. 鞘翅滑翔用原版行為，只限制視角從正下方最多往上抬 glide_pitch_up_max 度（預設 75，也就是最高只到水平線下 15 度）。
 *      離地 auto_chute_height 格時伺服器會自動開傘。自由落體完全是原版掉落。
 *   3. 開傘中：固定下降速度，水平用移動鍵操控（上限 chute_horizontal）。開傘後就回不到鞘翅。
 *   玩家移動由客戶端決定，所以開傘的速度在客戶端每 tick 設定；數值來自伺服器設定（登入時同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSkydive {

    private static boolean wasOnPlane;

    private ClientSkydive() {}

    /** 上飛機到落地：快捷欄鎖在第 9 格（降落傘）。 */
    /** 計畫書 6 D4：跳傘不再鎖快捷欄（沒有降落傘物品了）。保留這個方法讓其他地方的呼叫照常運作。 */
    public static boolean hotbarLocked() {
        return false;
    }

    /** 鎖住時：數字鍵換格無效、選在第 9 格（伺服器也會再強制一次）。 */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !hotbarLocked()) return;
        for (var key : mc.options.keyHotbarSlots) while (key.consumeClick()) { /* 吃掉 */ }
        mc.player.getInventory().selected = com.provinggrounds.item.ParachuteItem.SLOT;
    }

    /** 鎖住時：滑鼠滾輪不能換格。 */
    @SubscribeEvent
    public static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && hotbarLocked()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // 坐上飛機時原版會在快捷欄上方顯示「按下 蹲下 離開」，但飛機上要按空白鍵跳、不能蹲下離開：
        // 搭機期間及剛跳離飛機那 1 tick 清掉那一行，避免跳傘後下方殘留原版提示與 HUD 重疊
        boolean onPlane = mc.player != null && ClientBRState.get().selfState() == ClientBRState.SELF_PLANE;
        boolean ridingPlaneCarrier = mc.player != null && mc.player.isPassenger() && ClientBRState.active()
            && mc.player.getVehicle() instanceof net.minecraft.world.entity.Display;
        if (onPlane || ridingPlaneCarrier || wasOnPlane) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
        }
        wasOnPlane = onPlane || ridingPlaneCarrier;

        boolean pressed = false;
        while (mc.options.keyJump.consumeClick()) pressed = true; // 一律消耗掉，避免累積到下次跳傘才觸發
        if (!pressed || mc.player == null || mc.screen != null) return;
        byte self = effectiveState(mc.player);
        if (self == ClientBRState.SELF_PLANE || self == ClientBRState.SELF_GLIDE
                || self == ClientBRState.SELF_FREEFALL || self == ClientBRState.SELF_CHUTE) {
            PGNetwork.sendToServer(new SkydiveKeyPacket());
        }
    }

    /** 有效跳傘狀態：大逃殺內看 ClientBRState，非大逃殺（重新跳傘器）看 ClientPlan9Specials。 */
    private static byte effectiveState(LocalPlayer p) {
        if (ClientBRState.active()) return ClientBRState.get().selfState();
        return switch (com.provinggrounds.special.client.ClientPlan9Specials.freeSkydiveState(p.getUUID())) {
            case 1 -> ClientBRState.SELF_GLIDE;
            case 2 -> ClientBRState.SELF_CHUTE;
            case 3 -> ClientBRState.SELF_FREEFALL;
            default -> ClientBRState.SELF_ALIVE;
        };
    }

    /** 本地端已接觸地面／水中／飛行：用來在伺服器封包回來前先即時結束開傘／滑翔提示與限制。 */
    public static boolean isLocallyLanded(net.minecraft.world.entity.player.Player p) {
        return p.onGround() || p.isInWater() || p.getAbilities().flying;
    }

    /** 鞘翅滑翔中允許的最小俯仰角（Float.NaN = 不限制；供 EntityTurnMixin 與每 tick 保護共用）。 */
    public static float glideMinPitch(LocalPlayer p) {
        if (p == null || isLocallyLanded(p) || !p.isFallFlying() || effectiveState(p) != ClientBRState.SELF_GLIDE) {
            return Float.NaN;
        }
        double pitchUp = 75;
        try {
            pitchUp = PGConfig.SERVER.glidePitchUpMax.get();
        } catch (IllegalStateException ignored) {
            // 設定還沒同步過來：用預設值
        }
        // 原版的俯仰角：90 = 正下方、0 = 水平、-90 = 正上方。設定是「從正下方往上抬幾度」，所以最高能看到的角度 = 90 − 設定值
        return (float) (90.0 - pitchUp);
    }

    /** 鞘翅滑翔中：確保起始俯仰角不超過 glideMinPitch（滑鼠輸入夾限主要由 EntityTurnMixin 在當幀硬擋住）。 */
    private static void limitGlide(LocalPlayer p) {
        float minPitch = glideMinPitch(p);
        if (Float.isNaN(minPitch)) return;
        if (p.getXRot() < minPitch) p.setXRot(minPitch);
        if (p.xRotO < minPitch) p.xRotO = minPitch;
    }

    /** 開傘中：固定下降速度，移動鍵控制水平（前後 = forwardImpulse，左右 = leftImpulse）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer p) || !p.level().isClientSide) return;
        limitGlide(p);
        if (event.phase != TickEvent.Phase.START) return;
        if (effectiveState(p) != ClientBRState.SELF_CHUTE) return;
        if (isLocallyLanded(p)) return;
        double sink = 5, horiz = 12, drift = 4;
        try {
            sink = PGConfig.SERVER.chuteSink.get();
            horiz = PGConfig.SERVER.chuteHorizontal.get();
            drift = PGConfig.SERVER.chuteDrift.get();
        } catch (IllegalStateException ignored) {
            // 設定還沒同步過來：用預設值
        }
        float yawRad = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad); // 面向的水平方向
        double fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
        double vx = fx * fwd + fz * left, vz = fz * fwd - fx * left; // 左方 = 面向逆時針轉 90 度
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len > 1) { vx /= len; vz /= len; }
        // 降落傘打開時一直緩緩往前飄（不按方向鍵也會）；按方向鍵再加上去，總速度不超過 chute_horizontal
        double sx = vx * horiz + fx * drift, sz = vz * horiz + fz * drift;
        double speed = Math.sqrt(sx * sx + sz * sz), cap = Math.max(horiz, drift);
        if (speed > cap) { sx = sx / speed * cap; sz = sz / speed * cap; }
        p.setDeltaMovement(sx / 20.0, -sink / 20.0, sz / 20.0);
        p.fallDistance = 0;
    }
}
