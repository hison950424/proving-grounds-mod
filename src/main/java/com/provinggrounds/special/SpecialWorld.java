package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.CrateBlockEntity;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.special.entity.DecoyEntity;
import com.provinggrounds.special.entity.MineEntity;
import com.provinggrounds.special.entity.SpecialProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: SpecialWorld.java
 * 目的: 特殊物品放到世界裡、會持續一段時間的東西（伺服器每 tick 更新）：
 *   致盲黑霧、煙幕、毒氣、燃燒、雪牆、假槍聲、彈簧鞋的免摔傷。
 *   放到世界裡的方塊（雪塊、假箱）記在存檔（provinggrounds_special），伺服器重開也能清掉。
 *   每場開始與結束呼叫 cleanup：清掉地雷、假箱、分身、雪牆、毒氣、煙霧，並重置所有特殊物品效果。
 */
public final class SpecialWorld {

    private SpecialWorld() {}

    private record Burn(UUID owner, float damage, int[] ticks) {}
    private static final class Cloud { ServerLevel level; Vec3 pos; Vec3 dir; double travelled; UUID owner; }
    private record Area(ServerLevel level, Vec3 center, long end, @Nullable UUID owner) {}
    private static final class SnowTrail { Snowball ball; Vec3 start; long lastCol = Long.MIN_VALUE; }
    private record SnowColumn(ServerLevel level, int x, int z, double ballY, long at) {}
    private static final class Gunfire { ServerLevel level; Vec3 pos; long end; long next; int burst; ResourceLocation gun; Entity anchor; }

    private static final Map<UUID, Burn> BURNS = new HashMap<>();
    private static final List<Cloud> BLIND = new ArrayList<>();
    private static final List<Area> SMOKES = new ArrayList<>();
    private static final List<Area> GASES = new ArrayList<>();
    private static final List<SnowTrail> SNOW = new ArrayList<>();
    private static final List<SnowColumn> COLUMNS = new ArrayList<>();
    private static final List<Gunfire> GUNFIRE = new ArrayList<>();
    private static final Map<UUID, Long> NO_FALL = new HashMap<>();

    // ═════════════════════════════════════════════════════════════════════════
    // 新增
    // ═════════════════════════════════════════════════════════════════════════

    /** 燃燒：每秒燒傷一次（不用原版的火，避免原版每秒再多扣 1）。 */
    public static void burn(LivingEntity e, @Nullable ServerPlayer owner, int ticks, float damagePerSecond) {
        BURNS.put(e.getUUID(), new Burn(owner == null ? null : owner.getUUID(), damagePerSecond, new int[] { ticks }));
    }

