package com.provinggrounds.ui;

import com.provinggrounds.network.StatsLeaderboardPacket;
import com.provinggrounds.network.StatsPersonalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 檔案: ClientStatsBoard.java
 * 目的: 數據看板的客戶端資料與顯示狀態（計畫書 B5）。
 *   - personal：伺服器送來的自己的數據；leaderboard：附近看板同步來的全服排行。
 *   - 顯示狀態（看個人或排行、排行依什麼排序）只存在這個客戶端，所有看板共用，不存檔；重新登入回到個人數據。
 */
public final class ClientStatsBoard {

    public enum Sort {
        WINS("勝場", Comparator.comparingInt(StatsLeaderboardPacket.Entry::wins)),
        KILLS("擊殺", Comparator.comparingInt(StatsLeaderboardPacket.Entry::kills)),
        KD("K/D", Comparator.comparingDouble(StatsLeaderboardPacket.Entry::kd)),
        LEVEL("等級", Comparator.comparingInt(StatsLeaderboardPacket.Entry::level)),
        ASSISTS("助攻", Comparator.comparingInt(StatsLeaderboardPacket.Entry::assists));

        public final String label;
        final Comparator<StatsLeaderboardPacket.Entry> order;

        Sort(String label, Comparator<StatsLeaderboardPacket.Entry> asc) {
            this.label = label;
            this.order = asc.reversed().thenComparing(Comparator.comparingInt(StatsLeaderboardPacket.Entry::wins).reversed());
        }
    }

    @Nullable private static StatsPersonalPacket personal;
    private static List<StatsLeaderboardPacket.Entry> leaderboard = List.of();
    private static boolean showRanking;
    private static Sort sort = Sort.WINS;
    @Nullable private static List<StatsLeaderboardPacket.Entry> sortedCache;

    private ClientStatsBoard() {}

    public static void setPersonal(StatsPersonalPacket p) { personal = p; }

    public static void setLeaderboard(List<StatsLeaderboardPacket.Entry> list) {
        leaderboard = list;
        sortedCache = null;
    }

    /** 右鍵看板：切換畫面；蹲下＋右鍵：排行畫面切換排序（在個人畫面時先切到排行）。 */
    public static void onClick(boolean sneak) {
        if (sneak && showRanking) {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            sortedCache = null;
            actionBar("排行依「" + sort.label + "」排序");
        } else {
            showRanking = !showRanking;
            actionBar(showRanking ? "全服排行（蹲下＋右鍵換排序）" : "個人數據");
        }
    }

    private static void actionBar(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    @Nullable public static StatsPersonalPacket personal() { return personal; }
    public static boolean showRanking() { return showRanking; }
    public static Sort sort() { return sort; }

    public static List<StatsLeaderboardPacket.Entry> sorted() {
        if (sortedCache == null) {
            List<StatsLeaderboardPacket.Entry> list = new ArrayList<>(leaderboard);
            list.sort(sort.order);
            sortedCache = list;
        }
        return sortedCache;
    }

    public static void clear() {
        personal = null;
        leaderboard = List.of();
        sortedCache = null;
        showRanking = false;
        sort = Sort.WINS;
    }
}
