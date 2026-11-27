package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.modes.arena.ArenaData;
import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.modes.arena.ArenaMapGenerator;
import com.provinggrounds.modes.arena.ArenaMapRegistry;
import com.provinggrounds.modes.arena.ArenaMode;
import com.provinggrounds.modes.arena.ObsidianBunkerBlueprint;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

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
                var active = ArenaMapRegistry.get(c.getSource().getServer()).active(PGConfig.SERVER.arena);
                StringBuilder sb = new StringBuilder("目前團隊場地：" + active.name() + " (" + active.id() + ")"
                    + "｜紅隊重生 " + active.redSpawns().size()
                    + "｜藍隊重生 " + active.blueSpawns().size()
                    + "｜據點 " + active.domPoints().size()
                    + "｜槍王重生點 " + active.gkSpawns().size()
                    + "\n商店 " + ArenaData.shop().size() + " 項（" + String.join("、", ArenaData.shopCategories()) + "）\n槍王：");
                for (var cat : cats) sb.append(cat.name()).append(" ").append(cat.items().size()).append("　");
                sb.append("\n配件池：");
                ArenaData.gkAttachments().forEach((k, v) -> sb.append(k).append(" ").append(v.size()).append("　"));
                sb.append("\n目前對戰：").append(ArenaManager.current() == null ? "無" : ArenaManager.current().title());
                c.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA), false);
                return 1;
            }))
            .then(Commands.literal("map")
                .then(Commands.literal("generate").executes(c -> generateMap(c.getSource(), false)))
                .then(Commands.literal("regen").executes(c -> generateMap(c.getSource(), true)))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(ArenaMapGenerator.statusText()), false);
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(c -> {
                    if (ArenaMapGenerator.cancel(c.getSource())) return 1;
                    c.getSource().sendFailure(Component.literal("[團隊地圖] 目前沒有正在生成的場地。"));
                    return 0;
                }))
                .then(Commands.literal("list").executes(c -> listMaps(c.getSource())))
                .then(Commands.literal("select")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            b.suggest(ArenaMapRegistry.DEFAULT_ID);
                            b.suggest(ObsidianBunkerBlueprint.MAP_ID);
                            for (var m : ArenaMapRegistry.get(ctx.getSource().getServer()).customMaps()) {
                                b.suggest(m.id());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> selectMap(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("tp")
                    .executes(c -> tpMap(c.getSource(), null))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            b.suggest(ArenaMapRegistry.DEFAULT_ID);
                            b.suggest(ObsidianBunkerBlueprint.MAP_ID);
                            for (var m : ArenaMapRegistry.get(ctx.getSource().getServer()).customMaps()) {
                                b.suggest(m.id());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> tpMap(c.getSource(), StringArgumentType.getString(c, "id"))))))
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


    private static int generateMap(CommandSourceStack src, boolean regen) {
        String err = ArenaMapGenerator.startObsidianBunker(src.getServer(), src, regen);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int listMaps(CommandSourceStack src) {
        ArenaMapRegistry reg = ArenaMapRegistry.get(src.getServer());
        String activeId = reg.activeId();
        var defArena = ArenaMapRegistry.ActiveArena.fromDefault(PGConfig.SERVER.arena);
        src.sendSuccess(() -> Component.literal("[團隊地圖] 可用場地（目前使用：" + activeId + "）：").withStyle(ChatFormatting.GOLD), false);
        sendMapLine(src, defArena.id(), defArena.name(), defArena.redSpawns().size(), defArena.blueSpawns().size(),
            defArena.domPoints().size(), defArena.gkSpawns().size(), ArenaMapRegistry.DEFAULT_ID.equals(activeId));
        for (var m : reg.customMaps()) {
            sendMapLine(src, m.id(), m.name(), m.redSpawns().size(), m.blueSpawns().size(),
                m.domPoints().size(), m.gkSpawns().size(), m.id().equals(activeId));
        }
        return 1;
    }

    private static void sendMapLine(CommandSourceStack src, String id, String name, int redN, int blueN, int domN, int gkN, boolean active) {
        MutableComponent line = Component.literal((active ? " ★ " : " - ") + name + " (" + id + ")")
            .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.WHITE)
            .append(Component.literal(String.format(" [紅%d/藍%d/據點%d/槍王%d] ", redN, blueN, domN, gkN))
                .withStyle(ChatFormatting.GRAY));
        if (!active) {
            line.append(Component.literal("[切換]")
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pg arena map select " + id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/pg arena map select " + id)))));
            line.append(Component.literal(" "));
        }
        line.append(Component.literal("[傳送]")
            .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pg arena map tp " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/pg arena map tp " + id)))));
        src.sendSuccess(() -> line, false);
    }

    private static int selectMap(CommandSourceStack src, String id) {
        if (ArenaManager.isRunning()) {
            src.sendFailure(Component.literal("對戰進行中無法切換團隊地圖。"));
            return 0;
        }
        ArenaMapRegistry reg = ArenaMapRegistry.get(src.getServer());
        if (!reg.setActive(id)) {
            src.sendFailure(Component.literal("找不到團隊地圖「" + id + "」。請先 /pg arena map generate 或用 /pg arena map list 查看。"));
            return 0;
        }
        var active = reg.active(PGConfig.SERVER.arena);
        src.sendSuccess(() -> Component.literal("[團隊地圖] 已切換為「" + active.name() + "」(" + active.id() + ")。")
            .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int tpMap(CommandSourceStack src, @javax.annotation.Nullable String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ArenaMapRegistry reg = ArenaMapRegistry.get(src.getServer());
        String target = (id == null || id.isBlank()) ? reg.activeId() : id.trim();
        Vec3 pos;
        String label;
        if (ArenaMapRegistry.DEFAULT_ID.equalsIgnoreCase(target)) {
            var a = ArenaMapRegistry.ActiveArena.fromDefault(PGConfig.SERVER.arena);
            pos = a.redSpawn();
            label = a.name();
        } else {
            var m = reg.getMap(target);
            if (m == null) {
                src.sendFailure(Component.literal("找不到團隊地圖「" + target + "」。"));
                return 0;
            }
            pos = m.redSpawn();
            label = m.name();
        }
        p.teleportTo(src.getServer().overworld(), pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
        src.sendSuccess(() -> Component.literal(String.format("已傳送至「%s」紅隊基地 (%.1f, %.1f, %.1f)。", label, pos.x, pos.y, pos.z))
            .withStyle(ChatFormatting.AQUA), false);
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
