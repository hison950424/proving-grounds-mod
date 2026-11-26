package com.provinggrounds.modes.br;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunClass;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: HotbarRules.java
 * 目的: 快捷欄規則（計畫書 B2），所有入口都呼叫這裡。只在 BR 進行中、玩家是存活參賽者、且不是創造／旁觀模式時生效。
 *
 *   格子（Inventory 編號）：0、1 = 大型槍｜2 = 手槍｜3 = 補品｜4 = 投擲物｜5～8 = 其他（不能放槍）
 *                          9～35 = 背包（不能放槍）｜36～39 = 防具｜40 = 副手（不能放槍）
 *   分類：槍由槍械適配層判斷（GunCompat.classify）；補品、投擲物看物品標籤
 *         provinggrounds:healing、provinggrounds:throwable（data/provinggrounds/tags/items/）。
 *
 *   三道防線：
 *     1. 物品放置 insert()：箱子 Shift 快速移動、地面撿取、祭壇基礎裝備都走這裡，照規則放到對的格子。
 *     2. 點擊檢查（ContainerMenuMixin 呼叫 beforeClick / afterClick）：任何點擊（滑鼠、拖曳分堆、數字鍵、
 *        F 鍵、雙擊收集）執行後，如果身上違規的格子變多，就把整個點擊還原（物品回到原位，不會消失），並提示。
 *        游標上暫時拿著槍是允許的（用來互換）。
 *     3. 定期整理 normalize()：每半秒檢查一次，把不合規則的物品（例如 /give、關閉畫面時游標上的槍被塞回背包）
 *        搬到合法的格子，放不下就掉在腳邊。
 */
public final class HotbarRules {

    public static final TagKey<Item> HEALING = ItemTags.create(new ResourceLocation(ProvingGroundsMod.MOD_ID, "healing"));
    public static final TagKey<Item> THROWABLE = ItemTags.create(new ResourceLocation(ProvingGroundsMod.MOD_ID, "throwable"));

    public static final int LARGE_A = 0, LARGE_B = 1, PISTOL = 2, HEAL = 3, THROW = 4;
    public static final int OFFHAND = 40;

    /** SPECIAL = 特殊物品（只能放特殊物品欄）；SHIELD = 穿戴中的擊退盾（佔用當時拿著的那一格快捷欄）。 */
    public enum Kind { LARGE, PISTOL, MELEE, HEALING, THROWABLE, BACKPACK, SPECIAL, SHIELD, OTHER }

    public static final String MSG_LARGE = "無法拿取兩把以上的槍（可用替換的方式拿取）";
    public static final String MSG_PISTOL = "無法拿取兩把以上的手槍（可用替換的方式拿取）";
    public static final String MSG_FULL = "背包已滿，無法拿取";
    private static final String MSG_GUN_SLOT = "槍只能放在快捷欄第 1、2 格";
    private static final String MSG_SPECIAL_SLOT = "快捷欄第 1～5 格是專用格（1、2 槍｜3 近戰武器｜4 補品｜5 投擲物）";
    public static final String MSG_BACKPACK_HAVE = "已經裝著同等級或更高級的背包";

    private static final String MSG_DROPPED = "放不下的物品已掉在腳邊";
    public static final String MSG_SPECIAL_FULL = "特殊道具欄已經有物品（在箱子裡 Shift＋左鍵可以互換）";
    private static final String MSG_SPECIAL_ONLY = "特殊道具只能放在特殊道具欄（背包欄上方）";

    private static final Map<UUID, Long> lastMessage = new HashMap<>();

    private HotbarRules() {}

    // ── 分類與規則 ───────────────────────────────────────────────────────────

