package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 檔案: AmmoCategories.java
 * 目的: 彈藥分類對照表。原本的子彈 ID → 我們的 5 大類彈藥 ID（provinggrounds:light 等）。
 *       GunDataMixin 在 TACZ 問「這把槍吃什麼子彈」時查這張表（伺服器與玩家端都會執行）。
 *       表由伺服器設定 [ammo] 建立；伺服器設定會在玩家登入時同步到玩家端，所以兩邊一致。
 *       設定還沒載入時（例如玩家還在主畫面）表是空的，槍維持原本的子彈。
 */
public final class AmmoCategories {

    public static final String NAMESPACE = "provinggrounds";
    public static final String[] CATEGORIES = { "light", "medium", "heavy", "shotgun", "heavy_weapon" };

    private static volatile Map<ResourceLocation, ResourceLocation> table = Map.of();

    private AmmoCategories() {}

    /** 查原本子彈對應的大類；不在表上回傳 null。 */
    @Nullable
    public static ResourceLocation remap(@Nullable ResourceLocation original) {
        if (original == null) return null;
        return table.get(original);
    }

    /** 從設定重建對照表。 */
    public static void rebuild() {
        PGConfig c = PGConfig.SERVER;
        Map<ResourceLocation, ResourceLocation> map = new HashMap<>();
        try {
            if (c.ammoEnabled.get()) {
                add(map, "light", c.ammoLight.get());
                add(map, "medium", c.ammoMedium.get());
                add(map, "heavy", c.ammoHeavy.get());
                add(map, "shotgun", c.ammoShotgun.get());
                add(map, "heavy_weapon", c.ammoHeavyWeapon.get());
            }
        } catch (IllegalStateException e) {
            return; // 設定尚未載入
        }
        table = Map.copyOf(map);
        ProvingGroundsMod.LOGGER.info("Ammo categories: {} ammo types mapped.", map.size());
    }

