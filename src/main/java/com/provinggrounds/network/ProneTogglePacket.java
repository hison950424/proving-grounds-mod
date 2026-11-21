package com.provinggrounds.network;

import com.provinggrounds.core.ProneManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: ProneTogglePacket.java
 * 目的: 客戶端 → 伺服器。玩家按下趴下鍵（切換趴下／站起）。能不能趴、冷卻都由伺服器判斷。
 */
public record ProneTogglePacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static ProneTogglePacket decode(FriendlyByteBuf buf) {
        return new ProneTogglePacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ProneManager.toggle(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
