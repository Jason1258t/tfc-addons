package tfc.destroy.extras.devtest;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.compat.ponder.DestroyExtrasPonder;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;
import tfc.stand.scene.Scene;

/**
 * Автоматический клиентский прогон JEI, EMI/Jemi и Ponder с настоящими экранами и скриншотами, собранная машина на
 * площадке общей заготовки сцены (tools/stand-scene). На стенде — stand_submit scene=destroy-extras.
 */
public final class ClientTest {
    private final Scene scene = new Scene("destroy-extras");
    /** Ячейка машины: середина площадки, на 1 выше пола, машина лицом на юг */
    private BlockPos cell;

    public ClientTest() {
        String requested = Scene.requested();
        if (requested != null && !requested.equals("destroy-extras")) return;
        System.out.println("DESTROYEXTRACLIENT INIT");
        // В dev-runtime ServiceLoader не видит service-файл exploded resources; production jar использует его штатно.
        PonderIndex.addPlugin(new DestroyExtrasPonder());
        NeoForge.EVENT_BUS.addListener(this::screen);
        // EMI ждёт JEI и переносит категории Destroy заметно дольше, чем открывается мир.
        scene.ticks(600).client("прогрев JEI и EMI", () -> jei("electrolysis"));
        scene.ticks(40).client("JEI травление", () -> jei("etching"));
        scene.ticks(25).client("скриншот JEI травления", () -> {
            check("JEI/Jemi открыл категорию травления", recipeScreen());
            shot("jei-etching");
        });
        scene.ticks(5).client("JEI электролиз", () -> jei("electrolysis"));
        scene.ticks(25).client("скриншот JEI электролиза", () -> {
            check("JEI/Jemi открыл категорию электролиза", recipeScreen());
            shot("jei-electrolysis");
        });
        scene.ticks(5).client("JEI центрифуга", () -> {
            jeiDestroy("centrifugation");
            emiDestroy("centrifugation");
        });
        scene.ticks(30).client("скриншот JEI центрифуги", () -> {
            check("JEI/Jemi открыл штатную категорию центрифуги", recipeScreen());
            shot("jei-centrifugation");
        });
        scene.ticks(5).client("EMI центрифуга", () -> {
            emiDestroy("centrifugation");
            EmiApi.getRecipeManager().getCategories().stream()
                .filter(value -> value.getId().equals(ResourceLocation.fromNamespaceAndPath("destroy", "centrifugation")))
                .findFirst().ifPresent(value -> check("EMI показывает 3 рецепта центрифуги",
                    EmiApi.getRecipeManager().getRecipes(value).size() == 3));
        });
        scene.ticks(30).client("скриншот EMI центрифуги", () -> shot("emi-centrifugation"));
        scene.ticks(5).client("EMI электролиз", () -> emi("electrolysis"));
        scene.ticks(30).client("скриншот EMI электролиза", () -> shot("emi-electrolysis"));
        scene.ticks(5).client("EMI травление", () -> emi("etching"));
        scene.ticks(25).client("скриншот EMI травления", () -> shot("emi-etching"));
        scene.ticks(5).client("поворот машины на клиенте", this::rotationChecks);
        // Окно EMI ставит одиночную игру на паузу: пока оно открыто, сервер не тикает.
        scene.client("закрыть окна", () -> mc().setScreen(null));
        scene.server("собрать электролизёр в мире", c -> {
            cell = c.at(1, 1, -5);
            ElectrolyzerRig.place(c.level(), cell, Direction.SOUTH);
            check("машина собрана в мире", ElectrolyzerRig.form(c.level(), cell, Direction.SOUTH));
        });
        scene.until("клетки машины у клиента", 600, () -> mc().level.getBlockState(cell).is(DestroyExtras.ELECTROLYZER.block().get()));
        scene.gui(false);
        scene.camera(1, 1, -5, 6, 180, 15);
        scene.client("что видит клиент", this::formedDiagnostics);
        scene.shot("formed-front");
        scene.client("очки инженера", this::goggleChecks);
        scene.camera(1, 1, -5, 7, -40, 20);
        scene.shot("formed-back");
        scene.gui(true);
        scene.server("окно машины", c -> openWindow(c));
        scene.ticks(40).client("скриншот окна машины", () -> {
            check("открыто окно электролизёра", mc().screen instanceof tfc.destroy.extras.client.ElectrolyzerScreen);
            if (mc().screen instanceof tfc.destroy.extras.client.ElectrolyzerScreen screen) {
                var menu = screen.getMenu();
                // Машина тикает: сразу переливает вход в бак смеси, поэтому сверяется сумма
                int input = menu.input.getFluidAmount() + menu.mixture.getFluidAmount();
                check("окно получило баки с сервера: вход и смесь " + input + ", католит " + menu.catholyte.getFluidAmount(),
                    input == 4000 && menu.catholyte.getFluidAmount() == 1500);
                check("окно получило мембрану: " + menu.membraneLeft(), menu.membraneLeft() == 64);
            }
            shot("formed-gui");
            mc().setScreen(null);
        });
        scene.ticks(5).client("Ponder", () -> mc().setScreen(PonderUI.of(new ItemStack(DestroyExtras.ELECTROLYZER_ITEM.get()))));
        scene.ticks(180).client("скриншот Ponder", () -> {
            check("Ponder открыт для контроллера", mc().screen instanceof PonderUI);
            shot("ponder-industrial");
        });
    }

    private boolean recipeScreen() {
        return mc().screen != null && (mc().screen.getClass().getName().startsWith("mezz.jei")
            || mc().screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen"));
    }

    /** Что видит клиент: игрок, блок и блок-сущность в клетках машины, грани модели на клетку. */
    private void formedDiagnostics() {
        var player = mc().player;
        System.out.println("DESTROYEXTRACLIENT POS " + player.position() + " yaw=" + player.getYRot() + " pitch=" + player.getXRot());
        var level = mc().level;
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
        var be = mc().level.getBlockEntity(cell);
        check("ячейка машины поддерживает очки инженера", be instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation);
        var dummy = mc().level.getBlockEntity(cell.offset(-1, -1, 0));
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
    private void openWindow(Scene.Ctx c) {
        var state = ElectrolyzerRig.state(c.level(), cell);
        state.input.setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.dries007.tfc.common.fluids.TFCFluids.SALT_WATER.getSource(), 4000));
        state.catholyte.setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1500));
        state.membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
        var be = (blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE<?>) c.level().getBlockEntity(cell);
        @SuppressWarnings("unchecked")
        var ctx = (blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext<tfc.destroy.extras.electrolyzer.ElectrolyzerLogic.State>)
            be.getHelper().getContext();
        c.player().openMenu(tfc.destroy.extras.electrolyzer.ElectrolyzerMenu.provider(
            new blusunrize.immersiveengineering.common.gui.IEContainerMenu.MultiblockMenuContext<>(ctx, cell)));
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

    private void check(String name, boolean condition) {
        scene.check(name, condition);
    }

    private void shot(String name) {
        Screenshot.grab(mc().gameDirectory, "scene-destroy-extras-" + name + ".png", mc().getMainRenderTarget(),
            message -> System.out.println("SCENE SHOT " + name + ": " + message.getString()));
    }

    /** Мир сцена создаёт сама на главном меню; запуск — после регистрации плагинов клиента. */
    private void screen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) || started) return;
        started = true;
        scene.start();
    }

    private boolean started;
}
