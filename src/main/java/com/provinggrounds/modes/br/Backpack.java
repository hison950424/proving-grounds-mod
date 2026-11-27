package com.provinggrounds.modes.br;

import com.provinggrounds.config.PGConfig;
import com.provinggrounds.item.BackpackItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: Backpack.java
 * 目的: 背包系統（計畫書 B3）。
 *   - 每個玩家有一格「背包欄」（InventoryMenuMixin 加在物品欄畫面、副手格的正上方），只接受 BackpackItem。
 *     背包也可以放在物品欄裡帶著（例如拿去給隊友），只有裝在背包欄的才會解鎖格子。
 *   - 主背包 27 格（3 列 × 9 欄，Inventory 9～35）依背包等級一列一列解鎖：一級開最下列、二級加中間列、
 *     三級加最上列，每列開左邊 backpack_columns 欄（預設 7，也就是 7／14／21 格）。沒有背包時全鎖。
 *   - 鎖定只在 BR 進行中的參賽者身上生效（跟快捷欄規則一樣），大廳與創造模式不受影響。
 *   - 鎖定格不能放、不能拿（SlotMixin 改 Slot.mayPlace／mayPickup，伺服器與客戶端都檢查）；
 *     容量變小時，鎖定格裡的物品由 HotbarRules.normalize 搬到開放的格子，放不下掉在腳邊。
 *   - 背包欄的內容存在玩家資料（persistentData 的 pg_backpack），重新登入與重生（Clone）都保留；
 *     客戶端的內容由物品欄畫面的格子同步，不需要另外的封包。
 */
public final class Backpack {

    public static final String DATA_TAG = "pg_backpack";

    private static final Map<UUID, SimpleContainer> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, SimpleContainer> CLIENT = new ConcurrentHashMap<>();
    /** 記錄玩家在其他容器畫面從哪一格拿起背包，用來在點自己的背包欄替換時放回原容器。 */
    private static final Map<UUID, int[]> LAST_OTHER_PICKUP = new ConcurrentHashMap<>();

    /** 客戶端：自己目前是否受規則限制（由 ClientBRState 更新），用來預測與畫遮罩。 */
    public static volatile boolean clientRulesActive;

    private Backpack() {}

    /** 這個玩家的背包欄（一格）。 */
    public static SimpleContainer container(Player p) {
        boolean client = p.level().isClientSide;
        return (client ? CLIENT : SERVER).computeIfAbsent(p.getUUID(), id -> create(id, client));
    }

