package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.PcbCircuitDevice;
import ee.extras.pcb.PcbEditorBlockEntity;
import ee.extras.pcb.PcbEditorMenu;
import ee.extras.pcb.PcbItems;
import ee.extras.cable.CableKind;
import ee.extras.cable.CableLaying;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class PcbSupplyTest {
    private static final BlockPos SOURCE = new BlockPos(4, -60, 4);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbsupply").requires(source -> source.hasPermission(2))
                .then(Commands.literal("ac").executes(context -> build(context.getSource(), SOURCE, false, true)))
                .then(Commands.literal("dc").executes(context -> build(context.getSource(), SOURCE, true, true)))
                .then(Commands.literal("pass_dc").executes(context -> build(context.getSource(), SOURCE, false, false)))
                .then(Commands.literal("report").executes(context -> report(context.getSource()))));
    }

    static int build(CommandSourceStack source, BlockPos sourcePos, boolean rectified, boolean alternating) {
        var level = source.getLevel();
        BlockPos terminationPos = sourcePos.east(2);
        BlockPos supplyPos = sourcePos.east(5);
        BlockPos boardPos = sourcePos.east(8);
        BlockPos tablePos = sourcePos.south(2);
        for (int x = 0; x <= 8; x++) DevTest.place(level, sourcePos.east(x).below(), Blocks.SMOOTH_STONE.defaultBlockState());
        for (BlockPos pos : new BlockPos[] {sourcePos, terminationPos, supplyPos, boardPos, tablePos})
            DevTest.place(level, pos, Blocks.AIR.defaultBlockState());
        DevTest.place(level, tablePos, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(tablePos);
        var player = FakePlayerFactory.getMinecraft(level);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ee_extras",
                rectified ? "pcb_supply_dc" : "pcb_supply"));
        editor.setItem(0, item.getDefaultInstance());
        var menu = new PcbEditorMenu(1, player.getInventory(), ContainerLevelAccess.create(level, tablePos), editor);
        menu.clickMenuButton(player, PcbEditorMenu.VOLTS);
        DevTest.place(level, supplyPos.below(), Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, editor.getItem(0).copy());
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(supplyPos), Direction.UP, supplyPos.below(), false);
        if (!player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction())
            throw new IllegalStateException("Модуль не установился после настройки");
        DevTest.place(level, sourcePos, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, alternating));
        var devices = DevicesSavedData.load(level);
        var battery = devices.getDevice(sourcePos, CreativeBatteryDevice.class);
        battery.voltage = 300;
        battery.acFrequency = alternating ? 1 : 0;
        DevTest.place(level, terminationPos, Extras.eeTermination());
        var termination = Extras.end(level, terminationPos);
        DevTest.wire(level, sourcePos, 0, terminationPos, termination.conductor(1).id());
        DevTest.wire(level, sourcePos, 1, terminationPos, termination.conductor(2).id());
        if (!CableLaying.connect(level, CableKind.DUPLEX, termination, Extras.end(level, supplyPos)))
            throw new IllegalStateException("Модуль не принял двухжильный кабель");
        DevTest.place(level, boardPos, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
        DevTest.wire(level, supplyPos, 3, boardPos, 0);
        DevTest.wire(level, supplyPos, 4, boardPos, 1);
        devices.getDevice(boardPos, PcbCircuitDevice.class).setCircuit(java.util.List.of(
                new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR, 0, 1, 220, 0, 0, 1)));
        return 1;
    }

    private static int report(CommandSourceStack source) {
        var level = source.getLevel();
        var devices = DevicesSavedData.load(level);
        var data = new CompoundTag();
        devices.getDevice(SOURCE.east(5)).write(data);
        source.sendSuccess(() -> Component.literal("Модуль " + level.getBlockState(SOURCE.east(5)) + " " + data), false);
        return 1;
    }
}
