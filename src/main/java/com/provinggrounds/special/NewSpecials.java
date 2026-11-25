package com.provinggrounds.special;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.HotbarRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: NewSpecials.java
 * 目的: 計畫書 6 E 章的 12 個特殊物品（伺服器端）。SpecialUse 的 switch 把這些物品交給這裡。
 *   「所有玩家」都包括使用者自己與隊友。數值寫在下面的常數（計畫書有寫的照計畫書，沒寫的標「占位」）。
 *
 *   E1 洗腦工具       ：半徑 25 格所有玩家「洗腦」效果 15 秒（不能開物品欄、不能按 F、不能切換快捷欄；客戶端 ClientControlLock 擋輸入）。
 *   E2 定身球         ：SpecialProjectile 落地 → 半徑 2.5 格所有玩家「定身」3 秒（不能移動、跳；可轉視角）。
 *   E3 火焰鑽頭       ：SpecialProjectile，不受重力、10 秒沒碰到方塊就消失；黏在方塊上 7 秒，方塊後方扇形範圍每秒灼燒（每秒 5、持續 3 秒）。
 *   E4／E6 回溯點     ：第一次使用記錄位置（加強版另外記血量），不消耗；60 秒內再用一次回去並消耗；60 秒沒用自動消失（物品也消失）。
 *   E5／E7 賭徒釣竿   ：四種結果各 25%（掉特殊物品＋最大生命，或受傷＋最大生命下降＋震盪）。最大生命的增減一直到這場結束。
 *   E8／E9 雷神錘     ：準心地點水平 5 格內隨機落雷（每 0.2 秒、5 秒）／所有玩家被指定落雷（每 0.5 秒、5 秒）。
 *   E10～E12 重新跳傘 ：BattleRoyaleMode.reSkydive，只在大逃殺進行中有效。
 */
public final class NewSpecials {

    private NewSpecials() {}

    // ── 數值 ─────────────────────────────────────────────────────────────────
    static final double BRAINWASH_RADIUS = 25;
    public static final int BRAINWASH_TICKS = 15 * 20;
    public static final double ROOT_RADIUS = 2.5;
    public static final int ROOT_TICKS = 3 * 20;
    public static final int DRILL_FLY_TICKS = 10 * 20;
    public static final int DRILL_STUCK_TICKS = 7 * 20;
    public static final double DRILL_DEPTH = 4.5;       // 從撞到的方塊表面往後算（含方塊本身那 1 格，後面約 4 格）
    public static final double DRILL_HALF_WIDTH = 2.0;  // 扇形最寬處的一半（寬 4 格）
    public static final float DRILL_BURN_DPS = 5f;
    public static final int DRILL_BURN_TICKS = 3 * 20;
    static final int RECALL_TICKS = 60 * 20;
    static final double AIM_RANGE = 100;                // 占位：雷神錘、跳傘指定器的準心最遠距離
    static final double THOR_RADIUS = 20;              // 計畫書 7 C2：5 → 20
    static final float THOR_DAMAGE = 8f;                // 占位：雷神錘每道雷的傷害
    static final double THOR_HIT_RADIUS = 2;            // 占位：雷神錘每道雷的命中半徑
    static final float THOR_PLUS_DAMAGE = 6f;           // 占位：雷神錘加強版每道雷的傷害
    static final int THOR_TICKS = 5 * 20;
    static final double SKYDIVE_RADIUS = 5;            // 計畫書 7 C8：3 → 5
    private static final UUID GAMBLE_MOD = UUID.fromString("b3e7c2a1-4f6d-4e88-9a1b-6c0d2e5f7a93");

    // ── 狀態 ─────────────────────────────────────────────────────────────────
    private record Recall(SpecialType type, ResourceKey<Level> dim, Vec3 pos, float health, long until) {}
    private static final Map<UUID, Recall> RECALLS = new HashMap<>();
    private static final Map<UUID, Double> GAMBLE = new HashMap<>();

    private static final class Storm {
        ServerLevel level; Vec3 center; @Nullable UUID owner; boolean plus; int age;
    }
    private static final List<Storm> STORMS = new ArrayList<>();

    private static final DustParticleOptions RECALL_DUST = new DustParticleOptions(new Vector3f(0.4f, 0.9f, 1f), 1.5f);

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    /** 同隊（含自己）用一般魔法傷害（隊伍關閉友傷時也一定會受傷）；敵人算在使用者頭上（擊殺紀錄正確）。 */
    public static DamageSource sourceFor(ServerLevel level, @Nullable ServerPlayer owner, ServerPlayer victim) {
        if (owner == null || Engines.ally(owner, victim)) return level.damageSources().magic();
        return level.damageSources().playerAttack(owner);
    }

