package stress.test;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlockEntity;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import ee.extras.EeExtras;
import ee.extras.cable.CableEnd;
import ee.extras.converter.CableConverterBlock;
import ee.extras.lantern.LanternBlock;
import ee.extras.lantern.LanternConversion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Паровая электростанция на помосте (высота 200) с нагрузкой:
 *  - котёл Create 3×3×5 (45 баков, предел 11 уровней) на 9 горелках всполоха; горелки кормятся пирогом всполоха,
 *    когда до конца горения остаётся ≤ 500 тиков (как раздатчик у игрока; счёт пирогов — в отчёте);
 *  - вода: два творческих бака Create → механические помпы вплотную к котлу; каждую помпу через шестерню крутит
 *    электродвигатель EE (256 об/мин) от творческой батареи EE на 600 В постоянного тока;
 *  - 9 паровых двигателей в три ряда, ряды сведены пятью шестернями (через одну — паразитные, направление то же);
 *  - генератор EE: ротор с 4 статорами, трёхфазный щёточный узел (контакты 1–3 — фазы, 0 — нейтраль).
 *    Два ротора на холостом ходу берут 74 496 SU — больше трёх двигателей (49 152), поэтому ротор один;
 *  - 15 электропотребителей: 10 нагревателей EE между фазами и 5 ламп EE между фазой и нейтралью;
 *  - 6 электродвигателей EE между фазами, на каждом нагрузка Create: жернов, вентилятор, бур;
 *  - фаза 1 и нейтраль → соединитель EE → двухжильный кабель катушкой (кликами игрока-заглушки): 6 фонарей IE,
 *    2 прожектора IE, 3 кабельных преобразователя EE Extras — на конденсаторах IE и внешнем нагревателе IE.
 * Отчёт — /stress report и строка «[STRESS] станция …» в журнале.
 */
final class Plant {
    static final int Y = 200;
    static final List<Site> SITES = new ArrayList<>();

    record Site(ServerLevel level, BlockPos o) {
        BlockPos tank() { return o.above(); }
        BlockPos brushes() { return o.offset(5, 1, -3); }
        BlockPos rotor() { return o.offset(4, 1, -3); }
        /** Ряды по три: высоты 1, 3, 5 */
        BlockPos engine(int i) { return o.offset(i % 3, 1 + 2 * (i / 3), -1); }
        BlockPos burner(int i) { return o.offset(i % 3, 0, i / 3); }
        /** Помпа i (z 0 и 2) вплотную к котлу с запада; за ней творческий бак, над ней шестерня, над баком двигатель */
        BlockPos pump(int i) { return o.offset(-1, 2, i * 2); }
        BlockPos pumpMotor(int i) { return o.offset(-2, 3, i * 2); }
        BlockPos battery() { return o.offset(-4, 0, 1); }
        BlockPos consumer(int i) { return o.offset(8 + i * 2, 0, -8); }
        BlockPos motor(int i) { return o.offset(8 + i * 3, 0, -13); }
        BlockPos meter() { return o.offset(4, 0, -8); }
        BlockPos term() { return o.offset(4, 0, 7); }
        BlockPos lantern(int i) { return o.offset(8 + i * 4, 0, 7); }
        BlockPos flood(int i) { return o.offset(32 + i * 4, 0, 7); }
        BlockPos conv(int i) { return o.offset(40 + i * 4, 1, 7); }
        BlockPos lineMeter() { return o.offset(40, 0, 10); }
    }

    static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState with(BlockState s, String name, Comparable v) {
        for (Property p : s.getProperties()) if (p.getName().equals(name)) return s.setValue(p, v);
        return s;
    }

