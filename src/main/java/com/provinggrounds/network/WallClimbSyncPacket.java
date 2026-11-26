package com.provinggrounds.network;

import com.provinggrounds.core.WallClimb;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: WallClimbSyncPacket.java
 * 目的: 伺服器 → 全體客戶端：同步某位玩家正在翻爬柵欄／石牆的動畫與起迄狀態（供第三人稱與 F3+B 顯示）。
 */
public record WallClimbSyncPacket(int entityId, Vec3 start, Vec3 target, float yaw, int stowTicks, int duration) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        buf.writeFloat(yaw);
        buf.writeVarInt(stowTicks);
        buf.writeVarInt(duration);
    }

    public static WallClimbSyncPacket decode(FriendlyByteBuf buf) {
        return new WallClimbSyncPacket(
            buf.readVarInt(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readVarInt()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            int localId = mc.player != null ? mc.player.getId() : -1;
            WallClimb.applyClientSync(entityId, localId, start, target, yaw, stowTicks, duration);
        }));
        ctx.get().setPacketHandled(true);
    }
}
