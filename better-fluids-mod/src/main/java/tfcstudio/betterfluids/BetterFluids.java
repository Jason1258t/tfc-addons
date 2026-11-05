package tfcstudio.betterfluids;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(BetterFluids.ID)
public final class BetterFluids {
    public static final String ID = "better_fluids";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);

    static final Supplier<Block> TAP = BLOCKS.register("fluid_tap", () -> new FluidTapBlock(sectionProperties()));

    static final Supplier<FluidTankBlock> TANK = BLOCKS.register("iron_tank", () ->
        new IronTankBlock(BlockBehaviour.Properties.of().strength(3.0f, 6.0f)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    static final Supplier<FluidTankBlock> PLATE = BLOCKS.register("iron_tank_plate", () -> new IronTankSlabBlock(sectionProperties()));
    static final Supplier<FluidTankBlock> QUARTER = section("iron_tank_quarter", TankSection.QUARTER);
    static final Supplier<FluidTankBlock> BEVEL = section("iron_tank_bevel", TankSection.BEVEL);

    private static Supplier<FluidTankBlock> section(String name, TankSection section) {
        return BLOCKS.register(name, () -> new IronTankBlock(sectionProperties(), section));
    }

    private static BlockBehaviour.Properties sectionProperties() {
        return BlockBehaviour.Properties.of().strength(3.0f, 6.0f)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion();
    }

    static final Supplier<BlockEntityType<IronTankEntity>> TANK_ENTITY = ENTITIES.register("iron_tank", () ->
        BlockEntityType.Builder.of((pos, state) -> new IronTankEntity(tankEntityType(), pos, state), TANK.get(), PLATE.get(), QUARTER.get(), BEVEL.get()).build(null));

    private static BlockEntityType<IronTankEntity> tankEntityType() {
        return TANK_ENTITY.get();
    }

    public BetterFluids(IEventBus bus) {
        ITEMS.registerSimpleBlockItem("fluid_tap", TAP);
        ITEMS.register("iron_tank", () -> new IronTankItem(TANK.get(), new Item.Properties()));
        ITEMS.register("iron_tank_plate", () -> new IronTankItem(PLATE.get(), new Item.Properties()));
        ITEMS.register("iron_tank_quarter", () -> new IronTankItem(QUARTER.get(), new Item.Properties()));
        ITEMS.register("iron_tank_bevel", () -> new IronTankItem(BEVEL.get(), new Item.Properties()));
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TANK_ENTITY.get(),
            (tank, side) -> tank.fluidHandler());
    }
}
