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
        return fluid.copyWithAmount(stored(parts, fluid));
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
        int stored = stored(parts, resource);
        int filled = (int) Math.min(resource.getAmount(), capacity(parts) - stored);
        if (filled > 0 && action.execute()) {
            spread(parts, resource, stored + filled);
            port.queueComparatorUpdate();
        }
        return Math.max(filled, 0);
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
        if (!accepts(parts, resource)) {
            return drainInOrder(parts, resource, action);
        }
        int stored = stored(parts, resource);
        int drained = Math.min(resource.getAmount(), stored);
        if (drained > 0 && action.execute()) {
            spread(parts, resource, stored - drained);
            port.queueComparatorUpdate();
        }
        return drained > 0 ? resource.copyWithAmount(drained) : FluidStack.EMPTY;
    }

    /** Сеть с разными жидкостями (соединили два непустых бака): сливает по секциям сверху вниз, как раньше. */
    private FluidStack drainInOrder(List<IronTankEntity> parts, FluidStack resource, FluidAction action) {
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

    /**
     * Раскладывает amount жидкости по сети слоями: нижний слой заполняется целиком, следующий делится между
     * секциями поровну по их ёмкости, так что уровень в соседних секциях одного слоя одинаковый. Лёгкая
     * жидкость (газ) копится сверху. Секции, где количество не изменилось, не трогает.
     */
    static void spread(List<IronTankEntity> parts, FluidStack fluid, int amount) {
        var order = fluid.getFluid().getFluidType().isLighterThanAir() ? HEIGHT.reversed() : HEIGHT;
        var sorted = ordered(parts, order);
        int remaining = amount;
        for (int start = 0; start < sorted.size(); ) {
            int y = sorted.get(start).getBlockPos().getY();
            int end = start;
            long layerCapacity = 0;
            while (end < sorted.size() && sorted.get(end).getBlockPos().getY() == y) {
                layerCapacity += sorted.get(end).sectionCapacity();
                end++;
            }
            var layer = sorted.subList(start, end);
            int[] shares = new int[layer.size()];
            if (remaining >= layerCapacity) {
                for (int i = 0; i < shares.length; i++) {
                    shares[i] = layer.get(i).sectionCapacity();
                }
            } else {
                int left = remaining;
                for (int i = 0; i < shares.length; i++) {
                    shares[i] = (int) ((long) remaining * layer.get(i).sectionCapacity() / layerCapacity);
                    left -= shares[i];
                }
                // Остаток от деления — по одному мB первым секциям слоя.
                for (int i = 0; left > 0; i = (i + 1) % shares.length) {
                    if (shares[i] < layer.get(i).sectionCapacity()) {
                        shares[i]++;
                        left--;
                    }
                }
            }
            for (int i = 0; i < shares.length; i++) {
                remaining -= shares[i];
                var tank = layer.get(i).getTankInventory();
                // Ёмкость хранилища секции — по секции, как её считает сеть: иначе setFluid положит больше, чем влезет
                if (tank.getCapacity() != layer.get(i).sectionCapacity()) {
                    tank.setCapacity(layer.get(i).sectionCapacity());
                }
                if (tank.getFluidAmount() != shares[i]) {
                    tank.setFluid(shares[i] == 0 ? FluidStack.EMPTY : fluid.copyWithAmount(shares[i]));
                }
            }
            start = end;
        }
    }

    private static int stored(List<IronTankEntity> parts, FluidStack fluid) {
        int total = 0;
        for (IronTankEntity part : parts) {
            FluidStack stored = part.getTankInventory().getFluid();
            if (FluidStack.isSameFluidSameComponents(stored, fluid)) {
                total += stored.getAmount();
            }
        }
        return total;
    }

    private static long capacity(List<IronTankEntity> parts) {
        return parts.stream().mapToLong(IronTankEntity::sectionCapacity).sum();
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
