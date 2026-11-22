package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: BackpackSyncPacket.java
 * 目的: 伺服器 → 客戶端。玩家的背包欄內容一變就送（B3）。
 *   原版只在「物品欄畫面」是目前開著的畫面時才同步背包欄那一格；開著箱子時從箱子 Shift 拿到背包，
 *   客戶端不知道容量變了，鎖格遮罩要關掉畫面再打開才會更新。這個封包讓它立刻更新。
 */
public record BackpackSyncPacket(ItemStack stack) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(stack);
    }

    public static BackpackSyncPacket decode(FriendlyByteBuf buf) {
        return new BackpackSyncPacket(buf.readItem());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.ClientBackpackSync.apply(stack)));
        ctx.get().setPacketHandled(true);
    }
}
