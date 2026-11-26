package com.provinggrounds.network;

import com.provinggrounds.core.WallClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: WallClimbPacket.java
 * 目的: 客戶端 → 伺服器：玩家在跳躍浮空時對前方不高於頭部的方塊再按一次空白鍵，請求觸發翻爬。
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
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) WallClimb.startServer(sender, wallPos, yaw);
        });
        ctx.get().setPacketHandled(true);
    }
}
