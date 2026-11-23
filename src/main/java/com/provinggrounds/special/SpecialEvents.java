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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GlowManager.tick(event.getServer());
        SpecialWorld.tick(event.getServer());
    }

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
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 1f, 0.55f + r.nextFloat() * 0.15f);
                PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.COUGH, 0, 0));
            }
            if (r.nextInt(60) == 0) { // 失去方向感：被強制往前／後／左／右移動一段時間
                PGNetwork.sendTo(p, SpecialFxPacket.of(SpecialFxPacket.DISORIENT, r.nextInt(4), 12 + r.nextInt(18)));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0) return;
        var src = event.getSource();
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL)) return;
        if (p.hasEffect(SpecialRegistry.SHIELD_BUBBLE.get()) && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // 護盾泡泡：第一下傷害完全取消，給額外生命值（不會消失）與加速，只觸發一次
            event.setCanceled(true);
            SpecialConfig s = SpecialConfig.get();
            p.removeEffect(SpecialRegistry.SHIELD_BUBBLE.get());
            Engines.addExtraHealth(p, s.bubbleAbsorption.get().floatValue());
            int speed = SpecialConfig.ticks(s.bubbleSpeedSeconds);
            if (speed > 0) Engines.effect(p, SpecialRegistry.BUBBLE_SPEED.get(), speed, 0);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 1.2f);
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            return;
        }
        if (KnockbackShield.tryBlock(p, src, event.getAmount())) event.setCanceled(true);
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