    public static void addBlindCloud(ServerPlayer p) {
        Cloud c = new Cloud();
        c.level = p.serverLevel();
        c.dir = p.getLookAngle().normalize();
        c.pos = p.getEyePosition().add(c.dir.scale(1.5));
        c.owner = p.getUUID();
        BLIND.add(c);
        c.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1f, 0.6f);
    }

    public static void addSmoke(ServerPlayer p) {
        long end = p.server.getTickCount() + SpecialConfig.ticks(SpecialConfig.get().smokeSeconds);
        SMOKES.add(new Area(p.serverLevel(), p.position(), end, p.getUUID()));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1f, 0.6f);
    }

    public static void addGas(ServerLevel level, Vec3 at, @Nullable ServerPlayer owner) {
        long end = level.getServer().getTickCount() + SpecialConfig.ticks(SpecialConfig.get().gasSeconds);
        GASES.add(new Area(level, at, end, owner == null ? null : owner.getUUID()));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
    }

    public static void addSnowTrail(ServerPlayer p) {
        Snowball ball = new Snowball(p.level(), p);
        ball.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.5f, 0.5f);
        p.level().addFreshEntity(ball);
        SnowTrail t = new SnowTrail();
        t.ball = ball;
        t.start = ball.position();
        SNOW.add(t);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1f, 0.8f);
    }

    public static void addGunfire(ServerPlayer p, Vec3 at, ResourceLocation gun) {
        Gunfire g = new Gunfire();
        g.level = p.serverLevel();
        g.pos = at;
        g.end = p.server.getTickCount() + SpecialConfig.ticks(SpecialConfig.get().gunshotSeconds);
        g.next = p.server.getTickCount();
        g.gun = gun;
        // TACZ 的槍聲要綁在一個實體上播：放一個看不見的展示實體當音源
        net.minecraft.world.entity.Display.ItemDisplay anchor = new net.minecraft.world.entity.Display.ItemDisplay(
            net.minecraft.world.entity.EntityType.ITEM_DISPLAY, g.level);
        anchor.moveTo(at.x, at.y, at.z);
        anchor.addTag(ANCHOR_TAG);
        g.level.addFreshEntity(anchor);
        g.anchor = anchor;
        GUNFIRE.add(g);
    }

    private static final String ANCHOR_TAG = "pg_special_anchor";

    public static void noFallFor(ServerPlayer p, int ticks) {
        NO_FALL.put(p.getUUID(), p.server.getTickCount() + (long) ticks);
    }

    public static boolean noFall(ServerPlayer p) {
        Long until = NO_FALL.get(p.getUUID());
        return until != null && p.server.getTickCount() < until;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 每 tick
    // ═════════════════════════════════════════════════════════════════════════

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickBurns(server, now);
        tickBlind(now);
        tickSmoke(now);
        tickGas(server, now);
        tickSnow(server, now);
        tickGunfire(now);
        if (now % 20 == 0) {
            NO_FALL.values().removeIf(t -> t < now);
            for (ServerLevel l : server.getAllLevels()) expireSnow(l);
        }
    }

    private static void tickBurns(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<UUID, Burn>> it = BURNS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Burn> en = it.next();
            Burn b = en.getValue();
            Entity e = null;
            for (ServerLevel l : server.getAllLevels()) { e = l.getEntity(en.getKey()); if (e != null) break; }
            if (!(e instanceof LivingEntity le) || !le.isAlive() || b.ticks()[0]-- <= 0) { it.remove(); continue; }
            ServerLevel level = (ServerLevel) le.level();
            if (b.ticks()[0] % 4 == 0) level.sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + 1, le.getZ(), 3, 0.3, 0.5, 0.3, 0.01);
            if (b.ticks()[0] % 20 == 0) {
                ServerPlayer owner = b.owner() == null ? null : server.getPlayerList().getPlayer(b.owner());
                le.hurt(owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().onFire(), b.damage());
            }
        }
    }

    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.02f), 3f);
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 0.85f, 0.15f), 2.5f);

    private static void tickBlind(long now) {
        SpecialConfig s = SpecialConfig.get();
        double step = s.blindSpeed.get() / 20.0;
        int blind = SpecialConfig.ticks(s.blindSeconds);
        for (Iterator<Cloud> it = BLIND.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            c.pos = c.pos.add(c.dir.scale(step)); // 可穿透方塊：不做碰撞
            c.travelled += step;
            c.level.sendParticles(BLACK, c.pos.x, c.pos.y, c.pos.z, 14, 1.0, 1.0, 1.0, 0);
            for (Player p : c.level.getEntitiesOfClass(Player.class, new AABB(c.pos, c.pos).inflate(1.5))) {
                if (p.isSpectator() || p.getUUID().equals(c.owner)) continue; // 使用者本人不會被自己的黑霧致盲（隊友仍會）
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0, false, false, true));
            }
            if (c.travelled >= s.blindRange.get()) it.remove();
        }
    }

    @Nullable
    private static ParticleOptions smokeParticle() {
        var t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("lrtactical", "smoke_cloud"));
        return t instanceof SimpleParticleType st ? st : null;
    }

    private static void tickSmoke(long now) {
        if (SMOKES.isEmpty()) return;
        SMOKES.removeIf(a -> a.end() <= now);
        double r = SpecialConfig.get().smokeRadius.get();
        ParticleOptions lr = smokeParticle();
        for (Area a : SMOKES) {
            RandomSource rnd = a.level().getRandom();
            for (int i = 0; i < 4; i++) {
                double dx = (rnd.nextDouble() * 2 - 1) * r, dy = rnd.nextDouble() * r * 0.8, dz = (rnd.nextDouble() * 2 - 1) * r;
                if (dx * dx + dz * dz > r * r) continue;
                double x = a.center().x + dx, y = a.center().y + dy, z = a.center().z + dz;
                if (lr != null) {
                    for (ServerPlayer p : a.level().players()) a.level().sendParticles(p, lr, true, x, y, z, 1, 0, 0, 0, 0);
                } else {
                    a.level().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.2, 0.2, 0.2, 0.005);
                }
            }
        }
    }

    private static void tickGas(MinecraftServer server, long now) {
        if (GASES.isEmpty()) return;
        GASES.removeIf(a -> a.end() <= now);
        SpecialConfig s = SpecialConfig.get();
        int linger = Math.max(1, SpecialConfig.ticks(s.gasLingerSeconds));
        int poison = SpecialConfig.ticks(s.gasPoisonSeconds);
        for (Area a : GASES) {
            Vec3 c = a.center();
            if (now % 2 == 0) a.level().sendParticles(GREEN, c.x, c.y + 1.5, c.z, 10, 1.2, 1.2, 1.2, 0);
            AABB box = new AABB(c.x - 1.5, c.y - 0.5, c.z - 1.5, c.x + 1.5, c.y + 2.5, c.z + 1.5);
            ServerPlayer owner = a.owner() == null ? null : server.getPlayerList().getPlayer(a.owner());
            for (ServerPlayer p : a.level().getEntitiesOfClass(ServerPlayer.class, box)) {
                if (p.isSpectator() || (!s.friendlyFire.get() && owner != null && Engines.ally(owner, p))) continue; // 預設自己與隊友也會中毒氣
                p.addEffect(new MobEffectInstance(SpecialRegistry.GASSED.get(), linger, 0, false, false, true));
                MobEffectInstance cur = p.getEffect(MobEffects.POISON);
                if (cur == null || cur.getDuration() < poison - 20) p.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0, false, false, true));
            }
        }
    }

    private static void tickSnow(MinecraftServer server, long now) {
        SpecialConfig s = SpecialConfig.get();
        for (Iterator<SnowTrail> it = SNOW.iterator(); it.hasNext(); ) {
            SnowTrail t = it.next();
            if (t.ball.isRemoved()) { it.remove(); continue; }
            Vec3 pos = t.ball.position();
            if (Math.hypot(pos.x - t.start.x, pos.z - t.start.z) >= s.snowRange.get()) { t.ball.discard(); it.remove(); continue; }
            int x = (int) Math.floor(pos.x), z = (int) Math.floor(pos.z);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            if (key != t.lastCol) {
                t.lastCol = key;
                COLUMNS.add(new SnowColumn((ServerLevel) t.ball.level(), x, z, pos.y, now + 10)); // 0.5 秒後升起
            }
        }
        for (Iterator<SnowColumn> it = COLUMNS.iterator(); it.hasNext(); ) {
            SnowColumn c = it.next();
            if (c.at() > now) continue;
            it.remove();
            raiseColumn(c, s);
        }
    }

    private static void raiseColumn(SnowColumn c, SpecialConfig s) {
        ServerLevel level = c.level();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(c.x(), (int) Math.floor(c.ballY()), c.z());
        int ground = Integer.MIN_VALUE;
        for (int i = 0; i < 64 && m.getY() > level.getMinBuildHeight(); i++, m.move(0, -1, 0)) {
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) { ground = m.getY(); break; }
        }
        if (ground == Integer.MIN_VALUE) return;
        int h = (int) Math.ceil(c.ballY() - (ground + 1));
        h = Math.max(1, Math.min(s.snowHeight.get(), h)); // 雪球離地不超過最高高度時，依目前離地高度
        long expire = level.getGameTime() + SpecialConfig.ticks(s.snowSeconds);
        Ledger ledger = Ledger.get(level);
        for (int y = ground + 1; y <= ground + h; y++) {
            BlockPos p = new BlockPos(c.x(), y, c.z());
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && !st.canBeReplaced()) break;
            level.setBlockAndUpdate(p, Blocks.SNOW_BLOCK.defaultBlockState());
            ledger.snow.put(p.asLong(), expire);
        }
        ledger.setDirty();
        level.playSound(null, c.x() + 0.5, ground + 1, c.z() + 0.5, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    private static void expireSnow(ServerLevel level) {
        Ledger ledger = Ledger.getIfLoaded(level);
        if (ledger == null || ledger.snow.isEmpty()) return;
        long now = level.getGameTime();
        boolean changed = ledger.snow.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            BlockPos p = BlockPos.of(e.getKey());
            if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SNOW_BLOCK)) level.removeBlock(p, false);
            return true;
        });
        if (changed) ledger.setDirty();
    }

    private static void tickGunfire(long now) {
        for (Iterator<Gunfire> it = GUNFIRE.iterator(); it.hasNext(); ) {
            Gunfire g = it.next();
            if (now >= g.end) {
                if (g.anchor != null) g.anchor.discard();
                it.remove();
                continue;
            }
            if (now < g.next) continue;
            RandomSource r = g.level.getRandom();
            if (!GunCompat.playGunSound(g.anchor, g.gun)) {
                g.level.playSound(null, g.pos.x, g.pos.y, g.pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 3f, 0.6f + r.nextFloat() * 0.2f);
            }
            g.level.sendParticles(ParticleTypes.SMALL_FLAME, g.pos.x, g.pos.y, g.pos.z, 3, 0.1, 0.1, 0.1, 0.02);
            g.level.sendParticles(ParticleTypes.SMOKE, g.pos.x, g.pos.y, g.pos.z, 2, 0.1, 0.1, 0.1, 0.01);
            // 隨機連發節奏：連發 2～6 發（每 2～3 tick 一發），之後停 0.3～1 秒
            if (g.burst <= 0) g.burst = 2 + r.nextInt(5);
            g.burst--;
            g.next = now + (g.burst > 0 ? 2 + r.nextInt(2) : 6 + r.nextInt(15));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 假箱
    // ═════════════════════════════════════════════════════════════════════════

    public static void rememberFakeCrate(ServerLevel level, BlockPos pos) {
        Ledger l = Ledger.get(level);
        l.crates.add(pos.asLong());
        l.setDirty();
    }

    public static void forgetFakeCrate(ServerLevel level, BlockPos pos) {
        Ledger l = Ledger.get(level);
        if (l.crates.remove(pos.asLong())) l.setDirty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 清除（每場開始、結束、強制停止）
    // ═════════════════════════════════════════════════════════════════════════

    public static void cleanup(MinecraftServer server) {
        BURNS.clear();
        BLIND.clear();
        SMOKES.clear();
        GASES.clear();
        for (SnowTrail t : SNOW) t.ball.discard();
        SNOW.clear();
        COLUMNS.clear();
        for (Gunfire g : GUNFIRE) if (g.anchor != null) g.anchor.discard();
        GUNFIRE.clear();
        NO_FALL.clear();
        GlowManager.clearAll(server);
        KnockbackShield.restoreAll(server);
        SpecialUse.reset();
        for (ServerLevel level : server.getAllLevels()) {
            Ledger l = Ledger.getIfLoaded(level);
            if (l != null) {
                for (long key : l.snow.keySet()) {
                    BlockPos p = BlockPos.of(key);
                    if (level.getBlockState(p).is(Blocks.SNOW_BLOCK)) level.removeBlock(p, false);
                }
                for (long key : l.crates) {
                    BlockPos p = BlockPos.of(key);
                    if (level.getBlockEntity(p) instanceof CrateBlockEntity c && c.isFake()) level.removeBlock(p, false);
                }
                l.snow.clear();
                l.crates.clear();
                l.setDirty();
            }
            List<Entity> remove = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof MineEntity || e instanceof DecoyEntity || e instanceof SpecialProjectile || e.getTags().contains(ANCHOR_TAG)) remove.add(e);
            }
            remove.forEach(Entity::discard);
        }
        // 所有特殊物品效果重置
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (var eff : List.of(SpecialRegistry.CONCUSSION, SpecialRegistry.CLOAK, SpecialRegistry.ADRENALINE, SpecialRegistry.SILENT,
                    SpecialRegistry.SHIELD_BUBBLE, SpecialRegistry.BUBBLE_SPEED, SpecialRegistry.ZONE_SHELTER, SpecialRegistry.GASSED,
                    SpecialRegistry.TOTEM_HELD)) {
                p.removeEffect(eff.get());
            }
            Totem.remove(p);
        }
        ProvingGroundsMod.LOGGER.info("Special items: world cleaned up.");
    }

    /** 每位玩家身上的地雷數（超過上限時移除最舊的）。 */
    public static void limitMines(ServerLevel level, UUID owner, int max) {
        List<MineEntity> mine = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e instanceof MineEntity m && owner.equals(m.owner())) mine.add(m);
        mine.sort(Comparator.comparingInt(m -> -m.tickCount)); // 最舊的（tickCount 最大）排前面
        while (mine.size() >= max) mine.remove(0).discard();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 存檔：放到世界裡的雪塊、假箱
    // ═════════════════════════════════════════════════════════════════════════

    public static final class Ledger extends SavedData {
        private static final String NAME = "provinggrounds_special";
        final Map<Long, Long> snow = new HashMap<>();
        final Set<Long> crates = new HashSet<>();

        static Ledger get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Ledger::load, Ledger::new, NAME);
        }

        @Nullable
        static Ledger getIfLoaded(ServerLevel level) {
            return level.getDataStorage().get(Ledger::load, NAME);
        }

        static Ledger load(CompoundTag tag) {
            Ledger l = new Ledger();
            for (Tag t : tag.getList("Snow", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                l.snow.put(c.getLong("Pos"), c.getLong("Expire"));
            }
            for (long v : tag.getLongArray("Crates")) l.crates.add(v);
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            snow.forEach((pos, exp) -> {
                CompoundTag c = new CompoundTag();
                c.putLong("Pos", pos);
                c.putLong("Expire", exp);
                list.add(c);
            });
            tag.put("Snow", list);
            tag.putLongArray("Crates", crates.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }
    }
}
