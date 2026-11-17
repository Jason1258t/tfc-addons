package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import ee.extras.panel.PanelModules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PanelTest {
    private static final BlockPos SOURCE = new BlockPos(2, -60, 12);
    private static final BlockPos PANEL = SOURCE.east(4);
    private static final BlockPos TERMINAL = PANEL.east(4);
    private static final BlockPos METER = TERMINAL.east(2);

    static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevTest.place(level, SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, false));
        DevTest.place(level, PANEL, CEEBlocks.ELECTRICAL_PANEL.getDefaultState());
        DevTest.place(level, TERMINAL, Extras.eeTermination());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        var lead = Extras.end(level, TERMINAL);
        DevTest.wire(level, METER, 0, TERMINAL, lead.conductor(1).id());
        DevTest.wire(level, METER, 1, TERMINAL, lead.conductor(2).id());
        var inlet = panelHit(level, 0.31, 0.69);
        var input = panelHit(level, 0.25, 0.32);
        var output = panelHit(level, 0.75, 0.32);
        click(level, new ItemStack(CEEBlocks.DUPLEX_WIRE_TERMINATION.get()), inlet);
        click(level, new ItemStack(PanelModules.SWITCH_ITEM.get()), input);
        var in = ee.extras.panel.PanelPorts.hit(level, inlet);
        var a = ee.extras.panel.PanelPorts.hit(level, input);
        var b = ee.extras.panel.PanelPorts.hit(level, output);
        if (in == null || a == null || b == null || in.cable().equals(a.cable()) || a.cable().equals(b.cable()))
            throw new IllegalStateException("Щиток не распознал три ввода");
        for (int k = 1; k <= 2; k++)
            DevTest.wire(level, SOURCE, k - 1, PANEL, in.conductor(k).id());
        var spool = new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 8);
        click(level, spool, inlet);
        click(level, spool, input);
        click(level, spool, output);
        click(level, spool, new BlockHitResult(Vec3.atCenterOf(TERMINAL), Direction.UP, TERMINAL, false));
        return 1;
    }

    static int buildSinglePole(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevTest.place(level, SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, false));
        DevTest.place(level, PANEL, CEEBlocks.ELECTRICAL_PANEL.getDefaultState());
        BlockPos inputTerminal = SOURCE.east(2);
        DevTest.place(level, inputTerminal, Extras.eeTermination());
        DevTest.place(level, TERMINAL, Extras.eeTermination());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        var inputLead = Extras.end(level, inputTerminal);
        var outputLead = Extras.end(level, TERMINAL);
        for (int k = 1; k <= 2; k++) {
            DevTest.wire(level, SOURCE, k - 1, inputTerminal, inputLead.conductor(k).id());
            DevTest.wire(level, METER, k - 1, TERMINAL, outputLead.conductor(k).id());
        }

        var top = panelHit(level, 0.3125, 0.6875);
        var bottom = panelHit(level, 0.3125, 0.3125);
        click(level, new ItemStack(CEEBlocks.CUT_OFF_SWITCH.get()), panelHit(level, 0.6875, 0.5));
        click(level, new ItemStack(CEEBlocks.DUPLEX_WIRE_TERMINATION.get()), top);
        click(level, new ItemStack(CEEBlocks.DUPLEX_WIRE_TERMINATION.get()), bottom);
        var panel = (ElectricalPanelBlockEntity) level.getBlockEntity(PANEL);
        var lever = panel.getAttachments()[ElectricalPanelSlot.HALF_LEFT.ordinal()];
        var upper = ee.extras.panel.PanelPorts.hit(level, top);
        var lower = ee.extras.panel.PanelPorts.hit(level, bottom);
        if (lever == null || upper == null || lower == null || upper.cable().equals(lower.cable())
                || panel.getAttachments()[ElectricalPanelSlot.QUARTER_RIGHT_UPPER.ordinal()] == null
                || panel.getAttachments()[ElectricalPanelSlot.QUARTER_RIGHT_LOWER.ordinal()] == null)
            throw new IllegalStateException("Рубильник и два соединителя должны занимать три соседних слота");

        var spool = new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 8);
        click(level, spool, new BlockHitResult(Vec3.atCenterOf(inputTerminal), Direction.UP, inputTerminal, false));
        click(level, spool, top);
        click(level, spool, bottom);
        click(level, spool, new BlockHitResult(Vec3.atCenterOf(TERMINAL), Direction.UP, TERMINAL, false));
        clickWire(level, upper.conductor(2), lower.conductor(2));
        clickWire(level, upper.conductor(1), lever.nodes[0]);
        clickWire(level, lever.nodes[1], lower.conductor(1));
        return 1;
    }

    private static void clickWire(ServerLevel level, InWorldNode from, InWorldNode to) {
        var spool = new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 2);
        var face = level.getBlockState(PANEL).getValue(com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock.FACING);
        click(level, spool, new BlockHitResult(from.getPosition(level), face, PANEL, false));
        click(level, spool, new BlockHitResult(to.getPosition(level), face, PANEL, false));
        if (!InfrastructureSavedData.load(level).isConnected(from, to))
            throw new IllegalStateException("Провод щитка не соединяет выбранные клеммы: " + from + " → " + to);
    }

    static int toggleSinglePole(CommandSourceStack src) {
        click(src.getLevel(), ItemStack.EMPTY, panelHit(src.getLevel(), 0.6875, 0.5));
        return 1;
    }

    private static BlockHitResult panelHit(ServerLevel level, double x, double y) {
        var face = level.getBlockState(PANEL).getValue(com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock.FACING);
        var local = net.createmod.catnip.math.VecHelper.rotateCentered(new Vec3(x, y, 0.875), -face.toYRot() + 180.0, Direction.Axis.Y);
        return new BlockHitResult(Vec3.atLowerCornerOf(PANEL).add(local), face, PANEL, false);
    }

    private static void click(ServerLevel level, ItemStack stack, BlockHitResult hit) {
        var player = CapTest.player(level, stack);
        if (!player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit).consumesAction())
            throw new IllegalStateException("Клик по " + hit.getLocation() + " не сработал");
    }

    static int toggle(CommandSourceStack src) {
        click(src.getLevel(), ItemStack.EMPTY, panelHit(src.getLevel(), 0.5, 0.32));
        return 1;
    }

    static int report(CommandSourceStack src, boolean closed) {
        var meter = DevicesSavedData.load(src.getLevel()).getDevice(METER, GaugeDevice.class);
        double voltage = meter == null ? Double.NaN : Math.abs(meter.rmsVoltages.get());
        boolean pass = closed ? voltage > 80 : voltage < 1;
        var message = Component.literal((pass ? "PASS" : "FAIL") + " щиток: " + voltage + " В, рубильник " + (closed ? "включён" : "выключен"));
        if (pass) src.sendSuccess(() -> message, false);
        else src.sendFailure(message);
        return pass ? 1 : 0;
    }

    private PanelTest() {}
}
