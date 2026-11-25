package com.provinggrounds.special;

import com.provinggrounds.block.CrateBlockEntity;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SpecialFxPacket;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.entity.DecoyEntity;
import com.provinggrounds.special.entity.MineEntity;
import com.provinggrounds.special.entity.SpecialProjectile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: SpecialUse.java
 * 目的: 按下「使用特殊物品」鍵（預設 Q）時，伺服器端的處理（計畫書 4 的 B2）：
 *   1. 擊退盾穿戴中 → 衝撞；傳送珠飛行中 → 立刻傳送（「再次按使用鍵」）。
 *   2. 否則使用特殊物品欄那一格：驗證持有、動作鎖定（ActionLock）、冷卻，成功後物品銷毀。
 *   每個物品只是把參數交給共用引擎（GlowManager、Engines、SpecialWorld、MineEntity…）。
 *   用不了（例如沒有敵人可以偵測）時不消耗，並提示原因。
 */
public final class SpecialUse {

    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    private static final Map<UUID, SpecialProjectile> PEARLS = new HashMap<>();

    private SpecialUse() {}

    public static void reset() {
        LAST_USE.clear();
        for (SpecialProjectile e : PEARLS.values()) {
            if (e.getOwner() instanceof ServerPlayer p) PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.PEARL, 0, 0));
        }
        PEARLS.clear();
    }

    public static void forgetPearl(SpecialProjectile e) {
        PEARLS.values().removeIf(v -> v == e);
        if (e.getOwner() instanceof ServerPlayer p) PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.PEARL, 0, 0)); // 快捷欄提示收起
    }

    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }

    /** 可以使用的時機：對局外（大廳）隨時可以用，方便測試；對局中只有存活的參賽者能用（觀戰者不行）。 */
    private static boolean canUseNow(ServerPlayer p) {
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (p.isSpectator()) return false;
        if (!br.isInPlay()) return true;
        return br.isAliveParticipant(p.getUUID()) || (p.isCreative() && p.hasPermissions(2));
    }

    public static void onKey(ServerPlayer p) {
        boolean dom = DomGear.active(p); // 佔點的特殊物品裝備（計畫書 8）：不受大廳開關影響、用完進冷卻
        if (!SpecialConfig.enabledSafe() && !dom) { msg(p, "特殊物品模式已關閉"); return; }
        if (!canUseNow(p)) { msg(p, "觀戰中不能使用特殊物品"); return; }
        if (KnockbackShield.active(p)) {
            if (ActionLock.blocksSpecial(p)) { msg(p, "現在不能使用"); return; }
            KnockbackShield.charge(p);
            return;
        }
        SpecialProjectile pearl = PEARLS.get(p.getUUID());
        if (pearl != null && !pearl.isRemoved()) { pearl.warpBelow(); return; }
        PEARLS.remove(p.getUUID());

        ItemStack stack = SpecialSlot.get(p);
        SpecialType type = SpecialItem.typeOf(stack);
        if (type == null) { msg(p, "特殊物品欄是空的"); return; }
        if (ActionLock.blocksSpecial(p)) { msg(p, ActionLock.cloaked(p) ? "隱形中不能使用任何物品" : "現在不能使用特殊物品"); return; }
        String why = DomGear.blockReason(p, stack);
        if (why != null) { msg(p, why); return; }
        long now = p.server.getTickCount();
        Long last = LAST_USE.get(p.getUUID());
        if (last != null && now - last < SpecialConfig.get().useCooldownTicks.get()) return;
        keepItem = false;
        if (!use(p, type)) return;
        LAST_USE.put(p.getUUID(), now);
        if (DomGear.isGearStack(stack)) { // 佔點：裝備進冷卻、額外的扣次數（物品格的內容由 DomGear 換）
            DomGear.afterUse(p, stack, keepItem);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
            return;
        }
        if (type.twoUses()) { // 計畫書 7 C11：可以用 2 次，次數存在物品 NBT
            int left = SpecialItem.usesLeft(stack) - 1;
            if (left > 0) {
                ItemStack rest = stack.copy();
                rest.getOrCreateTag().putInt(SpecialItem.USES_TAG, left);
                SpecialSlot.container(p).setItem(0, rest);
                keepItem = true;
            }
        }
        if (!keepItem && (type != SpecialType.FIRST_AID_SHOT || !BattleRoyaleMode.get().isSelfReviving(p.getUUID()))) {
            SpecialSlot.clear(p); // 使用後銷毀（急救針自救要讀條完成才消耗）
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** 這次使用後物品留著（回溯點第一次使用只記錄位置）。 */
    private static boolean keepItem;

    /** 使用一個特殊物品。回傳 false = 沒有用掉（已提示原因）。 */
    private static boolean use(ServerPlayer p, SpecialType type) {
        SpecialConfig s = SpecialConfig.get();
        ServerLevel level = p.serverLevel();
        double glow = s.glowSeconds.get();
        switch (type) {
            case BLIND_ORB -> SpecialWorld.addBlindCloud(p);
            case ADRENALINE -> { // 強心劑（計畫書 7 C10）：自己受 10 傷害（至少留 1 血）、加速
                float dmg = (float) Math.min(s.stimSelfDamage.get(), p.getHealth() + p.getAbsorptionAmount() - 1);
                if (dmg > 0) { p.hurt(level.damageSources().magic(), dmg); p.hurtMarked = false; } // 不會被這次傷害擊退（使用者回饋）
                Engines.effect(p, SpecialRegistry.ADRENALINE.get(), SpecialConfig.ticks(s.adrenalineSeconds), 0);
            }
            case INVIS_CLOAK -> {
                int t = SpecialConfig.ticks(s.cloakSeconds);
                p.stopUsingItem();
                p.closeContainer();
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, t, 0, false, false, true));
                Engines.effect(p, SpecialRegistry.CLOAK.get(), t, 0);
            }
            case SPRING_SHOES -> {
                Vec3 look = p.getLookAngle();
                double power = s.springPower.get();
                p.setDeltaMovement(look.x * power, Math.max(0.3, look.y * power + 0.3), look.z * power);
                com.provinggrounds.events.BulletHitEvents.allow(p); // 彈簧鞋：自己的移動
                p.hurtMarked = true;
                p.fallDistance = 0;
                SpecialWorld.noFallFor(p, 100);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1f, 1.2f);
            }
            case ENEMY_SCANNER_BASIC, ENEMY_SCANNER -> { // 6 號依使用者定義：只有使用者本人看得到（與 5 號效果相同，見回報）
                ServerPlayer t = GlowManager.randomEnemy(p);
                if (t == null) { msg(p, "沒有可以偵測的敵人"); return false; }
                GlowManager.add(t, GlowManager.Audience.ONE, p, true, glow);
                scanSound(p);
            }
            case ENEMY_SCANNER_PLUS -> {
                List<ServerPlayer> team = GlowManager.randomEnemyTeam(p);
                if (team.isEmpty()) { msg(p, "沒有可以偵測的敵人"); return false; }
                for (ServerPlayer t : team) GlowManager.add(t, GlowManager.Audience.TEAM, p, true, glow);
                scanSound(p);
            }
            case BLAST_SCANNER_BASIC, BLAST_SCANNER -> {
                ServerPlayer t = GlowManager.randomEnemy(p);
                if (t == null) { msg(p, "沒有可以偵測的敵人"); return false; }
                GlowManager.add(t, GlowManager.Audience.ALL, p, type == SpecialType.BLAST_SCANNER, glow);
                scanSound(p);
            }
            case BLAST_SCANNER_PLUS -> {
                List<ServerPlayer> team = GlowManager.randomEnemyTeam(p);
                if (team.isEmpty()) { msg(p, "沒有可以偵測的敵人"); return false; }
                for (ServerPlayer t : team) GlowManager.add(t, GlowManager.Audience.ALL, p, true, glow);
                scanSound(p);
            }
            case RADAR_PULSE -> {
                double r = s.radarRange.get();
                for (ServerPlayer t : GlowManager.enemies(p)) {
                    if (t.level() == p.level() && t.distanceTo(p) <= r) {
                        GlowManager.add(t, GlowManager.Audience.ONE, p, true, s.radarSeconds.get(), false); // 敵人不會被通知
                    }
                }
                PGNetwork.sendTo(p, new SpecialFxPacket(SpecialFxPacket.RADAR, (int) r, 0, p.getX(), p.getY(), p.getZ()));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
            }
            case SNOW_WALL -> SpecialWorld.addSnowTrail(p);
            case KNOCKBACK_SHIELD -> {
                if (!KnockbackShield.equip(p)) return false;
            }
            case WIND_BOMB -> SpecialProjectile.throwFrom(p, SpecialType.WIND_BOMB, 1.4f);
            case GAS_GRENADE -> SpecialProjectile.throwFrom(p, SpecialType.GAS_GRENADE, 1.2f);
            case WARP_PEARL -> {
                PEARLS.put(p.getUUID(), SpecialProjectile.throwFrom(p, SpecialType.WARP_PEARL, s.pearlSpeed.get().floatValue()));
                PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.PEARL, 1, 0)); // 快捷欄提示「再按一次傳送」
            }
            case FAKE_CRATE_LOW, FAKE_CRATE_MID, FAKE_CRATE_HIGH -> {
                if (!placeFakeCrate(p, type)) { msg(p, "前方沒有可以放箱子的地面"); return false; }
            }
            case RECON_MINE, LAND_MINE, FIRE_MINE, SHOCK_MINE -> {
                if (!p.onGround()) { msg(p, "要站在地上才能放地雷"); return false; }
                level.addFreshEntity(MineEntity.create(level, type, p, p.position()));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6f, 0.8f);
            }
            case FLARE_GUN -> {
                var airdrops = BattleRoyaleMode.get().airdrops();
                if (airdrops == null || !BattleRoyaleMode.get().isInPlay()) { msg(p, "現在不能召喚空投"); return false; }
                airdrops.flareDrop(p);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2f, 0.8f);
            }
            case SMOKE_BURST -> SpecialWorld.addSmoke(p);
            case TOTEM -> Totem.give(p);
            case SHIELD_BUBBLE -> Engines.effect(p, SpecialRegistry.SHIELD_BUBBLE.get(), SpecialConfig.ticks(s.bubbleSeconds), 0);
            case SILENT_BOOTS -> Engines.effect(p, SpecialRegistry.SILENT.get(), SpecialConfig.ticks(s.silentSeconds), 0);
            case FIRST_AID_SHOT -> {
                BattleRoyaleMode br = BattleRoyaleMode.get();
                if (br.isDowned(p.getUUID())) {
                    if (!br.startSelfRevive(p)) { msg(p, "已經在自救了"); return false; }
                } else if (!br.instantRevive(p)) {
                    // 計畫書 7 C7：沒倒地、也沒在扶隊友 → 立刻回復 25 血
                    if (p.getHealth() >= p.getMaxHealth()) { msg(p, "血量已經是滿的"); return false; }
                    p.heal(25f);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.8f);
                }
            }
            case ARMOR_REPAIR, ARMOR_REPAIR_PLUS -> repairArmor(p, type == SpecialType.ARMOR_REPAIR_PLUS);
            case ZONE_SHELTER -> Engines.effect(p, SpecialRegistry.ZONE_SHELTER.get(), SpecialConfig.ticks(s.shelterSeconds), 0);
            case DECOY -> level.addFreshEntity(DecoyEntity.create(level, p));
            // ── 計畫書 6 E 章（邏輯在 NewSpecials） ──
            case BRAINWASH -> NewSpecials.brainwash(p);
            case ROOT_ORB -> SpecialProjectile.throwFrom(p, SpecialType.ROOT_ORB, 1.3f);
            case FIRE_DRILL -> SpecialProjectile.throwFrom(p, SpecialType.FIRE_DRILL, 1.2f);
            case RECALL, RECALL_PLUS -> {
                int r = NewSpecials.recall(p, type);
                if (r < 0) return false;
                keepItem = r == 0;
            }
            case GAMBLER_ROD -> NewSpecials.gamble(p, false);
            case GAMBLER_ROD_PLUS -> NewSpecials.gamble(p, true);
            case THOR_HAMMER -> NewSpecials.thor(p, false);
            case THOR_HAMMER_PLUS -> NewSpecials.thor(p, true);
            case RESKYDIVE, RESKYDIVE_GROUP, RESKYDIVE_TARGET -> {
                if (!NewSpecials.reSkydive(p, type)) return false;
            }
        }
        return true;
    }

    private static void scanSound(ServerPlayer p) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    /** 修復所有護甲；強化版另外讓耐久上限 +50%（記在物品 NBT pg_dura_bonus，ItemStackMixin 計算上限）。 */
    private static void repairArmor(ServerPlayer p, boolean plus) {
        SpecialConfig s = SpecialConfig.get();
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack a = p.getItemBySlot(slot);
            if (!(a.getItem() instanceof ArmorItem)) continue;
            if (plus) {
                int n = a.getOrCreateTag().getInt(DURA_TAG);
                int max = s.armorPlusMaxStacks.get();
                if (max <= 0 || n < max) a.getOrCreateTag().putInt(DURA_TAG, n + 1);
            }
            a.setDamageValue(0);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 強化護甲修復包疊加次數（物品 NBT）。 */
    public static final String DURA_TAG = "pg_dura_bonus";

    /** 在面前 1 格的地板放假箱（同階真箱的方塊，外觀完全一樣）。 */
    private static boolean placeFakeCrate(ServerPlayer p, SpecialType type) {
        ServerLevel level = p.serverLevel();
        Direction face = p.getDirection();
        BlockPos base = p.blockPosition().relative(face);
        Block block = switch (type) {
            case FAKE_CRATE_MID -> PGBlocks.CRATE_MID.get();
            case FAKE_CRATE_HIGH -> PGBlocks.CRATE_HIGH.get();
            default -> PGBlocks.CRATE_LOW.get();
        };
        for (int dy : new int[] { 0, 1, -1 }) {
            BlockPos pos = base.above(dy);
            BlockState here = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if ((here.isAir() || here.canBeReplaced()) && !below.getCollisionShape(level, pos.below()).isEmpty()) {
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                if (level.getBlockEntity(pos) instanceof CrateBlockEntity c) c.makeFake(p.getUUID());
                SpecialWorld.rememberFakeCrate(level, pos);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 1f);
                return true;
            }
        }
        return false;
    }

    /** 假箱被打開（CrateBlock.use 呼叫）：受傷、發光、震盪，假箱立刻銷毀。放置者與隊友打開也會觸發。 */
    public static void triggerFakeCrate(ServerLevel level, BlockPos pos, ServerPlayer opener, @Nullable UUID ownerId) {
        SpecialConfig s = SpecialConfig.get();
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        level.removeBlock(pos, false);
        SpecialWorld.forgetFakeCrate(level, pos);
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1f, 1.3f);
        opener.hurt(owner != null && owner != opener ? level.damageSources().playerAttack(owner) : level.damageSources().magic(),
            s.fakeCrateDamage.get().floatValue());
        if (s.fakeCrateGlowSeconds.get() > 0) GlowManager.add(opener, GlowManager.Audience.ALL, null, false, s.fakeCrateGlowSeconds.get());
        int con = SpecialConfig.ticks(s.fakeCrateConcussSeconds);
        if (con > 0) Engines.concuss(opener, con);
    }
}
