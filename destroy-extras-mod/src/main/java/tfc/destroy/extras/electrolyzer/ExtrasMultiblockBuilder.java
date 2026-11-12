package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistrationBuilder;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockLogic;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;
import tfc.destroy.extras.DestroyExtras;

/** Строитель регистрации мультиблока IE, как у Advanced TFC Tech: capability регистрируются позже, на шине мода. */
public final class ExtrasMultiblockBuilder<S extends IMultiblockState>
    extends MultiblockRegistrationBuilder<S, ExtrasMultiblockBuilder<S>> {
    private static final List<Consumer<IEventBus>> MOD_BUS = new ArrayList<>();

    public ExtrasMultiblockBuilder(IMultiblockLogic<S> logic, String name) {
        super(logic, DestroyExtras.rl(name));
    }

    public MultiblockRegistration<S> build() {
        return super.build(MOD_BUS::add);
    }

    @Override
    protected ExtrasMultiblockBuilder<S> self() {
        return this;
    }

    public static void registerOnModBus(IEventBus modBus) {
        MOD_BUS.forEach(registration -> registration.accept(modBus));
    }
}
