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
    public record Settings(int tdmKills, int tdmMinutes, int domScore, int gkMultiplier) {}

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
        if (m == null) return "不支援的模式：" + mode;
        if (mode != GameManager.GameMode.GK && participants.size() < 2) return "團隊模式至少要 2 個人。";
        current = m;
        String error = m.start(participants);
        if (error != null) current = null;
        return error;
    }

    public static void tick() {
        if (current != null) current.tick();
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
