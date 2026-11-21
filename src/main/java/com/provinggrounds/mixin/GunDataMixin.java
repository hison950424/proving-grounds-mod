package com.provinggrounds.mixin;

import com.provinggrounds.compat.AmmoCategories;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: GunDataMixin.java
 * 目的: 改寫 TACZ 槍械資料的 getAmmoId()：槍要吃的子彈改成我們的大類彈藥（對照表見 AmmoCategories）。
 *       TACZ 判斷「背包裡的子彈能不能裝進這把槍」都是透過這個方法，所以改這裡就全部生效，
 *       不用去改每個槍械包的檔案。沒裝 TACZ 時這個 Mixin 會被略過（@Pseudo + required=false）。
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.resource.pojo.data.gun.GunData", remap = false)
public abstract class GunDataMixin {

    @Inject(method = "getAmmoId", at = @At("RETURN"), cancellable = true, remap = false)
    private void provinggrounds$remapAmmo(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation mapped = AmmoCategories.remap(cir.getReturnValue());
        if (mapped != null) cir.setReturnValue(mapped);
    }
}