    private static SimpleContainer create(UUID id, boolean client) {
        SimpleContainer c = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() { return 1; }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) { return stack.getItem() instanceof BackpackItem; }
        };
        if (!client) c.addListener(changed -> save(id, changed.getItem(0)));
        return c;
    }

    private static void save(UUID id, ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p == null) return;
        if (stack.isEmpty()) p.getPersistentData().remove(DATA_TAG);
        else p.getPersistentData().put(DATA_TAG, stack.save(new CompoundTag()));
        com.provinggrounds.network.PGNetwork.sendTo(p, new com.provinggrounds.network.BackpackSyncPacket(stack.copy())); // 開著箱子時也立刻更新客戶端
    }

    /** 登入：從玩家資料讀回背包欄。 */
    public static void load(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData().getCompound(DATA_TAG);
        container(p).setItem(0, tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag));
    }

    /** 登出：記憶體裡的背包欄移除（資料已存在玩家資料裡）。 */
    public static void unload(Player p) {
        (p.level().isClientSide ? CLIENT : SERVER).remove(p.getUUID());
        LAST_OTHER_PICKUP.remove(p.getUUID());
    }

    public static void clearClient() {
        CLIENT.clear();
        clientRulesActive = false;
    }

    /** 重生時新的玩家物件沒有舊的 persistentData，把背包資料複製過去。 */
    public static void copyData(Player from, Player to) {
        if (from.getPersistentData().contains(DATA_TAG)) to.getPersistentData().put(DATA_TAG, from.getPersistentData().getCompound(DATA_TAG).copy());
    }

    // ── 等級與鎖定 ───────────────────────────────────────────────────────────

    public static int level(ItemStack s) {
        return s.getItem() instanceof BackpackItem b ? b.level() : 0;
    }

    public static int level(Player p) {
        return level(container(p).getItem(0));
    }

    /** 每列開放的欄數（設定 backpack_columns；還沒載入時用預設 7）。 */
    public static int columns() {
        try {
            return PGConfig.SERVER.backpackColumns.get();
        } catch (IllegalStateException e) {
            return 7;
        }
    }

    /** 等級對應的開放格數（等級 × 每列欄數）。 */
    public static int slotsFor(int level) {
        return Math.max(0, Math.min(3, level)) * columns();
    }

    private static boolean rulesActive(Player p) {
        if (p.level().isClientSide) return clientRulesActive && !p.isCreative() && !p.isSpectator();
        return HotbarRules.activeBr(p); // 團隊模式沒有背包系統，所有背包格都能用
    }

    /** 主背包第 slot 格（Inventory 9～35）是否鎖定。 */
    public static boolean locked(Player p, int slot) {
        if (slot < 9 || slot > 35 || !rulesActive(p)) return false;
        int row = (slot - 9) / 9, col = (slot - 9) % 9; // row 0 = 最上列
        int fromBottom = 2 - row;                        // 0 = 最下列
        return fromBottom >= level(p) || col >= columns();
    }

    // ── 裝上、清除 ───────────────────────────────────────────────────────────

    /** 比目前裝的更高級就換上（從 stack 拿一個），舊的掉在腳邊。回傳是否換上。 */
    public static boolean equipIfBetter(ServerPlayer p, ItemStack stack) {
        if (level(stack) <= level(p)) return false;
        SimpleContainer c = container(p);
        ItemStack old = c.getItem(0);
        c.setItem(0, stack.split(1));
        if (!old.isEmpty()) {
            HotbarRules.insert(p, old); // 舊的放進物品欄（可以拿去給隊友），放不下才掉在腳邊
            if (!old.isEmpty()) HotbarRules.dropAtFeet(p, old);
        }
        return true;
    }

    /** 拿掉背包（開局、祭壇復活、還原時）。 */
    public static void clear(ServerPlayer p) {
        container(p).setItem(0, ItemStack.EMPTY);
    }

    /** 淘汰：背包跟其他物品一起掉落。 */
    public static void dropEquipped(ServerPlayer p) {
        SimpleContainer c = container(p);
        ItemStack s = c.getItem(0);
        if (s.isEmpty()) return;
        c.setItem(0, ItemStack.EMPTY);
        p.drop(s, true, false);
    }

    // ── 其他容器介面中拿取／替換背包（舊背包進回該容器） ─────────────────────

    private static boolean isOtherContainerSlot(@Nullable Slot slot, Player p) {
        return slot != null
            && !(slot instanceof ResultSlot)
            && !(slot instanceof BackpackSlot)
            && slot.container != p.getInventory()
            && slot.container != com.provinggrounds.special.SpecialSlot.container(p);
    }

    private static boolean putIntoOtherContainer(AbstractContainerMenu menu, ServerPlayer p, ItemStack old, @Nullable Slot preferred) {
        if (isOtherContainerSlot(preferred, p) && !preferred.hasItem() && preferred.mayPlace(old)) {
            preferred.set(old.copy());
            preferred.setChanged();
            return true;
        }
        for (Slot s : menu.slots) {
            if (isOtherContainerSlot(s, p) && !s.hasItem() && s.mayPlace(old)) {
                s.set(old.copy());
                s.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * 在非自身背包畫面（箱子、墓碑等）操作背包時：
     *   1. Shift+左鍵點選其他容器裡的背包 → 直接裝上並把原本的舊背包放回原本那格（或同一個容器空位）。
     *   2. 從其他容器左鍵拿起背包，再點自己的 BackpackSlot 替換舊背包 → 新背包直接裝上、原本的舊背包放進那個其他容器。
     * 回傳 true 表示已處理（需取消原版點擊）。
     */
    public static boolean handleContainerClick(AbstractContainerMenu menu, int slotId, int button, ClickType type, ServerPlayer sp) {
        if (menu instanceof InventoryMenu || slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot slot = menu.slots.get(slotId);

        if (type == ClickType.QUICK_MOVE && isOtherContainerSlot(slot, sp)) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof BackpackItem) || stack.getCount() != 1 || !slot.mayPickup(sp)) {
                return false;
            }
            SimpleContainer bpCont = container(sp);
            ItemStack old = bpCont.getItem(0).copy();
            if (old.isEmpty()) {
                bpCont.setItem(0, stack.copy());
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
            } else if (slot.mayPlace(old)) {
                bpCont.setItem(0, stack.copy());
                slot.set(old);
                slot.setChanged();
            } else if (putIntoOtherContainer(menu, sp, old, null)) {
                bpCont.setItem(0, stack.copy());
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
            } else {
                return false;
            }
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 1f);
            if (HotbarRules.active(sp)) HotbarRules.normalize(sp);
            menu.broadcastChanges();
            return true;
        }

        if (type == ClickType.PICKUP && button == 0) {
            ItemStack carried = menu.getCarried();
            if (slot instanceof BackpackSlot && !carried.isEmpty() && carried.getItem() instanceof BackpackItem) {
                SimpleContainer bpCont = container(sp);
                ItemStack old = bpCont.getItem(0).copy();
                if (!old.isEmpty() && old.getItem() instanceof BackpackItem) {
                    int[] origin = LAST_OTHER_PICKUP.get(sp.getUUID());
                    Slot preferred = (origin != null && origin[0] == menu.containerId
                        && origin[1] >= 0 && origin[1] < menu.slots.size())
                        ? menu.slots.get(origin[1]) : null;
                    if (putIntoOtherContainer(menu, sp, old, preferred)) {
                        bpCont.setItem(0, carried.split(1));
                        if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
                        LAST_OTHER_PICKUP.remove(sp.getUUID());
                        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 1f);
                        if (HotbarRules.active(sp)) HotbarRules.normalize(sp);
                        menu.broadcastChanges();
                        return true;
                    }
                }
            }
            if (carried.isEmpty() && isOtherContainerSlot(slot, sp) && slot.getItem().getItem() instanceof BackpackItem) {
                LAST_OTHER_PICKUP.put(sp.getUUID(), new int[] { menu.containerId, slotId });
            } else if (!carried.isEmpty()) {
                LAST_OTHER_PICKUP.remove(sp.getUUID());
            }
        }
        return false;
    }
}
