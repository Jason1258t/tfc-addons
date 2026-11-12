package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.common.gui.IEContainerMenu;
import blusunrize.immersiveengineering.common.gui.sync.GenericContainerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import tfc.destroy.extras.DestroyExtras;

/**
 * Окно электролизёра в стиле машин IE (как у ферментера): пять баков, слот мембраны, прогресс партии, питание и
 * состояние. Баки синхронизируются штатным {@link GenericContainerData#fluid} IE — вместе с составом смесей Destroy.
 */
public final class ElectrolyzerMenu extends IEContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 176;
    public static final int MEMBRANE_X = 73;
    public static final int MEMBRANE_Y = 21;

    public final FluidTank input;
    public final FluidTank mixture;
    public final FluidTank cathodeGas;
    public final FluidTank anodeGas;
    public final FluidTank catholyte;
    private int progress;
    private int duration;
    private int status;
    private int membraneLeft;
    private int blocked = -1;
    private float voltage;
    private float current;

    public static ElectrolyzerMenu makeServer(MenuType<?> type, int id, Inventory inventory,
                                              MultiblockMenuContext<ElectrolyzerLogic.State> ctx) {
        ElectrolyzerLogic.State state = ctx.mbContext().getState();
        return new ElectrolyzerMenu(multiblockCtx(type, id, ctx), inventory, state, state.membrane,
            state.input, state.mixture, state.cathodeGas, state.anodeGas, state.catholyte);
    }

    public static ElectrolyzerMenu makeClient(MenuType<?> type, int id, Inventory inventory) {
        return new ElectrolyzerMenu(clientCtx(type, id), inventory, null, new ItemStackHandler(1),
            new FluidTank(ElectrolyzerLogic.INPUT_CAPACITY), new FluidTank(ElectrolyzerLogic.MIXTURE_CAPACITY),
            new FluidTank(ElectrolyzerLogic.OUTPUT_CAPACITY), new FluidTank(ElectrolyzerLogic.OUTPUT_CAPACITY),
            new FluidTank(ElectrolyzerLogic.OUTPUT_CAPACITY));
    }

    private ElectrolyzerMenu(MenuContext ctx, Inventory inventory, ElectrolyzerLogic.State state, IItemHandler membrane,
                             FluidTank input, FluidTank mixture, FluidTank cathodeGas, FluidTank anodeGas, FluidTank catholyte) {
        super(ctx);
        this.input = input;
        this.mixture = mixture;
        this.cathodeGas = cathodeGas;
        this.anodeGas = anodeGas;
        this.catholyte = catholyte;
        addSlot(new SlotItemHandler(membrane, 0, MEMBRANE_X, MEMBRANE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(DestroyExtras.MEMBRANE_ITEM.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        ownSlotCount = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 94 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 152));

        for (FluidTank tank : new FluidTank[] {input, mixture, cathodeGas, anodeGas, catholyte}) {
            addGenericData(GenericContainerData.fluid(tank));
        }
        // На сервере значения читаются из состояния машины, на клиенте — записываются синхронизацией.
        addGenericData(GenericContainerData.int32(() -> state != null ? state.progress : progress, v -> progress = v));
        addGenericData(GenericContainerData.int32(() -> state != null ? duration(state) : duration, v -> duration = v));
        addGenericData(GenericContainerData.int32(() -> state != null ? state.status().ordinal() : status, v -> status = v));
        addGenericData(GenericContainerData.int32(() -> state != null ? state.remainingMembraneOperations() : membraneLeft,
            v -> membraneLeft = v));
        addGenericData(GenericContainerData.int32(() -> state != null ? (state.blocked != null ? state.blocked.ordinal() : -1) : blocked,
            v -> blocked = v));
        addGenericData(GenericContainerData.float32(() -> state != null ? (float) state.voltage() : voltage, v -> voltage = v));
        addGenericData(GenericContainerData.float32(() -> state != null ? (float) state.current() : current, v -> current = v));
    }

    private static int duration(ElectrolyzerLogic.State state) {
        RecipeHolder<?> recipe = state.recipe();
        return recipe != null && recipe.value() instanceof tfc.destroy.extras.recipe.MembraneElectrolysisRecipe value
            ? value.getProcessingDuration() : 0;
    }

    public float progressFraction() {
        return duration > 0 ? Math.min(1.0F, (float) progress / duration) : 0.0F;
    }

    public ElectrolyzerStatus status() {
        ElectrolyzerStatus[] values = ElectrolyzerStatus.values();
        return status >= 0 && status < values.length ? values[status] : ElectrolyzerStatus.IDLE;
    }

    public Component statusText() {
        var ports = tfc.destroy.extras.recipe.MembraneElectrolysisRecipe.Port.values();
        return ElectrolyzerLogic.statusText(status(), blocked >= 0 && blocked < ports.length ? ports[blocked] : null);
    }

    public int membraneLeft() {
        return membraneLeft;
    }

    public float voltage() {
        return voltage;
    }

    public float current() {
        return current;
    }

    /** Окно для щелчка по машине; позиция щелчка — чтобы окно закрывалось, если отойти дальше 8 блоков. */
    public static MenuProvider provider(MultiblockMenuContext<ElectrolyzerLogic.State> ctx) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.destroy_extras.electrolyzer");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return makeServer(DestroyExtras.ELECTROLYZER_MENU.get(), id, inventory, ctx);
            }
        };
    }
}
