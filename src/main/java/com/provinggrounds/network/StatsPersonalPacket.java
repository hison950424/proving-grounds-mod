package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: StatsPersonalPacket.java
 * 目的: 伺服器 → 客戶端。玩家自己的大逃殺累計數據（計畫書 B5）。登入、打完一場、升級時各送一次給本人，
 *       客戶端存起來，所有數據看板的「個人數據」畫面都用這份，看板再多也不增加封包。
 */
public record StatsPersonalPacket(String name, int level, long xp, long xpToNext, int games, int wins, int kills, int deaths,
                                  int assists, int bestKills, int rankWins, int rankKills) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeVarInt(level);
        buf.writeVarLong(xp);
        buf.writeVarLong(xpToNext);
        buf.writeVarInt(games); buf.writeVarInt(wins); buf.writeVarInt(kills); buf.writeVarInt(deaths);
        buf.writeVarInt(assists); buf.writeVarInt(bestKills); buf.writeVarInt(rankWins); buf.writeVarInt(rankKills);
    }

    public static StatsPersonalPacket decode(FriendlyByteBuf buf) {
        return new StatsPersonalPacket(buf.readUtf(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.ClientStatsBoard.setPersonal(this)));
        ctx.get().setPacketHandled(true);
    }

    public double kd() { return deaths == 0 ? kills : (double) kills / deaths; }
}
