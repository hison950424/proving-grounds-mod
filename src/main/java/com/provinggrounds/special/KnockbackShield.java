package com.provinggrounds.special;

import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 檔案: KnockbackShield.java
 * 目的: 擊退盾（計畫書 4 的 9 號）。
 *   第一次使用 = 穿戴：把「當時拿著的那一格」的物品暫存到玩家資料（pg_shield），那一格換成擊退盾（耐久 500）；
 *     穿戴中不能切換快捷欄（每 tick 鎖回那一格），盾一直舉著（KnockbackShieldItem），擋下前方 180 度內的傷害並扣耐久。
 *   再次使用（按使用特殊物品鍵）= 向前衝撞，最遠 25 格：撞到敵人就停下，以敵人為中心放半徑 2.5 格的衝擊波（自己不受影響）；
 *     沒撞到人但撞到牆就在那裡放；都沒有就在終點放。
 *   用完（耐久耗盡或衝撞結束）、倒地、死亡、斷線、每場結束：一律先把原本那格的物品還回去。
 *   暫存放在玩家資料裡，伺服器重開後玩家登入也會還回去。
 */
public final class KnockbackShield {

    private static final String TAG = "pg_shield";

    private static final class Charge { Vec3 dir; Vec3 from; int ticks; }
    private static final Map<UUID, Charge> CHARGING = new HashMap<>();

    private KnockbackShield() {}

    public static boolean active(ServerPlayer p) {
        return p.getPersistentData().contains(TAG);
    }

