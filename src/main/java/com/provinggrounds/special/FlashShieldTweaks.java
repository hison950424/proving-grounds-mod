package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: FlashShieldTweaks.java
 * 目的: lrtactical 閃光盾的調整（計畫書 6 A21；伺服器端）。
 *   1. 閃光效果時間減半：FlashShieldMixin 在閃光盾閃光的那一瞬間呼叫 begin()／end()，
 *      這段期間加上的「致盲」「耳鳴」效果（lrtactical:blind、lrtactical:deafened）在 end() 時換成一半時間（冷卻不變）。
 *      閃光手榴彈不受影響。
 *   2. 耐久減半：開服時把 lrtactical 伺服器設定 flash_shield_max_durability 減半一次（例如 150 → 75），
 *      做過就記在本模組設定 flash_shield_durability_halved_v1，之後不再動（管理員之後自己改的數值會保留）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class FlashShieldTweaks {

    private static final ResourceLocation BLIND = new ResourceLocation("lrtactical", "blind");
    private static final ResourceLocation DEAFENED = new ResourceLocation("lrtactical", "deafened");

    private record Pending(LivingEntity entity, MobEffectInstance effect) {}

    private static boolean recording;
    private static final List<Pending> PENDING = new ArrayList<>();

    private FlashShieldTweaks() {}

    public static void begin() {
        recording = true;
        PENDING.clear();
    }

    public static void end() {
        recording = false;
        for (Pending p : PENDING) {
            MobEffectInstance e = p.effect();
            MobEffectInstance cur = p.entity().getEffect(e.getEffect());
            if (cur == null) continue;
            p.entity().removeEffect(e.getEffect());
            p.entity().addEffect(new MobEffectInstance(e.getEffect(), Math.max(1, cur.getDuration() / 2), cur.getAmplifier(),
                cur.isAmbient(), cur.isVisible(), cur.showIcon()));
        }
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!recording || event.getEntity().level().isClientSide) return;
        MobEffect effect = event.getEffectInstance().getEffect();
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (BLIND.equals(id) || DEAFENED.equals(id)) PENDING.add(new Pending(event.getEntity(), event.getEffectInstance()));
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            Object value = Class.forName("me.xjqsh.lrtactical.config.ServerConfig").getField("FLASH_SHIELD_MAX_DURABILITY").get(null);
            var cfg = (net.minecraftforge.common.ForgeConfigSpec.ConfigValue<Integer>) value;
            var done = SpecialConfig.get().flashShieldHalved;
            if (!done.get()) {
                int half = Math.max(1, cfg.get() / 2);
                cfg.set(half);
                cfg.save();
                done.set(true);
                done.save();
                ProvingGroundsMod.LOGGER.info("Flash shield durability halved to {}.", half);
            }
            var done045 = SpecialConfig.get().flashShield045; // 使用者回饋：再改成當時的 45%
            if (!done045.get()) {
                int v = Math.max(1, Math.round(cfg.get() * 0.45f));
                cfg.set(v);
                cfg.save();
                done045.set(true);
                done045.save();
                ProvingGroundsMod.LOGGER.info("Flash shield durability set to 45%: {}.", v);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // lrtactical 沒裝或改版：不處理
        }
    }
}
