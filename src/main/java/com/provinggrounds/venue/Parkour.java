package com.provinggrounds.venue;

import com.mojang.brigadier.CommandDispatcher;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: Parkour.java
 * 目的: 跑庫塔計時（計畫書 5 的 D2；伺服器端）。
 *   - 在「跑庫塔」場地（venue id = parkour）裡，走進起點 3 格內開始計時（離開起點範圍那一刻才開始跑秒）；
 *     站上終點方塊（壓力板）停止。動作欄顯示目前秒數。
 *   - 秒數存個人最佳；破個人最佳時全服聊天欄廣播＋音效，並更新前 3 名排行榜（文字顯示實體）。
 *   - 完成後傳回大廳重生點（預設 -4 32 -1）。
 *   - 起點、終點、排行榜位置、返回點用管理員指令登記：/pg parkour setstart｜setend｜setboard｜setreturn｜reset。
 *   - 資料存在世界存檔（SavedData，不用計分板）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class Parkour {

    private static final String BOARD_TAG = "pg_parkour_board";
    private static final Map<UUID, Long> RUNNING = new HashMap<>(); // 開始的 game tick
    private static final Set<UUID> AT_START = new HashSet<>();

    private Parkour() {}

    // ── 存檔 ──

    public static final class Data extends SavedData {
        @Nullable BlockPos start, end;
        @Nullable Vec3 board;
        Vec3 back = new Vec3(-4, 32, -1);
        final Map<UUID, Long> best = new HashMap<>();      // 個人最佳（tick）
        final Map<UUID, String> names = new HashMap<>();

        static Data load(CompoundTag t) {
            Data d = new Data();
            if (t.contains("start")) d.start = BlockPos.of(t.getLong("start"));
            if (t.contains("end")) d.end = BlockPos.of(t.getLong("end"));
            if (t.contains("bx")) d.board = new Vec3(t.getDouble("bx"), t.getDouble("by"), t.getDouble("bz"));
            if (t.contains("rx")) d.back = new Vec3(t.getDouble("rx"), t.getDouble("ry"), t.getDouble("rz"));
            for (Tag e : t.getList("best", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                UUID id = c.getUUID("id");
                d.best.put(id, c.getLong("t"));
                d.names.put(id, c.getString("n"));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            if (start != null) t.putLong("start", start.asLong());
            if (end != null) t.putLong("end", end.asLong());
            if (board != null) { t.putDouble("bx", board.x); t.putDouble("by", board.y); t.putDouble("bz", board.z); }
            t.putDouble("rx", back.x); t.putDouble("ry", back.y); t.putDouble("rz", back.z);
            ListTag list = new ListTag();
            best.forEach((id, v) -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", id);
                c.putLong("t", v);
                c.putString("n", names.getOrDefault(id, "?"));
                list.add(c);
            });
            t.put("best", list);
            return t;
        }

        List<Map.Entry<UUID, Long>> top(int n) {
            List<Map.Entry<UUID, Long>> all = new ArrayList<>(best.entrySet());
            all.sort(Map.Entry.comparingByValue());
            return all.subList(0, Math.min(n, all.size()));
        }
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "provinggrounds_parkour");
    }

    // ── 計時 ──

    private static boolean inParkour(ServerPlayer p) {
        Venue v = VenueManager.current(p);
        return v != null && "parkour".equals(v.id()) && !p.isSpectator();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 2 != 0) return;
        Data d = data(server);
        if (d.start == null || d.end == null) return;
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (!inParkour(p) || p.level() != level) { RUNNING.remove(id); AT_START.remove(id); continue; }
            boolean atStart = p.position().distanceToSqr(Vec3.atBottomCenterOf(d.start)) <= 9;
            if (atStart) {
                if (!AT_START.contains(id)) p.displayClientMessage(Component.literal("跑庫塔：離開起點就開始計時").withStyle(ChatFormatting.AQUA), true);
                AT_START.add(id);
                RUNNING.remove(id);
                continue;
            }
            if (AT_START.remove(id)) RUNNING.put(id, now); // 離開起點：開始跑秒
            Long t0 = RUNNING.get(id);
            if (t0 == null) continue;
            long ticks = now - t0;
            if (p.blockPosition().equals(d.end) || p.blockPosition().below().equals(d.end)) {
                RUNNING.remove(id);
                finish(server, d, p, ticks);
            } else if (ticks % 4 == 0) {
                p.displayClientMessage(Component.literal("跑庫塔 " + fmt(ticks)).withStyle(ChatFormatting.WHITE), true);
            }
        }
    }

    private static void finish(MinecraftServer server, Data d, ServerPlayer p, long ticks) {
        String name = p.getGameProfile().getName();
        Long old = d.best.get(p.getUUID());
        boolean pb = old == null || ticks < old;
        p.sendSystemMessage(Component.literal("[跑庫塔] 完成！" + fmt(ticks) + (old != null ? "（個人最佳 " + fmt(pb ? ticks : old) + "）" : ""))
            .withStyle(ChatFormatting.GREEN));
        if (pb) {
            d.best.put(p.getUUID(), ticks);
            d.names.put(p.getUUID(), name);
            d.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.literal("[跑庫塔] " + name + " 刷新個人最佳：" + fmt(ticks) + "！")
                .withStyle(ChatFormatting.GOLD), false);
            for (ServerPlayer o : server.getPlayerList().getPlayers()) o.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.5f, 1.2f);
            updateBoard(server, d);
        }
        p.teleportTo(server.overworld(), d.back.x, d.back.y, d.back.z, p.getYRot(), p.getXRot());
        VenueManager.backToLobby(p);
    }

    static String fmt(long ticks) {
        return String.format("%.2f 秒", ticks / 20.0);
    }

    /** 排行榜：前 3 名，用文字顯示實體（先刪掉舊的再生成）。 */
    static void updateBoard(MinecraftServer server, Data d) {
        if (d.board == null) return;
        StringBuilder sb = new StringBuilder("\"跑庫塔排行榜\\n");
        int i = 1;
        for (Map.Entry<UUID, Long> e : d.top(3)) {
            sb.append(i++).append(". ").append(d.names.getOrDefault(e.getKey(), "?").replace("\"", "")).append("  ").append(fmt(e.getValue())).append("\\n");
        }
        if (i == 1) sb.append("（還沒有紀錄）");
        sb.append("\"");
        CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        server.getCommands().performPrefixedCommand(src, "kill @e[type=minecraft:text_display,tag=" + BOARD_TAG + "]");
        server.getCommands().performPrefixedCommand(src, String.format(Locale.ROOT,
            "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\"],billboard:\"center\",text:'%s'}",
            d.board.x, d.board.y, d.board.z, BOARD_TAG, sb.toString().replace("'", "")));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RUNNING.remove(event.getEntity().getUUID());
        AT_START.remove(event.getEntity().getUUID());
    }

    // ── 管理員指令 ──

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pg").then(Commands.literal("parkour").requires(s -> s.hasPermission(2))
            .then(Commands.literal("setstart").executes(c -> set(c.getSource(), "start")))
            .then(Commands.literal("setend").executes(c -> set(c.getSource(), "end")))
            .then(Commands.literal("setboard").executes(c -> set(c.getSource(), "board")))
            .then(Commands.literal("setreturn").executes(c -> set(c.getSource(), "return")))
            .then(Commands.literal("reset").executes(c -> {
                Data d = data(c.getSource().getServer());
                d.best.clear();
                d.names.clear();
                d.setDirty();
                updateBoard(c.getSource().getServer(), d);
                c.getSource().sendSuccess(() -> Component.literal("跑庫塔紀錄已清空"), true);
                return 1;
            }))));
    }

    private static int set(CommandSourceStack src, String what) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("要由玩家執行（用你站的位置）")); return 0; }
        Data d = data(src.getServer());
        switch (what) {
            case "start" -> d.start = p.blockPosition();
            case "end" -> d.end = p.blockPosition(); // 站在終點壓力板上執行
            case "board" -> d.board = p.position().add(0, 2, 0);
            case "return" -> d.back = p.position();
            default -> { }
        }
        d.setDirty();
        if (what.equals("board")) updateBoard(src.getServer(), d);
        src.sendSuccess(() -> Component.literal("跑庫塔 " + what + " 已設在 " + p.blockPosition().toShortString()), true);
        return 1;
    }
}
