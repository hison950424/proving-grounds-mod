package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: ArenaManager.java
 * 目的: 團隊死鬥／佔點／槍王之王的入口：開局、每 tick、強制結束，並把 Forge 事件轉給目前的模式。
 *   同一時間只會有一個對戰（也不會和大逃殺同時進行）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class ArenaManager {

    /** 大廳選的對戰設定。 */
    /** downed：團隊模式倒地開關（計畫書 7 E，大廳設定，預設關）。 */
    public record Settings(int tdmKills, int tdmMinutes, int domScore, int gkMultiplier, boolean downed) {}

    /** 團隊模式倒地中（倒地開關開著時）。 */
    public static boolean isDowned(java.util.UUID id) {
        ArenaMode m = current;
        return m != null && m.downedEnabled && m.downed.isDowned(id);
    }

    public static java.util.Set<java.util.UUID> downedIds() {
        ArenaMode m = current;
        return m != null && m.downedEnabled ? m.downed.ids() : java.util.Set.of();
    }

    /** 掉線分身被殺（計畫書 7 D）。 */
    public static void onBodyKilled(com.provinggrounds.core.DisconnectBodies.OfflineState s, @javax.annotation.Nullable ServerPlayer killer) {
        if (current != null) current.handleBodyDeath(s, killer);
    }

    public static boolean debugGoOffline(ServerPlayer p) {
        return current != null && !current.isEnding() && current.debugGoOffline(p);
    }

    /** 測試指令的模擬回歸。 */
    public static boolean debugReturn(ServerPlayer p) {
        if (current == null) return false;
        current.handleLogin(p);
        return true;
    }

    /** F 鍵：扶團隊模式倒地的隊友。 */
    public static boolean startReviveByKey(ServerPlayer helper, @javax.annotation.Nullable net.minecraft.world.entity.Entity looked) {
        ArenaMode m = current;
        return m != null && m.downedEnabled && !m.isEnding() && m.downed.startReviveByKey(helper, looked);
    }

    /** 團隊模式倒地長按 F 3 秒放棄並直接死亡。 */
    public static boolean giveUpDowned(ServerPlayer p) {
        ArenaMode m = current;
        return m != null && m.downedEnabled && !m.isEnding() && m.downed.giveUp(p);
    }

    /** 致命傷：倒地開關開著、有站著的隊友 → 改成倒地（取消這次死亡）。比一般死亡處理先跑。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLethal(LivingDeathEvent event) {
        ArenaMode m = current;
        if (m == null || !m.downedEnabled || m.isEnding() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if (m.downed.onLethal(victim, event.getSource())) event.setCanceled(true);
    }

    @Nullable private static ArenaMode current;

    private ArenaManager() {}

    @Nullable
    public static ArenaMode current() { return current; }

    public static boolean isRunning() { return current != null; }

    public static boolean isParticipant(UUID id) {
        return current != null && current.isParticipant(id);
    }

    /** 開局。成功回傳 null。 */
    @Nullable
    public static String start(MinecraftServer server, GameManager.GameMode mode, List<ServerPlayer> participants, Settings s) {
        if (current != null || BattleRoyaleMode.get().isRunning() || !GameManager.get().isInLobby()) return "已經有遊戲在進行中。";
        ArenaMode m = switch (mode) {
            case TDM -> new TdmMode(server, s.tdmKills(), s.tdmMinutes());
            case DOM -> new DomMode(server, s.domScore());
            case GK -> new GkMode(server, s.gkMultiplier());
            default -> null;
        };
        if (m != null) m.downedEnabled = s.downed(); // 計畫書 7 E：團隊模式倒地開關
        if (m == null) return "不支援的模式：" + mode;
        // 任何模式 1 個人就能開始（方便測試）
        current = m;
        String error = m.start(participants);
        if (error != null) current = null;
        return error;
    }

    private static int speed = 1;

    public static void setSpeed(int n) { speed = Math.max(1, Math.min(20, n)); }
    public static int speed() { return speed; }

    public static void tick() {
        for (int i = 0; i < speed && current != null; i++) current.tick();
        if (current == null) speed = 1; // 測試加速只在這一場有效
    }

    /** 團隊死鬥／佔點允許破壞玻璃類方塊（TDM 10 秒自動復原、DOM 每回合復原）。 */
    public static boolean allowsGlassDamage(net.minecraft.core.BlockPos pos) {
        ArenaMode m = current;
        return m != null && !m.isEnding() && m.allowsGlassDamage(pos);
    }

    /** 通知目前進行中的團隊模式有一塊玻璃被打破，排程自動復原。 */
    public static void onGlassBroken(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                     net.minecraft.world.level.block.state.BlockState state) {
        ArenaMode m = current;
        if (m != null && !m.isEnding() && level == m.level) {
            m.onGlassBroken(pos, state);
        }
    }

    /** 敵方重生區內不能開槍（anti_camp_no_fire）。給 GunCompat 的開槍攔截用。 */
    public static boolean blocksFire(net.minecraft.world.entity.LivingEntity e) {
        return current != null && e instanceof ServerPlayer p && current.blocksFire(p.getUUID());
    }

    /** 開槍：結束重生保護。 */
    public static void onShoot(net.minecraft.world.entity.LivingEntity e) {
        if (current != null && e instanceof ServerPlayer p) current.endProtection(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (current != null && event.getEntity() instanceof ServerPlayer p) { current.handleLogout(p); current.downed.onLogout(p); }
    }

    /** 重生保護：被保護的人不會受傷，也不能攻擊別人（計畫書 5 的 H2）。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if ((current.loadoutOpen() || current.isProtected(victim.getUUID())) && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && (current.loadoutOpen() || current.isProtected(attacker.getUUID()))) event.setCanceled(true);
    }

    /** 管理員強制結束、或重置：立刻還原（finish 會呼叫 GameManager.reset）。 */
    public static void forceStop() {
        ArenaMode m = current;
        if (m == null) return;
        current = null;
        m.finish();
    }

    static void clear(ArenaMode m) {
        if (current == m) current = null;
    }

    // ── 事件 ─────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (current == null || event.isCanceled() || event.getAmount() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            current.recordDamage(victim, attacker);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (current == null || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer k && k != victim ? k : null;
        if (killer == null) {
            // 倒地後流血自然死亡、長按 F 放棄或環境致死：仍把擊殺歸屬給當初擊倒他的敵人
            ServerPlayer downedBy = current.downed.consumeDownedBy(victim.getUUID());
            if (downedBy != null && downedBy != victim) killer = downedBy;
        } else {
            current.downed.consumeDownedBy(victim.getUUID());
        }
        com.provinggrounds.core.KillEvents.setSource(event.getSource());
        current.handleDeath(victim, killer);
        pinRespawnInArena(victim);
    }

    /** 原重生點備份（重生後還原，避免影響大廳）。 */
    private record SavedRespawn(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
                                @Nullable net.minecraft.core.BlockPos pos, float angle, boolean forced) {}
    private static final java.util.Map<UUID, SavedRespawn> SAVED_RESPAWN = new java.util.HashMap<>();

    /** 修正「團隊模式死亡等待重生時會閃回大廳一瞬間」：把原版重生點暫時設在場內。 */
    private static void pinRespawnInArena(ServerPlayer victim) {
        ArenaMode m = current;
        if (m == null) return;
        net.minecraft.world.phys.Vec3 t = m.respawnTargetFor(victim.getUUID());
        if (t == null) return;
        SAVED_RESPAWN.putIfAbsent(victim.getUUID(), new SavedRespawn(victim.getRespawnDimension(),
            victim.getRespawnPosition(), victim.getRespawnAngle(), victim.isRespawnForced()));
        victim.setRespawnPosition(m.arenaDimension(), net.minecraft.core.BlockPos.containing(t), victim.getYRot(), true, false);
    }

    private static void restoreRespawn(ServerPlayer p) {
        SavedRespawn s = SAVED_RESPAWN.remove(p.getUUID());
        if (s != null) p.setRespawnPosition(s.dim(), s.pos(), s.angle(), s.forced(), false);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) restoreRespawn(sp);
        if (current == null || event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        current.handleRespawn(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (current.isParticipant(p.getUUID())) current.handleLogin(p);
        else if (current.wasRemoved(p.getUUID())) { // 掉線過久被移出：回來只能旁觀
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("你因掉線過久被移出比賽，這場只能旁觀。")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }

    }

    /** 不能丟的東西（裝備）立刻放回背包；允許丟出的通知模式（槍王之王會標記專屬拾取者）。 */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (current == null || !(event.getPlayer() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getEntity().getItem();
        if (!current.handleToss(p, stack)) {
            current.onTossed(p, event.getEntity());
            return;
        }
        event.setCanceled(true);
        if (!p.getInventory().add(stack.copy())) p.drop(stack.copy(), false);
        p.inventoryMenu.broadcastChanges();
    }

    /** 撿取物品過濾：團隊死鬥／佔點禁止撿非本職業限定槍械；槍王之王丟出的槍除了自己以外不能撿。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (current.handlePickup(p, event.getItem())) event.setCanceled(true);
    }

    /** 掉落物：佔點死亡不噴裝也不產墓碑（全部取消）；其他模式不掉無限彈藥箱。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer p) || !current.isParticipant(p.getUUID())) return;
        if (current.kind() == GameManager.GameMode.DOM) {
            event.getDrops().clear();
            event.setCanceled(true);
            return;
        }
        ItemStack box = com.provinggrounds.modes.br.RespawnKit.parse(com.provinggrounds.config.PGConfig.SERVER.arena.ammoBox.get());
        event.getDrops().removeIf((ItemEntity e) -> !box.isEmpty() && ArenaMode.matches(e.getItem(), box));
    }
}
