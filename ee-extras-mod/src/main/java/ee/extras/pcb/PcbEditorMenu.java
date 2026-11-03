package ee.extras.pcb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Кнопки сетки приходят на сервер как clickMenuButton; физический предмет — только в слоте 0. */
public class PcbEditorMenu extends AbstractContainerMenu {
    public static final int TOOL = 1000;
    public static final int LAYER = 1001;
    public static final int LEVEL = 1002;
    public static final int CHECK = 1003;
    public static final int SAVE = 1004;
    public static final int CLEAR = 1005;
    public static final int LOAD = 1006;

    private final ContainerLevelAccess access;
    private final PcbEditorBlockEntity editor;
    private final DataSlot tool = DataSlot.standalone();
    private final DataSlot layer = DataSlot.standalone();
    private final DataSlot level = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private static final String[] STATUS_TEXT = {
            "Проект ещё не проверен", "Топология проверена", "Проект очищен", "Чертёж загружен в редактор",
            "Положите бумагу или чертёж в ячейку", "Для другого уровня нужен новый лист бумаги",
            "Выводы должны быть на краю нижнего слоя", "Нужно компонентов: не менее 2",
            "Нужно компонентов: не менее 4", "Нужно компонентов: не менее 8",
            "Нужно не менее двух выводов на краю платы", "Пустой проект", "Есть обрыв или неподключённый вывод"
    };

    /** Клиентская фабрика MenuType. */
    public PcbEditorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, ContainerLevelAccess.NULL,
                inventory.player.level().getBlockEntity(pos) instanceof PcbEditorBlockEntity be ? be : null);
    }

    public PcbEditorMenu(int id, Inventory inventory, ContainerLevelAccess access, PcbEditorBlockEntity editor) {
        super(PcbItems.EDITOR_MENU.get(), id);
        this.access = access;
        this.editor = editor;
        Container container = editor == null ? new SimpleContainer(1) : editor;
        addSlot(new Slot(container, 0, 244, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.PAPER) || stack.getItem() instanceof PcbDesignItem;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 212));
        level.set(editor == null ? 1 : PcbTopology.level(editor.topology()));
        addDataSlot(tool);
        addDataSlot(layer);
        addDataSlot(level);
        addDataSlot(status);
    }

    public int tool() { return tool.get(); }
    public int layer() { return layer.get(); }
    public int level() { return level.get(); }
    public int status() { return status.get(); }
    public String statusText() { return statusText(status.get()); }

    /** В DataSlot уходит код: одинаковый русский текст восстанавливается и на сервере, и на клиенте. */
    public static int statusCode(String error) {
        if (error == null) return 1;
        for (int i = 4; i < STATUS_TEXT.length; i++) if (STATUS_TEXT[i].equals(error)) return i;
        return 0;
    }

    public static String statusText(int code) {
        return code >= 0 && code < STATUS_TEXT.length ? STATUS_TEXT[code] : STATUS_TEXT[0];
    }

    public PcbEditorBlockEntity editor() {
        return editor;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (editor == null) return false;
        if (id >= 0 && id < PcbTopology.CELLS) {
            int value = switch (tool.get()) {
                case 0 -> PcbTopology.TRACE;
                case 1 -> PcbTopology.COMPONENT;
                case 2 -> level.get() > 1 ? PcbTopology.VIA : PcbTopology.TRACE;
                case 3 -> PcbTopology.TERMINAL;
                default -> PcbTopology.EMPTY;
            };
            editor.setCell(layer.get(), id, value);
            status.set(0);
            return true;
        }
        switch (id) {
            case TOOL -> tool.set((tool.get() + 1) % 5);
            case LAYER -> layer.set((layer.get() + 1) % level.get());
            case LEVEL -> {
                int newLevel = level.get() % PcbTopology.MAX_LAYERS + 1;
                level.set(newLevel);
                layer.set(0);
                editor.setLevel(newLevel);
            }
            case CHECK -> check();
            case SAVE -> {
                String error = editor.saveDesign();
                setStatus(error);
            }
            case CLEAR -> {
                editor.clear(level.get());
                status.set(2);
            }
            case LOAD -> {
                editor.loadDesign();
                level.set(PcbTopology.level(editor.topology()));
                layer.set(0);
                status.set(3);
            }
            default -> { return false; }
        }
        return true;
    }

    private void check() {
        setStatus(PcbTopology.validate(editor.topology()));
    }

    private void setStatus(String error) {
        status.set(statusCode(error));
    }

    @Override
    public boolean stillValid(Player player) {
        return editor != null && stillValid(access, player, PcbItems.EDITOR_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index == 0) {
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }
}
