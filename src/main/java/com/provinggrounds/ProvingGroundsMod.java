package com.provinggrounds;

import com.provinggrounds.command.PGCommand;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.compat.AmmoCategories;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.events.ArmorEvents;
import com.provinggrounds.events.BREvents;
import com.provinggrounds.events.DownedEvents;
import com.provinggrounds.events.PlayerEvents;
import com.provinggrounds.events.WorldEvents;
import com.provinggrounds.modes.br.BRMovement;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.registry.PGBlocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ProvingGroundsMod.MOD_ID)
public class ProvingGroundsMod {

    public static final String MOD_ID = "provinggrounds";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ProvingGroundsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);

        // 方塊、物品、方塊實體（戰利品箱）
        PGBlocks.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PGConfig.SERVER_SPEC);
        modBus.addListener(AmmoCategories::onConfig); // 彈藥分類對照表

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new PlayerEvents());
        MinecraftForge.EVENT_BUS.register(new WorldEvents());
        MinecraftForge.EVENT_BUS.register(new BREvents());
        MinecraftForge.EVENT_BUS.register(new BRMovement());
        MinecraftForge.EVENT_BUS.register(new DownedEvents());
        MinecraftForge.EVENT_BUS.register(new ArmorEvents()); // 防彈
        MinecraftForge.EVENT_BUS.register(new com.provinggrounds.events.BulletHitEvents()); // 中彈不擊退

        LOGGER.info("Proving Grounds initializing...");
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PGNetwork::register);
        event.enqueueWork(GunCompat::init); // 槍械適配層：倒地時不能開槍
        LOGGER.info("Proving Grounds setup complete.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PGCommand.register(event.getDispatcher());
        LOGGER.info("Proving Grounds commands registered.");
    }
}
