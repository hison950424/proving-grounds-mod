package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.special.entity.DecoyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: DisconnectBodies.java
 * 目的: 掉線分身（計畫書 7 D）。玩家比賽中掉線時，在原地留下一個不動、可以被擊殺的分身（DecoyEntity 的 body 模式），
 *   避免「掉線躲死」。只在大逃殺與團隊死鬥／佔點／槍王之王的比賽中產生（迷你死鬥、大廳、訓練場、跑庫塔不產生）。
 *   - 掉線瞬間把狀態存進 SavedData（OfflineState）：分身 UUID、位置、血量、額外生命值、背包與裝備（大逃殺分身被殺時建立墓碑用）、
 *     背包欄、特殊物品欄、是否倒地、掉線時間（伺服器遊戲時間）。同一玩家同時只有一個分身。
 *   - 分身被殺：交給各模式（BattleRoyaleMode.onBodyKilled、ArenaMode.handleBodyDeath）照一般死亡處理（擊殺、擊殺紀錄、墓碑）。
 *     這些入口只用 UUID ＋ OfflineState，不為離線玩家造假的 ServerPlayer。
 *   - 回來：分身還活著 → 分身消失、玩家回到分身的位置與血量繼續；分身已被殺 → 依各模式規則（大逃殺旁觀；團隊模式重生倒數）。
 *   - 超時（disconnect_timeout）：各模式移出比賽（大逃殺視為淘汰，物品放進墓碑）。
 *   - 比賽結束、/pg reset、/pg arena stop：GameManager.reset 呼叫 clearAll，分身與狀態全部清掉。
 *   - 測試：/pg debug body spawn|return|kill <玩家>（模擬掉線：玩家變旁觀、模式把他當成離線）。
 */
public final class DisconnectBodies {

    private DisconnectBodies() {}

    /** 一位離線玩家的狀態。 */
    public static final class OfflineState {
        public UUID owner;
        public String name = "";
        public String mode = "";     // "br" 或 "arena"
        public UUID body;
        public Vec3 pos = Vec3.ZERO;
        public float health, absorption;
        public boolean downed;
        public boolean dead;          // 分身已經被殺（物品已經處理，回來時要清背包）
        public long since;            // 掉線時的伺服器遊戲時間
        public ListTag inventory = new ListTag();
        public CompoundTag backpack = new CompoundTag(), special = new CompoundTag();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", owner);
            t.putString("Name", name);
            t.putString("Mode", mode);
            if (body != null) t.putUUID("Body", body);
            t.putDouble("X", pos.x); t.putDouble("Y", pos.y); t.putDouble("Z", pos.z);
            t.putFloat("Health", health);
            t.putFloat("Absorption", absorption);
            t.putBoolean("Downed", downed);
            t.putBoolean("Dead", dead);
            t.putLong("Since", since);
            t.put("Inventory", inventory);
            t.put("Backpack", backpack);
            t.put("Special", special);
            return t;
        }

        static OfflineState load(CompoundTag t) {
            OfflineState s = new OfflineState();
            s.owner = t.getUUID("Owner");
            s.name = t.getString("Name");
            s.mode = t.getString("Mode");
            if (t.hasUUID("Body")) s.body = t.getUUID("Body");
            s.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
            s.health = t.getFloat("Health");
            s.absorption = t.getFloat("Absorption");
            s.downed = t.getBoolean("Downed");
            s.dead = t.getBoolean("Dead");
            s.since = t.getLong("Since");
            s.inventory = t.getList("Inventory", Tag.TAG_COMPOUND);
            s.backpack = t.getCompound("Backpack");
            s.special = t.getCompound("Special");
            return s;
        }

