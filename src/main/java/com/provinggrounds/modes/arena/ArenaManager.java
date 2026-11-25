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

    /** F 鍵：扶團隊模式倒地的隊友。 */
    public static boolean startReviveByKey(ServerPlayer helper, @javax.annotation.Nullable net.minecraft.world.entity.Entity looked) {
        ArenaMode m = current;
        return m != null && m.downedEnabled && !m.isEnding() && m.downed.startReviveByKey(helper, looked);
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
        if (current != null && event.getEntity() instanceof ServerPlayer p) { current.downed.onLogout(p); current.handleLogout(p); }
    }

    /** 重生保護：被保護的人不會受傷，也不能攻擊別人（計畫書 5 的 H2）。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if (current.isProtected(victim.getUUID()) && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && current.isProtected(attacker.getUUID())) event.setCanceled(true);
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
    public static void onDeath(LivingDeathEvent event) {
        if (current == null || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer k ? k : null;
        com.provinggrounds.core.KillEvents.setSource(event.getSource());
        current.handleDeath(victim, killer);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
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

    /** 不能丟的東西（裝備）立刻放回背包。 */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (current == null || !(event.getPlayer() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getEntity().getItem();
        if (!current.handleToss(p, stack)) return;
        event.setCanceled(true);
        if (!p.getInventory().add(stack.copy())) p.drop(stack.copy(), false);
        p.inventoryMenu.broadcastChanges();
    }

    /** 掉落物：無限彈藥箱不掉（佔點會掉其他東西）。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (current == null || !(event.getEntity() instanceof ServerPlayer p) || !current.isParticipant(p.getUUID())) return;
        ItemStack box = com.provinggrounds.modes.br.RespawnKit.parse(com.provinggrounds.config.PGConfig.SERVER.arena.ammoBox.get());
        event.getDrops().removeIf((ItemEntity e) -> !box.isEmpty() && ArenaMode.matches(e.getItem(), box));
    }
}
