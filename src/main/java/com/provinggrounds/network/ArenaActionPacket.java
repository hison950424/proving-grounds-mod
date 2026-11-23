package com.provinggrounds.network;

import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.modes.arena.ArenaMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: ArenaActionPacket.java
 * 目的: 客戶端 → 伺服器。商店介面的操作：購買（arg = 商品編號）、換職業（arg = 職業順序）、
 *   轉帳給隊友（arg = 金額，0 = 全部；target = 隊友）。規則都在伺服器檢查。
 */
public record ArenaActionPacket(Action action, int arg, UUID target) {

    public enum Action { BUY, SET_CLASS, TRANSFER }

    private static final UUID NONE = new UUID(0, 0);

    public ArenaActionPacket(Action action, int arg) { this(action, arg, NONE); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(arg);
        buf.writeUUID(target);
    }

    public static ArenaActionPacket decode(FriendlyByteBuf buf) {
        return new ArenaActionPacket(buf.readEnum(Action.class), buf.readVarInt(), buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            ArenaMode m = ArenaManager.current();
            if (p == null || m == null || m.isEnding()) return;
            switch (action) {
                case BUY -> m.buy(p, arg);
                case SET_CLASS -> m.setClass(p, arg);
                case TRANSFER -> m.transfer(p, target, arg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
