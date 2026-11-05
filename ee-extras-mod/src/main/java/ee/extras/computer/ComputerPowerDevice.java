package ee.extras.computer;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;

public final class ComputerPowerDevice extends BundledWireTerminationDevice {
    private static final double NOMINAL = 300;
    private static final double OPEN = 1e6;
    private static final int WINDOW = 20;
    private final double[] voltageSquares = new double[WINDOW];
    private double sumSquares;
    private int nextSample;
    private double resistance = OPEN;
    private double power;
    private long sampleTick = Long.MIN_VALUE;
    private long loadUntil = Long.MIN_VALUE;
    private int loadWatts;
    private int loadedSamples;
    private int surgeTicks;
    private boolean failed;
    private ItemStack pocket = ItemStack.EMPTY;

    public ComputerPowerDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public void requestLoad(int watts) {
        loadWatts = watts;
        loadUntil = level.getGameTime() + 2;
    }

    public boolean hasPower() {
        long now = level.getGameTime();
        double meanSquare = meanSquare();
        return !failed && sampleTick >= now - 2 && loadedSamples == WINDOW && meanSquare >= 240 * 240
                && meanSquare <= 330 * 330;
    }

    @Override
    public void preTick(BridgeCollector collector) {
        resistance = OPEN;
        if (!failed && level.isLoaded(pos)
                && level.getBlockState(pos).getBlock() instanceof ComputerPowerBlock block) {
            int watts = block.isCharger()
                    ? (pocket.isEmpty() || PocketCharge.get(pocket) == PocketCharge.CAPACITY ? 0 : 40)
                    : (level.getGameTime() <= loadUntil ? loadWatts : 2);
            if (watts > 0) resistance = NOMINAL * NOMINAL / watts;
        }
        collector.builder(pos).resistor(1, 2, resistance);
    }

    @Override
    public void postTick(SimulationResults results) {
        long now = level.getGameTime();
        if (sampleTick < now - 2) {
            Arrays.fill(voltageSquares, 0);
            sumSquares = 0;
            nextSample = 0;
            loadedSamples = 0;
            surgeTicks = 0;
        }
        sampleTick = now;
        double square = results.getVoltageAtSqr(pos, 1, 2);
        if (!Double.isFinite(square)) square = 0;
        sumSquares += square - voltageSquares[nextSample];
        voltageSquares[nextSample] = square;
        nextSample = (nextSample + 1) % WINDOW;
        double meanSquare = meanSquare();
        power = resistance == OPEN ? 0 : square / resistance;
        if (resistance < NOMINAL * NOMINAL / 2) loadedSamples = Math.min(WINDOW, loadedSamples + 1);
        else loadedSamples = 0;

        surgeTicks = meanSquare > 390 * 390 ? surgeTicks + 1 : 0;
        if (!failed && surgeTicks >= 10 && level instanceof ServerLevel server && level.isLoaded(pos)
                && CEEConfigs.server().componentDamage.get()) {
            failed = true;
            level.levelEvent(2004, pos, 0);
            if (meanSquare >= 500 * 500) server.getServer().execute(() -> burnDesktop(server));
        }

        if (!failed && !pocket.isEmpty() && hasPower() && power > 0) {
            PocketCharge.set(pocket, PocketCharge.get(pocket) + (int) Math.min(65, power * 0.85));
        }
    }

    private double meanSquare() {
        return Math.max(0, sumSquares / WINDOW);
    }

    private void burnDesktop(ServerLevel server) {
        if (!server.isLoaded(pos) || deviceSD.getDevice(pos) != this) return;
        BlockState inlet = server.getBlockState(pos);
        if (!(inlet.getBlock() instanceof ComputerPowerBlock block) || block.isCharger()) return;
        BlockPos desktop = pos.relative(inlet.getValue(ComputerPowerBlock.FACING).getOpposite());
        if (!server.isLoaded(desktop)) return;
        var id = BuiltInRegistries.BLOCK.getKey(server.getBlockState(desktop).getBlock());
        if (id.getNamespace().equals("computercraft")
                && (id.getPath().equals("computer_normal") || id.getPath().equals("computer_advanced"))) {
            server.destroyBlock(desktop, false);
        }
    }

    public boolean insertPocket(ItemStack stack) {
        if (!pocket.isEmpty() || !PocketCharge.isPocket(stack)) return false;
        pocket = stack.copyWithCount(1);
        return true;
    }

    public ItemStack takePocket() {
        ItemStack stack = pocket;
        pocket = ItemStack.EMPTY;
        return stack;
    }

    public Component status() {
        if (failed) return Component.translatable("ee_extras.computer_power.failed");
        int voltage = (int) Math.round(Math.sqrt(meanSquare()));
        if (pocket.isEmpty()) return Component.translatable("ee_extras.computer_power.voltage", voltage);
        return Component.translatable("ee_extras.computer_power.charge",
                100 * PocketCharge.get(pocket) / PocketCharge.CAPACITY, voltage);
    }

    @Override
    public void read(CompoundTag tag) {
        failed = tag.getBoolean("Failed");
        pocket = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Pocket"));
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Failed", failed);
        tag.putDouble("InputVoltage", Math.sqrt(meanSquare()));
        tag.putDouble("InputPower", power);
        if (!pocket.isEmpty()) tag.put("Pocket", pocket.save(level.registryAccess()));
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
