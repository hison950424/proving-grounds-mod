package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 檔案: SilentSync.java
 * 目的: 把「靜音靴」效果也告訴附近的其他玩家（伺服器端，計畫書 6 A15）。
 *   原版只把藥水效果送給自己，別人的客戶端不知道這個人穿著靜音靴，就會照樣畫奔跑時的腳步粒子。
 *   效果加上／移除／到期時送給所有正在追蹤這個玩家的人；有人開始看到他時（StartTracking）也補送一次。
 *   客戶端收到後 hasEffect 就是 true，EntitySpecialMixin 依此不畫腳步粒子（隱形斗篷是原版隱形，本來就同步）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class SilentSync {

    private SilentSync() {}

    private static boolean isSilent(MobEffectInstance e) {
        return e != null && e.getEffect() == SpecialRegistry.SILENT.get();
    }

    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isSilent(event.getEffectInstance())) return;
        PacketDistributor.TRACKING_ENTITY.with(() -> p).send(new ClientboundUpdateMobEffectPacket(p.getId(), event.getEffectInstance()));
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer p && isSilent(event.getEffectInstance())) sendRemove(p);
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof ServerPlayer p && isSilent(event.getEffectInstance())) sendRemove(p);
    }

    private static void sendRemove(ServerPlayer p) {
        PacketDistributor.TRACKING_ENTITY.with(() -> p).send(new ClientboundRemoveMobEffectPacket(p.getId(), SpecialRegistry.SILENT.get()));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        MobEffectInstance e = target.getEffect(SpecialRegistry.SILENT.get());
        if (e != null) viewer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), e));
    }
}