    /** 穿戴。回傳是否成功。 */
    public static boolean equip(ServerPlayer p) {
        if (active(p)) return false;
        Inventory inv = p.getInventory();
        int slot = inv.selected;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.put("Stash", inv.getItem(slot).save(new CompoundTag()));
        p.getPersistentData().put(TAG, tag);
        ItemStack shield = new ItemStack(SpecialRegistry.SHIELD_HELD.get());
        inv.setItem(slot, shield);
        p.inventoryMenu.broadcastChanges();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1f, 0.8f);
        p.displayClientMessage(Component.literal("已舉起擊退盾：再按一次使用特殊物品鍵向前衝撞").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 衝撞（再次使用）。 */
    public static void charge(ServerPlayer p) {
        if (!active(p) || CHARGING.containsKey(p.getUUID())) return;
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1e-4) dir = Vec3.directionFromRotation(0, p.getYRot());
        Charge c = new Charge();
        c.dir = dir.normalize();
        c.from = p.position();
        CHARGING.put(p.getUUID(), c);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 每 tick（SpecialEvents）。 */
    public static void tick(ServerPlayer p) {
        if (!active(p)) return;
        CompoundTag tag = p.getPersistentData().getCompound(TAG);
        int slot = tag.getInt("Slot");
        Inventory inv = p.getInventory();
        if (inv.selected != slot) { // 穿戴中不能切換快捷欄
            inv.selected = slot;
            p.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
        ItemStack held = inv.getItem(slot);
        if (!held.is(SpecialRegistry.SHIELD_HELD.get())) {
            // 盾被移走了（不應該發生）：先把別處的盾拿掉再放回去
            removeShields(inv);
            ItemStack other = inv.getItem(slot);
            if (!other.isEmpty() && !inv.add(other.copy())) com.provinggrounds.modes.br.HotbarRules.dropAtFeet(p, other.copy());
            inv.setItem(slot, new ItemStack(SpecialRegistry.SHIELD_HELD.get()));
        }
        Charge c = CHARGING.get(p.getUUID());
        if (c == null) return;
        SpecialConfig s = SpecialConfig.get();
        double speed = 1.2;
        p.setDeltaMovement(c.dir.x * speed, Math.min(p.getDeltaMovement().y, 0.1), c.dir.z * speed);
        com.provinggrounds.events.BulletHitEvents.allow(p); // 擊退盾衝撞：自己的移動
        p.hurtMarked = true;
        p.fallDistance = 0;
        // 撞到敵人？
        for (LivingEntity e : Engines.livingAround(p.serverLevel(), p.position(), 1.6)) {
            if (e == p || Engines.ally(p, e)) continue;
            finishCharge(p, e.position(), s);
            return;
        }
        double travelled = Math.hypot(p.getX() - c.from.x, p.getZ() - c.from.z);
        // 撞到牆、到達最遠距離、或衝太久（卡住）就結束
        if ((p.horizontalCollision && travelled > 0.8) || travelled >= s.shieldChargeRange.get() || ++c.ticks > 60) {
            finishCharge(p, p.position().add(c.dir.scale(0.5)), s);
        }
    }

    private static void finishCharge(ServerPlayer p, Vec3 at, SpecialConfig s) {
        CHARGING.remove(p.getUUID());
        p.setDeltaMovement(Vec3.ZERO);
        com.provinggrounds.events.BulletHitEvents.allow(p); // 擊退盾衝撞：自己的移動
        p.hurtMarked = true;
        SpecialConfig sc = SpecialConfig.get(); // 擊退盾：自己與隊友不受影響
        Engines.shockwave(p.serverLevel(), at, s.shieldShockRadius.get(), sc.shockCenterDamage.get(), SpecialConfig.ticks(sc.shockConcussSeconds), p, true);
        restore(p);
    }

    /** 受到傷害：前方 180 度內的傷害由盾擋下，扣耐久。回傳 true = 傷害取消。 */
    public static boolean tryBlock(ServerPlayer p, DamageSource source, float amount) {
        if (!active(p)) return false;
        Vec3 from = source.getSourcePosition();
        if (from == null) return false; // 沒有方向的傷害（圈外、流血、摔落）擋不了
        Vec3 look = p.getLookAngle();
        Vec3 to = from.subtract(p.position());
        if (look.x * to.x + look.z * to.z <= 0) return false; // 背後
        Inventory inv = p.getInventory();
        int slot = p.getPersistentData().getCompound(TAG).getInt("Slot");
        ItemStack shield = inv.getItem(slot);
        if (!shield.is(SpecialRegistry.SHIELD_HELD.get())) return false;
        int max = SpecialConfig.get().shieldDurability.get();
        // 物品本身的耐久上限是 500；設定不同時依比例換算
        int cost = Math.max(1, (int) Math.ceil(amount * shield.getMaxDamage() / (double) max));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
        if (shield.getDamageValue() + cost >= shield.getMaxDamage()) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1f, 1f);
            CHARGING.remove(p.getUUID());
            restore(p);
        } else {
            shield.setDamageValue(shield.getDamageValue() + cost);
        }
        return true;
    }

    /** 拿掉盾、還回原本那一格的物品。 */
    public static void restore(ServerPlayer p) {
        CHARGING.remove(p.getUUID());
        if (!active(p)) return;
        CompoundTag tag = p.getPersistentData().getCompound(TAG);
        p.getPersistentData().remove(TAG);
        Inventory inv = p.getInventory();
        removeShields(inv);
        int slot = tag.getInt("Slot");
        ItemStack stash = ItemStack.of(tag.getCompound("Stash"));
        if (!stash.isEmpty()) {
            if (inv.getItem(slot).isEmpty()) inv.setItem(slot, stash);
            else if (!inv.add(stash)) com.provinggrounds.modes.br.HotbarRules.dropAtFeet(p, stash);
        }
        p.inventoryMenu.broadcastChanges();
    }

    private static void removeShields(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(SpecialRegistry.SHIELD_HELD.get())) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    public static void restoreAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) restore(p);
        CHARGING.clear();
    }

    public static boolean charging(ServerPlayer p) { return CHARGING.containsKey(p.getUUID()); }

    /** 對局沒有在進行（例如回到大廳）時還拿著盾：還回去。 */
    public static void sanity(ServerPlayer p) {
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (active(p) && br.isInPlay() && !br.isAliveParticipant(p.getUUID()) && !p.isCreative()) restore(p); // 對局中被淘汰等：還原（大廳測試時不還原）
    }
}
