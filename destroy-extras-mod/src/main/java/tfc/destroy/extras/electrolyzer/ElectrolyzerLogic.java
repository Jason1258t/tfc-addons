package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IServerTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IInitialMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockLogic;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.CapabilityPosition;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockOrientation;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.ShapeType;
import blusunrize.immersiveengineering.common.gui.IEContainerMenu.MultiblockMenuContext;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.bridge.fluid.AtomicFluidOperation;
import tfc.destroy.bridge.fluid.SaltWaterAdapter;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;
import tfc.destroy.extras.recipe.MembraneElectrolysisRecipe;

/**
 * Логика собранного электролизёра: рецепт из RecipeManager, партия целиком или никак (AtomicFluidOperation),
 * питание — нагрузка EE на клетке клемм. Порты заданы в координатах шаблона {@link ElectrolyzerMultiblock}.
 */
public final class ElectrolyzerLogic implements IMultiblockLogic<ElectrolyzerLogic.State>,
    IServerTickableComponent<ElectrolyzerLogic.State> {
    public static final int INPUT_CAPACITY = 8000;
    public static final int MIXTURE_CAPACITY = 2000;
    public static final int OUTPUT_CAPACITY = 4000;
    public static final int MEMBRANE_OPERATIONS = 64;

    /** Клетка с клеммами EE: верх, задний ряд, середина. */
    public static final BlockPos POWER = new BlockPos(1, 2, 0);
    public static final CapabilityPosition CATHODE_GAS_PORT = port(0, 2, 1, Direction.UP);
    public static final CapabilityPosition ANODE_GAS_PORT = port(2, 2, 1, Direction.UP);
    public static final CapabilityPosition CATHOLYTE_PORT = port(0, 0, 1, Direction.WEST);
    public static final CapabilityPosition INPUT_PORT = port(2, 0, 0, Direction.EAST);
    public static final CapabilityPosition MEMBRANE_PORT = port(1, 1, 1, Direction.SOUTH);

    public static final MultiblockRegistration<State> REGISTRATION = new ExtrasMultiblockBuilder<>(new ElectrolyzerLogic(), "electrolyzer")
        .notMirrored()
        .structure(() -> DestroyExtras.ELECTROLYZER_MULTIBLOCK)
        .defaultBEs(ElectrolyzerBlockEntities.registration())
        .customBlock(DestroyExtras.BLOCKS, DestroyExtras.ITEMS, ElectrolyzerPartBlock::new,
            block -> new net.minecraft.world.item.BlockItem(block, new net.minecraft.world.item.Item.Properties()))
        .build();

    /** Сторона клетки шаблона (машина не повёрнута: лицом к игроку на юге) → сторона относительно машины для IE. */
    private static CapabilityPosition port(int x, int y, int z, Direction templateSide) {
        RelativeBlockFace face = RelativeBlockFace.from(new MultiblockOrientation(Direction.NORTH, false), templateSide);
        return new CapabilityPosition(x, y, z, face);
    }

    @Override
    public State createInitialState(IInitialMultiblockContext<State> context) {
        return new State();
    }

    @Override
    public Function<BlockPos, VoxelShape> shapeGetter(ShapeType type) {
        return pos -> Shapes.block();
    }

    @Override
    public void registerCapabilities(CapabilityRegistrar<State> registrar) {
        registrar.register(Capabilities.FluidHandler.BLOCK, (state, position) -> {
            if (CATHODE_GAS_PORT.equals(position)) return state.cathodeGasOut;
            if (ANODE_GAS_PORT.equals(position)) return state.anodeGasOut;
            if (CATHOLYTE_PORT.equals(position)) return state.catholyteOut;
            if (INPUT_PORT.equals(position)) return state.inputIn;
            return null;
        });
        registrar.registerAt(Capabilities.ItemHandler.BLOCK, MEMBRANE_PORT, state -> state.membrane);
    }

    @Override
    public void dropExtraItems(State state, java.util.function.Consumer<ItemStack> drop) {
        ItemStack stack = state.membrane.getStackInSlot(0);
        if (!stack.isEmpty()) drop.accept(stack.copy());
    }

    @Override
    public ItemInteractionResult click(IMultiblockContext<State> context, BlockPos posInMultiblock, Player player,
                                       InteractionHand hand, BlockHitResult hit, boolean isClient) {
        if (isClient) return ItemInteractionResult.SUCCESS;
        State state = context.getState();
        ItemStack held = player.getItemInHand(hand);
        if (posInMultiblock.equals(ElectrolyzerMultiblock.CELL)) {
            ItemStack installed = state.membrane.getStackInSlot(0);
            if (held.is(DestroyExtras.MEMBRANE_ITEM.get())) {
                if (!state.membrane.isItemValid(0, held)) {
                    player.displayClientMessage(Component.translatable("message.destroy_extras.electrolyzer.membrane_worn"), true);
                    return ItemInteractionResult.SUCCESS;
                }
                // Изношенная или любая старая мембрана отдаётся игроку, новая встаёт на её место.
                if (!installed.isEmpty() && !player.addItem(installed.copy())) player.drop(installed.copy(), false);
                state.membrane.setStackInSlot(0, held.split(1));
                context.markMasterDirty();
                player.displayClientMessage(Component.translatable("message.destroy_extras.electrolyzer.membrane_installed",
                    state.remainingMembraneOperations()), true);
                return ItemInteractionResult.SUCCESS;
            }
            if (held.isEmpty() && player.isShiftKeyDown() && !installed.isEmpty()) {
                state.membrane.setStackInSlot(0, ItemStack.EMPTY);
                if (!player.addItem(installed.copy())) player.drop(installed.copy(), false);
                context.markMasterDirty();
                return ItemInteractionResult.SUCCESS;
            }
        }
        if (!held.isEmpty() && !held.is(DestroyExtras.MEMBRANE_ITEM.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        player.openMenu(ElectrolyzerMenu.provider(new MultiblockMenuContext<>(context,
            context.getLevel().toAbsolute(posInMultiblock))));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void tickServer(IMultiblockContext<State> context) {
        State state = context.getState();
        if (!(context.getLevel().getRawLevel() instanceof ServerLevel level)) return;
        ElectrolyzerStatus previous = state.status;
        state.tick(level, context.getLevel().toAbsolute(POWER));
        // Очкам нужны прогресс и баки: при смене состояния сразу, иначе раз в полсекунды, если что-то поменялось.
        Synced now = state.synced();
        if (state.status != previous || context.getLevel().shouldTickModulo(10) && !now.equals(state.lastSent)) {
            state.lastSent = now;
            context.requestMasterBESync();
        }
        if (state.dirty) {
            state.dirty = false;
            context.markMasterDirty();
        }
    }

    public static final class State implements IMultiblockState {
        // Вход принимает любую жидкость: есть ли для неё рецепт, решает машина (состояние «нет рецепта»).
        public final FluidTank input = tank(INPUT_CAPACITY, stack -> true);
        public final FluidTank mixture = tank(MIXTURE_CAPACITY, stack -> true);
        public final FluidTank cathodeGas = tank(OUTPUT_CAPACITY, stack -> true);
        public final FluidTank anodeGas = tank(OUTPUT_CAPACITY, stack -> true);
        public final FluidTank catholyte = tank(OUTPUT_CAPACITY, stack -> true);
        public final ItemStackHandler membrane = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(DestroyExtras.MEMBRANE_ITEM.get()) && stack.getDamageValue() < stack.getMaxDamage();
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                dirty = true;
            }
        };
        // Порты: выходы только отдают; вход принимает и отдаёт — жидкость без рецепта можно слить обратно.
        final IFluidHandler inputIn = new PortHandler(input, true, true);
        final IFluidHandler cathodeGasOut = new PortHandler(cathodeGas, false, true);
        final IFluidHandler anodeGasOut = new PortHandler(anodeGas, false, true);
        final IFluidHandler catholyteOut = new PortHandler(catholyte, false, true);

        boolean dirty;
        private ProcessPowerDevice device;
        private RecipeManager recipeManager;
        private RecipeHolder<MembraneElectrolysisRecipe> recipe;
        private ResourceLocation recipeId;
        private long nextRecipeScan;
        private FluidStack lastScanned = FluidStack.EMPTY;
        ElectrolyzerStatus status = ElectrolyzerStatus.IDLE;
        double voltage;
        double current;
        /** Какой выход не принимает партию (при BLOCKED_OUTPUT), иначе null. */
        MembraneElectrolysisRecipe.Port blocked;
        /** На клиенте — то, что прислал сервер (writeSyncNBT); на сервере не используется. */
        private Synced clientSynced = Synced.EMPTY;
        Synced lastSent;
        public int progress;
        public int batches;
        /** Тиков логики с загрузки (не сохраняется): по нему видно, тикает ли машина. */
        public long ticks;

        State() {}

        private FluidTank tank(int capacity, Predicate<FluidStack> validator) {
            return new FluidTank(capacity, validator) {
                @Override
                protected void onContentsChanged() {
                    dirty = true;
                }
            };
        }

        void tick(ServerLevel level, BlockPos powerPos) {
            ticks++;
            if (device == null || !device.isValid() || !device.pos.equals(powerPos)) {
                device = DevicesSavedData.load(level).getDevice(powerPos, ProcessPowerDevice.class);
            }
            if (device == null) {
                status = ElectrolyzerStatus.FAULT;
                return;
            }
            voltage = device.measuredVoltage();
            current = device.measuredCurrent();
            blocked = null;
            selectRecipe(level, level.getRecipeManager());
            if (recipe == null) {
                device.setActive(false);
                status = mixture.isEmpty() && input.isEmpty() ? ElectrolyzerStatus.IDLE : ElectrolyzerStatus.NO_RECIPE;
                return;
            }

            adaptInput();
            MembraneElectrolysisRecipe value = recipe.value();
            FluidStack consumed = mixture.getFluid().copyWithAmount(value.input().amount());
            FluidStack[] products = value.products();
            FluidTank[] targets = targets(value);
            boolean hasBatch = mixture.getFluidAmount() >= value.input().amount();
            boolean ready = hasBatch && AtomicFluidOperation.canExecute(mixture, consumed, targets, products);
            boolean membraneOk = !value.needsMembrane() || hasUsableMembrane();
            device.setActive(ready && membraneOk);

            if (!membraneOk) {
                status = ElectrolyzerStatus.FAULT;
            } else if (hasBatch && !ready) {
                status = ElectrolyzerStatus.BLOCKED_OUTPUT;
                blocked = blockedPort(value, targets, products);
            } else if (!hasBatch) {
                status = ElectrolyzerStatus.IDLE;
            } else if (!device.hasWorkingPower()) {
                status = ElectrolyzerStatus.WAITING_FOR_POWER;
            } else {
                status = ElectrolyzerStatus.RUNNING;
            }
            if (status != ElectrolyzerStatus.RUNNING) return;

            progress++;
            dirty = true;
            if (progress < value.getProcessingDuration()) return;
            if (!AtomicFluidOperation.execute(mixture, consumed, targets, products)) {
                throw new IllegalStateException("Проверенная операция электролиза не выполнилась");
            }
            if (value.needsMembrane()) {
                ItemStack stack = membrane.getStackInSlot(0).copy();
                stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
                membrane.setStackInSlot(0, stack);
            }
            progress = 0;
            batches++;
            status = ElectrolyzerStatus.IDLE;
        }

        private static MembraneElectrolysisRecipe.Port blockedPort(MembraneElectrolysisRecipe value, FluidTank[] targets,
                                                                   FluidStack[] products) {
            for (int i = 0; i < targets.length; i++) {
                if (targets[i].fill(products[i], IFluidHandler.FluidAction.SIMULATE) < products[i].getAmount()) return value.ports().get(i);
            }
            return null;
        }

        /** Бак каждого продукта по его порту в рецепте. */
        private FluidTank[] targets(MembraneElectrolysisRecipe value) {
            return value.ports().stream().map(port -> switch (port) {
                case CATHODE_GAS -> cathodeGas;
                case ANODE_GAS -> anodeGas;
                case CATHOLYTE -> catholyte;
            }).toArray(FluidTank[]::new);
        }

        private void selectRecipe(Level level, RecipeManager manager) {
            if (recipeManager != manager) {
                recipeManager = manager;
                recipe = null;
                nextRecipeScan = 0;
            }
            // Партия не начата и вход сменился — рецепт выбирается заново (машина ведёт разные реакции).
            if (recipe != null && mixture.isEmpty() && !input.isEmpty() && !matchesInput(recipe.value())) {
                recipe = null;
                recipeId = null;
                progress = 0;
            }
            // Новая жидкость на входе — искать сразу, иначе не чаще раза в секунду.
            boolean inputChanged = !FluidStack.isSameFluidSameComponents(input.getFluid(), lastScanned);
            if (recipe != null || !inputChanged && level.getGameTime() < nextRecipeScan) return;
            nextRecipeScan = level.getGameTime() + 20;
            lastScanned = input.getFluid().copy();
            for (RecipeHolder<MembraneElectrolysisRecipe> candidate : manager.getAllRecipesFor(ExtrasRecipeTypes.ELECTROLYSIS_TYPE.get())) {
                if (recipeId != null && !candidate.id().equals(recipeId)) continue;
                MembraneElectrolysisRecipe value = candidate.value();
                if (!mixture.isEmpty() && value.input().test(mixture.getFluid())) {
                    setRecipe(candidate);
                    return;
                }
                FluidStack adapted = adaptedInput(value);
                if (!adapted.isEmpty() && value.input().test(adapted)) {
                    setRecipe(candidate);
                    return;
                }
            }
        }

        private void setRecipe(RecipeHolder<MembraneElectrolysisRecipe> value) {
            if (recipeId != null && !recipeId.equals(value.id())) progress = 0;
            recipe = value;
            recipeId = value.id();
        }

        private boolean matchesInput(MembraneElectrolysisRecipe value) {
            return value.input().ingredient().test(input.getFluid())
                || value.acceptsSaltWater() && SaltWaterAdapter.isSaltWater(input.getFluid());
        }

        /** Порция входа для партии: сама жидкость, если рецепт её принимает, иначе солёная вода TFC → смесь Destroy. */
        private FluidStack adaptedInput(MembraneElectrolysisRecipe value) {
            if (input.getFluidAmount() < value.input().amount()) return FluidStack.EMPTY;
            FluidStack portion = input.getFluid().copyWithAmount(value.input().amount());
            if (value.input().test(portion)) return portion;
            if (!value.acceptsSaltWater()) return FluidStack.EMPTY;
            return SaltWaterAdapter.toDestroyMixture(input.getFluid().copyWithAmount(value.input().amount()),
                value.saltConcentration(), value.waterConcentration(), value.translationKey());
        }

        private void adaptInput() {
            MembraneElectrolysisRecipe value = recipe.value();
            FluidStack adapted = adaptedInput(value);
            if (adapted.isEmpty()) return;
            FluidStack consumed = input.getFluid().copyWithAmount(value.input().amount());
            AtomicFluidOperation.execute(input, consumed, new FluidTank[] {mixture}, new FluidStack[] {adapted});
        }

        /** Изменение снаружи логики (devtest, команды): сохранить мастер-блок на ближайшем тике. */
        public void markChanged() {
            dirty = true;
        }

        public boolean hasUsableMembrane() {
            ItemStack stack = membrane.getStackInSlot(0);
            return stack.is(DestroyExtras.MEMBRANE_ITEM.get()) && stack.getDamageValue() < stack.getMaxDamage();
        }

        public int remainingMembraneOperations() {
            ItemStack stack = membrane.getStackInSlot(0);
            return hasUsableMembrane() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
        }

        public ElectrolyzerStatus status() {
            return status;
        }

        public double voltage() {
            return voltage;
        }

        public double current() {
            return current;
        }

        public RecipeHolder<MembraneElectrolysisRecipe> recipe() {
            return recipe;
        }

        private int duration() {
            return recipe != null ? recipe.value().getProcessingDuration() : 0;
        }

        /** Сводка для очков: на сервере — живые значения, на клиенте — присланные. */
        public Synced synced() {
            if (recipeManager == null && clientSynced != Synced.EMPTY) return clientSynced;
            return new Synced(status, progress, duration(), remainingMembraneOperations(), (float) voltage, (float) current, blocked);
        }

        public ProcessPowerDevice device() {
            return device;
        }

        public ResourceLocation recipeId() {
            return recipeId;
        }

        @Override
        public void writeSaveNBT(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
            tag.put("Mixture", mixture.writeToNBT(registries, new CompoundTag()));
            tag.put("CathodeGas", cathodeGas.writeToNBT(registries, new CompoundTag()));
            tag.put("AnodeGas", anodeGas.writeToNBT(registries, new CompoundTag()));
            tag.put("Catholyte", catholyte.writeToNBT(registries, new CompoundTag()));
            tag.put("Membrane", membrane.serializeNBT(registries));
            tag.putInt("Progress", progress);
            tag.putInt("Batches", batches);
            if (recipeId != null) tag.putString("Recipe", recipeId.toString());
        }

        @Override
        public void readSaveNBT(CompoundTag tag, HolderLookup.Provider registries) {
            input.readFromNBT(registries, tag.getCompound("Input"));
            mixture.readFromNBT(registries, tag.getCompound("Mixture"));
            cathodeGas.readFromNBT(registries, tag.getCompound("CathodeGas"));
            anodeGas.readFromNBT(registries, tag.getCompound("AnodeGas"));
            catholyte.readFromNBT(registries, tag.getCompound("Catholyte"));
            membrane.deserializeNBT(registries, tag.getCompound("Membrane"));
            progress = tag.getInt("Progress");
            batches = tag.getInt("Batches");
            recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
            recipe = null;
            recipeManager = null;
            device = null;
            status = ElectrolyzerStatus.IDLE;
        }

        @Override
        public void writeSyncNBT(CompoundTag tag, HolderLookup.Provider registries) {
            Synced s = synced();
            tag.putString("Status", s.status().id());
            tag.putInt("Progress", s.progress());
            tag.putInt("Duration", s.duration());
            tag.putInt("Membrane", s.membrane());
            tag.putFloat("Voltage", s.voltage());
            tag.putFloat("Current", s.current());
            if (s.blocked() != null) tag.putString("Blocked", s.blocked().getSerializedName());
            tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
            tag.put("CathodeGas", cathodeGas.writeToNBT(registries, new CompoundTag()));
            tag.put("AnodeGas", anodeGas.writeToNBT(registries, new CompoundTag()));
            tag.put("Catholyte", catholyte.writeToNBT(registries, new CompoundTag()));
        }

        @Override
        public void readSyncNBT(CompoundTag tag, HolderLookup.Provider registries) {
            status = ElectrolyzerStatus.byId(tag.getString("Status"));
            MembraneElectrolysisRecipe.Port port = null;
            for (MembraneElectrolysisRecipe.Port p : MembraneElectrolysisRecipe.Port.values()) {
                if (p.getSerializedName().equals(tag.getString("Blocked"))) port = p;
            }
            clientSynced = new Synced(status, tag.getInt("Progress"), tag.getInt("Duration"), tag.getInt("Membrane"),
                tag.getFloat("Voltage"), tag.getFloat("Current"), port);
            input.readFromNBT(registries, tag.getCompound("Input"));
            cathodeGas.readFromNBT(registries, tag.getCompound("CathodeGas"));
            anodeGas.readFromNBT(registries, tag.getCompound("AnodeGas"));
            catholyte.readFromNBT(registries, tag.getCompound("Catholyte"));
        }
    }

    /** Что показывают очки инженера; на клиент приходит синхронизацией мастер-блока IE. */
    public record Synced(ElectrolyzerStatus status, int progress, int duration, int membrane, float voltage, float current,
                         MembraneElectrolysisRecipe.Port blocked) {
        static final Synced EMPTY = new Synced(ElectrolyzerStatus.IDLE, 0, 0, 0, 0, 0, null);

        /** Подпись состояния; при переполнении — с названием выхода («Выход переполнен: анодный газ»). */
        public Component text() {
            return statusText(status, blocked);
        }
    }

    public static Component statusText(ElectrolyzerStatus status, MembraneElectrolysisRecipe.Port blocked) {
        if (status != ElectrolyzerStatus.BLOCKED_OUTPUT || blocked == null) return status.text();
        return Component.translatable("status.destroy_extras.electrolyzer.blocked_port",
            Component.translatable("gui.destroy_extras.electrolyzer.port." + blocked.getSerializedName()));
    }

    /** Порт одного бака: что разрешено — приём и/или выдача. */
    private record PortHandler(FluidTank tank, boolean accepts, boolean gives) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return accepts && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return accepts ? tank.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return gives ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            return gives ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }
}
