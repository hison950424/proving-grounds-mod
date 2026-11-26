package com.provinggrounds.network;

import com.provinggrounds.core.WallClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: WallClimbPacket.java
 * 目的: 客戶端 → 伺服器：玩家貼著柵欄／石牆按下空白鍵請求翻爬上去。
 */
public record WallClimbPacket(BlockPos wallPos, float yaw) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(wallPos);
        buf.writeFloat(yaw);
    }

    public static WallClimbPacket decode(FriendlyByteBuf buf) {
        return new WallClimbPacket(buf.readBlockPos(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) WallClimb.startServer(p, wallPos, yaw);
        });
        ctx.get().setPacketHandled(true);
    }
}
