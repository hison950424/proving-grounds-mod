package com.provinggrounds.special;

import com.provinggrounds.core.TeamManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.network.GlowSyncPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: GlowManager.java
 * 目的: 發光引擎（計畫書 4 的 B4）。六種組合 = 觀眾 × 是否上地圖：
 *   觀眾 ALL（全體）／ONE（只有使用者）／TEAM（使用者的整隊）；map = true 是「強力發光」（也畫在地圖上）。
 *   做法：伺服器記住每一筆發光（目標、觀眾、是否上地圖、到期時間），每 0.5 秒把「這個玩家現在看得到誰發光」
 *   整份清單送給他（GlowSyncPacket）。客戶端只對清單裡的實體畫發光外框（EntityGlowMixin），地圖上畫紅點。
 *   沒有被列為觀眾的玩家完全收不到，所以「單向發光」別人看不到。
 *   被發光的人會收到提示，並註明是「只有發光」還是「連地圖都顯示」。
 */
public final class GlowManager {

    public enum Audience { ALL, ONE, TEAM }

    private record Glow(UUID target, Audience audience, UUID user, boolean map, long endTick) {}

    private static final List<Glow> GLOWS = new ArrayList<>();
    /** 上次送過非空清單的玩家（清單變空時要再送一次空的）。 */
    private static final Set<UUID> SENT = new HashSet<>();
    private static boolean dirty;

    private GlowManager() {}

    /** 讓 target 發光 seconds 秒。user = 使用者（ONE、TEAM 的觀眾依據；ALL 時可以是 null）。 */
    public static void add(ServerPlayer target, Audience audience, @Nullable ServerPlayer user, boolean map, double seconds) {
        add(target, audience, user, map, seconds, true);
    }

    /** notify = false：被發光的人不會收到提示（雷達脈衝：計畫書寫明「敵人不會被通知」）。 */
    public static void add(ServerPlayer target, Audience audience, @Nullable ServerPlayer user, boolean map, double seconds, boolean notify) {
        long end = target.server.getTickCount() + Math.round(seconds * 20);
        GLOWS.add(new Glow(target.getUUID(), audience, user == null ? null : user.getUUID(), map, end));
        dirty = true;
        if (!notify) return;
        String who = switch (audience) {
            case ALL -> "所有人";
            case ONE -> "敵人";
            case TEAM -> "敵方整隊";
        };
        target.displayClientMessage(Component.literal(map
                ? "⚠ 你被強力發光了：" + who + "看得到你，連地圖都顯示你的位置（" + (int) Math.ceil(seconds) + " 秒）"
                : "⚠ 你被發光了：" + who + "看得到你的外框（不顯示在地圖，" + (int) Math.ceil(seconds) + " 秒）")
            .withStyle(ChatFormatting.GOLD), true);
    }

    public static void clearAll(MinecraftServer server) {
        GLOWS.clear();
        dirty = true;
        tick(server, true);
    }

    /** 每 tick 呼叫（WorldEvents）。 */
    public static void tick(MinecraftServer server) {
        tick(server, false);
    }

    private static void tick(MinecraftServer server, boolean force) {
        long now = server.getTickCount();
        if (GLOWS.removeIf(g -> g.endTick() <= now)) dirty = true;
        if (!force && !dirty && now % 10 != 0) return;
        dirty = false;
        if (GLOWS.isEmpty() && SENT.isEmpty()) return;
        TeamManager teams = TeamManager.get();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<GlowSyncPacket.Entry> list = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (Glow g : GLOWS) {
                if (!canSee(g, viewer, teams)) continue;
                ServerPlayer t = server.getPlayerList().getPlayer(g.target());
                if (t == null || t.level() != viewer.level() || t == viewer) continue;
                if (!seen.add(t.getUUID())) {
                    // 同一個目標有多筆：只要有一筆上地圖就上地圖
                    if (g.map()) list.replaceAll(e -> e.entityId() == t.getId() ? new GlowSyncPacket.Entry(e.entityId(), e.x(), e.z(), true, e.color()) : e);
                    continue;
                }
                list.add(new GlowSyncPacket.Entry(t.getId(), (float) t.getX(), (float) t.getZ(), g.map(), 0xFF3030));
            }
            if (list.isEmpty()) {
                if (SENT.remove(viewer.getUUID())) PGNetwork.sendTo(viewer, new GlowSyncPacket(list));
            } else {
                SENT.add(viewer.getUUID());
                PGNetwork.sendTo(viewer, new GlowSyncPacket(list));
            }
        }
        SENT.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static boolean canSee(Glow g, ServerPlayer viewer, TeamManager teams) {
        return switch (g.audience()) {
            case ALL -> true;
            case ONE -> viewer.getUUID().equals(g.user());
            case TEAM -> g.user() != null && (viewer.getUUID().equals(g.user()) || teams.isSameTeam(viewer.getUUID(), g.user()));
        };
    }

    // ── 選目標 ───────────────────────────────────────────────────────────────

    /** 存活的敵人（不同隊、不是自己；倒地的人也算）。 */
    public static List<ServerPlayer> enemies(ServerPlayer user) {
        BattleRoyaleMode br = BattleRoyaleMode.get();
        TeamManager teams = TeamManager.get();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : user.server.getPlayerList().getPlayers()) {
            if (p == user || !br.isAliveParticipant(p.getUUID())) continue;
            if (teams.isSameTeam(p.getUUID(), user.getUUID())) continue;
            out.add(p);
        }
        return out;
    }

    @Nullable
    public static ServerPlayer randomEnemy(ServerPlayer user) {
        List<ServerPlayer> list = enemies(user);
        return list.isEmpty() ? null : list.get(user.getRandom().nextInt(list.size()));
    }

    /** 隨機一隊敵人的所有存活成員。 */
    public static List<ServerPlayer> randomEnemyTeam(ServerPlayer user) {
        ServerPlayer pick = randomEnemy(user);
        if (pick == null) return List.of();
        TeamManager teams = TeamManager.get();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : enemies(user)) if (p == pick || teams.isSameTeam(p.getUUID(), pick.getUUID())) out.add(p);
        return out;
    }
}
