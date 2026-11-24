package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientSounds.java
 * 目的: 調整其他模組的音效音量（只在客戶端，計畫書 4 的 A7）。
 *   煙霧彈消散聲 lrtactical:entity.grenade.smoke.end → 音量乘上客戶端設定 smoke_dissipate_volume（預設 0.5）。
 *   圈外傷害的受傷聲（原版「世界邊界外」傷害類型）→ 音量 25%（降低 75%）。傷害類型由 DamageSoundMixin 在播聲音前記下。
 *   其他聲音完全不動。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSounds {

    private static final ResourceLocation SMOKE_END = new ResourceLocation("lrtactical", "entity.grenade.smoke.end");

    /** 客戶端正在處理的受傷事件的傷害來源（DamageSoundMixin 設定，只在渲染執行緒用）。 */
    public static volatile net.minecraft.world.damagesource.DamageSource currentDamage;
    private static final float BORDER_HURT_VOLUME = 0.25f;

    private ClientSounds() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null) return;
        double mul;
        var dmg = currentDamage;
        if (dmg != null && dmg.is(net.minecraft.world.damagesource.DamageTypes.OUTSIDE_BORDER)) {
            mul = BORDER_HURT_VOLUME; // 圈外傷害的受傷聲
        } else {
            if (!SMOKE_END.equals(s.getLocation())) return;
            try { mul = PGClientConfig.CLIENT.smokeDissipateVolume.get(); }
            catch (IllegalStateException e) { mul = 0.5; }
        }
        if (mul >= 0.999) return;
        if (mul <= 0) { event.setSound(null); return; }
        s.resolve(net.minecraft.client.Minecraft.getInstance().getSoundManager()); // 音量要在解析後才拿得到
        event.setSound(new SimpleSoundInstance(s.getLocation(), s.getSource(), (float) (s.getVolume() * mul), s.getPitch(),
            SoundInstance.createUnseededRandom(), s.isLooping(), s.getDelay(), s.getAttenuation(),
            s.getX(), s.getY(), s.getZ(), s.isRelative()));
    }
}
