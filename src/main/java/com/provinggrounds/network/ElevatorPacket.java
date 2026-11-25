package com.provinggrounds.network;

import com.provinggrounds.block.ElevatorEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: ElevatorPacket.java
 * 目的: 客戶端 → 伺服器（計畫書 7 A5）。玩家站在電梯方塊上「剛按下」跳躍鍵（up = true）或蹲下鍵（up = false）。
 *   以前伺服器用「離地往上移動」猜跳躍、用蹲下狀態判斷，會被傳送後的位置回報、自動跳躍、走上台階誤觸發；
 *   改成客戶端只在按鍵按下的那一下送一次，伺服器再確認站在電梯上與冷卻。
 */
public record ElevatorPacket(boolean up) {

    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(up); }

    public static ElevatorPacket decode(FriendlyByteBuf buf) { return new ElevatorPacket(buf.readBoolean()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ElevatorEvents.onKey(p, up);
        });
        ctx.get().setPacketHandled(true);
    }
}
