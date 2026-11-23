package com.provinggrounds.special;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * 檔案: KnockbackShieldItem.java
 * 目的: 擊退盾穿戴後拿在主手的盾（計畫書 4 的 9 號）。耐久 500，擋下的傷害扣耐久。
 *   外觀跟原版舉盾一樣：物品模型繼承原版 item/shield_blocking（舉盾時的角度），由 ShieldItemRenderer 畫原版盾牌模型；
 *   第三人稱手臂用舉盾姿勢。盾不能放下、不能移動或丟棄（KnockbackShield 每 tick 檢查），真正的擋傷害判斷在伺服器。
 */
public class KnockbackShieldItem extends Item {

    public KnockbackShieldItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.BLOCK;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.provinggrounds.special.client.ShieldItemRenderer();
                return renderer;
            }
        });
    }
}
