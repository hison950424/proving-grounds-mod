package com.provinggrounds.network;

import com.provinggrounds.modes.br.MatchResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: MatchResultPacket.java
 * 目的: 伺服器 → 客戶端。一場 BR 的結算數據（計畫書 B4）。結算時送給所有線上玩家（open = true 會開啟畫面），
 *       /pg result 也用這個封包把最近一場再送一次。客戶端會保留最近一場。
 */
public record MatchResultPacket(MatchResult result, boolean open) {

    public void encode(FriendlyByteBuf buf) {
        result.encode(buf);
        buf.writeBoolean(open);
    }

    public static MatchResultPacket decode(FriendlyByteBuf buf) {
        return new MatchResultPacket(MatchResult.decode(buf), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.MatchResultScreen.receive(result, open)));
        ctx.get().setPacketHandled(true);
    }
}
