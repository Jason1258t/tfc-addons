package tfc.destroy.bridge.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import petrolpark.mc.destroy.core.chemistry.data.ReactionDataReloadListener;
import tfc.destroy.bridge.DestroyBridge;
import tfc.destroy.bridge.electric.ProcessPowerDevice;

/** Команды проверки среды и M1; этот мод не входит в release jar. */
@Mod(DestroyTest.MOD_ID)
public final class DestroyTest {
    public static final String MOD_ID = DestroyBridge.MOD_ID + "_devtest";
    private static final BlockPos PROCESS = new BlockPos(0, -60, 10);
    private static final BlockPos SOURCE = PROCESS.north(2);
    private static final BlockPos BATTERY = PROCESS.east(2);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<ProcessBlock> PROCESS_BLOCK = BLOCKS.register("process",
        () -> new ProcessBlock(Block.Properties.of()));
    public static final DeferredBlock<SourceBlock> SOURCE_BLOCK = BLOCKS.register("source",
        () -> new SourceBlock(Block.Properties.of()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessBlockEntity>> PROCESS_BE =
        BLOCK_ENTITIES.register("process", () -> BlockEntityType.Builder
            .of(ProcessBlockEntity::new, PROCESS_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SourceBlockEntity>> SOURCE_BE =
        BLOCK_ENTITIES.register("source", () -> BlockEntityType.Builder
            .of(SourceBlockEntity::new, SOURCE_BLOCK.get()).build(null));

    private static int checks;
    private static int failures;

    public DestroyTest(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(DestroyTest::capabilities);
        NeoForge.EVENT_BUS.addListener(DestroyTest::commands);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PROCESS_BE.get(), (be, side) -> {
            if (side == Direction.NORTH) return be.hydrogen;
            if (side == Direction.SOUTH) return be.chlorine;
            if (side == Direction.EAST) return be.sodiumHydroxide;
            return be.input;
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SOURCE_BE.get(), (be, side) -> be.tank);
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroytest")
            .then(Commands.literal("environment").executes(ctx -> {
                checks = 0;
                failures = 0;
                ServerLevel level = ctx.getSource().getLevel();
                check(ctx, level.getRecipeManager().byKey(ResourceLocation.parse("destroy:electrolysis/castner_process")).isPresent(),
                    "штатный destroy:electrolysis/castner_process загружен");
                ResourceLocation reaction = ResourceLocation.parse("destroy_bridge_devtest:hydrogen_chloride");
                check(ctx, ReactionDataReloadListener.LAST_LOADED.containsKey(reaction),
                    "datapack-реакция Destroy загружена сервером");
                return report(ctx);
            }))
            .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
            .then(Commands.literal("power").then(Commands.argument("volts", DoubleArgumentType.doubleArg(0))
                .executes(ctx -> power(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "volts")))))
            .then(Commands.literal("fill").then(Commands.argument("output", StringArgumentType.word())
                .executes(ctx -> fill(ctx, StringArgumentType.getString(ctx, "output")))))
            .then(Commands.literal("report").executes(ctx -> reportState(ctx.getSource())))
            .then(Commands.literal("restartcase").executes(DestroyTest::restartCase))
            .then(Commands.literal("assert").executes(DestroyTest::report)
                .then(Commands.argument("case", StringArgumentType.word())
                    .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("fail").executes(ctx -> {
                checks = 0;
                failures = 0;
                check(ctx, false, "намеренно неверное ожидание");
                return report(ctx);
            })));
    }

    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        place(level, SOURCE, SOURCE_BLOCK.get().defaultBlockState());
        place(level, PROCESS, PROCESS_BLOCK.get().defaultBlockState());
        place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        SourceBlockEntity tank = source(level);
        tank.tank.setFluid(new FluidStack(TFCFluids.SALT_WATER.getSource(), 8000));
        tank.setChanged();
        wire(level, BATTERY, 0, PROCESS, ProcessPowerDevice.PLUS);
        wire(level, BATTERY, 1, PROCESS, ProcessPowerDevice.MINUS);
        // Напряжение задаёт отдельная команда после инициализации EE-устройств на следующем тике.
        source.sendSuccess(() -> Component.literal("стенд M1 собран"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b),
            CEEWireTypes.COPPER.get());
    }

    private static int power(CommandSourceStack source, double volts) {
        CreativeBatteryDevice battery = DevicesSavedData.load(source.getLevel())
            .getDevice(BATTERY, CreativeBatteryDevice.class);
        if (battery == null) {
            source.sendFailure(Component.literal("нет EE-батареи стенда"));
            return 0;
        }
        battery.voltage = volts;
        battery.acFrequency = 0;
        DevicesSavedData.load(source.getLevel()).setDirty();
        if (volts < ProcessPowerDevice.NOMINAL_VOLTAGE * 0.9) {
            ProcessBlockEntity process = process(source.getLevel());
            process.savedProgress = process.progress;
            process.setChanged();
        }
        source.sendSuccess(() -> Component.literal("напряжение стенда: " + volts + " В"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fill(CommandContext<CommandSourceStack> ctx, String output) {
        ProcessBlockEntity process = process(ctx.getSource().getLevel());
        int index = switch (output) {
            case "hydrogen" -> 0;
            case "chlorine" -> 1;
            case "sodium_hydroxide" -> 2;
            default -> -1;
        };
        if (index < 0) {
            ctx.getSource().sendFailure(Component.literal("неизвестный выход: " + output));
            return 0;
        }
        process.outputs[index].setFluid(ProcessBlockEntity.products()[index].copyWithAmount(ProcessBlockEntity.OUTPUT_CAPACITY));
        process.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("заполнен выход " + output), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int restartCase(CommandContext<CommandSourceStack> ctx) {
        checks = 0;
        failures = 0;
        ProcessBlockEntity process = process(ctx.getSource().getLevel());
        check(ctx, process.progress > 0 && process.progress < ProcessBlockEntity.DURATION,
            "перед перезапуском есть незавершённый прогресс");
        process.savedProgress = process.progress;
        process.savedBatches = process.batches;
        process.savedMixture = process.mixture.getFluid().copy();
        process.setChanged();
        power(ctx.getSource(), 0);
        return report(ctx);
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        checks = 0;
        failures = 0;
        ProcessBlockEntity process = process(ctx.getSource().getLevel());
        ProcessPowerDevice device = process.device();
        FluidStack[] products = ProcessBlockEntity.products();
        switch (testCase) {
            case "normal" -> {
                check(ctx, device != null, "EE-драйвер создан");
                if (device != null) {
                    check(ctx, close(device.measuredVoltage(), 300, 0.25), "измерено около 300 В с учётом проводов");
                    check(ctx, close(device.measuredCurrent(), 8, 0.1), "измерено около 8 А");
                    check(ctx, device.hasWorkingPower(), "напряжение и ток в рабочей зоне");
                }
                check(ctx, process.batches == 1, "выполнена ровно одна партия");
                for (int i = 0; i < 3; i++) {
                    final int output = i;
                    check(ctx, process.outputs[i].getFluidAmount() == products[i].getAmount(),
                        "точное количество выхода " + output);
                    check(ctx, FluidStack.isSameFluidSameComponents(process.outputs[i].getFluid(), products[i]),
                        "состав и data components выхода " + output + " сохранены");
                }
                check(ctx, remainingWater(ctx.getSource().getLevel()) == 7000,
                    "масса TFC/смеси сохранена: 8000 - 1000 = 7000 мБ");
            }
            case "undervoltage" -> {
                check(ctx, device != null, "EE-драйвер создан при просадке");
                if (device != null) {
                    check(ctx, close(device.measuredVoltage(), 200, 0.25), "измерено около 200 В с учётом проводов");
                    check(ctx, close(device.measuredCurrent(), 200.0 / ProcessPowerDevice.LOAD_RESISTANCE, 0.1),
                        "ток соответствует реальной нагрузке");
                    check(ctx, !device.hasWorkingPower(), "пониженное напряжение останавливает процесс");
                }
                check(ctx, process.progress >= process.savedProgress && process.progress <= process.savedProgress + 1
                    && process.batches == 0,
                    "после просадки возможен только один переходный тик, новая партия не расходуется");
                check(ctx, emptyOutputs(process), "нет бесплатных продуктов");
                check(ctx, remainingWater(ctx.getSource().getLevel()) == 8000, "входная партия не израсходована");
            }
            case "blocked_hydrogen", "blocked_chlorine", "blocked_sodium_hydroxide" -> {
                int blocked = switch (testCase) {
                    case "blocked_hydrogen" -> 0;
                    case "blocked_chlorine" -> 1;
                    default -> 2;
                };
                check(ctx, process.outputs[blocked].getFluidAmount() == ProcessBlockEntity.OUTPUT_CAPACITY,
                    "выбранный выход действительно полон");
                for (int i = 0; i < 3; i++) {
                    if (i != blocked) check(ctx, process.outputs[i].isEmpty(), "другой выход " + i + " остался пустым");
                }
                check(ctx, process.progress == 0 && process.batches == 0, "переполнение блокирует всю операцию");
                check(ctx, remainingWater(ctx.getSource().getLevel()) == 8000, "вход не списан при переполнении");
            }
            case "persisted" -> {
                check(ctx, process.savedProgress > 0, "контрольный прогресс сохранён в NBT");
                check(ctx, process.progress == process.savedProgress, "прогресс после перезапуска не изменился");
                check(ctx, process.batches == process.savedBatches, "партия не повторилась после перезапуска");
                check(ctx, emptyOutputs(process), "после reload нет повторных продуктов");
                check(ctx, remainingWater(ctx.getSource().getLevel()) == 8000, "все баки сохранили исходную массу");
                check(ctx, FluidStack.isSameFluidSameComponents(process.mixture.getFluid(), process.savedMixture),
                    "точный NBT-снимок Destroy-смеси пережил перезапуск");
            }
            default -> check(ctx, false, "неизвестный сценарий " + testCase);
        }
        return report(ctx);
    }

    private static boolean emptyOutputs(ProcessBlockEntity process) {
        for (var output : process.outputs) if (!output.isEmpty()) return false;
        return true;
    }

    private static int remainingWater(ServerLevel level) {
        ProcessBlockEntity process = process(level);
        return source(level).tank.getFluidAmount() + process.input.getFluidAmount() + process.mixture.getFluidAmount();
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static ProcessBlockEntity process(ServerLevel level) {
        if (level.getBlockEntity(PROCESS) instanceof ProcessBlockEntity process) return process;
        throw new IllegalStateException("На позиции стенда нет контроллера M1");
    }

    private static SourceBlockEntity source(ServerLevel level) {
        if (level.getBlockEntity(SOURCE) instanceof SourceBlockEntity source) return source;
        throw new IllegalStateException("На позиции стенда нет исходного бака");
    }

    private static int reportState(CommandSourceStack source) {
        ProcessBlockEntity process = process(source.getLevel());
        ProcessPowerDevice device = process.device();
        String message = "M1 " + process.status() + " progress=" + process.progress + " batches=" + process.batches
            + " tanks=" + process.input.getFluidAmount() + "/" + process.mixture.getFluidAmount() + "/"
            + process.hydrogen.getFluidAmount() + "/" + process.chlorine.getFluidAmount() + "/"
            + process.sodiumHydroxide.getFluidAmount();
        if (device != null) message += String.format(" V=%.3f A=%.3f P=%.1f", device.measuredVoltage(),
            device.measuredCurrent(), device.measuredPower());
        message += " mixture=" + process.mixture.getFluid().getComponents();
        String result = message;
        source.sendSuccess(() -> Component.literal(result), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void check(CommandContext<CommandSourceStack> ctx, boolean condition, String label) {
        checks++;
        if (!condition) failures++;
        ctx.getSource().sendSystemMessage(Component.literal("DESTROYTEST " + (condition ? "OK " : "FAIL ") + label));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        int passed = checks - failures;
        ctx.getSource().sendSystemMessage(Component.literal(
            "DESTROYTEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + passed + "/" + checks));
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
