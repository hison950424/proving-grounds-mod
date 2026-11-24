package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.modes.arena.ArenaData;
import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.modes.arena.ArenaMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 檔案: ArenaCommand.java
 * 目的: 管理員指令（團隊死鬥／佔點／槍王之王）：
 *   /pg arena start <tdm|dom|gk>       用大廳的設定直接開局（所有不是觀戰的玩家）
 *   /pg arena stop                     強制結束、全員回大廳
 *   /pg arena money <玩家> <金額>       給錢（負數＝扣錢，測試商店用）
 *   /pg arena info                     顯示資料載入狀況（商品數、槍王武器、重生點）
 */
public final class ArenaCommand {

    private ArenaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pg").then(Commands.literal("arena").requires(s -> s.hasPermission(2))
            .then(Commands.literal("start")
                .then(Commands.literal("tdm").executes(c -> start(c.getSource(), GameManager.GameMode.TDM)))
                .then(Commands.literal("dom").executes(c -> start(c.getSource(), GameManager.GameMode.DOM)))
                .then(Commands.literal("gk").executes(c -> start(c.getSource(), GameManager.GameMode.GK))))
            .then(Commands.literal("stop").executes(c -> {
                if (!ArenaManager.isRunning()) { c.getSource().sendFailure(Component.literal("目前沒有對戰。")); return 0; }
                ArenaManager.forceStop();
                c.getSource().sendSuccess(() -> Component.literal("已結束對戰，全員回大廳。"), true);
                return 1;
            }))
            .then(Commands.literal("money").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", IntegerArgumentType.integer(-1_000_000, 1_000_000)).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    ArenaMode m = ArenaManager.current();
                    ArenaMode.ArenaPlayer ap = m == null ? null : m.player(p.getUUID());
                    if (ap == null) { c.getSource().sendFailure(Component.literal("這個玩家不在對戰中。")); return 0; }
                    ap.money = Math.max(0, ap.money + IntegerArgumentType.getInteger(c, "amount"));
                    c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " 現在有 $" + ap.money), true);
                    return 1;
                }))))
            .then(Commands.literal("info").executes(c -> {
                var cats = ArenaData.gkCategories();
                StringBuilder sb = new StringBuilder("商店 " + ArenaData.shop().size() + " 項（" + String.join("、", ArenaData.shopCategories()) + "）\n槍王：");
                for (var cat : cats) sb.append(cat.name()).append(" ").append(cat.items().size()).append("　");
                sb.append("\n配件池：");
                ArenaData.gkAttachments().forEach((k, v) -> sb.append(k).append(" ").append(v.size()).append("　"));
                sb.append("\n槍王重生點 ").append(ArenaData.gkSpawns().size()).append(" 個；目前對戰：")
                    .append(ArenaManager.current() == null ? "無" : ArenaManager.current().title());
                c.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA), false);
                return 1;
            }))
            // 測試輔助（計畫書 5 的 C7）：只限管理員，不寫進正式設定
            .then(Commands.literal("debug")
                .then(Commands.literal("addkills").then(Commands.argument("team", StringArgumentType.word())
                    .then(Commands.argument("n", IntegerArgumentType.integer(1, 10000))
                        .executes(c -> debug(c.getSource(), "addkills", StringArgumentType.getString(c, "team"), IntegerArgumentType.getInteger(c, "n"))))))
                .then(Commands.literal("setmoney").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("n", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> {
                        ServerPlayer p = EntityArgument.getPlayer(c, "player");
                        var m = ArenaManager.current();
                        var ap = m == null ? null : m.player(p.getUUID());
                        if (ap == null) { c.getSource().sendFailure(Component.literal("這個玩家不在對戰中。")); return 0; }
                        ap.money = IntegerArgumentType.getInteger(c, "n");
                        c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " 現在有 $" + ap.money), true);
                        return 1;
                    }))))
                .then(Commands.literal("skipstage").executes(c -> debug(c.getSource(), "skipstage", "", 0)))
                .then(Commands.literal("skipround").executes(c -> debug(c.getSource(), "skipround", "", 0)))
                .then(Commands.literal("advance").then(Commands.argument("team", StringArgumentType.word())
                    .executes(c -> debug(c.getSource(), "advance", StringArgumentType.getString(c, "team"), 0))))
                .then(Commands.literal("speed").then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(c -> {
                    ArenaManager.setSpeed(IntegerArgumentType.getInteger(c, "n"));
                    c.getSource().sendSuccess(() -> Component.literal("對戰計時加速 ×" + ArenaManager.speed()), true);
                    return 1;
                }))))));
    }

    private static int debug(CommandSourceStack src, String action, String arg, int n) {
        var m = ArenaManager.current();
        if (m == null) { src.sendFailure(Component.literal("目前沒有對戰。")); return 0; }
        String msg = m.debug(action, arg, n);
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }


    private static int start(CommandSourceStack src, GameManager.GameMode mode) {
        var server = src.getServer();
        String error = ArenaManager.start(server, mode, LobbyManager.participants(server), LobbyManager.get().arenaSettings());
        if (error != null) { src.sendFailure(Component.literal(error)); return 0; }
        LobbyManager.get().broadcast();
        src.sendSuccess(() -> Component.literal("已開始 " + ArenaManager.current().title()), true);
        return 1;
    }
}
