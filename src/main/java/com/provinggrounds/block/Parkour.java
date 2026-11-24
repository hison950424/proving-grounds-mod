package com.provinggrounds.block;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 檔案: Parkour.java
 * 目的: 跑酷開始方塊、跑酷結束方塊（計畫書 6 D1；伺服器端）。
 *   連結：手上拿「跑酷結束方塊」右鍵一個「跑酷開始方塊」（選取起點）→ 再把結束方塊放下去，就連結到那個起點。
 *         一個起點只連一個終點（重新連結會換掉舊的）。沒先選起點就放結束方塊會被擋下並提示。
 *   計時：踩到開始方塊就（重新）開始計時，快捷欄上方顯示秒數；踩到「連結的」結束方塊就停止，
 *         成績比自己的紀錄好就更新排行榜（每人只留最佳，前 10 名），排行榜用文字顯示在開始方塊上方。
 *   拆掉開始方塊：刪掉排行榜與連結；拆掉結束方塊：取消連結。
 *   資料存在 world/data/provinggrounds_parkour.dat。執行者：玩家（PlayerTickEvent）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class Parkour {

    private static final String BOARD_TAG = "pg_parkour_board";
    private static final int TOP = 10;

    /** 玩家正在選取的起點（拿結束方塊右鍵起點後）。 */
    private static final Map<UUID, BlockPos> PENDING = new HashMap<>();
    /** 玩家正在跑的起點與開始的遊戲刻。 */
    private record Run(BlockPos start, long startTick) {}
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private Parkour() {}

    // ── 資料 ──

    public static final class Data extends SavedData {
        /** 起點 → 終點（null = 還沒連結）。 */
        final Map<BlockPos, BlockPos> links = new HashMap<>();
        /** 起點 → 排行榜（玩家名字 → 最佳毫秒）。 */
        final Map<BlockPos, Map<String, Long>> boards = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "provinggrounds_parkour");
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            for (Tag t : tag.getList("starts", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                BlockPos s = BlockPos.of(c.getLong("start"));
                d.links.put(s, c.contains("end") ? BlockPos.of(c.getLong("end")) : null);
                Map<String, Long> b = new HashMap<>();
                CompoundTag bt = c.getCompound("board");
                for (String k : bt.getAllKeys()) b.put(k, bt.getLong(k));
                d.boards.put(s, b);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Map.Entry<BlockPos, BlockPos> e : links.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLong("start", e.getKey().asLong());
                if (e.getValue() != null) c.putLong("end", e.getValue().asLong());
                CompoundTag bt = new CompoundTag();
                boards.getOrDefault(e.getKey(), Map.of()).forEach(bt::putLong);
                c.put("board", bt);
                list.add(c);
            }
            tag.put("starts", list);
            return tag;
        }

        BlockPos startOf(BlockPos end) {
            for (Map.Entry<BlockPos, BlockPos> e : links.entrySet()) if (end.equals(e.getValue())) return e.getKey();
            return null;
        }
    }

    // ── 放置、拆除、連結 ──

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getLevel().getBlockState(event.getPos()).is(PGBlocks.PARKOUR_START.get())) return;
        if (!event.getItemStack().is(PGBlocks.PARKOUR_END_ITEM.get())) return;
        PENDING.put(p.getUUID(), event.getPos().immutable());
        p.displayClientMessage(Component.literal("已選取跑酷起點，現在放下結束方塊就會連結").withStyle(ChatFormatting.GREEN), true);
        event.setCanceled(true); // 不要順便把結束方塊放在起點旁邊
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) return;
        Data d = Data.get(level.getServer());
        if (event.getPlacedBlock().is(PGBlocks.PARKOUR_START.get())) {
            d.links.putIfAbsent(event.getPos().immutable(), null);
            d.boards.putIfAbsent(event.getPos().immutable(), new HashMap<>());
            d.setDirty();
            updateBoard(level, event.getPos(), d);
            p.displayClientMessage(Component.literal("跑酷起點已放好。拿結束方塊右鍵這個起點，再放下結束方塊來連結").withStyle(ChatFormatting.AQUA), true);
        } else if (event.getPlacedBlock().is(PGBlocks.PARKOUR_END.get())) {
            BlockPos start = PENDING.remove(p.getUUID());
            if (start == null || !level.getBlockState(start).is(PGBlocks.PARKOUR_START.get())) {
                event.setCanceled(true);
                p.displayClientMessage(Component.literal("請先拿著結束方塊右鍵一個跑酷起點，再放下結束方塊").withStyle(ChatFormatting.RED), true);
                return;
            }
            d.links.put(start, event.getPos().immutable());
            d.boards.putIfAbsent(start, new HashMap<>());
            d.setDirty();
            p.displayClientMessage(Component.literal("已連結跑酷起點與終點").withStyle(ChatFormatting.GREEN), true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Data d = Data.get(level.getServer());
        BlockPos pos = event.getPos();
        if (event.getState().is(PGBlocks.PARKOUR_START.get())) {
            d.links.remove(pos);
            d.boards.remove(pos);
            d.setDirty();
            removeBoard(level, pos);
        } else if (event.getState().is(PGBlocks.PARKOUR_END.get())) {
            BlockPos s = d.startOf(pos);
            if (s != null) { d.links.put(s, null); d.setDirty(); }
        }
    }

    // ── 計時 ──

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (!p.onGround()) { showTimer(p, level); return; }
        BlockPos below = p.getOnPos();
        var state = level.getBlockState(below);
        long now = level.getGameTime();
        if (state.is(PGBlocks.PARKOUR_START.get())) {
            Run r = RUNS.get(p.getUUID());
            if (r == null || !r.start().equals(below) || now - r.startTick() > 2) {
                if (r == null || !r.start().equals(below)) p.displayClientMessage(Component.literal("跑酷開始！").withStyle(ChatFormatting.GREEN), true);
                RUNS.put(p.getUUID(), new Run(below.immutable(), now)); // 站在起點上 = 計時歸零
            }
            return;
        }
        Run r = RUNS.get(p.getUUID());
        if (r != null && state.is(PGBlocks.PARKOUR_END.get())) {
            Data d = Data.get(level.getServer());
            if (below.equals(d.links.get(r.start()))) {
                RUNS.remove(p.getUUID());
                finish(p, level, d, r.start(), (now - r.startTick()) * 50);
                return;
            }
        }
        showTimer(p, level);
    }

    private static void showTimer(ServerPlayer p, ServerLevel level) {
        Run r = RUNS.get(p.getUUID());
        if (r == null || level.getGameTime() % 2 != 0) return;
        p.displayClientMessage(Component.literal("跑酷 " + format((level.getGameTime() - r.startTick()) * 50)).withStyle(ChatFormatting.YELLOW), true);
    }

    private static void finish(ServerPlayer p, ServerLevel level, Data d, BlockPos start, long ms) {
        String name = p.getGameProfile().getName();
        Map<String, Long> board = d.boards.computeIfAbsent(start, k -> new HashMap<>());
        Long best = board.get(name);
        boolean record = best == null || ms < best;
        if (record) { board.put(name, ms); d.setDirty(); updateBoard(level, start, d); }
        p.displayClientMessage(Component.literal("完成！" + format(ms) + (record ? "（新紀錄）" : "（最佳 " + format(best) + "）"))
            .withStyle(record ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), record ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
            SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RUNS.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    static String format(long ms) {
        return String.format("%d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100);
    }

    // ── 排行榜（開始方塊上方的文字） ──

    private static void removeBoard(ServerLevel level, BlockPos start) {
        AABB box = new AABB(start).inflate(0.5, 4, 0.5);
        for (Display.TextDisplay t : level.getEntitiesOfClass(Display.TextDisplay.class, box, e -> e.getTags().contains(BOARD_TAG))) t.discard();
    }

    static void updateBoard(ServerLevel level, BlockPos start, Data d) {
        removeBoard(level, start);
        List<Map.Entry<String, Long>> list = new ArrayList<>(d.boards.getOrDefault(start, Map.of()).entrySet());
        list.sort(Map.Entry.comparingByValue());
        StringBuilder sb = new StringBuilder("§6§l跑酷排行榜§r");
        if (list.isEmpty()) sb.append("\n§7（還沒有紀錄）");
        for (int i = 0; i < Math.min(TOP, list.size()); i++) {
            sb.append("\n").append(i == 0 ? "§e" : "§f").append(i + 1).append(". ").append(list.get(i).getKey())
                .append("  §b").append(format(list.get(i).getValue()));
        }
        Display.TextDisplay t = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        int lines = 1 + Math.max(1, Math.min(TOP, list.size()));
        t.moveTo(start.getX() + 0.5, start.getY() + 1.6 + lines * 0.27, start.getZ() + 0.5);
        CompoundTag tag = new CompoundTag();
        t.saveWithoutId(tag);
        tag.putString("text", Component.Serializer.toJson(Component.literal(sb.toString())));
        tag.putString("billboard", "center");
        tag.putInt("line_width", 260);
        t.load(tag);
        t.addTag(BOARD_TAG);
        level.addFreshEntity(t);
    }

    /** 給 /pg 之類的工具用：玩家是否正在跑酷。 */
    public static boolean running(Player p) { return RUNS.containsKey(p.getUUID()); }
}
