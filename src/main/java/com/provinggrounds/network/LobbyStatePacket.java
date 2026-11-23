package com.provinggrounds.network;

import com.provinggrounds.client.ClientLobbyData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: LobbyStatePacket.java
 * 目的: 伺服器 → 客戶端。大廳目前狀態（管理員選的設定、分隊方式、隊伍數、強制開始倒數、
 *       每位玩家的隊伍／準備／觀戰）。有變動就廣播給所有人，選單即時更新。
 */
public record LobbyStatePacket(
    boolean inLobby, int mapId, int presetOrdinal, int variantOrdinal,
    int teamModeOrdinal, int teamCount, int countdownSeconds,
    boolean special, // 特殊物品模式
    int weaponModeOrdinal, // 槍械模式（WeaponMode）
    List<Entry> players
) {

    /** team：-1 = 未分隊（開局時是孤狼），0 = 孤狼，1… = 隊伍編號。 */
    public record Entry(UUID id, String name, int team, boolean ready, boolean spectator, boolean joinPending, boolean op) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inLobby);
        buf.writeVarInt(mapId);
        buf.writeVarInt(presetOrdinal);
        buf.writeVarInt(variantOrdinal);
        buf.writeVarInt(teamModeOrdinal);
        buf.writeVarInt(teamCount);
        buf.writeVarInt(countdownSeconds);
        buf.writeBoolean(special);
        buf.writeVarInt(weaponModeOrdinal);
        buf.writeVarInt(players.size());
        for (Entry e : players) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 32);
            buf.writeVarInt(e.team());
            byte flags = (byte) ((e.ready() ? 1 : 0) | (e.spectator() ? 2 : 0) | (e.joinPending() ? 4 : 0) | (e.op() ? 8 : 0));
            buf.writeByte(flags);
        }
    }

    public static LobbyStatePacket decode(FriendlyByteBuf buf) {
        boolean inLobby = buf.readBoolean();
        int map = buf.readVarInt(), preset = buf.readVarInt(), variant = buf.readVarInt();
        int teamMode = buf.readVarInt(), teamCount = buf.readVarInt(), countdown = buf.readVarInt();
        boolean special = buf.readBoolean();
        int weapon = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 1000);
        List<Entry> players = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(32);
            int team = buf.readVarInt();
            byte f = buf.readByte();
            players.add(new Entry(id, name, team, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0));
        }
        return new LobbyStatePacket(inLobby, map, preset, variant, teamMode, teamCount, countdown, special, weapon, players);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLobbyData.set(this));
        ctx.get().setPacketHandled(true);
    }
}
