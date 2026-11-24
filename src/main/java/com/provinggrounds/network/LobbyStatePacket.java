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
    int blockDamage, // 大逃殺：炸毀方塊（0 關、1 全部、2 只有槍械、3 只有投擲物）
    int weaponModeOrdinal, // 槍械模式（WeaponMode）
    int gameModeOrdinal, // 大廳選的模式（GameManager.GameMode）
    com.provinggrounds.modes.arena.ArenaManager.Settings arena, // 團隊死鬥／佔點／槍王之王設定
    int[] customCircle, // 自訂縮圈：階段數、間隔、速度 × 10、開場等待、最後一圈 %（計畫書 6 D2）
    List<Entry> players
) {

    /** team：-1 = 未分隊（開局時是孤狼），0 = 孤狼，1… = 隊伍編號。 */
    /** cls：大廳預選的職業（ArenaClass 的順序；團隊死鬥、佔點用）。 */
    public record Entry(UUID id, String name, int team, boolean ready, boolean spectator, boolean joinPending, boolean op, int cls) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inLobby);
        buf.writeVarInt(mapId);
        buf.writeVarInt(presetOrdinal);
        buf.writeVarInt(variantOrdinal);
        buf.writeVarInt(teamModeOrdinal);
        buf.writeVarInt(teamCount);
        buf.writeVarInt(countdownSeconds);
        buf.writeBoolean(special);
        buf.writeVarInt(blockDamage);
        buf.writeVarInt(weaponModeOrdinal);
        buf.writeVarInt(gameModeOrdinal);
        buf.writeVarInt(arena.tdmKills());
        buf.writeVarInt(arena.tdmMinutes());
        buf.writeVarInt(arena.domScore());
        buf.writeVarInt(arena.gkMultiplier());
        buf.writeVarIntArray(customCircle);
        buf.writeVarInt(players.size());
        for (Entry e : players) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 32);
            buf.writeVarInt(e.team());
            byte flags = (byte) ((e.ready() ? 1 : 0) | (e.spectator() ? 2 : 0) | (e.joinPending() ? 4 : 0) | (e.op() ? 8 : 0));
            buf.writeByte(flags);
            buf.writeVarInt(e.cls());
        }
    }

    public static LobbyStatePacket decode(FriendlyByteBuf buf) {
        boolean inLobby = buf.readBoolean();
        int map = buf.readVarInt(), preset = buf.readVarInt(), variant = buf.readVarInt();
        int teamMode = buf.readVarInt(), teamCount = buf.readVarInt(), countdown = buf.readVarInt();
        boolean special = buf.readBoolean();
        int blockDamage = buf.readVarInt();
        int weapon = buf.readVarInt();
        int gameMode = buf.readVarInt();
        var arena = new com.provinggrounds.modes.arena.ArenaManager.Settings(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int[] customCircle = buf.readVarIntArray(8);
        int n = Math.min(buf.readVarInt(), 1000);
        List<Entry> players = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(32);
            int team = buf.readVarInt();
            byte f = buf.readByte();
            int cls = buf.readVarInt();
            players.add(new Entry(id, name, team, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0, cls));
        }
        return new LobbyStatePacket(inLobby, map, preset, variant, teamMode, teamCount, countdown, special, blockDamage, weapon, gameMode, arena, customCircle, players);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLobbyData.set(this));
        ctx.get().setPacketHandled(true);
    }
}
