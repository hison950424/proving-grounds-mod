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

    public enum Kind { LARGE, PISTOL, HEALING, THROWABLE, BACKPACK, OTHER }

    public static final String MSG_LARGE = "無法拿取兩把以上的大型槍（可用替換的方式拿取）";
    public static final String MSG_PISTOL = "無法拿取兩把以上的手槍（可用替換的方式拿取）";
    public static final String MSG_FULL = "背包已滿，無法拿取";
    private static final String MSG_GUN_SLOT = "槍只能放在快捷欄第 1～3 格（第 1、2 格大型槍，第 3 格手槍）";
    private static final String MSG_SPECIAL_SLOT = "快捷欄第 1～5 格是專用格（1、2 大型槍｜3 手槍｜4 補品｜5 投擲物）";
    public static final String MSG_BACKPACK_HAVE = "已經裝著同等級或更高級的背包";
    private static final String MSG_BACKPACK_SLOT = "背包只能裝在物品欄的背包欄（副手格上方）";
    private static final String MSG_DROPPED = "放不下的物品已掉在腳邊";

    private static final Map<UUID, Long> lastMessage = new HashMap<>();

    private HotbarRules() {}

    // ── 分類與規則 ───────────────────────────────────────────────────────────

    public static Kind kind(ItemStack s) {
        if (s.getItem() instanceof com.provinggrounds.item.BackpackItem) return Kind.BACKPACK;
        GunClass g = GunCompat.classify(s);
        if (g == GunClass.LARGE) return Kind.LARGE;
        if (g == GunClass.PISTOL) return Kind.PISTOL;
        if (s.is(HEALING)) return Kind.HEALING;
        if (s.is(THROWABLE)) return Kind.THROWABLE;
        return Kind.OTHER;
    }

    public static boolean isGun(ItemStack s) {
        return GunCompat.classify(s) != GunClass.NONE;
    }

    /** 規則是否對這個玩家生效。 */
    public static boolean active(Player p) {
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
        if (k == Kind.BACKPACK) return false; // 背包只能在背包欄
        return switch (slot) {
            case LARGE_A, LARGE_B -> k == Kind.LARGE;
            case PISTOL -> k == Kind.PISTOL;
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
            case LARGE -> {
                if (!putEmpty(inv, stack, LARGE_A)) putEmpty(inv, stack, LARGE_B);
                fail = MSG_LARGE;
            }
            case PISTOL -> {
                putEmpty(inv, stack, PISTOL);
                fail = MSG_PISTOL;
            }
            case HEALING, THROWABLE -> {
                int target = kind(stack) == Kind.HEALING ? HEAL : THROW;
                merge(inv, stack, target);
                putEmpty(inv, stack, target);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) merge(inv, stack, i);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) putEmpty(inv, stack, i);
                fail = MSG_FULL;
            }
            case BACKPACK -> {
                Backpack.equipIfBetter(p, stack); // 更高級就換上，舊的掉在腳邊
                fail = MSG_BACKPACK_HAVE;
            }
            case OTHER -> {
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) merge(inv, stack, i);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) merge(inv, stack, i);
                for (int i = 5; i <= 8 && !stack.isEmpty(); i++) putEmpty(inv, stack, i);
                for (int i = 9; i <= 35 && !stack.isEmpty(); i++) if (backpackUsable(p, i)) putEmpty(inv, stack, i);
            }
        }
        int moved = before - stack.getCount();
        return new Result(moved, moved == 0 ? fail : null);
    }

    /** 背包 → 快捷欄（物品欄畫面裡 Shift 點背包的物品）：只放專用格或第 6～9 格，不再放回背包。 */
    private static Result insertToHotbar(ServerPlayer p, ItemStack stack) {
        int before = stack.getCount();
        Inventory inv = p.getInventory();
        switch (kind(stack)) {
            case HEALING -> { merge(inv, stack, HEAL); putEmpty(inv, stack, HEAL); }
            case THROWABLE -> { merge(inv, stack, THROW); putEmpty(inv, stack, THROW); }
            case LARGE -> { if (!putEmpty(inv, stack, LARGE_A)) putEmpty(inv, stack, LARGE_B); }
            case PISTOL -> putEmpty(inv, stack, PISTOL);
            case BACKPACK -> Backpack.equipIfBetter(p, stack);
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
                if (slot.container != inv && !(menu instanceof InventoryMenu)) {
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
                else if (kind(s) == Kind.BACKPACK) why = MSG_BACKPACK_SLOT;
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
