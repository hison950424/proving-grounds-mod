package com.provinggrounds.modes.br;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: MatchResult.java
 * 目的: 一場 BR 的結算資料（計畫書 B4）。伺服器在結算時建立，打包送給所有人（MatchResultPacket），
 *       客戶端的「本場數據」畫面、聊天欄精簡版、/pg result 都用這份資料與這裡的計算，數字一定一致。
 *   MVP 分數 = 擊殺 × wKill ＋ 助攻 × wAssist ＋ 傷害 ÷ damagePerPoint ＋ 扶起 × wRevive ＋ 祭壇 × wAltar ＋（獲勝 ＋ winBonus），
 *   權重由伺服器設定決定並跟著資料一起送出（客戶端不用讀設定）。同分依序比擊殺、傷害、存活時間。
 */
public record MatchResult(String winnerLabel, List<String> winnerNames, int durationSeconds, Weights weights, List<Row> rows) {

    public record Weights(float kill, float assist, float damagePerPoint, float revive, float altar, float winBonus) {}

    public record Row(UUID id, String name, String team, int color, int place, boolean winner, boolean offline,
                      int kills, int assists, int damage, int aliveSeconds, int revives, int altarRevives, int xp) {}

    /** 亮點：標題、名字（最多 3 位，其餘「等 N 人」；沒有人符合時為「無」）、數值文字。 */
    public record Highlight(String title, String names, String value) {}

    public float score(Row r) {
        Weights w = weights;
        return r.kills() * w.kill() + r.assists() * w.assist() + r.damage() / Math.max(1f, w.damagePerPoint())
            + r.revives() * w.revive() + r.altarRevives() * w.altar() + (r.winner() ? w.winBonus() : 0);
    }

    /** 排行表：名次 → 擊殺 → 傷害。 */
    public List<Row> sortedRows() {
        List<Row> list = new ArrayList<>(rows);
        list.sort(Comparator.comparingInt(Row::place).thenComparing(Comparator.comparingInt(Row::kills).reversed())
            .thenComparing(Comparator.comparingInt(Row::damage).reversed()));
        return list;
    }

    /** MVP（從所有參與者選，不限獲勝隊伍）；沒有參與者時為 null。 */
    public Row mvp() {
        Row best = null;
        for (Row r : rows) {
            if (best == null) { best = r; continue; }
            int c = Float.compare(score(r), score(best));
            if (c == 0) c = Integer.compare(r.kills(), best.kills());
            if (c == 0) c = Integer.compare(r.damage(), best.damage());
            if (c == 0) c = Integer.compare(r.aliveSeconds(), best.aliveSeconds());
            if (c > 0) best = r;
        }
        return best;
    }

    public List<Highlight> highlights() {
        List<Highlight> out = new ArrayList<>();
        Row mvp = mvp();
        out.add(new Highlight("本場 MVP", mvp == null ? "無" : mvp.name(), mvp == null ? "" : String.format("%.1f 分", score(mvp))));
        out.add(top("最高擊殺", Row::kills, v -> v + " 殺"));
        out.add(top("最高助攻", Row::assists, v -> v + " 次"));
        out.add(top("最高傷害", Row::damage, String::valueOf));
        out.add(top("最長存活", Row::aliveSeconds, MatchResult::mmss));
        out.add(top("扶起隊友", Row::revives, v -> v + " 次"));
        out.add(top("祭壇復活", Row::altarRevives, v -> v + " 人"));
        return out;
    }

    private Highlight top(String title, java.util.function.ToIntFunction<Row> f, java.util.function.IntFunction<String> format) {
        int max = 0;
        for (Row r : rows) max = Math.max(max, f.applyAsInt(r));
        if (max <= 0) return new Highlight(title, "無", "");
        List<String> names = new ArrayList<>();
        for (Row r : rows) if (f.applyAsInt(r) == max) names.add(r.name());
        String shown = String.join("、", names.subList(0, Math.min(3, names.size())));
        if (names.size() > 3) shown += " 等 " + names.size() + " 人";
        return new Highlight(title, shown, format.apply(max));
    }

    public static String mmss(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ── 封包 ────────────────────────────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(winnerLabel);
        buf.writeVarInt(winnerNames.size());
        for (String n : winnerNames) buf.writeUtf(n);
        buf.writeVarInt(durationSeconds);
        buf.writeFloat(weights.kill()); buf.writeFloat(weights.assist()); buf.writeFloat(weights.damagePerPoint());
        buf.writeFloat(weights.revive()); buf.writeFloat(weights.altar()); buf.writeFloat(weights.winBonus());
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUUID(r.id()); buf.writeUtf(r.name()); buf.writeUtf(r.team()); buf.writeInt(r.color());
            buf.writeVarInt(r.place()); buf.writeBoolean(r.winner()); buf.writeBoolean(r.offline());
            buf.writeVarInt(r.kills()); buf.writeVarInt(r.assists()); buf.writeVarInt(r.damage()); buf.writeVarInt(r.aliveSeconds());
            buf.writeVarInt(r.revives()); buf.writeVarInt(r.altarRevives()); buf.writeVarInt(r.xp());
        }
    }

    public static MatchResult decode(FriendlyByteBuf buf) {
        String label = buf.readUtf();
        int nw = buf.readVarInt();
        List<String> winners = new ArrayList<>();
        for (int i = 0; i < nw; i++) winners.add(buf.readUtf());
        int duration = buf.readVarInt();
        Weights w = new Weights(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new MatchResult(label, winners, duration, w, rows);
    }
}
