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

    public enum Action { BUY, SET_CLASS, TRANSFER, OPEN_TRASH, BUY_SET } // BUY_SET：arg = 防具材質（ArenaMode.ARMOR_SETS 的順序；計畫書 7 A8）

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
                case OPEN_TRASH -> openTrash(p);
                case BUY_SET -> m.buySet(p, arg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 商店的垃圾桶（計畫書 6 B8）：一個 2 排的暫時箱子，把要丟的東西放進去；關掉畫面時裡面的東西才真的刪掉，
     * 關掉之前都可以拿回來。箱子是臨時建立的（SimpleContainer），不存在世界裡，關掉就跟著消失。
     */
    private static void openTrash(ServerPlayer p) {
        net.minecraft.world.SimpleContainer bin = new net.minecraft.world.SimpleContainer(18);
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) ->
            new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inv, bin, 2),
            net.minecraft.network.chat.Component.literal("垃圾桶（關閉後刪除裡面的東西）")));
    }
}
