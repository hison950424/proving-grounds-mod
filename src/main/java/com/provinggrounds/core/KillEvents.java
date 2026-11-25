package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.events.ArmorEvents;
import com.provinggrounds.modes.arena.ArenaMode;
import com.provinggrounds.network.KillNoticePacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.venue.Venue;
import com.provinggrounds.venue.VenueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: KillEvents.java
 * 目的: 擊殺提示與連殺（計畫書 5 的 D1、E3；伺服器端）。
 *   - 團隊死鬥／佔點／槍王之王：ArenaMode.handleDeath 呼叫 arenaKill（擊殺紀錄給全部參賽者、圖示與音效給擊殺者）。
 *   - 大逃殺：BattleRoyaleMode 淘汰時呼叫 brKill（只給擊殺者圖示與音效；擊殺紀錄沿用大逃殺自己的）。
 *   - 迷你死鬥（場地 minidm）：這裡自己聽死亡事件，記錄每人連殺；死亡時連殺 ≥ 10 → 全服聊天欄公告＋音效，然後歸零。
 *   公告一律用聊天欄小字，不用畫面中央標題（A1）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class KillEvents {

    public static final int STREAK_ANNOUNCE = 10;

    private static final Map<UUID, Integer> MINIDM_STREAK = new HashMap<>();
    @Nullable private static DamageSource lastSource;

    private KillEvents() {}

    /** ArenaManager 在轉交死亡事件前記下傷害來源（判斷爆頭）。 */
    public static void setSource(@Nullable DamageSource source) { lastSource = source; }

    /** 擊殺紀錄的旗標（爆頭；大逃殺的擊殺資訊用）。 */
    public static int flagsFor(@Nullable ServerPlayer killer, ServerPlayer victim, @Nullable DamageSource source) {
        if (killer == null) return 0;
        return KillNoticePacket.flags(headshot(victim, source));
    }

    private static boolean headshot(ServerPlayer victim, @Nullable DamageSource source) {
        return source != null && Boolean.TRUE.equals(ArmorEvents.wasHeadshot(victim, source));
    }

    public static void arenaKill(ArenaMode m, @Nullable ServerPlayer killer, @Nullable ArenaMode.ArenaPlayer k,
                                 ServerPlayer victim, ArenaMode.ArenaPlayer v, int lostStreak) {
        boolean hs = killer != null && headshot(victim, lastSource);
        ItemStack weapon = killer != null ? killer.getMainHandItem().copyWithCount(1) : ItemStack.EMPTY;
        String kName = killer != null ? killer.getGameProfile().getName() : "";
        int flags = KillNoticePacket.flags(hs);
        int kColor = k != null ? m.colorOf(k) : 0xFFFFFF;
        for (ServerPlayer p : victim.server.getPlayerList().getPlayers()) {
            if (!m.isParticipant(p.getUUID())) continue;
            PGNetwork.sendTo(p, new KillNoticePacket(true, p == killer, kName, kColor, v.name, m.colorOf(v), weapon, flags, k != null ? k.streak : 0));
        }
        if (lostStreak >= STREAK_ANNOUNCE) announceEnded(victim.server, v.name, lostStreak, kName);
        lastSource = null;
    }

    /** 掉線分身被殺（計畫書 7 D）：沒有被擊殺者的玩家物件，用名字與隊伍顏色。 */
    public static void arenaKillOffline(ArenaMode m, net.minecraft.server.MinecraftServer server, @Nullable ServerPlayer killer,
                                        @Nullable ArenaMode.ArenaPlayer k, ArenaMode.ArenaPlayer v, int lostStreak) {
        ItemStack weapon = killer != null ? killer.getMainHandItem().copyWithCount(1) : ItemStack.EMPTY;
        String kName = killer != null ? killer.getGameProfile().getName() : "";
        int kColor = k != null ? m.colorOf(k) : 0xFFFFFF;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!m.isParticipant(p.getUUID())) continue;
            PGNetwork.sendTo(p, new KillNoticePacket(true, p == killer, kName, kColor, v.name, m.colorOf(v), weapon, 0, k != null ? k.streak : 0));
        }
        if (lostStreak >= STREAK_ANNOUNCE) announceEnded(server, v.name, lostStreak, kName);
    }

    /** 大逃殺淘汰：只給擊殺者圖示與音效。 */
    public static void brKill(@Nullable ServerPlayer killer, ServerPlayer victim, @Nullable DamageSource source, int streak) {
        if (killer == null) return;
        PGNetwork.sendTo(killer, new KillNoticePacket(false, true, killer.getGameProfile().getName(), 0xFFFFFF,
            victim.getGameProfile().getName(), 0xFFFFFF, killer.getMainHandItem().copyWithCount(1), KillNoticePacket.flags(headshot(victim, source)), streak));
    }

    private static void announceEnded(net.minecraft.server.MinecraftServer server, String name, int streak, String by) {
        Component msg = Component.literal(name + " 的 " + streak + " 連殺被" + (by.isEmpty() ? "" : " " + by + " ") + "終結了！")
            .withStyle(ChatFormatting.GOLD);
        server.getPlayerList().broadcastSystemMessage(msg, false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 0.4f, 1.4f);
        }
    }

    // ── 迷你死鬥連殺（D1）──

    private static boolean inMiniDm(ServerPlayer p) {
        Venue v = VenueManager.current(p);
        return v != null && "minidm".equals(v.id());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim) || !inMiniDm(victim)) return;
        int lost = MINIDM_STREAK.getOrDefault(victim.getUUID(), 0);
        MINIDM_STREAK.remove(victim.getUUID());
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer k && k != victim && inMiniDm(k) ? k : null;
        int streak = 0;
        if (killer != null) {
            streak = MINIDM_STREAK.merge(killer.getUUID(), 1, Integer::sum);
            boolean hs = headshot(victim, event.getSource());
            ItemStack weapon = killer.getMainHandItem().copyWithCount(1);
            int flags = KillNoticePacket.flags(hs);
            for (ServerPlayer p : victim.server.getPlayerList().getPlayers()) {
                if (!inMiniDm(p)) continue;
                PGNetwork.sendTo(p, new KillNoticePacket(true, p == killer, killer.getGameProfile().getName(), 0xFFFFFF,
                    victim.getGameProfile().getName(), 0xFFFFFF, weapon, flags, streak));
            }
        }
        if (lost >= STREAK_ANNOUNCE) announceEnded(victim.server, victim.getGameProfile().getName(), lost, killer != null ? killer.getGameProfile().getName() : "");
    }

    /**
     * 其他所有生物（計畫書 6 A5）：玩家殺死任何生物（動物、怪物、訓練假人…）也有擊殺圖示與音效。
     * 玩家被殺時，大逃殺、團隊模式、迷你死鬥已經各自處理，這裡只處理不在這些模式裡的玩家。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnyDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == event.getEntity()) return;
        net.minecraft.world.entity.LivingEntity victim = event.getEntity();
        if (victim instanceof com.provinggrounds.special.entity.DecoyEntity d && d.isBody()) return; // 掉線分身：各模式自己發擊殺提示（計畫書 7 D）
        if (victim instanceof ServerPlayer vp) {
            if (inMiniDm(vp) || com.provinggrounds.modes.BattleRoyaleMode.get().isParticipant(vp.getUUID())
                || com.provinggrounds.modes.arena.ArenaManager.isParticipant(vp.getUUID())) return;
        }
        PGNetwork.sendTo(killer, new KillNoticePacket(false, true, killer.getGameProfile().getName(), 0xFFFFFF,
            victim.getName().getString(), 0xFFFFFF, killer.getMainHandItem().copyWithCount(1), 0, 0));
    }

    /** 離開迷你死鬥（換場地、離線）就歸零。 */
    public static void resetMiniDm(UUID id) { MINIDM_STREAK.remove(id); }
}
