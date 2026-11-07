package ee.extras.pcb;

import com.george_vi.electroenergetics.CEEBlocks;
import ee.extras.pcb.PcbCircuitDevice.Kind;
import ee.extras.pcb.PcbCircuitDevice.Part;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PcbAssembly {
    static final String ASSEMBLED = "Assembled";
    static final String MISSING = "Для сборки не хватает деталей";
    static final String RECOVERED = "Плата разобрана. Найдены исправные детали";
    static final String NO_RECOVERY = "Плата разобрана. Исправных деталей не осталось";

    private PcbAssembly() {}

    static Item item(Kind kind) {
        return switch (kind) {
            case TRACE -> Items.AIR;
            case RESISTOR -> PcbItems.RESISTOR.get();
            case RHEOSTAT -> PcbItems.RHEOSTAT.get();
            case POTENTIOMETER -> PcbItems.POTENTIOMETER.get();
            case MECHANICAL_TIMER -> PcbItems.MECHANICAL_TIMER.get();
            case CLOCK -> PcbItems.CLOCK.get();
            case RELAY -> CEEBlocks.RELAY.asItem();
            case LATCHING_RELAY -> PcbItems.LATCHING_RELAY.get();
            case BUTTON -> Items.STONE_BUTTON;
            case SWITCH -> Items.LEVER;
            case THREE_POSITION_SWITCH -> PcbItems.SELECTOR.get();
            case LAMP -> CEEBlocks.INDICATOR_BULB.asItem();
            case LED -> PcbItems.LED.get();
            case LED_PANEL -> PcbItems.LED_PANEL.get();
            case LED_STRIP -> PcbItems.LED_STRIP.get();
            case REDSTONE_IN, REDSTONE_OUT -> PcbItems.CONNECTOR.get();
            case CAPACITOR -> PcbItems.CAPACITOR.get();
            case DIODE -> PcbItems.SILICON_DIODE.get();
            case FUSE -> CEEBlocks.FUSE.asItem();
            case VOLTMETER -> CEEBlocks.VOLTMETER.asItem();
            case AMMETER -> CEEBlocks.AMMETER.asItem();
            case TRANSISTOR -> PcbItems.TRANSISTOR.get();
        };
    }

    static Map<Item, Integer> requirements(List<Part> parts) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        var seen = new java.util.HashSet<Integer>();
        for (Part part : parts) {
            if (part.moduleIndex() >= 0 && !seen.add(part.moduleIndex())) continue;
            Item item = item(part.kind());
            if (item != Items.AIR) needed.merge(item, 1, Integer::sum);
        }
        return needed;
    }

    static boolean consume(Player player, List<Part> parts) {
        if (player == null) return false;
        if (player.getAbilities().instabuild) return true;
        Map<Item, Integer> needed = requirements(parts);
        var inventory = player.getInventory();
        for (var entry : needed.entrySet()) {
            int count = inventory.items.stream().filter(stack -> stack.is(entry.getKey())).mapToInt(ItemStack::getCount).sum();
            if (count < entry.getValue()) return false;
        }
        for (var entry : needed.entrySet()) {
            int remaining = entry.getValue();
            for (ItemStack stack : inventory.items) {
                if (!stack.is(entry.getKey())) continue;
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
                if (remaining == 0) break;
            }
        }
        inventory.setChanged();
        return true;
    }

    static int salvage(Player player, List<Part> parts, long blownFuses) {
        int recovered = 0;
        List<Item> candidates = new ArrayList<>();
        var seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).moduleIndex() >= 0 && !seen.add(parts.get(i).moduleIndex())) continue;
            Kind kind = parts.get(i).kind();
            Item item = item(kind);
            if (item == Items.AIR || (kind == Kind.FUSE && (blownFuses & (1L << i)) != 0)) continue;
            candidates.add(item);
            float chance = switch (kind) {
                case BUTTON, SWITCH, REDSTONE_IN, REDSTONE_OUT -> 0.70f;
                case TRANSISTOR, DIODE, LAMP, LED, LED_PANEL, LED_STRIP -> 0.45f;
                default -> 0.60f;
            };
            if (player.getRandom().nextFloat() < chance) {
                player.getInventory().placeItemBackInInventory(new ItemStack(item));
                recovered++;
            }
        }
        if (recovered == 0 && !candidates.isEmpty() && player.getRandom().nextFloat() < 0.85f) {
            Item spared = candidates.get(player.getRandom().nextInt(candidates.size()));
            player.getInventory().placeItemBackInInventory(new ItemStack(spared));
            return 1;
        }
        return recovered;
    }
}
