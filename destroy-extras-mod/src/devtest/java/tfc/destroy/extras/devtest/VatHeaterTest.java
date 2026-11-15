package tfc.destroy.extras.devtest;

import blusunrize.immersiveengineering.common.blocks.metal.FurnaceHeaterBlockEntity;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import ie.extras.AnalogHeaterBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;
import tfc.destroy.extras.heat.VatElectricHeat;

final class VatHeaterTest {
    private static final BlockPos CONTROLLER = new BlockPos(4, -56, 5);
    private static final BlockPos CENTER = CONTROLLER.north(2);
    private static final BlockPos HEATER = CENTER.below(3);
    private static final BlockPos SIGNAL = HEATER.east();

    private VatHeaterTest() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("vatheatbuild").executes(ctx -> {
                ServerLevel level = ctx.getSource().getLevel();
                level.removeBlock(HEATER, false);
                level.removeBlock(SIGNAL, false);
                for (BlockPos pos : BlockPos.betweenClosed(CENTER.offset(-2, -2, -2), CENTER.offset(2, 2, 2))) {
                    int faces = (Math.abs(pos.getX() - CENTER.getX()) == 2 ? 1 : 0)
                        + (Math.abs(pos.getY() - CENTER.getY()) == 2 ? 1 : 0)
                        + (Math.abs(pos.getZ() - CENTER.getZ()) == 2 ? 1 : 0);
                    level.setBlockAndUpdate(pos, faces == 1 ? Blocks.NETHERITE_BLOCK.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(CONTROLLER, DestroyBlocks.VAT_CONTROLLER.getDefaultState()
                    .setValue(VatControllerBlock.FACING, Direction.SOUTH));
                var vat = (VatControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                if (vat == null || (vat.getVatOptional().isEmpty() && !vat.tryMakeVat())) {
                    throw new IllegalStateException("DESTROYEXTRATEST FAIL: чан не собрался");
                }
                // Воздух маленького пустого чана быстро набирает давление даже с обычной горелкой.
                var water = new LegacyMixture().addMolecule(DestroyMolecules.WATER, DestroyMolecules.WATER.getPureConcentration());
                if (vat.addFluid(MixtureFluid.of(24000, water), FluidAction.EXECUTE) != 24000) {
                    throw new IllegalStateException("DESTROYEXTRATEST FAIL: вода не вошла в чан");
                }
                return 1;
            }))
            .then(Commands.literal("vatheatsource").then(Commands.argument("block", StringArgumentType.string())
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    String id = StringArgumentType.getString(ctx, "block");
                    Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow();
                    level.removeBlock(SIGNAL, false);
                    level.setBlockAndUpdate(HEATER, block.defaultBlockState());
                    if (level.getBlockEntity(HEATER) instanceof FurnaceHeaterBlockEntity ie) {
                        ie.energyStorage.setStoredEnergy(32000);
                    } else if (id.equals("ie_extras:analog_heater")) {
                        var analog = (AnalogHeaterBlockEntity) level.getBlockEntity(HEATER);
                        analog.energy.setStored(32000);
                    }
                    return 1;
                })))
            .then(Commands.literal("vatheatee").then(Commands.argument("heat", FloatArgumentType.floatArg(0, 1))
                .executes(ctx -> {
                    var heater = (ResistiveHeaterBlockEntity) ctx.getSource().getLevel().getBlockEntity(HEATER);
                    heater.heat = FloatArgumentType.getFloat(ctx, "heat");
                    return 1;
                })))
            .then(Commands.literal("vatheatassert")
                .then(Commands.argument("min", FloatArgumentType.floatArg(0))
                    .then(Commands.argument("max", FloatArgumentType.floatArg(0)).executes(ctx -> {
                        ServerLevel level = ctx.getSource().getLevel();
                        var vat = (VatControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                        if (vat == null || vat.getVatOptional().isEmpty()) {
                            ctx.getSource().sendFailure(Component.literal("DESTROYEXTRATEST FAIL: чан разрушен"));
                            return 0;
                        }
                        float power = vat.saveWithoutMetadata(level.registryAccess()).getFloat("HeatingPower")
                            / VatElectricHeat.maximumPower();
                        float min = FloatArgumentType.getFloat(ctx, "min");
                        float max = FloatArgumentType.getFloat(ctx, "max");
                        boolean ok = power >= min && power <= max;
                        String message = "DESTROYEXTRATEST " + (ok ? "PASS" : "FAIL") + " доля мощности: " + power
                            + " (ожидается " + min + "–" + max + "), температура "
                            + vat.getTemperature() + " K, давление " + vat.getPressure() + " Па";
                        if (ok) ctx.getSource().sendSuccess(() -> Component.literal(message), false);
                        else ctx.getSource().sendFailure(Component.literal(message));
                        return ok ? 1 : 0;
                    })))));
    }
}
