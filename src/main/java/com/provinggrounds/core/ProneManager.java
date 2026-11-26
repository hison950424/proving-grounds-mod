package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.BRDowned;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.ProneSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 檔案: ProneManager.java
 * 目的: 玩家自己按鍵趴下／站起來（伺服器端，所有模式通用，大廳也可以）。
 *   趴下 = 強制「游泳姿勢」（1 格高、移動變慢、可以照常開槍）。
 *   冷卻：趴下後要過 cooldown 秒才能站起來，站起來後也要過 cooldown 秒才能再趴下（預設 1 秒）。
 *   不能趴下：觀察者、死亡、騎乘中、倒地中、BR 飛機上／自由落體／開傘中、不在地面上。
 *   站起來：頭上要有空間，否則提示「上方空間不足」。
 *   上述狀態出現時（例如被擊倒、搭上飛機）自動解除趴下。
 *   趴下名單有變動就廣播給所有玩家（ProneSyncPacket），各客戶端自己套用姿勢與放慢動畫。
 * 執行者：伺服器（WorldEvents 每 tick 呼叫 tick()；客戶端按鍵經 ProneTogglePacket 呼叫 toggle()）。
 */
public final class ProneManager {

    private static final Set<UUID> PRONE = new HashSet<>();
    private static final Map<UUID, Long> LAST_CHANGE = new HashMap<>();
    private static boolean dirty;

    private ProneManager() {}

    public static boolean isProne(UUID id) { return PRONE.contains(id); }

    public static int cooldownTicks() {
        return (int) Math.round(PGConfig.SERVER.proneCooldownSeconds.get() * 20);
    }

    /** 玩家按下趴下鍵。 */
    public static void toggle(ServerPlayer p) {
        if (!PGConfig.SERVER.proneEnabled.get()) return;
        long now = p.level().getGameTime();
        Long last = LAST_CHANGE.get(p.getUUID());
        if (last != null && now - last < cooldownTicks()) {
            actionBar(p, "動作冷卻中…");
            return;
        }
        if (PRONE.contains(p.getUUID())) {
            if (!hasRoomToStand(p)) {
                actionBar(p, "上方空間不足，無法站起來");
                return;
            }
            PRONE.remove(p.getUUID());
            p.setForcedPose(null);
        } else {
            String why = cannotProne(p);
            if (why != null) {
                actionBar(p, why);
                return;
            }
            PRONE.add(p.getUUID());
            p.setForcedPose(Pose.SWIMMING);
            p.setSprinting(false);
        }
        LAST_CHANGE.put(p.getUUID(), now);
        dirty = true;
    }

    /** 立刻解除趴下（遊戲開始／結束還原時用）；不動姿勢以外的東西。 */
    public static void clear(ServerPlayer p) {
        if (PRONE.remove(p.getUUID())) {
            if (!BRDowned.isDowned(p)) p.setForcedPose(null);
            dirty = true;
        }
    }

    public static void onLogout(ServerPlayer p) {
        if (PRONE.remove(p.getUUID())) dirty = true;
        LAST_CHANGE.remove(p.getUUID());
    }

    /** 新登入的玩家要知道誰正在趴著。 */
    public static void onLogin(ServerPlayer p) {
        PGNetwork.sendTo(p, new ProneSyncPacket(syncList()));
    }

    /** 團隊模式倒地有變（計畫書 7 E）：下一 tick 重新同步趴下名單。 */
    public static void markDirty() { dirty = true; }

    /** 同步給客戶端的趴下名單：自己趴下的人＋團隊模式倒地的人。 */
    private static List<UUID> syncList() {
        List<UUID> out = new ArrayList<>(PRONE);
        for (UUID id : com.provinggrounds.modes.arena.ArenaManager.downedIds()) if (!out.contains(id)) out.add(id);
        return out;
    }

    public static void tick(MinecraftServer server) {
        for (UUID id : new ArrayList<>(PRONE)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) { PRONE.remove(id); dirty = true; continue; }
            if (BRDowned.isDowned(p)) { PRONE.remove(id); dirty = true; continue; } // 倒地姿勢由 BR 接手
            if (p.isSpectator() || !p.isAlive() || p.isPassenger() || BattleRoyaleMode.get().isAirborne(id)) {
                PRONE.remove(id);
                p.setForcedPose(null);
                dirty = true;
                continue;
            }
            if (p.getForcedPose() != Pose.SWIMMING) p.setForcedPose(Pose.SWIMMING); // 被其他程式還原時補回來
        }
        if (dirty) {
            dirty = false;
            PGNetwork.sendToAll(new ProneSyncPacket(syncList()));
        }
    }

    private static String cannotProne(ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive()) return "現在不能趴下";
        if (BRDowned.isDowned(p)) return "倒地中不能趴下";
        if (p.isPassenger()) return "騎乘中不能趴下";
        if (BattleRoyaleMode.get().isAirborne(p.getUUID())) return "跳傘中不能趴下";
        return null; // 計畫書 10：空中也可以直接趴下
    }

    /** 站起來的話會不會卡進方塊。 */
    private static boolean hasRoomToStand(ServerPlayer p) {
        AABB box = p.getDimensions(Pose.STANDING).makeBoundingBox(p.position()).deflate(1.0E-7);
        return p.level().noCollision(p, box);
    }

    private static void actionBar(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY), true);
    }
}
