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

    public static void init() {
        if (ModList.get().isLoaded("tacz")) {
            GunAdapter adapter = new TaczGunAdapter();
            active = adapter;
            if (adapter.registerBlocker(BRDowned::cannotAct)) {
                ProvingGroundsMod.LOGGER.info("Gun adapter enabled: {}", adapter.name());
            }
            adapter.registerHitListener(ArmorEvents::recordHit); // 防彈：記錄爆頭
            adapter.registerBlockHitListener(com.provinggrounds.events.GlassEvents::onBulletHit); // 記錄被子彈打破的玻璃

        } else {
            ProvingGroundsMod.LOGGER.info("No supported gun mod loaded; gun adapter disabled.");
        }
    }
}
