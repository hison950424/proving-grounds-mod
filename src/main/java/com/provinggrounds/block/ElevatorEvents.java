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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: ElevatorEvents.java
 * 目的: 電梯方塊的上下傳送（伺服器端判斷，客戶端 ClientElevator 只負責偵測按鍵）。
 *   站在電梯方塊上「剛按下跳躍鍵」：往正上方找最近的電梯方塊（最多 MAX_RANGE 格），傳送到它上面。
 *   站在電梯方塊上「剛按下蹲下鍵」：往正下方找最近的電梯方塊，傳送到它上面。
 *   計畫書 7 A5 修正：以前伺服器用「離地往上移動」猜跳躍、每 tick 比對蹲下狀態，傳送後客戶端回報的位置、
 *   自動跳躍、走上台階都可能被當成跳躍，冷卻又只有 0.3 秒 → 傳下去又被傳回來、走上去就被傳走。
 *   現在只有客戶端送來的 ElevatorPacket（按鍵按下的那一下）會觸發；傳送後冷卻 elevator_cooldown_seconds（預設 1 秒）；
 *   蹲下要先放開再按（客戶端偵測按下瞬間，伺服器另外要求傳送後看過一次「沒蹲下」）。
 *   - 目標電梯上方要有 2 格能站人的空間，否則提示「被擋住」；中間隔著其他方塊沒關係。
 *   - 傳送後保留玩家在方塊上的水平位置與視角，清掉速度（不會飛起來或摔傷）。
 *   執行者：觸發的玩家（ServerPlayer）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ElevatorEvents {

    /** 往上／往下最多找幾格。 */
    public static final int MAX_RANGE = 64;

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    /** 傳送後還沒放開蹲下的人（放開前不能再往下）。 */
    private static final java.util.Set<UUID> NEED_RELEASE = new java.util.HashSet<>();

    private ElevatorEvents() {}

    /** 客戶端按下跳躍／蹲下鍵（ElevatorPacket）。 */
    public static void onKey(ServerPlayer p, boolean up) {
        if (!up && NEED_RELEASE.contains(p.getUUID())) return;
        use(p, up);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!p.isShiftKeyDown()) NEED_RELEASE.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEED_RELEASE.remove(event.getEntity().getUUID());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }

    /** 冷卻 tick 數（設定還沒載入時用 1 秒）。 */
    private static int cooldownTicks() {
        try { return (int) Math.round(com.provinggrounds.config.PGConfig.SERVER.elevatorCooldownSeconds.get() * 20); }
        catch (IllegalStateException e) { return 20; }
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

        COOLDOWN_UNTIL.put(p.getUUID(), now + cooldownTicks());
        NEED_RELEASE.add(p.getUUID());
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
