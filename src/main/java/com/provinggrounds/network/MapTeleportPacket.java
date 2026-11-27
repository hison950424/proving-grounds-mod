package com.provinggrounds.network;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: MapTeleportPacket.java
 * 目的: 客戶端 → 伺服器。管理員於大地圖／地圖預覽右鍵點選座標後請求傳送（僅限權限等級 2 以上管理員）。
 */
public record MapTeleportPacket(int x, int z) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
    }

    public static MapTeleportPacket decode(FriendlyByteBuf buf) {
        return new MapTeleportPacket(buf.readInt(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000) return;

            ServerLevel level = player.server.overworld();
            ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
            if (y <= level.getMinBuildHeight() + 1) {
                y = Math.max(level.getSeaLevel() + 10, 80);
            }
            int maxY = level.getMaxBuildHeight() - 2;
            for (int step = 0; step < 16 && y < maxY; step++) {
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos head = feet.above();
                if (!level.getBlockState(feet).blocksMotion() && !level.getBlockState(head).blocksMotion()) {
                    break;
                }
                y++;
            }

            player.fallDistance = 0.0f;
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("[PG] 已傳送至 (" + x + ", " + y + ", " + z + ")")
                .withStyle(ChatFormatting.GREEN));
        });
        ctx.get().setPacketHandled(true);
    }
}
