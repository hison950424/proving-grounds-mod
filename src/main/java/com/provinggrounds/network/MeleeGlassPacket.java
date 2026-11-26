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
 * 目的: 客戶端 → 伺服器：拿近戰武器左鍵輕擊或右鍵重擊打到玻璃／玻璃片／鐵欄杆時打破（計畫書 6 A6 擴充）。
 *   冒險模式的客戶端不會送「開始挖方塊」，因此由 ClientMeleeGlass 在按下左／右鍵時傳送此封包。
 *   伺服器端驗證：手持近戰武器、距離 5.5 格內、目標為玻璃／鐵欄杆、該位置允許破壞（BlockProtection），
 *   通過且武器冷卻就緒後打破（不掉落，並由 BlockLedger 記錄於賽後／開局還原）。
 */
public record MeleeGlassPacket(BlockPos pos, boolean heavy) {

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();
    private static final int COOLDOWN_LIGHT = 10;
    private static final int COOLDOWN_HEAVY = 14;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(heavy);
    }

    public static MeleeGlassPacket decode(FriendlyByteBuf buf) {
        return new MeleeGlassPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || p.isSpectator() || !p.getMainHandItem().is(GunCompat.MELEE)) return;
            ServerLevel level = p.serverLevel();
            if (!level.isLoaded(pos) || p.getEyePosition().distanceToSqr(pos.getCenter()) > 5.5 * 5.5) return;
            BlockState state = level.getBlockState(pos);
            if (!BlockLedger.isGlass(state) || state.getDestroySpeed(level, pos) < 0) return;
            if (!BlockProtection.allowsDamage(level, pos)) return;
            long now = level.getGameTime();
            Long last = LAST.get(p.getUUID());
            int cd = heavy ? COOLDOWN_HEAVY : COOLDOWN_LIGHT;
            if (last != null && now - last < cd) return;
            if (!heavy && p.getAttackStrengthScale(0.5f) < 0.9f) return;
            LAST.put(p.getUUID(), now);
            BlockLedger.get(level.getServer()).recordGlass(level, pos, state);
            level.destroyBlock(pos, false, p);
        });
        ctx.get().setPacketHandled(true);
    }
}