    public static Kind kind(ItemStack s) {
        if (s.getItem() instanceof com.provinggrounds.item.BackpackItem) return Kind.BACKPACK;
        if (com.provinggrounds.special.SpecialSlot.isSpecial(s)) return Kind.SPECIAL;
        if (s.is(com.provinggrounds.special.SpecialRegistry.SHIELD_HELD.get())) return Kind.SHIELD;
        GunClass g = GunCompat.classify(s);
        if (g == GunClass.LARGE) return Kind.LARGE;
        if (g == GunClass.PISTOL) return Kind.PISTOL;
        if (s.is(GunCompat.MELEE)) return Kind.MELEE; // lrtactical 近戰武器：可放第 3 格（手槍格）
        if (s.is(HEALING)) return Kind.HEALING;
        // 團隊模式職業的「大補」「速補」藥水（NBT 有 PgArenaKit）也算補品（計畫書 6 C2）
        if (s.getItem() instanceof net.minecraft.world.item.PotionItem && s.hasTag() && s.getTag().getBoolean("PgArenaKit")) return Kind.HEALING;
        if (s.is(THROWABLE)) return Kind.THROWABLE;
        return Kind.OTHER;
    }

    public static boolean isGun(ItemStack s) {
        return GunCompat.classify(s) != GunClass.NONE;
    }

    /**
     * 規則是否對這個玩家生效：大逃殺，以及團隊死鬥／佔點／槍王之王（計畫書 5 的 A2：槍械放置規則同大逃殺）。
     * 團隊模式沒有背包系統，背包格的鎖定只看 activeBr（Backpack 用）。
     */
    public static boolean active(Player p) {
        if (activeBr(p)) return true;
        if (!(p instanceof ServerPlayer sp) || !com.provinggrounds.modes.arena.ArenaManager.isParticipant(sp.getUUID())) return false;
        try {
            if (!PGConfig.SERVER.hotbarRulesEnabled.get()) return false;
        } catch (IllegalStateException e) {
            return false;
        }
        GameType mode = sp.gameMode.getGameModeForPlayer();
        return mode != GameType.CREATIVE && mode != GameType.SPECTATOR && sp.isAlive();
    }

