package tfc.destroy.extras.devtest;

import com.simibubi.create.compat.jei.CreateJEI;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.jemi.JemiCategory;
import dev.emi.emi.jemi.JemiRecipe;
import dev.emi.emi.registry.EmiRecipes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.common.Internal;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.compat.ponder.DestroyExtrasPonder;

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
        step(40, "прогрев JEI", () -> jei("electrolysis"));
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
        step(5, "EMI центрифуга", () -> emiDestroy("centrifugation"));
        step(30, "скриншот EMI центрифуги", () -> shot("emi-centrifugation"));
        step(5, "EMI электролиз", () -> emi("electrolysis"));
        step(30, "скриншот EMI электролиза", () -> shot("emi-electrolysis"));
        step(5, "EMI травление", () -> emi("etching"));
        step(25, "скриншот EMI травления", () -> shot("emi-etching"));
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
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("destroy", category);
        if (EmiApi.getRecipeManager().getCategories().stream().noneMatch(value -> id.equals(value.getId()))) {
            // Destroy сам не переносит центрифугу в EMI; для скриншота используем его штатный JEI-экран через Jemi.
            RecipeType<?> type = Internal.getJeiRuntime().getRecipeManager().getRecipeType(id).orElseThrow();
            var jeiCategory = Internal.getJeiRuntime().getRecipeManager().getRecipeCategory(type);
            JemiCategory emiCategory = new JemiCategory(jeiCategory);
            EmiRecipes.addCategory(emiCategory);
            CreateJEI.getTypedRecipes(petrolpark.mc.destroy.DestroyRecipeTypes.CENTRIFUGATION.getType())
                .forEach(holder -> EmiRecipes.addRecipe(new JemiRecipe(emiCategory, jeiCategory, holder)));
            EmiRecipes.bake();
        }
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
