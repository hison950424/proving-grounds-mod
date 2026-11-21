package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;

/**
 * 檔案: RandomCommand.java
 * 目的: 把 1.20.2 才有的原版 /random 指令移植到 1.20.1（舊 Datapack 用過，例如
 *       execute store result score @s rand_val run random value 1..100）。
 *   /random value <最小..最大> [序列]  回傳隨機整數（可被 execute store 接住），只給執行者看。需要 OP 等級 2。
 *   /random roll  <最小..最大> [序列]  擲骰子，結果廣播給所有玩家。所有人都能用。
 *   範圍兩端都要寫（例如 1..100），範圍最大 2147483646。
 *   [序列] 參數為了相容原版語法而接受，但目前不使用（一律用伺服器的隨機數）。
 */
public final class RandomCommand {

    private static final SimpleCommandExceptionType UNBOUNDED =
        new SimpleCommandExceptionType(Component.literal("範圍兩端都要有數字，例如 1..100"));
    private static final SimpleCommandExceptionType TOO_LARGE =
        new SimpleCommandExceptionType(Component.literal("範圍太大（最多 2147483646）"));
    private static final SimpleCommandExceptionType EMPTY =
        new SimpleCommandExceptionType(Component.literal("最小值不能大於最大值"));

    private RandomCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("random")
            .then(branch("value", false).requires(src -> src.hasPermission(2)))
            .then(branch("roll", true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> branch(String name, boolean broadcast) {
        return Commands.literal(name)
            .then(Commands.argument("range", RangeArgument.intRange())
                .executes(ctx -> run(ctx, broadcast))
                .then(Commands.argument("sequence", ResourceLocationArgument.id())
                    .executes(ctx -> run(ctx, broadcast))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean broadcast) throws CommandSyntaxException {
        MinMaxBounds.Ints range = RangeArgument.Ints.getRange(ctx, "range");
        Integer min = range.getMin(), max = range.getMax();
        if (min == null || max == null) throw UNBOUNDED.create();
        if (min > max) throw EMPTY.create();
        long span = (long) max - min + 1;
        if (span > 2147483646L) throw TOO_LARGE.create();
        CommandSourceStack src = ctx.getSource();
        int value = (int) (min + src.getLevel().getRandom().nextInt((int) span));
        if (broadcast) {
            Component msg = Component.literal(src.getTextName() + " 擲出了 " + value + "（" + min + " ~ " + max + "）");
            src.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        } else {
            src.sendSuccess(() -> Component.literal("隨機數：" + value), false);
        }
        return value;
    }
}
