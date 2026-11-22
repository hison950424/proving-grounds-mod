package com.provinggrounds.network;

import com.provinggrounds.modes.br.HotbarRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: WheelSelectPacket.java
 * 目的: 客戶端 → 伺服器。補品／投擲物輪盤選好種類（計畫書 B2）。
 *   target = 3（第 4 格，補品）或 4（第 5 格，投擲物）；fromSlot = 選中的那一疊目前在身上第幾格。
 *   伺服器驗證規則有效、格子裡真的是那一類，才搬移並切換到那一格（HotbarRules.wheelSelect）。
 */
public record WheelSelectPacket(int target, int fromSlot) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(target);
        buf.writeByte(fromSlot);
    }

    public static WheelSelectPacket decode(FriendlyByteBuf buf) {
        return new WheelSelectPacket(buf.readByte(), buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && HotbarRules.active(p)) HotbarRules.wheelSelect(p, target, fromSlot);
        });
        ctx.get().setPacketHandled(true);
    }
}
