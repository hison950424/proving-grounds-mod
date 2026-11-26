package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.WallClimbSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
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

/**
 * 檔案: WallClimb.java
 * 目的: 柵欄與石牆攀爬核心系統（雙端共用數學、伺服器端權威移動與廣播、客戶端本地預測＋他人生動同步）。
 *   - 玩家貼著柵欄／石牆／關閉的柵欄門按下空白鍵時，平順地拉起並翻上頂端（9 tick = 0.45 秒）。
 *   - 過程伴隨雙手上抓撐起＋雙腿交替提膝動畫（HumanoidArmsMixin／ProneAnimMixin），並即時同步精準 Hitbox。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class WallClimb {

    public static final int DURATION_TICKS = 9;
    public static final int STOW_TICKS = 3;
    public static final int COOLDOWN_TICKS = 10;
    public static final float MAX_YAW_OFFSET = 45.0f;

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

    private WallClimb() {}

    /** 主手或副手是否拿著任何物品（需要先行收起再爬牆）。 */
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

    /** 是否為可攀爬方塊（柵欄、石牆或關閉中的柵欄門）。 */
    public static boolean isClimbable(BlockState st) {
        if (st.is(BlockTags.FENCES) || st.is(BlockTags.WALLS)) return true;
        return st.is(BlockTags.FENCE_GATES) && st.hasProperty(FenceGateBlock.OPEN) && !st.getValue(FenceGateBlock.OPEN);
    }

    /** 尋找玩家正前方貼著的可攀爬柵欄／石牆。找不到或不合淨空條件回傳 null。 */
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
        int baseY = Mth.floor(p.getY() + 0.25);

        for (double dist : new double[] { 0.48, 0.82 }) {
            for (int dyOff : new int[] { 0, -1 }) {
                BlockPos wallPos = BlockPos.containing(p.getX() + fx * dist, baseY + dyOff, p.getZ() + fz * dist);
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
        if (!isClimbable(st)) return null;

        VoxelShape shape = st.getCollisionShape(level, wallPos);
        // 需要視野大致面向這面牆／柵欄（水平夾角約 ±55° 內），避免背向或側身誤觸發
        double wallCx = wallPos.getX() + (shape.isEmpty() ? 0.5 : (shape.bounds().minX + shape.bounds().maxX) * 0.5);
        double wallCz = wallPos.getZ() + (shape.isEmpty() ? 0.5 : (shape.bounds().minZ + shape.bounds().maxZ) * 0.5);
        double toWallX = wallCx - p.getX();
        double toWallZ = wallCz - p.getZ();
        double horizDist = Math.hypot(toWallX, toWallZ);
        if (horizDist < 0.15 || horizDist > 1.25) return null;
        double facingDot = (toWallX * fx + toWallZ * fz) / horizDist;
        if (facingDot < 0.57) return null;

        double shapeMaxY = shape.isEmpty() ? 1.5 : shape.max(Direction.Axis.Y);
        double topY = wallPos.getY() + shapeMaxY + 0.01;
        double dy = topY - p.getY();
        if (dy < 0.45 || dy > 1.85) return null;

        double tx = Mth.clamp(p.getX() + fx * 0.65, wallPos.getX() + 0.28, wallPos.getX() + 0.72);
        double tz = Mth.clamp(p.getZ() + fz * 0.65, wallPos.getZ() + 0.28, wallPos.getZ() + 0.72);

        // 檢查頂端落腳空間（排除雙層高牆與矮天花板）
        AABB landBox = new AABB(tx - 0.25, topY + 0.02, tz - 0.25, tx + 0.25, topY + 1.48, tz + 0.25);
        if (!level.noCollision(p, landBox)) return null;

        // 檢查起跳路徑上方淨空
        AABB riseBox = new AABB(p.getX() - 0.22, p.getY() + 1.5, p.getZ() - 0.22, p.getX() + 0.22, topY + 1.2, p.getZ() + 0.22);
        if (!level.noCollision(p, riseBox)) return null;

        return new ClimbTarget(wallPos.immutable(), new Vec3(tx, topY, tz), yaw);
    }

    private static boolean canStartBase(Player p) {
        if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.isSleeping() || p.getAbilities().flying) return false;
        if (p.isInWater() || p.isInLava()) return false;
        return !PoseOverride.mustCrawl(p) && p.getPose() != Pose.SWIMMING;
    }

    /** 先拉升高度、後半往前踏上牆面的拋物平滑曲線。 */
    public static Vec3 interpolatePos(Vec3 start, Vec3 target, float t) {
        float c = Mth.clamp(t, 0.0f, 1.0f);
        float upT = Mth.clamp(c / 0.72f, 0.0f, 1.0f);
        float easeY = upT * upT * (3.0f - 2.0f * upT);
        float fwdT = Mth.clamp((c - 0.22f) / 0.78f, 0.0f, 1.0f);
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

    /** 雙手是否已完成收起物品並騰出來攀爬（第三人稱隱藏手上物品、HumanoidArms/Hitbox 收起持槍姿勢）。 */
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

    /** 本地玩家攀爬時的基準水平朝向（限制左右轉頭用；未攀爬回傳 Float.NaN）。 */
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

    // ── 伺服器端權威狀態與廣播 ──

    public static void startServer(ServerPlayer p, BlockPos wallPos, float yaw) {
        if (!canStartBase(p) || SERVER_ACTIVE.containsKey(p.getUUID())) return;
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(wallPos)) return;
        long now = level.getGameTime();
        Long cd = SERVER_COOLDOWN.get(p.getUUID());
        if (cd != null && now < cd) return;

        double dx = p.getX() - (wallPos.getX() + 0.5);
        double dz = p.getZ() - (wallPos.getZ() + 0.5);
        if (dx * dx + dz * dz > 1.65 * 1.65) return;

        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        ClimbTarget target = computeLanding(p, wallPos, yaw, fx, fz);
        if (target == null) return;

        int stow = hasHeldItem(p) ? STOW_TICKS : 0;
        p.stopUsingItem();
        GunCompat.cancelGunAction(p);

        Vec3 start = p.position();
        SERVER_ACTIVE.put(p.getUUID(), new ClimbState(start, target.targetPos(), yaw, stow, DURATION_TICKS, false));
        p.setDeltaMovement(Vec3.ZERO);
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
        if (p.isDeadOrDying() || !canStartBase(p)) {
            SERVER_ACTIVE.remove(p.getUUID());
            PGNetwork.sendToAll(new WallClimbSyncPacket(p.getId(), p.position(), p.position(), s.yaw, 0, 0));
            return;
        }
        if (p.isUsingItem()) p.stopUsingItem();

        s.tick++;
        if (s.tick <= s.stowTicks) {
            p.setPos(s.startPos.x, s.startPos.y, s.startPos.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0f;
            p.hasImpulse = true;
            return;
        }
        if (s.stowTicks > 0 && s.tick == s.stowTicks + 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f, 1.15f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.5f, 1.25f);
        }

        float frac = Mth.clamp((s.tick - s.stowTicks) / (float) s.duration, 0.0f, 1.0f);
        Vec3 pos = interpolatePos(s.startPos, s.targetPos, frac);
        p.setPos(pos.x, pos.y, pos.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0f;
        p.hasImpulse = true;

        if (s.tick >= s.totalTicks()) {
            SERVER_ACTIVE.remove(p.getUUID());
            SERVER_COOLDOWN.put(p.getUUID(), level.getGameTime() + COOLDOWN_TICKS);
            p.setOnGround(true);
            BlockPos below = BlockPos.containing(s.targetPos.x, s.targetPos.y - 0.15, s.targetPos.z);
            BlockState st = level.getBlockState(below);
            level.playSound(null, pos.x, pos.y, pos.z, st.getSoundType().getStepSound(), SoundSource.PLAYERS, 0.6f, 1.05f);
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

    // ── 客戶端狀態管理（ClientWallClimb / WallClimbSyncPacket 呼叫）──

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
            if (existing != null && existing.local) return; // 本地已進入預測攀爬
        }
        CLIENT_ACTIVE.put(entityId, new ClimbState(start, target, yaw, Math.max(0, stowTicks), duration, false));
    }

    public static boolean isClientClimbing(int entityId) {
        return CLIENT_ACTIVE.containsKey(entityId);
    }

    /** 推進客戶端攀爬進度；若本地玩家正好在這一 tick 完成攀爬則回傳 true（供觸發舉起物品動畫）。 */
    public static boolean tickClient(@Nullable Player localPlayer) {
        if (CLIENT_ACTIVE.isEmpty()) return false;
        boolean localFinished = false;
        Iterator<Map.Entry<Integer, ClimbState>> it = CLIENT_ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ClimbState> e = it.next();
            ClimbState s = e.getValue();
            s.tick++;
            if (s.local && localPlayer != null && e.getKey() == localPlayer.getId()) {
                if (s.tick <= s.stowTicks) {
                    localPlayer.setPos(s.startPos.x, s.startPos.y, s.startPos.z);
                } else {
                    float frac = Mth.clamp((s.tick - s.stowTicks) / (float) s.duration, 0.0f, 1.0f);
                    Vec3 pos = interpolatePos(s.startPos, s.targetPos, frac);
                    localPlayer.setPos(pos.x, pos.y, pos.z);
                }
                localPlayer.setDeltaMovement(Vec3.ZERO);
                localPlayer.fallDistance = 0.0f;
            }
            if (s.tick >= s.totalTicks()) {
                if (s.local && localPlayer != null && e.getKey() == localPlayer.getId()) {
                    localPlayer.setOnGround(true);
                    clientCooldownUntilMs = System.currentTimeMillis() + COOLDOWN_TICKS * 50L;
                    localFinished = true;
                }
                it.remove();
            }
        }
        return localFinished;
    }

    public static void clearClient() {
        CLIENT_ACTIVE.clear();
        clientCooldownUntilMs = 0L;
    }
}
