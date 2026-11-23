package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: SpecialUsePacket.java
 * 目的: 客戶端 → 伺服器。玩家按下「使用特殊物品」鍵（預設 G）。伺服器驗證後使用特殊物品欄裡的物品（SpecialUse）。
 */
public record SpecialUsePacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static SpecialUsePacket decode(FriendlyByteBuf buf) { return new SpecialUsePacket(); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) com.provinggrounds.special.SpecialUse.onKey(p); });
        ctx.get().setPacketHandled(true);
    }
}
