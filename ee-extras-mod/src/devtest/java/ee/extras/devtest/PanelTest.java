package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
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
        var inlet = panelHit(level, 0.5, 0.68);
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
