package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: BRBoardPacket.java
 * 目的: 伺服器 → 客戶端：大逃殺戰績面板的資料（計畫書 6 B1），每秒送一次。
 *   每一列：玩家名字、隊伍顏色、擊殺、狀態（0 存活、1 倒地、2 陣亡）、被復活祭壇救回幾次。
 */
public record BRBoardPacket(List<Row> rows) {

    public static final byte ALIVE = 0, DOWNED = 1, DEAD = 2;

    public record Row(String name, int color, int kills, byte state, int altarRevived) {}

    /** 客戶端最近收到的資料（沒有大逃殺時是空的）。 */
    private static volatile List<Row> latest = List.of();

    public static List<Row> latest() { return latest; }

    public static void clear() { latest = List.of(); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUtf(r.name(), 32);
            buf.writeInt(r.color());
            buf.writeVarInt(r.kills());
            buf.writeByte(r.state());
            buf.writeVarInt(r.altarRevived());
        }
    }

    public static BRBoardPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 200);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readUtf(32), buf.readInt(), buf.readVarInt(), buf.readByte(), buf.readVarInt()));
        return new BRBoardPacket(rows);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> latest = List.copyOf(rows));
        ctx.get().setPacketHandled(true);
    }
}
