package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: QuietWalkPacket.java
 * 目的: 客戶端 → 伺服器。玩家開始／停止靜走（按住「靜走」鍵，預設 Shift）。伺服器用來決定別人聽到的腳步聲大小。
 */
public record QuietWalkPacket(boolean quiet) {

    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(quiet); }

    public static QuietWalkPacket decode(FriendlyByteBuf buf) { return new QuietWalkPacket(buf.readBoolean()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null) return;
            com.provinggrounds.core.Footsteps.setQuiet(p.getUUID(), quiet);
            if (quiet && p.isSprinting()) p.setSprinting(false);
        });
        ctx.get().setPacketHandled(true);
    }
}
