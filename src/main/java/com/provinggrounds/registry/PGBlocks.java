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

    /** 數據看板（計畫書 B5）：只有 OP 或創造模式能擺放、破壞。 */
    public static final RegistryObject<Block> STATS_BOARD = BLOCKS.register("stats_board", () -> new com.provinggrounds.block.StatsBoardBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 3600000f).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> STATS_BOARD_ITEM = ITEMS.register("stats_board",
        () -> new BlockItem(STATS_BOARD.get(), new Item.Properties()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<com.provinggrounds.block.StatsBoardBlockEntity>> STATS_BOARD_BE =
        BLOCK_ENTITIES.register("stats_board", () -> BlockEntityType.Builder.of(com.provinggrounds.block.StatsBoardBlockEntity::new, STATS_BOARD.get()).build(null));

    /** 復活牌（只會以掉落物形式存在；撿起後轉成玩家身上的資料，不進背包）。 */
    public static final RegistryObject<Item> REVIVE_CARD = ITEMS.register("revive_card",
        () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 一～三級背包（計畫書 B3）。 */
    public static final RegistryObject<Item> BACKPACK_1 = ITEMS.register("backpack_1",
        () -> new com.provinggrounds.item.BackpackItem(1, new Item.Properties()));
    public static final RegistryObject<Item> BACKPACK_2 = ITEMS.register("backpack_2",
        () -> new com.provinggrounds.item.BackpackItem(2, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BACKPACK_3 = ITEMS.register("backpack_3",
        () -> new com.provinggrounds.item.BackpackItem(3, new Item.Properties().rarity(Rarity.EPIC)));

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
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BACKPACK_1);
            event.accept(BACKPACK_2);
            event.accept(BACKPACK_3);
        }
    }
}
