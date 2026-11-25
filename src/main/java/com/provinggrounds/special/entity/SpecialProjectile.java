package com.provinggrounds.special.entity;

import com.provinggrounds.special.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * 目的: 特殊物品丟出去的東西（一種實體，依帶著的物品決定行為）：
 *   擊退風暴彈：落地處產生衝擊波。
 *   毒氣彈：落地處形成 3×3×3 毒氣（SpecialWorld）。
 *   傳送珠：不受重力；超過最遠距離、飛太久、或使用者再按一次使用鍵時，傳送到「目前位置正下方第一個方塊」上；
 *           中途撞到方塊就在撞擊點傳送；傳送沒有摔傷；找不到下方方塊（虛空）就取消並退還物品。
 *   定身球（計畫書 6 E2）：落地處半徑 2.5 格所有玩家定身 3 秒。
 *   火焰鑽頭（計畫書 6 E3）：不受重力、會穿過玩家；10 秒沒碰到方塊就消失；碰到方塊就黏住 7 秒，
 *           每秒讓方塊後方扇形範圍內的玩家灼燒（NewSpecials.drillBurn）。
 */
public class SpecialProjectile extends ThrowableItemProjectile {

    private Vec3 start;
    /** 火焰鑽頭：黏住後經過的 tick（-1 = 還在飛）、撞到的表面位置與飛來的方向。 */
    private int stuckTicks = -1;
    private Vec3 drillAt, drillDir;

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
        if (k == SpecialType.WARP_PEARL) {
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.1);
            SpecialConfig s = SpecialConfig.get();
            double horiz = Math.hypot(getX() - start.x, getZ() - start.z);
            if (horiz >= s.pearlRange.get() || tickCount >= SpecialConfig.ticks(s.pearlSeconds)) warpBelow();
        } else if (k == SpecialType.GAS_GRENADE) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.4f, 0.8f, 0.1f), 1f),
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        } else if (k == SpecialType.FIRE_DRILL) {
            if (stuckTicks < 0) {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.005);
                if (tickCount >= NewSpecials.DRILL_FLY_TICKS) { discard(); return; }
            } else {
                setDeltaMovement(Vec3.ZERO);
                if (stuckTicks % 20 == 0) {
                    NewSpecials.drillBurn(level, drillAt, drillDir, getOwner() instanceof ServerPlayer o ? o : null);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 0.7f);
                }
                if (stuckTicks % 2 == 0) NewSpecials.drillParticles(level, drillAt, drillDir);
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
        if (k0 == SpecialType.FIRE_DRILL) { // 火焰鑽頭：穿過玩家，碰到方塊才黏住
            if (stuckTicks >= 0 || !(hit instanceof BlockHitResult bh) || !(level() instanceof ServerLevel lv)) return;
            Vec3 v = getDeltaMovement();
            drillDir = v.lengthSqr() < 1e-6 ? Vec3.atLowerCornerOf(bh.getDirection().getOpposite().getNormal()) : v.normalize();
            drillAt = bh.getLocation();
            stuckTicks = 0;
            setDeltaMovement(Vec3.ZERO);
            setPos(drillAt.subtract(drillDir.scale(0.1)));
            lv.playSound(null, drillAt.x, drillAt.y, drillAt.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.6f);
            return;
        }
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        ServerPlayer owner = getOwner() instanceof ServerPlayer p ? p : null;
        SpecialType k = kind();
        Vec3 at = hit.getLocation();
        if (k == SpecialType.WIND_BOMB) {
            Engines.shockwave(level, at, SpecialConfig.get().windShockRadius.get(), owner);
            discard();
        } else if (k == SpecialType.GAS_GRENADE) {
            SpecialWorld.addGas(level, at, owner);
            discard();
        } else if (k == SpecialType.ROOT_ORB) {
            NewSpecials.rootBurst(level, at);
            discard();
        } else if (k == SpecialType.WARP_PEARL) {
            if (hit instanceof BlockHitResult bh) {
                // 撞到方塊：退到方塊外面一點再傳送
                Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
                warpTo(at.add(n.scale(0.5)));
            } else {
                warpTo(at);
            }
        } else {
            discard();
        }
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
