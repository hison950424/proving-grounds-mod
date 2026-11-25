package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.modes.arena.ArenaMode;
import com.provinggrounds.modes.arena.DomMode;
import com.provinggrounds.network.DomGearPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: DomGear.java
 * 目的: 佔點的特殊物品裝備（計畫書 8）。執行者：伺服器（強度表兩邊都能讀）。
 *   - 佔點沒有職業；每位玩家在大廳（或比賽中準備階段、在基地裡）自選三個特殊物品當「裝備」。
 *   - 裝備用完不會消失，而是依強度進入冷卻（弱 30、中 60、強 120 秒，可在設定調）；每回合開始冷卻歸零。
 *   - 商店可以買一次性的「額外特殊物品」（價格依強度），用完就沒了，不進冷卻；最多帶 extra_max_v1 個。
 *   - 長按 5 開輪盤，選要放進特殊物品格的是哪一個（裝備 3 個＋額外的），再按 Q 使用。
 *   - 特殊物品格裡放的是代表物品，NBT pg_dom_gear 記著它是第幾個裝備（0～2）或額外的（10）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class DomGear {

    public static final String TAG = "pg_dom_gear";
    public static final int EXTRA = 10;
    public static final int SLOTS = 3;

    private DomGear() {}

    // ═════════════════════════════════════════════════════════════════════════
    // 強度表（評估理由寫在 docs/PLAN8_DOM_SPECIALS.md）
    // ═════════════════════════════════════════════════════════════════════════

    private static final Map<SpecialType, Integer> BUILTIN = new EnumMap<>(SpecialType.class);

    private static void tier(int t, SpecialType... types) { for (SpecialType s : types) BUILTIN.put(s, t); }

    static {
        // 弱：只給一點資訊、一點機動、或防守用的小工具
        tier(1, SpecialType.ENEMY_SCANNER_BASIC, SpecialType.ENEMY_SCANNER, SpecialType.BLAST_SCANNER_BASIC, SpecialType.BLAST_SCANNER,
            SpecialType.SPRING_SHOES, SpecialType.SMOKE_BURST, SpecialType.SILENT_BOOTS, SpecialType.DECOY, SpecialType.RECON_MINE,
            SpecialType.SNOW_WALL, SpecialType.ARMOR_REPAIR);
        // 中：能直接改變一場交火（控場、傷害、救命、整隊資訊）
        tier(2, SpecialType.BLIND_ORB, SpecialType.ADRENALINE, SpecialType.INVIS_CLOAK, SpecialType.ENEMY_SCANNER_PLUS,
            SpecialType.BLAST_SCANNER_PLUS, SpecialType.RADAR_PULSE, SpecialType.KNOCKBACK_SHIELD, SpecialType.WIND_BOMB,
            SpecialType.LAND_MINE, SpecialType.FIRE_MINE, SpecialType.SHOCK_MINE, SpecialType.WARP_PEARL, SpecialType.GAS_GRENADE,
            SpecialType.FIRST_AID_SHOT, SpecialType.ARMOR_REPAIR_PLUS, SpecialType.ROOT_ORB, SpecialType.FIRE_DRILL, SpecialType.RECALL);
        // 強：一個人就能翻盤（免死、大範圍傷害、癱瘓整區、賭命）
        tier(3, SpecialType.TOTEM, SpecialType.SHIELD_BUBBLE, SpecialType.BRAINWASH, SpecialType.RECALL_PLUS,
            SpecialType.GAMBLER_ROD, SpecialType.GAMBLER_ROD_PLUS, SpecialType.THOR_HAMMER, SpecialType.THOR_HAMMER_PLUS);
        // 沒寫的 = 0（佔點用不到）：信號槍（空投）、三種假箱（佔點沒有箱子）、圈外庇護（沒有毒圈）、三種重新跳傘
    }

    private static List<? extends String> cachedList;
    private static Map<SpecialType, Integer> cachedOverrides = Map.of();

    /** 強度：1 弱、2 中、3 強、0 佔點不能用。 */
    public static int tier(SpecialType t) {
        try {
            List<? extends String> list = PGConfig.SERVER.arena.domGearTiers.get();
            if (list != cachedList) {
                Map<SpecialType, Integer> m = new EnumMap<>(SpecialType.class);
                for (String s : list) {
                    String[] kv = s.split("=");
                    if (kv.length != 2) continue;
                    SpecialType st = SpecialType.byId(kv[0].trim());
                    try { if (st != null) m.put(st, Math.max(0, Math.min(3, Integer.parseInt(kv[1].trim())))); } catch (NumberFormatException ignored) {}
                }
                cachedOverrides = m;
                cachedList = list;
            }
            Integer o = cachedOverrides.get(t);
            if (o != null) return o;
        } catch (IllegalStateException ignored) {}
        return BUILTIN.getOrDefault(t, 0);
    }

    private static int at(int tier, boolean price) {
        int[] def = price ? new int[] { 150, 300, 600 } : new int[] { 30, 60, 120 };
        int i = Math.max(0, Math.min(2, tier - 1));
        try {
            List<? extends Integer> l = price ? PGConfig.SERVER.arena.domGearPrice.get() : PGConfig.SERVER.arena.domGearCooldown.get();
            if (!l.isEmpty()) return Math.max(0, l.get(Math.min(i, l.size() - 1)));
        } catch (IllegalStateException ignored) {}
        return def[i];
    }

    public static int cooldownSeconds(SpecialType t) { return at(tier(t), false); }

    public static int price(SpecialType t) { return at(tier(t), true); }

    public static String tierName(int tier) { return switch (tier) { case 1 -> "弱"; case 2 -> "中"; case 3 -> "強"; default -> "—"; }; }

    public static int tierColor(int tier) { return switch (tier) { case 1 -> 0x7FD67F; case 2 -> 0x6FB6FF; case 3 -> 0xFF8A5C; default -> 0x909090; }; }

    /** 佔點能用的特殊物品（照強度、再照清單順序）。 */
    public static List<SpecialType> allowed() {
        List<SpecialType> out = new ArrayList<>();
        for (SpecialType t : SpecialType.values()) if (tier(t) > 0) out.add(t);
        out.sort(Comparator.<SpecialType>comparingInt(t -> tier(t)).thenComparingInt(Enum::ordinal));
        return out;
    }

    private static int extraMax() {
        try { return PGConfig.SERVER.arena.domGearExtraMax.get(); } catch (IllegalStateException e) { return 3; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 大廳選擇（跟職業一樣存在記憶體；伺服器重開會回到預設）
    // ═════════════════════════════════════════════════════════════════════════

    private static final SpecialType[] DEFAULT = { SpecialType.SPRING_SHOES, SpecialType.SMOKE_BURST, SpecialType.FIRST_AID_SHOT };
    private static final Map<UUID, SpecialType[]> CHOICE = new HashMap<>();

    public static SpecialType[] choice(UUID id) {
        SpecialType[] c = CHOICE.computeIfAbsent(id, k -> DEFAULT.clone());
        for (int i = 0; i < SLOTS; i++) if (c[i] == null || tier(c[i]) == 0) c[i] = firstUnused(c);
        return c;
    }

    private static SpecialType firstUnused(SpecialType[] c) {
        for (SpecialType t : allowed()) if (!Arrays.asList(c).contains(t)) return t;
        return SpecialType.SMOKE_BURST;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 比賽中
    // ═════════════════════════════════════════════════════════════════════════

    private static final class Extra {
        final SpecialType type;
        int uses;
        Extra(SpecialType type) { this.type = type; this.uses = type.twoUses() ? 2 : 1; }
    }

    private static final class State {
        final SpecialType[] gear = new SpecialType[SLOTS];
        final long[] readyAt = new long[SLOTS];   // 伺服器 tick；到了才能再用
        final List<Extra> extras = new ArrayList<>();
        int selected;                              // 0～2 = 裝備；3 起 = 額外的第幾個
        int lastGear;                              // 額外的用完後切回這個裝備
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Nullable
    private static DomMode dom() {
        ArenaMode m = ArenaManager.current();
        return m instanceof DomMode d && !d.isEnding() ? d : null;
    }

    /** 這位玩家正在用佔點裝備（佔點進行中而且是參賽者）。 */
    public static boolean active(ServerPlayer p) {
        return dom() != null && STATES.containsKey(p.getUUID());
    }

    private static long now() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? 0 : s.getTickCount();
    }

    /** 開局：照大廳的選擇發裝備（DomMode.onStart 呼叫）。 */
    public static void startMatch(Collection<ServerPlayer> players) {
        STATES.clear();
        for (ServerPlayer p : players) {
            State st = new State();
            SpecialType[] c = choice(p.getUUID());
            System.arraycopy(c, 0, st.gear, 0, SLOTS);
            STATES.put(p.getUUID(), st);
            refreshSlot(p, st);
            p.sendSystemMessage(Component.literal("[佔點] 你的特殊物品：" + names(st.gear) + "。長按 5 切換、按 Q 使用；用完會進入冷卻，不會消失。")
                .withStyle(ChatFormatting.AQUA));
        }
    }

    private static String names(SpecialType[] g) {
        StringBuilder sb = new StringBuilder();
        for (SpecialType t : g) { if (sb.length() > 0) sb.append("、"); sb.append(t.zh); }
        return sb.toString();
    }

    /** 每回合戰鬥開始：所有裝備冷卻歸零（DomMode.startRound 呼叫）。 */
    public static void resetCooldowns() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, State> e : STATES.entrySet()) {
            Arrays.fill(e.getValue().readyAt, 0);
            ServerPlayer p = s == null ? null : s.getPlayerList().getPlayer(e.getKey());
            if (p != null) sync(p);
        }
    }

    /** 比賽結束：收回特殊物品格裡的代表物品、清掉賭徒釣竿的生命增減（DomMode.onFinish 呼叫）。 */
    public static void endMatch(MinecraftServer server) {
        for (UUID id : new ArrayList<>(STATES.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (isGearStack(SpecialSlot.get(p))) SpecialSlot.clear(p);
            NewSpecials.clearGamble(p);
        }
        Set<UUID> ids = new HashSet<>(STATES.keySet());
        STATES.clear();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sync(p);
        }
    }

    private static ItemStack stackFor(State st) {
        if (st.selected >= SLOTS && st.selected - SLOTS < st.extras.size()) {
            Extra x = st.extras.get(st.selected - SLOTS);
            ItemStack s = new ItemStack(SpecialRegistry.item(x.type));
            s.getOrCreateTag().putInt(TAG, EXTRA);
            if (x.type.twoUses()) s.getOrCreateTag().putInt(SpecialItem.USES_TAG, x.uses);
            return s;
        }
        st.selected = Math.max(0, Math.min(SLOTS - 1, st.selected));
        ItemStack s = new ItemStack(SpecialRegistry.item(st.gear[st.selected]));
        s.getOrCreateTag().putInt(TAG, st.selected);
        return s;
    }

    private static void refreshSlot(ServerPlayer p, State st) {
        SpecialSlot.set(p, stackFor(st));
        sync(p);
    }

    public static boolean isGearStack(ItemStack s) {
        return !s.isEmpty() && s.getTag() != null && s.getTag().contains(TAG);
    }

    private static int cooldownLeft(State st, int i) {
        return (int) Math.max(0, st.readyAt[i] - now());
    }

    // ── 客戶端的操作（DomGearActionPacket） ──────────────────────────────────

    /** 輪盤選好了：index 0～2 = 裝備、3 起 = 額外的。 */
    public static void select(ServerPlayer p, int index) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null || p.isSpectator()) return;
        if (ActionLock.brainwashed(p)) return;
        if (index < 0 || index >= SLOTS + st.extras.size()) return;
        if (KnockbackShield.active(p)) { msg(p, "舉著擊退盾時不能切換"); return; }
        st.selected = index;
        if (index < SLOTS) st.lastGear = index;
        refreshSlot(p, st);
    }

    /** 換裝備：大廳隨時可以；比賽中只有準備／結算階段、在自己的基地裡可以。 */
    public static void setGear(ServerPlayer p, int slot, int typeOrdinal) {
        SpecialType[] all = SpecialType.values();
        if (slot < 0 || slot >= SLOTS || typeOrdinal < 0 || typeOrdinal >= all.length) return;
        SpecialType t = all[typeOrdinal];
        if (tier(t) == 0) { msg(p, t.zh + " 在佔點不能用"); return; }
        SpecialType[] c = choice(p.getUUID());
        State st = STATES.get(p.getUUID());
        DomMode d = dom();
        if (st != null && d != null) {
            ArenaMode.ArenaPlayer ap = d.player(p.getUUID());
            if (d.fighting()) { msg(p, "戰鬥中不能換裝備，等下一回合的準備時間"); return; }
            if (ap == null || !d.inBase(p, ap)) { msg(p, "回到自己的基地才能換裝備"); return; }
        }
        // 已經在別格的話就兩格互換（三個一定不重複）
        for (int i = 0; i < SLOTS; i++) if (i != slot && c[i] == t) c[i] = c[slot];
        c[slot] = t;
        if (st != null && d != null) {
            System.arraycopy(c, 0, st.gear, 0, SLOTS);
            Arrays.fill(st.readyAt, 0);
            p.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.MASTER, 0.8f, 1.2f);
            refreshSlot(p, st);
        } else {
            sync(p);
        }
    }

    /** 商店：買一個一次性的額外特殊物品。 */
    public static void buy(ServerPlayer p, int typeOrdinal) {
        SpecialType[] all = SpecialType.values();
        State st = STATES.get(p.getUUID());
        DomMode d = dom();
        if (st == null || d == null || typeOrdinal < 0 || typeOrdinal >= all.length) return;
        SpecialType t = all[typeOrdinal];
        if (tier(t) == 0) return;
        if (Arrays.asList(st.gear).contains(t)) { msg(p, "你的裝備已經有" + t.zh + "了"); return; }
        if (st.extras.size() >= extraMax()) { msg(p, "額外特殊物品最多帶 " + extraMax() + " 個"); return; }
        if (!d.trySpend(p, price(t), t.zh)) return;
        st.extras.add(new Extra(t));
        sync(p);
    }

    // ── SpecialUse 的掛鉤 ────────────────────────────────────────────────────

    /** 現在不能用的原因（null = 可以用）。只處理特殊物品格裡的佔點代表物品。 */
    @Nullable
    public static String blockReason(ServerPlayer p, ItemStack stack) {
        if (!isGearStack(stack)) return null;
        State st = STATES.get(p.getUUID());
        DomMode d = dom();
        if (st == null || d == null) { SpecialSlot.clear(p); return "比賽已經結束"; } // 比賽外殘留的代表物品
        if (!d.fighting()) return "準備階段不能使用特殊物品";
        int idx = stack.getTag().getInt(TAG);
        if (idx < SLOTS) {
            int left = cooldownLeft(st, idx);
            if (left > 0) return st.gear[idx].zh + " 冷卻中，還要 " + (left + 19) / 20 + " 秒";
        }
        return null;
    }

    /** 用掉了（keep = 回溯點第一次使用只記錄位置，不算用掉）。 */
    public static void afterUse(ServerPlayer p, ItemStack stack, boolean keep) {
        State st = STATES.get(p.getUUID());
        if (st == null || keep) return;
        int idx = stack.getTag() == null ? -1 : stack.getTag().getInt(TAG);
        if (idx >= 0 && idx < SLOTS) {
            st.readyAt[idx] = now() + cooldownSeconds(st.gear[idx]) * 20L;
            refreshSlot(p, st);
            return;
        }
        int x = st.selected - SLOTS;
        if (x < 0 || x >= st.extras.size()) { refreshSlot(p, st); return; }
        Extra e = st.extras.get(x);
        if (--e.uses <= 0) {
            st.extras.remove(x);
            st.selected = st.lastGear;
        }
        refreshSlot(p, st);
    }

    /** 傳送珠飛不出去等「退回」：裝備的冷卻取消、額外的還回去。回傳 true = 已處理。 */
    public static boolean refund(ServerPlayer p, SpecialType t) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        for (int i = 0; i < SLOTS; i++) {
            if (st.gear[i] == t && st.readyAt[i] > now()) { st.readyAt[i] = 0; refreshSlot(p, st); return true; }
        }
        st.extras.add(new Extra(t));
        sync(p);
        return true;
    }

    /** 回溯點 60 秒沒用完自動消失：裝備進入冷卻、額外的用掉。回傳 true = 已處理。 */
    public static boolean recallExpired(ServerPlayer p, SpecialType t) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        ItemStack cur = SpecialSlot.get(p);
        if (isGearStack(cur) && SpecialItem.typeOf(cur) == t) { afterUse(p, cur, false); return true; }
        for (int i = 0; i < SLOTS; i++) {
            if (st.gear[i] == t) { st.readyAt[i] = now() + cooldownSeconds(t) * 20L; refreshSlot(p, st); return true; }
        }
        for (int i = 0; i < st.extras.size(); i++) {
            if (st.extras.get(i).type == t) {
                st.extras.remove(i);
                if (st.selected >= SLOTS) st.selected = st.lastGear;
                refreshSlot(p, st);
                return true;
            }
        }
        return true;
    }

    /** 賭徒釣竿掉出的特殊物品：佔點裡變成額外特殊物品（超過上限就沒有）。回傳 true = 已處理。 */
    public static boolean giveLoot(ServerPlayer p, ItemStack loot) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        SpecialType t = SpecialItem.typeOf(loot);
        if (t == null || tier(t) == 0) t = randomAllowed(p);
        if (st.extras.size() >= extraMax()) { msg(p, "額外特殊物品已滿，" + t.zh + " 沒拿到"); return true; }
        st.extras.add(new Extra(t));
        p.displayClientMessage(Component.literal("獲得額外特殊物品：" + t.zh + "（長按 5 選擇）").withStyle(ChatFormatting.GOLD), false);
        sync(p);
        return true;
    }

    private static SpecialType randomAllowed(ServerPlayer p) {
        List<SpecialType> a = allowed();
        return a.get(p.getRandom().nextInt(a.size()));
    }

    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }

    // ── 同步 ─────────────────────────────────────────────────────────────────

    public static void sync(ServerPlayer p) {
        SpecialType[] c = choice(p.getUUID());
        int[] choice = new int[SLOTS], gear = new int[SLOTS], cd = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) choice[i] = c[i].ordinal();
        State st = dom() == null ? null : STATES.get(p.getUUID());
        List<int[]> extras = new ArrayList<>();
        int selected = 0;
        if (st != null) {
            for (int i = 0; i < SLOTS; i++) { gear[i] = st.gear[i].ordinal(); cd[i] = cooldownLeft(st, i); }
            for (Extra e : st.extras) extras.add(new int[] { e.type.ordinal(), e.uses });
            selected = st.selected;
        }
        PGNetwork.sendTo(p, new DomGearPacket(st != null, choice, gear, cd, extras, selected));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    /** 每 5 秒校正一次客戶端的冷卻倒數。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STATES.isEmpty() || event.getServer().getTickCount() % 100 != 0) return;
        if (dom() == null) return;
        for (UUID id : STATES.keySet()) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p != null) sync(p);
        }
    }
}