    /** 大逃殺中（背包系統只在大逃殺）。 */
    public static boolean activeBr(Player p) {
        if (!(p instanceof ServerPlayer sp)) return false;

        try {
            if (!PGConfig.SERVER.hotbarRulesEnabled.get()) return false;
        } catch (IllegalStateException e) {
            return false;
        }
        GameType mode = sp.gameMode.getGameModeForPlayer();
        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) return false;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        BattleRoyaleMode.Phase phase = br.getPhase();
        if (phase != BattleRoyaleMode.Phase.PLANE && phase != BattleRoyaleMode.Phase.ACTIVE) return false;
        return br.isAliveParticipant(sp.getUUID());
    }

    /** 背包格（9～35）可不可以用（依背包等級，見 Backpack）。 */
    public static boolean backpackUsable(ServerPlayer p, int slot) {
        return !Backpack.locked(p, slot);
    }

    /** 這格可不可以放這個物品。 */
    public static boolean allowedAt(ServerPlayer p, int slot, ItemStack s) {
        if (s.isEmpty()) return true;
        if (slot >= 36 && slot <= 39) return true; // 防具欄不管
        Kind k = kind(s);
        // 背包：除了裝在背包欄，也可以放在第 6～9 格或背包裡（帶去給隊友），規則同一般物品
        if (k == Kind.SPECIAL) return false; // 特殊物品只能在特殊物品欄
        if (k == Kind.SHIELD) return slot >= 0 && slot <= 8; // 擊退盾佔用穿戴時拿著的那一格
        return switch (slot) {
            case LARGE_A, LARGE_B -> k == Kind.LARGE || k == Kind.PISTOL; // 任何槍（含手槍）都放第 1、2 格
            case PISTOL -> k == Kind.MELEE;                                  // 第 3 格：近戰武器專用
            case HEAL -> k == Kind.HEALING;
            case THROW -> k == Kind.THROWABLE;
            default -> k != Kind.LARGE && k != Kind.PISTOL && (slot < 9 || slot > 35 || backpackUsable(p, slot));
        };
    }

    /** 身上違規的格子數。 */
    public static int violations(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i <= OFFHAND; i++) if (!allowedAt(p, i, inv.getItem(i))) n++;
        return n;
    }

    // ── 1. 物品放置 ─────────────────────────────────────────────────────────

    /** 放置結果：放進去幾個；完全放不進去時的提示（可能是 null）。 */
    public record Result(int moved, @Nullable String message) {}

    /** 依規則把 stack 放進玩家身上（會減少 stack 的數量）。 */
    public static Result insert(ServerPlayer p, ItemStack stack) {
        int before = stack.getCount();
        Inventory inv = p.getInventory();
        String fail = null;
        switch (kind(stack)) {
            case LARGE, PISTOL -> {
                if (!putEmpty(inv, stack, LARGE_A)) putEmpty(inv, stack, LARGE_B);
                fail = MSG_LARGE;
            }
            case MELEE -> {
                // 近戰武器：第 3 格空著就放第 3 格，否則當一般物品放第 6～9 格或背包
                if (!putEmpty(inv, stack, PISTOL)) insertOther(p, inv, stack);
            }
            case HEALING, THROWABLE -> {
                int target = kind(stack) == Kind.HEALING ? HEAL : THROW;
                merge(inv, stack, target);
                putEmpty(inv, stack, target);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) merge(inv, stack, i);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) putEmpty(inv, stack, i);
                // 專用格有東西、背包也滿了：放快捷欄第 6～9 格（計畫書 6 A2）
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) merge(inv, stack, i);
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
                fail = MSG_FULL;
            }
            case BACKPACK -> {
                // 更高級就換上（舊的放進物品欄）；不是更高級就當一般物品帶著（可以拿去給隊友）
                if (!Backpack.equipIfBetter(p, stack)) insertOther(p, inv, stack);
                fail = MSG_FULL;
            }
            case SPECIAL -> {
                com.provinggrounds.special.SpecialSlot.tryInsert(p, stack);
                fail = MSG_SPECIAL_FULL;
            }
            case SHIELD -> stack.setCount(0); // 擊退盾不會掉在地上，萬一出現就直接消失
            case OTHER -> insertOther(p, inv, stack);
        }
        int moved = before - stack.getCount();
        return new Result(moved, moved == 0 ? fail : null);
    }

    /**
     * 一般物品（不是有專用格的槍、近戰、補品、投擲物、特殊物品，也不是裝備）：優先放背包，背包放不下才放快捷欄第 6～9 格。
     * 順序：疊到背包同種物品上 → 背包空格 → 疊到第 6～9 格同種物品上 → 第 6～9 格空格。
     */
    private static void insertOther(ServerPlayer p, Inventory inv, ItemStack stack) {
        for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) merge(inv, stack, i);
        for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) putEmpty(inv, stack, i);
        for (int i = 5; i <= 8 && !stack.isEmpty(); i++) merge(inv, stack, i);
        for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
    }


    /**
     * 清空玩家身上物品，但保留一把近戰武器放到第 3 格（開局加入時、登機時呼叫）。
     * 玩家可以在大廳先拿好近戰武器帶進對局。
     */
    public static void clearKeepMelee(ServerPlayer p) {
        Inventory inv = p.getInventory();
        ItemStack melee = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize() && melee.isEmpty(); i++) {
            if (inv.getItem(i).is(GunCompat.MELEE)) melee = inv.getItem(i).copyWithCount(1);
        }
        if (melee.isEmpty() && p.containerMenu.getCarried().is(GunCompat.MELEE)) melee = p.containerMenu.getCarried().copyWithCount(1);
        inv.clearContent();
        p.containerMenu.setCarried(ItemStack.EMPTY);
        if (melee.isEmpty()) melee = randomMelee(p); // 沒有自己帶近戰武器：隨機給一把
        if (!melee.isEmpty()) inv.setItem(PISTOL, melee);
    }

    /** 從設定 [battle_royale.hotbar].random_melee 隨機挑一把近戰武器（格式同 /give）；清單是空的或都解析失敗時回傳空物品。 */
    private static ItemStack randomMelee(ServerPlayer p) {
        java.util.List<? extends String> list;
        try {
            list = com.provinggrounds.config.PGConfig.SERVER.randomMelee.get();
        } catch (IllegalStateException e) {
            return ItemStack.EMPTY;
        }
        if (list.isEmpty()) return ItemStack.EMPTY;
        return RespawnKit.parse(list.get(p.getRandom().nextInt(list.size())));
    }

    /** 背包 → 快捷欄（物品欄畫面裡 Shift 點背包的物品）：只放專用格或第 6～9 格，不再放回背包。 */
    private static Result insertToHotbar(ServerPlayer p, ItemStack stack) {
        int before = stack.getCount();
        Inventory inv = p.getInventory();
        net.minecraft.world.entity.EquipmentSlot armor = net.minecraft.world.entity.Mob.getEquipmentSlotForItem(stack);
        if (armor.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR && p.getItemBySlot(armor).isEmpty()) {
            p.setItemSlot(armor, stack.split(1)); // 防具欄空著：直接穿上
            return new Result(before - stack.getCount(), null);
        }
        switch (kind(stack)) {
            case HEALING, THROWABLE -> {
                int target = kind(stack) == Kind.HEALING ? HEAL : THROW;
                merge(inv, stack, target);
                putEmpty(inv, stack, target);
                // 專用格放不下：放快捷欄第 6～9 格
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) merge(inv, stack, i);
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
            }
            case LARGE, PISTOL -> { if (!putEmpty(inv, stack, LARGE_A)) putEmpty(inv, stack, LARGE_B); }
            case MELEE -> { if (!putEmpty(inv, stack, PISTOL)) { for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i); } }
            case BACKPACK -> {
                if (!Backpack.equipIfBetter(p, stack)) {
                    for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
                }
            }
            case SPECIAL -> com.provinggrounds.special.SpecialSlot.tryInsert(p, stack);
            case SHIELD -> {}
            case OTHER -> {
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) merge(inv, stack, i);
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
            }
        }
        return new Result(before - stack.getCount(), null);
    }

    private static void merge(Inventory inv, ItemStack stack, int slot) {
        ItemStack there = inv.getItem(slot);
        if (stack.isEmpty() || there.isEmpty() || !ItemStack.isSameItemSameTags(there, stack)) return;
        int room = Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getCount();
        if (room <= 0) return;
        int n = Math.min(room, stack.getCount());
        there.grow(n);
        stack.shrink(n);
        inv.setChanged();
    }

    private static boolean putEmpty(Inventory inv, ItemStack stack, int slot) {
        if (stack.isEmpty() || !inv.getItem(slot).isEmpty()) return false;
        inv.setItem(slot, stack.split(Math.min(stack.getMaxStackSize(), inv.getMaxStackSize())));
        return true;
    }

    /** 地面撿取（EntityItemPickupEvent 已取消原版撿取後呼叫）。 */
    public static void pickup(ServerPlayer p, ItemEntity entity) {
        ItemStack original = entity.getItem();
        ItemStack work = original.copy();
        Result r = insert(p, work);
        if (r.moved() > 0) {
            p.take(entity, r.moved());
            p.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(original.getItem()), r.moved());
            if (work.isEmpty()) entity.discard();
            else entity.setItem(work);
        } else if (r.message() != null) {
            message(p, r.message());
        }
    }

    // ── 2. 點擊檢查 ─────────────────────────────────────────────────────────

    /** 點擊前的狀態（還原用）。 */
    public static final class Snapshot {
        final ItemStack[] menuSlots;  // 非玩家物品欄的格子（箱子等）
        final ItemStack[] inventory;  // 玩家物品欄 0～40
        final ItemStack carried;
        final int violations;
        final int backpackLevel;

        Snapshot(AbstractContainerMenu menu, ServerPlayer p) {
            Inventory inv = p.getInventory();
            menuSlots = new ItemStack[menu.slots.size()];
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                menuSlots[i] = s.container == inv ? null : s.getItem().copy();
            }
            inventory = new ItemStack[OFFHAND + 1];
            for (int i = 0; i <= OFFHAND; i++) inventory[i] = inv.getItem(i).copy();
            carried = menu.getCarried().copy();
            violations = HotbarRules.violations(p);
            backpackLevel = Backpack.level(p);
        }

        void restore(AbstractContainerMenu menu, ServerPlayer p) {
            for (int i = 0; i < menuSlots.length && i < menu.slots.size(); i++) {
                if (menuSlots[i] != null) menu.slots.get(i).set(menuSlots[i].copy());
            }
            Inventory inv = p.getInventory();
            for (int i = 0; i <= OFFHAND; i++) inv.setItem(i, inventory[i].copy());
            menu.setCarried(carried.copy());
        }
    }

    /**
     * 點擊前呼叫。回傳 true = 已自行處理（Shift 快速移動），原版的點擊要取消。
     * 需要事後檢查時，把快照放進 out[0]。
     */
    public static boolean beforeClick(AbstractContainerMenu menu, int slotId, ClickType type, ServerPlayer p, Snapshot[] out) {
        if (type == ClickType.QUICK_MOVE && slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.slots.get(slotId);
            ItemStack stack = slot.getItem();
            Inventory inv = p.getInventory();
            if (!stack.isEmpty() && !(slot instanceof ResultSlot) && slot.mayPickup(p)) {
                // 防具：Shift＋左鍵背包、快捷欄或箱子裡的防具，身上同部位已經有穿 → 直接互換（換下來的放回原本那一格）
                if (swapArmor(slot, stack, p, inv)) return true;
                if (slot.container != inv && !(menu instanceof InventoryMenu)) {
                    // 特殊物品：特殊物品欄已經有東西時，Shift 點箱子裡的特殊物品 = 互換（舊的放回箱子那一格）
                    if (kind(stack) == Kind.SPECIAL && stack.getCount() == 1
                            && !com.provinggrounds.special.SpecialSlot.get(p).isEmpty()) {
                        ItemStack old = com.provinggrounds.special.SpecialSlot.get(p).copy();
                        if (slot.mayPlace(old)) {
                            com.provinggrounds.special.SpecialSlot.set(p, stack.copy());
                            slot.set(old);
                            message(p, "已替換特殊道具（原本的放回箱子）");
                            return true;
                        }
                    }
                    // 箱子 → 身上：照規則放（欄位滿了就拒絕，不自動替換）
                    quickMove(slot, p, insert(p, stack.copy()));
                    return true;
                }
                if (slot.container == inv && menu instanceof InventoryMenu && slot.getContainerSlot() >= 9 && slot.getContainerSlot() <= 35) {
                    // 物品欄畫面：背包 → 快捷欄
                    quickMove(slot, p, insertToHotbar(p, stack.copy()));
                    return true;
                }
            }
        }
        out[0] = new Snapshot(menu, p);
        return false;
    }

    /** 防具互換：回傳 true = 已處理。身上那個部位空著時交給原本的流程（直接穿上）。 */
    private static boolean swapArmor(Slot slot, ItemStack stack, ServerPlayer p, Inventory inv) {
        if (slot.container == inv && slot.getContainerSlot() >= 36 && slot.getContainerSlot() <= 39) return false; // 本來就在防具欄
        if (stack.getCount() != 1) return false;
        net.minecraft.world.entity.EquipmentSlot es = net.minecraft.world.entity.Mob.getEquipmentSlotForItem(stack);
        if (es.getType() != net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) return false;
        ItemStack worn = p.getItemBySlot(es);
        if (worn.isEmpty()) {
            if (slot.container == inv) return false; // 自己背包裡的：交給原本的流程直接穿上
            p.setItemSlot(es, stack.copy()); // 箱子、墓碑裡的防具：身上那個部位空著就直接穿上
            slot.set(ItemStack.EMPTY);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
            return true;
        }
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(worn) && !p.isCreative()) return false; // 綁定詛咒脫不下來
        if (!slot.mayPlace(worn)) return false;
        ItemStack old = worn.copy();
        p.setItemSlot(es, stack.copy());
        slot.set(old);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    private static void quickMove(Slot slot, ServerPlayer p, Result r) {
        if (r.moved() > 0) {
            ItemStack taken = slot.remove(r.moved());
            slot.onTake(p, taken);
        } else if (r.message() != null) {
            message(p, r.message());
        }
    }

    /**
     * 每位玩家「游標上的物品是從哪一格拿起來的」（menu 的 containerId 與格子編號）。
     * 放錯格子被還原時，物品直接放回這一格，而不是留在游標上。
     */
    private static final Map<UUID, int[]> pickupOrigin = new HashMap<>();

    /** 點擊後呼叫：違規變多就整個還原，並把游標上的物品放回拿起來的那一格。 */
    public static void afterClick(AbstractContainerMenu menu, int slotId, ServerPlayer p, Snapshot snap) {
        if (Backpack.level(p) != snap.backpackLevel) {
            normalize(p); // 換了背包：容量變了，鎖定格裡的物品搬走或掉落（不還原）
            return;
        }
        if (violations(p) <= snap.violations) {
            // 合法的點擊：記下游標上的物品是從哪一格拿起來的
            if (snap.carried.isEmpty() && !menu.getCarried().isEmpty() && slotId >= 0 && slotId < menu.slots.size()) {
                pickupOrigin.put(p.getUUID(), new int[] { menu.containerId, slotId });
            } else if (menu.getCarried().isEmpty()) {
                pickupOrigin.remove(p.getUUID());
            }
            return;
        }
        String why = MSG_SPECIAL_SLOT;
        Inventory inv = p.getInventory();
        for (int i = 0; i <= OFFHAND; i++) {
            ItemStack s = inv.getItem(i);
            if (!allowedAt(p, i, s)) {
                if (isGun(s)) why = MSG_GUN_SLOT;
                else if (kind(s) == Kind.SPECIAL) why = MSG_SPECIAL_ONLY;
                break;
            }
        }
        snap.restore(menu, p);
        returnCarried(menu, p);
        message(p, why);
    }

    /**
     * 還原後游標上還拿著東西（例如拿起槍、放到錯的格子）：放回拿起來的那一格；
     * 那一格已經被佔走或不知道從哪拿的，就照規則放到合法的格子；都不行才留在游標上。
     */
    private static void returnCarried(AbstractContainerMenu menu, ServerPlayer p) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;
        int[] origin = pickupOrigin.remove(p.getUUID());
        if (origin != null && origin[0] == menu.containerId && origin[1] < menu.slots.size()) {
            Slot slot = menu.slots.get(origin[1]);
            boolean legal = !(slot.container instanceof Inventory) || allowedAt(p, slot.getContainerSlot(), carried);
            if (!slot.hasItem() && legal && slot.mayPlace(carried)) {
                slot.set(carried.copy());
                menu.setCarried(ItemStack.EMPTY);
                return;
            }
        }
        ItemStack work = carried.copy();
        insert(p, work);
        menu.setCarried(work);
    }

    /** 玩家登出：清掉記錄。 */
    public static void forget(UUID id) {
        pickupOrigin.remove(id);
        lastSpecial.remove(id);
        lastMessage.remove(id);
    }

    // ── 3. 定期整理 ─────────────────────────────────────────────────────────

    /** 把不合規則的物品搬到合法的格子，放不下就掉在腳邊。 */
    public static void normalize(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean dropped = false;
        for (int i = 0; i <= OFFHAND; i++) {
            ItemStack s = inv.getItem(i);
            if (allowedAt(p, i, s)) continue;
            ItemStack moving = inv.removeItemNoUpdate(i);
            insert(p, moving);
            if (!moving.isEmpty()) {
                dropAtFeet(p, moving);
                dropped = true;
            }
        }
        if (dropped) message(p, MSG_DROPPED);
    }

    // ── 自動補位（第 4、5 格）───────────────────────────────────────────────

    /** 每位玩家上一 tick 第 4、5 格的物品（比較用）。 */
    private static final Map<UUID, ItemStack[]> lastSpecial = new HashMap<>();

    /**
     * 第 4 格（補品）、第 5 格（投擲物）拿在手上用完變空時，從背包或第 6～9 格找「一模一樣的物品」
     * （同物品、同 NBT，例如同一種急救包）補進去，玩家不用自己去翻背包。
     * 只在「那一格正拿在手上」時才補：用掉一定是拿在手上；在物品欄裡把它拖走時通常沒有拿著那一格，
     * 這樣不會出現「剛拖走又自己跑回來」的情況。
     */
    public static void autoRefill(ServerPlayer p) {
        Inventory inv = p.getInventory();
        ItemStack[] last = lastSpecial.computeIfAbsent(p.getUUID(), k -> new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY });
        for (int k = 0; k < 2; k++) {
            int slot = k == 0 ? HEAL : THROW;
            ItemStack now = inv.getItem(slot);
            ItemStack before = last[k];
            if (now.isEmpty() && !before.isEmpty()) {
                // 計畫書 6 C3：先補同樣的物品，沒有就補同類型（補品補補品、投擲物補投擲物）
                int from = findSame(p, before, slot);
                if (from < 0) from = findKind(p, k == 0 ? Kind.HEALING : Kind.THROWABLE, slot);
                if (from >= 0) {
                    inv.setItem(slot, inv.getItem(from));
                    inv.setItem(from, ItemStack.EMPTY);
                    now = inv.getItem(slot);
                }
            }
            last[k] = now.isEmpty() ? ItemStack.EMPTY : now.copyWithCount(1);
        }
    }

    /** 在第 6～9 格與可用的背包格找跟 sample 一樣的物品（同物品同 NBT）；找不到回傳 -1。 */
    private static int findSame(ServerPlayer p, ItemStack sample, int target) {
        Inventory inv = p.getInventory();
        for (int i = 5; i <= 35; i++) {
            if (i == target || (i >= 9 && !backpackUsable(p, i))) continue;
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, sample)) return i;
        }
        return -1;
    }

    /** 在第 6～9 格與可用的背包格找同類型的物品（補品／投擲物）；找不到回傳 -1。 */
    private static int findKind(ServerPlayer p, Kind want, int target) {
        Inventory inv = p.getInventory();
        for (int i = 5; i <= 35; i++) {
            if (i == target || (i >= 9 && !backpackUsable(p, i))) continue;
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && kind(s) == want) return i;
        }
        return -1;
    }

    public static void forgetRefill(UUID id) {
        lastSpecial.remove(id);
    }

    /** 掉在腳邊。不用 player.drop()：那會觸發丟棄事件（倒地時會被放回背包而無限循環）。 */
    public static void dropAtFeet(ServerPlayer p, ItemStack stack) {
        ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), stack);
        e.setPickUpDelay(40);
        p.level().addFreshEntity(e);
    }

    // ── 輪盤 ────────────────────────────────────────────────────────────────

    /**
     * 輪盤選取（WheelSelectPacket）：把 fromSlot 那一疊換到第 4 格（補品）或第 5 格（投擲物），並切到那一格。
     * 原本在專用格的物品換到 fromSlot（剛空出來，一定放得下）。
     */
    public static void wheelSelect(ServerPlayer p, int target, int fromSlot) {
        if (target != HEAL && target != THROW) return;
        if (fromSlot < 0 || fromSlot > 35) return;
        p.stopUsingItem(); // 換物品前一定先中斷使用中的補品/投擲物，避免用到一半換成別的物品
        Inventory inv = p.getInventory();
        if (fromSlot != target) {
            ItemStack from = inv.getItem(fromSlot);
            Kind want = target == HEAL ? Kind.HEALING : Kind.THROWABLE;
            if (from.isEmpty() || kind(from) != want) return;
            ItemStack old = inv.getItem(target);
            inv.setItem(target, from);
            inv.setItem(fromSlot, old);
        }
        inv.selected = target;
        p.connection.send(new ClientboundSetCarriedItemPacket(target));
    }

    // ── 提示 ────────────────────────────────────────────────────────────────

    /** 同一玩家 1 秒內只提示一次（顯示在快捷欄上方）。 */
    public static void message(ServerPlayer p, String text) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(p.getUUID());
        if (last != null && now - last < 1000) return;
        lastMessage.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }
}
