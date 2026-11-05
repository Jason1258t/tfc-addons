package tfcstudio.betterfluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TankNetwork implements IFluidHandler {
    private static final Comparator<IronTankEntity> HEIGHT = Comparator.comparingInt(tank -> tank.getBlockPos().getY());
    private final IronTankEntity port;

    TankNetwork(IronTankEntity port) {
        this.port = port;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) {
            return FluidStack.EMPTY;
        }
        var parts = parts();
        var fluid = firstFluid(parts);
        int total = 0;
        for (IronTankEntity part : parts) {
            FluidStack stored = part.getTankInventory().getFluid();
            if (FluidStack.isSameFluidSameComponents(stored, fluid)) {
                total += stored.getAmount();
            }
        }
        return fluid.copyWithAmount(total);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? parts().stream().mapToInt(IronTankEntity::sectionCapacity).sum() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack fluid) {
        return tank == 0 && accepts(parts(), fluid);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        var parts = parts();
        if (resource.isEmpty() || !accepts(parts, resource) || clientExecution(action)) {
            return 0;
        }
        parts = ordered(parts, HEIGHT);
        int remaining = resource.getAmount();
        for (IronTankEntity part : parts) {
            remaining -= part.getTankInventory().fill(resource.copyWithAmount(remaining), action);
            if (remaining == 0) {
                break;
            }
        }
        if (remaining != resource.getAmount() && action.execute()) {
            port.queueComparatorUpdate();
        }
        return resource.getAmount() - remaining;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return drain(parts(), resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        var parts = parts();
        return drain(parts, firstFluid(parts).copyWithAmount(maxDrain), action);
    }

    private FluidStack drain(List<IronTankEntity> parts, FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || clientExecution(action)) {
            return FluidStack.EMPTY;
        }
        parts = ordered(parts, HEIGHT.reversed());
        int remaining = resource.getAmount();
        for (IronTankEntity part : parts) {
            remaining -= part.getTankInventory().drain(resource.copyWithAmount(remaining), action).getAmount();
            if (remaining == 0) {
                break;
            }
        }
        if (remaining != resource.getAmount() && action.execute()) {
            port.queueComparatorUpdate();
        }
        return resource.copyWithAmount(resource.getAmount() - remaining);
    }

    private boolean clientExecution(FluidAction action) {
        return action.execute() && port.getLevel() != null && port.getLevel().isClientSide;
    }

    private List<IronTankEntity> parts() {
        return port.networkParts();
    }

    private static List<IronTankEntity> ordered(List<IronTankEntity> parts, Comparator<IronTankEntity> order) {
        var sorted = new ArrayList<>(parts);
        sorted.sort(order.thenComparing(IronTankEntity::getBlockPos));
        return sorted;
    }

    private static FluidStack firstFluid(List<IronTankEntity> parts) {
        return parts.stream().filter(part -> !part.getTankInventory().isEmpty())
            .min(HEIGHT.thenComparing(IronTankEntity::getBlockPos))
            .map(part -> part.getTankInventory().getFluid())
            .orElse(FluidStack.EMPTY);
    }

    private static boolean accepts(List<IronTankEntity> parts, FluidStack fluid) {
        return !parts.isEmpty() && parts.stream().allMatch(part -> {
            var stored = part.getTankInventory().getFluid();
            return stored.isEmpty() || FluidStack.isSameFluidSameComponents(stored, fluid);
        });
    }
}
