package com.provinggrounds.network;

import com.provinggrounds.core.WallClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: WallClimbPacket.java
 * 目的: 客戶端 → 伺服器：玩家在跳躍浮空時對前方不高於頭部的方塊再按一次空白鍵請求觸發翻爬，
 *   或於攀爬中按下後退／左／右主動通知伺服器取消攀爬。
 */
public record WallClimbPacket(BlockPos wallPos, float yaw) {

    /** 建立「取消攀爬」請求封包（沿用相同封包格式，以 yaw = NaN 區分）。 */
    public static WallClimbPacket cancel() {
        return new WallClimbPacket(BlockPos.ZERO, Float.NaN);
    }

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
            if (sender != null) {
                if (Float.isNaN(yaw)) WallClimb.cancelServer(sender);
                else WallClimb.startServer(sender, wallPos, yaw);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
