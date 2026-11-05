package ee.extras.devtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.util.NonNegativeId;
import ee.extras.pcb.PcbItems;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Проверки M12 на настоящем RecipeManager и data components CC, без замены Lua/API и идентификаторов устройств. */
final class ComputerCraftTest {
    private static final BlockPos MIGRATION_CHEST = new BlockPos(80, -60, 10);
    private static int checks;

    private ComputerCraftTest() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rectest")
                .then(Commands.literal("cc").executes(c -> run(c.getSource())))
                .then(Commands.literal("ccfail").executes(c -> {
                    throw new IllegalStateException("CC EXPECTED FAIL: контрольный прогон обнаружил намеренную ошибку");
                })));
    }

    private static int run(CommandSourceStack src) {
        checks = 0;
        ServerLevel level = src.getLevel();
        boolean restored = checkMigration(level);
        verifyManifest(level);
        for (String id : List.of("computercraft:cable", "ee_extras:cc/storage_bay")) {
            Recipe<?> recipe = level.getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow().value();
            require(recipe.getIngredients().stream().anyMatch(i -> i.test(item(PcbItems.ABS_SHEET))),
                    "ABS-лист не принят как изоляция: " + id);
        }
        ResourceLocation magnetId = ResourceLocation.parse("ee_extras:cc/materials/permanent_magnet");
        Recipe<?> magnet = level.getRecipeManager().byKey(magnetId).orElseThrow().value();
        require(magnet instanceof DeployerApplicationRecipe deploying && deploying.shouldKeepHeldItem(),
                "деплойер расходует катушку при намагничивании");
        verifyOldIds();
        verifyShortcutsRemoved(level);
        ItemStack disk = verifyDisk(level);
        ItemStack pocket = verifyPocket(level);
        verifyComputer(level);
        ItemStack turtle = verifyTurtle(level);
        if (!restored) saveMigration(level, turtle, pocket, disk);
        String message = "CC PASS: " + checks + " проверок; рецепты 83/83; миграция=" + (restored ? "восстановлена" : "создана");
        src.sendSuccess(() -> Component.literal(message), false);
        return checks;
    }

    private static void verifyManifest(ServerLevel level) {
        InputStream stream = ComputerCraftTest.class.getResourceAsStream("/cc-test-manifest.json");
        require(stream != null, "нет тестового манифеста рецептов");
        int count = 0;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                String output = entry.get("output").getAsString();
                Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(ResourceLocation.parse(id));
                if (output.isEmpty()) {
                    require(holder.isEmpty() || holder.get().value().getResultItem(level.registryAccess()).isEmpty(),
                            "рецепт удаления lapis_block всё ещё активен: " + id);
                } else {
                    require(holder.isPresent(), "рецепт не загружен: " + id);
                    ItemStack result = holder.get().value().getResultItem(level.registryAccess());
                    require(!result.isEmpty(), "пустой результат: " + id);
                    require(result.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(output))),
                            "неправильный результат " + id + ": " + result);
                }
                count++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("CC FAIL: не прочитан манифест", e);
        }
        require(count == 83, "манифест содержит " + count + " вместо 83 рецептов");
    }

    private static void verifyOldIds() {
        for (String path : List.of("computer_normal", "computer_advanced", "pocket_computer_normal", "pocket_computer_advanced",
                "turtle_normal", "turtle_advanced", "monitor_normal", "monitor_advanced", "disk_drive", "printer", "speaker",
                "wired_modem", "wireless_modem_normal", "wireless_modem_advanced", "cable", "redstone_relay", "disk")) {
            require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("computercraft", path)),
                    "изменился или исчез старый ID computercraft:" + path);
        }
        require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("ccbr", "basic_integrated_circuit")),
                "исчез ID базовой микросхемы CCBR");
        require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("ccbr", "integrated_circuit")),
                "исчез ID продвинутой микросхемы CCBR");
    }

    private static void verifyShortcutsRemoved(ServerLevel level) {
        List<ItemStack> stone = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.STONE)));
        stone.set(4, new ItemStack(Items.REDSTONE));
        require(!crafting(level, "computercraft:computer_normal").matches(input(3, 3, stone), level),
                "старый каменный shortcut компьютера всё ещё работает");
        List<ItemStack> gold = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.GOLD_INGOT)));
        gold.set(4, new ItemStack(Items.REDSTONE));
        require(!crafting(level, "computercraft:computer_advanced").matches(input(3, 3, gold), level),
                "старый золотой shortcut продвинутого компьютера всё ещё работает");
        require(!crafting(level, "computercraft:disk").matches(
                        input(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.REDSTONE),
                                ItemStack.EMPTY, ItemStack.EMPTY)), level),
                "дискета всё ещё создаётся из бумаги и редстоуна");
    }

    private static ItemStack verifyDisk(ServerLevel level) {
        Recipe<CraftingInput> recipe = crafting(level, "computercraft:disk");
        ItemStack medium = new ItemStack(PcbItems.MAGNETIC_DISK_MEDIUM.get());
        CraftingInput plain = input(2, 2, List.of(medium, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        require(recipe.matches(plain, level), "магнитный носитель не подходит к штатному рецепту дискеты");
        ItemStack made = recipe.assemble(plain, level.registryAccess());
        require(made.is(ModRegistry.Items.DISK.get()), "носитель не превратился в дискету CC");
        require(made.has(DataComponents.DYED_COLOR), "новая дискета не получила штатный цвет");
        require(made.getCount() == 1, "дубликаты носителя изменили число дискет");

        CraftingInput dyedInput = input(2, 2, List.of(medium, new ItemStack(Items.RED_DYE), ItemStack.EMPTY, ItemStack.EMPTY));
        require(recipe.matches(dyedInput, level), "носитель и краситель не подходят к рецепту дискеты");
        ItemStack dyed = recipe.assemble(dyedInput, level.registryAccess());
        require(!Objects.equals(dyed.get(DataComponents.DYED_COLOR), made.get(DataComponents.DYED_COLOR)),
                "краситель не изменил цвет новой дискеты");

        dyed.set(ModRegistry.DataComponents.DISK_ID.get(), NonNegativeId.of(4242));
        dyed.set(DataComponents.CUSTOM_NAME, Component.literal("Программы шахты"));
        Recipe<CraftingInput> colour = crafting(level, "computercraft:colour");
        CraftingInput recolour = input(2, 2, List.of(dyed, new ItemStack(Items.BLUE_DYE), ItemStack.EMPTY, ItemStack.EMPTY));
        require(colour.matches(recolour, level), "штатная окраска существующей дискеты не работает");
        ItemStack recoloured = colour.assemble(recolour, level.registryAccess());
        same(dyed, recoloured, ModRegistry.DataComponents.DISK_ID.get(), "окраска удалила DISK_ID и доступ к файлам");
        same(dyed, recoloured, DataComponents.CUSTOM_NAME, "окраска удалила название дискеты");

        Recipe<CraftingInput> clear = crafting(level, "computercraft:clear_colour");
        CraftingInput clearing = input(2, 2, List.of(recoloured, new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY, ItemStack.EMPTY));
        require(clear.matches(clearing, level), "штатная очистка цвета дискеты не работает");
        ItemStack cleared = clear.assemble(clearing, level.registryAccess());
        same(recoloured, cleared, ModRegistry.DataComponents.DISK_ID.get(), "очистка удалила DISK_ID и доступ к файлам");
        same(recoloured, cleared, DataComponents.CUSTOM_NAME, "очистка удалила название дискеты");
        require(!cleared.has(DataComponents.DYED_COLOR), "очистка не удалила только цвет дискеты");
        return cleared;
    }

    private static void verifyComputer(ServerLevel level) {
        ItemStack normal = new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get());
        normal.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(7001));
        normal.set(ModRegistry.DataComponents.ON.get(), true);
        normal.set(DataComponents.CUSTOM_NAME, Component.literal("Старый компьютер"));
        CraftingInput upgrade = input(3, 3, List.of(
                item(PcbItems.ADVANCED_COMPUTER_CASE), item(PcbItems.ADVANCED_MOTHERBOARD), item(PcbItems.ADVANCED_COMPUTER_CASE),
                item(PcbItems.DISPLAY_ADVANCED), normal, item(PcbItems.DISPLAY_ADVANCED),
                item(PcbItems.KEYBOARD), item(PcbItems.POWER_SUPPLY), item(PcbItems.KEYBOARD)));
        Recipe<CraftingInput> recipe = crafting(level, "computercraft:computer_advanced_upgrade");
        require(recipe.matches(upgrade, level), "промышленный upgrade компьютера не совпал с сеткой");
        ItemStack result = recipe.assemble(upgrade, level.registryAccess());
        require(result.is(ModRegistry.Items.COMPUTER_ADVANCED.get()), "upgrade не выдал продвинутый компьютер");
        same(normal, result, ModRegistry.DataComponents.COMPUTER_ID.get(), "upgrade компьютера изменил COMPUTER_ID");
        same(normal, result, ModRegistry.DataComponents.ON.get(), "upgrade компьютера потерял состояние ON");
        same(normal, result, DataComponents.CUSTOM_NAME, "upgrade компьютера потерял название");
    }

    private static ItemStack verifyPocket(ServerLevel level) {
        ItemStack normal = new ItemStack(ModRegistry.Items.POCKET_COMPUTER_NORMAL.get());
        normal.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(8001));
        normal.set(DataComponents.CUSTOM_NAME, Component.literal("Карманный терминал"));
        Recipe<CraftingInput> peripheral = crafting(level, "computercraft:pocket_computer_upgrade");
        ItemStack speaker = new ItemStack(ModRegistry.Items.SPEAKER.get());
        require(dan200.computercraft.impl.PocketUpgrades.instance().get(level.registryAccess(), speaker) != null,
                "динамик не зарегистрирован как pocket upgrade");
        CraftingInput peripheralInput = input(1, 2, List.of(speaker, normal));
        require(peripheral.matches(peripheralInput, level), "штатное улучшение карманного компьютера не работает");
        ItemStack withSpeaker = peripheral.assemble(peripheralInput, level.registryAccess());
        require(withSpeaker.get(ModRegistry.DataComponents.POCKET_UPGRADE.get()) != null, "динамик не установлен в карманный компьютер");

        CraftingInput upgrade = input(3, 3, List.of(
                item(PcbItems.POCKET_CASE), item(PcbItems.ADVANCED_MOTHERBOARD), item(PcbItems.POCKET_CASE),
                item(PcbItems.POCKET_COLOR_DISPLAY), withSpeaker, item(PcbItems.POCKET_COLOR_DISPLAY),
                ItemStack.EMPTY, item(PcbItems.BATTERY_MODULE), ItemStack.EMPTY));
        Recipe<CraftingInput> recipe = crafting(level, "computercraft:pocket_computer_advanced_upgrade");
        require(recipe.matches(upgrade, level), "промышленный upgrade карманного компьютера не совпал с сеткой");
        CraftingInput monochrome = input(3, 3, List.of(
                item(PcbItems.POCKET_CASE), item(PcbItems.ADVANCED_MOTHERBOARD), item(PcbItems.POCKET_CASE),
                item(PcbItems.POCKET_DISPLAY), withSpeaker, item(PcbItems.POCKET_DISPLAY),
                ItemStack.EMPTY, item(PcbItems.BATTERY_MODULE), ItemStack.EMPTY));
        require(!recipe.matches(monochrome, level), "улучшение приняло обычный экран вместо цветной матрицы");
        ItemStack result = recipe.assemble(upgrade, level.registryAccess());
        require(result.is(ModRegistry.Items.POCKET_COMPUTER_ADVANCED.get()), "upgrade не выдал продвинутый карманный компьютер");
        same(normal, result, ModRegistry.DataComponents.COMPUTER_ID.get(), "upgrade карманного компьютера изменил COMPUTER_ID");
        same(normal, result, DataComponents.CUSTOM_NAME, "upgrade карманного компьютера потерял название");
        same(withSpeaker, result, ModRegistry.DataComponents.POCKET_UPGRADE.get(), "upgrade потерял периферию карманного компьютера");
        return result;
    }

    private static ItemStack verifyTurtle(ServerLevel level) {
        ItemStack normal = new ItemStack(ModRegistry.Items.TURTLE_NORMAL.get());
        normal.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(9001));
        normal.set(ModRegistry.DataComponents.FUEL.get(), 123);
        normal.set(DataComponents.CUSTOM_NAME, Component.literal("Старая черепаха"));
        normal.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7))));

        Recipe<CraftingInput> tools = crafting(level, "computercraft:turtle_upgrade");
        CraftingInput addTools = input(3, 1, List.of(new ItemStack(Items.DIAMOND_PICKAXE), normal,
                new ItemStack(ModRegistry.Items.WIRELESS_MODEM_NORMAL.get())));
        require(tools.matches(addTools, level), "штатные tool/peripheral upgrades черепахи не работают");
        ItemStack equipped = tools.assemble(addTools, level.registryAccess());
        require(equipped.get(ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get()) != null,
                "инструмент не установлен на черепаху");
        require(equipped.get(ModRegistry.DataComponents.RIGHT_TURTLE_UPGRADE.get()) != null,
                "модем не установлен на черепаху");
        same(normal, equipped, DataComponents.CONTAINER, "установка улучшений потеряла инвентарь черепахи");

        CraftingInput upgrade = input(3, 3, List.of(
                ItemStack.EMPTY, item(PcbItems.ROBOT_CHASSIS), ItemStack.EMPTY,
                item(PcbItems.ADVANCED_TURTLE_CONTROLLER), equipped, item(PcbItems.PRECISION_ACTUATOR),
                ItemStack.EMPTY, item(PcbItems.STORAGE_BAY), ItemStack.EMPTY));
        Recipe<CraftingInput> recipe = crafting(level, "computercraft:turtle_advanced_upgrade");
        require(recipe.matches(upgrade, level), "промышленный upgrade черепахи не совпал с сеткой");
        ItemStack result = recipe.assemble(upgrade, level.registryAccess());
        require(result.is(ModRegistry.Items.TURTLE_ADVANCED.get()), "upgrade не выдал продвинутую черепаху");
        same(normal, result, ModRegistry.DataComponents.COMPUTER_ID.get(), "upgrade черепахи изменил COMPUTER_ID");
        same(normal, result, ModRegistry.DataComponents.FUEL.get(), "upgrade черепахи потерял топливо");
        same(normal, result, DataComponents.CUSTOM_NAME, "upgrade черепахи потерял название");
        same(normal, result, DataComponents.CONTAINER, "upgrade черепахи потерял инвентарь");
        same(equipped, result, ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get(), "upgrade потерял инструмент черепахи");
        same(equipped, result, ModRegistry.DataComponents.RIGHT_TURTLE_UPGRADE.get(), "upgrade потерял модем черепахи");
        return result;
    }

    private static boolean checkMigration(ServerLevel level) {
        if (!(level.getBlockEntity(MIGRATION_CHEST) instanceof ChestBlockEntity chest)) return false;
        ItemStack turtle = chest.getItem(0);
        ItemStack pocket = chest.getItem(1);
        ItemStack disk = chest.getItem(2);
        require(turtle.is(ModRegistry.Items.TURTLE_ADVANCED.get()), "после перезапуска потеряна продвинутая черепаха");
        require(pocket.is(ModRegistry.Items.POCKET_COMPUTER_ADVANCED.get()), "после перезапуска потерян карманный компьютер");
        require(disk.is(ModRegistry.Items.DISK.get()), "после перезапуска потеряна дискета");
        require(Objects.equals(turtle.get(ModRegistry.DataComponents.COMPUTER_ID.get()), NonNegativeId.of(9001)),
                "после перезапуска изменился COMPUTER_ID черепахи");
        require(Objects.equals(turtle.get(ModRegistry.DataComponents.FUEL.get()), 123), "после перезапуска потеряно топливо");
        require(turtle.get(DataComponents.CONTAINER).getStackInSlot(0).getCount() == 7,
                "после перезапуска потерян инвентарь черепахи");
        require(turtle.get(ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get()) != null
                        && turtle.get(ModRegistry.DataComponents.RIGHT_TURTLE_UPGRADE.get()) != null,
                "после перезапуска потеряны улучшения черепахи");
        require(Objects.equals(pocket.get(ModRegistry.DataComponents.COMPUTER_ID.get()), NonNegativeId.of(8001)),
                "после перезапуска изменился COMPUTER_ID карманного компьютера");
        require(pocket.get(ModRegistry.DataComponents.POCKET_UPGRADE.get()) != null,
                "после перезапуска потеряна периферия карманного компьютера");
        require(Objects.equals(disk.get(ModRegistry.DataComponents.DISK_ID.get()), NonNegativeId.of(4242)),
                "после перезапуска изменился DISK_ID");
        require(!disk.has(DataComponents.DYED_COLOR), "после перезапуска не сохранилась очистка цвета");
        return true;
    }

    private static void saveMigration(ServerLevel level, ItemStack turtle, ItemStack pocket, ItemStack disk) {
        DevTest.place(level, MIGRATION_CHEST, Blocks.CHEST.defaultBlockState());
        require(level.getBlockEntity(MIGRATION_CHEST) instanceof ChestBlockEntity, "не создан сундук миграции");
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(MIGRATION_CHEST);
        chest.setItem(0, turtle.copy());
        chest.setItem(1, pocket.copy());
        chest.setItem(2, disk.copy());
        chest.setChanged();
    }

    private static ItemStack item(dan200.computercraft.shared.platform.RegistryEntry<? extends net.minecraft.world.item.Item> entry) {
        return new ItemStack(entry.get());
    }

    private static ItemStack item(net.neoforged.neoforge.registries.DeferredItem<? extends net.minecraft.world.item.Item> entry) {
        return new ItemStack(entry.get());
    }

    private static CraftingInput input(int width, int height, List<ItemStack> items) {
        return CraftingInput.of(width, height, items);
    }

    @SuppressWarnings("unchecked")
    private static Recipe<CraftingInput> crafting(ServerLevel level, String id) {
        RecipeHolder<?> holder = level.getRecipeManager().byKey(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("CC FAIL: нет рецепта " + id));
        return (Recipe<CraftingInput>) holder.value();
    }

    private static <T> void same(ItemStack expected, ItemStack actual, DataComponentType<T> type, String message) {
        require(Objects.equals(expected.get(type), actual.get(type)), message);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("CC FAIL: " + message);
    }
}
