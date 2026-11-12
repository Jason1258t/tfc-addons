package tfc.destroy.extras.compat.ponder;

import com.simibubi.create.AllBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;

/** Обучение промышленной сборке; регистрируется Ponder только на клиенте. */
public final class DestroyExtrasPonder implements PonderPlugin {
    @Override
    public String getModId() {
        return DestroyExtras.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(DestroyExtras.rl("membrane_electrolyzer"))
            .addStoryBoard("industrial_electrolyzer", DestroyExtrasPonder::electrolyzer);
    }

    private static void electrolyzer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("industrial_electrolyzer", "Membrane electrolyzer");
        scene.configureBasePlate(0, 0, 3);
        // Шаблон мультиблока: x 0 — слева от игрока, z 1 — передний ряд (в сцене игрок смотрит с юга, +z).
        BlockPos origin = new BlockPos(0, 1, 0);
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos pos = origin.offset(x, y, z + 1);
                    BlockState state;
                    if (x == 1) {
                        state = y == 1 && z == 1
                            ? DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState().setValue(MembraneElectrolyzerBlock.FACING, Direction.SOUTH)
                            : AllBlocks.COPPER_CASING.getDefaultState();
                    } else if (y == 2) {
                        state = AllBlocks.FLUID_PIPE.getDefaultState();
                    } else if (x == 2 && y == 0 && z == 0) {
                        state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "electric_pump")).defaultBlockState();
                    } else {
                        state = AllBlocks.FLUID_TANK.getDefaultState();
                    }
                    scene.world().setBlock(pos, state, false);
                }
            }
        }
        BlockPos cell = origin.offset(1, 1, 2);
        scene.world().showSection(util.select().everywhere(), Direction.UP);
        scene.idle(20);
        scene.overlay().showText(90).text("Create tanks and pipes on the sides, copper casing in the middle, an EE electric pump at the back")
            .pointAt(util.vector().blockSurface(cell, Direction.SOUTH));
        scene.idle(100);
        scene.overlay().showText(90).text("Hit the membrane cell with the Engineer's Hammer; power goes to the two roof terminals")
            .pointAt(util.vector().blockSurface(origin.offset(1, 2, 1), Direction.UP));
        scene.idle(100);
    }
}
