package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.br.CirclePreset;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 檔案: CircleCommand.java
 * 目的: 管理員在遊戲裡查看、修改縮圈數值（會寫回伺服器設定檔，下一場開始生效）：
 *   /pg circle                                      列出三種方案的所有數值與每張地圖的估計時間
 *   /pg circle set <fast|standard|slow> ratios <比例…>   各階段半徑 ÷ 地圖半徑，例如 0.6 0.35 0.15 0（空白或逗號分隔）
 *   /pg circle set <方案> interval <秒>              每階段的等待時間與最短縮圈時間
 *   /pg circle set <方案> speed <倍率>               圈移動速度上限 = BR 疾跑速度 × 倍率
 *   /pg circle set <方案> first_wait <秒>            開場第一次縮圈前額外多等幾秒
 *   /pg circle set <方案> final_hold <秒>            決賽圈（半徑 2.5 格）停留幾秒後再縮到 0（計畫書 7 B）
 *   /pg circle reset <方案>                          改回預設值
 */
public final class CircleCommand {

    private CircleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (CirclePreset p : CirclePreset.values()) {
            if (p == CirclePreset.CUSTOM) continue; // 自訂方案在大廳選單調整
            set.then(Commands.literal(p.key)
                .then(Commands.literal("ratios").then(Commands.argument("values", StringArgumentType.greedyString())
                    .executes(ctx -> setRatios(ctx.getSource(), p, StringArgumentType.getString(ctx, "values")))))
                .then(Commands.literal("interval").then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                    .executes(ctx -> { p.intervalValue().set(IntegerArgumentType.getInteger(ctx, "seconds")); return saved(ctx.getSource(), p, p.intervalValue()); })))
                .then(Commands.literal("speed").then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.1, 2.0))
                    .executes(ctx -> { p.speedValue().set(DoubleArgumentType.getDouble(ctx, "factor")); return saved(ctx.getSource(), p, p.speedValue()); })))
                .then(Commands.literal("first_wait").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                    .executes(ctx -> { p.firstWaitValue().set(IntegerArgumentType.getInteger(ctx, "seconds")); return saved(ctx.getSource(), p, p.firstWaitValue()); })))
                .then(Commands.literal("final_hold").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                    .executes(ctx -> { p.finalHoldValue().set(IntegerArgumentType.getInteger(ctx, "seconds")); return saved(ctx.getSource(), p, p.finalHoldValue()); }))));
        }
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
        for (CirclePreset p : CirclePreset.values()) {
            if (p == CirclePreset.CUSTOM) continue;
            reset.then(Commands.literal(p.key).executes(ctx -> {
                p.ratiosValue().set(p.ratiosValue().getDefault());
                p.intervalValue().set(p.intervalValue().getDefault());
                p.speedValue().set(p.speedValue().getDefault());
                p.firstWaitValue().set(p.firstWaitValue().getDefault());
                p.finalHoldValue().set(p.finalHoldValue().getDefault());
                return saved(ctx.getSource(), p, p.ratiosValue(), p.intervalValue(), p.speedValue(), p.firstWaitValue(), p.finalHoldValue());
            }));
        }
        dispatcher.register(Commands.literal("pg").then(Commands.literal("circle").requires(s -> s.hasPermission(2))
            .executes(ctx -> show(ctx.getSource()))
            .then(set)
            .then(reset)));
    }

    private static int setRatios(CommandSourceStack src, CirclePreset p, String text) {
        List<Double> list = new ArrayList<>();
        for (String part : text.split("[\\s,，]+")) {
            if (part.isBlank()) continue;
            try {
                double v = Double.parseDouble(part);
                if (v < 0 || v >= 1) throw new NumberFormatException();
                list.add(v);
            } catch (NumberFormatException e) {
                src.sendFailure(Component.literal("[PG] 比例要是 0（含）到 1（不含）之間的數字：" + part));
                return 0;
            }
        }
        if (list.isEmpty()) { src.sendFailure(Component.literal("[PG] 至少要有一個階段")); return 0; }
        list.sort((a, b) -> Double.compare(b, a)); // 由大到小
        p.ratiosValue().set(list);
        return saved(src, p, p.ratiosValue());
    }

    private static int saved(CommandSourceStack src, CirclePreset p, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>... values) {
        for (var v : values) v.save();
        src.sendSuccess(() -> Component.literal("[PG] 已更新「" + p.displayName + "」縮圈設定（下一場開始生效）").withStyle(ChatFormatting.GREEN), true);
        return show(src);
    }

    private static int show(CommandSourceStack src) {
        List<Double> radii = new ArrayList<>();
        for (MapRegistry.BRMap m : MapRegistry.get(src.getServer()).all()) radii.add(m.radius);
        for (CirclePreset p : CirclePreset.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[PG] ").append(p.displayName).append("（").append(p.key).append("）：")
                .append(p.ratios().length).append(" 階段 ").append(Arrays.toString(p.ratios()))
                .append("｜間隔 ").append(p.intervalSeconds()).append(" 秒")
                .append("｜速度 疾跑×").append(p.speedFactor())
                .append("｜開場多等 ").append(p.firstWaitSeconds()).append(" 秒")
                .append("｜決賽圈（2.5 格）停留 ").append(p.finalHoldSeconds()).append(" 秒");
            if (!radii.isEmpty()) {
                double min = Double.MAX_VALUE, max = 0;
                for (double r : radii) { double t = p.estimateSeconds(r); min = Math.min(min, t); max = Math.max(max, t); }
                sb.append(String.format("｜每場約 %.1f～%.1f 分鐘", min / 60, max / 60));
            }
            String line = sb.toString();
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        src.sendSuccess(() -> Component.literal("[PG] 修改：/pg circle set <fast|standard|slow> <ratios|interval|speed|first_wait|final_hold> <值>；還原：/pg circle reset <方案>")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
