package tfc.destroy.extras.gas;

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

final class GasTankLogic implements IMultiblockLogic<GasTankLogic.State> {
    // При концентрации 1 моль/л один мБ смеси содержит один миллимоль.
    private static final double PASCAL_LITERS_PER_MILLIMOLE = 8.3145 * 293;
    private static final int PASCALS_PER_BAR = 100_000;
    private static final List<String> SUPPORTED_GASES = List.of("destroy:hydrogen", "destroy:chlorine");
    private static final RelativeBlockFace FRONT = RelativeBlockFace.from(
        new MultiblockOrientation(Direction.NORTH, false), Direction.SOUTH);

    private final GasTankMultiblock.Size size;
    private final GasTankMultiblock.Material material;
    private final int capacity;

    GasTankLogic(GasTankMultiblock.Size size, GasTankMultiblock.Material material) {
        this.size = size;
        this.material = material;
        capacity = (int) Math.floor(material.limitBar * (double) PASCALS_PER_BAR * size.liters
            / PASCAL_LITERS_PER_MILLIMOLE);
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
    public ItemInteractionResult click(IMultiblockContext<State> context, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit, boolean isClient) {
        if (!player.getItemInHand(hand).isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!isClient) {
            FluidStack gas = context.getState().tank.getFluid();
            double bar = (gas.getAmount() * PASCAL_LITERS_PER_MILLIMOLE / size.liters) / PASCALS_PER_BAR;
            player.displayClientMessage(Component.translatable("message.destroy_extras.gas_tank",
                gas.isEmpty() ? Component.translatable("message.destroy_extras.gas_tank.empty") : gas.getHoverName(),
                gas.getAmount(), capacity, String.format(Locale.ROOT, "%.2f", bar), material.limitBar, size.liters), true);
        }
        return ItemInteractionResult.sidedSuccess(isClient);
    }

    static String gasSpecies(FluidStack stack) {
        if (stack.isEmpty() || !DestroyFluids.isMixture(stack)) return "";
        CompoundTag mixture = stack.get(DestroyDataComponents.MIXTURE);
        if (mixture == null) return "";
        var contents = mixture.getList("Contents", Tag.TAG_COMPOUND);
        if (contents.size() != 1) return "";
        CompoundTag component = contents.getCompound(0);
        String species = component.getString("Molecule");
        if (!SUPPORTED_GASES.contains(species) || component.getFloat("Concentration") != 1.0f) return "";
        // Старые партии электролизёра выдавались без явного фазового признака.
        return !component.contains("Gaseous") || component.getFloat("Gaseous") == 1.0f ? species : "";
    }

    static final class State implements IMultiblockState {
        final FluidTank tank;
        final IFluidHandler in;
        final IFluidHandler out;

        State(int capacity, Runnable markDirty) {
            tank = new FluidTank(capacity, stack -> !gasSpecies(stack).isEmpty()) {
                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    String species = gasSpecies(resource);
                    if (species.isEmpty()) return 0;
                    FluidStack canonical = new FluidStack(resource.getFluid(), resource.getAmount());
                    CompoundTag mixture = new CompoundTag();
                    CompoundTag component = new CompoundTag();
                    component.putString("Molecule", species);
                    component.putFloat("Concentration", 1.0f);
                    component.putFloat("Gaseous", 1.0f);
                    var contents = new net.minecraft.nbt.ListTag();
                    contents.add(component);
                    mixture.put("Contents", contents);
                    mixture.putFloat("Temperature", 293);
                    mixture.putString("TranslationKey", "destroy_extras.fluid." + species.substring("destroy:".length()));
                    canonical.set(DestroyDataComponents.MIXTURE, mixture);
                    return super.fill(canonical, action);
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
