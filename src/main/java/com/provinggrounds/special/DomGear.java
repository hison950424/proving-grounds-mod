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
            SpecialType.SNOW_WALL, SpecialType.ARMOR_REPAIR,
            SpecialType.LOOK_UP_ONE, SpecialType.LOOK_UP_ALL, SpecialType.CRUDE_SWAPPER, SpecialType.ICE_SKATES, SpecialType.MIMIC,
            SpecialType.RESKYDIVE);
        // 中：能直接改變一場交火（控場、傷害、救命、整隊資訊）
        tier(2, SpecialType.BLIND_ORB, SpecialType.ADRENALINE, SpecialType.INVIS_CLOAK, SpecialType.ENEMY_SCANNER_PLUS,
            SpecialType.BLAST_SCANNER_PLUS, SpecialType.RADAR_PULSE, SpecialType.KNOCKBACK_SHIELD, SpecialType.WIND_BOMB,
            SpecialType.LAND_MINE, SpecialType.FIRE_MINE, SpecialType.SHOCK_MINE, SpecialType.WARP_PEARL, SpecialType.GAS_GRENADE,
            SpecialType.FIRST_AID_SHOT, SpecialType.ARMOR_REPAIR_PLUS, SpecialType.ROOT_ORB, SpecialType.FIRE_DRILL, SpecialType.RECALL,
            SpecialType.COCKROACH, SpecialType.PARANOIA, SpecialType.MAGNET_GRENADE, SpecialType.MISFIRE_CURSE,
            SpecialType.SPACE_SWAPPER, SpecialType.ICE_SKATES_ALL, SpecialType.HUD_JAMMER, SpecialType.OVERHEAT_CURSE, SpecialType.ETHEREAL,
            SpecialType.RESKYDIVE_GROUP, SpecialType.RESKYDIVE_TARGET);
        // 強：一個人就能翻盤（免死、大範圍傷害、癱瘓整區、賭命）
        tier(3, SpecialType.TOTEM, SpecialType.SHIELD_BUBBLE, SpecialType.BRAINWASH, SpecialType.RECALL_PLUS,
            SpecialType.GAMBLER_ROD, SpecialType.GAMBLER_ROD_PLUS, SpecialType.THOR_HAMMER, SpecialType.THOR_HAMMER_PLUS,
            SpecialType.DOMAIN_EXPANSION, SpecialType.FRIEND_OR_FOE, SpecialType.RED_LIGHT);
        // 沒寫的 = 0（團隊模式用不到）：信號槍（空投）、三種假箱（沒有箱子）、圈外庇護（沒有毒圈）、三種重新跳傘
    }

    private static List<? extends String> cachedList;
    private static Map<SpecialType, Integer> cachedOverrides = Map.of();

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

    /** 佔點選了這個強度時，商店價格加幾 %。 */
    public static int markupPercent(int tier) {
        if (tier <= 0) return 0;
        int[] def = { 10, 25, 50 };
        int i = Math.min(2, tier - 1);
        try {
            List<? extends Integer> l = PGConfig.SERVER.arena.domGearMarkup.get();
            if (!l.isEmpty()) return Math.max(0, l.get(Math.min(i, l.size() - 1)));
        } catch (IllegalStateException ignored) {}
        return def[i];
    }

    /** 加價後的價格（伺服器扣錢與客戶端顯示用同一個算法）。 */
    public static int applyMarkup(int price, int tier) {
        return (int) Math.round(price * (100 + markupPercent(tier)) / 100.0);
    }

    public static String tierName(int tier) { return switch (tier) { case 1 -> "弱"; case 2 -> "中"; case 3 -> "強"; default -> "—"; }; }

    /** 團隊模式能用的特殊物品（照強度、再照清單順序）。 */
    public static List<SpecialType> allowed() {
        List<SpecialType> out = new ArrayList<>();
        for (SpecialType t : SpecialType.values()) if (tier(t) > 0) out.add(t);
        out.sort(Comparator.<SpecialType>comparingInt(t -> tier(t)).thenComparingInt(Enum::ordinal));
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
            st.used = false;
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
        boolean used; // 這回合用過了
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

    /** 這位玩家在佔點選的特殊物品強度（給商店加價；沒有 = 0）。 */
    public static int activeTier(ServerPlayer p) {
        State st = dom() == null ? null : STATES.get(p.getUUID());
        return st == null ? 0 : tier(st.type);
    }

    /** 開局（DomMode.onStart 呼叫）：特殊物品模式開著才發。 */
    public static void startMatch(Collection<ServerPlayer> players) {
        STATES.clear();
        if (!SpecialConfig.enabledSafe()) return;
        for (ServerPlayer p : players) {
            State st = new State();
            st.type = choice(p.getUUID());
            STATES.put(p.getUUID(), st);
            refreshSlot(p, st);
            int tier = tier(st.type);
            p.sendSystemMessage(Component.literal("[佔點] 你的特殊道具：" + st.type.zh + "（" + tierName(tier) + "）。每回合可以用一次，按 Q 使用；"
                + "商店價格 +" + markupPercent(tier) + "%。").withStyle(ChatFormatting.AQUA));
        }
    }

    /** 每回合戰鬥開始：恢復可以用（DomMode.startRound 呼叫）。 */
    public static void newRound(MinecraftServer server) {
        for (Map.Entry<UUID, State> e : STATES.entrySet()) {
            e.getValue().used = false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) refreshSlot(p, e.getValue());
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
        if (st.used) return "這回合已經用過了，下一回合才能再用";
        return null;
    }

    /** 用掉了（keep = 回溯點第一次使用只記錄位置，不算用掉）。 */
    public static void afterUse(ServerPlayer p, boolean keep) {
        State st = STATES.get(p.getUUID());
        if (st == null || keep) return;
        st.used = true;
        sync(p);
    }

    /** 傳送珠飛不出去等「退回」：這回合可以再用。回傳 true = 已處理。 */
    public static boolean refund(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        st.used = false;
        sync(p);
        return true;
    }

    /** 回溯點 60 秒沒用完自動消失：算這回合用掉了。回傳 true = 已處理。 */
    public static boolean recallExpired(ServerPlayer p) {
        State st = STATES.get(p.getUUID());
        if (st == null || dom() == null) return false;
        st.used = true;
        sync(p);
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
        PGNetwork.sendTo(p, new DomGearPacket(st != null, choice(p.getUUID()).ordinal(), st == null ? -1 : st.type.ordinal(), st != null && st.used));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }
}
