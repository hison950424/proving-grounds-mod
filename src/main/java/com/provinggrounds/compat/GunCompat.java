package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.events.ArmorEvents;
import com.provinggrounds.modes.br.BRDowned;
import net.minecraftforge.fml.ModList;

/**
 * 檔案: GunCompat.java
 * 目的: 在模組設定階段挑選並啟用槍械適配層（有裝哪個槍械模組就用哪個）。
 *       倒地中的玩家不能開槍、近戰、換彈。
 */
public final class GunCompat {

    private static GunAdapter active;

    /**
     * 子彈造成的傷害類型（資料驅動：data/provinggrounds/tags/damage_type/bullets.json，目前包含 #tacz:bullets）。
     * 日後換成自製槍械時只改這個標籤檔。
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> BULLET_DAMAGE =
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
            new net.minecraft.resources.ResourceLocation(ProvingGroundsMod.MOD_ID, "bullets"));

    /** 物品分類標籤（資料驅動，data/provinggrounds/tags/items/）：彈藥、配件、近戰。 */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> AMMO = itemTag("ammo"), ATTACHMENT = itemTag("attachment"), MELEE = itemTag("melee");

    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> itemTag(String name) {
        return net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation(ProvingGroundsMod.MOD_ID, name));
    }

    private GunCompat() {}

    /** 是不是槍械配件；沒有槍械模組時一律 false。 */
    public static boolean isAttachment(net.minecraft.world.item.ItemStack stack) {
        return active != null && active.isAttachment(stack);
    }

    /** 這把槍能不能裝這個配件；沒有槍械模組時一律 false。 */
    public static boolean canAttach(net.minecraft.world.item.ItemStack gun, net.minecraft.world.item.ItemStack attachment) {
        return active != null && active.canAttach(gun, attachment);
    }

    /** 物品的槍械分類；沒有槍械模組時一律 NONE。 */
    public static GunClass classify(net.minecraft.world.item.ItemStack stack) {
        return active == null ? GunClass.NONE : active.classify(stack);
    }

    /** 把配件裝到槍上；回傳換下來的舊配件（可能是空物品），裝不上回傳 null。 */
    @javax.annotation.Nullable
    public static net.minecraft.world.item.ItemStack installAttachment(net.minecraft.world.entity.player.Player p,
            net.minecraft.world.item.ItemStack gun, net.minecraft.world.item.ItemStack attachment) {
        return active == null ? null : active.installAttachment(p, gun, attachment);
    }

    @javax.annotation.Nullable
    public static String gunType(net.minecraft.world.item.ItemStack stack) { return active == null ? null : active.gunType(stack); }

    public static java.util.List<net.minecraft.resources.ResourceLocation> gunsOfType(String type) { return active == null ? java.util.List.of() : active.gunsOfType(type); }

    public static java.util.Set<String> allGunIds() { return active == null ? java.util.Set.of() : active.allGunIds(); }

    public static net.minecraft.world.item.ItemStack createGun(net.minecraft.resources.ResourceLocation id) { return active == null ? net.minecraft.world.item.ItemStack.EMPTY : active.createGun(id); }

    /** 上好子彈的槍（團隊死鬥／佔點／槍王之王發槍用）。 */
    public static net.minecraft.world.item.ItemStack createLoadedGun(net.minecraft.resources.ResourceLocation id, @javax.annotation.Nullable String fireMode) { return active == null ? net.minecraft.world.item.ItemStack.EMPTY : active.createLoadedGun(id, fireMode); }

    /** 把槍裝滿子彈（不是槍就不做事）。 */
    public static void fillAmmo(net.minecraft.world.item.ItemStack gun) { if (active != null) active.fillAmmo(gun); }

    /** 這把槍有沒有裝消音器；沒有槍械模組時一律 false。 */
    public static boolean isSilenced(net.minecraft.world.item.ItemStack gun) {
        return active != null && !gun.isEmpty() && active.isSilenced(gun);
    }

    /** 槍的 ID；沒有槍械模組或不是槍時回傳 null。 */
    @javax.annotation.Nullable
    public static net.minecraft.resources.ResourceLocation gunId(net.minecraft.world.item.ItemStack stack) {
        return active == null ? null : active.gunId(stack);
    }

    /** 在實體的位置播放槍聲；沒有槍械模組或失敗時回傳 false。 */
    public static boolean playGunSound(net.minecraft.world.entity.Entity at, net.minecraft.world.item.ItemStack gun) {
        return active != null && active.playGunSound(at, gun);
    }

    private static final java.util.List<java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>> SHOOT_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 每次真的開槍時通知（兩端都會呼叫，用 instanceof ServerPlayer 分辨伺服器端）。 */
    public static void onShoot(java.util.function.Consumer<net.minecraft.world.entity.LivingEntity> listener) { SHOOT_LISTENERS.add(listener); }

    /** 主手拿的是不是槍。 */
    public static boolean holdsGun(net.minecraft.world.entity.LivingEntity e) {
        return classify(e.getMainHandItem()) != GunClass.NONE;
    }

    public static void init() {

        if (ModList.get().isLoaded("tacz")) {
            GunAdapter adapter = new TaczGunAdapter();
            active = adapter;
            if (adapter.registerBlocker(e -> com.provinggrounds.special.ActionLock.blocksActions(e) || com.provinggrounds.modes.arena.ArenaManager.blocksFire(e))) {
                ProvingGroundsMod.LOGGER.info("Gun adapter enabled: {}", adapter.name());
            }
            adapter.registerHitListener(ArmorEvents::recordHit); // 防彈：記錄爆頭
            adapter.registerDamageBalancer(); // 槍械平衡（GunBalance）
            // 精準命中＋友軍誤傷＋領域展開過濾：子彈這一 tick 的路線要打到人物模型，且通過友軍誤傷／領域規則才算數
            adapter.registerHitFilter((target, bullet) -> {
                if (!com.provinggrounds.events.FriendlyFireEvents.allowBulletHit(target, bullet)) return false;
                if (com.provinggrounds.special.Plan9Specials.blocksBullet(target, bullet)) return false;
                net.minecraft.world.phys.Vec3 v = bullet.getDeltaMovement();
                return com.provinggrounds.core.Hitbox.hitsModel(target, bullet.position().subtract(v), bullet.position().add(v));
            });
            adapter.registerBlockHitListener(com.provinggrounds.events.GlassEvents::onBulletHit); // 記錄被子彈打破的玻璃
            adapter.registerShootListener(shooter -> { for (var l : SHOOT_LISTENERS) l.accept(shooter); }); // 開槍通知（中斷讀條等）

        } else {
            ProvingGroundsMod.LOGGER.info("No supported gun mod loaded; gun adapter disabled.");
        }
    }
}