        /** 背包與裝備（Inventory 的格子編號 → 物品；0～8 快捷欄、9～35 背包、36～39 防具、40 副手）。 */
        public Map<Integer, ItemStack> items() {
            Map<Integer, ItemStack> out = new HashMap<>();
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag t = inventory.getCompound(i);
                int slot = t.getByte("Slot") & 255;
                ItemStack s = ItemStack.of(t);
                if (s.isEmpty()) continue;
                // Inventory.save 的編號：0～35 照原樣、防具 100～103、副手 150
                if (slot >= 100 && slot < 104) slot = 36 + (slot - 100);
                else if (slot == 150) slot = 40;
                out.put(slot, s);
            }
            return out;
        }
    }

    // ── 存檔 ────────────────────────────────────────────────────────────────

    public static final class Store extends SavedData {
        private static final String NAME = "provinggrounds_offline";
        final Map<UUID, OfflineState> states = new HashMap<>();

        static Store load(CompoundTag tag) {
            Store s = new Store();
            ListTag list = tag.getList("States", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                OfflineState st = OfflineState.load(list.getCompound(i));
                s.states.put(st.owner, st);
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (OfflineState st : states.values()) list.add(st.save());
            tag.put("States", list);
            return tag;
        }
    }

    @Nullable private static Store cached;

    private static Store store(MinecraftServer server) {
        if (cached == null) cached = server.overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, Store.NAME);
        return cached;
    }

    /** 伺服器關閉時清掉快取（下次開服重新讀檔）。 */
    public static void onServerStopped() { cached = null; SIMULATED.clear(); }

    @Nullable
    public static OfflineState get(MinecraftServer server, UUID owner) { return store(server).states.get(owner); }

    /** 分身實體還是這位玩家目前的分身嗎（分身每 tick 自己檢查，不是就消失）。 */
    public static boolean isActiveBody(UUID owner, UUID bodyId) {
        MinecraftServer server = GameManager.get().getServer();
        if (server == null) return false;
        OfflineState s = store(server).states.get(owner);
        return s != null && !s.dead && bodyId.equals(s.body);
    }

    // ── 模擬掉線（測試指令）─────────────────────────────────────────────────

    private static final Set<UUID> SIMULATED = new HashSet<>();

    /** 測試用「模擬掉線」中（模式要把他當成不在線上）。 */
    public static boolean isSimulated(UUID id) { return SIMULATED.contains(id); }

    public static void setSimulated(UUID id, boolean on) { if (on) SIMULATED.add(id); else SIMULATED.remove(id); }

    // ── 建立、找、移除 ──────────────────────────────────────────────────────

    /** 掉線（或模擬掉線）：在玩家位置建立分身並存下狀態。mode = "br" 或 "arena"。 */
    public static OfflineState spawn(ServerPlayer p, String mode, boolean downed) {
        MinecraftServer server = p.server;
        remove(server, p.getUUID()); // 同一玩家同時只有一個分身
        ServerLevel level = p.serverLevel();
        DecoyEntity body = DecoyEntity.createBody(level, p, downed);
        OfflineState s = new OfflineState();
        s.owner = p.getUUID();
        s.name = p.getGameProfile().getName();
        s.mode = mode;
        s.body = body.getUUID();
        s.pos = p.position();
        s.health = p.getHealth();
        s.absorption = p.getAbsorptionAmount();
        s.downed = downed;
        s.since = level.getGameTime();
        s.inventory = p.getInventory().save(new ListTag());
        s.backpack = com.provinggrounds.modes.br.Backpack.container(p).getItem(0).save(new CompoundTag());
        s.special = com.provinggrounds.special.SpecialSlot.get(p).save(new CompoundTag());
        Store st = store(server);
        st.states.put(s.owner, s);
        st.setDirty();
        level.addFreshEntity(body);
        ProvingGroundsMod.LOGGER.info("Disconnect body for {} ({}) at {}", s.name, mode, BlockPos.containing(s.pos).toShortString());
        return s;
    }

    /** 分身實體（在已載入的區塊裡才找得到；沒載入時以 OfflineState 為準）。 */
    @Nullable
    public static DecoyEntity bodyOf(MinecraftServer server, UUID owner) {
        OfflineState s = store(server).states.get(owner);
        if (s == null || s.body == null) return null;
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(s.body);
            if (e instanceof DecoyEntity d && d.isAlive()) return d;
        }
        return null;
    }

    /** 移除分身與狀態（玩家回來、超時、比賽結束）。回傳移除前的狀態。 */
    @Nullable
    public static OfflineState remove(MinecraftServer server, UUID owner) {
        Store st = store(server);
        DecoyEntity body = bodyOf(server, owner);
        OfflineState s = st.states.remove(owner);
        if (s != null) st.setDirty();
        if (body != null) body.discard();
        return s;
    }

    @Nullable
    private static final java.lang.reflect.Field SPAWN_INVULN = findSpawnInvuln();

    @Nullable
    private static java.lang.reflect.Field findSpawnInvuln() {
        try {
            return net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ServerPlayer.class, "f_8921_");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 確保斷線重連的玩家不會殘留無敵狀態：
     *   - 清除實體 invulnerable、非創造／旁觀時的 abilities.invulnerable、invulnerableTime、
     *     原版登入 60 tick 無敵（spawnInvulnerableTime）、以及殘留的無實體化效果。
     */
    public static void clearInvulnerability(ServerPlayer p, boolean clearSpawnGrace) {
        p.setInvulnerable(false);
        p.setCamera(p);
        if (!p.isCreative() && !p.isSpectator()) {
            if (p.getAbilities().invulnerable) {
                p.getAbilities().invulnerable = false;
                p.onUpdateAbilities();
            }
            p.invulnerableTime = 0;
            p.removeEffect(com.provinggrounds.special.SpecialRegistry.ETHEREAL.get());
            if (clearSpawnGrace && SPAWN_INVULN != null) {
                try { SPAWN_INVULN.setInt(p, 0); } catch (IllegalAccessException ignored) {}
            }
        }
    }

    /**
     * 玩家回來：分身還活著 → 回到分身的位置與血量（分身消失）；回傳狀態（null = 沒有分身）。
     * 分身已被殺（dead）→ 清掉背包（物品已經進了墓碑或照模式規則處理），回傳狀態讓模式處理。
     */
    @Nullable
    public static OfflineState restore(ServerPlayer p) {
        MinecraftServer server = p.server;
        OfflineState s = store(server).states.get(p.getUUID());
        if (s == null) return null;
        DecoyEntity body = bodyOf(server, p.getUUID());
        remove(server, p.getUUID());
        if (s.dead) return s;
        Vec3 at = body != null ? body.position() : s.pos;
        float hp = body != null ? body.getHealth() : s.health;
        float abs = body != null ? body.getAbsorptionAmount() : s.absorption;
        p.teleportTo(p.serverLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
        p.setHealth(Math.max(1, Math.min(p.getMaxHealth(), hp)));
        p.setAbsorptionAmount(abs);
        p.fallDistance = 0;
        clearInvulnerability(p, true);
        return s;
    }

    /** 分身被殺（DecoyEntity.die 呼叫）。 */
    public static void onKilled(DecoyEntity body, DamageSource source) {
        MinecraftServer server = body.getServer();
        UUID owner = body.owner();
        if (server == null || owner == null) return;
        OfflineState s = store(server).states.get(owner);
        if (s == null || s.dead || !body.getUUID().equals(s.body)) return;
        s.dead = true;
        s.pos = body.position();
        store(server).setDirty();
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer k ? k : null;
        if ("br".equals(s.mode)) BattleRoyaleMode.get().onBodyKilled(s, killer, source);
        else ArenaManager.onBodyKilled(s, killer);
    }

    /** 倒地的分身流血到 0：當作被擊倒的人殺死（沒有擊倒者就是自然死亡）。 */
    public static void bleedOut(DecoyEntity body) {
        MinecraftServer server = body.getServer();
        if (server == null) return;
        body.hurt(server.overworld().damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** 比賽結束、重置：清掉所有分身與狀態。 */
    public static void clearAll(@Nullable MinecraftServer server) {
        SIMULATED.clear();
        if (server == null) return;
        Store st = store(server);
        for (UUID id : new ArrayList<>(st.states.keySet())) remove(server, id);
        st.states.clear();
        st.setDirty();
    }

    /** 通知同隊（小字）。 */
    public static void notifyTeam(MinecraftServer server, UUID owner, String text) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(owner)) continue;
            if (TeamManager.get().isSameTeam(p.getUUID(), owner)) {
                p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** 所有離線狀態（模式檢查超時用）。 */
    public static List<OfflineState> all(MinecraftServer server) {
        return new ArrayList<>(store(server).states.values());
    }
}
