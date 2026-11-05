package ee.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;

/** Получение PET в настоящем Vat Destroy, а не только проверка наличия JSON. */
final class PetTest {
    private static final BlockPos VAT = new BlockPos(20, -60, 11);
    private static boolean thermostat;

    private PetTest() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rectest")
                .then(Commands.literal("petbuild").executes(PetTest::build))
                .then(Commands.literal("petprepare").executes(PetTest::prepare))
                .then(Commands.literal("petassert").executes(PetTest::assertPet)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PetTest::tick);
    }

    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (thermostat && event.getServer().overworld().getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) {
            vat.changeHeatingPower(vat.getTemperature() > 450.0F ? -30_000_000.0F : 30_000_000.0F);
        }
    }

    private static int build(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(VAT.offset(-2, -2, -4), VAT.offset(2, 2, 0))) {
            boolean inside = Math.abs(pos.getX() - VAT.getX()) <= 1 && Math.abs(pos.getY() - VAT.getY()) <= 1
                    && pos.getZ() >= VAT.getZ() - 3 && pos.getZ() < VAT.getZ();
            level.setBlock(pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.NETHERITE_BLOCK.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        level.setBlock(VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState().setValue(VatControllerBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL);
        VatControllerBlockEntity vat = vat(level);
        if (vat.getVatOptional().isEmpty()) vat.tryMakeVat();
        require(context, vat.getVatOptional().isPresent(), "Vat сформирован");
        require(context, LegacyReaction.REACTIONS.containsKey("ee_extras:pet_overall_synthesis"), "реакция PET загружена");
        return Command.SINGLE_SUCCESS;
    }

    private static int prepare(CommandContext<CommandSourceStack> context) {
        VatControllerBlockEntity vat = vat(context.getSource().getLevel());
        thermostat = true;
        vat.getGasTank().setFluid(FluidStack.EMPTY);
        vat.getLiquidTank().setFluid(FluidStack.EMPTY);
        for (int slot = 0; slot < vat.inventory.getSlots(); slot++) vat.inventory.extractItem(slot, 64, false);
        LegacyMixture mixture = new LegacyMixture()
                .addMolecule(DestroyMolecules.PARAXYLENE, 0.01F)
                .addMolecule(DestroyMolecules.ETHENE, 0.01F)
                .addMolecule(DestroyMolecules.OXYGEN, 0.03F);
        mixture.heat((450.0F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        vat.getGasTank().setFluid(MixtureFluid.of(1000, mixture));
        vat.updateCachedMixture();
        vat.setChanged();
        context.getSource().sendSuccess(() -> Component.literal("PET: параксилол, этен и кислород загружены"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertPet(CommandContext<CommandSourceStack> context) {
        VatControllerBlockEntity vat = vat(context.getSource().getLevel());
        int count = 0;
        for (int slot = 0; slot < vat.inventory.getSlots(); slot++) {
            ItemStack stack = vat.inventory.getStackInSlot(slot);
            if (stack.is(DestroyItems.POLYETHENE_TEREPHTHALATE.get())) count += stack.getCount();
        }
        thermostat = false;
        require(context, count > 0, "Vat произвёл PET, штук: " + count);
        return Command.SINGLE_SUCCESS;
    }

    private static VatControllerBlockEntity vat(ServerLevel level) {
        if (level.getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) return vat;
        throw new IllegalStateException("PET FAIL: на позиции стенда нет Vat");
    }

    private static void require(CommandContext<CommandSourceStack> context, boolean condition, String message) {
        context.getSource().sendSystemMessage(Component.literal("PET " + (condition ? "PASS: " : "FAIL: ") + message));
        if (!condition) throw new IllegalStateException("PET FAIL: " + message);
    }
}
