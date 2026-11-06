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
    public static final int PART = 1007;
    public static final int ROTATE = 1008;
    public static final int OHMS = 1009;
    public static final int VOLTS = 1010;
    public static final int CONTACT = 1011;

    public static final int SELECT_TOOL = 1020;
    static final int SELECT_PART = 1040;
    static final int CUT_TOOL = 6;

    private final ContainerLevelAccess access;
    private final PcbEditorBlockEntity editor;
    private final DataSlot tool = DataSlot.standalone();
    private final DataSlot layer = DataSlot.standalone();
    private final DataSlot level = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot part = DataSlot.standalone();
    private final DataSlot rotation = DataSlot.standalone();
    private final DataSlot resistance = DataSlot.standalone();
    private final DataSlot threshold = DataSlot.standalone();
    private final DataSlot contact = DataSlot.standalone();
    private static final String[] STATUS_TEXT = {
            "Проект ещё не проверен", "Топология проверена", "Проект очищен", "Чертёж загружен в редактор",
            "Положите бумагу или чертёж в ячейку", "Для другого уровня нужен новый лист бумаги",
            "Выводы должны быть на краю нижнего слоя", "Нужно компонентов: не менее 2",
            "Нужно компонентов: не менее 4", "Нужно компонентов: не менее 8",
            "Нужно не менее двух выводов на краю платы", "Пустой проект", "Есть обрыв или неподключённый вывод",
            PcbWiring.ERROR, PcbBoardItem.EDITOR_ERROR, PcbAssembly.MISSING, PcbAssembly.RECOVERED, PcbAssembly.NO_RECOVERY
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
                return stack.is(net.minecraft.world.item.Items.PAPER) || stack.getItem() instanceof PcbDesignItem || PcbBoardItem.level(stack) != 0 || stack.is(PcbItems.BURNT_BOARD.get());
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
        addDataSlot(part);
        addDataSlot(rotation);
        addDataSlot(resistance);
        addDataSlot(threshold);
        addDataSlot(contact);
    }

    int part() { return part.get(); }
    boolean available(int type) { return PcbWiring.minimumLevel(type) <= level(); }

    int selectedDefinition() {
        return PcbWiring.component(part(), rotation(), resistance(), threshold(), normallyClosed());
    }
    int rotation() { return rotation.get(); }
    int resistance() { return resistance.get(); }
    int threshold() { return threshold.get(); }
    boolean normallyClosed() { return contact.get() != 0; }

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
        int currentLevel = PcbTopology.level(editor.topology());
        if (level() != currentLevel) {
            level.set(currentLevel);
            layer.set(Math.min(layer(), currentLevel - 1));
            if (!available(part())) part.set(0);
        }
        if (id >= SELECT_TOOL && id <= SELECT_TOOL + CUT_TOOL) {
            tool.set(id - SELECT_TOOL);
            return true;
        }
        if (id >= SELECT_PART && id < SELECT_PART + PcbWiring.KINDS.length) {
            int type = id - SELECT_PART;
            if (!available(type)) return false;
            part.set(type);
            tool.set(5);
            return true;
        }
        if (id >= 0 && id < PcbTopology.CELLS) {
            if (tool.get() == CUT_TOOL) {
                editor.toggleCut(id);
                status.set(0);
                return true;
            }
            if (PcbTopology.cut(editor.topology(), id)) return false;
            if (tool.get() == 5) {
                if (!available(part())) return false;
                editor.setPart(layer.get(), id, selectedDefinition());
                status.set(0);
                return true;
            }
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
            case TOOL -> tool.set((tool.get() + 1) % (CUT_TOOL + 1));
            case PART -> {
                tool.set(5);
                do {
                    part.set((part.get() + 1) % PcbWiring.KINDS.length);
                } while (!available(part()));
            }
            case CONTACT -> contact.set(1 - contact.get());
            case ROTATE -> rotation.set((rotation.get() + 1) % 4);
            case OHMS -> resistance.set((resistance.get() + 1) % PcbWiring.OHMS.length);
            case VOLTS -> threshold.set((threshold.get() + 1) % PcbWiring.VOLTS.length);
            case LAYER -> layer.set((layer.get() + 1) % level.get());
            case LEVEL -> {
                int newLevel = level.get() % PcbTopology.MAX_LAYERS + 1;
                level.set(newLevel);
                if (!available(part())) part.set(0);
                layer.set(0);
                editor.setLevel(newLevel);
            }
            case CHECK -> check();
            case SAVE -> {
                String error = getSlot(0).getItem().is(PcbItems.BURNT_BOARD.get())
                        ? editor.salvage(player) : editor.saveDesign(player);
                setStatus(error);
            }
            case CLEAR -> {
                editor.clear(level.get());
                status.set(2);
            }
            case LOAD -> {
                editor.loadDesign();
                level.set(PcbTopology.level(editor.topology()));
                if (!available(part())) part.set(0);
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