    /** 範圍內（球形）的所有玩家，含自己；不含觀戰者。 */
    public static List<ServerPlayer> playersAround(ServerLevel level, Vec3 c, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer t : level.players()) {
            if (t.isAlive() && !t.isSpectator() && t.position().distanceTo(c) <= r) out.add(t);
        }
        return out;
    }

    /** 範圍內（水平圓，上下 maxDy 格內）的所有玩家，含自己。 */
    private static List<ServerPlayer> playersHoriz(ServerLevel level, Vec3 c, double r, double maxDy) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer t : level.players()) {
            if (!t.isAlive() || t.isSpectator()) continue;
            double dx = t.getX() - c.x, dz = t.getZ() - c.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(t.getY() - c.y) <= maxDy) out.add(t);
        }
        return out;
    }

    /** 準心指的地點（打到方塊就是那一點，沒打到就是最遠處往下找地面）。 */
    private static Vec3 aimPoint(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(AIM_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.BLOCK) return hit.getLocation();
        return new Vec3(end.x, groundY(level, end.x, end.y, end.z), end.z);
    }

    /** 從 (x, y + 6, z) 往下找第一個實心方塊的上緣；往下 24 格都沒有就用地表高度。 */
    private static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = BlockPos.containing(x, y + 6, z).mutable();
        for (int i = 0; i < 30 && m.getY() > level.getMinBuildHeight(); i++, m.move(0, -1, 0)) {
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) return m.getY() + 1.0;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E1 洗腦工具
    // ═════════════════════════════════════════════════════════════════════════

    public static void brainwash(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : playersAround(level, p.position(), BRAINWASH_RADIUS)) {
            Engines.effect(t, SpecialRegistry.BRAINWASH.get(), BRAINWASH_TICKS, 0);
            if (t != p) msg(t, "你被洗腦了！15 秒內不能開物品欄、互動、切換快捷欄", ChatFormatting.LIGHT_PURPLE);
        }
        level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 80, 4, 1, 4, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E2 定身球（落地時 SpecialProjectile 呼叫）
    // ═════════════════════════════════════════════════════════════════════════

    public static void rootBurst(ServerLevel level, Vec3 at) {
        for (ServerPlayer t : playersAround(level, at, ROOT_RADIUS)) {
            Engines.effect(t, SpecialRegistry.ROOTED.get(), ROOT_TICKS, 0);
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y + 0.3, at.z, 40, ROOT_RADIUS * 0.5, 0.2, ROOT_RADIUS * 0.5, 0.05);
        for (int i = 0; i < 48; i++) { // 地上畫一圈範圍
            double a = i * Math.PI * 2 / 48;
            level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * ROOT_RADIUS, at.y + 0.1, at.z + Math.sin(a) * ROOT_RADIUS, 1, 0, 0, 0, 0);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E3 火焰鑽頭（黏住之後每秒 SpecialProjectile 呼叫）
    // ═════════════════════════════════════════════════════════════════════════

    /** 方塊後方的扇形（越往後越寬，最寬 4 格；深度 DRILL_DEPTH）。dir = 鑽頭飛來的方向。 */
    public static void drillBurn(ServerLevel level, Vec3 surface, Vec3 dir, @Nullable ServerPlayer owner) {
        for (ServerPlayer t : level.players()) {
            if (!t.isAlive() || t.isSpectator()) continue;
            Vec3 v = t.getBoundingBox().getCenter().subtract(surface);
            double along = v.dot(dir);
            if (along < 0 || along > DRILL_DEPTH) continue;
            double perp = v.subtract(dir.scale(along)).length();
            double width = Math.min(DRILL_HALF_WIDTH, 0.6 + along * 0.45) + 0.4; // 0.4 = 身體寬度
            if (perp > width) continue;
            SpecialWorld.burn(t, owner == null || Engines.ally(owner, t) ? null : owner, DRILL_BURN_TICKS, DRILL_BURN_DPS);
        }
    }

    /** 鑽頭黏住時的火焰粒子：從方塊後面往後噴。 */
    public static void drillParticles(ServerLevel level, Vec3 surface, Vec3 dir) {
        var r = level.getRandom();
        for (int i = 0; i < 4; i++) {
            double d = 1 + r.nextDouble() * (DRILL_DEPTH - 1);
            double w = Math.min(DRILL_HALF_WIDTH, 0.6 + d * 0.45);
            Vec3 jitter = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(w * 0.4);
            Vec3 at = surface.add(dir.scale(d)).add(jitter);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, dir.x, dir.y, dir.z, 0.08);
        }
        level.sendParticles(ParticleTypes.LAVA, surface.x, surface.y, surface.z, 1, 0.1, 0.1, 0.1, 0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E4／E6 回溯點
    // ═════════════════════════════════════════════════════════════════════════

    /** 回傳 0 = 這次是「記錄」（物品不消耗）；1 = 已經回去了（物品消耗）；-1 = 不能用（不消耗）。 */
    public static int recall(ServerPlayer p, SpecialType type) {
        long now = p.server.getTickCount();
        Recall r = RECALLS.get(p.getUUID());
        if (r == null || r.until() < now) {
            RECALLS.put(p.getUUID(), new Recall(type, p.level().dimension(), p.position(), p.getHealth(), now + RECALL_TICKS));
            msg(p, "已記錄回溯點：60 秒內再按一次使用鍵回到這裡", ChatFormatting.AQUA);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.6f);
            return 0;
        }
        if (BattleRoyaleMode.get().isDowned(p.getUUID())) { msg(p, "倒地時不能回溯", ChatFormatting.RED); return -1; }
        ServerLevel to = p.server.getLevel(r.dim());
        if (to == null || to != p.level()) { RECALLS.remove(p.getUUID()); msg(p, "回溯點在別的世界，已取消", ChatFormatting.RED); return 1; }
        RECALLS.remove(p.getUUID());
        if (p.isPassenger()) p.stopRiding();
        to.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        p.teleportTo(r.pos().x, r.pos().y, r.pos().z);
        p.fallDistance = 0;
        if (r.type() == SpecialType.RECALL_PLUS) p.setHealth(Math.min(p.getMaxHealth(), Math.max(1f, r.health())));
        to.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.3f);
        return 1;
    }

    private static void tickRecalls(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<UUID, Recall>> it = RECALLS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Recall> en = it.next();
            Recall r = en.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) { it.remove(); continue; }
            if (r.until() < now) {
                it.remove();
                SpecialType held = SpecialItem.typeOf(SpecialSlot.get(p));
                if (held == SpecialType.RECALL || held == SpecialType.RECALL_PLUS) SpecialSlot.clear(p);
                msg(p, "回溯點已消失", ChatFormatting.GRAY);
                continue;
            }
            if (now % 5 == 0 && p.level().dimension() == r.dim()) { // 位置的特效（所有人都看得到，計畫書 7 C3）
                ServerLevel l = p.serverLevel();
                Vec3 c = r.pos();
                l.sendParticles(RECALL_DUST, c.x, c.y + 0.1, c.z, 6, 0.3, 0.02, 0.3, 0);
                l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.2 + (now % 40) / 20.0, c.z, 1, 0.05, 0.05, 0.05, 0);
            }
            if (now % 20 == 0) {
                long left = (r.until() - now) / 20;
                msg(p, "回溯點：剩 " + left + " 秒（按使用鍵回去）", ChatFormatting.AQUA);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E5／E7 賭徒釣竿
    // ═════════════════════════════════════════════════════════════════════════

    public static void gamble(ServerPlayer p, boolean hopeless) {
        ServerLevel level = p.serverLevel();
        int roll = level.getRandom().nextInt(4);
        int k = hopeless ? 2 : 1; // 沒救的版本：數值都兩倍
        switch (roll) {
            case 0, 1 -> {
                int items = (roll == 0 ? 1 : 2) * k;
                double hp = (roll == 0 ? 5 : 10) * k;
                for (int i = 0; i < items; i++) {
                    ItemStack s = SpecialLoot.roll(level);
                    if (!s.isEmpty()) HotbarRules.dropAtFeet(p, s);
                }
                changeMaxHealth(p, hp);
                p.heal((float) hp);
                msg(p, "賭贏了！掉出 " + items + " 個特殊物品，最大生命 +" + (int) hp, ChatFormatting.GOLD);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.6, 0.5, 0);
            }
            default -> {
                float dmg = (roll == 2 ? 25 : 75) * k;
                double hp = (roll == 2 ? 5 : 10) * k;
                changeMaxHealth(p, -hp);
                msg(p, "賭輸了！受到 " + (int) dmg + " 傷害，最大生命 -" + (int) hp, ChatFormatting.DARK_RED);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1f, 0.8f);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 8, 0.5, 0.6, 0.5, 0);
                Engines.concuss(p, 5 * 20);
                p.hurt(level.damageSources().magic(), dmg);
            }
        }
    }

    /** 最大生命增減（這場結束、或祭壇復活時清掉）。最大生命最少留 2。 */
    private static void changeMaxHealth(ServerPlayer p, double delta) {
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        double total = GAMBLE.getOrDefault(p.getUUID(), 0.0) + delta;
        attr.removeModifier(GAMBLE_MOD);
        double without = attr.getValue();
        if (without + total < 2) total = 2 - without;
        GAMBLE.put(p.getUUID(), total);
        if (total != 0) attr.addTransientModifier(new AttributeModifier(GAMBLE_MOD, "pg_gamble_health", total, AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** 移除賭徒釣竿的最大生命增減（祭壇復活、這場結束）。 */
    public static void clearGamble(ServerPlayer p) {
        GAMBLE.remove(p.getUUID());
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(GAMBLE_MOD);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E8／E9 雷神錘
    // ═════════════════════════════════════════════════════════════════════════

    public static void thor(ServerPlayer p, boolean plus) {
        Storm s = new Storm();
        s.level = p.serverLevel();
        s.center = aimPoint(p);
        s.owner = p.getUUID();
        s.plus = plus;
        STORMS.add(s);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void tickStorms(MinecraftServer server) {
        for (Iterator<Storm> it = STORMS.iterator(); it.hasNext(); ) {
            Storm s = it.next();
            if (s.age >= THOR_TICKS) { it.remove(); continue; }
            ServerPlayer owner = s.owner == null ? null : server.getPlayerList().getPlayer(s.owner);
            if (!s.plus && s.age % 4 == 0) { // 每 0.2 秒一道，隨機落在水平 5 格內
                var r = s.level.getRandom();
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * THOR_RADIUS;
                double x = s.center.x + Math.cos(a) * d, z = s.center.z + Math.sin(a) * d;
                Vec3 at = new Vec3(x, groundY(s.level, x, s.center.y, z), z);
                strike(s.level, at, owner);
                for (ServerPlayer t : playersHoriz(s.level, at, THOR_HIT_RADIUS, 3)) t.hurt(sourceFor(s.level, owner, t), THOR_DAMAGE);
            } else if (s.plus && s.age % 10 == 0) { // 每 0.5 秒：範圍內每個玩家各被一道雷打中
                for (ServerPlayer t : playersHoriz(s.level, s.center, THOR_RADIUS, 12)) {
                    strike(s.level, t.position(), owner);
                    t.hurt(sourceFor(s.level, owner, t), THOR_PLUS_DAMAGE);
                }
            }
            if (s.age % 10 == 0) { // 範圍邊框
                for (int i = 0; i < 120; i++) {
                    double a = i * Math.PI * 2 / 120;
                    double x = s.center.x + Math.cos(a) * THOR_RADIUS, z = s.center.z + Math.sin(a) * THOR_RADIUS;
                    s.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, s.center.y + 0.2, z, 1, 0, 0.1, 0, 0);
                }
            }
            s.age++;
        }
    }

    /** 只有外觀與聲音的閃電（不點火、不用原版傷害；傷害另外算）。 */
    private static void strike(ServerLevel level, Vec3 at, @Nullable ServerPlayer owner) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        if (owner != null) bolt.setCause(owner);
        level.addFreshEntity(bolt);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E10～E12 重新跳傘
    // ═════════════════════════════════════════════════════════════════════════

    /** 回傳 false = 沒有人能重新跳傘（物品不消耗，已提示原因）。 */
    public static boolean reSkydive(ServerPlayer p, SpecialType type) {
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (!br.isInPlay()) { msg(p, "只有大逃殺進行中才能使用", ChatFormatting.RED); return false; }
        ServerLevel level = p.serverLevel();
        Vec3 me = p.position();
        int n = 0;
        switch (type) {
            case RESKYDIVE -> { if (br.reSkydive(p, me.x, me.y, me.z)) n++; }
            case RESKYDIVE_GROUP -> {
                for (ServerPlayer t : playersAround(level, me, SKYDIVE_RADIUS)) { // 保持彼此的水平相對位置，不會全部疊在一起
                    if (br.reSkydive(t, t.getX(), me.y, t.getZ())) n++;
                }
            }
            case RESKYDIVE_TARGET -> {
                Vec3 at = aimPoint(p);
                for (ServerPlayer t : playersHoriz(level, at, SKYDIVE_RADIUS, 4)) {
                    if (br.reSkydive(t, me.x + (t.getX() - at.x), me.y, me.z + (t.getZ() - at.z))) n++;
                }
            }
            default -> {}
        }
        if (n == 0) {
            msg(p, type == SpecialType.RESKYDIVE ? "現在不能重新跳傘（要在地面上、沒有倒地）" : "範圍內沒有可以重新跳傘的玩家", ChatFormatting.RED);
            return false;
        }
        level.playSound(null, me.x, me.y, me.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 0.7f);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 每 tick／清除
    // ═════════════════════════════════════════════════════════════════════════

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickRecalls(server, now);
        tickStorms(server);
    }

    public static void cleanup(MinecraftServer server) {
        RECALLS.clear();
        STORMS.clear();
        for (UUID id : new ArrayList<>(GAMBLE.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) clearGamble(p);
        }
        GAMBLE.clear();
    }
}
