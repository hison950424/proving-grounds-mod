package com.provinggrounds.modes.br;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.network.BRStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 檔案: BRMovement.java
 * 目的: BR 移動規則。
 *       速度：移動速度屬性的暫時性修改（transient，不寫進玩家存檔，崩潰重啟後自動消失）。
 *             MULTIPLY_TOTAL 與速度藥水、疾跑加成相乘 → 走路與疾跑都是原版的 75%。
 *       跳躍：玩家移動由客戶端決定，所以在客戶端的跳躍事件修正起跳垂直速度，
 *             讓高度 = 原高度 × (jump_height ÷ 原版無藥水高度)，跳躍藥水依同比例縮小；
 *             並抵銷疾跑跳多出來的水平前衝。
 */
public final class BRMovement {

    private static final UUID SPEED_ID = UUID.fromString("6f3c1d2a-8b7e-4c55-9a1e-2b0d7c4e9f10");

    /** 原版重力與空氣阻力（每 tick）。 */
    private static final double GRAVITY = 0.08, DRAG = 0.98;
    /** 原版無藥水起跳速度。 */
    private static final double VANILLA_JUMP_VELOCITY = 0.42;
    private static final double VANILLA_JUMP_HEIGHT = jumpHeightFor(VANILLA_JUMP_VELOCITY);

    /** 由主類別建立一個實例註冊到事件匯流排（接收跳躍事件）。 */
    public BRMovement() {}

    // ── 速度（伺服器端） ──────────────────────────────────────────────────────

    public static void apply(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(SPEED_ID);
        double mult = PGConfig.SERVER.brSpeedMultiplier.get();
        attr.addTransientModifier(new AttributeModifier(SPEED_ID, "pg_br_speed", mult - 1.0,
            AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 移除所有 BR 速度修改（包含倒地緩速）。 */
    public static void remove(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(SPEED_ID);
        attr.removeModifier(DOWNED_ID);
    }

    // ── 倒地緩速（伺服器端） ─────────────────────────────────────────────────

    private static final UUID DOWNED_ID = UUID.fromString("b1d4e7a3-2c5f-4e91-8a3d-6f0c9b2e1d47");

    public static void applyDowned(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(DOWNED_ID);
        attr.addTransientModifier(new AttributeModifier(DOWNED_ID, "pg_br_downed",
            -PGConfig.SERVER.brDownedSpeedPenalty.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public static void removeDowned(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(DOWNED_ID);
    }

    // ── 跳躍（客戶端） ───────────────────────────────────────────────────────

    /** 跳躍事件在客戶端與伺服器都會觸發；玩家移動由客戶端決定，只在客戶端修正。 */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.level().isClientSide) return;
        BRStatePacket state = ClientBRState.get();
        if (state.selfState() == ClientBRState.SELF_DOWNED) {
            // 倒地不能跳：把起跳的垂直速度歸零
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
            return;
        }
        if (state.jumpHeight() <= 0 || state.selfState() != ClientBRState.SELF_ALIVE) return;
        adjustJump(player, state.jumpHeight());
    }

    private static void adjustJump(Player player, double targetHeight) {
        Vec3 v = player.getDeltaMovement();
        if (v.y <= 0) return;
        double scale = targetHeight / VANILLA_JUMP_HEIGHT;
        double newVy = velocityForHeight(jumpHeightFor(v.y) * scale);

        double dx = v.x, dz = v.z;
        if (player.isSprinting()) {
            // 原版疾跑跳會加上 (-sin(yaw)×0.2, cos(yaw)×0.2)，這裡抵銷掉
            float f = player.getYRot() * ((float) Math.PI / 180F);
            dx += Mth.sin(f) * 0.2F;
            dz -= Mth.cos(f) * 0.2F;
        }
        player.setDeltaMovement(dx, newVy, dz);
    }

    /** 以原版物理逐 tick 模擬，求起跳速度 v 能達到的最大高度。 */
    static double jumpHeightFor(double v) {
        // 每 tick 先移動 v，再套用重力與阻力；v 轉為非正時即達最高點（0.42 → 約 1.252 格）
        double y = 0;
        for (int i = 0; i < 200 && v > 0; i++) {
            y += v;
            v = (v - GRAVITY) * DRAG;
        }
        return y;
    }

    /** 二分搜尋：求達到指定高度所需的起跳速度。 */
    static double velocityForHeight(double height) {
        double lo = 0, hi = 3;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (jumpHeightFor(mid) < height) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }
}
