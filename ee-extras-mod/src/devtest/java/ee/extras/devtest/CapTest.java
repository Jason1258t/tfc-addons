package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import ee.extras.EeExtras;
import ee.extras.cable.CableCap;
import ee.extras.cable.CableEnd;
import ee.extras.cable.CableWires;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Крышка двухжильного кабеля на приборе EE (#273). Всё — кликами FakePlayer настоящим путём (useItemOn →
 * PlayerInteractEvent.RightClickBlock):
 *   /rectest caps — батарея постоянного тока → соединитель EE A; крышки на лампу, нагреватель и вольтметр, кабели от A к
 *                   каждому. Отказы: выпрямитель (4 контакта), опорный изолятор (1), нагреватель с проводом (занято),
 *                   вторая крышка на лампу, кабель к нагревателю без крышки, провод EE на прибор с крышкой.
 *   /rectest capreport — горит ли лампа, вольтметр, жила 1 → контакт 1 и жила 2 → контакт 2, крышки, выпавшее
 *   /rectest capbreak — сломать лампу с выпадением (крышка выпадает, кабель к ней снят)
 *   /rectest captake — Shift + пустая рука по нагревателю: крышка и катушка — игроку
 */
final class CapTest {
    static final BlockPos BAT = new BlockPos(0, -60, 150);
    static final BlockPos A = BAT.east(2);
    static final BlockPos BULB = BAT.east(6);
    static final BlockPos HEATER = BAT.east(8);
    static final BlockPos VM = BAT.east(10);
    static final BlockPos RECT = BAT.offset(6, 0, 4);
    static final BlockPos INSULATOR = BAT.offset(8, 0, 4);
    static final BlockPos WIRED = BAT.offset(10, 0, 4);
    static final BlockPos BARE = BAT.offset(12, 0, 4);
    static final List<String> log = new ArrayList<>();