    /** Провод EE медный между контактами, как катушка в руках (тот же путь, что devtest EE Extras) */
    static void wire(ServerLevel level, BlockPos a, int na, BlockPos b, int nb) {
        for (BlockPos p : new BlockPos[] {a, b}) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof ElectricalDeviceBlock<?> eb) eb.ensureNodesExist(level, p, st);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(na, a), new InWorldNode(nb, b), CEEWireTypes.COPPER.get());
    }

    static BlockState stator(Direction facing) {
        return CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, facing).setValue(StatorBlock.ROLL, true);
    }

    static int build(CommandSourceStack src, int copies) {
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        // Начало на границе чанка, в 16 блоках к югу от игрока
        BlockPos base = new BlockPos((at.getX() >> 4) << 4, Y, ((at.getZ() >> 4) << 4) + 32);
        for (int c = 0; c < copies; c++) {
            Site s = new Site(level, base.offset(0, 0, c * 32));
            buildOne(s);
            SITES.add(s);
        }
        // Игрок встаёт посреди первой станции (её чанки грузятся и тикают, как у игрока на базе)
        if (src.getPlayer() != null) src.getPlayer().connection.teleport(base.getX() + 24.5, Y, base.getZ() + 0.5, 0f, 30f);
        StressTest.LOG.info("[STRESS] станций: {} у {}", copies, base.toShortString());
        src.sendSuccess(() -> Component.literal("станций: " + copies + " у " + base.toShortString()), false);
        return copies;
    }

    static void buildOne(Site s) {
        ServerLevel level = s.level();
        BlockPos o = s.o();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -6; x <= 52; x++)
            for (int z = -16; z <= 12; z++) {
                place(level, o.offset(x, -1, z), floor);
                for (int y = 0; y < 8; y++) place(level, o.offset(x, y, z), air);
            }
        // Котёл: баки, под ними горелки
        for (int y = 1; y <= 5; y++)
            for (int x = 0; x < 3; x++)
                for (int z = 0; z < 3; z++) place(level, o.offset(x, y, z), AllBlocks.FLUID_TANK.getDefaultState());
        for (int i = 0; i < 9; i++) {
            place(level, s.burner(i), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            feed(level, s.burner(i));
        }
        // Вода: творческий бак → помпа → котёл; помпа — как малая шестерня, её крутит шестерня сверху
        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockState pumpMotor = with(block("electroenergetics:electric_motor"), "facing", Direction.EAST);
        place(level, s.battery(), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        for (int i = 0; i < 2; i++) {
            BlockPos p = s.pump(i);
            place(level, p.west(), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
            if (level.getBlockEntity(p.west()) instanceof CreativeFluidTankBlockEntity t
                    && t.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank ct)
                ct.setContainedFluid(new FluidStack(Fluids.WATER, 1000));
            place(level, p, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
            place(level, p.above(), cog);
            place(level, s.pumpMotor(i), pumpMotor);
            wire(level, s.battery(), 1, s.pumpMotor(i), 0);
            wire(level, s.battery(), 0, s.pumpMotor(i), 1);
        }
        // Валы рядов: x 0..2 (у двигателей станут приводными), x 3 — шестерни на высотах 1–5; ротор x 4, узел x 5
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        for (int x = 0; x < 3; x++)
            for (int y = 1; y <= 5; y += 2) place(level, o.offset(x, y, -3), shaft);
        for (int y = 1; y <= 5; y++) place(level, o.offset(3, y, -3), cog);
        BlockPos r = s.rotor();
        place(level, r, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        place(level, r.above(), stator(Direction.DOWN));
        place(level, r.below(), stator(Direction.UP));
        place(level, r.north(), stator(Direction.SOUTH));
        place(level, r.south(), stator(Direction.NORTH));
        place(level, s.brushes(), CEEBlocks.THREE_PHASE_ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        BlockState engine = AllBlocks.STEAM_ENGINE.getDefaultState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (int i = 0; i < 9; i++) place(level, s.engine(i), engine);

        BlockPos br = s.brushes();
        // 15 электропотребителей: 10 нагревателей между фазами, 5 ламп между фазой и нейтралью
        for (int i = 0; i < 15; i++) {
            BlockPos p = s.consumer(i);
            place(level, p, i < 10 ? CEEBlocks.RESISTIVE_HEATER.getDefaultState() : CEEBlocks.BULB.getDefaultState());
            wire(level, br, 1 + i % 3, p, 0);
            wire(level, br, i < 10 ? 1 + (i + 1) % 3 : 0, p, 1);
        }
        place(level, s.meter(), CEEBlocks.VOLTMETER.getDefaultState());
        wire(level, br, 1, s.meter(), 0);
        wire(level, br, 2, s.meter(), 1);
        // 6 двигателей лицом вверх, над ними нагрузка Create
        BlockState motor = with(block("electroenergetics:electric_motor"), "facing", Direction.UP);
        for (int i = 0; i < 6; i++) {
            BlockPos m = s.motor(i);
            place(level, m, motor);
            switch (i % 3) {
                case 0 -> place(level, m.above(), AllBlocks.MILLSTONE.getDefaultState());
                case 1 -> place(level, m.above(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                default -> {
                    place(level, m.above(), AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                    place(level, m.above(2), Blocks.OBSIDIAN.defaultBlockState());
                }
            }
            wire(level, br, 1 + i % 3, m, 0);
            wire(level, br, 1 + (i + 1) % 3, m, 1);
        }
        // Линия освещения и IE: соединитель EE (фаза 1 и нейтраль), фонари, прожекторы, преобразователи
        place(level, s.term(), CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState()
                .setValue(BundledWireTerminationBlock.FACING, Direction.UP)
                .setValue(BundledWireTerminationBlock.ROLL, false)
                .setValue(BundledWireTerminationBlock.FLIP, false));
        CableEnd a = CableEnd.of(s.term(), level.getBlockState(s.term()));
        wire(level, br, 1, s.term(), a.conductor(1).id());
        wire(level, br, 0, s.term(), a.conductor(2).id());
        BlockState lantern = with(BuiltInRegistries.BLOCK.get(LanternConversion.IE_LANTERN).defaultBlockState(), "facing", Direction.DOWN);
        for (int i = 0; i < 6; i++) place(level, s.lantern(i), lantern);
        for (int i = 0; i < 2; i++) {
            place(level, s.flood(i), block("immersiveengineering:floodlight").setValue(IEProperties.FACING_ALL, Direction.UP));
            if (level.getBlockEntity(s.flood(i)) instanceof FloodlightBlockEntity be) be.redstoneControlInverted = true;
        }
        String[] ie = {"immersiveengineering:capacitor_lv", "immersiveengineering:furnace_heater", "immersiveengineering:capacitor_lv"};
        for (int i = 0; i < 3; i++) {
            place(level, s.conv(i).below(), block(ie[i]));
            place(level, s.conv(i), EeExtras.CABLE_CONVERTER.get().defaultBlockState().setValue(CableConverterBlock.FACING, Direction.UP));
        }
        // Устройства EE появляются тиком позже: кабель — через секунду
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + 20, () -> lay(s)));
    }

    static void click(ServerLevel level, FakePlayer player, BlockPos pos) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    static int cakes, debug;
    static java.lang.reflect.Method tryFuel;

    /** Пирог всполоха в горелку, если до конца горения ≤ 500 тиков (горелка сама решает, принять ли) */
    static void feed(ServerLevel level, BlockPos pos) {
        var raw = level.getBlockEntity(pos);
        if (!(raw instanceof BlazeBurnerBlockEntity be)) {
            if (debug++ < 3) StressTest.LOG.info("[STRESS] горелка {}: {}", pos.toShortString(), raw == null ? "нет BE" : raw.getClass().getName());
            return;
        }
        if (be.getRemainingBurnTime() > 500) return;
        try {
            if (tryFuel == null) {
                tryFuel = BlazeBurnerBlockEntity.class.getDeclaredMethod("tryUpdateFuel", ItemStack.class, boolean.class, boolean.class);
                tryFuel.setAccessible(true);
            }
            boolean ok = (boolean) tryFuel.invoke(be, AllItems.BLAZE_CAKE.asStack(), false, false);
            if (ok) cakes++;
            else if (debug++ < 3) StressTest.LOG.info("[STRESS] горелка {} не взяла пирог: топливо {}, осталось {}, творческая {}", pos.toShortString(),
                    be.getActiveFuel(), be.getRemainingBurnTime(), be.isCreative());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Батарея помп 600 В и 256 об/мин у их двигателей — когда устройство EE уже есть */
    static void pumps(Site s) {
        ServerLevel level = s.level();
        CreativeBatteryDevice b = DevicesSavedData.load(level).getDevice(s.battery(), CreativeBatteryDevice.class);
        if (b != null) b.voltage = 600;
        for (int i = 0; i < 2; i++)
            if (level.getBlockEntity(s.pumpMotor(i)) instanceof KineticBlockEntity m)
                for (var beh : m.getAllBehaviours())
                    if (beh instanceof ScrollValueBehaviour v) v.setValue(256);
        StressTest.LOG.info("[STRESS] батарея помп: {}", b == null ? "нет устройства" : b.voltage + " В");
    }

    static void lay(Site s) {
        ServerLevel level = s.level();
        pumps(s);
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 64));
        List<BlockPos> chain = new ArrayList<>();
        chain.add(s.term());
        for (int i = 0; i < 6; i++) chain.addAll(List.of(s.lantern(i), s.lantern(i)));
        for (int i = 0; i < 2; i++) chain.addAll(List.of(s.flood(i), s.flood(i)));
        for (int i = 0; i < 3; i++) chain.addAll(List.of(s.conv(i), s.conv(i)));
        chain.remove(chain.size() - 1);
        for (BlockPos p : chain) click(level, player, p);
        place(level, s.lineMeter(), CEEBlocks.VOLTMETER.getDefaultState());
        CableEnd last = CableEnd.of(level, s.conv(2), level.getBlockState(s.conv(2)));
        if (last != null) {
            wire(level, s.conv(2), last.conductor(1).id(), s.lineMeter(), 0);
            wire(level, s.conv(2), last.conductor(2).id(), s.lineMeter(), 1);
        }
        StressTest.LOG.info("[STRESS] кабель освещения проложен, катушек осталось {}", player.getItemInHand(InteractionHand.MAIN_HAND).getCount());
    }

    /** Подкормка горелок, раз в минуту — отчёт первой станции */
    static void tick(MinecraftServer server) {
        for (Site s : SITES)
            for (int i = 0; i < 9; i++) feed(s.level(), s.burner(i));
        if (!SITES.isEmpty() && server.getTickCount() % 1200 == 0) StressTest.LOG.info("[STRESS] станция: {}", describe(SITES.get(0)));
    }

    static String device(DevicesSavedData d, BlockPos pos) {
        SimulatedDevice dev = d.getDevice(pos, SimulatedDevice.class);
        if (dev == null) return "нет";
        CompoundTag t = new CompoundTag();
        dev.write(t);
        return t.toString();
    }

    static String describe(Site s) {
        ServerLevel level = s.level();
        StringBuilder out = new StringBuilder();
        if (level.getBlockEntity(s.tank()) instanceof FluidTankBlockEntity t && t.getControllerBE() != null) {
            var b = t.getControllerBE().boiler;
            out.append(String.format("котёл: жар %d, вода %.0f мБ/т, двигателей %d, баков %d, пирогов скормлено %d",
                    b.activeHeat, b.waterSupply, b.attachedEngines, t.getControllerBE().getTotalTankSize(), cakes));
        } else out.append("котла нет");
        if (level.getBlockEntity(s.rotor()) instanceof KineticBlockEntity r) {
            out.append(String.format("; вал %.1f об/мин", r.getSpeed()));
            var net = r.getOrCreateNetwork();
            if (net != null) out.append(String.format(", нагрузка %.0f из %.0f SU", net.calculateStress(), net.calculateCapacity()));
        }
        DevicesSavedData d = DevicesSavedData.load(level);
        out.append("; узел ").append(device(d, s.brushes()));
        out.append("; вольтметр фаз 1–2 ").append(device(d, s.meter()));
        int motors = 0;
        StringBuilder rpm = new StringBuilder();
        for (int i = 0; i < 6; i++)
            if (level.getBlockEntity(s.motor(i)) instanceof KineticBlockEntity m) {
                if (m.getSpeed() != 0) motors++;
                rpm.append(' ').append((int) m.getSpeed());
            }
        out.append("; помпы об/мин");
        for (int i = 0; i < 2; i++)
            out.append(' ').append(level.getBlockEntity(s.pump(i)) instanceof KineticBlockEntity pk ? (int) pk.getSpeed() : -1);
        out.append("; двигателей крутится ").append(motors).append(" из 6 (об/мин").append(rpm).append(')');
        int bulbs = 0, lit = 0, floods = 0;
        String bulbId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(s.consumer(10)).getBlock()).getPath();
        for (int i = 10; i < 15; i++) if (level.getBlockState(s.consumer(i)).getLightEmission(level, s.consumer(i)) > 0) bulbs++;
        for (int i = 0; i < 6; i++) {
            BlockState st = level.getBlockState(s.lantern(i));
            if (st.getBlock() instanceof LanternBlock && st.getValue(LanternBlock.LIT)) lit++;
        }
        for (int i = 0; i < 2; i++)
            if (level.getBlockState(s.flood(i)).getLightEmission(level, s.flood(i)) > 0) floods++;
        out.append(String.format("; ламп горит %d из 5 (%s), фонарей IE %d из 6, прожекторов %d из 2", bulbs, bulbId, lit, floods));
        out.append("; ФЭ IE:");
        for (int i = 0; i < 3; i++) {
            IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, s.conv(i).below(), null);
            out.append(' ').append(e == null ? "нет" : e.getEnergyStored() + "/" + e.getMaxEnergyStored());
        }
        out.append("; вольтметр конца линии ").append(device(d, s.lineMeter()));
        return out.toString();
    }

    static int report(CommandSourceStack src) {
        if (SITES.isEmpty()) {
            src.sendFailure(Component.literal("станций нет: /stress plant"));
            return 0;
        }
        for (Site s : SITES) {
            String msg = describe(s);
            StressTest.LOG.info("[STRESS] станция у {}: {}", s.o().toShortString(), msg);
            src.sendSuccess(() -> Component.literal(msg), false);
        }
        return SITES.size();
    }

    private Plant() {
    }
}
