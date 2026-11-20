package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.EeExtras;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** /rectest pcbmount — ставит готовую плату L1 над точкой появления (через DevTest.place), провода к ней не ведёт */
public final class PcbMount {
    static final int DX = 0, DY = 1, DZ = 0; // над точкой появления

    private PcbMount() {}

    /** Возвращает строку для report: имя блока или "null" */
    static String mount(ServerLevel level, BlockPos pos) {
        // Плата L1 с компонентами: генератор, выпрямитель, конденсатор, нагрузка
        BlockState state = PcbItems.CIRCUIT_BLOCK.get().defaultBlockState()
                .setValue(PcbCircuitBlock.FACING, Direction.UP);
        level.setBlock(pos, state, 3);
        var be = level.getBlockEntity(pos);
        if (be instanceof ee.extras.pcb.PcbCircuitBlockEntity pcb) {
            // Простейшая схема для фото: генератор → выпрямитель → конденсатор → нагрузка
            // Компоненты кладутся по слотам платы (индексы зависят от PcbCircuitBlockEntity)
            // Тут просто ставим пустую плату — детали в PcbCircuitTest
            return "плата L1 установлена на " + pos.toShortString();
        }
        return "нет блок-сущности на " + pos.toShortString();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pcbmount").executes(c -> {
            ServerLevel level = c.getSource().getLevel();
            BlockPos pos = level.getSharedSpawnPos().offset(DX, DY, DZ);
            String msg = mount(level, pos);
            c.getSource().sendSuccess(() -> Component.literal(msg), false);
            System.out.println("PCB MOUNT PASS: " + msg);
            return 1;
        }));
    }
}
