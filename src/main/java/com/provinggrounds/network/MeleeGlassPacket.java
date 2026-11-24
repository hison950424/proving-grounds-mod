package com.provinggrounds.network;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.BlockProtection;
import com.provinggrounds.data.BlockLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: MeleeGlassPacket.java
 * 目的: 客戶端 → 伺服器：拿近戰武器左鍵打到玻璃（計畫書 6 A6）。
 *   冒險模式的客戶端本來就不會送「開始挖方塊」，所以由客戶端（ClientMeleeGlass）另外送這個封包，
 *   伺服器檢查：手上真的是近戰武器、距離 5 格內、那一格是玻璃、這裡允許破壞（跟子彈打破玻璃同一套規則 BlockProtection），
 *   通過就打破（不掉落，比賽外打破的玻璃由 BlockLedger 記錄、之後修回）。
 */
public record MeleeGlassPacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public static MeleeGlassPacket decode(FriendlyByteBuf buf) { return new MeleeGlassPacket(buf.readBlockPos()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || p.isSpectator() || !p.getMainHandItem().is(GunCompat.MELEE)) return;
            ServerLevel level = p.serverLevel();
            if (!level.isLoaded(pos) || p.getEyePosition().distanceToSqr(pos.getCenter()) > 5.5 * 5.5) return;
            BlockState state = level.getBlockState(pos);
            if (!BlockLedger.isGlass(state) || state.getDestroySpeed(level, pos) < 0) return;
            if (!BlockProtection.allowsDamage(level, pos)) return;
            BlockLedger.get(level.getServer()).recordGlass(level, pos, state);
            level.destroyBlock(pos, false, p); // 有破碎音效與粒子，不掉落
        });
        ctx.get().setPacketHandled(true);
    }
}
