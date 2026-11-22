package com.provinggrounds.admin;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * 檔案: FloatText.java
 * 目的: 浮空文字（計畫書 B6 建立、B7 刪除最近一個、B10 復原）。
 *   文字用原版文字展示實體（text_display），billboard = center（永遠面向看的人）、有陰影、背景透明，
 *   加上標籤 pg_float_text：只處理有這個標籤的實體，不會刪到其他展示實體或告示牌。
 *   實體跟著區塊存檔，重啟後仍在。BR 的清理流程只刪掉落物與帶自己標籤的空投實體，不會碰到這些文字。
 *   刪除紀錄（復原用）存在 SavedData（world/data/provinggrounds_float_text_log.dat），最多 text_undo_max 筆；
 *   只記錄 B7 刪掉的文字，用 /kill 等其他方式刪掉的無法復原。
 */
public final class FloatText {

    public static final String TAG = "pg_float_text";
    public static final int MAX_LENGTH = 100;
    private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private FloatText() {}

    // ── B6 建立 ─────────────────────────────────────────────────────────────

    /** 在玩家所在位置上方約 1.5 格建立文字。回傳給玩家的訊息。 */
    public static Component create(ServerPlayer p, String raw) {
        String text = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ');
        if (text.isBlank()) return AdminTools.err("文字是空的，沒有建立。");
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) return AdminTools.err("文字太長（最多 " + MAX_LENGTH + " 個字）。");
        String colored = COLOR.matcher(text).replaceAll("§$1");
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(Component.literal(colored)));
        tag.putString("billboard", "center");
        tag.putBoolean("shadow", true);
        tag.putInt("background", 0);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Vec3 pos = p.position().add(0, 1.5, 0);
        Entity e = spawn(p.serverLevel(), tag, pos);
        if (e == null) return AdminTools.err("建立失敗。");
        return AdminTools.ok(String.format("已建立浮空文字：%s（%.1f, %.1f, %.1f）", plain(colored), pos.x, pos.y, pos.z));
    }

    @Nullable
    private static Entity spawn(ServerLevel level, CompoundTag tag, Vec3 pos) {
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> { x.moveTo(pos.x, pos.y, pos.z, x.getYRot(), x.getXRot()); return x; });
        if (e == null || !level.tryAddFreshEntityWithPassengers(e)) return null;
        return e;
    }

    // ── B7 刪除最近一個 ─────────────────────────────────────────────────────

    public static Component deleteNearest(ServerPlayer p) {
        double range = PGConfig.SERVER.adminTextDeleteRange.get();
        ServerLevel level = p.serverLevel();
        Entity best = null;
        double bestSq = range * range;
        for (Entity e : level.getEntities((Entity) null, new AABB(p.blockPosition()).inflate(range), FloatText::isFloatText)) {
            double d = e.distanceToSqr(p);
            if (d <= bestSq) { bestSq = d; best = e; }
        }
        if (best == null) return AdminTools.err("附近 " + (int) range + " 格內沒有浮空文字。");
        CompoundTag data = new CompoundTag();
        best.saveWithoutId(data);
        data.putString("id", "minecraft:text_display");
        data.remove("UUID");
        String text = textOf(best);
        Log.get(p.server).push(level.dimension(), data);
        best.discard();
        return AdminTools.ok(String.format("已刪除浮空文字：%s（距離 %.1f 格）", text, Math.sqrt(bestSq)));
    }

    public static boolean isFloatText(Entity e) {
        return e instanceof Display.TextDisplay && e.getTags().contains(TAG);
    }

    /** 文字內容（去掉色碼）。 */
    public static String textOf(Entity e) {
        CompoundTag t = new CompoundTag();
        e.saveWithoutId(t);
        return textOf(t);
    }

    private static String textOf(CompoundTag t) {
        try {
            Component c = Component.Serializer.fromJson(t.getString("text"));
            return c == null ? "" : plain(c.getString());
        } catch (RuntimeException ex) {
            return t.getString("text");
        }
    }

    private static String plain(String s) {
        return s.replaceAll("§.", "");
    }

    // ── B10 復原 ────────────────────────────────────────────────────────────

    /** 復原最近一筆刪除紀錄（物品與 /pg text undo 共用）。 */
    public static Component undo(MinecraftServer server) {
        Log log = Log.get(server);
        Log.Entry entry = log.pop();
        if (entry == null) return AdminTools.err("沒有可復原的浮空文字。");
        ServerLevel level = server.getLevel(entry.dim());
        if (level == null) return AdminTools.err("找不到原本的世界（" + entry.dim().location() + "），這筆紀錄已移除。");
        ListTag posTag = entry.data().getList("Pos", 6);
        Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
        level.getChunk((int) Math.floor(pos.x) >> 4, (int) Math.floor(pos.z) >> 4); // 區塊未載入時先載入
        String text = textOf(entry.data());
        for (Entity e : level.getEntities((Entity) null, new AABB(pos, pos).inflate(0.5), FloatText::isFloatText)) {
            if (e.position().distanceTo(pos) <= 0.5 && textOf(e).equals(text)) {
                return AdminTools.err("該位置已有相同文字，這筆紀錄已移除。");
            }
        }
        Entity e = spawn(level, entry.data().copy(), pos);
        if (e == null) return AdminTools.err("復原失敗，這筆紀錄已移除。");
        return AdminTools.ok(String.format("已復原浮空文字：%s（%.1f, %.1f, %.1f）", text, pos.x, pos.y, pos.z));
    }

    /** 刪除紀錄（後進先出），存在伺服器存檔裡。 */
    public static final class Log extends SavedData {
        private static final String FILE = "provinggrounds_float_text_log";

        record Entry(ResourceKey<Level> dim, CompoundTag data) {}

        private final Deque<Entry> entries = new ArrayDeque<>();

        static Log get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Log::load, Log::new, FILE);
        }

        void push(ResourceKey<Level> dim, CompoundTag data) {
            entries.push(new Entry(dim, data));
            int max = PGConfig.SERVER.adminTextUndoMax.get();
            while (entries.size() > max) entries.removeLast();
            setDirty();
        }

        @Nullable
        Entry pop() {
            Entry e = entries.poll();
            if (e != null) setDirty();
            return e;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Entry e : entries) { // 由新到舊
                CompoundTag t = new CompoundTag();
                t.putString("dim", e.dim().location().toString());
                t.put("data", e.data());
                list.add(t);
            }
            tag.put("entries", list);
            return tag;
        }

        static Log load(CompoundTag tag) {
            Log log = new Log();
            ListTag list = tag.getList("entries", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
                if (dim == null) {
                    ProvingGroundsMod.LOGGER.warn("Float text log: invalid dimension '{}', skipped.", t.getString("dim"));
                    continue;
                }
                log.entries.addLast(new Entry(ResourceKey.create(Registries.DIMENSION, dim), t.getCompound("data")));
            }
            return log;
        }
    }
}
