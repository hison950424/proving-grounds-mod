package com.provinggrounds.network;

import com.provinggrounds.client.ClientBRState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: BRStatePacket.java
 * 目的: 伺服器每 5 tick 送給每位玩家的 BR 狀態（HUD、縮圈牆、跳躍修正用）。
 *       phase / selfState 的數值對照見 {@link ClientBRState}。
 */
public record BRStatePacket(
    byte phase,           // 0 無, 1 準備, 2 飛機, 3 進行中, 4 結算
    int seconds,          // 準備倒數 / 距離下次縮圈開始或結束的秒數
    boolean shrinking,
    byte stage,           // 目前階段（1 起算），0 = 尚未開始
    byte totalStages,
    float cx, float cz, float radius,
    float nextCx, float nextCz, float nextRadius, // nextRadius < 0 表示沒有下一圈
    short alivePlayers,
    short aliveTeams,
    byte selfState,       // 0 非參賽, 1 飛機上, 2 自由落體, 3 開傘, 4 地面存活, 5 已淘汰
    float jumpHeight      // >0 時客戶端套用 BR 跳躍修正
) {

    public static BRStatePacket none() {
        return new BRStatePacket((byte) 0, 0, false, (byte) 0, (byte) 0, 0, 0, -1, 0, 0, -1,
            (short) 0, (short) 0, (byte) 0, 0f);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase);
        buf.writeVarInt(seconds);
        buf.writeBoolean(shrinking);
        buf.writeByte(stage);
        buf.writeByte(totalStages);
        buf.writeFloat(cx); buf.writeFloat(cz); buf.writeFloat(radius);
        buf.writeFloat(nextCx); buf.writeFloat(nextCz); buf.writeFloat(nextRadius);
        buf.writeShort(alivePlayers);
        buf.writeShort(aliveTeams);
        buf.writeByte(selfState);
        buf.writeFloat(jumpHeight);
    }

    public static BRStatePacket decode(FriendlyByteBuf buf) {
        return new BRStatePacket(buf.readByte(), buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readByte(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readShort(), buf.readShort(), buf.readByte(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientBRState.apply(this));
        ctx.get().setPacketHandled(true);
    }
}
