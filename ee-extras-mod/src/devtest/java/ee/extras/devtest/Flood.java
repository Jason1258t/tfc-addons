package ee.extras.devtest;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.wires.Connection;
import blusunrize.immersiveengineering.api.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.wires.GlobalWireNetwork;
import blusunrize.immersiveengineering.api.wires.WireType;
import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlockEntity;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import ee.extras.EeExtras;
import ee.extras.cable.CableEnd;
import ee.extras.lantern.EeFloodlightBlock;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Прожектор IE на кабеле и кабельный преобразователь в настоящей симуляции EE (сервер с IE: IE=1):
 *   /rectest floods — батарея → соединитель EE → прожектор F1 (на полу, редстоун рядом, повёрнут) → F2 (на стене, инверсия)
 *                     → кабельный преобразователь C1 на конденсаторе IE → C2 на печном нагревателе IE. Кабель — кликами катушкой
 *   /rectest floodpower V — напряжение батареи (переменный ток 1 Гц)
 *   /rectest floodreport, floodoff (убрать редстоун у F1), floodcut (сломать F1 с выпадением), floodie (с проводом IE — отказ)
 */
final class Flood {
    static final BlockPos BAT = new BlockPos(0, -60, 90);
    static final BlockPos A = BAT.east(2);
    static final BlockPos F1 = BAT.east(8);
    static final BlockPos RS = F1.north();
    static final BlockPos WALL = BAT.east(14);
    static final BlockPos F2 = WALL.south();
    static final BlockPos CAP = BAT.east(20);
    static final BlockPos C1 = CAP.above();
    static final BlockPos HEATER = BAT.east(26);
    static final BlockPos C2 = HEATER.above();
    static final BlockPos IE_A = BAT.east(32);

