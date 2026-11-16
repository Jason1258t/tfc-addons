package tfc.destroy.extras.gas;

import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IServerTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IInitialMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockLogic;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.CapabilityPosition;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockOrientation;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.ShapeType;
import blusunrize.immersiveengineering.common.fluids.ArrayFluidHandler;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;

final class GasTankLogic implements IMultiblockLogic<GasTankLogic.State>, IServerTickableComponent<GasTankLogic.State> {
    // При концентрации 1 моль/л один мБ смеси содержит один миллимоль.
    private static final double PASCAL_LITERS_PER_MILLIMOLE = 8.3145 * 293;
    private static final int PASCALS_PER_BAR = 100_000;
    private static final List<String> SUPPORTED_GASES = List.of("destroy:hydrogen", "destroy:chlorine", "destroy:nitrogen", "destroy:oxygen");
    private static final RelativeBlockFace FRONT = RelativeBlockFace.from(
        new MultiblockOrientation(Direction.NORTH, false), Direction.SOUTH);

    private final GasTankMultiblock.Size size;
    private final GasTankMultiblock.Material material;
    private final int capacity;

    GasTankLogic(GasTankMultiblock.Size size, GasTankMultiblock.Material material) {
        this.size = size;
        this.material = material;
        capacity = capacity(size.liters, material.limitBar);
    }

    @Override
    public State createInitialState(IInitialMultiblockContext<State> context) {
        return new State(capacity, context.getMarkDirtyRunnable());
    }

    @Override
    public Function<BlockPos, VoxelShape> shapeGetter(ShapeType type) {
        return pos -> Shapes.block();
    }

    @Override
    public void registerCapabilities(CapabilityRegistrar<State> registrar) {
        BlockPos controller = size.controller;
        registrar.registerAt(Capabilities.FluidHandler.BLOCK,
            new CapabilityPosition(controller.getX(), controller.getY(), controller.getZ(), FRONT), state -> state.in);
        registrar.registerAt(Capabilities.FluidHandler.BLOCK,
            new CapabilityPosition(controller.getX(), controller.getY() + 1, controller.getZ(), FRONT), state -> state.out);
    }

    @Override
    public void tickServer(IMultiblockContext<State> context) {
        State state = context.getState();
        if (state.tank.isEmpty() || !(context.getLevel().getRawLevel() instanceof ServerLevel level)
            || !context.getLevel().shouldTickModulo(10)) return;
        BlockPos outlet = size.controller.above();
        BlockPos port = context.getLevel().toAbsolute(outlet);
        BlockPos front = context.getLevel().toAbsolute(outlet.south()).subtract(port);
        Direction toward = Direction.fromDelta(front.getX(), front.getY(), front.getZ());
        if (toward != null) {
            GasPipeBlock.pushIntoPipe(level, port, toward, state.out, context.getLevel().toAbsolute(size.controller));
        }
    }

    @Override
    public ItemInteractionResult click(IMultiblockContext<State> context, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit, boolean isClient) {
        if (!player.getItemInHand(hand).isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!isClient) {
            FluidStack gas = context.getState().tank.getFluid();
            player.displayClientMessage(Component.translatable("message.destroy_extras.gas_tank",
                gas.isEmpty() ? Component.translatable("message.destroy_extras.gas_tank.empty") : gas.getHoverName(),
                quantity(gas), capacity, pressure(gas, size.liters), material.limitBar, size.liters), true);
        }
        return ItemInteractionResult.sidedSuccess(isClient);
    }

    static int capacity(int liters, int limitBar) {
        return (int) Math.floor(limitBar * (double) PASCALS_PER_BAR * liters / PASCAL_LITERS_PER_MILLIMOLE);
    }

    static String pressure(FluidStack gas, int liters) {
        double bar = millimoles(gas) * PASCAL_LITERS_PER_MILLIMOLE / liters / PASCALS_PER_BAR;
        return String.format(Locale.ROOT, "%.2f", bar);
    }