    static String click(ServerLevel level, FakePlayer player, String what, BlockPos pos) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        String r = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit).toString();
        log.add(what + ":" + r);
        return r;
    }

    static FakePlayer player(ServerLevel level, ItemStack stack) {
        FakePlayer p = FakePlayerFactory.getMinecraft(level);
        p.getInventory().clearContent();
        p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        log.clear();
        DevTest.place(level, BAT, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, false));
        DevTest.place(level, A, Extras.eeTermination());
        DevTest.place(level, BULB, CEEBlocks.BULB.getDefaultState());
        DevTest.place(level, HEATER, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        DevTest.place(level, VM, CEEBlocks.VOLTMETER.getDefaultState());
        DevTest.place(level, RECT, EeExtras.SINGLE_PHASE.block.get().defaultBlockState());
        DevTest.place(level, INSULATOR, CEEBlocks.CONNECTOR.getDefaultState());
        DevTest.place(level, WIRED, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        DevTest.place(level, BARE, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        CableEnd a = Extras.end(level, A);
        DevTest.wire(level, BAT, 0, A, a.conductor(1).id());
        DevTest.wire(level, BAT, 1, A, a.conductor(2).id());
        DevTest.wire(level, WIRED, 0, INSULATOR, 0);

        FakePlayer p = player(level, new ItemStack(EeExtras.CABLE_CAP.get(), 16));
        click(level, p, "лампа", BULB);
        click(level, p, "нагреватель", HEATER);
        click(level, p, "вольтметр", VM);
        click(level, p, "выпрямитель", RECT);
        click(level, p, "изолятор", INSULATOR);
        click(level, p, "нагреватель_с_проводом", WIRED);
        click(level, p, "лампа_ещё_раз", BULB);
        int caps = p.getItemInHand(InteractionHand.MAIN_HAND).getCount();

        p = player(level, new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 16));
        for (BlockPos to : new BlockPos[] {BULB, HEATER, VM}) {
            click(level, p, "кабель_A", A);
            click(level, p, "кабель_к", to);
        }
        click(level, p, "две_крышки_начало", BULB);
        click(level, p, "две_крышки_конец", HEATER);
        click(level, p, "кабель_A", A);
        click(level, p, "кабель_без_крышки", BARE);

        p = player(level, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16));
        click(level, p, "провод_на_крышку", BULB);
        power(src, 120);
        src.sendSuccess(() -> Component.literal("клики: " + String.join(" ", log) + "; крышек осталось " + caps), false);
        return 1;
    }

    static int power(CommandSourceStack src, double volts) {
        CreativeBatteryDevice b = DevicesSavedData.load(src.getLevel()).getDevice(BAT, CreativeBatteryDevice.class);
        if (b != null) b.voltage = volts;
        return 1;
    }

    static int report(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        CableEnd a = Extras.end(level, A);
        StringBuilder out = new StringBuilder();
        BlockState bulb = level.getBlockState(BULB);
        out.append("лампа: ").append(bulb.getBlock() instanceof BulbBlock ? "свет " + bulb.getValue(BulbBlock.LIGHT) : "нет");
        out.append("; вольтметр ").append(Extras.vm(DevicesSavedData.load(level), VM)).append(" В");
        for (BlockPos p : new BlockPos[] {BULB, HEATER, VM, BARE}) {
            BlockState st = level.getBlockState(p);
            int[] c = CableCap.contacts(level, p, st);
            out.append("; ").append(p.getX() - BAT.getX()).append(" ").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath())
                    .append(" контактов ").append(c == null ? "—" : c.length).append(": крышка ").append(CableCap.get(level).has(p));
            if (c != null && c.length == 2) {
                boolean k1 = sd.isConnected(a.conductor(1), new InWorldNode(c[0], p));
                boolean k2 = sd.isConnected(a.conductor(2), new InWorldNode(c[1], p));
                boolean cross = sd.isConnected(a.conductor(1), new InWorldNode(c[1], p));
                boolean sheath = sd.isConnected(a.cable(), new InWorldNode(c[0], p));
                out.append(", ж1→к1 ").append(k1).append(", ж2→к2 ").append(k2).append(", ж1→к2 ").append(cross)
                        .append(", оболочка ").append(sheath);
            }
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(1.5)))
                out.append(", выпало ").append(e.getItem());
        }
        CableEnd lamp = CableEnd.of(level, BULB, level.getBlockState(BULB));
        CableEnd heater = CableEnd.of(level, HEATER, level.getBlockState(HEATER));
        if (lamp != null && heater != null) {
            var sheath = sd.getConnectionData(new InWorldNodeConnection(lamp.conductor(1), heater.conductor(2)));
            var first = sd.getConnectionData(new InWorldNodeConnection(lamp.conductor(1), heater.conductor(1)));
            var second = sd.getConnectionData(new InWorldNodeConnection(lamp.conductor(2), heater.conductor(2)));
            out.append("; между крышками: оболочка ").append(sheath != null && sheath.wireType() == CableWires.DUPLEX.get())
                    .append(", жила 1 ").append(first != null && first.wireType() == CableWires.DUPLEX_CORE.get())
                    .append(", жила 2 ").append(second != null && second.wireType() == CableWires.DUPLEX_CORE.get());
        }
        src.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    static int breakBulb(CommandSourceStack src) {
        boolean ok = src.getLevel().destroyBlock(BULB, true);
        src.sendSuccess(() -> Component.literal("лампа сломана: " + ok), false);
        return 1;
    }

    static int take(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        log.clear();
        FakePlayer p = player(level, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        click(level, p, "снять", HEATER);
        p.setShiftKeyDown(false);
        StringBuilder inv = new StringBuilder();
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && !s.is(Items.AIR)) inv.append(s).append(' ');
        src.sendSuccess(() -> Component.literal(String.join(" ", log) + "; у игрока: " + inv), false);
        return 1;
    }

    private CapTest() {
    }
}
