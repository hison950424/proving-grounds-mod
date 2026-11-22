package com.provinggrounds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: SpawnPointFlashPacket.java
 * 目的: 伺服器 → 客戶端。剛被「箱子生成點刪除工具」刪掉的位置，客戶端閃紅色方框 3 秒（可穿牆看到），
 *       讓管理員知道刪掉的是哪一個。
 */
public record SpawnPointFlashPacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static SpawnPointFlashPacket decode(FriendlyByteBuf buf) {
        return new SpawnPointFlashPacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.ui.SpawnPointRenderer.flash(pos)));
        ctx.get().setPacketHandled(true);
    }
}
