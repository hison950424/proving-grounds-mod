package com.provinggrounds.network;

import com.provinggrounds.client.ClientMenuData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: MenuDataPacket.java
 * 目的: 伺服器 → 客戶端。主選單需要的資料：玩家戰績、等級、地圖清單、是否為管理員、遊戲狀態。
 */
public record MenuDataPacket(
    int kills, int deaths, int assists, int wins,
    long xp, int level, long nextLevelXp,
    boolean isOp, boolean inLobby, String status,
    List<MapEntry> maps,
    List<String> venues // 場地選擇（名稱，順序同 VenueManager.visible）
) {

    public record MapEntry(int id, String name, int lootPoints) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(kills);
        buf.writeVarInt(deaths);
        buf.writeVarInt(assists);
        buf.writeVarInt(wins);
        buf.writeVarLong(xp);
        buf.writeVarInt(level);
        buf.writeVarLong(nextLevelXp);
        buf.writeBoolean(isOp);
        buf.writeBoolean(inLobby);
        buf.writeUtf(status, 128);
        buf.writeVarInt(maps.size());
        for (MapEntry m : maps) {
            buf.writeVarInt(m.id());
            buf.writeUtf(m.name(), 64);
            buf.writeVarInt(m.lootPoints());
        }
        buf.writeVarInt(venues.size());
        for (String v : venues) buf.writeUtf(v, 64);
    }

    public static MenuDataPacket decode(FriendlyByteBuf buf) {
        int kills = buf.readVarInt(), deaths = buf.readVarInt(), assists = buf.readVarInt(), wins = buf.readVarInt();
        long xp = buf.readVarLong();
        int level = buf.readVarInt();
        long next = buf.readVarLong();
        boolean isOp = buf.readBoolean(), inLobby = buf.readBoolean();
        String status = buf.readUtf(128);
        int n = Math.min(buf.readVarInt(), 256);
        List<MapEntry> maps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) maps.add(new MapEntry(buf.readVarInt(), buf.readUtf(64), buf.readVarInt()));
        int vn = Math.min(buf.readVarInt(), 64);
        List<String> venues = new ArrayList<>(vn);
        for (int i = 0; i < vn; i++) venues.add(buf.readUtf(64));
        return new MenuDataPacket(kills, deaths, assists, wins, xp, level, next, isOp, inLobby, status, maps, venues);
    }

    public double kd() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientMenuData.set(this));
        ctx.get().setPacketHandled(true);
    }
}
