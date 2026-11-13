package incinerator;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Мусоросжигатель для сборки TFC Studio: блок, который уничтожает всё, что в него попадает, — упавшие сверху предметы
 * и всё, что вставляют шлюзы, желоба, воронки и прочие устройства через стандартную ёмкость предметов NeoForge. От
 * сожжённого с шансом остаётся «гарь» (тег incinerator:residue), её можно забрать снизу или шлюзом на выход.
 * Кода Create не требует: шлюзы и желоба Create работают с блоком через ту же ёмкость.
 */
@Mod(Incinerator.MOD_ID)
public class Incinerator {
    public static final String MOD_ID = "incinerator";
    /** Что остаётся от сожжённого — по одному случайному предмету тега */
    public static final TagKey<Item> RESIDUE = TagKey.create(Registries.ITEM, rl("residue"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<IncineratorBlock> INCINERATOR = BLOCKS.register("incinerator", () -> new IncineratorBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERRACK)
                    .lightLevel(s -> 3)));
    public static final DeferredItem<Item> INCINERATOR_ITEM = ITEMS.register("incinerator",
            () -> new TooltipBlockItem(INCINERATOR.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncineratorBlockEntity>> INCINERATOR_BE =
            BLOCK_ENTITIES.register("incinerator", () -> BlockEntityType.Builder
                    .of(IncineratorBlockEntity::new, INCINERATOR.get()).build(null));

    public Incinerator(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                INCINERATOR_BE.get(), (be, side) -> be.handler));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) e.accept(INCINERATOR_ITEM.get());
        });
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Предмет блока с подсказкой из языкового файла: что делает (.tooltip) и как подключать (.tooltip.more) */
    public static class TooltipBlockItem extends BlockItem {
        public TooltipBlockItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
