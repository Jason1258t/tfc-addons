package tfc.destroy.extras.compat.ponder;

import com.simibubi.create.AllBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import tfc.destroy.extras.DestroyExtras;

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
        scene.title("industrial_electrolyzer", "Industrial membrane electrolyzer");
        scene.configureBasePlate(0, 0, 3);
        BlockPos controller = new BlockPos(1, 1, 0);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos pos = controller.offset(x, y, z);
                    if ((y == 0 && z == 0) || (x == 1 && y == 1 && z == 1)) continue;
                    if (x == 0 && y == 0 && z == 0) {
                        scene.world().setBlock(pos, DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState(), false);
                    } else if (x != 0) {
                        scene.world().setBlock(pos, AllBlocks.FLUID_TANK.getDefaultState(), false);
                    } else if (y == 1 && z == 1) {
                        scene.world().setBlock(pos, AllBlocks.MECHANICAL_PUMP.getDefaultState(), false);
                    } else {
                        scene.world().setBlock(pos, AllBlocks.COPPER_CASING.getDefaultState(), false);
                    }
                }
            }
        }
        scene.world().setBlock(controller.offset(1, 1, 1), AllBlocks.COGWHEEL.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Z), false);
        scene.world().showSection(util.select().everywhere(), Direction.UP);
        scene.idle(20);
        scene.overlay().showText(70).text("Two Create tank compartments separate the fluid circuits")
            .pointAt(util.vector().blockSurface(controller, Direction.WEST));
        scene.idle(80);
        scene.overlay().showText(90).text("Supply EE power and at least 16 RPM to the mechanical pump")
            .pointAt(util.vector().blockSurface(controller, Direction.NORTH));
        scene.idle(100);
    }
}
