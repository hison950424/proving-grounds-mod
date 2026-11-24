package com.provinggrounds.item;

import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: ParachuteItem.java
 * 目的: 降落傘（大逃殺跳傘用）。從上飛機到落地，固定放在第 9 格、快捷欄鎖在第 9 格。
 *   物品上的 NBT「Open」= 降落傘打開中：手上的傘包變成看不見（模型覆寫 provinggrounds:open），
 *   客戶端看到誰手上拿著打開的降落傘，就在他頭上畫傘衣、雙手舉高（ParachuteRenderer、HumanoidArmsMixin）。
 *   因為「手上拿的東西」原版本來就會同步給附近的玩家，所以不用另外的網路封包。
 *   開傘、收傘的操作和以前一樣（空白鍵），伺服器每 tick 依跳傘狀態更新 Open。
 */
public class ParachuteItem extends Item {

    public static final int SLOT = 8; // 第 9 格

    public ParachuteItem(Properties props) {
        super(props);
    }

    public static boolean is(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof ParachuteItem;
    }

    public static boolean isOpen(ItemStack s) {
        return is(s) && s.getTag() != null && s.getTag().getBoolean("Open");
    }

    public static ItemStack create() {
        ItemStack s = new ItemStack(PGBlocks.PARACHUTE.get());
        s.setHoverName(Component.literal("降落傘").withStyle(ChatFormatting.AQUA));
        return s;
    }

    /**
     * 跳傘中每 tick：第 9 格一定是降落傘（其他地方的降落傘清掉；第 9 格原本的東西放回背包）、
     * 快捷欄選在第 9 格、Open 跟著是否開傘。
     */
    public static void enforce(ServerPlayer p, boolean open) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i != SLOT && is(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (is(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        ItemStack cur = inv.getItem(SLOT);
        if (!is(cur)) {
            if (!cur.isEmpty()) {
                ItemStack moved = cur.copy();
                inv.setItem(SLOT, ItemStack.EMPTY);
                if (!inv.add(moved) && !moved.isEmpty()) p.drop(moved, false);
            }
            cur = create();
            inv.setItem(SLOT, cur);
        }
        if (isOpen(cur) != open) {
            ItemStack updated = cur.copy();
            if (open) updated.getOrCreateTag().putBoolean("Open", true);
            else if (updated.getTag() != null) updated.getTag().remove("Open");
            inv.setItem(SLOT, updated);
        }
        if (inv.selected != SLOT) {
            inv.selected = SLOT;
            p.connection.send(new ClientboundSetCarriedItemPacket(SLOT));
        }
    }

    /** 落地、淘汰、還原：收回所有降落傘。 */
    public static void removeAll(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (is(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (is(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }
}
