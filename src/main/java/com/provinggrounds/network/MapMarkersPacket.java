package com.provinggrounds.network;

import com.provinggrounds.client.ClientMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: MapMarkersPacket.java
 * 目的: 伺服器 → 客戶端（遊戲中每 5 tick）。地圖上要畫的標記：
 *       隊友（含倒地）、隊友視野內看到的敵人（最後看到的位置，保留幾秒）、空投。
 *       不屬於任何參賽隊伍的觀戰者會收到所有參賽者的位置。自己的位置客戶端自己知道，不在清單裡。
 */
public record MapMarkersPacket(List<Marker> markers) {

    public static final byte TEAMMATE = 0, TEAMMATE_DOWNED = 1, ENEMY = 2, AIRDROP = 3, AIRDROP_FALLING = 4, PLAYER = 5;

    /** color：隊伍顏色（RGB）；yaw：面向（度）；age：敵人標記距離最後看到幾 tick。 */
    public record Marker(byte kind, float x, float z, float yaw, int color, String name, int age) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(markers.size());
        for (Marker m : markers) {
            buf.writeByte(m.kind());
            buf.writeFloat(m.x());
            buf.writeFloat(m.z());
            buf.writeFloat(m.yaw());
            buf.writeInt(m.color());
            buf.writeUtf(m.name(), 32);
            buf.writeVarInt(m.age());
        }
    }

    public static MapMarkersPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<Marker> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Marker(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(),
                buf.readUtf(32), buf.readVarInt()));
        }
        return new MapMarkersPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientMapData.setMarkers(this));
        ctx.get().setPacketHandled(true);
    }
}
