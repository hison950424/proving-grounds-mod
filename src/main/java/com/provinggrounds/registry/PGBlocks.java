package com.provinggrounds.registry;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.AltarBlock;
import com.provinggrounds.block.AltarBlockEntity;
import com.provinggrounds.block.CrateBlock;
import com.provinggrounds.block.CrateBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 檔案: PGBlocks.java
 * 目的: 註冊 Mod 自製方塊、方塊物品與方塊實體類型。
 */
public final class PGBlocks {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, ProvingGroundsMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ProvingGroundsMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ProvingGroundsMod.MOD_ID);

    public static final RegistryObject<Block> CRATE_LOW     = crate("crate_low", MapColor.WOOD, SoundType.WOOD);
    public static final RegistryObject<Block> CRATE_MID     = crate("crate_mid", MapColor.METAL, SoundType.METAL);
    public static final RegistryObject<Block> CRATE_HIGH    = crate("crate_high", MapColor.GOLD, SoundType.METAL);
    public static final RegistryObject<Block> CRATE_AIRDROP = crate("crate_airdrop", MapColor.GOLD, SoundType.METAL);

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CrateBlockEntity>> CRATE_BE =
        BLOCK_ENTITIES.register("crate", () -> BlockEntityType.Builder.of(CrateBlockEntity::new,
            CRATE_LOW.get(), CRATE_MID.get(), CRATE_HIGH.get(), CRATE_AIRDROP.get()).build(null));

    /** 復活祭壇（兩格高，就緒時亮度 7）。 */
    public static final RegistryObject<Block> ALTAR = BLOCKS.register("altar", () -> new AltarBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 1200f).sound(SoundType.STONE)
            .lightLevel(s -> s.getValue(AltarBlock.READY) ? 7 : 0)));
    public static final RegistryObject<Item> ALTAR_ITEM = ITEMS.register("altar",
        () -> new BlockItem(ALTAR.get(), new Item.Properties()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AltarBlockEntity>> ALTAR_BE =
        BLOCK_ENTITIES.register("altar", () -> BlockEntityType.Builder.of(AltarBlockEntity::new, ALTAR.get()).build(null));

    /** 墓碑：BR 淘汰時收納身上所有物品，一局結束清除（不掉落、一般玩家不能破壞）。 */
    public static final RegistryObject<Block> TOMBSTONE = BLOCKS.register("tombstone", () -> new com.provinggrounds.block.TombstoneBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0f, 3600000f).sound(SoundType.STONE).noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<com.provinggrounds.block.TombstoneBlockEntity>> TOMBSTONE_BE =
        BLOCK_ENTITIES.register("tombstone", () -> BlockEntityType.Builder.of(com.provinggrounds.block.TombstoneBlockEntity::new, TOMBSTONE.get()).build(null));

    /** 數據看板（計畫書 B5）：只有 OP 或創造模式能擺放、破壞。 */
    public static final RegistryObject<Block> STATS_BOARD = BLOCKS.register("stats_board", () -> new com.provinggrounds.block.StatsBoardBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 3600000f).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> STATS_BOARD_ITEM = ITEMS.register("stats_board",
        () -> new BlockItem(STATS_BOARD.get(), new Item.Properties()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<com.provinggrounds.block.StatsBoardBlockEntity>> STATS_BOARD_BE =
        BLOCK_ENTITIES.register("stats_board", () -> BlockEntityType.Builder.of(com.provinggrounds.block.StatsBoardBlockEntity::new, STATS_BOARD.get()).build(null));

    /** 復活牌（只會以掉落物形式存在；撿起後轉成玩家身上的資料，不進背包）。 */
    /** 電梯方塊：站在上面跳躍 = 傳送到正上方的電梯、蹲下 = 傳送到正下方的電梯（邏輯在 block/ElevatorEvents）。 */
    public static final RegistryObject<Block> ELEVATOR = BLOCKS.register("elevator", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> ELEVATOR_ITEM = ITEMS.register("elevator",
        () -> new BlockItem(ELEVATOR.get(), new Item.Properties()));

    /** 跑酷開始方塊、跑酷結束方塊（計畫書 6 D1；邏輯在 block/Parkour）。 */
    public static final RegistryObject<Block> PARKOUR_START = BLOCKS.register("parkour_start", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(1.5f, 6f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> PARKOUR_START_ITEM = ITEMS.register("parkour_start",
        () -> new BlockItem(PARKOUR_START.get(), new Item.Properties()));
    public static final RegistryObject<Block> PARKOUR_END = BLOCKS.register("parkour_end", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f, 6f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> PARKOUR_END_ITEM = ITEMS.register("parkour_end",
        () -> new BlockItem(PARKOUR_END.get(), new Item.Properties()));

    public static final RegistryObject<Item> REVIVE_CARD = ITEMS.register("revive_card",
        () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 降落傘（跳傘時固定在第 9 格）與只用來畫在頭上的傘衣模型（不會出現在遊戲裡）。 */
    public static final RegistryObject<Item> PARACHUTE = ITEMS.register("parachute",
        () -> new com.provinggrounds.item.ParachuteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PARACHUTE_CANOPY = ITEMS.register("parachute_canopy",
        () -> new Item(new Item.Properties().stacksTo(1)));

    /** 一～三級背包（計畫書 B3）。 */

    public static final RegistryObject<Item> BACKPACK_1 = ITEMS.register("backpack_1",
        () -> new com.provinggrounds.item.BackpackItem(1, new Item.Properties()));
    public static final RegistryObject<Item> BACKPACK_2 = ITEMS.register("backpack_2",
        () -> new com.provinggrounds.item.BackpackItem(2, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BACKPACK_3 = ITEMS.register("backpack_3",
        () -> new com.provinggrounds.item.BackpackItem(3, new Item.Properties().rarity(Rarity.EPIC)));

    /** 管理員工具物品（計畫書 B6～B10），放在專屬的創造模式分頁「PG 管理工具」。 */
    public static final java.util.Map<com.provinggrounds.item.AdminToolItem.Tool, RegistryObject<Item>> ADMIN_TOOLS = new java.util.EnumMap<>(com.provinggrounds.item.AdminToolItem.Tool.class);
    static {
        for (com.provinggrounds.item.AdminToolItem.Tool t : com.provinggrounds.item.AdminToolItem.Tool.values()) {
            ADMIN_TOOLS.put(t, ITEMS.register(t.id, () -> new com.provinggrounds.item.AdminToolItem(t)));
        }
    }

    /** 墓碑畫面（跟物品欄一樣的分區）。 */
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, ProvingGroundsMod.MOD_ID);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.provinggrounds.block.TombstoneMenu>> TOMBSTONE_MENU =
        MENUS.register("tombstone", () -> new net.minecraft.world.inventory.MenuType<>(com.provinggrounds.block.TombstoneMenu::new,
            net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ProvingGroundsMod.MOD_ID);
    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> ADMIN_TAB = TABS.register("admin_tools",
        () -> net.minecraft.world.item.CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.literal("Proving Grounds"))
            .icon(() -> new net.minecraft.world.item.ItemStack(ADMIN_TOOLS.get(com.provinggrounds.item.AdminToolItem.Tool.FLOAT_TEXT).get()))
            .displayItems((params, out) -> {
                // 管理員會用到的全部放這裡（看板、箱子、祭壇、背包、工具），不用到各個原版分頁找
                ADMIN_TOOLS.values().forEach(r -> out.accept(r.get()));
                out.accept(STATS_BOARD_ITEM.get());
                out.accept(CRATE_LOW.get()); out.accept(CRATE_MID.get()); out.accept(CRATE_HIGH.get()); out.accept(CRATE_AIRDROP.get());
                out.accept(ALTAR_ITEM.get());
                out.accept(ELEVATOR_ITEM.get());
                out.accept(PARKOUR_START_ITEM.get());
                out.accept(PARKOUR_END_ITEM.get());
                out.accept(BACKPACK_1.get()); out.accept(BACKPACK_2.get()); out.accept(BACKPACK_3.get());
                out.accept(REVIVE_CARD.get());
                // 特殊物品（計畫書 4）與擊退盾：本模組所有物品都可以在創造模式拿到
                com.provinggrounds.special.SpecialRegistry.ITEM.values().forEach(r -> out.accept(r.get()));
                out.accept(com.provinggrounds.special.SpecialRegistry.SHIELD_HELD.get());
            })
            .build());

    private PGBlocks() {}

    private static RegistryObject<Block> crate(String name, MapColor color, SoundType sound) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new CrateBlock(
            BlockBehaviour.Properties.of().mapColor(color).strength(2.5f).sound(sound)));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PGBlocks::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CRATE_LOW);
            event.accept(CRATE_MID);
            event.accept(CRATE_HIGH);
            event.accept(CRATE_AIRDROP);
            event.accept(ALTAR_ITEM);
            event.accept(STATS_BOARD_ITEM);
            event.accept(ELEVATOR_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BACKPACK_1);
            event.accept(BACKPACK_2);
            event.accept(BACKPACK_3);
        }
    }
}
