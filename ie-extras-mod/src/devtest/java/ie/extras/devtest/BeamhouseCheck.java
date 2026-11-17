package ie.extras.devtest;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.TemplateMultiblock;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockOrientation;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

final class BeamhouseCheck {
    static boolean verify(ServerLevel level, Player player, BlockPos origin, Direction front, boolean mirror) {
        var mb = MultiblockHandler.getByUniqueName(id("advancedtfctech:multiblocks/beamhouse"));
        if (mb == null) return fail("барабан не зарегистрирован");
        for (var entry : mb.getStructure(level)) {
            var state = entry.state();
            if (mirror) state = state.mirror(Mirror.FRONT_BACK);
            var rotation = switch (front) {
                case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
                case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
                case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                default -> net.minecraft.world.level.block.Rotation.NONE;
            };
            level.setBlockAndUpdate(pos(origin, entry.pos(), mirror, front), state.rotate(rotation));
        }
        var trigger = pos(origin, mb.getTriggerOffset(), mirror, front);
        if (!mb.createStructure(level, trigger, front.getOpposite(), player)) return fail("структура не собралась");
        var orientation = new MultiblockOrientation(front, mirror);
        var inputPos = pos(origin, new BlockPos(3, 0, 1), mirror, front);
        if (level.getBlockState(inputPos).getValue(IEProperties.MIRRORED) != mirror) return fail("не совпало зеркальное отражение");
        var inputSide = RelativeBlockFace.LEFT.forFront(orientation);
        var input = level.getCapability(Capabilities.ItemHandler.BLOCK, inputPos, inputSide);
        var fluid = level.getCapability(Capabilities.FluidHandler.BLOCK,
                pos(origin, new BlockPos(2, 0, 3), mirror, front), front.getOpposite());
        var hide = new ItemStack(BuiltInRegistries.ITEM.get(id("tfc:large_raw_hide")), 2);
        var stone = new ItemStack(Items.STONE);
        if (input == null || fluid == null) {
            return fail("обработчики: вход=" + (input != null) + ", жидкость=" + (fluid != null));
        }
        if (hide.isEmpty() || input.insertItem(0, stone, false).getCount() != 1
                || !input.insertItem(0, hide, true).isEmpty() || !input.insertItem(0, hide, false).isEmpty()) {
            return fail("фильтр или приём шкур");
        }
        if (!input.extractItem(0, 1, true).isEmpty()) {
            return fail("направление передачи предметов");
        }
        if (fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) != 1000
                || level.getCapability(Capabilities.FluidHandler.BLOCK, inputPos, inputSide) != null) {
            return fail("жидкостный вход");
        }
        return true;
    }

    private static boolean fail(String detail) {
        System.out.println("Кожевенный барабан: " + detail);
        return false;
    }

    private static BlockPos pos(BlockPos origin, BlockPos pos, boolean mirror, Direction front) {
        return TemplateMultiblock.withSettingsAndOffset(origin, pos, mirror, front);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
