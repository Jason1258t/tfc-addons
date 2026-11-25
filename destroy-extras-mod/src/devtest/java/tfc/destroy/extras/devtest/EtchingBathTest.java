package tfc.destroy.extras.devtest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import ee.extras.pcb.PcbItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.etching.EtchingChemistry;

/** Приёмка реальных capability, ПКМ, точки механической руки, химии и сохранения. */
public final class EtchingBathTest {
    private static void check(boolean ok, String label) {
        if (!ok) throw new IllegalStateException("ETCHING FAIL " + label);
        System.out.println("ETCHING PASS " + label);
    }

    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("etchingtest")
            .requires(source -> source.hasPermission(2)).executes(context -> {
                var level = context.getSource().getLevel();
                run(level, new BlockPos(0, -59, 0), net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level));
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("ETCHING ALL PASS"), false);
                return 1;
            }).then(net.minecraft.commands.Commands.literal("negative").executes(context -> {
                var level = context.getSource().getLevel();
                var pos = new BlockPos(0, -59, 0);
                level.setBlockAndUpdate(pos, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
                var bath = (EtchingBathBlockEntity) level.getBlockEntity(pos);
                bath.fluids.fill(solution(100, .12f), FluidAction.EXECUTE);
                check(bath.input.getFluidAmount() == 101, "контроль: намеренно неверный объём 101 вместо 100");
                return 1;
            })));
    }

    public static ItemStack mask() {
        ItemStack mask = new ItemStack(PcbItems.L1_MASK.get());
        CompoundTag topology = new CompoundTag();
        topology.putInt("version", 1);
        topology.putInt("width", 8);
        topology.putInt("height", 8);
        topology.putIntArray("traces", new int[] {1, 2, 5, 8, 13, 21});
        mask.set(PcbItems.PCB_TOPOLOGY.get(), topology);
        return mask;
    }

    public static FluidStack solution(int amount, float iron) {
        return MixtureFluid.of(amount, new LegacyMixture()
            .addMolecule(DestroyMolecules.WATER, 50)
            .addMolecule(DestroyMolecules.IRON_III, iron)
            .addMolecule(DestroyMolecules.CHLORIDE, 3 * iron));
    }

    public static void run(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.setBlockAndUpdate(pos, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
        var bath = (EtchingBathBlockEntity) level.getBlockEntity(pos);
        for (Direction side : Direction.values()) {
            var cap = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
            check(cap != null && cap.fill(solution(100, 0.12f), FluidAction.EXECUTE) == 100,
                "подача с " + side);
        }
        double moles = EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.IRON_III);
        check(bath.fluids.fill(solution(200, 0.24f), FluidAction.SIMULATE) == 200
            && bath.input.getFluidAmount() == 600, "симуляция смешивания не меняет бак");
        bath.fluids.fill(solution(200, 0.24f), FluidAction.EXECUTE);
        check(bath.input.getFluidAmount() == 800
            && Math.abs(EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.IRON_III) - moles - 48) < .01,
            "смешивание сохраняет объём и вещество");
        bath.spent.setFluid(solution(250, 0.01f));
        check(bath.fluids.drain(100, FluidAction.SIMULATE).getAmount() == 100
            && bath.spent.getFluidAmount() == 250, "симуляция откачки");
        bath.fluids.drain(100, FluidAction.EXECUTE);
        check(bath.spent.getFluidAmount() == 150 && bath.input.getFluidAmount() == 800, "приоритет отработки");
        bath.spent.setFluid(FluidStack.EMPTY);
        bath.input.setFluid(FluidStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        check(bath.input.getFluidAmount() == 1000, "ПКМ ведром как у чаши Create");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PcbItems.L1_BOARD_BLANK.get()));
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        check(bath.items.getStackInSlot(0).is(PcbItems.L1_BOARD_BLANK.get()), "ПКМ заготовкой");
        bath.items.setStackInSlot(1, mask());
        for (int i = 0; i < 450; i++) bath.tick();
        check(bath.batches == 0 && bath.input.getFluidAmount() == 1000 && bath.progress == 0,
            "неподходящий раствор не расходуется");
        bath.input.setFluid(solution(4000, 0.12f));
        check(bath.fluids.fill(solution(100, .12f), FluidAction.EXECUTE) == 0
            && bath.input.getFluidAmount() == 4000, "переполнение отклонено");
        level.setBlockAndUpdate(pos.east(2), AllBlocks.MECHANICAL_ARM.getDefaultState());
        var arm = (ArmBlockEntity) level.getBlockEntity(pos.east(2));
        var type = CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.get(DestroyExtras.rl("etching_bath"));
        check(type != null && type.canCreatePoint(level, pos, bath.getBlockState()), "ванна выбирается механической рукой");
        var point = type.createPoint(level, pos, bath.getBlockState());
        check(point.extract(arm, 0, false).isEmpty() && point.extract(arm, 1, false).isEmpty(),
            "рука не крадёт заготовку и маску");
        ItemStack boards = new ItemStack(PcbItems.L1_BOARD_BLANK.get(), 3);
        check(point.insert(arm, boards, true).isEmpty() && bath.items.getStackInSlot(3).isEmpty(), "симуляция руки");
        check(point.insert(arm, boards, false).isEmpty(), "рука размещает ещё три платы");
        check(point.insert(arm, new ItemStack(PcbItems.L1_BOARD_BLANK.get()), false).getCount() == 1, "пятой плате нет места");
        bath.spent.setFluid(solution(4000, .01f));
        for (int i = 0; i < 450; i++) bath.tick();
        check(bath.batches == 0 && bath.input.getFluidAmount() == 4000, "полная отработка блокирует расход");
        bath.spent.setFluid(FluidStack.EMPTY);
        for (int slot : EtchingBathBlockEntity.BOARD_SLOTS) {
            bath.items.setStackInSlot(1, mask());
            for (int i = 0; i < 450 && !bath.items.getStackInSlot(slot).is(PcbItems.L1_BOARD.get()); i++) bath.tick();
            check(bath.items.getStackInSlot(slot).is(PcbItems.L1_BOARD.get()), "рецепт на месте " + slot);
        }
        check(bath.batches == 4 && bath.input.getFluidAmount() == 3000 && bath.spent.getFluidAmount() == 1000,
            "четыре рецепта: 1000 мБ без потерь");
        check(Math.abs(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.COPPER_II) - 12) < .01,
            "медь сохраняется в отработке");
        var saved = bath.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        var restored = new EtchingBathBlockEntity(pos, bath.getBlockState());
        level.setBlockEntity(restored);
        restored.loadWithComponents(saved, level.registryAccess());
        check(restored.items.getSlots() == 6 && restored.input.getFluidAmount() == 3000
            && restored.spent.getFluidAmount() == 1000 && restored.items.getStackInSlot(5).is(PcbItems.L1_BOARD.get()),
            "сохранение четырёх плат и двух баков");
        for (int slot : EtchingBathBlockEntity.BOARD_SLOTS) {
            check(point.extract(arm, slot, true).is(PcbItems.L1_BOARD.get())
                && point.extract(arm, slot, false).is(PcbItems.L1_BOARD.get()), "рука забирает готовую плату " + slot);
        }
        // После проверки остаётся наглядный стенд с четырьмя вертикальными платами.
        for (int slot : EtchingBathBlockEntity.BOARD_SLOTS)
            restored.items.setStackInSlot(slot, new ItemStack(PcbItems.L1_BOARD_BLANK.get()));
        restored.items.setStackInSlot(1, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        industrial(level, pos.west(3), 2);
        industrial(level, pos.west(5), 3);
        System.out.println("ETCHING ALL PASS");
    }
    private static void industrial(ServerLevel level, BlockPos pos, int pcbLevel) {
        level.setBlockAndUpdate(pos, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
        var bath = (EtchingBathBlockEntity) level.getBlockEntity(pos);
        ItemStack design = new ItemStack(PcbItems.PCB_DESIGN.get());
        ee.extras.pcb.PcbTopology.attach(design, M11Test.topology(pcbLevel));
        var expected = design.get(PcbItems.PCB_TOPOLOGY.get()).copy();
        bath.items.setStackInSlot(1, design);
        for (int slot : EtchingBathBlockEntity.BOARD_SLOTS)
            bath.items.setStackInSlot(slot, new ItemStack(pcbLevel == 2 ? PcbItems.L2_PREPARED_BLANK.get() : PcbItems.L3_PREPARED_BLANK.get()));
        bath.input.setFluid(solution(1000, .12f));
        for (int i = 0; i < 3300; i++) bath.tick();
        check(bath.batches == 4 && bath.input.isEmpty() && bath.spent.getFluidAmount() == 1000,
            "L" + pcbLevel + ": четыре платы из одного бака");
        check(ItemStack.isSameItemSameComponents(bath.items.getStackInSlot(1), design)
            && bath.items.getStackInSlot(1).getCount() == 1, "L" + pcbLevel + ": общий чертёж не расходуется");
        for (int slot : EtchingBathBlockEntity.BOARD_SLOTS)
            check(bath.items.getStackInSlot(slot).is(pcbLevel == 2 ? PcbItems.L2_ETCHED_BOARD.get() : PcbItems.L3_ETCHED_BOARD.get())
                && expected.equals(bath.items.getStackInSlot(slot).get(PcbItems.PCB_TOPOLOGY.get())),
                "L" + pcbLevel + ": топология и via на месте " + slot);
        level.removeBlock(pos, false);
    }

}
