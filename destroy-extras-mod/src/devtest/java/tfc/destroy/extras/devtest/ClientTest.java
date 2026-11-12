package tfc.destroy.extras.devtest;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.common.Internal;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.compat.ponder.DestroyExtrasPonder;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;

/** Автоматический клиентский прогон JEI, EMI/Jemi и Ponder с настоящими экранами и скриншотами. */
public final class ClientTest {
    private final Deque<Step> steps = new ArrayDeque<>();
    private boolean started;
    private int wait;
    private int failures;

    private record Step(int delay, String name, Runnable action) {}

    public ClientTest() {
        System.out.println("DESTROYEXTRACLIENT INIT");
        // В dev-runtime ServiceLoader не видит service-файл exploded resources; production jar использует его штатно.
        PonderIndex.addPlugin(new DestroyExtrasPonder());
        NeoForge.EVENT_BUS.addListener(this::screen);
        NeoForge.EVENT_BUS.addListener(this::tick);
        step(100, "мир", () -> check("клиент и интегрированный мир загружены", mc().player != null));
        // EMI ждёт JEI и переносит категории Destroy заметно дольше, чем открывается мир.
        step(600, "прогрев JEI и EMI", () -> jei("electrolysis"));
        step(40, "JEI травление", () -> jei("etching"));
        step(25, "скриншот JEI травления", () -> {
            check("JEI/Jemi открыл категорию травления", mc().screen != null
                && (mc().screen.getClass().getName().startsWith("mezz.jei")
                    || mc().screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen")));
            shot("jei-etching");
        });
        step(5, "JEI электролиз", () -> jei("electrolysis"));
        step(25, "скриншот JEI электролиза", () -> {
            check("JEI/Jemi открыл категорию электролиза", mc().screen != null
                && (mc().screen.getClass().getName().startsWith("mezz.jei")
                    || mc().screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen")));
            shot("jei-electrolysis");
        });
        step(5, "JEI центрифуга", () -> {
            jeiDestroy("centrifugation");
            emiDestroy("centrifugation");
        });
        step(30, "скриншот JEI центрифуги", () -> {
            check("JEI/Jemi открыл штатную категорию центрифуги", mc().screen != null
                && (mc().screen.getClass().getName().startsWith("mezz.jei")
                    || mc().screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen")));
            shot("jei-centrifugation");
        });
        step(5, "EMI центрифуга", () -> {
            emiDestroy("centrifugation");
            EmiApi.getRecipeManager().getCategories().stream()
                .filter(value -> value.getId().equals(ResourceLocation.fromNamespaceAndPath("destroy", "centrifugation")))
                .findFirst().ifPresent(value -> check("EMI показывает 3 рецепта центрифуги",
                    EmiApi.getRecipeManager().getRecipes(value).size() == 3));
        });
        step(30, "скриншот EMI центрифуги", () -> shot("emi-centrifugation"));
        step(5, "EMI электролиз", () -> emi("electrolysis"));
        step(30, "скриншот EMI электролиза", () -> shot("emi-electrolysis"));
        step(5, "EMI травление", () -> emi("etching"));
        step(25, "скриншот EMI травления", () -> shot("emi-etching"));
        step(5, "поворот машины на клиенте", this::rotationChecks);
        step(5, "собрать электролизёр в мире", () -> server("formed"));
        step(60, "вид спереди", () -> {
            mc().setScreen(null);
            mc().options.hideGui = true;
            command("time set noon");
            command("tp @p 4 -59 1 facing 1 -59 -5");
        });
        step(40, "скриншот собранной машины спереди", () -> {
            formedDiagnostics();
            shot("formed-front");
        });
        step(5, "очки инженера", this::goggleChecks);
        step(5, "вид сзади", () -> command("tp @p -2 -58 -10 facing 1 -59 -5"));
        step(40, "скриншот собранной машины сзади", () -> {
            shot("formed-back");
            mc().options.hideGui = false;
        });
        step(5, "окно машины", this::openWindow);
        step(40, "скриншот окна машины", () -> {
            check("открыто окно электролизёра", mc().screen instanceof tfc.destroy.extras.client.ElectrolyzerScreen);
            if (mc().screen instanceof tfc.destroy.extras.client.ElectrolyzerScreen screen) {
                var menu = screen.getMenu();
                check("окно получило баки с сервера: вход " + menu.input.getFluidAmount() + ", католит " + menu.catholyte.getFluidAmount(),
                    menu.input.getFluidAmount() == 4000 && menu.catholyte.getFluidAmount() == 1500);
                check("окно получило мембрану: " + menu.membraneLeft(), menu.membraneLeft() == 64);
            }
            shot("formed-gui");
            mc().setScreen(null);
        });
        step(5, "Ponder", () -> mc().setScreen(PonderUI.of(new ItemStack(DestroyExtras.ELECTROLYZER_ITEM.get()))));
        step(180, "скриншот Ponder", () -> {
            check("Ponder открыт для контроллера", mc().screen instanceof PonderUI);
            shot("ponder-industrial");
        });
        step(5, "завершение", () -> {
            System.out.println("DESTROYEXTRACLIENT " + (failures == 0 ? "PASS" : "FAIL " + failures));
            mc().stop();
        });
    }

    /** Ячейка (1, -59, -5): машина лицом на юг, к точке обзора; собирается на встроенном сервере, как молотом. */
    private void server(String what) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            var cell = new net.minecraft.core.BlockPos(1, -59, -5);
            ElectrolyzerRig.place(level, cell, Direction.SOUTH);
            check("машина собрана в мире клиента", ElectrolyzerRig.form(level, cell, Direction.SOUTH));
        });
    }

    /** Что видит клиент: игрок, блок и блок-сущность в клетках машины, грани модели на клетку. */
    private void formedDiagnostics() {
        var player = mc().player;
        System.out.println("DESTROYEXTRACLIENT POS " + player.position() + " yaw=" + player.getYRot() + " pitch=" + player.getXRot());
        var level = mc().level;
        var cell = new net.minecraft.core.BlockPos(1, -59, -5);
        int total = 0;
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            var state = level.getBlockState(pos);
            if (!state.is(DestroyExtras.ELECTROLYZER.block().get())) continue;
            var model = mc().getBlockRenderer().getBlockModel(state);
            var data = model.getModelData(level, pos, state, net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            int quads = model.getQuads(state, null, net.minecraft.util.RandomSource.create(), data, null).size();
            total += quads;
            System.out.println("DESTROYEXTRACLIENT CELL " + pos.toShortString() + " be=" + level.getBlockEntity(pos) + " quads=" + quads);
        }
        check("у собранной машины на клиенте есть грани модели: " + total, total > 0);
    }

    /** Очки Create: блок-сущности машины отдают строки с названием и состоянием. */
    private void goggleChecks() {
        var be = mc().level.getBlockEntity(new net.minecraft.core.BlockPos(1, -59, -5));
        check("ячейка машины поддерживает очки инженера", be instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation);
        var dummy = mc().level.getBlockEntity(new net.minecraft.core.BlockPos(0, -60, -5));
        check("любой блок машины поддерживает очки инженера", dummy instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation);
        if (be instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation goggles) {
            List<Component> lines = new java.util.ArrayList<>();
            boolean shown = goggles.addToGoggleTooltip(lines, true);
            lines.forEach(line -> System.out.println("DESTROYEXTRACLIENT GOGGLES " + line.getString()));
            check("очки показывают состояние и баки", shown && lines.stream().anyMatch(l -> l.getString().contains("Состояние"))
                && lines.stream().anyMatch(l -> l.getString().contains("Вход")));
        }
    }

    /** На встроенном сервере: залить баки и вставить мембрану, открыть игроку окно, как щелчком по машине. */
    private void openWindow() {
        var server = mc().getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            var cell = new net.minecraft.core.BlockPos(1, -59, -5);
            var state = ElectrolyzerRig.state(level, cell);
            state.input.setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.dries007.tfc.common.fluids.TFCFluids.SALT_WATER.getSource(), 4000));
            state.catholyte.setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1500));
            state.membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
            var player = server.getPlayerList().getPlayers().get(0);
            var be = (blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE<?>) level.getBlockEntity(cell);
            @SuppressWarnings("unchecked")
            var ctx = (blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext<tfc.destroy.extras.electrolyzer.ElectrolyzerLogic.State>)
                be.getHelper().getContext();
            player.openMenu(tfc.destroy.extras.electrolyzer.ElectrolyzerMenu.provider(
                new blusunrize.immersiveengineering.common.gui.IEContainerMenu.MultiblockMenuContext<>(ctx, cell)));
        });
    }

    private void command(String command) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    private void jei(String category) {
        jei(DestroyExtras.MOD_ID, category);
    }

    private void jeiDestroy(String category) {
        jei("destroy", category);
    }

    private void jei(String namespace, String category) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, category);
        RecipeType<?> type = Internal.getJeiRuntime().getRecipeManager().getRecipeType(id).orElse(null);
        check("JEI зарегистрировала категорию " + category, type != null);
        if (type != null) {
            check("JEI содержит рецепты " + category,
                Internal.getJeiRuntime().getRecipeManager().createRecipeLookup(type).get().findAny().isPresent());
            mc().setScreen(null);
            Internal.getJeiRuntime().getRecipesGui().showTypes(List.of(type));
        }
    }

    private void emi(String category) {
        emi(DestroyExtras.MOD_ID, category);
    }

    private void emiDestroy(String category) {
        emi("destroy", category);
    }

    private void emi(String namespace, String category) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, category);
        EmiRecipeCategory found = EmiApi.getRecipeManager().getCategories().stream()
            .filter(value -> id.equals(value.getId())).findFirst().orElse(null);
        check("EMI/Jemi зарегистрировала категорию " + category, found != null);
        if (found != null) {
            check("EMI/Jemi содержит рецепты " + category, !EmiApi.getRecipeManager().getRecipes(found).isEmpty());
            EmiApi.displayRecipeCategory(found);
        }
    }

    private void rotationChecks() {
        var block = DestroyExtras.ELECTROLYZER_BLOCK.get();
        var renderer = mc().getBlockRenderer();
        var missing = mc().getModelManager().getMissingModel();
        Map<Direction, BakedModel> models = new LinkedHashMap<>();
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            BakedModel model = renderer.getBlockModel(block.defaultBlockState().setValue(MembraneElectrolyzerBlock.FACING, facing));
            check("модель машины загружена для " + facing, model != null && model != missing);
            models.put(facing, model);
        }
        check("модели севера и востока различаются (поворот виден)",
            models.get(Direction.NORTH) != models.get(Direction.EAST));
        var formed = DestroyExtras.ELECTROLYZER.block().get();
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            BakedModel model = renderer.getBlockModel(formed.defaultBlockState()
                .setValue(blusunrize.immersiveengineering.api.IEProperties.FACING_HORIZONTAL, facing));
            check("модель собранного электролизёра загружена для " + facing, model != null && model != missing);
        }
        List<Component> tooltip = new ItemStack(DestroyExtras.ELECTROLYZER_ITEM.get()).getTooltipLines(
            Item.TooltipContext.of(mc().level), mc().player, TooltipFlag.NORMAL);
        check("подсказка ячейки говорит про молот IE", tooltip.stream()
            .anyMatch(line -> line.getString().contains("молота IE")));
    }

    private void step(int delay, String name, Runnable action) {
        steps.add(new Step(delay, name, action));
    }

    private void check(String name, boolean condition) {
        if (!condition) failures++;
        System.out.println("DESTROYEXTRACLIENT " + (condition ? "OK " : "FAIL ") + name);
    }

    private void shot(String name) {
        Screenshot.grab(mc().gameDirectory, "destroyextras-" + name + ".png", mc().getMainRenderTarget(),
            message -> System.out.println("DESTROYEXTRACLIENT SCREENSHOT " + name + ": " + message.getString()));
    }

    private void screen(ScreenEvent.Init.Post event) {
        System.out.println("DESTROYEXTRACLIENT SCREEN " + event.getScreen().getClass().getName());
        if (started) return;
        started = true;
        Minecraft mc = mc();
        mc.options.pauseOnLostFocus = false;
        LevelSettings settings = new LevelSettings("destroyextras-client", GameType.CREATIVE, false,
            Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("destroyextras-client", settings, new WorldOptions(1, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                .createWorldDimensions(), event.getScreen());
    }

    private void tick(ClientTickEvent.Post event) {
        if (mc().player == null || mc().getSingleplayerServer() == null || steps.isEmpty()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peek();
        if (step.delay > 0) {
            wait = step.delay;
            steps.poll();
            steps.addFirst(new Step(0, step.name, step.action));
            return;
        }
        steps.poll();
        try {
            step.action.run();
        } catch (Exception error) {
            check(step.name + ": " + error, false);
            error.printStackTrace();
        }
    }
}
