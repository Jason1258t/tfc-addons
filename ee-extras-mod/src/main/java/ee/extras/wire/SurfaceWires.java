package ee.extras.wire;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.simulation.WireType;
import ee.extras.EeExtras;
import ee.extras.cable.CableWires;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Декоративный провод (#205): провод по поверхностям блоков, перенесённый из Create: Power Grid 0.6.2, и
 * распределительная коробка. Регистрация, настройки, пакеты, события.
 */
public final class SurfaceWires {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, EeExtras.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SurfaceWireEntity>> WIRE_ENTITY = ENTITIES.register("surface_wire",
            () -> EntityType.Builder.<SurfaceWireEntity>of(SurfaceWireEntity::new, MobCategory.MISC)
                    .sized(0.125f, 0.125f)
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("surface_wire"));

    /** Невидимая жила: свойства — жилы двухжильного кабеля, катушка — сам провод (смотать её можно только случайно) */
    public static final DeferredHolder<WireType, WireType> DUPLEX_CORE = EeExtras.WIRES.register("surface_wire_core",
            () -> new WireType.Builder(CableWires.EMPTY_SEGMENT)
                    .resistance(CEEConfigs.server().resistanceValues.wireResistance::get)
                    .spoolItem(() -> SurfaceWires.DUPLEX_ITEM.get())
                    .maxInsulationVoltage(CEEConfigs.server().voltageValues.wireMaxVoltage::get)
                    .insulationResistance(330000.0)
                    .maxTemperature(() -> 3540.0)
                    .maxLength(() -> Integer.MAX_VALUE)
                    .build());

    public static final DeferredItem<SurfaceWireItem> DUPLEX_ITEM = EeExtras.ITEMS.register("surface_wire",
            () -> new SurfaceWireItem(new Item.Properties()));

    public static final DeferredBlock<JunctionBoxBlock> JUNCTION_BOX = EeExtras.BLOCKS.register("junction_box",
            () -> new JunctionBoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f, 3.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<Item> JUNCTION_BOX_ITEM = EeExtras.ITEMS.register("junction_box",
            () -> new ee.extras.TooltipBlockItem(JUNCTION_BOX.get(), new Item.Properties()));

    /**
     * Вид провода — то, что у Power Grid лежало в WireItemEntry: число жил, толщина (блоки), цвет, текстура, тип жилы
     * EE. Длина куска и расход — в WireConfig.
     */
    public record Spec(int conductors, float thickness, boolean colorable, int defaultColor, ResourceLocation texture,
                       Supplier<WireType> core) {
    }

    public static final Spec DUPLEX = new Spec(2, 0.09375f, true, 0x413B31, EeExtras.rl("textures/entity/surface_wire.png"),
            DUPLEX_CORE::get);

    public static Spec spec(Item item) {
        return item instanceof SurfaceWireItem w ? w.spec() : null;
    }

    public static void register(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, WireConfig.SPEC);
        modBus.addListener(WirePackets::register);
        WireEvents.register(NeoForge.EVENT_BUS);
    }

    private SurfaceWires() {
    }
}
