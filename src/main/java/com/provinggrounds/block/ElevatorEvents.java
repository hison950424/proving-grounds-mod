package com.provinggrounds.block;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: ElevatorEvents.java
 * 目的: 電梯方塊的上下傳送（伺服器端判斷，客戶端 ClientElevator 只負責偵測按鍵）：
 *   - 大廳／非對戰／創造模式：立即傳送，冷卻 elevator_cooldown_seconds（預設 1 秒）。
 *   - 大逃殺與團隊模式對戰中（生存／冒險模式）：需於電梯方塊上讀條啟動 1.8 秒（36 tick），
 *     期間出發與目標樓層都會發出機械提示音與雲霧粒子讓敵人察覺；
 *     在電梯上方跳躍不會取消搭乘，水平離開方塊或受到傷害才會中斷；對戰中不設額外冷卻時間。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ElevatorEvents {

    /** 往上／往下最多找幾格。 */
    public static final int MAX_RANGE = 64;
    /** 大逃殺啟動讀條（1.8 秒 = 36 tick）。 */
    public static final int BR_DELAY_TICKS = 36;
    /** 大逃殺搭完後的冷卻時間（0 = 取消冷卻，只保留啟動使用時間）。 */
    public static final int BR_COOLDOWN_TICKS = 0;

    private record PendingRide(ServerLevel level, BlockPos from, BlockPos target, boolean up, long triggerTick) {}

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, PendingRide> PENDING = new HashMap<>();
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

        PendingRide ride = PENDING.get(p.getUUID());
        if (ride == null) return;

        ServerLevel level = p.serverLevel();
        if (p.isDeadOrDying() || !isMatchActive()
            || level != ride.level() || !isWithinElevatorColumn(p, ride.from())) {
            PENDING.remove(p.getUUID());
            p.displayClientMessage(Component.literal("已離開電梯，取消搭乘").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        long now = level.getGameTime();
        long left = ride.triggerTick() - now;
        if (left <= 0) {
            PENDING.remove(p.getUUID());
            if (!hasRoom(level, ride.target())) {
                p.displayClientMessage(Component.literal(ride.up() ? "上方的電梯被擋住了" : "下方的電梯被擋住了")
                    .withStyle(ChatFormatting.RED), true);
                return;
            }
            completeTeleport(p, level, ride.from(), ride.target(), BR_COOLDOWN_TICKS, true);
            return;
        }

        if (left % 8 == 0) {
            p.displayClientMessage(Component.literal(String.format("電梯啟動中（%.1f 秒，請勿離開方塊）", left / 20.0))
                .withStyle(ChatFormatting.AQUA), true);
            spawnWarningParticles(level, ride.from(), 3);
            spawnWarningParticles(level, ride.target(), 4);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.getAmount() <= 0) return;
        if (PENDING.remove(sp.getUUID()) != null) {
            sp.displayClientMessage(Component.literal("受到攻擊，電梯搭乘已中斷！").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        NEED_RELEASE.remove(id);
        COOLDOWN_UNTIL.remove(id);
        PENDING.remove(id);
    }

    /** 冷卻 tick 數（設定還沒載入時用 1 秒）。 */
    private static int cooldownTicks() {
        try { return (int) Math.round(com.provinggrounds.config.PGConfig.SERVER.elevatorCooldownSeconds.get() * 20); }
        catch (IllegalStateException e) { return 20; }
    }

    private static boolean isMatchActive() {
        return BattleRoyaleMode.get().isRunning() || com.provinggrounds.modes.arena.ArenaManager.isRunning();
    }

    private static boolean useMatchRule(ServerPlayer p) {
        return isMatchActive() && !p.isCreative() && !p.isSpectator();
    }

    /** 站在電梯方塊上時，往上（up = true）或往下找下一個電梯方塊並傳送。 */
    private static void use(ServerPlayer p, boolean up) {
        if (p.isSpectator() || p.isPassenger() || p.isSleeping()) return;
        BlockPos from = standingOn(p);
        if (from == null) return;
        if (PENDING.containsKey(p.getUUID())) return;

        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(p.getUUID());
        if (until != null && now < until) {
            double left = (until - now) / 20.0;
            p.displayClientMessage(Component.literal(String.format("電梯冷卻中（剩 %.1f 秒）", left))
                .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

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

        if (useMatchRule(p)) {
            NEED_RELEASE.add(p.getUUID());
            PENDING.put(p.getUUID(), new PendingRide(level, from, target, up, now + BR_DELAY_TICKS));
            level.playSound(null, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.1f, 0.9f);
            level.playSound(null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.1f, 1.1f);
            spawnWarningParticles(level, from, 6);
            spawnWarningParticles(level, target, 8);
            p.displayClientMessage(Component.literal("電梯啟動中（1.8 秒，請勿離開方塊）...").withStyle(ChatFormatting.AQUA), true);
            return;
        }

        completeTeleport(p, level, from, target, cooldownTicks(), false);
    }

    private static void completeTeleport(ServerPlayer p, ServerLevel level, BlockPos from, BlockPos target,
                                         int cdTicks, boolean loudWarning) {
        long now = level.getGameTime();
        if (cdTicks > 0) COOLDOWN_UNTIL.put(p.getUUID(), now + cdTicks);
        else COOLDOWN_UNTIL.remove(p.getUUID());
        NEED_RELEASE.add(p.getUUID());
        double ox = p.getX() - from.getX(), oz = p.getZ() - from.getZ(); // 保留在方塊上的水平位置
        if (loudWarning) {
            level.playSound(null, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.2f, 0.9f);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4f, 1.5f);
        }
        p.teleportTo(target.getX() + ox, target.getY() + 1.0, target.getZ() + oz);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (loudWarning) {
            level.playSound(null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2f, 1.2f);
            level.playSound(null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.1f, 1.0f);
            spawnWarningParticles(level, target, 10);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4f, 1.5f);
        }
    }

    private static void spawnWarningParticles(ServerLevel level, BlockPos pos, int count) {
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
            count, 0.25, 0.2, 0.25, 0.02);
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

    /** 讀條期間判定玩家是否仍在出發電梯上方（允許原地跳躍，不會因跳上樓起跳高度 > 0.6 格而誤取消）。 */
    private static boolean isWithinElevatorColumn(ServerPlayer p, BlockPos from) {
        double dx = Math.abs(p.getX() - (from.getX() + 0.5));
        double dz = Math.abs(p.getZ() - (from.getZ() + 0.5));
        if (dx > 0.72 || dz > 0.72) return false;
        double relY = p.getY() - (from.getY() + 1.0);
        return relY >= -0.15 && relY <= 1.65;
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
