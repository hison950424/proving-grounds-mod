package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.data.MarkerScanJob;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.CirclePreset;
import com.provinggrounds.modes.br.LootSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Locale;

/**
 * 檔案: PGCommand.java
 * 目的: 管理員指令（OP 等級 2 以上）。
 *   /pg reload | reset | status | mode <mode>
 *   /pg glass [restore]   被打破的原版玻璃（數量／立刻修復；每局開始自動修）
 *   /pg map  list | set <id> <x> <z> <radius> [name] | here <id> <radius> [name] | remove <id>
 *            | render <id> | render cancel   （產生大地圖／小地圖俯視圖）
 *   /pg loot add [map] | remove [map] | list [map] | count [map] | clear <map> confirm
 *   /pg loot import <map>   舊 Datapack 箱子 marker 轉成點位，存檔確認後刪掉 marker
 *   /pg loot markers import <map> | purge <map> confirm | cancel   （分開做：只匯入／只刪除）
 *   /pg team join <players> <team> | leave <players> | list | clear
 *   /pg br   start <map> [fast|standard|slow] [normal|competitive] | stop
 *   /pg stats [player]
 */
public class PGCommand {

    private static final String PREFIX = "[PG] ";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pg")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                // 被打破的玻璃：查看數量／立刻修復（平常每局開始會自動修）
                .then(Commands.literal("glass")
                    .executes(ctx -> {
                        int n = com.provinggrounds.data.GlassLedger.get(ctx.getSource().getServer()).size();
                        ok(ctx.getSource(), "目前記錄了 " + n + " 塊被打破的原版玻璃，下一局開始時會修回去。", false);
                        return n;
                    })
                    .then(Commands.literal("restore").executes(ctx -> {
                        int n = com.provinggrounds.data.GlassLedger.get(ctx.getSource().getServer())
                            .restoreAll(ctx.getSource().getServer().overworld());
                        ok(ctx.getSource(), "已修復 " + n + " 塊玻璃。", true);
                        return n;
                    })))
                // 彈藥分類檢查
                .then(Commands.literal("ammo").executes(ctx -> {
                    for (String line : com.provinggrounds.compat.AmmoCategories.report()) ok(ctx.getSource(), line, false);
                    return 1;
                }))

                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (GameManager.GameMode m : GameManager.GameMode.values()) {
                                if (m != GameManager.GameMode.NONE) builder.suggest(m.name().toLowerCase(Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))

                .then(mapCommands())
                .then(lootCommands())
                .then(teamCommands())
                .then(brCommands())

                .then(Commands.literal("stats")
                    .executes(ctx -> stats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> stats(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 基本
    // ═════════════════════════════════════════════════════════════════════════

    private static int reload(CommandSourceStack src) {
        try {
            PGConfig.SERVER_SPEC.afterReload();
            com.provinggrounds.compat.AmmoCategories.rebuild(); // 玩家端要重新進入伺服器才會更新
            ok(src, "設定已重載。", true);
            ProvingGroundsMod.LOGGER.info("Config reloaded by {}", src.getTextName());
        } catch (Exception e) {
            fail(src, "重載失敗：" + e.getMessage());
            ProvingGroundsMod.LOGGER.error("Config reload failed", e);
        }
        return 1;
    }

    private static int reset(CommandSourceStack src) {
        BattleRoyaleMode.get().forceStop(true); // 管理員重置：同步清掉箱子
        GameManager.get().reset();
        ok(src, "遊戲狀態已重置，回到大廳。", true);
        ProvingGroundsMod.LOGGER.info("Game reset by {}", src.getTextName());
        return 1;
    }

    private static int status(CommandSourceStack src) {
        GameManager gm = GameManager.get();
        BattleRoyaleMode br = BattleRoyaleMode.get();
        String msg = String.format("狀態: %s | 模式: %s | 玩家數: %d", gm.getState(), gm.getMode(), gm.getActivePlayerCount());
        if (br.isRunning() && br.getMap() != null) {
            msg += String.format(" | BR 階段: %s | 地圖 #%d", br.getPhase(), br.getMap().id);
        }
        ok(src, msg, false);
        return 1;
    }

    private static int setMode(CommandSourceStack src, String modeName) {
        if (!GameManager.get().isInLobby()) {
            fail(src, "只能在大廳切換模式。");
            return 0;
        }
        GameManager.GameMode mode;
        try {
            mode = GameManager.GameMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            fail(src, "未知模式: " + modeName + "。可用：battle_royale, tdm, dom, gk");
            return 0;
        }
        if (mode == GameManager.GameMode.NONE) {
            fail(src, "無效模式。");
            return 0;
        }
        if (mode == GameManager.GameMode.BATTLE_ROYALE) {
            fail(src, "BR 請用 /pg br start <地圖編號> [fast|standard|slow] [normal|competitive]");
            return 0;
        }
        GameManager.get().startGame(mode);
        ok(src, "已切換至模式: " + mode + "（此模式尚未實作，Phase 2）", true);
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // /pg map
    // ═════════════════════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> mapCommands() {
        return Commands.literal("map")
            .then(Commands.literal("list").executes(ctx -> mapList(ctx.getSource())))
            .then(Commands.literal("set")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("radius", DoubleArgumentType.doubleArg(10))
                                .executes(ctx -> mapSet(ctx, DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "z"), null))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> mapSet(ctx, DoubleArgumentType.getDouble(ctx, "x"),
                                        DoubleArgumentType.getDouble(ctx, "z"), StringArgumentType.getString(ctx, "name")))))))))
            .then(Commands.literal("here")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(10))
                        .executes(ctx -> mapSet(ctx, ctx.getSource().getPosition().x, ctx.getSource().getPosition().z, null))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> mapSet(ctx, ctx.getSource().getPosition().x, ctx.getSource().getPosition().z,
                                StringArgumentType.getString(ctx, "name")))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(ctx -> mapRemove(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
            // 產生大地圖／小地圖用的俯視圖
            .then(Commands.literal("render")
                .then(Commands.literal("cancel").executes(ctx -> {
                    if (com.provinggrounds.data.MapRenderJob.cancel()) return 1;
                    fail(ctx.getSource(), "目前沒有地圖在產生。");
                    return 0;
                }))
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(ctx -> mapRender(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))));
    }

    private static int mapRender(CommandSourceStack src, int mapId) {
        if (!GameManager.get().isInLobby() || BattleRoyaleMode.get().isRunning()) {
            fail(src, "遊戲進行中不能產生地圖，請在大廳時執行。");
            return 0;
        }
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        String error = com.provinggrounds.data.MapRenderJob.start(src.getServer(), m, src);
        if (error != null) {
            fail(src, error);
            return 0;
        }
        return 1;
    }

    private static int mapList(CommandSourceStack src) {
        MapRegistry reg = MapRegistry.get(src.getServer());
        if (reg.all().isEmpty()) {
            ok(src, "尚未登記任何地圖。", false);
            return 0;
        }
        ok(src, "已登記地圖：", false);
        for (MapRegistry.BRMap m : reg.all()) {
            MutableComponent line = Component.literal(String.format("  #%d %s  中心 (%.0f, %.0f)  半徑 %.0f  點位 %d",
                m.id, m.name, m.centerX, m.centerZ, m.radius, m.lootPoints.size()));
            src.sendSuccess(() -> teleportLink(line, m.centerX, 200, m.centerZ), false);
        }
        return reg.all().size();
    }

    private static int mapSet(CommandContext<CommandSourceStack> ctx, double x, double z, @Nullable String name) {
        CommandSourceStack src = ctx.getSource();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        MapRegistry reg = MapRegistry.get(src.getServer());
        boolean existed = reg.getMap(id) != null;
        MapRegistry.BRMap m = reg.setMap(id, name != null ? name : (existed ? null : "地圖 " + id), x, z, radius);
        ok(src, String.format("%s地圖 #%d %s：中心 (%.0f, %.0f)，半徑 %.0f",
            existed ? "已更新" : "已新增", m.id, m.name, m.centerX, m.centerZ, m.radius), true);
        return 1;
    }

    private static int mapRemove(CommandSourceStack src, int id) {
        if (BattleRoyaleMode.get().isRunning() && BattleRoyaleMode.get().getMap() != null
            && BattleRoyaleMode.get().getMap().id == id) {
            fail(src, "這張地圖正在進行 BR，不能刪除。");
            return 0;
        }
        if (MapRegistry.get(src.getServer()).removeMap(id)) {
            ok(src, "已刪除地圖 #" + id + "（連同它的戰利品點位）。", true);
            return 1;
        }
        fail(src, "找不到地圖 #" + id + "。");
        return 0;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // /pg loot
    // ═════════════════════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> lootCommands() {
        return Commands.literal("loot")
            .then(Commands.literal("add")
                .executes(ctx -> lootAdd(ctx.getSource(), null))
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> lootAdd(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map")))))
            .then(Commands.literal("remove")
                .executes(ctx -> lootRemove(ctx.getSource(), null))
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> lootRemove(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map")))))
            .then(Commands.literal("list")
                .executes(ctx -> lootList(ctx.getSource(), null))
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> lootList(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map")))))
            .then(Commands.literal("count")
                .executes(ctx -> lootCount(ctx.getSource(), null))
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> lootCount(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map")))))
            .then(Commands.literal("clear")
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        fail(ctx.getSource(), "這會刪除該地圖全部點位。確定的話請在最後加上 confirm。");
                        return 0;
                    })
                    .then(Commands.literal("confirm")
                        .executes(ctx -> lootClear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"))))))
            // 一步完成：舊 marker 轉成點位 → 存檔 → 刪掉已確認的 marker
            .then(Commands.literal("import")
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> markerJob(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"), MarkerScanJob.Mode.MIGRATE))))
            // 舊 Datapack 的箱子 marker：匯入成點位 / 刪除 / 取消
            .then(Commands.literal("markers")
                .then(Commands.literal("import")
                    .then(Commands.argument("map", IntegerArgumentType.integer(1))
                        .executes(ctx -> markerJob(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"), MarkerScanJob.Mode.IMPORT))))
                .then(Commands.literal("purge")
                    .then(Commands.argument("map", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            fail(ctx.getSource(), "這會刪除該地圖範圍內所有舊箱子 marker（無法復原）。建議先 import。確定的話請在最後加上 confirm。");
                            return 0;
                        })
                        .then(Commands.literal("confirm")
                            .executes(ctx -> markerJob(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"), MarkerScanJob.Mode.PURGE)))))
                .then(Commands.literal("cancel").executes(ctx -> {
                    if (MarkerScanJob.cancel()) return 1;
                    fail(ctx.getSource(), "目前沒有 marker 掃描在進行。");
                    return 0;
                })));
    }

    private static int markerJob(CommandSourceStack src, int mapId, MarkerScanJob.Mode mode) {
        if (!GameManager.get().isInLobby() || BattleRoyaleMode.get().isRunning()) {
            fail(src, "遊戲進行中不能掃描 marker，請在大廳時執行。");
            return 0;
        }
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        String error = MarkerScanJob.start(src.getServer(), m, mode, src);
        if (error != null) {
            fail(src, error);
            return 0;
        }
        return 1;
    }

    /** 指定編號就用那張；沒指定就用執行者所在位置的地圖。 */
    @Nullable
    private static MapRegistry.BRMap resolveMap(CommandSourceStack src, @Nullable Integer mapId) {
        MapRegistry reg = MapRegistry.get(src.getServer());
        MapRegistry.BRMap m = mapId != null ? reg.getMap(mapId) : reg.findAt(src.getPosition().x, src.getPosition().z);
        if (m == null) {
            fail(src, mapId != null ? "找不到地圖 #" + mapId + "。" : "你不在任何已登記地圖的範圍內，請在指令後加上地圖編號。");
        }
        return m;
    }

    private static int lootAdd(CommandSourceStack src, @Nullable Integer mapId) {
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        BlockPos pos = BlockPos.containing(src.getPosition());
        if (!m.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) {
            fail(src, String.format("這個位置在地圖 #%d 範圍外（中心 %.0f, %.0f，半徑 %.0f）。", m.id, m.centerX, m.centerZ, m.radius));
            return 0;
        }
        if (MapRegistry.get(src.getServer()).addPoint(m, pos)) {
            ok(src, String.format("已在地圖 #%d 新增點位 (%d, %d, %d)，共 %d 個。",
                m.id, pos.getX(), pos.getY(), pos.getZ(), m.lootPoints.size()), false);
            return 1;
        }
        fail(src, "這個位置已經是點位了。");
        return 0;
    }

    private static int lootRemove(CommandSourceStack src, @Nullable Integer mapId) {
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        BlockPos removed = MapRegistry.get(src.getServer()).removeNearestPoint(m, BlockPos.containing(src.getPosition()), 3);
        if (removed == null) {
            fail(src, "附近 3 格內沒有點位。");
            return 0;
        }
        ok(src, String.format("已刪除點位 (%d, %d, %d)，地圖 #%d 剩 %d 個。",
            removed.getX(), removed.getY(), removed.getZ(), m.id, m.lootPoints.size()), false);
        return 1;
    }

    private static int lootList(CommandSourceStack src, @Nullable Integer mapId) {
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        ok(src, String.format("地圖 #%d %s 共 %d 個點位（點擊傳送，最多列出 30 個）：", m.id, m.name, m.lootPoints.size()), false);
        int shown = 0;
        for (BlockPos p : m.lootPoints) {
            if (shown++ >= 30) break;
            MutableComponent line = Component.literal(String.format("  (%d, %d, %d)", p.getX(), p.getY(), p.getZ()));
            src.sendSuccess(() -> teleportLink(line, p.getX() + 0.5, p.getY(), p.getZ() + 0.5), false);
        }
        return m.lootPoints.size();
    }

    private static int lootCount(CommandSourceStack src, @Nullable Integer mapId) {
        MapRegistry.BRMap m = resolveMap(src, mapId);
        if (m == null) return 0;
        PGConfig c = PGConfig.SERVER;
        ok(src, String.format("地圖 #%d %s：點位 %d 個，本局預期 %.0f 箱（生成機率 %.0f%%，木/鐵/金 權重 %d/%d/%d）",
            m.id, m.name, m.lootPoints.size(), LootSpawner.expectedCrates(m), c.lootSpawnChance.get() * 100,
            c.lootWeightLow.get(), c.lootWeightMid.get(), c.lootWeightHigh.get()), false);
        return m.lootPoints.size();
    }

    private static int lootClear(CommandSourceStack src, int mapId) {
        MapRegistry reg = MapRegistry.get(src.getServer());
        MapRegistry.BRMap m = reg.getMap(mapId);
        if (m == null) {
            fail(src, "找不到地圖 #" + mapId + "。");
            return 0;
        }
        int n = reg.clearPoints(m);
        ok(src, "已清空地圖 #" + mapId + " 的 " + n + " 個點位。", true);
        return n;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // /pg team
    // ═════════════════════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> teamCommands() {
        return Commands.literal("team")
            .then(Commands.literal("join")
                .then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("team", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            for (String s : new String[] { "solo", "1", "2", "3", "4", "red", "blue", "green", "yellow" }) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(ctx -> teamJoin(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"),
                            StringArgumentType.getString(ctx, "team"))))))
            .then(Commands.literal("leave")
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> teamLeave(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))))
            .then(Commands.literal("list").executes(ctx -> teamList(ctx.getSource())))
            .then(Commands.literal("clear").executes(ctx -> {
                TeamManager.get().clearAssignments();
                ok(ctx.getSource(), "已清空所有分隊（沒有隊伍的玩家開局時會是孤狼）。", true);
                return 1;
            }));
    }

    private static int teamJoin(CommandSourceStack src, Collection<ServerPlayer> players, String teamName) {
        if (BattleRoyaleMode.get().isRunning() && BattleRoyaleMode.get().getPhase() != BattleRoyaleMode.Phase.PREPARING) {
            fail(src, "遊戲進行中不能換隊。");
            return 0;
        }
        TeamManager.Team team = TeamManager.Team.parse(teamName);
        if (team == null) {
            fail(src, "未知隊伍：" + teamName + "。可用：隊伍編號（1、2、3…）、red, blue, green, yellow, solo");
            return 0;
        }
        for (ServerPlayer p : players) TeamManager.get().assignTeam(p, team);
        com.provinggrounds.core.LobbyManager.get().broadcast();
        ok(src, players.size() + " 位玩家已加入 " + team.displayName + "。", true);
        return players.size();
    }

    private static int teamLeave(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) TeamManager.get().unassign(p);
        com.provinggrounds.core.LobbyManager.get().broadcast();
        ok(src, players.size() + " 位玩家已離開隊伍（開局時會是孤狼）。", true);
        return players.size();
    }

    private static int teamList(CommandSourceStack src) {
        ok(src, "目前分隊（未分隊 = 孤狼）：", false);
        java.util.TreeMap<Integer, TeamManager.Team> teams = new java.util.TreeMap<>();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            TeamManager.get().getTeam(p).ifPresent(t -> teams.put(t.id, t));
        }
        for (TeamManager.Team t : teams.values()) {
            StringBuilder names = new StringBuilder();
            for (ServerPlayer p : TeamManager.get().getTeamPlayers(t, src.getServer())) {
                if (names.length() > 0) names.append("、");
                names.append(p.getGameProfile().getName());
            }
            if (names.length() == 0) continue;
            src.sendSuccess(() -> Component.literal("  " + t.displayName + "：").withStyle(t.color)
                .append(Component.literal(names.toString()).withStyle(ChatFormatting.WHITE)), false);
        }
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // /pg br
    // ═════════════════════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> brCommands() {
        return Commands.literal("br")
            .then(Commands.literal("start")
                .then(Commands.argument("map", IntegerArgumentType.integer(1))
                    .executes(ctx -> brStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"), "standard", "normal"))
                    .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((ctx, b) -> b.suggest("fast").suggest("standard").suggest("slow").buildFuture())
                        .executes(ctx -> brStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"),
                            StringArgumentType.getString(ctx, "preset"), "normal"))
                        .then(Commands.argument("variant", StringArgumentType.word())
                            .suggests((ctx, b) -> b.suggest("normal").suggest("competitive").buildFuture())
                            .executes(ctx -> brStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "map"),
                                StringArgumentType.getString(ctx, "preset"), StringArgumentType.getString(ctx, "variant")))))))
            .then(Commands.literal("stop").executes(ctx -> {
                if (!BattleRoyaleMode.get().isRunning()) {
                    fail(ctx.getSource(), "目前沒有進行中的 BR。");
                    return 0;
                }
                BattleRoyaleMode.get().forceStop(true);
                GameManager.get().reset();
                ok(ctx.getSource(), "BR 已強制結束，玩家已還原。", true);
                return 1;
            }))
            .then(Commands.literal("airdrop").executes(ctx -> {
                // 測試用：不等時間窗，立刻在當前圈內投下一個空投
                String error = BattleRoyaleMode.get().forceAirdrop();
                if (error != null) {
                    fail(ctx.getSource(), error);
                    return 0;
                }
                ok(ctx.getSource(), "已投下一個空投。", true);
                return 1;
            }));
    }

    private static int brStart(CommandSourceStack src, int mapId, String presetName, String variantName) {
        CirclePreset preset;
        BattleRoyaleMode.Variant variant;
        try {
            preset = CirclePreset.valueOf(presetName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            fail(src, "未知縮圈方案：" + presetName + "。可用：fast, standard, slow");
            return 0;
        }
        try {
            variant = BattleRoyaleMode.Variant.valueOf(variantName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            fail(src, "未知模式：" + variantName + "。可用：normal, competitive");
            return 0;
        }
        String error = BattleRoyaleMode.get().start(src.getServer(), mapId, preset, variant);
        if (error != null) {
            fail(src, error);
            return 0;
        }
        ok(src, "BR 已開始。", true);
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // /pg stats
    // ═════════════════════════════════════════════════════════════════════════

    private static int stats(CommandSourceStack src, ServerPlayer target) {
        PlayerDataManager.PlayerStats s = PlayerDataManager.get().getStats(target);
        int lv = s.getLevel();
        ok(src, String.format("%s：Lv.%d（XP %d / 下一級 %d）｜擊殺 %d 死亡 %d 助攻 %d｜K/D %.2f｜勝場 %d",
            target.getGameProfile().getName(), lv, s.xp, PlayerDataManager.xpForNextLevel(lv),
            s.kills, s.deaths, s.assists, s.getKD(), s.wins), false);
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 小工具
    // ═════════════════════════════════════════════════════════════════════════

    private static Component teleportLink(MutableComponent text, double x, double y, double z) {
        String cmd = String.format(Locale.ROOT, "/tp @s %.1f %.1f %.1f", x, y, z);
        return text.withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("點擊傳送"))));
    }

    private static void ok(CommandSourceStack src, String msg, boolean broadcastToOps) {
        src.sendSuccess(() -> Component.literal(PREFIX + msg), broadcastToOps);
    }

    private static void fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(PREFIX + msg));
    }
}
