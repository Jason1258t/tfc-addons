package tfc.destroy.extras.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GasCylinderItem extends Item {
    private final int liters;
    private final int limitBar;
    private final int capacity;

    GasCylinderItem(int liters, int limitBar) {
        super(new Properties().stacksTo(1));
        this.liters = liters;
        this.limitBar = limitBar;
        capacity = GasTankLogic.capacity(liters, limitBar);
    }

    FluidStack gas(ItemStack cylinder) {
        Contents contents = cylinder.get(GasFillingStation.GAS.get());
        return contents == null ? FluidStack.EMPTY
            : GasTankLogic.gasStack(contents.species(), Math.min(contents.amount(), capacity));
    }

    void setGas(ItemStack cylinder, FluidStack gas, int amount) {
        if (amount <= 0) cylinder.remove(GasFillingStation.GAS.get());
        else cylinder.set(GasFillingStation.GAS.get(),
            new Contents(GasTankLogic.gasSpecies(gas), Math.min(amount, capacity)));
    }

    record Contents(String species, int amount) {
        private static final Codec<String> SPECIES = Codec.STRING.validate(species ->
            species.equals("destroy:hydrogen") || species.equals("destroy:chlorine")
                ? DataResult.success(species) : DataResult.error(() -> "Неизвестный газ: " + species));
        static final Codec<Contents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SPECIES.fieldOf("species").forGetter(Contents::species),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Contents::amount)
        ).apply(instance, Contents::new));
    }

    int capacity() {
        return capacity;
    }

    Component status(ItemStack cylinder) {
        FluidStack gas = gas(cylinder);
        return Component.translatable("message.destroy_extras.gas_tank",
            gas.isEmpty() ? Component.translatable("message.destroy_extras.gas_tank.empty") : gas.getHoverName(),
            gas.getAmount(), capacity, GasTankLogic.pressure(gas.getAmount(), liters), limitBar, liters);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(status(stack));
    }
}
