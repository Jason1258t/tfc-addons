package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ee.extras.cable.CableEnd;
import ee.extras.wire.JunctionData;
import ee.extras.wire.Point;
import ee.extras.wire.SurfaceWireEntity;
import ee.extras.wire.SurfaceWires;
import ee.extras.wire.WireEnd;
import ee.extras.wire.WirePackets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Декоративный провод (#205) на сервере разработки:
 *   /wiretest build — батарея → соединитель EE A; провод кладёт FakePlayer настоящим путём (useItemOn): A, пол между,
 *                     соединитель B (за ним нагреватель и вольтметр VB); затем ответвление: клик по середине провода
 *                     (WirePackets.attachAt), пол, распределительная коробка C; от C — провод к соединителю D (вольтметр VD)
 *   /wiretest power V — напряжение батареи (переменный ток 1 Гц)
 *   /wiretest report — куски (длина, предметы, концы, жилы EE), узлы ответвлений, вольтметры, выпавшее
 *   /wiretest long — провод на 20 блоков: должен отказать (maxLength 16)
 *   /wiretest cut — кусачками вырезать середину куска C–D
 *   /wiretest breakb — сломать B (конец у B отключается, отрезок выпадает)
 *   /wiretest wall — поставить камень на путь провода у A (участок вырезается)
 *   /wiretest unbranch — снять кусок к коробке: два оставшихся куска срастаются, узел убирается
 */
final class Wire {
    static final BlockPos BAT = new BlockPos(0, -60, 200);
    static final BlockPos A = BAT.east(2);
    static final BlockPos B = BAT.east(12);
    static final BlockPos HB = B.south(3);
    static final BlockPos VB = HB.east(2);
    static final BlockPos C = BAT.offset(7, 0, 8);
    static final BlockPos D = BAT.offset(12, 0, 12);
    static final BlockPos VD = D.south(3);
    static final List<String> log = new ArrayList<>();

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("wiretest")
                .then(Commands.literal("build").executes(c -> build(c.getSource())))
                .then(Commands.literal("power").then(Commands.argument("v", DoubleArgumentType.doubleArg(0))
                        .executes(c -> power(c.getSource(), DoubleArgumentType.getDouble(c, "v")))))
                .then(Commands.literal("report").executes(c -> report(c.getSource())))
                .then(Commands.literal("path").executes(c -> path(c.getSource())))
                .then(Commands.literal("lantern").executes(c -> lantern(c.getSource())))
                .then(Commands.literal("lamppath").executes(c -> {
                    ServerLevel level = c.getSource().getLevel();
                    Vec3 from = ee.extras.wire.BlockTrace.alignPosition(new WireEnd.Block(L_A).position(level));
                    StringBuilder r = new StringBuilder();
                    for (BlockPos to : new BlockPos[] {LAMP, LAMP.west()}) {
                        Vec3 target = to.equals(LAMP) ? new WireEnd.Block(LAMP).position(level) : new Vec3(to.getX() + 0.5, to.getY() + 0.03, to.getZ() + 0.5);
                        var t = ee.extras.wire.BlockTrace.findPathWithState(level, from, target, new ee.extras.wire.BlockTrace.Terminal(LAMP, target), null);
                        r.append(from).append(" → ").append(target).append(": ")
                                .append(t == null ? "null" : t.result() == null ? "нет" : t.result().reachedTarget() + " " + t.result().length() + " " + t.result().points().size()).append(" | ");
                    }
                    c.getSource().sendSuccess(() -> Component.literal(r.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("lampreport").executes(c -> lampReport(c.getSource())))
                .then(Commands.literal("long").executes(c -> tooLong(c.getSource())))
                .then(Commands.literal("cut").executes(c -> cut(c.getSource())))
                .then(Commands.literal("breakb").executes(c -> {
                    c.getSource().getLevel().destroyBlock(B, true);
                    c.getSource().sendSuccess(() -> Component.literal("B сломан"), false);
                    return 1;
                }))
                .then(Commands.literal("wall").executes(c -> wall(c.getSource())))
                .then(Commands.literal("unbranch").executes(c -> unbranch(c.getSource())))
                .then(Commands.literal("give").then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                        .executes(c -> {
                            player(c.getSource().getLevel()).setItemInHand(InteractionHand.MAIN_HAND,
                                    new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), IntegerArgumentType.getInteger(c, "n")));
                            return 1;
                        }))));
    }

    static FakePlayer player(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /** Клик по верхней грани блока pos (пол — центр грани; прибор — его центр) */
    static InteractionResult click(ServerLevel level, FakePlayer p, BlockPos pos, boolean floor) {
        ItemStack stack = p.getItemInHand(InteractionHand.MAIN_HAND);
        Vec3 at = floor ? new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) : Vec3.atCenterOf(pos);
        InteractionResult r = p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(at, Direction.UP, pos, false));
        log.add((floor ? "пол " : "") + pos.getX() + "," + pos.getZ() + ":" + r
                + (r == InteractionResult.FAIL ? "(" + ee.extras.wire.SurfaceWireItem.lastFail + ")" : ""));
        ee.extras.wire.SurfaceWireItem.lastFail = null;
        return r;
    }

    static List<SurfaceWireEntity> wires(ServerLevel level) {
        List<SurfaceWireEntity> out = level.getEntitiesOfClass(SurfaceWireEntity.class, new AABB(BAT).inflate(40));
        out.sort(Comparator.comparingDouble(w -> w.position().x * 1000 + w.position().z));
        return out;
    }

    static SurfaceWireEntity wireBetween(ServerLevel level, BlockPos a, BlockPos b) {
        for (SurfaceWireEntity w : wires(level)) {
            boolean ea = is(w.end1(), a) || is(w.end2(), a), eb = is(w.end1(), b) || is(w.end2(), b);
            if (ea && (b == null || eb)) return w;
        }
        return null;
    }

    static boolean is(WireEnd e, BlockPos p) {
        return e instanceof WireEnd.Block b && b.pos().equals(p);
    }

    static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        log.clear();
        DevTest.place(level, BAT, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, true));
        for (BlockPos p : new BlockPos[] {A, B, D}) DevTest.place(level, p, Extras.eeTermination());
        DevTest.place(level, C, SurfaceWires.JUNCTION_BOX.get().defaultBlockState());
        DevTest.place(level, HB, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        DevTest.place(level, VB, CEEBlocks.VOLTMETER.getDefaultState());
        DevTest.place(level, VD, CEEBlocks.VOLTMETER.getDefaultState());
        CableEnd a = Extras.end(level, A), b = Extras.end(level, B), d = Extras.end(level, D);
        DevTest.wire(level, BAT, 0, A, a.conductor(1).id());
        DevTest.wire(level, BAT, 1, A, a.conductor(2).id());
        DevTest.wire(level, B, b.conductor(1).id(), HB, 0);
        DevTest.wire(level, B, b.conductor(2).id(), HB, 1);
        DevTest.wire(level, B, b.conductor(1).id(), VB, 0);
        DevTest.wire(level, B, b.conductor(2).id(), VB, 1);
        DevTest.wire(level, D, d.conductor(1).id(), VD, 0);
        DevTest.wire(level, D, d.conductor(2).id(), VD, 1);

        FakePlayer p = player(level);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), 64));
        click(level, p, A, false);
        click(level, p, BAT.offset(7, -1, 0), true);
        click(level, p, B, false);
        SurfaceWireEntity ab = wireBetween(level, A, B);
        log.add("A–B: " + (ab == null ? "нет" : describe(ab)));
        if (ab != null) {
            // Середина: отрезок с наибольшей длиной, его середина
            int idx = 0;
            for (int i = 0; i < ab.segments.size(); i++) if (ab.segments.get(i).gridLength > ab.segments.get(idx).gridLength) idx = i;
            WirePackets.attachAt(p, ab, idx, ab.segments.get(idx).gridLength / 2);
            log.add("выбор: " + ee.extras.wire.SurfaceWireItem.selection(p.getMainHandItem()));
            click(level, p, BAT.offset(7, -1, 4), true);
            click(level, p, C, false);
        }
        click(level, p, C, false);
        click(level, p, BAT.offset(10, -1, 12), true);
        click(level, p, D, false);
        src.sendSuccess(() -> Component.literal("клики: " + String.join("; ", log) + "; осталось провода "
                + p.getItemInHand(InteractionHand.MAIN_HAND).getCount()), false);
        return 1;
    }

    static String describe(SurfaceWireEntity w) {
        ServerLevel level = (ServerLevel) w.level();
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        StringBuilder s = new StringBuilder();
        s.append(String.format("%.2f м", w.totalLength())).append(", отрезков ").append(w.segments.size())
                .append(", предметов ").append(w.itemCount()).append(", концы ").append(end(w.end1())).append(" → ").append(end(w.end2()));
        int cores = 0, connected = 0;
        double r = 0;
        for (var c : coreList(w)) {
            cores++;
            if (sd.hasNode(c[0]) && sd.hasNode(c[1]) && sd.isConnected(c[0], c[1])) {
                connected++;
                var data = sd.getConnectionData(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(c[0], c[1]));
                if (data != null) r = data.getResistance();
            }
        }
        s.append(", жил ").append(connected).append("/").append(cores);
        if (r > 0) s.append(String.format(", R жилы %.4f Ом", r));
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    static List<InWorldNode[]> coreList(SurfaceWireEntity w) {
        try {
            var f = SurfaceWireEntity.class.getDeclaredField("cores");
            f.setAccessible(true);
            return (List<InWorldNode[]>) f.get(w);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    static String end(WireEnd e) {
        if (e == null) return "свободный";
        if (e instanceof WireEnd.Block b) return "прибор " + b.pos().getX() + "," + b.pos().getZ();
        if (e instanceof WireEnd.Junction j) return String.format("узел %.2f,%.2f", j.pos().x, j.pos().z);
        return e.type().name();
    }

    static int power(CommandSourceStack src, double volts) {
        CreativeBatteryDevice b = DevicesSavedData.load(src.getLevel()).getDevice(BAT, CreativeBatteryDevice.class);
        if (b != null) {
            b.voltage = volts;
            b.acFrequency = 1;
        }
        src.sendSuccess(() -> Component.literal("батарея провода: " + volts + " В"), false);
        return 1;
    }

    static int report(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        StringBuilder out = new StringBuilder();
        List<SurfaceWireEntity> ws = wires(level);
        out.append("кусков ").append(ws.size()).append(": ");
        for (SurfaceWireEntity w : ws) out.append("[").append(describe(w)).append("] ");
        out.append("узлов ответвлений ").append(JunctionData.get(level).count());
        DevicesSavedData d = DevicesSavedData.load(level);
        out.append("; VB ").append(Extras.vm(d, VB)).append(" В, VD ").append(Extras.vm(d, VD)).append(" В");
        int items = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(BAT).inflate(40)))
            if (e.getItem().is(SurfaceWires.DUPLEX_ITEM.get())) items += e.getItem().getCount();
        out.append("; на земле провода ").append(items);
        src.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    /** Путь от края провода у A к точке кабеля B: где цель, дошёл ли */
    static int path(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SurfaceWireEntity w = wireBetween(level, A, null);
        WireEnd.Block b = new WireEnd.Block(B);
        Vec3 target = b.position(level);
        Vec3 from = w == null ? ee.extras.wire.BlockTrace.alignPosition(new WireEnd.Block(A).position(level)) : w.endPosition();
        Direction dir = w == null ? null : w.segments.get(w.segments.size() - 1).direction;
        StringBuilder r = new StringBuilder();
        if (w != null) for (Point s : w.segments) r.append(s.direction).append(s.gridLength).append(' ');
        for (Direction cd : new Direction[] {null, dir}) {
            var t = ee.extras.wire.BlockTrace.findPathWithState(level, from, target, new ee.extras.wire.BlockTrace.Terminal(B, target), cd);
            r.append("| ").append(cd).append(": ").append(t == null ? "null" : t.result() == null ? "нет результата" : t.result().reachedTarget() + " " + t.result().length());
        }
        src.sendSuccess(() -> Component.literal("от " + from + " к " + target + ": " + r), false);
        return 1;
    }

    static final BlockPos L_A = BAT.offset(0, 0, 24);
    static final BlockPos LAMP = L_A.east(5);

    /** Фонарь IE (IE=1): соединитель → клик проводом по фонарю IE — превращается и подключается, горит */
    static int lantern(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevTest.place(level, L_A, Extras.eeTermination());
        DevTest.place(level, LAMP, Lantern.ieLantern(false));
        CableEnd a = Extras.end(level, L_A);
        DevTest.wire(level, BAT, 0, L_A, a.conductor(1).id());
        DevTest.wire(level, BAT, 1, L_A, a.conductor(2).id());
        FakePlayer p = player(level);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), 64));
        log.clear();
        click(level, p, L_A, false);
        click(level, p, LAMP, false);
        SurfaceWireEntity w = wireBetween(level, L_A, LAMP);
        src.sendSuccess(() -> Component.literal("фонарь: " + String.join("; ", log) + "; блок "
                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(LAMP).getBlock()) + "; провод "
                + (w == null ? "нет" : describe(w))), false);
        return 1;
    }

    static int lampReport(CommandSourceStack src) {
        BlockState st = src.getLevel().getBlockState(LAMP);
        String lit = st.getBlock() instanceof ee.extras.lantern.LanternBlock ? (st.getValue(ee.extras.lantern.LanternBlock.LIT) ? "горит" : "не горит") : "не фонарь EE";
        src.sendSuccess(() -> Component.literal("фонарь " + lit), false);
        return 1;
    }

    static int tooLong(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos a = BAT.offset(0, 0, 30), b = a.east(20);
        DevTest.place(level, a, Extras.eeTermination());
        DevTest.place(level, b, Extras.eeTermination());
        FakePlayer p = player(level);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), 64));
        log.clear();
        click(level, p, a, false);
        click(level, p, b, false);
        SurfaceWireEntity w = wireBetween(level, a, b);
        int left = p.getMainHandItem().getCount();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        src.sendSuccess(() -> Component.literal("20 блоков: " + String.join("; ", log) + "; провод " + (w == null ? "не проложен" : describe(w))
                + "; провода в руке " + left), false);
        return 1;
    }

    static int cut(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SurfaceWireEntity w = wireBetween(level, D, null);
        if (w == null) return 0;
        int total = w.gridLength();
        w.cutRange(total / 2 - 4, total / 2 + 4, null);
        src.sendSuccess(() -> Component.literal("вырезано 8 шагов из " + total), false);
        return 1;
    }

    static int wall(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SurfaceWireEntity w = wireBetween(level, A, null);
        if (w == null) return 0;
        BlockPos at = BlockPos.containing(w.at(Math.min(w.gridLength(), 24)));
        BlockState stone = Blocks.STONE.defaultBlockState();
        level.setBlock(at, stone, 3);
        w.markSupportDirty();
        src.sendSuccess(() -> Component.literal("камень в " + at.toShortString()), false);
        return 1;
    }

    static int unbranch(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        for (SurfaceWireEntity w : wires(level)) {
            if (is(w.end2(), C) && w.end1() instanceof WireEnd.Junction || is(w.end1(), C) && w.end2() instanceof WireEnd.Junction) {
                w.removeWhole(null);
                src.sendSuccess(() -> Component.literal("кусок к коробке снят"), false);
                return 1;
            }
        }
        return 0;
    }

    private Wire() {
    }
}
