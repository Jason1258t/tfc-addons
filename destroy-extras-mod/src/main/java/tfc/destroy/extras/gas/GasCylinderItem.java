package tfc.destroy.extras.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GasCylinderItem extends BlockItem {
    private final int liters;
    private final int limitBar;
    private final int capacity;

    GasCylinderItem(GasCylinderBlock block, int liters, int limitBar) {
        super(block, new Properties().stacksTo(1));
        this.liters = liters;
        this.limitBar = limitBar;
        capacity = GasTankLogic.capacity(liters, limitBar);
    }

    FluidStack gas(ItemStack cylinder) {
        Contents contents = cylinder.get(GasFillingStation.GAS.get());
        return gas(contents);
    }

    FluidStack gas(Contents contents) {
        if (contents == null) return FluidStack.EMPTY;
        int volume = (int) Math.min(contents.amount(), Math.floor(capacity / (double) contents.concentration()));
        return volume == 0 ? FluidStack.EMPTY : GasTankLogic.gasStack(contents.species(), volume, contents.concentration());
    }

    void setGas(ItemStack cylinder, FluidStack gas) {
        if (gas.isEmpty()) cylinder.remove(GasFillingStation.GAS.get());
        else cylinder.set(GasFillingStation.GAS.get(), Contents.of(gas));
    }

    record Contents(String species, int amount, float concentration) {
        static Contents of(FluidStack gas) {
            return new Contents(GasTankLogic.gasSpecies(gas), gas.getAmount(), GasTankLogic.concentration(gas));
        }

        private static final Codec<String> SPECIES = Codec.STRING.validate(species ->
            GasTankLogic.supports(species)
                ? DataResult.success(species) : DataResult.error(() -> "Неизвестный газ: " + species));
        static final Codec<Contents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SPECIES.fieldOf("species").forGetter(Contents::species),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Contents::amount),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("concentration", 1.0f)
                .forGetter(Contents::concentration)
        ).apply(instance, Contents::new));
    }

    int capacity() {
        return capacity;
    }

    Component status(ItemStack cylinder) {
        return status(gas(cylinder));
    }

    Component status(FluidStack gas) {
        return Component.translatable("message.destroy_extras.gas_tank",
            gas.isEmpty() ? Component.translatable("message.destroy_extras.gas_tank.empty") : gas.getHoverName(),
            GasTankLogic.quantity(gas), capacity, GasTankLogic.pressure(gas, liters), limitBar, liters);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(status(stack));
        tooltip.add(Component.translatable("tooltip.destroy_extras.gas_cylinder.port"));
    }
}
