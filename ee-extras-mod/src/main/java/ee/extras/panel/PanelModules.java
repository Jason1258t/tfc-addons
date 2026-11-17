package ee.extras.panel;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.PanelAttachmentMode;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import ee.extras.EeExtras;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PanelModules {
    private static final DeferredRegister<PanelAttachmentType> MODULES =
            DeferredRegister.create(CEERegistries.PANEL_ATTACHMENT_TYPE.key(), EeExtras.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EeExtras.MOD_ID);
    public static final java.util.function.Supplier<DataComponentType<InWorldNode>> SELECTED_PORT =
            COMPONENTS.registerComponentType("panel_port",
                    b -> b.persistent(InWorldNode.CODEC).networkSynchronized(InWorldNode.STREAM_CODEC));
    public static final java.util.function.Supplier<Item> SWITCH_ITEM =
            EeExtras.ITEMS.registerSimpleItem("panel_duplex_switch");

    static {
        MODULES.register("panel_duplex_inlet", () -> new CableModuleType(
                PanelInlet::new, CEEBlocks.DUPLEX_WIRE_TERMINATION, false));
        MODULES.register("panel_duplex_switch", () -> new CableModuleType(
                PanelDuplexSwitch::new, SWITCH_ITEM.get(), true));
    }

    public static void register(IEventBus bus) {
        MODULES.register(bus);
        COMPONENTS.register(bus);
    }

    /** Координатная сетка штатного щитка EE 1.1.2: x = 3 + столбец, y = 1,5 + строка. */
    private static InWorldNode grid(BlockPos pos, int x, int row) {
        return new InWorldNode(30 + row * 20 + x - 3, pos);
    }

    private static final class CableModuleType extends PanelAttachmentType {
        private final boolean twoPorts;

        CableModuleType(PanelAttachmentFactory factory, ItemLike item, boolean twoPorts) {
            super(factory, item, twoPorts ? PanelAttachmentMode.HALF : PanelAttachmentMode.QUARTER_NONE);
            this.twoPorts = twoPorts;
        }

        @Override
        public PanelAttachment createNew(BlockPos pos, InWorldNode[] contacts, Level level,
                ElectricalPanelSlot slot, Direction facing, HolderLookup.Provider registries) {
            int x = (int) Math.round(slot.center.x * 16);
            int row = (int) (slot.center.y * 16 - 1.5);
            InWorldNode[] nodes;
            if (twoPorts && slot.isHorizontal) {
                nodes = new InWorldNode[]{grid(pos, x - 4, row - 1), grid(pos, x - 4, row + 1), grid(pos, x - 4, row),
                        grid(pos, x + 4, row - 1), grid(pos, x + 4, row + 1), grid(pos, x + 4, row)};
            } else if (twoPorts) {
                nodes = new InWorldNode[]{grid(pos, x - 1, row + 4), grid(pos, x + 1, row + 4), grid(pos, x, row + 4),
                        grid(pos, x - 1, row - 4), grid(pos, x + 1, row - 4), grid(pos, x, row - 4)};
            } else if (slot.name().startsWith("QUARTER_")) {
                nodes = new InWorldNode[]{grid(pos, x - 1, row), grid(pos, x + 1, row), grid(pos, x, row + 1)};
            } else {
                // Половинные вставки из 2.22.0 сохраняют прежние узлы и уже подключённые провода.
                nodes = Arrays.copyOf(PanelAttachmentMode.HALF.getNodesFor(pos, slot), 3);
                nodes[2] = grid(pos, x, row);
            }
            return super.createNew(pos, nodes, level, slot, facing, registries);
        }
    }

    private PanelModules() {}
}
