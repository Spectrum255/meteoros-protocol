package com.spectrum255.meteorosprotocol;

import com.mojang.logging.LogUtils;
import java.util.List;
import org.slf4j.Logger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import com.spectrum255.meteorosprotocol.menu.FilterMenu;
import com.spectrum255.meteorosprotocol.menu.InfiniteParticleCellMenu;
import com.spectrum255.meteorosprotocol.item.CanisterItem;
import com.spectrum255.meteorosprotocol.item.FilterItem;
import com.spectrum255.meteorosprotocol.item.ParticleDetectorItem;
import com.spectrum255.meteorosprotocol.item.WrenchItem;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.CreativeEnergyBlock;
import com.spectrum255.meteorosprotocol.machine.CreativeEnergyBlockEntity;
import com.spectrum255.meteorosprotocol.machine.CreativeParticleBlock;
import com.spectrum255.meteorosprotocol.machine.CreativeParticleBlockEntity;
import com.spectrum255.meteorosprotocol.machine.IntakeBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.machine.ParticlePipeBlock;
import com.spectrum255.meteorosprotocol.particle.ParticleCapabilities;
import com.spectrum255.meteorosprotocol.particle.Phase;
import com.spectrum255.meteorosprotocol.particle.PhaseRegistry;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

@Mod(MeteorosProtocol.MODID)
@EventBusSubscriber(modid="meteorosprotocol", bus=EventBusSubscriber.Bus.MOD)
public class MeteorosProtocol {
    public static final String MODID="meteorosprotocol";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MODID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MODID);
    public static final DeferredHolder<MenuType<?>,MenuType<IntakeMenu>> INTAKE_MENU=MENUS.register("particle_intake",()->IMenuTypeExtension.create(IntakeMenu::new));
    public static final DeferredHolder<MenuType<?>,MenuType<FilterMenu>> FILTER_MENU=MENUS.register("filter_cartridge",()->IMenuTypeExtension.create(FilterMenu::new));
    public static final DeferredHolder<MenuType<?>,MenuType<InfiniteParticleCellMenu>> INFINITE_PARTICLE_CELL_MENU=MENUS.register("infinite_particle_cell",()->IMenuTypeExtension.create(InfiniteParticleCellMenu::new));
    private static final BlockBehaviour.Properties MACHINE=BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0f).sound(SoundType.METAL);
    public static final DeferredBlock<Block> MACHINE_FRAME=BLOCKS.register("machine_frame",()->new Block(MACHINE));
    public static final DeferredBlock<Block> THERMAL_GENERATOR=BLOCKS.register("thermal_generator",()->new MachineBlock(MACHINE));
    public static final DeferredBlock<Block> PARTICLE_INTAKE=BLOCKS.register("particle_intake",()->new IntakeBlock(MACHINE));
    public static final DeferredBlock<Block> PHASE_SEPARATOR=BLOCKS.register("phase_separator",()->new MachineBlock(MACHINE));
    public static final DeferredBlock<Block> WEATHER_STATION=BLOCKS.register("weather_station",()->new MachineBlock(MACHINE));
    public static final DeferredBlock<Block> WEATHER_TANK=BLOCKS.register("weather_tank",()->new MachineBlock(MACHINE));
    public static final DeferredBlock<Block> PARTICLE_PIPE=BLOCKS.register("particle_pipe",()->new ParticlePipeBlock(MACHINE));
    public static final DeferredBlock<Block> PORCELAIN_SHELL=BLOCKS.register("porcelain_shell",()->new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5f).sound(SoundType.STONE)));
    private static final BlockBehaviour.Properties TEST_BLOCK=BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0f).sound(SoundType.METAL);
    public static final DeferredBlock<Block> INFINITE_ENERGY=BLOCKS.register("infinite_energy",()->new CreativeEnergyBlock(TEST_BLOCK));
    public static final DeferredBlock<Block> INFINITE_PARTICLE_CELL=BLOCKS.register("infinite_particle_cell",()->new CreativeParticleBlock(TEST_BLOCK));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MODID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MachineBlockEntity>> MACHINE_BLOCK_ENTITY=BLOCK_ENTITY_TYPES.register("machine",()->BlockEntityType.Builder.of(MachineBlockEntity::new, THERMAL_GENERATOR.get(), PARTICLE_INTAKE.get(), PHASE_SEPARATOR.get(), WEATHER_STATION.get(), WEATHER_TANK.get(), PARTICLE_PIPE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CreativeEnergyBlockEntity>> CREATIVE_ENERGY_BLOCK_ENTITY=BLOCK_ENTITY_TYPES.register("creative_energy",()->BlockEntityType.Builder.of(CreativeEnergyBlockEntity::new, INFINITE_ENERGY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CreativeParticleBlockEntity>> CREATIVE_PARTICLE_BLOCK_ENTITY=BLOCK_ENTITY_TYPES.register("creative_particle",()->BlockEntityType.Builder.of(CreativeParticleBlockEntity::new, INFINITE_PARTICLE_CELL.get()).build(null));
    public static final DeferredItem<BlockItem> MACHINE_FRAME_ITEM=ITEMS.registerSimpleBlockItem("machine_frame",MACHINE_FRAME);
    public static final DeferredItem<BlockItem> THERMAL_GENERATOR_ITEM=ITEMS.registerSimpleBlockItem("thermal_generator",THERMAL_GENERATOR);
    public static final DeferredItem<BlockItem> PARTICLE_INTAKE_ITEM=ITEMS.registerSimpleBlockItem("particle_intake",PARTICLE_INTAKE);
    public static final DeferredItem<BlockItem> PHASE_SEPARATOR_ITEM=ITEMS.registerSimpleBlockItem("phase_separator",PHASE_SEPARATOR);
    public static final DeferredItem<BlockItem> WEATHER_STATION_ITEM=ITEMS.registerSimpleBlockItem("weather_station",WEATHER_STATION);
    public static final DeferredItem<BlockItem> WEATHER_TANK_ITEM=ITEMS.registerSimpleBlockItem("weather_tank",WEATHER_TANK);
    public static final DeferredItem<BlockItem> PARTICLE_PIPE_ITEM=ITEMS.registerSimpleBlockItem("particle_pipe",PARTICLE_PIPE);
    public static final DeferredItem<BlockItem> PORCELAIN_SHELL_BLOCK_ITEM=ITEMS.registerSimpleBlockItem("porcelain_shell",PORCELAIN_SHELL);
    public static final DeferredItem<BlockItem> INFINITE_ENERGY_ITEM=ITEMS.registerSimpleBlockItem("infinite_energy",INFINITE_ENERGY);
    public static final DeferredItem<BlockItem> INFINITE_PARTICLE_CELL_ITEM=ITEMS.registerSimpleBlockItem("infinite_particle_cell",INFINITE_PARTICLE_CELL);
    public static final DeferredItem<Item> WRENCH=ITEMS.register("wrench",()->new WrenchItem(new Item.Properties()));
    public static final DeferredItem<Item> CANISTER=ITEMS.register("canister",()->new CanisterItem(new Item.Properties()));
    public static final DeferredItem<Item> FILTER_CARTRIDGE=ITEMS.register("filter_cartridge",()->new FilterItem(new Item.Properties()));
    public static final DeferredItem<Item> PARTICLE_NOZZLE=ITEMS.registerSimpleItem("particle_nozzle");
    public static final DeferredItem<Item> ENERGY_COIL=ITEMS.registerSimpleItem("energy_coil");
    public static final DeferredItem<Item> PORCELAIN_SHELL_ITEM=ITEMS.registerSimpleItem("porcelain_shell_item");
    public static final DeferredItem<Item> INFINITE_PARTICLE_CANISTER=ITEMS.register("infinite_particle_canister",()->new InfiniteParticleCanisterItem(new Item.Properties()));
    public static final DeferredItem<Item> PARTICLE_DETECTOR=ITEMS.register("particle_detector",()->new ParticleDetectorItem(new Item.Properties()));
    public static final List<DeferredBlock<Block>> BLOCK_LIST=List.of(MACHINE_FRAME,THERMAL_GENERATOR,PARTICLE_INTAKE,PHASE_SEPARATOR,WEATHER_STATION,WEATHER_TANK,PARTICLE_PIPE,PORCELAIN_SHELL,INFINITE_ENERGY,INFINITE_PARTICLE_CELL);
    public static final List<DeferredItem<Item>> ITEM_LIST=List.of(WRENCH,CANISTER,FILTER_CARTRIDGE,PARTICLE_NOZZLE,ENERGY_COIL,PORCELAIN_SHELL_ITEM,INFINITE_PARTICLE_CANISTER,PARTICLE_DETECTOR);
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEM_LIST=List.of(MACHINE_FRAME_ITEM,THERMAL_GENERATOR_ITEM,PARTICLE_INTAKE_ITEM,PHASE_SEPARATOR_ITEM,WEATHER_STATION_ITEM,WEATHER_TANK_ITEM,PARTICLE_PIPE_ITEM,PORCELAIN_SHELL_BLOCK_ITEM,INFINITE_ENERGY_ITEM,INFINITE_PARTICLE_CELL_ITEM);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> MAIN_TAB=TABS.register("main",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.meteorosprotocol.main")).icon(()->new ItemStack(WEATHER_STATION_ITEM.get())).displayItems((p,o)->{for(DeferredItem<BlockItem>b:BLOCK_ITEM_LIST)o.accept(b.get());for(DeferredItem<Item>i:ITEM_LIST){if(i!=INFINITE_PARTICLE_CANISTER)o.accept(i.get());}o.accept(INFINITE_PARTICLE_CANISTER.get());for(Phase phase:Phase.all())o.accept(InfiniteParticleCanisterItem.createFilled(phase));}).build());
    public MeteorosProtocol(IEventBus bus,ModContainer container){PhaseRegistry.PHASES.register(bus);BLOCKS.register(bus);BLOCK_ENTITY_TYPES.register(bus);ITEMS.register(bus);TABS.register(bus);MENUS.register(bus);container.registerConfig(ModConfig.Type.COMMON,Config.SPEC);}

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        // Registries are frozen by the time setup runs; this fixes the phase
        // indices once instead of letting the first lazy Phase.all() call race
        // the register events.
        event.enqueueWork(PhaseRegistry::bootstrap);
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ParticleCapabilities.MIXED_STORAGE, MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine.mixedStorageForSide(side));
        event.registerBlockEntity(ParticleCapabilities.PHASE_STORAGE, MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine.phaseStorage());
        event.registerBlockEntity(ParticleCapabilities.MIXED_STORAGE, CREATIVE_PARTICLE_BLOCK_ENTITY.get(), (source, side) -> source.storageForSide(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CREATIVE_ENERGY_BLOCK_ENTITY.get(), (source, side) -> source);
    }
}
