package com.provinggrounds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: SpawnPointsPacket.java
 * 目的: 伺服器 → 客戶端。顯示／隱藏箱子生成點（計畫書 B9），只送給切換的那位管理員；點位變動時重送。
 */
public record SpawnPointsPacket(boolean show, List<BlockPos> points) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(show);
        buf.writeVarInt(points.size());
        for (BlockPos p : points) buf.writeBlockPos(p);
    }

    public static SpawnPointsPacket decode(FriendlyByteBuf buf) {
        boolean show = buf.readBoolean();
        int n = buf.readVarInt();
        List<BlockPos> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
        return new SpawnPointsPacket(show, list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.SpawnPointRenderer.set(show, points)));
        ctx.get().setPacketHandled(true);
    }
}