    static FluidStack gasStack(String species, int amount, float concentration) {
        FluidStack canonical = new FluidStack(DestroyFluids.MIXTURE.get().getSource(), amount);
        CompoundTag mixture = new CompoundTag();
        CompoundTag component = new CompoundTag();
        component.putString("Molecule", species);
        component.putFloat("Concentration", concentration);
        component.putFloat("Gaseous", 1.0f);
        var contents = new net.minecraft.nbt.ListTag();
        contents.add(component);
        mixture.put("Contents", contents);
        mixture.putFloat("Temperature", 293);
        mixture.putString("TranslationKey", "destroy_extras.fluid." + species.substring("destroy:".length()));
        canonical.set(DestroyDataComponents.MIXTURE, mixture);
        return canonical;
    }

    static boolean supports(String species) {
        return SUPPORTED_GASES.contains(species);
    }

    static String gasSpecies(FluidStack stack) {
        if (stack.isEmpty() || !DestroyFluids.isMixture(stack)) return "";
        CompoundTag mixture = stack.get(DestroyDataComponents.MIXTURE);
        if (mixture == null) return "";
        var contents = mixture.getList("Contents", Tag.TAG_COMPOUND);
        if (contents.size() != 1) return "";
        CompoundTag component = contents.getCompound(0);
        String species = component.getString("Molecule");
        float concentration = component.getFloat("Concentration");
        if (!supports(species) || !Float.isFinite(concentration) || concentration <= 0) return "";
        // Старые партии электролизёра выдавались без явного фазового признака.
        return !component.contains("Gaseous") || component.getFloat("Gaseous") == 1.0f ? species : "";
    }

    static float concentration(FluidStack gas) {
        var mixture = gas.get(DestroyDataComponents.MIXTURE);
        return mixture == null ? 0 : mixture.getList("Contents", Tag.TAG_COMPOUND).getCompound(0).getFloat("Concentration");
    }

    static double millimoles(FluidStack gas) {
        return gas.getAmount() * (double) concentration(gas);
    }

    static String quantity(FluidStack gas) {
        double amount = millimoles(gas);
        return String.format(Locale.ROOT, amount == Math.rint(amount) ? "%.0f" : "%.2f", amount);
    }

    static int accepted(FluidStack stored, FluidStack incoming, int capacity) {
        String species = gasSpecies(incoming);
        if (species.isEmpty() || !stored.isEmpty() && !gasSpecies(stored).equals(species)) return 0;
        double allowed = Math.floor(Math.max(0, capacity - millimoles(stored)) / concentration(incoming));
        return (int) Math.min(Math.min(allowed, incoming.getAmount()), Integer.MAX_VALUE - stored.getAmount());
    }

    static FluidStack add(FluidStack stored, FluidStack incoming, int accepted) {
        int total = stored.getAmount() + accepted;
        double moles = millimoles(stored) + accepted * (double) concentration(incoming);
        float concentration = (float) (moles / total);
        if (concentration * (double) total > moles) concentration = Math.nextDown(concentration);
        return gasStack(gasSpecies(incoming), total, concentration);
    }

    static final class State implements IMultiblockState {
        final FluidTank tank;
        final IFluidHandler in;
        final IFluidHandler out;

        State(int capacity, Runnable markDirty) {
            tank = new FluidTank(capacity, stack -> !gasSpecies(stack).isEmpty()) {
                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    int accepted = accepted(getFluid(), resource, getCapacity());
                    if (accepted > 0 && action.execute()) {
                        setFluid(add(getFluid(), resource, accepted));
                        markDirty.run();
                    }
                    return accepted;
                }

                @Override
                protected void onContentsChanged() {
                    markDirty.run();
                }
            };
            in = ArrayFluidHandler.fillOnly(tank, () -> {});
            out = ArrayFluidHandler.drainOnly(tank, () -> {});
        }

        @Override
        public void writeSaveNBT(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("gas", tank.writeToNBT(registries, new CompoundTag()));
        }

        @Override
        public void readSaveNBT(CompoundTag tag, HolderLookup.Provider registries) {
            tank.readFromNBT(registries, tag.getCompound("gas"));
        }
    }
}
