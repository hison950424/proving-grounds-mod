package com.provinggrounds.network;

import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: SkydiveKeyPacket.java
 * 目的: 客戶端 → 伺服器。跳傘期間玩家按下跳躍鍵（預設空白鍵）。
 *       跳出飛機後伺服器收不到空白鍵輸入，所以由客戶端偵測按下的瞬間送過來；
 *       伺服器依目前狀態決定是跳出、開傘還是收傘，並檢查冷卻（計畫書 B1）。
 */
public record SkydiveKeyPacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static SkydiveKeyPacket decode(FriendlyByteBuf buf) {
        return new SkydiveKeyPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) BattleRoyaleMode.get().onSkydiveKey(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
