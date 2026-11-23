package com.provinggrounds.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * 檔案: KnockbackShieldItem.java
 * 目的: 擊退盾穿戴後拿在主手的盾（計畫書 4 的 9 號）。耐久 500，擋下的傷害扣耐久。
 *   一直維持「舉盾」的樣子（第三人稱手臂用舉盾姿勢、第一人稱盾移到畫面中央），不能放下；
 *   真正的擋傷害判斷在伺服器（KnockbackShield）。這個物品不能被移動或丟棄（KnockbackShield 每 tick 檢查）。
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
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.BLOCK;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                // 像原版舉盾：往畫面中央、稍微往前，並轉正
                pose.translate(side * 0.3f, -0.45f, -0.75f);
                pose.mulPose(Axis.YP.rotationDegrees(side * -20f));
                pose.scale(1.4f, 1.4f, 1.4f);
                return true;
            }
        });
    }
}
