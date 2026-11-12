package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistrationBuilder;
import blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockBlockEntityDummy;
import blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockBlockEntityMaster;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableObject;
import tfc.destroy.extras.DestroyExtras;

/**
 * Блок-сущности электролизёра — наследники штатных IE с интерфейсом очков инженера Create: Create показывает строки,
 * только если сама блок-сущность его реализует. Регистрируются вместо IE через {@code defaultBEs(RegistrationMethod)}.
 */
public final class ElectrolyzerBlockEntities {
    private ElectrolyzerBlockEntities() {}

    /** Подменяет фабрику IE: под теми же именами (_master/_dummy) регистрируются наши типы. */
    public static MultiblockRegistrationBuilder.RegistrationMethod<BlockEntityType<?>> registration() {
        return new MultiblockRegistrationBuilder.RegistrationMethod<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends BlockEntityType<?>> Supplier<T> register(String name, Supplier<T> ieDefault) {
                boolean master = name.endsWith(MultiblockRegistrationBuilder.MASTER_BE_SUFFIX);
                return (Supplier<T>) DestroyExtras.BLOCK_ENTITIES.register(name, () -> master ? masterType() : dummyType());
            }
        };
    }

    private static BlockEntityType<Master> masterType() {
        MutableObject<BlockEntityType<Master>> type = new MutableObject<>();
        type.setValue(new BlockEntityType<>((pos, state) -> new Master(type.getValue(), pos, state),
            Set.of(DestroyExtras.ELECTROLYZER.block().get()), null));
        return type.getValue();
    }

    private static BlockEntityType<Dummy> dummyType() {
        MutableObject<BlockEntityType<Dummy>> type = new MutableObject<>();
        type.setValue(new BlockEntityType<>((pos, state) -> new Dummy(type.getValue(), pos, state),
            Set.of(DestroyExtras.ELECTROLYZER.block().get()), null));
        return type.getValue();
    }

    private static MultiblockRegistration<ElectrolyzerLogic.State> registration0() {
        return ElectrolyzerLogic.REGISTRATION;
    }

    public static final class Master extends MultiblockBlockEntityMaster<ElectrolyzerLogic.State> implements IHaveGoggleInformation {
        Master(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state, registration0());
        }

        @Override
        public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
            return ElectrolyzerGoggles.add(getHelper().getState(), tooltip, isPlayerSneaking);
        }
    }

    public static final class Dummy extends MultiblockBlockEntityDummy<ElectrolyzerLogic.State> implements IHaveGoggleInformation {
        Dummy(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state, registration0());
        }

        @Override
        public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
            return ElectrolyzerGoggles.add(getHelper().getState(), tooltip, isPlayerSneaking);
        }
    }
}
