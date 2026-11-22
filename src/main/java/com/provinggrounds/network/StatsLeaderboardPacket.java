package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: StatsLeaderboardPacket.java
 * 目的: 伺服器 → 客戶端。全服大逃殺排行（計畫書 B5），伺服器算一次、所有看板共用；
 *       只送給站在看板附近的玩家，最多每 10 秒一次（資料有變動才送）。客戶端依玩家選的欄位排序、顯示前 10 名。
 */
public record StatsLeaderboardPacket(List<Entry> entries) {

    public record Entry(UUID id, String name, int wins, int games, int kills, int deaths, int assists, int level) {
        public double kd() { return deaths == 0 ? kills : (double) kills / deaths; }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUUID(e.id()); buf.writeUtf(e.name());
            buf.writeVarInt(e.wins()); buf.writeVarInt(e.games()); buf.writeVarInt(e.kills());
            buf.writeVarInt(e.deaths()); buf.writeVarInt(e.assists()); buf.writeVarInt(e.level());
        }
    }

    public static StatsLeaderboardPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new StatsLeaderboardPacket(list);
    }

    public void handle(java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.ClientStatsBoard.setLeaderboard(entries)));
        ctx.get().setPacketHandled(true);
    }
}
