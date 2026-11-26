package com.provinggrounds.special;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.special.entity.DecoyEntity;
import com.provinggrounds.special.entity.MineEntity;
import com.provinggrounds.special.entity.SpecialProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * 檔案: SpecialRegistry.java
 * 目的: 特殊物品系統要註冊的東西：34 種特殊物品、擊退盾（拿在手上的那一面盾）、自訂效果、實體。
 *   特殊物品都在物品標籤 provinggrounds:special（data/provinggrounds/tags/items/special.json）。
 */
public final class SpecialRegistry {

    public static final TagKey<Item> SPECIAL = ItemTags.create(new ResourceLocation(ProvingGroundsMod.MOD_ID, "special"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ProvingGroundsMod.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ProvingGroundsMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ProvingGroundsMod.MOD_ID);

    public static final Map<SpecialType, RegistryObject<Item>> ITEM = new EnumMap<>(SpecialType.class);
    static {
        for (SpecialType t : SpecialType.values()) {
            ITEM.put(t, ITEMS.register(t.id, () -> new SpecialItem(t, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        }
    }

    /** 擊退盾：穿戴後拿在主手的那一面盾（耐久 500，不是特殊物品，不能被拿走）。 */
    public static final RegistryObject<Item> SHIELD_HELD = ITEMS.register("knockback_shield_held",
        () -> new KnockbackShieldItem(new Item.Properties().durability(500).rarity(Rarity.RARE)));

    // ── 效果（都沒有粒子；套用時一律 visible=false） ─────────────────────────
    public static final RegistryObject<MobEffect> CONCUSSION   = effect("concussion", MobEffectCategory.HARMFUL, 0xC0C0C0);
    public static final RegistryObject<MobEffect> CLOAK        = effect("cloak", MobEffectCategory.BENEFICIAL, 0x8080C0);
    public static final RegistryObject<MobEffect> ADRENALINE   = effect("adrenaline", MobEffectCategory.BENEFICIAL, 0xE04040);
    public static final RegistryObject<MobEffect> SILENT       = effect("silent", MobEffectCategory.BENEFICIAL, 0x404060);
    public static final RegistryObject<MobEffect> SHIELD_BUBBLE = effect("shield_bubble", MobEffectCategory.BENEFICIAL, 0x60C0FF);
    public static final RegistryObject<MobEffect> BUBBLE_SPEED = effect("bubble_speed", MobEffectCategory.BENEFICIAL, 0x60C0FF);
    public static final RegistryObject<MobEffect> ZONE_SHELTER = effect("zone_shelter", MobEffectCategory.BENEFICIAL, 0xC0F0C0);
    public static final RegistryObject<MobEffect> GASSED       = effect("gassed", MobEffectCategory.HARMFUL, 0x60C020);
    public static final RegistryObject<MobEffect> TOTEM_HELD   = effect("totem_held", MobEffectCategory.BENEFICIAL, 0xF0D040);
    public static final RegistryObject<MobEffect> BRAINWASH    = effect("brainwash", MobEffectCategory.HARMFUL, 0xC040C0); // 計畫書 6 E1
    public static final RegistryObject<MobEffect> STIM_FREE    = effect("stim_free", MobEffectCategory.BENEFICIAL, 0xE04040); // 計畫書 7 C10：強心劑倒地後可行動
    public static final RegistryObject<MobEffect> DOWNED_MARK  = effect("downed", MobEffectCategory.HARMFUL, 0xA02020); // 計畫書 7 E：團隊模式倒地（讓客戶端知道自己倒地）
    public static final RegistryObject<MobEffect> ROOTED       = effect("rooted", MobEffectCategory.HARMFUL, 0x50E0B0);    // 計畫書 6 E2
    // 計畫書 9 新增效果
    public static final RegistryObject<MobEffect> FRIEND_OR_FOE = effect("friend_or_foe", MobEffectCategory.HARMFUL, 0xD05080);
    public static final RegistryObject<MobEffect> PARANOIA      = effect("paranoia", MobEffectCategory.HARMFUL, 0xB02040);
    public static final RegistryObject<MobEffect> MISFIRE       = effect("misfire", MobEffectCategory.HARMFUL, 0xF05020);
    public static final RegistryObject<MobEffect> ICE_SKATES    = effect("ice_skates", MobEffectCategory.NEUTRAL, 0x80E0FF);
    public static final RegistryObject<MobEffect> HUD_JAM       = effect("hud_jam", MobEffectCategory.HARMFUL, 0x505060);
    public static final RegistryObject<MobEffect> OVERHEAT      = effect("overheat", MobEffectCategory.HARMFUL, 0xF08020);
    public static final RegistryObject<MobEffect> ETHEREAL      = effect("ethereal", MobEffectCategory.BENEFICIAL, 0xB0E0FF);
    public static final RegistryObject<MobEffect> MIMIC         = effect("mimic", MobEffectCategory.BENEFICIAL, 0xC09040);

    // ── 實體 ─────────────────────────────────────────────────────────────────
    public static final RegistryObject<EntityType<MineEntity>> MINE = ENTITIES.register("mine",
        () -> EntityType.Builder.<MineEntity>of(MineEntity::new, MobCategory.MISC).sized(0.5f, 0.1f)
            .clientTrackingRange(8).updateInterval(20).build("mine"));
    public static final RegistryObject<EntityType<DecoyEntity>> DECOY = ENTITIES.register("decoy",
        () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC).sized(0.6f, 1.8f)
            .clientTrackingRange(10).build("decoy"));
    public static final RegistryObject<EntityType<SpecialProjectile>> PROJECTILE = ENTITIES.register("special_projectile",
        () -> EntityType.Builder.<SpecialProjectile>of(SpecialProjectile::new, MobCategory.MISC).sized(0.25f, 0.25f)
            .clientTrackingRange(8).updateInterval(2).build("special_projectile"));

    private SpecialRegistry() {}

    private static RegistryObject<MobEffect> effect(String name, MobEffectCategory cat, int color) {
        return EFFECTS.register(name, () -> new MobEffect(cat, color) {});
    }

    /** 音效（assets/provinggrounds/sounds.json）。毒氣咳嗽：公眾領域的咳嗽錄音切出 4 聲並過濾雜音（tools/extract_cough_clips.py，來源見 docs/CREDITS.md）。 */
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ProvingGroundsMod.MOD_ID);
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> GAS_COUGH = SOUNDS.register("gas_cough",
        () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(ProvingGroundsMod.MOD_ID, "gas_cough")));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);

        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(SpecialRegistry::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DECOY.get(), DecoyEntity.createAttributes().build());
    }

    public static Item item(SpecialType t) { return ITEM.get(t).get(); }
}