    private static void add(Map<ResourceLocation, ResourceLocation> map, String category, List<? extends String> ids) {
        ResourceLocation target = new ResourceLocation(NAMESPACE, category);
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id.trim());
            if (rl == null) {
                ProvingGroundsMod.LOGGER.warn("Ammo categories: invalid ammo id '{}' in [ammo].{}", id, category);
                continue;
            }
            map.put(rl, target);
        }
    }

    /**
     * 檢查報告（伺服器啟動時寫進 log，/pg ammo 也會顯示）：
     *   5 種大類彈藥有沒有載入（沒有 = 槍械包 provinggrounds_ammo 沒放）、
     *   幾把槍改吃大類彈藥、還有哪些子彈不在任何分類裡。
     * 用反射呼叫 TACZ，沒裝 TACZ 時回傳說明文字。
     */
    public static List<String> report() {
        List<String> lines = new java.util.ArrayList<>();
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            java.lang.reflect.Method getAmmo = api.getMethod("getCommonAmmoIndex", ResourceLocation.class);
            List<String> missing = new java.util.ArrayList<>();
            for (String cat : CATEGORIES) {
                Object opt = getAmmo.invoke(null, new ResourceLocation(NAMESPACE, cat));
                if (!((java.util.Optional<?>) opt).isPresent()) missing.add(cat);
            }
            if (!missing.isEmpty()) lines.add("找不到大類彈藥 " + missing + "：tacz 資料夾裡沒有放槍械包 provinggrounds_ammo？");

            int total = 0, mapped = 0;
            java.util.TreeSet<String> unmapped = new java.util.TreeSet<>();
            for (Object o : (java.util.Set<?>) api.getMethod("getAllCommonGunIndex").invoke(null)) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object index = entry.getValue();
                Object data = index.getClass().getMethod("getGunData").invoke(index);
                Object ammo = data.getClass().getMethod("getAmmoId").invoke(data);
                total++;
                if (ammo instanceof ResourceLocation rl && NAMESPACE.equals(rl.getNamespace())) mapped++;
                else unmapped.add(entry.getKey() + "(" + ammo + ")");
            }
            lines.add(String.format("彈藥分類：%d / %d 把槍使用大類彈藥。", mapped, total));
            if (!unmapped.isEmpty()) lines.add("未分類（維持原本子彈）：" + String.join("、", unmapped));
        } catch (ClassNotFoundException e) {
            lines.add("沒有安裝 TACZ，彈藥分類不作用。");
        } catch (ReflectiveOperationException | RuntimeException e) {
            lines.add("彈藥分類檢查失敗：" + e);
        }
        return lines;
    }

    /**
     * 箱子生成內容後呼叫：箱子裡每有一把槍，就放一份那把槍用的子彈（半組～一組，依彈藥堆疊上限）。
     * 子彈種類問 TACZ（經過 GunDataMixin，所以是我們的大類彈藥）。沒裝 TACZ 或查不到就略過。
     */
    public static void addAmmoForGuns(net.minecraft.world.Container container, net.minecraft.util.RandomSource random) {
        net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo"));
        if (ammoItem == null || ammoItem == net.minecraft.world.item.Items.AIR) return;
        List<ResourceLocation> wanted = new java.util.ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || stack.getTag() == null || !stack.getTag().contains("GunId")) continue;
            ResourceLocation gunId = ResourceLocation.tryParse(stack.getTag().getString("GunId"));
            ResourceLocation ammoId = gunId != null ? ammoOfGun(gunId) : null;
            if (ammoId != null) wanted.add(ammoId);
        }
        for (ResourceLocation ammoId : wanted) {
            int max = stackSizeOf(ammoId);
            int count = Math.max(1, max / 2 + random.nextInt(max - max / 2 + 1));
            net.minecraft.world.item.ItemStack ammo = new net.minecraft.world.item.ItemStack(ammoItem, count);
            ammo.getOrCreateTag().putString("AmmoId", ammoId.toString());
            // 放進隨機一個空格；箱子滿了就不放
            List<Integer> empty = new java.util.ArrayList<>();
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).isEmpty()) empty.add(i);
            if (empty.isEmpty()) return;
            container.setItem(empty.get(random.nextInt(empty.size())), ammo);
        }
    }

    /** 一份這把槍用的子彈（半組～一組）；不是槍或查不到時回傳空物品。 */
    public static net.minecraft.world.item.ItemStack ammoFor(net.minecraft.world.item.ItemStack gun, net.minecraft.util.RandomSource random) {
        net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo"));
        if (ammoItem == null || ammoItem == net.minecraft.world.item.Items.AIR || gun.getTag() == null) return net.minecraft.world.item.ItemStack.EMPTY;
        ResourceLocation gunId = ResourceLocation.tryParse(gun.getTag().getString("GunId"));
        ResourceLocation ammoId = gunId != null ? ammoOfGun(gunId) : null;
        if (ammoId == null) return net.minecraft.world.item.ItemStack.EMPTY;
        int max = stackSizeOf(ammoId);
        net.minecraft.world.item.ItemStack ammo = new net.minecraft.world.item.ItemStack(ammoItem, Math.max(1, max / 2 + random.nextInt(max - max / 2 + 1)));
        ammo.getOrCreateTag().putString("AmmoId", ammoId.toString());
        return ammo;
    }

    /** 這把槍吃的子彈 ID（已套用大類對照）。 */
    @Nullable
    private static ResourceLocation ammoOfGun(ResourceLocation gunId) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, gunId);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return null;
            Object index = o.get();
            Object data = index.getClass().getMethod("getGunData").invoke(index);
            Object ammo = data.getClass().getMethod("getAmmoId").invoke(data);
            return ammo instanceof ResourceLocation rl ? rl : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** 子彈的堆疊上限（查不到用 30）。 */
    private static int stackSizeOf(ResourceLocation ammoId) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonAmmoIndex", ResourceLocation.class).invoke(null, ammoId);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                Object index = o.get();
                return Math.max(1, (Integer) index.getClass().getMethod("getStackSize").invoke(index));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 用預設值
        }
        return 30;
    }

    /** 模組匯流排：伺服器設定載入／重載（含玩家端收到同步）時重建。 */
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == PGConfig.SERVER_SPEC && event.getConfig().getType() == ModConfig.Type.SERVER) {
            rebuild();
        }
    }
}
