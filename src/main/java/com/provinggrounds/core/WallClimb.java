package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.events.MeleeDrawSpeed;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.WallClimbSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 檔案: WallClimb.java
 * 目的: 跳躍後再按空白鍵翻爬前方方塊核心系統（雙端共用幾何驗證＋零抖動客戶端 travel 接管＋伺服器音效與同步）：
 *   1. 玩家在地面跳躍浮空時，面對（±45° 內）任意有碰撞箱且最高不超過自身頭頂的方塊再按一次空白鍵，即可攀上該方塊。
 *   2. 客戶端於 LivingEntity.travel 階段直接接管位置（取代原本 ClientTickEvent＋伺服器 setPos 互相打架），徹底消除第一人稱視角抖動。
 *   3. 軌跡採「先垂直拉起過頂面、後半段再跨上平台」，身體碰撞箱絕不會刮到牆面；過程伴隨第一人稱／第三人稱雙手撐起與 Hitbox 同步。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class WallClimb {

    public static final int DURATION_TICKS = 9;
    public static final int STOW_TICKS = 2;
    public static final int COOLDOWN_TICKS = 10;
    public static final float MAX_YAW_OFFSET = 45.0f;
    public static final double MIN_FACING_DOT = Math.cos(Math.toRadians(45.0));

    public record ClimbTarget(BlockPos wallPos, Vec3 targetPos, float yaw) {}

    private static final class ClimbState {
        final Vec3 startPos;
        final Vec3 targetPos;
        final float yaw;
        final int stowTicks;
        final int duration;
        final boolean local;
        int tick;

        ClimbState(Vec3 startPos, Vec3 targetPos, float yaw, int stowTicks, int duration, boolean local) {
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.yaw = yaw;
            this.stowTicks = stowTicks;
            this.duration = duration;
            this.local = local;
        }

        int totalTicks() {
            return stowTicks + duration;
        }
    }

    private static final Map<UUID, ClimbState> SERVER_ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SERVER_COOLDOWN = new ConcurrentHashMap<>();

    private static final Map<Integer, ClimbState> CLIENT_ACTIVE = new ConcurrentHashMap<>();
    private static volatile long clientCooldownUntilMs;
    private static volatile @Nullable Consumer<Player> localFinishHandler;

    private WallClimb() {}

    /** 由 ClientWallClimb 註冊完成攀爬時要執行的本地動畫重播回呼（避免共用類別直接依賴客戶端類別）。 */
    public static void setLocalFinishHandler(Consumer<Player> handler) {
        localFinishHandler = handler;
    }

    /** 主手或副手是否拿著任何物品（需要先行收起騰出雙手）。 */
    public static boolean hasHeldItem(Player p) {
        return !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
    }

    /** 雙端通用：該實體是否正在翻爬中（供 ActionLock 等查詢）。 */
    public static boolean isClimbing(Entity e) {
        if (!(e instanceof Player p)) return false;
        return p.level().isClientSide
            ? CLIENT_ACTIVE.containsKey(p.getId())
            : SERVER_ACTIVE.containsKey(p.getUUID());
    }

    /** 尋找跳躍浮空後前方（±45° 內、頂面不高於自身頭頂）可翻上的方塊。 */
    @Nullable
    public static ClimbTarget findClimbTarget(Player p) {
        if (!canStartBase(p)) return null;
        if (p.level().isClientSide) {
            if (CLIENT_ACTIVE.containsKey(p.getId()) || System.currentTimeMillis() < clientCooldownUntilMs) return null;
        } else {
            if (SERVER_ACTIVE.containsKey(p.getUUID())) return null;
            Long cd = SERVER_COOLDOWN.get(p.getUUID());
            if (cd != null && p.level().getGameTime() < cd) return null;
        }

        double rad = Math.toRadians(p.getYRot());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);

        int yTop = Mth.floor(p.getY() + p.getBbHeight());
        int yBot = Mth.floor(p.getY());

        for (double dist : new double[] { 0.50, 0.78, 1.02 }) {
            for (int by = yTop; by >= yBot; by--) {
                BlockPos wallPos = BlockPos.containing(p.getX() + fx * dist, by + 0.5, p.getZ() + fz * dist);
                ClimbTarget target = computeLanding(p, wallPos, p.getYRot(), fx, fz);
                if (target != null) return target;
            }
        }
        return null;
    }

    @Nullable
    private static ClimbTarget computeLanding(Player p, BlockPos wallPos, float yaw, double fx, double fz) {
        Level level = p.level();
        BlockState st = level.getBlockState(wallPos);
        VoxelShape shape = st.getCollisionShape(level, wallPos);
        if (shape.isEmpty()) return null;

        double headTopY = p.getY() + p.getBbHeight();
        double topY = wallPos.getY() + shape.max(Direction.Axis.Y);
        double dy = topY - p.getY();
        if (topY > headTopY + 0.05 || dy < 0.35) return null;

        AABB sb = shape.bounds();
        double minX = wallPos.getX() + sb.minX;
        double maxX = wallPos.getX() + sb.maxX;
        double minZ = wallPos.getZ() + sb.minZ;
        double maxZ = wallPos.getZ() + sb.maxZ;

        double closestX = Mth.clamp(p.getX(), minX, maxX);
        double closestZ = Mth.clamp(p.getZ(), minZ, maxZ);
        double toX = closestX - p.getX();
        double toZ = closestZ - p.getZ();
        double horizDist = Math.hypot(toX, toZ);
        if (horizDist < 0.05) {
            toX = (minX + maxX) * 0.5 - p.getX();
            toZ = (minZ + maxZ) * 0.5 - p.getZ();
            horizDist = Math.hypot(toX, toZ);
        }
        if (horizDist < 0.05 || horizDist > 1.15) return null;

        double facingDot = (toX * fx + toZ * fz) / horizDist;
        if (facingDot < MIN_FACING_DOT) return null;

        double ty = topY + 0.01;
        double stepForward = Math.max(0.55, horizDist + 0.32);
        double tx = (maxX - minX >= 0.42)
            ? Mth.clamp(p.getX() + fx * stepForward, minX + 0.20, maxX - 0.20)
            : (minX + maxX) * 0.5;
        double tz = (maxZ - minZ >= 0.42)
            ? Mth.clamp(p.getZ() + fz * stepForward, minZ + 0.20, maxZ - 0.20)
            : (minZ + maxZ) * 0.5;

        // 檢查頂端落腳空間（排除太矮的天花板）
        AABB landBox = new AABB(tx - 0.28, ty + 0.02, tz - 0.28, tx + 0.28, ty + 1.78, tz + 0.28);
        if (!level.noCollision(p, landBox)) return null;

        // 檢查垂直拉起空間（避免起跳上方有頂蓋撞頭）
        AABB riseBox = new AABB(p.getX() - 0.28, Math.min(p.getY() + 1.0, ty), p.getZ() - 0.28,
            p.getX() + 0.28, ty + 1.78, p.getZ() + 0.28);
        if (!level.noCollision(p, riseBox)) return null;

        return new ClimbTarget(wallPos.immutable(), new Vec3(tx, ty, tz), yaw);
    }

    private static boolean canStartBase(Player p) {
        if (p.onGround()) return false;
        if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.isSleeping() || p.getAbilities().flying) return false;
        if (p.isInWater() || p.isInLava()) return false;
        if (PoseOverride.mustCrawl(p) || p.getPose() == Pose.SWIMMING) return false;
        if (!p.level().isClientSide && BattleRoyaleMode.get().isAirborne(p.getUUID())) return false;
        // 只有大逃殺能翻爬（使用者決定）：伺服器看「大逃殺進行中＋活著的參賽者」，客戶端看自己的大逃殺狀態
        if (p.level().isClientSide) {
            java.util.function.Predicate<Player> allowed = clientModeAllowed;
            if (allowed == null || !allowed.test(p)) return false;
        } else {
            BattleRoyaleMode br = BattleRoyaleMode.get();
            if (!br.isInPlay() || !br.isAliveParticipant(p.getUUID())) return false;
        }
        return true;
    }

    /** 客戶端：現在的模式能不能翻爬（由 ClientWallClimb 註冊，避免共用類別直接依賴客戶端類別）。 */
    private static volatile @Nullable java.util.function.Predicate<Player> clientModeAllowed;

    public static void setClientModeAllowed(java.util.function.Predicate<Player> allowed) {
        clientModeAllowed = allowed;
    }

    /** 先垂直拉起超過牆頂、後半段再平滑向前跨上平台，確保碰撞箱絕不會擦到前方牆面造成抖動。 */
    public static Vec3 interpolatePos(Vec3 start, Vec3 target, float t) {
        float c = Mth.clamp(t, 0.0f, 1.0f);
        float upT = Mth.clamp(c / 0.60f, 0.0f, 1.0f);
        float easeY = upT * upT * (3.0f - 2.0f * upT);
        float fwdT = Mth.clamp((c - 0.56f) / 0.44f, 0.0f, 1.0f);
        float easeXZ = fwdT * fwdT * (3.0f - 2.0f * fwdT);
        return new Vec3(
            Mth.lerp(easeXZ, start.x, target.x),
            Mth.lerp(easeY, start.y, target.y),
            Mth.lerp(easeXZ, start.z, target.z)
        );
    }

    /** 收起手上物品的進度（0 = 剛開始，1 = 已收好；未持有物品或不在攀爬時回傳 0）。 */
    public static float stowProgress(Player p, float partialTick) {
        ClimbState s = p.level().isClientSide ? CLIENT_ACTIVE.get(p.getId()) : SERVER_ACTIVE.get(p.getUUID());
        if (s == null || s.stowTicks <= 0) return 0.0f;
        float tAbs = p.level().isClientSide ? (s.tick + partialTick) : s.tick;
        return Mth.clamp(tAbs / (float) s.stowTicks, 0.0f, 1.0f);
    }

    /** 雙手是否已完成收起物品並騰出來攀爬（第一／三人稱隱藏手上物品、HumanoidArms/Hitbox 收起持槍姿勢）。 */
    public static boolean isHandsStowed(Player p, float partialTick) {
        ClimbState s = p.level().isClientSide ? CLIENT_ACTIVE.get(p.getId()) : SERVER_ACTIVE.get(p.getUUID());
        if (s == null) return false;
        if (s.stowTicks <= 0) return true;
        float tAbs = p.level().isClientSide ? (s.tick + partialTick) : s.tick;
        return tAbs >= s.stowTicks;
    }

    public static boolean isHandsStowed(Player p) {
        return isHandsStowed(p, 0.0f);
    }

    /** 本地玩家攀爬時的基準水平朝向（限制左右 ±45° 轉頭用；未攀爬回傳 Float.NaN）。 */
    public static float climbBaseYaw(Player p) {
        if (!p.level().isClientSide) return Float.NaN;
        ClimbState s = CLIENT_ACTIVE.get(p.getId());
        return (s != null && s.local) ? s.yaw : Float.NaN;
    }

    /** 客戶端任意玩家攀爬時鎖定的身體朝向（未攀爬回傳 Float.NaN）。 */
    public static float climbBodyYaw(Player p) {
        if (!p.level().isClientSide) return Float.NaN;
        ClimbState s = CLIENT_ACTIVE.get(p.getId());
        return s != null ? s.yaw : Float.NaN;
    }

    /** 目前攀爬進度（0 = 未攀爬或收起物品階段；(0, 1] = 攀爬動畫進度）。雙端與 F3+B 共用。 */
    public static float progress(Player p, float partialTick) {
        if (p.level().isClientSide) {
            ClimbState s = CLIENT_ACTIVE.get(p.getId());
            if (s == null || s.duration <= 0) return 0.0f;
            float climbTick = (s.tick + partialTick) - s.stowTicks;
            if (climbTick <= 0.0f) return 0.0f;
            return Mth.clamp(climbTick / (float) s.duration, 0.0f, 1.0f);
        }
        ClimbState s = SERVER_ACTIVE.get(p.getUUID());
        if (s == null || s.duration <= 0) return 0.0f;
        int climbTick = s.tick - s.stowTicks;
        if (climbTick <= 0) return 0.0f;
        return Mth.clamp(climbTick / (float) s.duration, 0.0f, 1.0f);
    }

    // ── 伺服器端權威狀態與廣播（不再每 tick 強行 teleport 覆寫位置，避免與客戶端封包互扯）──

    public static void startServer(ServerPlayer p, BlockPos wallPos, float yaw) {
        if (!canStartBase(p) || SERVER_ACTIVE.containsKey(p.getUUID())) return;
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(wallPos)) return;
        long now = level.getGameTime();
        Long cd = SERVER_COOLDOWN.get(p.getUUID());
        if (cd != null && now < cd) return;

        double dx = p.getX() - (wallPos.getX() + 0.5);
        double dz = p.getZ() - (wallPos.getZ() + 0.5);
        if (dx * dx + dz * dz > 1.85 * 1.85) return;

        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        ClimbTarget target = computeLanding(p, wallPos, yaw, fx, fz);
        if (target == null) return;

        int stow = hasHeldItem(p) ? STOW_TICKS : 0;
        p.stopUsingItem();
        GunCompat.cancelGunAction(p);
        // 爬完客戶端會重新拿槍：那次不要把「收槍時間」算進等待（使用者回饋：舉槍動畫播完還不能開鏡／開槍）
        com.provinggrounds.compat.TaczRedraw.expect(p, 3000L);

        Vec3 start = p.position();
        SERVER_ACTIVE.put(p.getUUID(), new ClimbState(start, target.targetPos(), yaw, stow, DURATION_TICKS, false));
        p.fallDistance = 0.0f;

        if (stow == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f, 1.15f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.5f, 1.25f);
        }
        PGNetwork.sendToAll(new WallClimbSyncPacket(p.getId(), start, target.targetPos(), yaw, stow, DURATION_TICKS));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ClimbState s = SERVER_ACTIVE.get(p.getUUID());
        if (s == null) return;

        ServerLevel level = p.serverLevel();
        p.fallDistance = 0.0f;
        if (p.isUsingItem()) p.stopUsingItem();

        if (p.isDeadOrDying()) {
            SERVER_ACTIVE.remove(p.getUUID());
            PGNetwork.sendToAll(new WallClimbSyncPacket(p.getId(), p.position(), p.position(), s.yaw, 0, 0));
            return;
        }

        s.tick++;
        float frac = Mth.clamp(Math.max(0, s.tick - s.stowTicks) / (float) s.duration, 0.0f, 1.0f);
        Vec3 expected = interpolatePos(s.startPos, s.targetPos, frac);
        if (p.position().distanceToSqr(expected) > 3.0 * 3.0) {
            SERVER_ACTIVE.remove(p.getUUID());
            PGNetwork.sendToAll(new WallClimbSyncPacket(p.getId(), p.position(), p.position(), s.yaw, 0, 0));
            return;
        }

        if (s.stowTicks > 0 && s.tick == s.stowTicks + 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f, 1.15f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.5f, 1.25f);
        }

        if (s.tick >= s.totalTicks()) {
            SERVER_ACTIVE.remove(p.getUUID());
            SERVER_COOLDOWN.put(p.getUUID(), level.getGameTime() + COOLDOWN_TICKS);
            BlockPos below = BlockPos.containing(s.targetPos.x, s.targetPos.y - 0.15, s.targetPos.z);
            BlockState st = level.getBlockState(below);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), st.getSoundType().getStepSound(), SoundSource.PLAYERS, 0.6f, 1.05f);
            MeleeDrawSpeed.restartDraw(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SERVER_ACTIVE.remove(id);
        SERVER_COOLDOWN.remove(id);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SERVER_ACTIVE.remove(p.getUUID()) != null) {
            PGNetwork.sendToAll(new WallClimbSyncPacket(p.getId(), p.position(), p.position(), p.getYRot(), 0, 0));
        }
    }

    // ── 客戶端狀態管理 ──

    public static void startClientLocal(Player p, ClimbTarget target) {
        int stow = hasHeldItem(p) ? STOW_TICKS : 0;
        p.stopUsingItem();
        CLIENT_ACTIVE.put(p.getId(), new ClimbState(p.position(), target.targetPos(), target.yaw(), stow, DURATION_TICKS, true));
    }

    public static void applyClientSync(int entityId, int localPlayerId, Vec3 start, Vec3 target, float yaw, int stowTicks, int duration) {
        if (duration <= 0) {
            CLIENT_ACTIVE.remove(entityId);
            return;
        }
        if (entityId == localPlayerId) {
            ClimbState existing = CLIENT_ACTIVE.get(entityId);
            if (existing != null && existing.local) return;
        }
        CLIENT_ACTIVE.put(entityId, new ClimbState(start, target, yaw, Math.max(0, stowTicks), duration, false));
    }

    public static boolean isClientClimbing(int entityId) {
        return CLIENT_ACTIVE.containsKey(entityId);
    }

    /**
     * 在本地玩家 LivingEntity.travel 階段直接步進翻爬位置（xo/yo/zo 之後、sendPosition 之前），
     * 取消原版重力與牆面碰撞擠壓，徹底消除第一人稱視角抖動。
     * @return true 代表目前正在翻爬並已接管本 tick 移動，呼叫端應取消原版 travel()。
     */
    public static boolean stepLocalTravel(Player localPlayer) {
        ClimbState s = CLIENT_ACTIVE.get(localPlayer.getId());
        if (s == null || !s.local) return false;

        s.tick++;
        if (s.tick <= s.stowTicks) {
            localPlayer.setPos(s.startPos.x, s.startPos.y, s.startPos.z);
        } else {
            float frac = Mth.clamp((s.tick - s.stowTicks) / (float) s.duration, 0.0f, 1.0f);
            Vec3 pos = interpolatePos(s.startPos, s.targetPos, frac);
            localPlayer.setPos(pos.x, pos.y, pos.z);
        }
        localPlayer.setDeltaMovement(Vec3.ZERO);
        localPlayer.fallDistance = 0.0f;

        if (s.tick >= s.totalTicks()) {
            localPlayer.setOnGround(true);
            clientCooldownUntilMs = System.currentTimeMillis() + COOLDOWN_TICKS * 50L;
            CLIENT_ACTIVE.remove(localPlayer.getId());
            Consumer<Player> cb = localFinishHandler;
            if (cb != null) cb.accept(localPlayer);
        }
        return true;
    }

    /** ClientTickEvent 用：僅推進畫面中其他遠端玩家的攀爬動畫進度。 */
    public static void tickRemoteClients(int localPlayerId) {
        if (CLIENT_ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<Integer, ClimbState>> it = CLIENT_ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ClimbState> e = it.next();
            ClimbState s = e.getValue();
            if (s.local && e.getKey() == localPlayerId) continue;
            s.tick++;
            if (s.tick >= s.totalTicks()) it.remove();
        }
    }

    public static void clearClient() {
        CLIENT_ACTIVE.clear();
        clientCooldownUntilMs = 0L;
    }
}
