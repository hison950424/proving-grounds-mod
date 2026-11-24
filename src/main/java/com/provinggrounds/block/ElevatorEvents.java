package com.provinggrounds.block;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: ElevatorEvents.java
 * 目的: 電梯方塊的上下傳送（只在伺服器執行，不需要客戶端封包）。
 *   站在電梯方塊上「跳躍」：往正上方找最近的電梯方塊（最多 MAX_RANGE 格），傳送到它上面。
 *   站在電梯方塊上「蹲下」（按下蹲的那一下）：往正下方找最近的電梯方塊，傳送到它上面。
 *   - 跳躍：原版玩家跳起時伺服器會呼叫 jumpFromGround → LivingJumpEvent，所以伺服器端就能知道。
 *   - 蹲下：每 tick 比對 isShiftKeyDown()（不管蹲下綁在哪個鍵），從「沒蹲」變成「蹲」才算一次。
 *   - 目標電梯上方要有 2 格能站人的空間，否則提示「被擋住」；中間隔著其他方塊沒關係（跟一般電梯模組一樣）。
 *   - 傳送後保留玩家在方塊上的水平位置與視角，清掉速度（不會飛起來或摔傷），短暫冷卻避免連續觸發。
 *   執行者：觸發的玩家（ServerPlayer）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ElevatorEvents {

    /** 往上／往下最多找幾格。 */
    public static final int MAX_RANGE = 64;
    private static final int COOLDOWN_TICKS = 6;

    private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private ElevatorEvents() {}

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) use(p, true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        boolean sneak = p.isShiftKeyDown();
        Boolean was = WAS_SNEAKING.put(p.getUUID(), sneak);
        if (sneak && (was == null || !was)) use(p, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WAS_SNEAKING.remove(event.getEntity().getUUID());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }

    /** 站在電梯方塊上時，往上（up = true）或往下找下一個電梯方塊並傳送。 */
    private static void use(ServerPlayer p, boolean up) {
        if (p.isSpectator() || p.isPassenger() || p.isSleeping()) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(p.getUUID());
        if (until != null && now < until) return;

        BlockPos from = standingOn(p);
        if (from == null) return;

        BlockPos target = null;
        boolean blocked = false;
        int step = up ? 1 : -1;
        for (int i = 1; i <= MAX_RANGE; i++) {
            BlockPos pos = from.offset(0, step * i, 0);
            if (level.isOutsideBuildHeight(pos)) break;
            if (!level.getBlockState(pos).is(PGBlocks.ELEVATOR.get())) continue;
            if (hasRoom(level, pos)) { target = pos; break; }
            blocked = true; // 這台被擋住：繼續找更遠的
        }
        if (target == null) {
            if (blocked) p.displayClientMessage(Component.literal(up ? "上方的電梯被擋住了" : "下方的電梯被擋住了"), true);
            return;
        }

        COOLDOWN_UNTIL.put(p.getUUID(), now + COOLDOWN_TICKS);
        WAS_SNEAKING.put(p.getUUID(), p.isShiftKeyDown());
        double ox = p.getX() - from.getX(), oz = p.getZ() - from.getZ(); // 保留在方塊上的水平位置
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4f, 1.5f);
        p.teleportTo(target.getX() + ox, target.getY() + 1.0, target.getZ() + oz);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4f, 1.5f);
    }

    /** 玩家腳下（含剛起跳的那一刻）是電梯方塊就回傳它的位置。 */
    private static BlockPos standingOn(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        // 起跳那一刻 y 可能已經往上一點點：檢查腳下 0.2 格內
        for (double dy : new double[] { 0.2, 0.5 }) {
            BlockPos pos = BlockPos.containing(p.getX(), p.getY() - dy, p.getZ());
            if (level.getBlockState(pos).is(PGBlocks.ELEVATOR.get()) && p.getY() - (pos.getY() + 1) < 0.6) return pos;
        }
        return null;
    }

    /** 電梯方塊上方 2 格沒有會擋住人的方塊。 */
    private static boolean hasRoom(ServerLevel level, BlockPos elevator) {
        for (int i = 1; i <= 2; i++) {
            BlockPos pos = elevator.above(i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }
}
