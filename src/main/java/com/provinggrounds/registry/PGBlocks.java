package com.provinggrounds.registry;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.CrateBlock;
import com.provinggrounds.block.CrateBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
        }
    }
}
