package com.provinggrounds.special.entity;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.special.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 檔案: MineEntity.java
 * 目的: 地雷引擎（計畫書 4 的 B4）：偵查地雷（藍）、地雷（棕）、燃燒地雷（紅橘）、震爆地雷（白）。
 *   - 只有放置者與隊友看得到（MineRenderer 只對同隊畫出來；不能被點到、不能被打到）。
 *   - 敵人進入 mine_trigger_radius（2 格）內觸發，mine_delay_seconds（0.5 秒）後依型別產生效果，效果半徑 mine_effect_radius（3.5 格）。
 *   - 每場開始與結束清除（SpecialWorld.cleanup）。
 */
public class MineEntity extends Entity {

    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> TEAM = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.STRING);

    private int fuse = -1;

    public MineEntity(EntityType<? extends MineEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public static MineEntity create(ServerLevel level, SpecialType type, ServerPlayer owner, Vec3 pos) {
        MineEntity m = new MineEntity(SpecialRegistry.MINE.get(), level);
        m.setPos(pos.x, pos.y, pos.z);
        m.entityData.set(TYPE, (byte) type.ordinal());
        m.entityData.set(OWNER, owner.getStringUUID());
        m.entityData.set(TEAM, owner.getTeam() != null ? owner.getTeam().getName() : "");
        return m;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TYPE, (byte) SpecialType.LAND_MINE.ordinal());
        entityData.define(TEAM, "");
        entityData.define(OWNER, "");
    }

    public SpecialType mineType() {
        int i = entityData.get(TYPE);
        SpecialType[] all = SpecialType.values();
        return i >= 0 && i < all.length ? all[i] : SpecialType.LAND_MINE;
    }

    public String team() { return entityData.get(TEAM); }

    @Nullable
    public UUID owner() {
        try { return UUID.fromString(entityData.get(OWNER)); } catch (IllegalArgumentException e) { return null; }
    }

    /** 地雷的顏色（藍／棕／紅橘／白）。 */
    public int color() {
        return switch (mineType()) {
            case RECON_MINE -> 0x3070F0;
            case FIRE_MINE -> 0xF06020;
            case SHOCK_MINE -> 0xF0F0F0;
            default -> 0x704020;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        }
        if (!(level() instanceof ServerLevel level)) return;
        SpecialConfig s = SpecialConfig.get();
        ServerPlayer owner = ownerPlayer();
        if (fuse < 0) {
            if (tickCount < 20) return; // 放下後 1 秒才開始偵測
            double r = s.mineTriggerRadius.get();
            for (LivingEntity e : Engines.livingAround(level, position(), r)) {
                if (!(e instanceof ServerPlayer p) || !BattleRoyaleMode.get().isAliveParticipant(p.getUUID())) continue;
                if (owner != null ? Engines.ally(owner, p) : p.getUUID().equals(owner())) continue;
                fuse = SpecialConfig.ticks(s.mineDelaySeconds);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1f, 1.6f);
                break;
            }
            return;
        }
        if (fuse-- > 0) return;
        detonate(level, owner);
        discard();
    }

    private void detonate(ServerLevel level, @Nullable ServerPlayer owner) {
        SpecialConfig s = SpecialConfig.get();
        double r = s.mineEffectRadius.get();
        Vec3 c = position();
        boolean ff = s.friendlyFire.get();
        switch (mineType()) {
            case RECON_MINE -> {
                for (LivingEntity e : Engines.livingAround(level, c, r)) {
                    if (e instanceof ServerPlayer p && owner != null && !Engines.ally(owner, p)) {
                        GlowManager.add(p, GlowManager.Audience.ONE, owner, true, s.glowSeconds.get());
                    }
                }
                level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.3, c.z, 30, r * 0.4, 0.3, r * 0.4, 0.02);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.8f);
            }
            case LAND_MINE -> {
                for (LivingEntity e : Engines.livingAround(level, c, r)) {
                    if (!ff && Engines.ally(owner, e)) continue;
                    double k = Math.max(0, 1 - e.position().distanceTo(c) / r);
                    e.hurt(Engines.source(level, owner), (float) (s.landMineDamage.get() * k));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SpecialConfig.ticks(s.landMineSlowSeconds), 1, false, false, true));
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 1.0f);
            }
            case FIRE_MINE -> {
                for (LivingEntity e : Engines.livingAround(level, c, r)) {
                    if (!ff && Engines.ally(owner, e)) continue;
                    SpecialWorld.burn(e, owner, SpecialConfig.ticks(s.fireMineSeconds), s.fireMineDamage.get().floatValue());
                }
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 80, r * 0.4, 0.3, r * 0.4, 0.05);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.5f, 0.8f);
            }
            case SHOCK_MINE -> Engines.shockwave(level, c, r, owner);
            default -> {}
        }
    }

    @Nullable
    private ServerPlayer ownerPlayer() {
        UUID id = owner();
        return id == null || getServer() == null ? null : getServer().getPlayerList().getPlayer(id);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TYPE, tag.getByte("Type"));
        entityData.set(TEAM, tag.getString("Team"));
        entityData.set(OWNER, tag.getString("Owner"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Type", entityData.get(TYPE));
        tag.putString("Team", entityData.get(TEAM));
        tag.putString("Owner", entityData.get(OWNER));
    }

    /** 客戶端：這個地雷要不要畫出來（只有放置者與隊友看得到）。 */
    public boolean visibleTo(@Nullable Entity viewer) {
        if (viewer == null) return false;
        if (viewer.getStringUUID().equals(entityData.get(OWNER))) return true;
        String t = team();
        return !t.isEmpty() && viewer.getTeam() != null && t.equals(viewer.getTeam().getName());
    }
}
