package com.provinggrounds.network;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.BlockProtection;
import com.provinggrounds.data.BlockLedger;
import com.provinggrounds.special.ActionLock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: MeleeGlassPacket.java
 * 目的: 拿近戰武器打破玻璃／玻璃片／鐵欄杆（計畫書 6 A6 擴充）。
 *   - lrtactical 刀械：由伺服器端 MeleeGlassMixin 在每把刀各自的真實揮刀命中幀（IMeleeWeapon.attack）
 *     直接呼叫 tryBreakGlassAtAim，因此完全依照該刀的拔刀時間、左／右鍵與連段揮刀延遲與冷卻判定。
 *   - 其他非 lrtactical 的 #provinggrounds:melee 近戰武器：由 ClientMeleeGlass 在點擊時傳送此封包作為後備路徑。
 *   伺服器端驗證：手持近戰武器、未被動作鎖定、目標為玻璃／鐵欄杆、該位置允許破壞（BlockProtection），
 *   通過後打破（不掉落，並由 BlockLedger 記錄於賽後／開局還原）。
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

    /** 依玩家目前準星視線射線檢查並破壞範圍內的玻璃／玻璃片／鐵欄杆（供 lrtactical 真實命中幀呼叫）。 */
    public static void tryBreakGlassAtAim(ServerPlayer p) {
        if (p == null || p.isSpectator() || ActionLock.blocksActions(p)) return;
        if (!p.getMainHandItem().is(GunCompat.MELEE)) return;
        double reach = p.isCreative() ? 5.0 : 4.5;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(reach));
        BlockHitResult hit = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        tryBreakGlass(p, hit.getBlockPos());
    }

    private static boolean tryBreakGlass(ServerPlayer p, BlockPos targetPos) {
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(targetPos)) return false;
        BlockState state = level.getBlockState(targetPos);
        if (!BlockLedger.isGlass(state) || state.getDestroySpeed(level, targetPos) < 0) return false;
        if (!BlockProtection.allowsGlassDamage(level, targetPos)) return false;
        BlockLedger.get(level.getServer()).recordGlass(level, targetPos, state);
        com.provinggrounds.modes.arena.ArenaManager.onGlassBroken(level, targetPos, state);
        return level.destroyBlock(targetPos, false, p);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || p.isSpectator() || ActionLock.blocksActions(p)) return;
            if (!p.getMainHandItem().is(GunCompat.MELEE) || GunCompat.isLrtacticalMelee(p.getMainHandItem())) return;
            ServerLevel level = p.serverLevel();
            if (!level.isLoaded(pos) || p.getEyePosition().distanceToSqr(pos.getCenter()) > 5.5 * 5.5) return;
            long now = level.getGameTime();
            Long last = LAST.get(p.getUUID());
            int cd = heavy ? COOLDOWN_HEAVY : COOLDOWN_LIGHT;
            if (last != null && now - last < cd) return;
            if (!heavy && p.getAttackStrengthScale(0.5f) < 0.9f) return;
            if (tryBreakGlass(p, pos)) {
                LAST.put(p.getUUID(), now);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