    static BlockState ie(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("immersiveengineering", id)).defaultBlockState();
    }

    static void click(ServerLevel level, FakePlayer player, BlockPos pos) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevTest.place(level, BAT, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, true));
        DevTest.place(level, A, Extras.eeTermination());
        DevTest.place(level, F1, ie("floodlight").setValue(IEProperties.FACING_ALL, Direction.UP));
        DevTest.place(level, RS, Blocks.REDSTONE_BLOCK.defaultBlockState());
        DevTest.place(level, WALL, Blocks.STONE.defaultBlockState());
        DevTest.place(level, F2, ie("floodlight").setValue(IEProperties.FACING_ALL, Direction.SOUTH));
        DevTest.place(level, CAP, ie("capacitor_lv"));
        DevTest.place(level, C1, EeExtras.CABLE_CONVERTER.get().defaultBlockState().setValue(ee.extras.converter.CableConverterBlock.FACING, Direction.UP));
        DevTest.place(level, HEATER, ie("furnace_heater"));
        DevTest.place(level, C2, EeExtras.CABLE_CONVERTER.get().defaultBlockState().setValue(ee.extras.converter.CableConverterBlock.FACING, Direction.UP));
        if (level.getBlockEntity(F1) instanceof FloodlightBlockEntity be) {
            be.rotX = 22.5f;
            be.rotY = 45f;
            be.facing = Direction.EAST;
        }
        if (level.getBlockEntity(F2) instanceof FloodlightBlockEntity be) be.redstoneControlInverted = true;
        CableEnd a = Extras.end(level, A);
        DevTest.wire(level, BAT, 0, A, a.conductor(1).id());
        DevTest.wire(level, BAT, 1, A, a.conductor(2).id());
        // Устройства и узлы EE появляются на тике после установки: прокладка — позже (floodlay)
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 20, () -> lay(level, src)));
        return 1;
    }

    static void lay(ServerLevel level, CommandSourceStack src) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 16));
        BlockPos[] chain = {A, F1, F1, F2, F2, C1, C1, C2};
        for (BlockPos p : chain) click(level, player, p);
        src.sendSuccess(() -> Component.literal("кабель проложен, катушек осталось " + player.getItemInHand(InteractionHand.MAIN_HAND).getCount()), false);
    }

    static int power(CommandSourceStack src, double volts) {
        CreativeBatteryDevice b = DevicesSavedData.load(src.getLevel()).getDevice(BAT, CreativeBatteryDevice.class);
        if (b != null) {
            b.voltage = volts;
            b.acFrequency = 1;
        }
        src.sendSuccess(() -> Component.literal("батарея: " + volts + " В"), false);
        return 1;
    }

    static String device(ServerLevel level, BlockPos pos) {
        var d = DevicesSavedData.load(level).getDevice(pos, com.george_vi.electroenergetics.devices.device.SimulatedDevice.class);
        if (d == null) return "нет устройства";
        CompoundTag t = new CompoundTag();
        d.write(t);
        return t.toString();
    }

    static int wires(ServerLevel level, BlockPos pos) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        int wires = 0;
        for (InWorldNode n : sd.getNodes()) if (n.sourcePos().equals(pos)) wires += sd.getConnections(n).size();
        return wires;
    }

    static int report(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        StringBuilder out = new StringBuilder();
        for (BlockPos p : new BlockPos[] {F1, F2}) {
            BlockState st = level.getBlockState(p);
            out.append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
            if (st.getBlock() instanceof EeFloodlightBlock block) {
                out.append(" active=").append(st.getValue(IEProperties.ACTIVE)).append(' ').append(st.getValue(IEProperties.FACING_ALL))
                        .append(" свет ").append(st.getLightEmission(level, p)).append(" узел0 ").append(block.getNodePosition(level, p, st, 0));
                if (level.getBlockEntity(p) instanceof FloodlightBlockEntity be)
                    out.append(" BE rotX=").append(be.rotX).append(" rotY=").append(be.rotY).append(" facing=").append(be.facing)
                            .append(" inv=").append(be.redstoneControlInverted).append(" energy=").append(be.energyStorage)
                            .append(" огней=").append(be.fakeLights.size());
                out.append(' ').append(device(level, p));
            }
            out.append(" концов ").append(wires(level, p));
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(1))) out.append(" выпало ").append(e.getItem());
            out.append("\n");
        }
        for (BlockPos p : new BlockPos[] {C1, C2}) {
            BlockPos behind = p.below();
            IEnergyStorage s = null;
            for (Direction side : Direction.values())
                if (s == null) s = level.getCapability(Capabilities.EnergyStorage.BLOCK, behind, side);
            out.append("преобразователь ").append(device(level, p)).append(" концов ").append(wires(level, p)).append(" → ")
                    .append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(behind).getBlock()).getPath()).append(" FE ")
                    .append(s == null ? "нет" : s.getEnergyStored() + "/" + s.getMaxEnergyStored()).append("\n");
        }
        src.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    static int redstoneOff(CommandSourceStack src) {
        src.getLevel().removeBlock(RS, false);
        src.sendSuccess(() -> Component.literal("редстоун убран"), false);
        return 1;
    }

    static int cut(CommandSourceStack src) {
        src.getLevel().destroyBlock(F1, true);
        src.sendSuccess(() -> Component.literal("F1 сломан"), false);
        return 1;
    }

    /** Прожектор с проводом IE катушкой EE не превращается */
    static int ieWired(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos p1 = IE_A, p2 = IE_A.east(2);
        DevTest.place(level, p1, ie("floodlight"));
        DevTest.place(level, p2, ie("floodlight"));
        GlobalWireNetwork net = GlobalWireNetwork.getNetwork(level);
        net.addConnection(new Connection(WireType.COPPER, new ConnectionPoint(p1, 0), new ConnectionPoint(p2, 0), net));
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 16));
        click(level, player, p1);
        List<String> ids = List.of(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p1).getBlock()).toString());
        src.sendSuccess(() -> Component.literal("с проводом IE: " + ids), false);
        return 1;
    }

    private Flood() {
    }
}
