package com.provinggrounds.network;

import com.provinggrounds.client.ClientBRState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: KillFeedPacket.java
 * 目的: 擊殺提示（HUD 右下角，淡出）。killer 為空字串代表非玩家致死（縮圈、摔落等）。
 *       顏色為 ChatFormatting 的 RGB 值。
 */
public record KillFeedPacket(String killer, int killerColor, String victim, int victimColor) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(killer, 64);
        buf.writeInt(killerColor);
        buf.writeUtf(victim, 64);
        buf.writeInt(victimColor);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf.readUtf(64), buf.readInt(), buf.readUtf(64), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientBRState.addKill(this));
        ctx.get().setPacketHandled(true);
    }
}
