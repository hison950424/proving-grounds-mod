package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.events.BulletHitEvents;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.Plan9SyncPacket;
import com.provinggrounds.network.SpecialFxPacket;
import com.provinggrounds.special.entity.SpecialProjectile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: Plan9Specials.java
 * 目的: 計畫書 9 新增的 17 種特殊道具（伺服器端邏輯與狀態同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class Plan9Specials {

    private static final double RADIUS_20 = 20.0;
    private static final int TICKS_15S = 15 * 20;
    private static final int TICKS_5S = 5 * 20;
    private static final int TICKS_60S = 60 * 20;

    private Plan9Specials() {}

    // ── 強制丟棄標記（「欸幹你手上有蟑螂」繞過 ArenaMode.handleToss） ──
    private static final Set<UUID> FORCED_DROP = new HashSet<>();
    public static boolean isForcedDrop(ServerPlayer p) { return FORCED_DROP.contains(p.getUUID()); }

    // ── 領域展開 ──
    private static final class Domain {
        ServerLevel level;
        Vec3 center;
        double radius;
        int leftTicks;
        final Set<UUID> inside = new HashSet<>();
        /** 沒收的物品：玩家 UUID → 沒收紀錄。 */
        final Map<UUID, SavedGear> confiscated = new HashMap<>();
    }
    private static final class SavedGear {
        final Map<Integer, ItemStack> main = new HashMap<>();
        ItemStack offhand = ItemStack.EMPTY;
        ItemStack special = ItemStack.EMPTY;
    }
    private static final List<Domain> DOMAINS = new ArrayList<>();

    // ── 123木頭人 ──
    private static final class RedLightGame {
        ServerLevel level;
        @Nullable UUID owner;
        final Set<UUID> targets = new HashSet<>();
        int age;
        final int[] triggerAt; // 兩次隨機開始倒數的 tick
        int checkingRound = -1;
        int checkAtTick = -1;
        final Map<UUID, Vec3> freezePos = new HashMap<>();

        RedLightGame(ServerLevel level, @Nullable UUID owner, Set<UUID> targets, int t1, int t2) {
            this.level = level;
            this.owner = owner;
            this.targets.addAll(targets);
            this.triggerAt = new int[] { t1, t2 };
        }
    }
    private static final List<RedLightGame> RED_LIGHTS = new ArrayList<>();

    // ── 過熱詛咒：記錄每位玩家拿著同一格快捷欄的持續時間 ──
    private static final class HoldTrack { int slot = -1; int ticks = 0; }
    private static final Map<UUID, HoldTrack> OVERHEAT_HOLD = new HashMap<>();

    // ── 擬態：記錄初始位置，移動即解除 ──
    private static final Map<UUID, Vec3> MIMIC_ORIGIN = new HashMap<>();

    // ── 無實體化：追蹤中狀態，結束時避免卡在方塊內 ──
    private static final Set<UUID> ETHEREAL_TRACK = new HashSet<>();

    // ── 同步節流 ──
    private static boolean syncDirty = true;
    private static int lastSyncHash = 0;

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    private static void sendTitle(ServerPlayer p, String title, ChatFormatting tc, String sub, ChatFormatting sc, int stay) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(2, stay, 5));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(sc)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(tc, ChatFormatting.BOLD)));
    }

    @Nullable
    private static ServerPlayer nearestEnemy(ServerPlayer p, double radius) {
        ServerPlayer best = null;
        double bestDist = radius * radius;
        for (ServerPlayer t : p.serverLevel().players()) {
            if (t == p || !t.isAlive() || t.isSpectator()) continue;
            if (TeamManager.get().isSameTeam(p, t)) continue;
            double d = t.distanceToSqr(p);
            if (d <= bestDist) { bestDist = d; best = t; }
        }
        return best;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 17 種特殊道具入口（SpecialUse 呼叫）
    // ═════════════════════════════════════════════════════════════════════════

    public static boolean use(ServerPlayer p, SpecialType type) {
        return switch (type) {
            case LOOK_UP_ONE -> lookUpOne(p);
            case LOOK_UP_ALL -> lookUpAll(p);
            case COCKROACH -> cockroach(p);
            case DOMAIN_EXPANSION -> domainExpansion(p);
            case FRIEND_OR_FOE -> friendOrFoe(p);
            case PARANOIA -> paranoia(p);
            case RED_LIGHT -> redLight(p);
            case MAGNET_GRENADE -> { SpecialProjectile.throwFrom(p, SpecialType.MAGNET_GRENADE, 1.35f); yield true; }
            case MISFIRE_CURSE -> misfireCurse(p);
            case CRUDE_SWAPPER -> crudeSwapper(p);
            case SPACE_SWAPPER -> spaceSwapper(p);
            case ICE_SKATES -> iceSkates(p, false);
            case ICE_SKATES_ALL -> iceSkates(p, true);
            case HUD_JAMMER -> hudJammer(p);
            case OVERHEAT_CURSE -> overheatCurse(p);
            case ETHEREAL -> ethereal(p);
            case MIMIC -> mimic(p);
            default -> false;
        };
    }

    // 1. 欸你看
    private static boolean lookUpOne(ServerPlayer p) {
        ServerPlayer target = nearestEnemy(p, RADIUS_20);
        if (target == null) { msg(p, "20 格內沒有敵人", ChatFormatting.RED); return false; }
        forceLookUp(target);
        msg(target, "欸你看！", ChatFormatting.YELLOW);
        p.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1.8f);
        return true;
    }

    // 2. 欸你們看
    private static boolean lookUpAll(ServerPlayer p) {
        for (ServerPlayer t : NewSpecials.playersAround(p.serverLevel(), p.position(), RADIUS_20)) {
            forceLookUp(t);
            msg(t, "欸你們看！", ChatFormatting.YELLOW);
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.2f, 1.6f);
        return true;
    }

    private static void forceLookUp(ServerPlayer t) {
        t.setXRot(-90f);
        PGNetwork.sendTo(t, SpecialFxPacket.of(SpecialFxPacket.FORCE_LOOK_UP, 0, 0));
    }

    // 3. 欸幹你手上有蟑螂
    private static boolean cockroach(ServerPlayer p) {
        ServerPlayer target = nearestEnemy(p, RADIUS_20);
        if (target == null) { msg(p, "20 格內沒有敵人", ChatFormatting.RED); return false; }
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) { msg(p, "最近的敵人手上沒有東西", ChatFormatting.RED); return false; }
        ItemStack copy = held.copy();
        target.getInventory().setItem(target.getInventory().selected, ItemStack.EMPTY);
        FORCED_DROP.add(target.getUUID());
        try {
            ItemEntity dropped = target.drop(copy, true, false);
            if (dropped != null) dropped.setPickUpDelay(35); // 1.75 秒後才撿得回來
        } finally {
            FORCED_DROP.remove(target.getUUID());
        }
        target.inventoryMenu.broadcastChanges();
        msg(target, "欸幹你手上有蟑螂！！你嚇到把東西丟掉了！", ChatFormatting.RED);
        msg(p, "已讓 " + target.getGameProfile().getName() + " 丟出手上物品", ChatFormatting.GREEN);
        p.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SILVERFISH_HURT, SoundSource.PLAYERS, 1.2f, 1.5f);
        return true;
    }

    // 4. 領域展開，但你沒抓到訣竅
    private static boolean domainExpansion(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Domain d = new Domain();
        d.level = level;
        d.center = p.position();
        d.radius = RADIUS_20;
        d.leftTicks = TICKS_15S;
        for (ServerPlayer t : NewSpecials.playersAround(level, d.center, d.radius)) {
            d.inside.add(t.getUUID());
            confiscateNonMelee(d, t);
            sendTitle(t, "領域展開", ChatFormatting.DARK_PURPLE, "但你沒抓到訣竅！15 秒內只能用刀", ChatFormatting.LIGHT_PURPLE, 40);
        }
        DOMAINS.add(d);
        syncDirty = true;
        level.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2f, 0.7f);
        return true;
    }

    private static void confiscateNonMelee(Domain d, ServerPlayer p) {
        SavedGear sg = new SavedGear();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack st = inv.items.get(i);
            if (!st.isEmpty() && !st.is(GunCompat.MELEE)) {
                sg.main.put(i, st.copy());
                inv.items.set(i, ItemStack.EMPTY);
            }
        }
        ItemStack off = inv.offhand.get(0);
        if (!off.isEmpty() && !off.is(GunCompat.MELEE)) {
            sg.offhand = off.copy();
            inv.offhand.set(0, ItemStack.EMPTY);
        }
        ItemStack sp = SpecialSlot.get(p);
        if (!sp.isEmpty() && !DomGear.isGearStack(sp)) {
            sg.special = sp.copy();
            SpecialSlot.clear(p);
        }
        d.confiscated.put(p.getUUID(), sg);
        p.inventoryMenu.broadcastChanges();
    }

    private static void restoreGear(Domain d, UUID id, @Nullable ServerPlayer p) {
        SavedGear sg = d.confiscated.remove(id);
        if (sg == null || p == null) return;
        Inventory inv = p.getInventory();
        for (Map.Entry<Integer, ItemStack> e : sg.main.entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot < inv.items.size() && inv.items.get(slot).isEmpty()) {
                inv.items.set(slot, e.getValue());
            } else if (!inv.add(e.getValue())) {
                p.drop(e.getValue(), false);
            }
        }
        if (!sg.offhand.isEmpty()) {
            if (inv.offhand.get(0).isEmpty()) inv.offhand.set(0, sg.offhand);
            else if (!inv.add(sg.offhand)) p.drop(sg.offhand, false);
        }
        if (!sg.special.isEmpty()) {
            if (SpecialSlot.get(p).isEmpty()) SpecialSlot.set(p, sg.special);
            else if (!inv.add(sg.special)) p.drop(sg.special, false);
        }
        p.inventoryMenu.broadcastChanges();
        msg(p, "領域解除，物品已歸還", ChatFormatting.AQUA);
    }

    /** 領域阻擋子彈進出（GunCompat 命中過濾呼叫）。 */
    public static boolean blocksBullet(Entity target, Entity bullet) {
        if (DOMAINS.isEmpty()) return false;
        Entity owner = bullet instanceof Projectile pr ? pr.getOwner() : null;
        for (Domain d : DOMAINS) {
            if (target.level() != d.level) continue;
            boolean targetIn = d.inside.contains(target.getUUID()) || target.position().distanceToSqr(d.center) <= d.radius * d.radius;
            boolean ownerIn = owner != null && (d.inside.contains(owner.getUUID()) || owner.position().distanceToSqr(d.center) <= d.radius * d.radius);
            if (targetIn != ownerIn) return true; // 一內一外：子彈進不去也出不來
        }
        return false;
    }

    // 5. 敵我不分
    private static boolean friendOrFoe(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            Engines.effect(t, SpecialRegistry.FRIEND_OR_FOE.get(), TICKS_15S, 0);
            msg(t, "敵我不分！15 秒內所有人變史蒂夫、隱藏隊友標記、開啟友軍誤傷！", ChatFormatting.LIGHT_PURPLE);
        }
        syncDirty = true;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.2f, 0.9f);
        return true;
    }

    // 6. 被害妄想症
    private static boolean paranoia(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            Engines.effect(t, SpecialRegistry.PARANOIA.get(), TICKS_15S, 0);
            msg(t, "被害妄想症發作！15 秒內出現幻覺受擊與假敵人訊號…", ChatFormatting.DARK_RED);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // 7. 123木頭人
    private static boolean redLight(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Set<UUID> targets = new HashSet<>();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            targets.add(t.getUUID());
            msg(t, "123 木頭人開始！15 秒內隨機倒數 3、2、1、0，數到 0 時動就會扣 25 血！", ChatFormatting.GOLD);
        }
        var r = level.getRandom();
        int t1 = 15 + r.nextInt(45);       // 第 1 次倒數在 0.75~3 秒間開始（+3秒到0）
        int t2 = 145 + r.nextInt(55);      // 第 2 次倒數在 7.25~10 秒間開始（+3秒到0，仍在 15 秒內）
        RED_LIGHTS.add(new RedLightGame(level, p.getUUID(), targets, t1, t2));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 1.0f);
        return true;
    }

    // 8. 磁人手榴彈（由 SpecialProjectile 觸發）
    public static void magnetBurst(ServerLevel level, Vec3 at) {
        double r = 5.0;
        for (ServerPlayer t : NewSpecials.playersAround(level, at, r)) {
            BulletHitEvents.allow(t);
            if (t.isPassenger()) t.stopRiding();
            t.teleportTo(at.x, at.y + 0.1, at.z);
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
            msg(t, "被磁人手榴彈吸到中心！", ChatFormatting.AQUA);
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.2f, 0.7f, 1.0f), 1.8f);
        level.sendParticles(dust, at.x, at.y + 0.5, at.z, 60, 2.5, 0.6, 2.5, 0.1);
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 1.5f);
    }

    // 9. 走火詛咒
    private static boolean misfireCurse(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            Engines.effect(t, SpecialRegistry.MISFIRE.get(), TICKS_15S, 0);
            msg(t, "中了走火詛咒！15 秒內拿著槍就會強制開火！", ChatFormatting.RED);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.3f);
        return true;
    }

    // 10. 簡陋的空間交換器
    private static boolean crudeSwapper(ServerPlayer p) {
        List<ServerPlayer> list = NewSpecials.playersAround(p.serverLevel(), p.position(), RADIUS_20);
        if (list.size() < 2) { msg(p, "20 格內不足兩位玩家，無法交換", ChatFormatting.RED); return false; }
        Collections.shuffle(list);
        ServerPlayer a = list.get(0), b = list.get(1);
        swapTwo(a, b);
        return true;
    }

    private static void swapTwo(ServerPlayer a, ServerPlayer b) {
        Vec3 pa = a.position(), pb = b.position();
        float ya = a.getYRot(), xa = a.getXRot(), yb = b.getYRot(), xb = b.getXRot();
        if (a.isPassenger()) a.stopRiding();
        if (b.isPassenger()) b.stopRiding();
        a.teleportTo(a.serverLevel(), pb.x, pb.y, pb.z, yb, xb);
        b.teleportTo(b.serverLevel(), pa.x, pa.y, pa.z, ya, xa);
        a.fallDistance = 0; b.fallDistance = 0;
        a.serverLevel().sendParticles(ParticleTypes.PORTAL, pa.x, pa.y + 1, pa.z, 30, 0.4, 0.8, 0.4, 0.1);
        a.serverLevel().sendParticles(ParticleTypes.PORTAL, pb.x, pb.y + 1, pb.z, 30, 0.4, 0.8, 0.4, 0.1);
        a.serverLevel().playSound(null, pb.x, pb.y, pb.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.1f);
        b.serverLevel().playSound(null, pa.x, pa.y, pa.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.1f);
        msg(a, "空間交換！你和 " + b.getGameProfile().getName() + " 互換了位置", ChatFormatting.AQUA);
        msg(b, "空間交換！你和 " + a.getGameProfile().getName() + " 互換了位置", ChatFormatting.AQUA);
    }

    // 11. 空間交換器
    private static boolean spaceSwapper(ServerPlayer p) {
        List<ServerPlayer> list = NewSpecials.playersAround(p.serverLevel(), p.position(), RADIUS_20);
        if (list.size() < 2) { msg(p, "20 格內不足兩位玩家，無法交換", ChatFormatting.RED); return false; }
        List<Vec3> positions = new ArrayList<>();
        List<float[]> rots = new ArrayList<>();
        for (ServerPlayer t : list) {
            positions.add(t.position());
            rots.add(new float[] { t.getYRot(), t.getXRot() });
        }
        // 隨機錯排（保證每個人都換到別人的位置）
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) order.add(i);
        Collections.shuffle(order);
        for (int i = 0; i < list.size(); i++) {
            ServerPlayer t = list.get(order.get(i));
            int dst = order.get((i + 1) % list.size());
            Vec3 pos = positions.get(dst);
            float[] rot = rots.get(dst);
            if (t.isPassenger()) t.stopRiding();
            t.teleportTo(t.serverLevel(), pos.x, pos.y, pos.z, rot[0], rot[1]);
            t.fallDistance = 0;
            t.serverLevel().sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 25, 0.4, 0.8, 0.4, 0.1);
            msg(t, "全體空間交換！你的位置已隨機調換", ChatFormatting.AQUA);
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.9f);
        return true;
    }

    // 12 & 13. 溜冰鞋
    private static boolean iceSkates(ServerPlayer p, boolean all) {
        ServerLevel level = p.serverLevel();
        List<ServerPlayer> targets = all ? NewSpecials.playersAround(level, p.position(), RADIUS_20) : List.of(p);
        for (ServerPlayer t : targets) {
            Engines.effect(t, SpecialRegistry.ICE_SKATES.get(), TICKS_15S, 0);
            msg(t, "穿上溜冰鞋！15 秒內放開方向鍵將一路滑行，撞到方塊才會停下", ChatFormatting.AQUA);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_STEP, SoundSource.PLAYERS, 1.0f, 1.5f);
        return true;
    }

    // 14. 顯示干擾器
    private static boolean hudJammer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            Engines.effect(t, SpecialRegistry.HUD_JAM.get(), TICKS_15S, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    // 15. 過熱詛咒
    private static boolean overheatCurse(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (ServerPlayer t : NewSpecials.playersAround(level, p.position(), RADIUS_20)) {
            Engines.effect(t, SpecialRegistry.OVERHEAT.get(), TICKS_15S, 0);
            OVERHEAT_HOLD.remove(t.getUUID());
            msg(t, "中了過熱詛咒！15 秒內持續拿同一格物品超過 3 秒會灼傷並震盪！", ChatFormatting.GOLD);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.8f);
        return true;
    }

    // 16. 無實體化
    private static boolean ethereal(ServerPlayer p) {
        Engines.effect(p, SpecialRegistry.ETHEREAL.get(), TICKS_5S, 0);
        ETHEREAL_TRACK.add(p.getUUID());
        syncDirty = true;
        msg(p, "無實體化！5 秒內無法攻擊也不受傷害，可穿過 1 格厚的方塊", ChatFormatting.AQUA);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VEX_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    // 17. 擬態
    private static boolean mimic(ServerPlayer p) {
        int kind = p.getRandom().nextInt(3); // 0 木箱、1 鐵箱、2 金箱
        Engines.effect(p, SpecialRegistry.MIMIC.get(), TICKS_60S, kind);
        MIMIC_ORIGIN.put(p.getUUID(), p.position());
        syncDirty = true;
        String name = kind == 0 ? "木箱（低階）" : kind == 1 ? "鐵箱（中階）" : "金箱（高階）";
        msg(p, "擬態為 " + name + "！移動或受到傷害會解除（最多 60 秒）", ChatFormatting.GOLD);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 每 tick 更新
    // ═════════════════════════════════════════════════════════════════════════

    public static void tick(MinecraftServer server) {
        tickDomains(server);
        tickRedLights(server);
        tickPlayerStates(server);
        syncClients(server);
    }

    private static void tickDomains(MinecraftServer server) {
        for (Iterator<Domain> it = DOMAINS.iterator(); it.hasNext(); ) {
            Domain d = it.next();
            d.leftTicks--;
            if (d.leftTicks <= 0) {
                for (UUID id : new ArrayList<>(d.confiscated.keySet())) {
                    restoreGear(d, id, server.getPlayerList().getPlayer(id));
                }
                d.level.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
                it.remove();
                syncDirty = true;
                continue;
            }
            // 邊界限制：裡面出不去、外面進不來
            for (ServerPlayer p : d.level.players()) {
                if (!p.isAlive() || p.isSpectator()) continue;
                Vec3 diff = p.position().subtract(d.center);
                double dist = diff.length();
                boolean in = d.inside.contains(p.getUUID());
                if (in && dist > d.radius - 0.7) {
                    Vec3 dir = dist < 1e-4 ? new Vec3(1, 0, 0) : diff.scale(1.0 / dist);
                    Vec3 clamp = d.center.add(dir.scale(d.radius - 0.85));
                    p.teleportTo(clamp.x, clamp.y, clamp.z);
                    p.setDeltaMovement(Vec3.ZERO);
                    p.hurtMarked = true;
                } else if (!in && dist < d.radius + 0.7) {
                    Vec3 dir = dist < 1e-4 ? new Vec3(1, 0, 0) : diff.scale(1.0 / dist);
                    Vec3 push = d.center.add(dir.scale(d.radius + 0.9));
                    p.teleportTo(push.x, push.y, push.z);
                    p.setDeltaMovement(Vec3.ZERO);
                    p.hurtMarked = true;
                }
            }
            // 攔截外部射進領域的子彈／投擲物
            AABB box = new AABB(d.center, d.center).inflate(d.radius + 1.0);
            for (Projectile pr : d.level.getEntitiesOfClass(Projectile.class, box)) {
                if (pr instanceof SpecialProjectile) continue;
                Entity owner = pr.getOwner();
                boolean ownerIn = owner != null && d.inside.contains(owner.getUUID());
                boolean projIn = pr.position().distanceToSqr(d.center) <= d.radius * d.radius;
                if (projIn != ownerIn) pr.discard();
            }
        }
    }

    private static void tickRedLights(MinecraftServer server) {
        for (Iterator<RedLightGame> it = RED_LIGHTS.iterator(); it.hasNext(); ) {
            RedLightGame g = it.next();
            g.age++;
            for (int r = 0; r < g.triggerAt.length; r++) {
                int rel = g.age - g.triggerAt[r];
                if (rel == 0 || rel == 20 || rel == 40) {
                    int num = 3 - rel / 20;
                    for (UUID id : g.targets) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p == null || !p.isAlive() || p.isSpectator()) continue;
                        sendTitle(p, String.valueOf(num), ChatFormatting.YELLOW, "123 木頭人！數到 0 時不能動", ChatFormatting.WHITE, 18);
                        p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1.0f, 1.0f + (3 - num) * 0.25f);
                    }
                } else if (rel == 60) {
                    g.checkingRound = r;
                    g.checkAtTick = g.age + 6; // 6 tick 檢查是否仍在移動
                    g.freezePos.clear();
                    for (UUID id : g.targets) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p == null || !p.isAlive() || p.isSpectator()) continue;
                        g.freezePos.put(id, p.position());
                        sendTitle(p, "0！別動！", ChatFormatting.RED, "移動就扣 25 HP", ChatFormatting.GOLD, 16);
                        p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 0.7f);
                    }
                }
            }
            if (g.checkAtTick > 0 && g.age == g.checkAtTick) {
                g.checkAtTick = -1;
                ServerPlayer owner = g.owner == null ? null : server.getPlayerList().getPlayer(g.owner);
                for (Map.Entry<UUID, Vec3> e : g.freezePos.entrySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                    if (p == null || !p.isAlive() || p.isSpectator()) continue;
                    if (p.position().distanceToSqr(e.getValue()) > 0.08 * 0.08) {
                        p.hurt(NewSpecials.sourceFor(g.level, owner, p), 25f);
                        sendTitle(p, "你動了！-25 HP", ChatFormatting.DARK_RED, "123 木頭人懲罰", ChatFormatting.RED, 25);
                        p.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.8f, 0.8f);
                    }
                }
                g.freezePos.clear();
            }
            if (g.age >= TICKS_15S + 20) it.remove();
        }
    }

    private static void tickPlayerStates(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            // 過熱詛咒
            if (p.hasEffect(SpecialRegistry.OVERHEAT.get()) && p.isAlive() && !p.isSpectator()) {
                HoldTrack tr = OVERHEAT_HOLD.computeIfAbsent(id, k -> new HoldTrack());
                int sel = p.getInventory().selected;
                ItemStack held = p.getMainHandItem();
                if (sel == tr.slot && !held.isEmpty()) {
                    tr.ticks++;
                    if (tr.ticks >= 60 && (tr.ticks - 60) % 20 == 0) {
                        p.hurt(p.serverLevel().damageSources().onFire(), 6f);
                        Engines.concuss(p, 40);
                        p.serverLevel().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.1, p.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);
                        msg(p, "過熱詛咒！手持同一格超過 3 秒，快切換快捷欄！", ChatFormatting.RED);
                    }
                } else {
                    tr.slot = sel;
                    tr.ticks = 0;
                }
            } else {
                OVERHEAT_HOLD.remove(id);
            }

            // 擬態：移動即解除
            if (p.hasEffect(SpecialRegistry.MIMIC.get())) {
                Vec3 orig = MIMIC_ORIGIN.get(id);
                if (orig != null && p.position().distanceToSqr(orig) > 0.04) {
                    p.removeEffect(SpecialRegistry.MIMIC.get());
                    MIMIC_ORIGIN.remove(id);
                    syncDirty = true;
                    msg(p, "移動了，擬態已解除", ChatFormatting.GRAY);
                }
            } else if (MIMIC_ORIGIN.remove(id) != null) {
                syncDirty = true;
            }

            // 無實體化結束：若卡在方塊內，推到最近的空位
            if (p.hasEffect(SpecialRegistry.ETHEREAL.get())) {
                ETHEREAL_TRACK.add(id);
                if (p.tickCount % 4 == 0) {
                    p.serverLevel().sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.25, 0.4, 0.25, 0.01);
                }
            } else if (ETHEREAL_TRACK.remove(id)) {
                syncDirty = true;
                unstuckIfSuffocating(p);
            }
        }
    }

    /** 無實體化結束時若剛好停在 1 格牆裡，移到旁邊最近的空方塊避免窒息或卡牆。 */
    private static void unstuckIfSuffocating(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        BlockPos bp = p.blockPosition();
        if (lv.getBlockState(bp).getCollisionShape(lv, bp).isEmpty()
                && lv.getBlockState(bp.above()).getCollisionShape(lv, bp.above()).isEmpty()) return;
        for (BlockPos cand : new BlockPos[] { bp.north(), bp.south(), bp.east(), bp.west(), bp.above() }) {
            if (lv.getBlockState(cand).getCollisionShape(lv, cand).isEmpty()
                    && lv.getBlockState(cand.above()).getCollisionShape(lv, cand.above()).isEmpty()) {
                p.teleportTo(cand.getX() + 0.5, cand.getY(), cand.getZ() + 0.5);
                return;
            }
        }
    }

    private static void syncClients(MinecraftServer server) {
        List<UUID> disguised = new ArrayList<>();
        List<UUID> ethereal = new ArrayList<>();
        Map<UUID, Integer> mimics = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get())) disguised.add(p.getUUID());
            if (p.hasEffect(SpecialRegistry.ETHEREAL.get())) ethereal.add(p.getUUID());
            MobEffectInstance m = p.getEffect(SpecialRegistry.MIMIC.get());
            if (m != null) mimics.put(p.getUUID(), m.getAmplifier());
        }
        List<Plan9SyncPacket.DomainInfo> doms = new ArrayList<>();
        for (Domain d : DOMAINS) doms.add(new Plan9SyncPacket.DomainInfo(d.center.x, d.center.y, d.center.z, d.radius, d.leftTicks));
        int hash = Objects.hash(disguised, ethereal, mimics, doms.size());
        if (syncDirty || hash != lastSyncHash || (!doms.isEmpty() && server.getTickCount() % 20 == 0)) {
            syncDirty = false;
            lastSyncHash = hash;
            PGNetwork.sendToAll(new Plan9SyncPacket(disguised, ethereal, mimics, doms));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Forge 事件：無實體化無敵、擬態受傷解除、領域無法破壞、斷線／死亡歸還裝備
    // ═════════════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.hasEffect(SpecialRegistry.ETHEREAL.get()) && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) && !event.getSource().is(DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true); // 無實體化不受傷害
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0) return;
        if (p.hasEffect(SpecialRegistry.MIMIC.get())) {
            p.removeEffect(SpecialRegistry.MIMIC.get());
            MIMIC_ORIGIN.remove(p.getUUID());
            syncDirty = true;
            msg(p, "受到傷害，擬態已解除！", ChatFormatting.RED);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (DOMAINS.isEmpty() || !(event.getLevel() instanceof ServerLevel lv)) return;
        Vec3 pos = Vec3.atCenterOf(event.getPos());
        for (Domain d : DOMAINS) {
            if (d.level == lv && pos.distanceToSqr(d.center) <= (d.radius + 1.5) * (d.radius + 1.5)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (DOMAINS.isEmpty() || !(event.getLevel() instanceof ServerLevel lv)) return;
        event.getAffectedBlocks().removeIf(bp -> {
            Vec3 pos = Vec3.atCenterOf(bp);
            for (Domain d : DOMAINS) {
                if (d.level == lv && pos.distanceToSqr(d.center) <= (d.radius + 1.5) * (d.radius + 1.5)) return true;
            }
            return false;
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (Domain d : DOMAINS) restoreGear(d, p.getUUID(), p);
        MIMIC_ORIGIN.remove(p.getUUID());
        ETHEREAL_TRACK.remove(p.getUUID());
        OVERHEAT_HOLD.remove(p.getUUID());
        syncDirty = true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (Domain d : DOMAINS) restoreGear(d, p.getUUID(), p);
        MIMIC_ORIGIN.remove(p.getUUID());
        ETHEREAL_TRACK.remove(p.getUUID());
        OVERHEAT_HOLD.remove(p.getUUID());
        syncDirty = true;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncDirty = true;
    }

    public static void cleanup(MinecraftServer server) {
        for (Domain d : DOMAINS) {
            for (UUID id : new ArrayList<>(d.confiscated.keySet())) {
                restoreGear(d, id, server.getPlayerList().getPlayer(id));
            }
        }
        DOMAINS.clear();
        RED_LIGHTS.clear();
        OVERHEAT_HOLD.clear();
        MIMIC_ORIGIN.clear();
        ETHEREAL_TRACK.clear();
        FORCED_DROP.clear();
        syncDirty = true;
        PGNetwork.sendToAll(new Plan9SyncPacket(List.of(), List.of(), Map.of(), List.of()));
    }
}
