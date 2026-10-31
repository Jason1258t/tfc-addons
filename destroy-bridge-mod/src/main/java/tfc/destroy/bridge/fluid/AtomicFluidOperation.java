package tfc.destroy.bridge.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Операция для одного входа и нескольких независимых выходов; вызывается только на основном серверном потоке. */
public final class AtomicFluidOperation {
    private AtomicFluidOperation() {}

    /** Сначала проверяет всю партию и место под каждый продукт, ничего не меняя в баках. */
    public static boolean canExecute(FluidTank input, FluidStack consumed, FluidTank[] outputs, FluidStack[] products) {
        if (outputs.length == 0 || outputs.length != products.length || consumed.isEmpty()) return false;
        FluidStack drained = input.drain(consumed, FluidAction.SIMULATE);
        if (drained.getAmount() != consumed.getAmount()
            || !FluidStack.isSameFluidSameComponents(consumed, drained)) return false;
        for (int i = 0; i < outputs.length; i++) {
            if (products[i].isEmpty() || outputs[i].fill(products[i], FluidAction.SIMULATE) != products[i].getAmount()) {
                return false;
            }
        }
        return true;
    }

    /** Выполняет заранее проверенную операцию целиком; при любом отказе до исполнения не появляется частичный продукт. */
    public static boolean execute(FluidTank input, FluidStack consumed, FluidTank[] outputs, FluidStack[] products) {
        if (!canExecute(input, consumed, outputs, products)) return false;
        FluidStack drained = input.drain(consumed, FluidAction.EXECUTE);
        if (drained.getAmount() != consumed.getAmount()
            || !FluidStack.isSameFluidSameComponents(consumed, drained)) {
            throw new IllegalStateException("Бак входа изменился между проверкой и исполнением операции");
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i].fill(products[i], FluidAction.EXECUTE) != products[i].getAmount()) {
                throw new IllegalStateException("Бак выхода изменился между проверкой и исполнением операции");
            }
        }
        return true;
    }
}
