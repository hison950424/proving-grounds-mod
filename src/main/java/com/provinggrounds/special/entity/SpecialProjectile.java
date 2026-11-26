package com.provinggrounds.special.entity;

import com.provinggrounds.special.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 檔案: SpecialProjectile.java
 * 目的: 特殊道具丟出去的投擲實體（計畫書 4、6、9）：
 *   - 球類品（臨時雪牆、擊退風暴彈、毒氣彈、定身球、磁人手榴彈）：碰到牆壁或天花板反彈 1 次，碰到地面馬上觸發！
 *   - 傳送珠：不受重力，飛到盡頭或再按 Q 傳送到正下方。
 *   - 火焰鑽頭：不受重力、穿過玩家；碰到方塊後往內穿透最多 7 格，穿到對面才開始形成扇形灼燒（超過 7 格則失敗）。
 */
public class SpecialProjectile extends ThrowableItemProjectile {

    private Vec3 start;
    /** 球類品已經反彈的次數（最多反彈牆壁 1 次）。 */
    private int bounces = 0;
    /** 臨時雪牆：記錄上一個格子，沿途升起雪牆。 */
    private long lastSnowCol = Long.MIN_VALUE;
    /** 火焰鑽頭：黏住後經過的 tick（-1 = 還在飛）、穿透後的對面出口位置與飛來的方向。 */
    private int stuckTicks = -1;
    private Vec3 drillExit, drillDir;

    public SpecialProjectile(EntityType<? extends SpecialProjectile> type, Level level) {
        super(type, level);
    }

