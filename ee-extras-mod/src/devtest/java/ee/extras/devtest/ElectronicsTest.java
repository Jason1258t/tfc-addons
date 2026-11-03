package ee.extras.devtest;

import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.PcbEditorBlockEntity;
import ee.extras.pcb.PcbEditorMenu;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbTopology;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Проверки M10: топология, проверка обрыва, сохранение проекта на настоящий предмет и блок-сущность. */
final class ElectronicsTest {
    private static final BlockPos EDITOR = new BlockPos(60, -60, 10);
    private static int checks;

    private ElectronicsTest() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rectest").then(Commands.literal("pcb").executes(c -> run(c.getSource()))));
    }

    private static int run(CommandSourceStack src) {
        checks = 0;
        ServerLevel level = src.getLevel();
        CompoundTag tag = validL1();
        require(PcbTopology.validate(tag) == null, "валидная L1 не прошла проверку");
        PcbTopology.set(tag, 0, 7, PcbTopology.EMPTY);
        require(PcbTopology.validate(tag) != null, "обрыв не обнаружен");

        CompoundTag multi = PcbTopology.create(2);
        PcbTopology.copyInto(validL1(), multi);
        multi.putInt("Level", 2);
        for (int cell = 16; cell < 20; cell++) PcbTopology.set(multi, 0, cell, PcbTopology.COMPONENT);
        PcbTopology.set(multi, 0, 20, PcbTopology.VIA);
        PcbTopology.set(multi, 1, 20, PcbTopology.TRACE);
        require(PcbTopology.validate(multi) == null, "связанные слои L2 не прошли проверку");
        PcbTopology.set(multi, 0, 20, PcbTopology.TRACE);
        require(PcbTopology.validate(multi) != null, "изолированный второй слой не обнаружен");

        DevTest.place(level, EDITOR, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        require(level.getBlockEntity(EDITOR) instanceof PcbEditorBlockEntity, "блок-сущность редактора не создана");
        PcbEditorBlockEntity editor = (PcbEditorBlockEntity) level.getBlockEntity(EDITOR);
        editor.clear(1);
        CompoundTag valid = validL1();
        for (int cell = 0; cell < 16; cell++) editor.setCell(0, cell, PcbTopology.get(valid, 0, cell));
        editor.setItem(0, new ItemStack(Items.PAPER));
        require(editor.saveDesign() == null, "чертёж не сохранён");
        ItemStack design = editor.getItem(0);
        require(design.is(PcbItems.L1_MASK.get()), "L1 сохранён не в маску L1");
        CompoundTag attached = design.get(PcbItems.PCB_TOPOLOGY.get());
        require(attached != null && PcbTopology.validate(attached) == null, "топология предмета потеряна");
        require(design.getCount() == 1 && !design.is(Items.PAPER), "бумага не израсходована");
        require(design.getCount() == 1 && editor.getItem(0) == design, "проект продублирован");

        CompoundTag saved = editor.saveWithFullMetadata(level.registryAccess());
        editor.clear(3);
        editor.loadWithComponents(saved, level.registryAccess());
        require(PcbTopology.level(editor.topology()) == 1, "уровень не сохранился в NBT");
        require(PcbTopology.validate(editor.topology()) == null, "топология не сохранилась в NBT");
        require(editor.getItem(0).is(PcbItems.L1_MASK.get()), "предмет не сохранился в NBT");

        int validStatus = PcbEditorMenu.statusCode(null);
        int brokenStatus = PcbEditorMenu.statusCode("Есть обрыв или неподключённый вывод");
        require(PcbEditorMenu.statusText(validStatus).equals("Топология проверена"), "успешная проверка не кодируется для клиента");
        require(PcbEditorMenu.statusText(brokenStatus).equals("Есть обрыв или неподключённый вывод"), "ошибка не кодируется для клиента");

        String message = "PCB PASS: " + checks + "/15 — обрыв, L2/via, предмет, NBT и статус";
        src.sendSuccess(() -> Component.literal(message), false);
        return checks;
    }

    private static CompoundTag validL1() {
        CompoundTag tag = PcbTopology.create(1);
        for (int cell = 0; cell < 16; cell++) PcbTopology.set(tag, 0, cell, PcbTopology.TRACE);
        PcbTopology.set(tag, 0, 0, PcbTopology.TERMINAL);
        PcbTopology.set(tag, 0, 15, PcbTopology.TERMINAL);
        PcbTopology.set(tag, 0, 5, PcbTopology.COMPONENT);
        PcbTopology.set(tag, 0, 10, PcbTopology.COMPONENT);
        return tag;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("PCB FAIL: " + message);
    }
}
