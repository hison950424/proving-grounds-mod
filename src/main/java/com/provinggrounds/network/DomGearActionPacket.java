package com.provinggrounds.network;

import com.provinggrounds.special.DomGear;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: DomGearActionPacket.java
 * 目的: 客戶端 → 伺服器。大廳選佔點的整場特殊物品（type = SpecialType 的順序；計畫書 8）。規則在 DomGear 檢查。
 */
public record DomGearActionPacket(int type) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(type);
    }

    public static DomGearActionPacket decode(FriendlyByteBuf buf) {
        return new DomGearActionPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) DomGear.setChoice(p, type);
        });
        ctx.get().setPacketHandled(true);
    }
}