    public static SpecialProjectile throwFrom(ServerPlayer owner, SpecialType kind, float speed) {
        SpecialProjectile e = new SpecialProjectile(SpecialRegistry.PROJECTILE.get(), owner.level());
        e.setOwner(owner);
        e.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        e.setItem(new ItemStack(SpecialRegistry.item(kind)));
        e.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, speed, 0.5f);
        e.start = e.position();
        owner.level().addFreshEntity(e);
        return e;
    }

    @Nullable
    public SpecialType kind() { return SpecialItem.typeOf(getItem()); }

    @Override
    protected Item getDefaultItem() { return SpecialRegistry.item(SpecialType.WIND_BOMB); }

    @Override
    protected float getGravity() {
        SpecialType k = kind();
        return k == SpecialType.WARP_PEARL || k == SpecialType.FIRE_DRILL ? 0f : 0.03f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        if (start == null) start = position();
        SpecialType k = kind();
        if (k == SpecialType.SNOW_WALL) {
            SpecialConfig s = SpecialConfig.get();
            Vec3 pos = position();
            if (Math.hypot(pos.x - start.x, pos.z - start.z) >= s.snowRange.get()) { discard(); return; }
            int x = (int) Math.floor(pos.x), z = (int) Math.floor(pos.z);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            if (key != lastSnowCol) {
                lastSnowCol = key;
                SpecialWorld.recordSnowColumn(level, x, z, pos.y);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
        } else if (k == SpecialType.WARP_PEARL) {
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.1);
            SpecialConfig s = SpecialConfig.get();
            double horiz = Math.hypot(getX() - start.x, getZ() - start.z);
            if (horiz >= s.pearlRange.get() || tickCount >= SpecialConfig.ticks(s.pearlSeconds)) warpBelow();
        } else if (k == SpecialType.GAS_GRENADE) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.4f, 0.8f, 0.1f), 1f),
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        } else if (k == SpecialType.MAGNET_GRENADE) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.2f, 0.65f, 1.0f), 1.1f),
                getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0);
        } else if (k == SpecialType.FIRE_DRILL) {
            if (stuckTicks < 0) {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.005);
                if (tickCount >= NewSpecials.DRILL_FLY_TICKS) { discard(); return; }
            } else {
                setDeltaMovement(Vec3.ZERO);
                if (stuckTicks % 20 == 0) {
                    NewSpecials.drillBurn(level, drillExit, drillDir, getOwner() instanceof ServerPlayer o ? o : null);
                    level.playSound(null, drillExit.x, drillExit.y, drillExit.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.7f);
                }
                if (stuckTicks % 2 == 0) NewSpecials.drillParticles(level, drillExit, drillDir);
                if (++stuckTicks >= NewSpecials.DRILL_STUCK_TICKS) { discard(); return; }
            }
        } else if (k == SpecialType.ROOT_ORB) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
        if (getY() < level.getMinBuildHeight() - 16) {
            if (k == SpecialType.WARP_PEARL) refund("傳送珠掉出世界，已退還");
            discard();
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        SpecialType k0 = kind();
        if (k0 == SpecialType.FIRE_DRILL) { // 火焰鑽頭（計畫書 9）：穿過方塊（最厚 7 格）到對面才開始形成扇形傷害
            if (stuckTicks >= 0 || !(hit instanceof BlockHitResult bh) || !(level() instanceof ServerLevel lv)) return;
            Vec3 v = getDeltaMovement();
            drillDir = v.lengthSqr() < 1e-6 ? Vec3.atLowerCornerOf(bh.getDirection().getOpposite().getNormal()) : v.normalize();
            Vec3 entry = bh.getLocation();
            Vec3 exit = findDrillExit(lv, entry, drillDir, 7.0);
            if (exit == null) {
                lv.playSound(null, entry.x, entry.y, entry.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.4f);
                if (getOwner() instanceof ServerPlayer o) {
                    o.displayClientMessage(Component.literal("方塊太厚（超過 7 格），火焰鑽頭無法穿透").withStyle(ChatFormatting.YELLOW), true);
                }
                discard();
                return;
            }
            drillExit = exit;
            stuckTicks = 0;
            setDeltaMovement(Vec3.ZERO);
            setPos(entry.subtract(drillDir.scale(0.1)));
            lv.playSound(null, entry.x, entry.y, entry.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.6f);
            lv.playSound(null, exit.x, exit.y, exit.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
            return;
        }

        // 球類特殊道具（計畫書 9）：撞到牆壁或天花板可反彈 1 次；撞到地面（UP）則是馬上觸發
        if (k0 != null && k0.isBounceBall() && hit instanceof BlockHitResult bh && level() instanceof ServerLevel lv) {
            Direction face = bh.getDirection();
            if (face != Direction.UP && bounces < 1) {
                bounces++;
                Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
                Vec3 vel = getDeltaMovement();
                double dot = vel.dot(n);
                Vec3 refl = vel.subtract(n.scale(2.0 * dot)).scale(0.72);
                Vec3 pos = bh.getLocation().add(n.scale(0.15));
                setPos(pos.x, pos.y, pos.z);
                setDeltaMovement(refl);
                lv.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.7f, 1.3f);
                return;
            }
        }

        super.onHit(hit);
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        ServerPlayer owner = getOwner() instanceof ServerPlayer p ? p : null;
        SpecialType k = kind();
        Vec3 at = hit.getLocation();
        if (k == SpecialType.SNOW_WALL) {
            SpecialWorld.recordSnowColumn(level, (int) Math.floor(at.x), (int) Math.floor(at.z), at.y);
            discard();
        } else if (k == SpecialType.WIND_BOMB) {
            Engines.shockwave(level, at, SpecialConfig.get().windShockRadius.get(), owner);
            discard();
        } else if (k == SpecialType.GAS_GRENADE) {
            SpecialWorld.addGas(level, at, owner);
            discard();
        } else if (k == SpecialType.ROOT_ORB) {
            NewSpecials.rootBurst(level, at);
            discard();
        } else if (k == SpecialType.MAGNET_GRENADE) {
            Plan9Specials.magnetBurst(level, at);
            discard();
        } else if (k == SpecialType.WARP_PEARL) {
            if (hit instanceof BlockHitResult bh) {
                Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
                warpTo(at.add(n.scale(0.5)));
            } else {
                warpTo(at);
            }
        } else {
            discard();
        }
    }

    /** 沿著鑽頭方向往方塊內推進（每步 0.25 格，最遠 maxThick 格），找到第一個空方塊當作對面出口；穿不過去回傳 null。 */
    @Nullable
    private static Vec3 findDrillExit(ServerLevel level, Vec3 entry, Vec3 dir, double maxThick) {
        boolean enteredSolid = false;
        for (double d = 0.1; d <= maxThick + 0.25; d += 0.25) {
            Vec3 pt = entry.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(pt);
            boolean solid = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
            if (solid) enteredSolid = true;
            else if (enteredSolid) return pt;
        }
        return null;
    }

    /** 再按一次使用鍵、超過距離或時間：傳送到正下方第一個方塊上。 */
    public void warpBelow() {
        if (!(level() instanceof ServerLevel level)) return;
        BlockPos.MutableBlockPos m = BlockPos.containing(position()).mutable();
        while (m.getY() > level.getMinBuildHeight()) {
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                warpTo(new Vec3(getX(), m.getY() + 1.0, getZ()));
                return;
            }
            m.move(0, -1, 0);
        }
        refund("下方沒有可以站的地方，傳送取消（已退還傳送珠）");
        discard();
    }

    private void warpTo(Vec3 pos) {
        if (getOwner() instanceof ServerPlayer p && p.isAlive() && p.level() == level()) {
            if (p.isPassenger()) p.stopRiding();
            p.teleportTo(pos.x, pos.y, pos.z);
            p.fallDistance = 0;
            level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        }
        SpecialUse.forgetPearl(this);
        discard();
    }

    private void refund(String msg) {
        SpecialUse.forgetPearl(this);
        if (getOwner() instanceof ServerPlayer p) {
            ItemStack back = new ItemStack(SpecialRegistry.item(SpecialType.WARP_PEARL));
            if (!com.provinggrounds.special.DomGear.refund(p) && !SpecialSlot.tryInsert(p, back))
                com.provinggrounds.modes.br.HotbarRules.dropAtFeet(p, back);
            p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
