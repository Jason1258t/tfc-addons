package tfc.destroy.extras.gas;

import blusunrize.immersiveengineering.api.multiblocks.BlockMatcher;
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import blusunrize.immersiveengineering.common.blocks.multiblocks.IETemplateMultiblock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.mutable.MutableObject;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ExtrasMultiblockBuilder;

public final class GasTankMultiblock extends IETemplateMultiblock {
    enum Size {
        COMPACT("compact", 1, 2, 500),
        MEDIUM("medium", 2, 3, 4000),
        LARGE("large", 3, 5, 16000);

        final String id;
        final BlockPos dimensions;
        final BlockPos controller;
        final int liters;

        Size(String id, int width, int height, int liters) {
            this.id = id;
            dimensions = new BlockPos(width, height, width);
            controller = new BlockPos((width - 1) / 2, 0, width - 1);
            this.liters = liters;
        }
    }

    enum Material {
        STEEL("steel", 2),
        STAINLESS_STEEL("stainless_steel", 4),
        BLUE_STEEL("blue_steel", 6);

        final String id;
        final int limitBar;

        Material(String id, int limitBar) {
            this.id = id;
            this.limitBar = limitBar;
        }
    }

    private static final BlockMatcher.MatcherPredicate SAME_BLOCK = (expected, found, level, pos) ->
        expected.getBlock() == found.getBlock() ? BlockMatcher.Result.allow(2) : BlockMatcher.Result.DEFAULT;

    private GasTankMultiblock(String id, Size size, MultiblockRegistration<GasTankLogic.State> registration) {
        super(DestroyExtras.rl("multiblocks/" + id), size.controller, size.controller, size.dimensions,
            registration, List.of(SAME_BLOCK));
    }

    public static void register() {
        for (Size size : Size.values()) {
            var controller = DestroyExtras.BLOCKS.register("gas_tank_controller_" + size.id,
                () -> new Block(BlockBehaviour.Properties.of().strength(3).requiresCorrectToolForDrops()));
            DestroyExtras.ITEMS.registerSimpleBlockItem(controller);

            for (Material material : Material.values()) {
                if (material != Material.STAINLESS_STEEL || ModList.get().isLoaded("firmalife")) {
                    registerTank(size, material);
                }
            }
        }
    }

    private static void registerTank(Size size, Material material) {
        String id = "gas_tank_" + size.id + "_" + material.id;
        MutableObject<GasTankMultiblock> structure = new MutableObject<>();
        var registration = new ExtrasMultiblockBuilder<>(new GasTankLogic(size, material), id)
            .notMirrored()
            .structure(structure::getValue)
            .defaultBEs(DestroyExtras.BLOCK_ENTITIES)
            .defaultBlock(DestroyExtras.BLOCKS, DestroyExtras.ITEMS,
                BlockBehaviour.Properties.of().strength(3).requiresCorrectToolForDrops())
            .build();
        structure.setValue(new GasTankMultiblock(id, size, registration));
        MultiblockHandler.registerMultiblock(structure.getValue());
    }

    @Override
    public float getManualScale() {
        return 12;
    }
}
