package ee.extras.lantern;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlockEntity;
import blusunrize.immersiveengineering.common.register.IEBlockEntities;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Прожектор IE в сети EE: регистрация и превращение. Весь класс — только при загруженном IE (EeExtras зовёт init,
 * LanternConversion — convert, когда прожектор IE уже стоит в мире).
 */
public final class EeFloodlight {
    public static DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FloodlightDevice>> DEVICE;
    public static DeferredBlock<EeFloodlightBlock> BLOCK;

    public static void init(IEventBus modBus) {
        DEVICE = EeExtras.DEVICES.register("floodlight", () -> new SimulatedDeviceType<FloodlightDevice>(EeExtras.rl("floodlight"),
                (type, level, pos, devices) -> new FloodlightDevice(level, pos, devices, type)));
        BLOCK = EeExtras.BLOCKS.register("floodlight", EeFloodlightBlock::new);
        // Блок-сущность — сама IE (FloodlightBlockEntity с типом FLOODLIGHT): добавляем свой блок в допустимые для типа
        modBus.addListener((BlockEntityTypeAddBlocksEvent e) -> e.modify(IEBlockEntities.FLOODLIGHT.get(), BLOCK.get()));
    }

    /** Заменить прожектор IE своим блоком: положение, поворот луча и инверсия редстоуна сохраняются */
    static void convert(ServerLevel level, BlockPos pos, BlockState old) {
        Direction facing = Direction.NORTH;
        float rotX = 0, rotY = 0;
        boolean inverted = false;
        if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity be) {
            facing = be.facing;
            rotX = be.rotX;
            rotY = be.rotY;
            inverted = be.redstoneControlInverted;
        }
        BlockState state = BLOCK.get().defaultBlockState()
                .setValue(IEProperties.FACING_ALL, old.getValue(IEProperties.FACING_ALL))
                .setValue(BlockStateProperties.WATERLOGGED, old.getValue(BlockStateProperties.WATERLOGGED));
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity be) {
            be.facing = facing;
            be.rotX = rotX;
            be.rotY = rotY;
            be.redstoneControlInverted = inverted;
            be.setChanged();
            be.markContainingBlockForUpdate(null);
        }
    }

    private EeFloodlight() {
    }
}
