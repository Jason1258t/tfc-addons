package tfc.destroy.extras.electrolyzer;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Строки очков инженера: состояние, прогресс, питание, мембрана; с Shift — содержимое баков. */
final class ElectrolyzerGoggles {
    private static final String INDENT = "    ";

    private ElectrolyzerGoggles() {}

    static boolean add(ElectrolyzerLogic.State state, List<Component> tooltip, boolean sneaking) {
        if (state == null) return false;
        tooltip.add(Component.literal(INDENT).append(Component.translatable("block.destroy_extras.electrolyzer")));
        ElectrolyzerLogic.Synced s = state.synced();
        tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.status",
            s.text().copy().withStyle(color(s.status())))));
        if (s.duration() > 0 && s.status() == ElectrolyzerStatus.RUNNING) {
            tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.progress",
                Math.round(100.0F * s.progress() / s.duration()))));
        }
        tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.power",
            String.format(Locale.ROOT, "%.0f", s.voltage()), String.format(Locale.ROOT, "%.1f", s.current()))));
        tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.membrane", s.membrane())));
        if (!sneaking) {
            tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.shift").withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }
        tank(tooltip, "input", state.input.getFluid(), ElectrolyzerLogic.INPUT_CAPACITY);
        tank(tooltip, "cathode_gas", state.cathodeGas.getFluid(), ElectrolyzerLogic.OUTPUT_CAPACITY);
        tank(tooltip, "anode_gas", state.anodeGas.getFluid(), ElectrolyzerLogic.OUTPUT_CAPACITY);
        tank(tooltip, "catholyte", state.catholyte.getFluid(), ElectrolyzerLogic.OUTPUT_CAPACITY);
        return true;
    }

    private static void tank(List<Component> tooltip, String role, FluidStack fluid, int capacity) {
        Component what = fluid.isEmpty() ? Component.translatable("goggles.destroy_extras.electrolyzer.empty") : fluid.getHoverName();
        tooltip.add(line(Component.translatable("goggles.destroy_extras.electrolyzer.tank." + role, what,
            fluid.getAmount(), capacity)).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent line(Component text) {
        return Component.literal(INDENT + " ").append(text);
    }

    private static ChatFormatting color(ElectrolyzerStatus status) {
        return switch (status) {
            case RUNNING -> ChatFormatting.GREEN;
            case IDLE -> ChatFormatting.GRAY;
            case WAITING_FOR_POWER, BLOCKED_OUTPUT -> ChatFormatting.GOLD;
            case NO_RECIPE, FAULT -> ChatFormatting.RED;
        };
    }
}
