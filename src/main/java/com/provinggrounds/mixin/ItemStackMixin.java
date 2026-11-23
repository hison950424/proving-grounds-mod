package com.provinggrounds.mixin;

import com.provinggrounds.config.PGConfig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: ItemStackMixin.java
 * 目的: 所有防具（ArmorItem）的最大耐久度乘上設定倍率 armor_durability_multiplier（預設 2.0，計畫書 A14）。
 *       改在 ItemStack.getMaxDamage()，物品欄的耐久條、提示文字、壞掉的判斷都會自動跟著變，不用改物品資料。
 *       伺服器設定會在登入時同步到客戶端，兩邊算出來的值相同。
 *   為什麼用 Mixin：耐久上限由原版物品類別決定，Forge 沒有對應的事件可以改。
 *   方法名稱同時寫開發環境的名稱（getMaxDamage）與正式環境的 SRG 名稱（m_41776_），
 *   不需要 refmap；找不到其中一個不會報錯（defaultRequire = 0）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = { "getMaxDamage", "m_41776_" }, at = @At("RETURN"), cancellable = true, remap = false)
    private void provinggrounds$armorDurability(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof ArmorItem)) return;
        double mult;
        try {
            mult = PGConfig.SERVER.armorDurabilityMultiplier.get();
        } catch (IllegalStateException e) {
            return; // 設定還沒載入（例如主畫面）：用原版耐久
        }
        // 強化護甲修復包（計畫書 4 的 30 號）：每疊一次 +armor_plus_bonus 倍的原始值
        net.minecraft.nbt.CompoundTag tag = self.getTag();
        int stacks = tag == null ? 0 : tag.getInt(com.provinggrounds.special.SpecialUse.DURA_TAG);
        double bonus = 0;
        if (stacks > 0) {
            try { bonus = stacks * PGConfig.SERVER.special.armorPlusBonus.get(); } catch (IllegalStateException e) { bonus = stacks * 0.5; }
        }
        if (mult != 1.0 || bonus != 0) cir.setReturnValue((int) Math.round(cir.getReturnValue() * mult * (1 + bonus)));
    }
}
