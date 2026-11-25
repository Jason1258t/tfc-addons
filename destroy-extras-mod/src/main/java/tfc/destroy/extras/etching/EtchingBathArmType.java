package tfc.destroy.extras.etching;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.TopFaceArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.extras.DestroyExtras;

/** Точка механической руки: установка заготовок и съём готовых плат сверху. */
public final class EtchingBathArmType extends ArmInteractionPointType {
    private static final DeferredRegister<ArmInteractionPointType> TYPES =
        DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, DestroyExtras.MOD_ID);
    static { TYPES.register("etching_bath", EtchingBathArmType::new); }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.is(DestroyExtras.ETCHING_BATH_BLOCK.get());
    }
    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new TopFaceArmInteractionPoint(this, level, pos, state);
    }
}
