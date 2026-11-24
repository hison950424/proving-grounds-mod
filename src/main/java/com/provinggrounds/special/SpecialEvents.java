package com.provinggrounds.special;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SpecialFxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * 檔案: SpecialEvents.java
 * 目的: 特殊物品系統的事件（伺服器端）：
 *   - 每 tick：發光引擎、世界裡的效果（SpecialWorld）、每位玩家的狀態（不死圖騰、擊退盾、毒氣的咳嗽與失去方向感）。
 *   - 受到傷害：護盾泡泡（第一下完全擋掉，給額外生命值與加速）、擊退盾（擋前方的傷害）。
 *   - 彈簧鞋衝刺後不受摔傷。
 *   - 斷線、登入：擊退盾一律還原。
 *   - 指令：/pg special give <物品ID>、/pg special simulate <次數>、/pg special list（管理員）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class SpecialEvents {

    private SpecialEvents() {}

    /** 護盾泡泡觸發的 tick（同一 tick 內的其他傷害一起擋掉）。 */
    private static final java.util.Map<java.util.UUID, Long> BUBBLE_POPPED = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GlowManager.tick(event.getServer());
        SpecialWorld.tick(event.getServer());
    }

    /** 正在咳的玩家 → 這一陣還剩幾聲。 */
    private static final java.util.Map<java.util.UUID, Integer> COUGH_FITS = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Totem.tick(p);
        com.provinggrounds.core.Hitbox.record(p); // 精準命中的位置記錄（延遲補償用）
        KnockbackShield.tick(p);
        if (p.tickCount % 20 == 0) KnockbackShield.sanity(p);
        if (p.hasEffect(SpecialRegistry.GASSED.get())) {
            var r = p.getRandom();
            if (r.nextInt(45) == 0) { // 不定時劇烈咳嗽：大家都聽得到，本人視角先上揚再往下
                COUGH_FITS.put(p.getUUID(), 1 + r.nextInt(3)); // 一陣咳 1～3 聲（咳、咳咳）
                PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.COUGH, 0, 0));
            }
            // 失去方向感：客戶端在毒氣效果期間自己處理（每 3～5 秒換一個方向讓視角慢慢偏），不用伺服器通知
        }
        // 一陣咳嗽：每 5 tick 咳一聲
        Integer left = COUGH_FITS.get(p.getUUID());
        if (left != null && p.tickCount % 5 == 0) {
            var r = p.getRandom();
            p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SpecialRegistry.GAS_COUGH.get(), SoundSource.PLAYERS, 1f, 0.9f + r.nextFloat() * 0.2f);
            if (left <= 1) COUGH_FITS.remove(p.getUUID());
            else COUGH_FITS.put(p.getUUID(), left - 1);

        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0) return;
        var src = event.getSource();
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL)) return;
        Long popped = BUBBLE_POPPED.get(p.getUUID());
        if (popped != null && p.server.getTickCount() - popped <= 1) {
            // 護盾泡泡剛擋下的那一下：同一發攻擊的其他部分（例如 TACZ 的穿甲傷害是分開扣的）也一起擋掉
            event.setCanceled(true);
            return;
        }
        // 不能看「無視無敵」標籤：TACZ 的 bullet_void 子彈也在那個標籤裡，會讓護盾泡泡擋不到
        if (p.hasEffect(SpecialRegistry.SHIELD_BUBBLE.get())) {
            // 護盾泡泡：第一下傷害完全取消，給額外生命值（不會消失）與加速，只觸發一次
            event.setCanceled(true);
            SpecialConfig s = SpecialConfig.get();
            p.removeEffect(SpecialRegistry.SHIELD_BUBBLE.get());
            BUBBLE_POPPED.put(p.getUUID(), (long) p.server.getTickCount());
            Engines.addExtraHealth(p, s.bubbleAbsorption.get().floatValue());
            int speed = SpecialConfig.ticks(s.bubbleSpeedSeconds);
            if (speed > 0) Engines.effect(p, SpecialRegistry.BUBBLE_SPEED.get(), speed, 0);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 1.2f);
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            return;
        }
        if (KnockbackShield.tryBlock(p, src, event.getAmount())) event.setCanceled(true);
    }

    /**
     * 不死圖騰（計畫書 4 的 23 號，使用者 2026-11-23 調整）：受到致命傷時由我們處理，不讓原版圖騰觸發（沒有螢幕中間的圖騰動畫）：
     *   這一下傷害取消 → 血量回到 totem_heal（50）、額外生命值 +totem_absorption（25）、恢復效果 I totem_regen_seconds（10 秒）；
     *   圖騰粒子照樣有，圖騰音效音量 × totem_sound_volume。圖騰用掉後消失。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount() < p.getHealth()) return;
        var src = event.getSource();
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL)) return;
        var off = p.getInventory().offhand.get(0);
        if (!off.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) || !off.hasTag() || !off.getTag().getBoolean(com.provinggrounds.events.OffhandEvents.SYSTEM_TAG)) return;
        event.setCanceled(true);
        SpecialConfig s = SpecialConfig.get();
        Totem.remove(p);
        p.removeEffect(SpecialRegistry.TOTEM_HELD.get());
        p.setHealth((float) Math.min(p.getMaxHealth(), s.totemHeal.get()));
        Engines.addExtraHealth(p, s.totemAbsorption.get().floatValue());
        int regen = SpecialConfig.ticks(s.totemRegenSeconds);
        if (regen > 0) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, regen, 0, false, false, true));
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.8, 0.4, 0.4);
        float vol = s.totemSoundVolume.get().floatValue();
        if (vol > 0) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, vol, 1f);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SpecialWorld.noFall(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            KnockbackShield.restore(p);
            com.provinggrounds.core.Footsteps.setQuiet(p.getUUID(), false);
            com.provinggrounds.core.Hitbox.forget(p.getUUID());
            Totem.remove(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) KnockbackShield.restore(p); // 伺服器重開後殘留的暫存也還回去
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 指令
    // ═════════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        String[] ids = Arrays.stream(SpecialType.values()).map(t -> t.id).toArray(String[]::new);
        event.getDispatcher().register(Commands.literal("pg")
            .then(Commands.literal("special").requires(s -> s.hasPermission(2))
                .then(Commands.literal("mode")
                    .then(Commands.literal("on").executes(ctx -> mode(ctx.getSource(), true)))
                    .then(Commands.literal("off").executes(ctx -> mode(ctx.getSource(), false))))
                .then(Commands.literal("list").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("[PG] 特殊物品（" + ids.length + " 種）：");
                    for (SpecialType t : SpecialType.values()) sb.append("\n  ").append(t.id).append("  ").append(t.zh);
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA), false);
                    return ids.length;
                }))
                .then(Commands.literal("give")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            SpecialType t = SpecialType.byId(StringArgumentType.getString(ctx, "id"));
                            if (t == null) {
                                ctx.getSource().sendFailure(Component.literal("[PG] 沒有這個特殊物品，用 /pg special list 查看全部 ID"));
                                return 0;
                            }
                            ItemStack stack = new ItemStack(SpecialRegistry.item(t));
                            if (!SpecialSlot.tryInsert(p, stack) && !p.getInventory().add(stack)) p.drop(stack, false);
                            ctx.getSource().sendSuccess(() -> Component.literal("[PG] 已給予 " + t.zh).withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        })))
                .then(Commands.literal("simulate")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> simulate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    /** 特殊物品模式開關（同大廳選單的按鈕）。 */
    private static int mode(net.minecraft.commands.CommandSourceStack src, boolean on) {
        SpecialConfig.setEnabled(on);
        src.sendSuccess(() -> Component.literal("[PG] 特殊物品模式：" + (on ? "開" : "關")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 模擬開 count 個箱子：印出每種特殊物品出現幾次（驗證「約 10 箱 1 件」）。 */
    private static int simulate(net.minecraft.commands.CommandSourceStack src, int count) {
        var level = src.getLevel();
        SpecialConfig s = SpecialConfig.get();
        Map<SpecialType, Integer> got = new EnumMap<>(SpecialType.class);
        int total = 0;
        for (int i = 0; i < count; i++) {
            if (level.getRandom().nextDouble() >= s.chance.get()) continue;
            SpecialType t = SpecialItem.typeOf(SpecialLoot.roll(level));
            if (t == null) continue;
            got.merge(t, 1, Integer::sum);
            total++;
        }
        StringBuilder sb = new StringBuilder(String.format("[PG] 模擬 %d 箱：出現特殊物品 %d 件（平均每 %.1f 箱 1 件）",
            count, total, total == 0 ? 0.0 : count / (double) total));
        got.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> sb.append("\n  ").append(e.getKey().zh).append("：").append(e.getValue()));
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
        return total;
    }
}
