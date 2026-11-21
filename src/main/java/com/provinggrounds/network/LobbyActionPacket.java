package com.provinggrounds.network;

import com.provinggrounds.core.LobbyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: LobbyActionPacket.java
 * 目的: 客戶端 → 伺服器。玩家在大廳選單上的操作（換隊、準備、管理員設定、開始…）。
 *       權限一律在伺服器端（LobbyManager.handle）檢查，客戶端無法繞過。
 */
public record LobbyActionPacket(LobbyManager.Action action, int arg, @Nullable UUID target) {

    public LobbyActionPacket(LobbyManager.Action action, int arg) {
        this(action, arg, null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(arg);
        buf.writeBoolean(target != null);
        if (target != null) buf.writeUUID(target);
    }

    public static LobbyActionPacket decode(FriendlyByteBuf buf) {
        LobbyManager.Action action = buf.readEnum(LobbyManager.Action.class);
        int arg = buf.readVarInt();
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new LobbyActionPacket(action, arg, target);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) LobbyManager.get().handle(player, action, arg, target);
        });
        ctx.get().setPacketHandled(true);
    }
}
