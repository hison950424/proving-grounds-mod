package com.provinggrounds.network;

import com.provinggrounds.modes.arena.ArenaMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: ArenaStatePacket.java
 * 目的: 伺服器 → 客戶端。團隊死鬥／佔點／槍王之王的 HUD 與商店狀態（每 10 tick 一次）。
 *   kind 0 = 沒有對戰（客戶端清掉 HUD）。
 */
public record ArenaStatePacket(
    byte kind, String title, int seconds, String timerLabel, String status,
    List<ArenaMode.ScoreLine> scores,
    int money, int classOrdinal, boolean shop, boolean classes, boolean canShop, boolean loadoutOpen, int team,
    int transferCooldown, int kills, int deaths, List<Mate> mates,
    List<Marker> markers, List<Row> board
) {

    /** 隊友（轉帳用）。 */
    public record Mate(UUID id, String name, int money) {}

    /**
     * 世界裡要畫的標記（計畫書 5 的 H2、I）：kind 0 = 開放中的據點（光柱、範圍框、字母與距離），
     * kind 1 = 敵方重生區（接近時的紅色邊界）。radius：據點是方形半邊長、重生區是圓半徑。
     */
    public record Marker(byte kind, String label, double x, double y, double z, float radius, int color, boolean contested) {}

    /** 戰績面板的一行（計畫書 5 的 E2）。money < 0 = 這個模式沒有金錢。 */
    public record Row(String name, int color, int kills, int deaths, int money, boolean online) {}

    public static final ArenaStatePacket NONE = new ArenaStatePacket((byte) 0, "", -1, "", "", List.of(),
        0, 0, false, false, false, false, 0, 0, 0, 0, List.of(), List.of(), List.of());

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        if (kind == 0) return;
        buf.writeUtf(title, 64);
        buf.writeVarInt(seconds + 1);
        buf.writeUtf(timerLabel, 64);
        buf.writeUtf(status, 256);
        buf.writeVarInt(scores.size());
        for (ArenaMode.ScoreLine s : scores) {
            buf.writeInt(s.color());
            buf.writeUtf(s.label(), 32);
            buf.writeVarInt(s.value());
            buf.writeVarInt(s.target());
            buf.writeUtf(s.extra(), 64);
        }
        buf.writeVarInt(money);
        buf.writeVarInt(classOrdinal);
        buf.writeByte((shop ? 1 : 0) | (classes ? 2 : 0) | (canShop ? 4 : 0) | (loadoutOpen ? 8 : 0));
        buf.writeVarInt(team);
        buf.writeVarInt(transferCooldown);
        buf.writeVarInt(kills);
        buf.writeVarInt(deaths);
        buf.writeVarInt(mates.size());
        for (Mate m : mates) {
            buf.writeUUID(m.id());
            buf.writeUtf(m.name(), 32);
            buf.writeVarInt(m.money());
        }
        buf.writeVarInt(markers.size());
        for (Marker k : markers) {
            buf.writeByte(k.kind());
            buf.writeUtf(k.label(), 16);
            buf.writeDouble(k.x()); buf.writeDouble(k.y()); buf.writeDouble(k.z());
            buf.writeFloat(k.radius());
            buf.writeInt(k.color());
            buf.writeBoolean(k.contested());
        }
        buf.writeVarInt(board.size());
        for (Row r : board) {
            buf.writeUtf(r.name(), 32);
            buf.writeInt(r.color());
            buf.writeVarInt(r.kills()); buf.writeVarInt(r.deaths()); buf.writeVarInt(r.money() + 1);
            buf.writeBoolean(r.online());
        }
    }

    public static ArenaStatePacket decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        if (kind == 0) return NONE;
        String title = buf.readUtf(64);
        int seconds = buf.readVarInt() - 1;
        String timerLabel = buf.readUtf(64);
        String status = buf.readUtf(256);
        int n = Math.min(buf.readVarInt(), 64);
        List<ArenaMode.ScoreLine> scores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            scores.add(new ArenaMode.ScoreLine(buf.readInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));
        }
        int money = buf.readVarInt(), cls = buf.readVarInt();
        byte f = buf.readByte();
        int team = buf.readVarInt(), cd = buf.readVarInt(), kills = buf.readVarInt(), deaths = buf.readVarInt();
        int m = Math.min(buf.readVarInt(), 128);
        List<Mate> mates = new ArrayList<>(m);
        for (int i = 0; i < m; i++) mates.add(new Mate(buf.readUUID(), buf.readUtf(32), buf.readVarInt()));
        int mk = Math.min(buf.readVarInt(), 64);
        List<Marker> markers = new ArrayList<>(mk);
        for (int i = 0; i < mk; i++) {
            markers.add(new Marker(buf.readByte(), buf.readUtf(16), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readInt(), buf.readBoolean()));
        }
        int rb = Math.min(buf.readVarInt(), 128);
        List<Row> board = new ArrayList<>(rb);
        for (int i = 0; i < rb; i++) {
            board.add(new Row(buf.readUtf(32), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean()));
        }
        return new ArenaStatePacket(kind, title, seconds, timerLabel, status, scores, money, cls,
            (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0, team, cd, kills, deaths, mates, markers, board);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.provinggrounds.client.ClientArenaState.set(this));
        ctx.get().setPacketHandled(true);
    }
}
