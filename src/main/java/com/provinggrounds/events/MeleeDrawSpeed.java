package com.provinggrounds.events;

import com.google.common.collect.Multimap;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: MeleeDrawSpeed.java
 * 目的: 拔刀動畫播完才有跑速加成（所有模式，只在伺服器執行）。
 *   近戰武器（lrtactical 近戰、CS2 刀具包）本身帶「移動速度 +20%」的物品屬性，一拿到手上原版就立刻套用；
 *   大逃殺另外有「拿近戰武器 = melee_speed_multiplier」的規則（BRMovement）。
 *   這裡記錄玩家「這一把刀是什麼時候拿出來的」（換格、換武器都重新計時），在拔刀動畫長度（設定 melee_draw_ticks）內：
 *     - 加一個剛好抵銷刀子速度屬性的暫時修改（不寫進存檔），所以拔刀期間跟拿刀前一樣快；
 *     - drawn() 回傳 false，BRMovement 也先不套用近戰速度。
 *   動畫播完就移除抵銷修改、drawn() 回傳 true。
 *   執行者：每位線上玩家（PlayerTickEvent，伺服器端）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class MeleeDrawSpeed {

    private static final UUID CANCEL_ID = UUID.fromString("3e6a9c51-7d24-4f8b-b0e2-5a91c8d7f364");

    /** 玩家目前拿著的那一把（格子＋武器）與拿出來的時間（遊戲刻）。 */
    private record Held(int slot, String weapon, long since) {}
    private static final Map<UUID, Held> HELD = new HashMap<>();

    private MeleeDrawSpeed() {}

    /** 重新開始拔刀計時（例如攀爬收起武器結束後，重新拔出近戰武器）。 */
    public static void restartDraw(ServerPlayer p) {
        if (p != null) HELD.remove(p.getUUID());
    }

    /** 手上拿著近戰武器，而且拔刀動畫已經播完。 */
    public static boolean drawn(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (!stack.is(GunCompat.MELEE)) return false;
        Held h = HELD.get(p.getUUID());
        if (h == null || h.slot() != p.getInventory().selected || !h.weapon().equals(weaponId(stack))) return false; // 這 tick 剛換：還沒拔完
        return p.serverLevel().getGameTime() - h.since() >= drawTicks(h.weapon());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer p)) return;
        ItemStack stack = p.getMainHandItem();
        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        if (!stack.is(GunCompat.MELEE)) {
            HELD.remove(p.getUUID());
            if (attr.getModifier(CANCEL_ID) != null) attr.removeModifier(CANCEL_ID);
            return;
        }
        long now = p.serverLevel().getGameTime();
        String weapon = weaponId(stack);
        int slot = p.getInventory().selected;
        Held h = HELD.get(p.getUUID());
        if (h == null || h.slot() != slot || !h.weapon().equals(weapon)) {
            h = new Held(slot, weapon, now);
            HELD.put(p.getUUID(), h);
        }
        boolean drawing = now - h.since() < drawTicks(weapon);
        AttributeModifier cur = attr.getModifier(CANCEL_ID);
        if (!drawing) {
            if (cur != null) attr.removeModifier(CANCEL_ID);
            return;
        }
        AttributeModifier want = cancelFor(stack);
        if (want == null) {
            if (cur != null) attr.removeModifier(CANCEL_ID);
        } else if (cur == null || cur.getOperation() != want.getOperation() || Math.abs(cur.getAmount() - want.getAmount()) > 1e-6) {
            if (cur != null) attr.removeModifier(CANCEL_ID);
            attr.addTransientModifier(want);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
    }

    /**
     * 算出剛好抵銷這把武器移動速度屬性的修改（只抵銷第一個；刀子只有一個速度屬性）：
     * 加法 / 乘基礎值 → 取負號；乘總值 (1 + a) → 乘 1 / (1 + a)。
     */
    private static AttributeModifier cancelFor(ItemStack stack) {
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (AttributeModifier m : mods.get(Attributes.MOVEMENT_SPEED)) {
            double a = m.getAmount();
            if (a == 0) continue;
            double amount = m.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL ? 1.0 / (1.0 + a) - 1.0 : -a;
            return new AttributeModifier(CANCEL_ID, "pg_melee_drawing", amount, m.getOperation());
        }
        return null;
    }

    private static String weaponId(ItemStack stack) {
        var tag = stack.getTag();
        String id = tag != null ? tag.getString("MeleeWeaponId") : "";
        return id.isEmpty() ? String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())) : id;
    }

    private static int drawTicks(String weapon) {
        try {
            for (String s : PGConfig.SERVER.meleeDrawTicks.get()) {
                int eq = s.lastIndexOf('=');
                if (eq > 0 && s.substring(0, eq).trim().equals(weapon)) {
                    try { return Math.max(0, Integer.parseInt(s.substring(eq + 1).trim())); } catch (NumberFormatException ignored) {}
                }
            }
            return PGConfig.SERVER.meleeDrawTicksDefault.get();
        } catch (IllegalStateException e) {
            return 20; // 設定還沒載入
        }
    }
}
