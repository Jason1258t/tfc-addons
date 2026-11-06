package ee.extras.pcb;

import java.util.Map;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Предмет готовой платы. Пассивная плата остаётся материалом для рецептов. */
final class PcbBoardItem extends BlockItem {
    static final String EDITOR_ERROR = "Нужна чистая плата уровня проекта";
    private final int level;

    PcbBoardItem(int level) {
        super(PcbItems.CIRCUIT_BLOCK.get(), new Properties().stacksTo(16));
        this.level = level;
    }

    static int level(ItemStack stack) {
        return stack.getItem() instanceof PcbBoardItem board ? board.level : 0;
    }

    static boolean isClean(ItemStack stack, int level) {
        if (stack.isEmpty() || level(stack) != level) return false;
        var topology = stack.get(PcbItems.PCB_TOPOLOGY.get());
        return topology == null || !topology.getBoolean(PcbAssembly.ASSEMBLED);
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        // У одного блока три исходных предмета, уровень хранится в установленной плате.
    }

    @Override
    public String getDescriptionId() {
        return "item.ee_extras.l" + level + "_board";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var topology = stack.get(PcbItems.PCB_TOPOLOGY.get());
        if (topology == null || !topology.getBoolean(PcbAssembly.ASSEMBLED)) return;
        var rating = PcbCircuitDevice.Rating.values()[level - 1];
        tooltip.add(Component.translatable("ee_extras.pcb.rating", (int) rating.volts, (int) rating.watts, rating.amps));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        CompoundTag topology = context.getItemInHand().get(PcbItems.PCB_TOPOLOGY.get());
        if (topology == null || !topology.getBoolean(PcbAssembly.ASSEMBLED) || PcbTopology.level(topology) != level || !PcbWiring.active(topology)
                || PcbWiring.compile(topology) == null) return InteractionResult.PASS;
        return super.place(context);
    }
}
