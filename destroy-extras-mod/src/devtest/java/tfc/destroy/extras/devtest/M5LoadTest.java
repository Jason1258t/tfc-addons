package tfc.destroy.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStatus;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;

/** Длительный прогон 8/32 production-установок с Create-трубами и насосами, EE и сохранением между частями. */
final class M5LoadTest {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DestroyExtrasTest.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DestroyExtrasTest.MOD_ID);
    private static final DeferredBlock<TestSinkBlock> SINK_BLOCK = BLOCKS.register("load_sink",
        () -> new TestSinkBlock(Block.Properties.of()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestSinkBlockEntity>> SINK_BE =
        BLOCK_ENTITIES.register("load_sink", () -> BlockEntityType.Builder
            .of(TestSinkBlockEntity::new, SINK_BLOCK.get()).build(null));

    private static final List<BlockPos> MACHINES = new ArrayList<>();
    private static final Map<BlockPos, ElectrolyzerStatus> STATUSES = new HashMap<>();
    private static final List<Long> SAMPLES = new ArrayList<>();
    private static int count;
    private static boolean active;
    private static long tickStarted;
    private static long ticks;
    private static long wallNanos;
    private static long resumedAt;
    private static long syncPackets;
    private static long syncBytes;
    private static long membraneReplacements;
    private static long sourceRefills;

    private M5LoadTest() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(M5LoadTest::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SINK_BE.get(), (be, side) -> be.tank);
    }

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m5load")
                .then(Commands.literal("start").then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("resume").then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(ctx -> io(ctx, IntegerArgumentType.getInteger(ctx, "count"), true))))
                .then(Commands.literal("report").then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(ctx -> io(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))))));
    }

    private static int io(CommandContext<CommandSourceStack> ctx, int requested, boolean resume) {
        try {
            return resume ? resume(ctx, requested) : report(ctx, requested);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось сохранить метрики M5", error);
        }
    }

    static void tickPre(ServerTickEvent.Pre event) {
        if (active) tickStarted = System.nanoTime();
    }

    static void tickPost(ServerTickEvent.Post event) {
        if (!active) return;
        long now = System.nanoTime();
        SAMPLES.add(now - tickStarted);
        ticks++;
        ServerLevel level = event.getServer().overworld();
        for (BlockPos pos : MACHINES) {
            MembraneElectrolyzerBlockEntity process = process(level, pos);
            TestSourceBlockEntity source = source(level, pos);
            if (source.tank.getFluidAmount() < 2000) {
                source.tank.setFluid(new FluidStack(TFCFluids.SALT_WATER.getSource(), 8000));
                sourceRefills++;
            }
            if (process.remainingMembraneOperations() < 4) {
                process.membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
                membraneReplacements++;
            }
            drain(level, pos, Direction.NORTH, process, 0);
            drain(level, pos, Direction.SOUTH, process, 1);
            drain(level, pos, Direction.EAST, process, 2);
            ElectrolyzerStatus previous = STATUSES.put(pos, process.status());
            if (previous != null && previous != process.status()) {
                syncPackets++;
                syncBytes += nbtSize(process.getUpdateTag(level.registryAccess()));
            }
        }
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int requested) {
        ServerLevel level = ctx.getSource().getLevel();
        reset(requested);
        for (int i = 0; i < requested; i++) build(level, position(i));
        active = true;
        resumedAt = System.nanoTime();
        ctx.getSource().sendSuccess(() -> Component.literal("M5 LOAD START machines=" + requested
            + " realCreateInputPumps=" + requested + " eeCircuits=" + requested), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx, int requested) throws IOException {
        loadState(ctx.getSource().getLevel(), requested);
        long savedBatches = totalBatches(ctx.getSource().getLevel());
        active = true;
        resumedAt = System.nanoTime();
        ctx.getSource().sendSuccess(() -> Component.literal("M5 LOAD RESUME machines=" + requested
            + " persistedBatches=" + savedBatches + " priorTicks=" + ticks), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int report(CommandContext<CommandSourceStack> ctx, int requested) throws IOException {
        ServerLevel level = ctx.getSource().getLevel();
        active = false;
        wallNanos += System.nanoTime() - resumedAt;
        resumedAt = 0;
        int formed = 0;
        int powered = 0;
        int minBatches = Integer.MAX_VALUE;
        int maxBatches = 0;
        double minVoltage = Double.MAX_VALUE;
        double maxCurrent = 0;
        for (BlockPos pos : MACHINES) {
            MembraneElectrolyzerBlockEntity process = process(level, pos);
            if (process.formed() && process.pumpRpm() >= 16) formed++;
            ProcessPowerDevice device = process.device();
            if (device != null && device.hasWorkingPower()) powered++;
            if (device != null) {
                minVoltage = Math.min(minVoltage, device.measuredVoltage());
                maxCurrent = Math.max(maxCurrent, device.measuredCurrent());
            }
            minBatches = Math.min(minBatches, process.batches);
            maxBatches = Math.max(maxBatches, process.batches);
        }
        saveState(level);
        List<Long> sorted = new ArrayList<>(SAMPLES);
        sorted.sort(Comparator.naturalOrder());
        double seconds = wallNanos / 1_000_000_000.0;
        double p50 = percentile(sorted, 0.50) / 1_000_000.0;
        double p95 = percentile(sorted, 0.95) / 1_000_000.0;
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        int batches = totalBatches(level);
        boolean pass = MACHINES.size() == requested && formed == requested && powered == requested
            && minBatches > 0 && ticks > 0;
        String result = String.format(Locale.ROOT,
            "DESTROYEXTRATEST %s M5 LOAD machines=%d controllers=%d formed=%d powered=%d seconds=%.1f ticks=%d TPS=%.3f MSPT_mean=%.3f MSPT_p50=%.3f MSPT_p95=%.3f batches=%d minBatches=%d maxBatches=%d syncPackets=%d syncNbtBytes=%d syncAvgBytes=%.1f minVoltage=%.3f maxCurrent=%.3f membraneReplacements=%d sourceRefills=%d",
            pass ? "PASS" : "FAIL", requested, MACHINES.size(), formed, powered, seconds, ticks,
            ticks / seconds, mean, p50, p95, batches, minBatches, maxBatches, syncPackets, syncBytes,
            syncPackets == 0 ? 0 : (double) syncBytes / syncPackets, minVoltage, maxCurrent,
            membraneReplacements, sourceRefills);
        ctx.getSource().sendSystemMessage(Component.literal(result));
        return pass ? Command.SINGLE_SUCCESS : 0;
    }

    private static void build(ServerLevel level, BlockPos controller) {
        DestroyExtrasTest.buildIndustrial(level, controller);
        BlockPos sourcePos = controller.west(2);
        BlockPos pumpPos = controller.west();
        BlockPos cogPos = pumpPos.north();
        BlockPos motorPos = cogPos.west();
        level.setBlock(sourcePos, DestroyExtrasTest.SOURCE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pumpPos, AllBlocks.MECHANICAL_PUMP.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(cogPos, AllBlocks.COGWHEEL.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_ALL);
        if (level.getBlockEntity(motorPos) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
        source(level, controller).tank.setFluid(new FluidStack(TFCFluids.SALT_WATER.getSource(), 8000));
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST}) {
            level.setBlock(controller.relative(direction, 2), SINK_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos batteryPos = controller.relative(Direction.SOUTH, 3);
        level.setBlock(batteryPos, CEEBlocks.CREATIVE_BATTERY.getDefaultState(), Block.UPDATE_ALL);
        wire(level, batteryPos, 0, controller, ProcessPowerDevice.PLUS);
        wire(level, batteryPos, 1, controller, ProcessPowerDevice.MINUS);
        CreativeBatteryDevice battery = DevicesSavedData.load(level).getDevice(batteryPos, CreativeBatteryDevice.class);
        if (battery == null) throw new IllegalStateException("EE-батарея нагрузочного стенда не создана");
        battery.voltage = 300;
        battery.acFrequency = 0;
        DevicesSavedData.load(level).setDirty();
        process(level, controller).membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
    }

    private static void drain(ServerLevel level, BlockPos controller, Direction direction,
        MembraneElectrolyzerBlockEntity process, int output) {
        if (level.getBlockEntity(controller.relative(direction, 2)) instanceof TestSinkBlockEntity sink) {
            FluidUtil.tryFluidTransfer(sink.tank, process.outputs[output], 4000, true);
        }
    }

    private static BlockPos position(int index) {
        return new BlockPos(100 + (index % 8) * 10, -60, 10 + (index / 8) * 10);
    }

    private static MembraneElectrolyzerBlockEntity process(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MembraneElectrolyzerBlockEntity process) return process;
        throw new IllegalStateException("Нет электролизёра нагрузочного стенда: " + pos);
    }

    private static TestSourceBlockEntity source(ServerLevel level, BlockPos controller) {
        if (level.getBlockEntity(controller.west(2)) instanceof TestSourceBlockEntity source) return source;
        throw new IllegalStateException("Нет исходного бака нагрузочного стенда: " + controller);
    }

    private static int totalBatches(ServerLevel level) {
        int result = 0;
        for (BlockPos pos : MACHINES) result += process(level, pos).batches;
        return result;
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b),
            CEEWireTypes.COPPER.get());
    }

    private static int nbtSize(CompoundTag tag) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return bytes.size();
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось измерить update tag", error);
        }
    }

    private static long percentile(List<Long> sorted, double fraction) {
        if (sorted.isEmpty()) return 0;
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(sorted.size() * fraction)));
    }

    private static Path statePath(ServerLevel level, String suffix) {
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("m5load-" + count + suffix);
    }

    private static void saveState(ServerLevel level) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("ticks", Long.toString(ticks));
        properties.setProperty("wallNanos", Long.toString(wallNanos));
        properties.setProperty("syncPackets", Long.toString(syncPackets));
        properties.setProperty("syncBytes", Long.toString(syncBytes));
        properties.setProperty("membraneReplacements", Long.toString(membraneReplacements));
        properties.setProperty("sourceRefills", Long.toString(sourceRefills));
        try (var output = Files.newOutputStream(statePath(level, ".properties"))) {
            properties.store(output, "M5 load test");
        }
        List<String> csv = new ArrayList<>(SAMPLES.size() + 1);
        csv.add("tick_nanos");
        for (long sample : SAMPLES) csv.add(Long.toString(sample));
        Files.write(statePath(level, ".csv"), csv);
    }

    private static void loadState(ServerLevel level, int requested) throws IOException {
        reset(requested);
        Properties properties = new Properties();
        try (var input = Files.newInputStream(statePath(level, ".properties"))) {
            properties.load(input);
        }
        ticks = Long.parseLong(properties.getProperty("ticks"));
        wallNanos = Long.parseLong(properties.getProperty("wallNanos"));
        syncPackets = Long.parseLong(properties.getProperty("syncPackets"));
        syncBytes = Long.parseLong(properties.getProperty("syncBytes"));
        membraneReplacements = Long.parseLong(properties.getProperty("membraneReplacements"));
        sourceRefills = Long.parseLong(properties.getProperty("sourceRefills"));
        List<String> csv = Files.readAllLines(statePath(level, ".csv"));
        for (int i = 1; i < csv.size(); i++) SAMPLES.add(Long.parseLong(csv.get(i)));
    }

    private static void reset(int requested) {
        count = requested;
        active = false;
        MACHINES.clear();
        STATUSES.clear();
        SAMPLES.clear();
        for (int i = 0; i < requested; i++) MACHINES.add(position(i));
        ticks = 0;
        wallNanos = 0;
        resumedAt = 0;
        syncPackets = 0;
        syncBytes = 0;
        membraneReplacements = 0;
        sourceRefills = 0;
    }
}
