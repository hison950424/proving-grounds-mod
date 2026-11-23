package com.provinggrounds.special.entity;

import com.provinggrounds.special.SpecialConfig;
import com.provinggrounds.special.SpecialRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 檔案: DecoyEntity.java
 * 目的: 殘影分身（計畫書 4 的 34 號）。
 *   - 外觀複製使用者的皮膚（DecoyRenderer 依 OWNER 取皮膚）、護甲與手持物（生成時複製到這個實體的裝備格）。
 *   - 朝使用者當時面向的方向奔跑（疾跑速度）decoy_seconds 秒後消失；前方有 1 格高的方塊會自動跳起。
 *   - 只有 1 點血：被打中就冒煙消失，不算擊殺、不掉東西。
 *   - 隊友看到帶淡色輪廓（EntitySpecialMixin 對同隊的人畫發光外框），避免誤傷；不出現在地圖上。
 */
public class DecoyEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEAM = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.STRING);

    /** 疾跑速度（格/tick）：原版疾跑約 5.6 格/秒 × BR 速度倍率。 */
    private double speed = 0.224;
    private int life = 200;
    private float runYaw;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    public static DecoyEntity create(ServerLevel level, ServerPlayer owner) {
        DecoyEntity d = new DecoyEntity(SpecialRegistry.DECOY.get(), level);
        d.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0);
        d.setYHeadRot(owner.getYRot());
        d.setYBodyRot(owner.getYRot());
        d.runYaw = owner.getYRot();
        d.entityData.set(OWNER, owner.getStringUUID());
        d.entityData.set(TEAM, owner.getTeam() != null ? owner.getTeam().getName() : "");
        d.setCustomName(owner.getName());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            d.setItemSlot(slot, owner.getItemBySlot(slot).copy());
            d.setDropChance(slot, 0);
        }
        d.speed = 0.28 * com.provinggrounds.config.PGConfig.SERVER.brSpeedMultiplier.get();
        d.life = SpecialConfig.ticks(SpecialConfig.get().decoySeconds);
        d.setSprinting(true);
        return d;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, "");
        entityData.define(TEAM, "");
    }

    @Nullable
    public UUID owner() {
        try { return UUID.fromString(entityData.get(OWNER)); } catch (IllegalArgumentException e) { return null; }
    }

    public String team() { return entityData.get(TEAM); }

    /** 這個人是不是使用者或隊友（客戶端用來畫淡色輪廓）。 */
    public boolean allyOf(@Nullable Entity viewer) {
        if (viewer == null) return false;
        if (viewer.getStringUUID().equals(entityData.get(OWNER))) return true;
        String t = team();
        return !t.isEmpty() && viewer.getTeam() != null && t.equals(viewer.getTeam().getName());
    }

    @Override
    protected void registerGoals() {} // 沒有 AI，只往前跑

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (--life <= 0) { poof(); return; }
        setYRot(runYaw);
        setYHeadRot(runYaw);
        setYBodyRot(runYaw);
        setSprinting(true);
        double rad = Math.toRadians(runYaw);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(-Math.sin(rad) * speed, v.y, Math.cos(rad) * speed);
        if (horizontalCollision && onGround()) jumpFromGround(); // 前方 1 格高的方塊自動跳起
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
        poof();
        return false; // 不算傷害、不算擊殺
    }

    /** 冒煙消失。 */
    public void poof() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double dist) { return false; }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {}

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Owner", entityData.get(OWNER));
        tag.putString("Team", entityData.get(TEAM));
        tag.putInt("Life", life);
        tag.putFloat("RunYaw", runYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER, tag.getString("Owner"));
        entityData.set(TEAM, tag.getString("Team"));
        life = tag.getInt("Life");
        runYaw = tag.getFloat("RunYaw");
    }
}
