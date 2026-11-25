package com.provinggrounds.network;

import com.provinggrounds.core.TeamManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: PingPacket.java
 * 目的: 標點（使用者回饋）。客戶端 → 伺服器：玩家按標點鍵（預設 X），準星指著的位置。
 *   伺服器檢查距離後轉給自己與同隊的人（PingSyncPacket）；不同隊看不到。旁觀者、太遠（超過 512 格）的不轉。
 */
public record PingPacket(double x, double y, double z) {

    public static final double MAX_RANGE = 512;

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static PingPacket decode(FriendlyByteBuf buf) {
        return new PingPacket(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || p.isSpectator()) return;
            if (p.position().distanceToSqr(x, y, z) > MAX_RANGE * MAX_RANGE) return;
            PingSyncPacket sync = new PingSyncPacket(p.getUUID(), p.getGameProfile().getName(), x, y, z);
            for (ServerPlayer t : p.server.getPlayerList().getPlayers()) {
                if (t == p || (t.level() == p.level() && TeamManager.get().isSameTeam(t.getUUID(), p.getUUID()))) PGNetwork.sendTo(t, sync);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
