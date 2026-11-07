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
    static final int MOVE_TOOL = 7;
    static final int EDGE_TOOL = 8;
    static final int EDGE = 3000;
    static final int PICK = 2000;

    private final ContainerLevelAccess access;
    private final PcbEditorBlockEntity editor;
    private final DataSlot selected = DataSlot.standalone();
    private final DataSlot moving = DataSlot.standalone();
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
            PcbWiring.ERROR, PcbBoardItem.EDITOR_ERROR, PcbAssembly.MISSING, PcbAssembly.RECOVERED, PcbAssembly.NO_RECOVERY, PcbFootprint.ERROR, PcbTopology.UNSUPPORTED, "Компоненты устанавливаются на слое 1"
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
        addSlot(new Slot(container, 0, 268, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PcbSupplyItem || stack.is(net.minecraft.world.item.Items.PAPER) || stack.getItem() instanceof PcbDesignItem || PcbBoardItem.level(stack) != 0 || stack.is(PcbItems.BURNT_BOARD.get());
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 4 + col * 18, 176 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 4 + col * 18, 230));
        level.set(editor == null ? 1 : PcbTopology.level(editor.topology()));
        selected.set(-1);
        addDataSlot(selected);
        moving.set(-1);
        addDataSlot(moving);
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

    /** Только координаты клиентского экрана; серверная нумерация и содержимое слотов сохраняются. */
    void layoutClientSlots(int width, int height) {
        for (int i = 0; i < slots.size(); i++) {
            Slot previous = slots.get(i);
            int x, y;
            if (i == 0) { x = width - 36; y = 8; }
            else if (i <= 27) { x = 12 + (i - 1) % 9 * 18; y = height - 74 + (i - 1) / 9 * 18; }
            else { x = 12 + (i - 28) * 18; y = height - 20; }
            Slot replacement = new Slot(previous.container, previous.getContainerSlot(), x, y) {
                @Override public boolean mayPlace(ItemStack stack) { return previous.mayPlace(stack); }
                @Override public boolean mayPickup(Player player) { return previous.mayPickup(player); }
            };
            replacement.index = previous.index;
            slots.set(i, replacement);
        }
    }

    int selected() { return selected.get(); }
    int moving() { return moving.get(); }
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
        if (editor.topology().getInt("Version") != PcbTopology.VERSION && id != CLEAR && id != LOAD) {
            status.set(statusCode(PcbTopology.UNSUPPORTED));
            return true;
        }
        int currentLevel = PcbTopology.level(editor.topology());
        if (level() != currentLevel) {
            level.set(currentLevel);
            layer.set(Math.min(layer(), currentLevel - 1));
            if (!available(part())) part.set(0);
        }
        if (id >= SELECT_TOOL && id <= SELECT_TOOL + EDGE_TOOL) {
            moving.set(-1);
            selected.set(-1);
            tool.set(id - SELECT_TOOL);
            return true;
        }
        if (id >= SELECT_PART && id < SELECT_PART + PcbWiring.KINDS.length) {
            int type = id - SELECT_PART;
            if (!available(type)) return false;
            part.set(type);
            selected.set(-1);
            moving.set(-1);
            layer.set(0);
            tool.set(5);
            return true;
        }
        if (id >= EDGE && id < EDGE + PcbTopology.CELLS * 4) {
            if (tool() != EDGE_TOOL) return false;
            int encoded = id - EDGE;
            if (!editor.toggleEdge(layer(), encoded / 4, encoded % 4)) return false;
            status.set(0);
            return true;
        }
        if (id >= PICK && id < PICK + PcbTopology.CELLS) {
            int owner = PcbFootprint.owner(editor.topology(), layer(), id - PICK);
            if (owner < 0) return false;
            selected.set(owner % PcbTopology.CELLS);
            int definition = editor.topology().getIntArray(PcbWiring.KEY)[owner];
            part.set(PcbWiring.type(definition));
            rotation.set(PcbWiring.rotation(definition));
            resistance.set(PcbWiring.resistance(definition));
            threshold.set(PcbWiring.threshold(definition));
            contact.set(PcbWiring.normallyClosed(definition) ? 1 : 0);
            return true;
        }
        if (id >= 0 && id < PcbTopology.CELLS) {
            if (tool() == EDGE_TOOL) return false;
            if (tool() == MOVE_TOOL) {
                if (moving() < 0) {
                    int owner = PcbFootprint.owner(editor.topology(), layer(), id);
                    if (owner >= 0) moving.set(owner % PcbTopology.CELLS);
                } else if (editor.movePart(layer(), moving(), id)) {
                    moving.set(-1);
                    status.set(0);
                } else status.set(statusCode(PcbFootprint.ERROR));
                return true;
            }
            if (tool.get() == CUT_TOOL) {
                editor.toggleCut(id);
                status.set(0);
                return true;
            }
            if (PcbTopology.cut(editor.topology(), id)) return false;
            if (tool.get() == 5) {
                if (layer() != 0) { status.set(statusCode("Компоненты устанавливаются на слое 1")); return true; }
                if (!available(part())) return false;
                status.set(editor.setPart(layer.get(), id, selectedDefinition()) ? 0 : statusCode(PcbFootprint.ERROR));
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
        int previousDefinition = selectedDefinition();
        switch (id) {
            case TOOL -> tool.set((tool.get() + 1) % (EDGE_TOOL + 1));
            case PART -> {
                tool.set(5);
                selected.set(-1);
                moving.set(-1);
                layer.set(0);
                do {
                    part.set((part.get() + 1) % PcbWiring.KINDS.length);
                } while (!available(part()));
            }
            case CONTACT -> contact.set(1 - contact.get());
            case ROTATE -> rotation.set((rotation.get() + 1) % 4);
            case OHMS -> {
                if (!PcbModules.ALL[part()].resistanceSetting()) return false;
                resistance.set((resistance.get() + 1) % PcbWiring.OHMS.length);
            }
            case VOLTS -> {
                var item = getSlot(0).getItem();
                if (item.getItem() instanceof PcbSupplyItem) {
                    PcbSupplyItem.configure(item, (PcbSupplyItem.setting(item) + 1) % PcbSupplyBlock.VOLTS.length);
                    getSlot(0).setChanged();
                } else {
                    if (!PcbModules.ALL[part()].thresholdSetting()) return false;
                    threshold.set((threshold.get() + 1) % PcbWiring.VOLTS.length);
                }
            }
            case LAYER -> { selected.set(-1); moving.set(-1); layer.set((layer.get() + 1) % level.get()); }
            case LEVEL -> {
                selected.set(-1);
                moving.set(-1);
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
                selected.set(-1);
                moving.set(-1);
                editor.clear(level.get());
                status.set(2);
            }
            case LOAD -> {
                selected.set(-1);
                moving.set(-1);
                editor.loadDesign();
                level.set(PcbTopology.level(editor.topology()));
                if (!available(part())) part.set(0);
                layer.set(0);
                status.set(editor.topology().getInt("Version") == PcbTopology.VERSION ? 3 : statusCode(PcbTopology.UNSUPPORTED));
            }
            default -> { return false; }
        }
        if (selected() >= 0 && (id == ROTATE || id == OHMS || id == VOLTS || id == CONTACT)
                && !editor.updatePart(layer(), selected(), selectedDefinition())) {
            rotation.set(PcbWiring.rotation(previousDefinition));
            resistance.set(PcbWiring.resistance(previousDefinition));
            threshold.set(PcbWiring.threshold(previousDefinition));
            contact.set(PcbWiring.normallyClosed(previousDefinition) ? 1 : 0);
            status.set(statusCode(PcbFootprint.ERROR));
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
