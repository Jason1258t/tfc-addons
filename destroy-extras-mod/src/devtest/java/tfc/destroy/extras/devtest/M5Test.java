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
import java.util.List;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStatus;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;

/** Проверки промышленной формы M5: Create-привод, остановка, восстановление и разборка без потерь. */
final class M5Test {
    private static final BlockPos PROCESS = new BlockPos(40, -60, 10);
    private static final BlockPos BATTERY = PROCESS.east(3);
    private static final BlockPos MOTOR = PROCESS.offset(1, 1, 2);
    private static final BlockPos SHELL_PART = PROCESS.offset(-1, 2, 1);
    private static int checks;
    private static int failures;
    private static int stoppedProgress;
    private static int stoppedBatches;
    private static int sableBatches;
    private static int sableWater;
    private static int sableMembrane;

    private M5Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m5build").executes(ctx -> build(ctx.getSource())))
            .then(Commands.literal("m5rpm").then(Commands.argument("rpm", IntegerArgumentType.integer(0, 256))
                .executes(ctx -> rpm(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rpm")))))
            .then(Commands.literal("m5breakshell").executes(ctx -> breakShell(ctx.getSource())))
            .then(Commands.literal("m5repairshell").executes(ctx -> repairShell(ctx.getSource())))
            .then(Commands.literal("m5sample").executes(ctx -> {
                MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
                stoppedProgress = process.progress;
                stoppedBatches = process.batches;
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("m5sableprepare").executes(M5Test::sablePrepare))
            .then(Commands.literal("m5sableassert").executes(M5Test::sableAssert))
            .then(Commands.literal("m5assert").then(Commands.literal("stopped").executes(ctx -> stopped(ctx)))
                .then(Commands.literal("running").executes(ctx -> running(ctx))))
            .then(Commands.literal("m5break").executes(ctx -> breakController(ctx))));
    }

    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        DestroyExtrasTest.buildIndustrial(level, PROCESS);
        level.setBlock(BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState(), Block.UPDATE_ALL);
        process(level).input.setFluid(new FluidStack(TFCFluids.SALT_WATER.getSource(), 8000));
        process(level).membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
        wire(level, BATTERY, 0, PROCESS, ProcessPowerDevice.PLUS);
        wire(level, BATTERY, 1, PROCESS, ProcessPowerDevice.MINUS);
        CreativeBatteryDevice battery = DevicesSavedData.load(level).getDevice(BATTERY, CreativeBatteryDevice.class);
        if (battery != null) {
            battery.voltage = 300;
            battery.acFrequency = 0;
            DevicesSavedData.load(level).setDirty();
        }
        source.sendSuccess(() -> Component.literal("стенд M5 собран: корпус, насос, EE и 8000 мБ рассола"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int rpm(CommandSourceStack source, int rpm) {
        if (source.getLevel().getBlockEntity(MOTOR) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
            source.sendSuccess(() -> Component.literal("насос M5: " + rpm + " RPM"), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.literal("нет Create-мотора M5"));
        return 0;
    }

    private static int breakShell(CommandSourceStack source) {
        MembraneElectrolyzerBlockEntity process = process(source.getLevel());
        stoppedProgress = process.progress;
        stoppedBatches = process.batches;
        source.getLevel().setBlock(SHELL_PART, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        source.sendSuccess(() -> Component.literal("удалён блок корпуса M5"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int repairShell(CommandSourceStack source) {
        source.getLevel().setBlock(SHELL_PART, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        source.sendSuccess(() -> Component.literal("корпус M5 восстановлен"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopped(CommandContext<CommandSourceStack> ctx) {
        reset();
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        check(ctx, process.status() == ElectrolyzerStatus.FAULT, "неисправная форма или привод даёт FAULT");
        check(ctx, !process.formed() || process.pumpRpm() < 16, "диагностика видит причину остановки");
        check(ctx, process.progress == stoppedProgress && process.batches == stoppedBatches,
            "за время остановки прогресс и партии не изменились");
        check(ctx, process.batches == 0 && process.outputs[0].isEmpty(), "остановка до первой партии не создаёт продукты");
        return report(ctx);
    }

    private static int running(CommandContext<CommandSourceStack> ctx) {
        reset();
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        check(ctx, process.formed() && process.pumpRpm() >= 16, "корпус и Create-насос восстановлены");
        check(ctx, process.batches >= 1 && process.outputs[0].getFluidAmount() >= 250,
            "после восстановления выполнена production-партия");
        check(ctx, process.remainingMembraneOperations() <= 63, "мембрана изношена только за выполненные партии");
        return report(ctx);
    }

    private static int breakController(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity before = process(level);
        int progress = before.progress;
        int batches = before.batches;
        int water = before.input.getFluidAmount();
        int membrane = before.remainingMembraneOperations();
        level.destroyBlock(PROCESS, true, null);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(PROCESS).inflate(2),
            entity -> entity.getItem().is(DestroyExtras.ELECTROLYZER_ITEM.get()));
        check(ctx, drops.size() == 1, "контроллер выпал одним предметом");
        if (drops.size() == 1) {
            ItemStack dropped = drops.getFirst().getItem().copy();
            CustomData data = dropped.get(DataComponents.BLOCK_ENTITY_DATA);
            check(ctx, data != null, "предмет контроллера содержит BlockEntityData");
            if (data != null) {
                CompoundTag tag = data.copyTag();
                check(ctx, tag.contains("Input") && tag.contains("Mixture") && tag.contains("Hydrogen")
                    && tag.contains("Chlorine") && tag.contains("SodiumHydroxide") && tag.contains("Membrane")
                    && tag.contains("Progress") && tag.contains("Batches"), "все баки, мембрана и прогресс сохранены");
                drops.getFirst().discard();
                level.setBlock(PROCESS, DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
                net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, null, PROCESS, dropped);
                MembraneElectrolyzerBlockEntity restored = process(level);
                check(ctx, restored.progress == progress && restored.batches == batches,
                    "повторная установка не сбросила и не повторила операцию");
                check(ctx, restored.input.getFluidAmount() == water && restored.remainingMembraneOperations() == membrane,
                    "повторная установка сохранила исходную воду и ресурс мембраны");
                check(ctx, restored.outputs[0].getFluidAmount() == before.outputs[0].getFluidAmount(),
                    "продукты не продублированы");
            }
        }
        return report(ctx);
    }

    private static int sablePrepare(CommandContext<CommandSourceStack> ctx) {
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        sableBatches = process.batches;
        sableWater = totalFluid(process);
        sableMembrane = process.remainingMembraneOperations();
        ctx.getSource().sendSuccess(() -> Component.literal("M5 Sable: исходное состояние сохранено"), false);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static int sableAssert(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity found = null;
        int controllers = 0;
        try {
            var field = net.minecraft.world.level.Level.class.getDeclaredField("blockEntityTickers");
            field.setAccessible(true);
            for (net.minecraft.world.level.block.entity.TickingBlockEntity ticker
                : (List<net.minecraft.world.level.block.entity.TickingBlockEntity>) field.get(level)) {
                if (level.getBlockEntity(ticker.getPos()) instanceof MembraneElectrolyzerBlockEntity process) {
                    found = process;
                    controllers++;
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Не удалось перечислить блок-сущности Sable", error);
        }
        check(ctx, controllers == 1 && found != null && !found.getBlockPos().equals(PROCESS),
            "при сборке Sable контроллер перемещён ровно один раз");
        if (found != null) {
            check(ctx, found.formed() && found.pumpRpm() >= 16, "корпус и Create-насос работают в подуровне Sable");
            check(ctx, found.device() != null && found.device().hasWorkingPower(), "EE-устройство и провода сохранили питание");
            check(ctx, found.batches > sableBatches, "после переноса выполнена новая production-партия");
            check(ctx, totalFluid(found) == sableWater, "все пять баков сохранили полную массу жидкостей");
            check(ctx, found.remainingMembraneOperations() == sableMembrane - (found.batches - sableBatches),
                "мембрана сохранена и изношена только за новые партии");
            ctx.getSource().sendSystemMessage(Component.literal("M5 SABLE pos=" + found.getBlockPos()
                + " batches=" + found.batches + " pumpRpm=" + found.pumpRpm()));
        }
        return report(ctx);
    }

    private static int totalFluid(MembraneElectrolyzerBlockEntity process) {
        int total = process.input.getFluidAmount() + process.mixture.getFluidAmount();
        for (var output : process.outputs) total += output.getFluidAmount();
        return total;
    }

    private static MembraneElectrolyzerBlockEntity process(ServerLevel level) {
        if (level.getBlockEntity(PROCESS) instanceof MembraneElectrolyzerBlockEntity process) return process;
        throw new IllegalStateException("На позиции стенда нет электролизёра M5");
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b),
            CEEWireTypes.COPPER.get());
    }

    private static void reset() {
        checks = 0;
        failures = 0;
    }

    private static void check(CommandContext<CommandSourceStack> ctx, boolean condition, String label) {
        checks++;
        if (!condition) failures++;
        ctx.getSource().sendSystemMessage(Component.literal("DESTROYEXTRATEST " + (condition ? "OK " : "FAIL ") + label));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        int passed = checks - failures;
        ctx.getSource().sendSystemMessage(Component.literal(
            "DESTROYEXTRATEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + passed + "/" + checks));
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
