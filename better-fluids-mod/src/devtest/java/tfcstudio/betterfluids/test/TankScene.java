package tfcstudio.betterfluids.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import tfc.stand.scene.Scene;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Сцена стенда «better-fluids» (dev #346): бак 3×2×3 с окнами, 150 вёдер лавы — нижний слой полный, в верхнем у всех
 * девяти секций одинаковый уровень; медный бак Create рядом. Текстуры железного бака не совпадают по пути с медными:
 * JourneyMap кэширует цвет по пути спрайта без пространства имён.
 */
final class TankScene {
    private final Scene scene = new Scene("better-fluids");
    private BlockPos corner;
    private BlockPos copper;

    TankScene() {
        scene.server("бак 3×2×3 и медный бак рядом", c -> {
            var tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("better_fluids:iron_tank"));
            corner = c.at(-1, 0, -5);
            copper = c.at(3, 0, -4);
            for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(2, 1, 2))) {
                c.set(pos, tank.defaultBlockState());
            }
            c.set(copper, AllBlocks.FLUID_TANK.getDefaultState());
            var handler = c.level().getCapability(Capabilities.FluidHandler.BLOCK, corner.offset(1, 1, 1), Direction.UP);
            scene.check("заливка 150 вёдер лавы",
                handler != null && handler.fill(new FluidStack(Fluids.LAVA, 150_000), FluidAction.EXECUTE) == 150_000);
            levels(c.level(), "сервер");
        });
        scene.until("секции и жидкость у клиента", 600, () -> amount(mc().level, corner.offset(2, 1, 2)) > 0);
        scene.ticks(40);
        scene.client("уровни у клиента", () -> levels(mc().level, "клиент"));
        scene.client("текстуры железного бака не совпадают с медными", this::sprites);
        scene.gui(false);
        scene.camera(0.5, 1, -3.5, 7, 200, 25);
        scene.shot("tank");
        scene.camera(0.5, 2, -3.5, 5, 180, 60);
        scene.shot("tank-top");
        scene.start();
    }

    private void levels(Level level, String side) {
        for (int y = 0; y < 2; y++) {
            int expected = y == 0 ? 14_000 : 24_000 / 9;
            var seen = new TreeSet<Integer>();
            for (BlockPos pos : BlockPos.betweenClosed(corner.above(y), corner.offset(2, y, 2))) {
                seen.add(amount(level, pos));
            }
            scene.check(side + ": слой " + y + " — " + seen + " мБ",
                seen.first() >= expected && seen.last() <= expected + 1);
        }
    }

    /** Пути спрайтов граней, как их берёт JourneyMap (getQuads с данными модели из мира). */
    private void sprites() {
        var iron = paths(corner.offset(1, 1, 1));
        var create = paths(copper);
        scene.note("железный бак: " + iron + "; медный: " + create);
        var shared = new HashSet<>(iron);
        shared.retainAll(create);
        scene.check("общих путей нет: " + shared, !iron.isEmpty() && shared.isEmpty());
    }

    private static Set<String> paths(BlockPos pos) {
        var level = mc().level;
        var state = level.getBlockState(pos);
        var model = mc().getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(level, pos, state, level.getModelData(pos));
        var result = new TreeSet<String>();
        for (Direction side : new Direction[] {Direction.UP, null}) {
            for (RenderType type : model.getRenderTypes(state, RandomSource.create(0), data)) {
                for (var quad : model.getQuads(state, side, RandomSource.create(0), data, type)) {
                    result.add(quad.getSprite().contents().name().getPath());
                }
            }
        }
        return result;
    }

    private static int amount(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank ? tank.getTankInventory().getFluidAmount() : -1;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
