package tfc.destroy.bridge.devtest;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.bridge.fluid.AtomicFluidOperation;
import tfc.destroy.bridge.fluid.SaltWaterAdapter;

/** Тестовый контроллер M1: настоящая подача, преобразование 1:1 и одна атомарная партия с тремя выходами. */
public final class ProcessBlockEntity extends BlockEntity {
    public static final int BATCH = 1000;
    public static final int DURATION = 200;
    public static final int OUTPUT_CAPACITY = 4000;

    public final FluidTank input = tank(2000, SaltWaterAdapter::isSaltWater);
    public final FluidTank mixture = tank(2000, stack -> true);
    public final FluidTank hydrogen = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank chlorine = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank sodiumHydroxide = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank[] outputs = {hydrogen, chlorine, sodiumHydroxide};

    private ProcessPowerDevice device;
    public int progress;
    public int batches;
    public int savedProgress = -1;
    public int savedBatches = -1;
    public FluidStack savedMixture = FluidStack.EMPTY;

    public ProcessBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyTest.PROCESS_BE.get(), pos, state);
    }

    private FluidTank tank(int capacity, Predicate<FluidStack> validator) {
        return new FluidTank(capacity, validator) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        transferFromSource(serverLevel);
        adaptInput();
        if (device == null || !device.isValid()) {
            device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, ProcessPowerDevice.class);
        }
        if (device == null) return;

        FluidStack consumed = mixture.getFluid().copyWithAmount(BATCH);
        FluidStack[] products = products();
        boolean ready = AtomicFluidOperation.canExecute(mixture, consumed, outputs, products);
        device.setActive(ready);
        if (!ready || !device.hasWorkingPower()) return;

        progress++;
        if (progress < DURATION) {
            setChanged();
            return;
        }
        if (!AtomicFluidOperation.execute(mixture, consumed, outputs, products)) {
            throw new IllegalStateException("Проверенная операция M1 не выполнилась");
        }
        progress = 0;
        batches++;
        setChanged();
    }

    private void transferFromSource(ServerLevel serverLevel) {
        BlockPos sourcePos = worldPosition.north(2);
        IFluidHandler source = serverLevel.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
        if (source != null) FluidUtil.tryFluidTransfer(input, source, BATCH, true);
    }

    private void adaptInput() {
        if (input.getFluidAmount() < BATCH) return;
        FluidStack consumed = new FluidStack(net.dries007.tfc.common.fluids.TFCFluids.SALT_WATER.getSource(), BATCH);
        // Только тестовый состав M1; рецептурная концентрация будет задана JSON на M2.
        FluidStack adapted = SaltWaterAdapter.toDestroyMixture(consumed, 1, 10, "destroy_bridge.devtest.salt_water");
        AtomicFluidOperation.execute(input, consumed, new FluidTank[] {mixture}, new FluidStack[] {adapted});
    }

    public static FluidStack[] products() {
        return new FluidStack[] {gas(DestroyMolecules.HYDROGEN, 250, "hydrogen"),
            gas(DestroyMolecules.CHLORINE, 250, "chlorine"), sodiumHydroxide(500)};
    }

    private static FluidStack gas(petrolpark.mc.destroy.chemistry.legacy.LegacySpecies molecule, int amount, String name) {
        LegacyMixture mixture = new LegacyMixture().addMolecule(molecule, 1);
        return MixtureFluid.gasOf(MixtureFluid.of(amount, mixture, "destroy_bridge.devtest." + name));
    }

    private static FluidStack sodiumHydroxide(int amount) {
        LegacyMixture mixture = new LegacyMixture()
            .addMolecule(DestroyMolecules.SODIUM_ION, 1)
            .addMolecule(DestroyMolecules.HYDROXIDE, 1)
            .addMolecule(DestroyMolecules.WATER, 4);
        return MixtureFluid.of(amount, mixture, "destroy_bridge.devtest.sodium_hydroxide");
    }

    public String status() {
        if (device == null) return "FAULT";
        FluidStack consumed = mixture.getFluid().copyWithAmount(BATCH);
        if (!AtomicFluidOperation.canExecute(mixture, consumed, outputs, products())) {
            return mixture.getFluidAmount() >= BATCH ? "BLOCKED_OUTPUT" : "IDLE";
        }
        return device.hasWorkingPower() ? "RUNNING" : "WAITING_FOR_POWER";
    }

    public ProcessPowerDevice device() {
        return device;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("Mixture", mixture.writeToNBT(registries, new CompoundTag()));
        tag.put("Hydrogen", hydrogen.writeToNBT(registries, new CompoundTag()));
        tag.put("Chlorine", chlorine.writeToNBT(registries, new CompoundTag()));
        tag.put("SodiumHydroxide", sodiumHydroxide.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putInt("SavedProgress", savedProgress);
        tag.putInt("SavedBatches", savedBatches);
        if (!savedMixture.isEmpty()) tag.put("SavedMixture", savedMixture.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.readFromNBT(registries, tag.getCompound("Input"));
        mixture.readFromNBT(registries, tag.getCompound("Mixture"));
        hydrogen.readFromNBT(registries, tag.getCompound("Hydrogen"));
        chlorine.readFromNBT(registries, tag.getCompound("Chlorine"));
        sodiumHydroxide.readFromNBT(registries, tag.getCompound("SodiumHydroxide"));
        progress = tag.getInt("Progress");
        batches = tag.getInt("Batches");
        savedProgress = tag.getInt("SavedProgress");
        savedBatches = tag.getInt("SavedBatches");
        savedMixture = FluidStack.parseOptional(registries, tag.getCompound("SavedMixture"));
    }
}
