package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: SpecialSlotSyncPacket.java
 * 目的: 伺服器 → 客戶端。特殊物品欄的內容一變就送（原版只在物品欄畫面開著時才同步那一格）。
 */
public record SpecialSlotSyncPacket(ItemStack stack) {

    public void encode(FriendlyByteBuf buf) { buf.writeItem(stack); }

    public static SpecialSlotSyncPacket decode(FriendlyByteBuf buf) { return new SpecialSlotSyncPacket(buf.readItem()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.special.client.ClientSpecial.applySlot(stack)));
        ctx.get().setPacketHandled(true);
    }
}
