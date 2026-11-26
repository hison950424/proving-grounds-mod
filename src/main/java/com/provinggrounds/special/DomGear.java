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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: DomGear.java
 * 目的: 團隊模式的特殊物品規則（計畫書 8，使用者第二次回饋後的版本）。執行者：伺服器（強度表兩邊都能讀）。
 *   只在大廳的「特殊物品模式」開著時有效。
 *   - 佔點：每位玩家在大廳選「一種」特殊物品，整場都是它；每一回合只能用一次（下回合戰鬥開始時恢復），準備階段不能用。
 *     選的越強，自己在商店買東西越貴（弱 +10%、中 +25%、強 +50%，可在設定調）。
 *   - 團隊死鬥：每次出生（含開局）隨機拿到一個、擊殺一名敵人再拿一個（特殊物品格空著才放得進去），用完就消失。
 *   特殊物品格裡放的是代表物品，NBT pg_dom_gear 標記它是佔點的整場特殊物品（拿不出來、用完不刪）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class DomGear {

    public static final String TAG = "pg_dom_gear";
    /** 佔點特殊道具剩餘冷卻回合數（0 = 可用、1 = 下回合可用、2+ = 還需 N 回合；存於代表物品 NBT 同步給客戶端顯示）。 */
    public static final String COOLDOWN_TAG = "pg_dom_cd";

    private DomGear() {}

    // ═════════════════════════════════════════════════════════════════════════
    // 強度表（評估理由寫在 docs/PLAN8_DOM_SPECIALS.md）
    // ═════════════════════════════════════════════════════════════════════════

    private static final Map<SpecialType, Integer> BUILTIN = new EnumMap<>(SpecialType.class);
    /** 每種特殊道具各自的獨立商店加價（%）：因為已改為弱 1／中 2／強 3 回合冷卻，整體加價懲罰大幅調降。 */
    private static final Map<SpecialType, Integer> BUILTIN_MARKUP = new EnumMap<>(SpecialType.class);

    private static void gear(int t, SpecialType type, int markupPct) {
        BUILTIN.put(type, t);
        BUILTIN_MARKUP.put(type, markupPct);
    }

    static {
        // 弱（每回合可用，原一律 +10% → 依實戰價值細分為 +2%～+8%）
        gear(1, SpecialType.LOOK_UP_ALL,         2);
        gear(1, SpecialType.CRUDE_SWAPPER,       2);
        gear(1, SpecialType.ICE_SKATES,          3);
        gear(1, SpecialType.BLAST_SCANNER_BASIC, 3);
        gear(1, SpecialType.ENEMY_SCANNER_BASIC, 4);
        gear(1, SpecialType.LOOK_UP_ONE,         4);
        gear(1, SpecialType.DECOY,               4);
        gear(1, SpecialType.BLAST_SCANNER,       5);
        gear(1, SpecialType.ENEMY_SCANNER,       5);
        gear(1, SpecialType.RECON_MINE,          5);
        gear(1, SpecialType.SPRING_SHOES,        5);
        gear(1, SpecialType.SMOKE_BURST,         6);
        gear(1, SpecialType.SNOW_WALL,           6);
        gear(1, SpecialType.ARMOR_REPAIR,        7);
        gear(1, SpecialType.SILENT_BOOTS,        8);

        // 中（每 2 回合可用，原一律 +25% → 依實戰價值細分為 +8%～+16%）
        gear(2, SpecialType.ICE_SKATES_ALL,      8);
        gear(2, SpecialType.SPACE_SWAPPER,       9);
        gear(2, SpecialType.PARANOIA,            9);
        gear(2, SpecialType.OVERHEAT_CURSE,      9);
        gear(2, SpecialType.MISFIRE_CURSE,       10);
        gear(2, SpecialType.HUD_JAMMER,          10);
        gear(2, SpecialType.SHOCK_MINE,          10);
        gear(2, SpecialType.FIRE_MINE,           11);
        gear(2, SpecialType.MAGNET_GRENADE,      11);
        gear(2, SpecialType.WIND_BOMB,           11);
        gear(2, SpecialType.BLAST_SCANNER_PLUS,  11);
        gear(2, SpecialType.LAND_MINE,           12);
        gear(2, SpecialType.BLIND_ORB,           12);
        gear(2, SpecialType.COCKROACH,           12);
        gear(2, SpecialType.FIRE_DRILL,          12);
        gear(2, SpecialType.GAS_GRENADE,         12);
        gear(2, SpecialType.ROOT_ORB,            13);
        gear(2, SpecialType.KNOCKBACK_SHIELD,    13);
        gear(2, SpecialType.FIRST_AID_SHOT,      13);
        gear(2, SpecialType.ENEMY_SCANNER_PLUS,  13);
        gear(2, SpecialType.RADAR_PULSE,         14);
        gear(2, SpecialType.WARP_PEARL,          14);
        gear(2, SpecialType.ADRENALINE,          14);
        gear(2, SpecialType.RECALL,              14);
        gear(2, SpecialType.ARMOR_REPAIR_PLUS,   15);
        gear(2, SpecialType.ETHEREAL,            15);
        gear(2, SpecialType.INVIS_CLOAK,         16);

        // 強（每 3 回合可用，原一律 +50% → 依實戰價值細分為 +18%～+28%）
        gear(3, SpecialType.GAMBLER_ROD,         18);
        gear(3, SpecialType.FRIEND_OR_FOE,       19);
        gear(3, SpecialType.RED_LIGHT,           20);
        gear(3, SpecialType.GAMBLER_ROD_PLUS,    20);
        gear(3, SpecialType.BRAINWASH,           22);
        gear(3, SpecialType.RECALL_PLUS,         23);
        gear(3, SpecialType.DOMAIN_EXPANSION,    24);
        gear(3, SpecialType.SHIELD_BUBBLE,       25);
        gear(3, SpecialType.TOTEM,               28);

        // 沒寫的 = 0（室內團隊模式排除）：
        //   大逃殺專屬：信號槍（無空投）、三種假箱與擬態（無物資箱）、圈外庇護（無毒圈）
        //   室內戰場不合理：三種重新跳傘（會飛出屋頂／場外）、兩把雷神錘（室內天降落雷與跨樓層雷擊）
    }

    private static List<? extends String> cachedList;
    private static Map<SpecialType, Integer> cachedOverrides = Map.of();
    private static List<? extends String> cachedMarkupList;
    private static Map<SpecialType, Integer> cachedMarkupOverrides = Map.of();

    /** 強度：1 弱、2 中、3 強、0 團隊模式不能用。 */
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

    /** 單一道具在佔點模式的商店加價 %（先看設定覆寫，再看各道具獨立加價表，最後回退到強度預設值）。 */
    public static int markupPercent(@Nullable SpecialType t) {
        if (t == null) return 0;
        int tr = tier(t);
        if (tr <= 0) return 0;
        try {
            List<? extends String> list = PGConfig.SERVER.arena.domGearItemMarkups.get();
            if (list != cachedMarkupList) {
                Map<SpecialType, Integer> m = new EnumMap<>(SpecialType.class);
                for (String s : list) {
                    String[] kv = s.split("=");
                    if (kv.length != 2) continue;
                    SpecialType st = SpecialType.byId(kv[0].trim());
                    try { if (st != null) m.put(st, Math.max(0, Math.min(200, Integer.parseInt(kv[1].trim())))); } catch (NumberFormatException ignored) {}
                }
                cachedMarkupOverrides = m;
                cachedMarkupList = list;
            }
            Integer o = cachedMarkupOverrides.get(t);
            if (o != null) return o;
        } catch (IllegalStateException ignored) {}
        Integer b = BUILTIN_MARKUP.get(t);
        return b != null ? b : markupPercent(tr);
    }

    /** 各強度的備用預設加價 %（當道具沒有單獨設定加價時使用）。 */
    public static int markupPercent(int tier) {
        if (tier <= 0) return 0;
        int[] def = { 5, 12, 22 };
        int i = Math.min(2, tier - 1);
        try {
            List<? extends Integer> l = PGConfig.SERVER.arena.domGearMarkup.get();
            if (!l.isEmpty()) return Math.max(0, l.get(Math.min(i, l.size() - 1)));
        } catch (IllegalStateException ignored) {}
        return def[i];
    }

    /** 加價後的價格（依該道具的獨立加價 %；伺服器扣錢與客戶端顯示用同一個算法）。 */
    public static int applyMarkup(int price, @Nullable SpecialType type) {
        int pct = markupPercent(type);
        return pct <= 0 ? price : (int) Math.round(price * (100 + pct) / 100.0);
    }

    public static int applyMarkup(int price, int tier) {
        return (int) Math.round(price * (100 + markupPercent(tier)) / 100.0);
    }

    public static String tierName(int tier) { return switch (tier) { case 1 -> "弱"; case 2 -> "中"; case 3 -> "強"; default -> "—"; }; }

    /** 佔點模式使用後冷卻幾回合（弱 1 = 每回合都能用、中 2 = 隔 1 回合、強 3 = 隔 2 回合）。 */
    public static int cooldownRounds(int tier) { return Math.max(1, Math.min(3, tier)); }

    public static int cooldownRounds(SpecialType t) { return cooldownRounds(tier(t)); }

    public static String cooldownLabel(int tier) {
        int r = cooldownRounds(tier);
        return r <= 1 ? "每回合可用" : "每 " + r + " 回合";
    }

    /** 從佔點代表物品讀取剩餘冷卻回合數（客戶端 HUD 顯示用）。 */
    public static int cooldownLeft(ItemStack s) {
        if (!isGearStack(s) || s.getTag() == null) return 0;
        return Math.max(0, s.getTag().getInt(COOLDOWN_TAG));
    }

    /** 團隊模式能用的特殊物品（照強度、再照商店加價由低到高、再照清單順序）。 */
    public static List<SpecialType> allowed() {
        List<SpecialType> out = new ArrayList<>();
        for (SpecialType t : SpecialType.values()) if (tier(t) > 0) out.add(t);
        out.sort(Comparator.<SpecialType>comparingInt(DomGear::tier)
            .thenComparingInt(DomGear::markupPercent)
            .thenComparingInt(Enum::ordinal));
        return out;
    }

    /** 隨機一個團隊模式能用的特殊物品（照箱子的權重抽，抽到不能用的就重抽）。 */
    public static ItemStack randomArena(ServerLevel level) {
        for (int i = 0; i < 20; i++) {
            ItemStack s = SpecialLoot.roll(level);
            SpecialType t = SpecialItem.typeOf(s);
            if (t != null && tier(t) > 0) return s;
        }
        List<SpecialType> a = allowed();
        return a.isEmpty() ? ItemStack.EMPTY : new ItemStack(SpecialRegistry.item(a.get(level.random.nextInt(a.size()))));
    }

    /** 團隊死鬥：發一個隨機特殊物品到特殊物品格（格子有東西就不給）。 */
    public static void giveRandom(ServerPlayer p, String why) {
        if (!SpecialSlot.get(p).isEmpty()) {
            p.displayClientMessage(Component.literal(why + "：特殊道具欄已經有東西，這次沒有拿到").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack s = randomArena(p.serverLevel());
        if (s.isEmpty()) return;
        SpecialType t = SpecialItem.typeOf(s);
        SpecialSlot.set(p, s);
        p.displayClientMessage(Component.literal(why + "：獲得 " + (t == null ? "特殊道具" : t.zh) + "（按 Q 使用）").withStyle(ChatFormatting.GOLD), true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 佔點：大廳選一種（存在記憶體，跟職業一樣；伺服器重開會回到預設）
    // ═════════════════════════════════════════════════════════════════════════

    private static final SpecialType DEFAULT = SpecialType.SMOKE_BURST;
    private static final Map<UUID, SpecialType> CHOICE = new HashMap<>();

    public static SpecialType choice(UUID id) {
        SpecialType c = CHOICE.get(id);
        return c == null || tier(c) == 0 ? DEFAULT : c;
    }

    /** 大廳或佔點開局 10 秒準備期選特殊道具（戰鬥開始後不能換：整場只能一種）。 */
    public static void setChoice(ServerPlayer p, int typeOrdinal) {
        SpecialType[] all = SpecialType.values();
        if (typeOrdinal < 0 || typeOrdinal >= all.length) return;
        SpecialType t = all[typeOrdinal];
        if (tier(t) == 0) { msg(p, t.zh + " 在團隊模式不能用"); return; }
        DomMode d = dom();
        if (STATES.containsKey(p.getUUID()) && d != null) {
            if (!d.loadoutOpen()) { msg(p, "比賽開始後不能換，整場只能用一種"); return; }
            CHOICE.put(p.getUUID(), t);
            State st = STATES.get(p.getUUID());
            st.type = t;
            st.cooldown = 0;
            refreshSlot(p, st);
            return;
        }
        CHOICE.put(p.getUUID(), t);
        sync(p);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 佔點：比賽中
    // ═════════════════════════════════════════════════════════════════════════

    private static final class State {
        SpecialType type;
        int cooldown; // 剩餘冷卻回合數（0 = 可用；使用後設為弱 1／中 2／強 3，每次新回合開始 -1）
        boolean partialUsed; // 可用 2 次的道具本回合已用 1 次（若回合結束未用第 2 次，下回合仍視為本回合已使用）
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Nullable
    private static DomMode dom() {
        ArenaMode m = ArenaManager.current();
        return m instanceof DomMode d && !d.isEnding() ? d : null;
    }

    /** 這位玩家正在用佔點的整場特殊物品。 */
    public static boolean active(ServerPlayer p) {
        return dom() != null && STATES.containsKey(p.getUUID());
    }

    /** 這位玩家在佔點選的特殊道具（沒有 = null）。 */
    @Nullable
    public static SpecialType activeGear(ServerPlayer p) {
        State st = dom() == null ? null : STATES.get(p.getUUID());
        return st == null ? null : st.type;
    }

    /** 這位玩家在佔點選的特殊物品強度（沒有 = 0）。 */
    public static int activeTier(ServerPlayer p) {
        SpecialType g = activeGear(p);
        return g == null ? 0 : tier(g);
    }

    /** 開局（DomMode.onStart 呼叫）：特殊物品模式開著才發。 */
    public static void startMatch(Collection<ServerPlayer> players) {
        STATES.clear();
        if (!SpecialConfig.enabledSafe()) return;
        for (ServerPlayer p : players) {
            State st = new State();
            st.type = choice(p.getUUID());
            st.cooldown = 0;
            STATES.put(p.getUUID(), st);
            refreshSlot(p, st);
            int tier = tier(st.type);
            int cd = cooldownRounds(tier);
            String freq = cd <= 1 ? "每回合可用" : "使用後冷卻 " + cd + " 回合";
            p.sendSystemMessage(Component.literal("[佔點] 你的特殊道具：" + st.type.zh + "（" + tierName(tier) + "，" + freq + "，按 Q 使用；"
                + "商店價格 +" + markupPercent(st.type) + "%）。").withStyle(ChatFormatting.AQUA));
        }
    }

    /** 每回合戰鬥開始：剩餘冷卻回合數 -1，降到 0 即恢復可用（DomMode.startRound 呼叫）。 */
    public static void newRound(MinecraftServer server) {
        for (Map.Entry<UUID, State> e : STATES.entrySet()) {
            State st = e.getValue();
            if (st.cooldown > 0) {
                st.cooldown--;
            } else if (st.partialUsed) {
                st.cooldown = Math.max(0, cooldownRounds(st.type) - 1);
            }
            st.partialUsed = false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) refreshSlot(p, st);
        }
    }

    /** 比賽結束：收回代表物品、清掉賭徒釣竿的生命增減（DomMode.onFinish 呼叫）。 */
    public static void endMatch(MinecraftServer server) {
        Set<UUID> ids = new HashSet<>(STATES.keySet());
        STATES.clear();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (isGearStack(SpecialSlot.get(p))) SpecialSlot.clear(p);
            NewSpecials.clearGamble(p);
            sync(p);
        }
    }

    private static void refreshSlot(ServerPlayer p, State st) {
        ItemStack s = new ItemStack(SpecialRegistry.item(st.type));
        s.getOrCreateTag().putInt(TAG, 1);
        s.getOrCreateTag().putInt(COOLDOWN_TAG, st.cooldown);
        SpecialSlot.set(p, s);
        sync(p);
    }

    public static boolean isGearStack(ItemStack s) {
        return !s.isEmpty() && s.getTag() != null && s.getTag().contains(TAG);
    }

    // ── SpecialUse 的掛鉤 ────────────────────────────────────────────────────

    /** 現在不能用的原因（null = 可以用）。只處理特殊物品格裡的佔點代表物品。 */
    @Nullable
    public static String blockReason(ServerPlayer p, ItemStack stack) {
        if (!isGearStack(stack)) return null;
        State st = STATES.get(p.getUUID());
        DomMode d = dom();
        if (st == null || d == null) { SpecialSlot.clear(p); return "比賽已經結束"; } // 比賽外殘留的代表物品
        if (!d.fighting()) return "準備階段不能使用特殊道具";
        if (st.cooldown == 1) return "冷卻中，下一回合才能再用";
        if (st.cooldown > 1) return "冷卻中，還需 " + st.cooldown + " 回合才能再用";
        return null;
    }

    /** 可用 2 次的道具在本回合用掉第 1 次時標記（避免中階地雷每回合只用 1 次規避冷卻）。 */
    public static void markPartialUsed(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st != null) st.partialUsed = true;
    }

    /** 用掉了（keep = 回溯點第一次使用只記錄位置，不算用掉）。依強度設定冷卻回合（弱 1／中 2／強 3）。 */
    public static void afterUse(ServerPlayer p, boolean keep) {
        State st = STATES.get(p.getUUID());
        if (st == null || keep) return;
        st.partialUsed = false;
        st.cooldown = cooldownRounds(st.type);
        refreshSlot(p, st);
    }

    /** 傳送珠飛不出去等「退回」：這回合可以再用。回傳 true = 已處理。 */
    public static boolean refund(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        st.cooldown = 0;
        refreshSlot(p, st);
        return true;
    }

    /** 回溯點逾時自動消失：算已使用並進入對應強度冷卻。回傳 true = 已處理。 */
    public static boolean recallExpired(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        st.cooldown = cooldownRounds(st.type);
        refreshSlot(p, st);
        return true;
    }

    /** 賭徒釣竿在佔點不會掉出特殊物品（整場只能一種）。回傳 true = 已處理（不掉）。 */
    public static boolean blockLoot(ServerPlayer p) {
        return active(p);
    }

    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }

    // ── 同步 ─────────────────────────────────────────────────────────────────

    public static void sync(ServerPlayer p) {
        State st = dom() == null ? null : STATES.get(p.getUUID());
        PGNetwork.sendTo(p, new DomGearPacket(st != null, choice(p.getUUID()).ordinal(), st == null ? -1 : st.type.ordinal(), st != null && st.cooldown > 0));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }
}
