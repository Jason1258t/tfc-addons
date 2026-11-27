package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.EeExtras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class MachineControlsTest {
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("machinecontrols").executes(ctx -> {
            var level = ctx.getSource().getLevel();
            var blocks = new net.minecraft.world.level.block.Block[]{EeExtras.ELECTRIC_PRESS.get(),
                    EeExtras.ELECTRIC_MIXER.get(), EeExtras.ELECTRIC_DEPLOYER.get()};
            for (int i = 0; i < blocks.length; i++) {
                var pos = new BlockPos(5 + i * 4, -57, 20);
                DevTest.place(level, pos, blocks[i].defaultBlockState());
                check(level, pos);
            }
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("УПРАВЛЕНИЕ ПРИБОРАМИ ОК: 3 прибора, скорость, стоп, стили, окраска, сохранение, запрет катушки"), false);
            return 1;
        }));
    }
    static void click(ServerPlayer player, BlockPos pos, ItemStack stack, int slot, boolean shift) {
        player.setShiftKeyDown(shift);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(slot == 0 ? .25 : .75, .8, 1), Direction.SOUTH, pos, false);
        if (!player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit).consumesAction())
            throw new IllegalStateException("Клик управления не принят: " + stack);
        player.setShiftKeyDown(false);
    }
    static void mount(ServerPlayer player, BlockPos pos) {
        click(player, pos, new ItemStack(CEEBlocks.CUT_OFF_SWITCH.get()), 0, false);
        click(player, pos, new ItemStack(CEEBlocks.EMERGENCY_STOP_BUTTON.get()), 1, false);
        click(player, pos, ItemStack.EMPTY, 0, false);
    }
    static void check(ServerLevel level, BlockPos pos) {
        var player = CapTest.player(level, ItemStack.EMPTY);
        mount(player, pos);
        var setting = BlockEntityBehaviour.get(level, pos, ScrollValueBehaviour.TYPE);
        if (setting == null) throw new IllegalStateException("Нет окошка скорости");
        setting.setValue(128);
        var be = (KineticBlockEntity) level.getBlockEntity(pos);
        var data = be.saveWithFullMetadata(level.registryAccess());
        if (!data.getCompound("MachineControls").getCompound("Slot0").getBoolean("IsClosed")
                || !data.getCompound("MachineControls").getCompound("Slot1").getBoolean("IsClosed"))
            throw new IllegalStateException("Рубильник и кнопка не установились");
        click(player, pos, new ItemStack(Items.BLUE_DYE), 0, false);
        click(player, pos, new ItemStack(CEEBlocks.CUT_OFF_SWITCH.get()), 0, false);
        click(player, pos, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get()), 0, false);
        if (player.getMainHandItem().has(com.george_vi.electroenergetics.CEEDataComponents.SELECTED_NODE))
            throw new IllegalStateException("Катушка выбрала управление");
        click(player, pos, ItemStack.EMPTY, 1, false);
        data = be.saveWithFullMetadata(level.registryAccess());
        if (data.getCompound("MachineControls").getCompound("Slot1").getBoolean("IsClosed"))
            throw new IllegalStateException("Аварийный стоп не сработал");
        be.loadWithComponents(data, level.registryAccess());
        if (setting.getValue() != 128 || be.getSpeed() != 0) throw new IllegalStateException("Не сохранена скорость или стоп");
        click(player, pos, ItemStack.EMPTY, 1, true);
        click(player, pos, new ItemStack(AllItems.WRENCH.get()), 0, true);
        if (be.saveWithFullMetadata(level.registryAccess()).getCompound("MachineControls").contains("Slot0"))
            throw new IllegalStateException("Ключ не снял рубильник");
    }
    private MachineControlsTest() {}
}
