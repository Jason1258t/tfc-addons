package ee.extras.pcb;

import ee.extras.pcb.PcbCircuitDevice.Kind;

/** Общий каталог: редактор, монтаж и модели используют одно описание модуля. */
final class PcbModules {
    record Module(Kind kind, String name, String model, int width, int height, int level) {
        int pins() { return kind.controlled() ? 4 : kind == Kind.POTENTIOMETER ? 3 : 2; }
        String pinName(int pin) {
            if (kind == Kind.POTENTIOMETER) return switch (pin) { case 0 -> "A"; case 1 -> "B"; default -> "Ползунок W"; };
            if (kind == Kind.CLOCK && pin >= 2) return pin == 2 ? "Питание +" : "Питание −";
            if (kind.controlled()) return switch (pin) { case 0 -> "Контакт A"; case 1 -> "Контакт B"; case 2 -> "Управление +"; default -> "Управление −"; };
            if (kind == Kind.LED || kind == Kind.DIODE) return pin == 0 ? "Анод +" : "Катод −";
            return pin == 0 ? "A" : "B";
        }
        boolean resistanceSetting() {
            return switch (kind) { case RESISTOR, RELAY, LAMP, LED, CAPACITOR, RHEOSTAT, POTENTIOMETER, MECHANICAL_TIMER, CLOCK -> true; default -> false; };
        }
        boolean thresholdSetting() {
            return switch (kind) { case RELAY, LAMP, LED, FUSE, VOLTMETER, AMMETER, TRANSISTOR, REDSTONE_OUT -> true; default -> false; };
        }
        boolean timed() { return kind == Kind.MECHANICAL_TIMER || kind == Kind.CLOCK; }
        boolean adjustable() { return kind == Kind.RHEOSTAT || kind == Kind.POTENTIOMETER; }
        boolean input() { return kind == Kind.BUTTON || kind == Kind.SWITCH || adjustable() || timed(); }
    }

    static final Module[] ALL = {
            new Module(Kind.RESISTOR, "Резистор", "resistor", 3, 1, 1),
            new Module(Kind.RELAY, "Реле", "relay", 3, 3, 1),
            new Module(Kind.BUTTON, "Кнопка", "button", 2, 2, 1),
            new Module(Kind.SWITCH, "Переключатель", "switch", 2, 3, 1),
            new Module(Kind.LAMP, "Индикатор", "neon_bulb", 2, 2, 1),
            new Module(Kind.REDSTONE_IN, "Редстоун-вход", "redstone_relay", 3, 2, 1),
            new Module(Kind.REDSTONE_OUT, "Редстоун-выход", "redstone_relay", 3, 2, 1),
            new Module(Kind.CAPACITOR, "Конденсатор", "capacitor", 2, 2, 2),
            new Module(Kind.DIODE, "Диод", "diode", 2, 1, 2),
            new Module(Kind.FUSE, "Предохранитель", "fuse", 3, 1, 1),
            new Module(Kind.VOLTMETER, "Вольтметр", "voltage_gauge", 3, 3, 2),
            new Module(Kind.AMMETER, "Амперметр", "current_gauge", 3, 3, 2),
            new Module(Kind.TRANSISTOR, "Транзистор", "bjt_npn", 2, 2, 2),
            new Module(Kind.LED, "Светодиод", "led", 1, 1, 1),
            new Module(Kind.RHEOSTAT, "Реостат", "rheostat", 2, 4, 1),
            new Module(Kind.POTENTIOMETER, "Ползунок", "potentiometer", 2, 4, 1),
            new Module(Kind.MECHANICAL_TIMER, "Механический таймер", "mechanical_timer", 3, 3, 1),
            new Module(Kind.CLOCK, "Клок", "clock", 3, 3, 2)
    };

    private PcbModules() {}
    static Module get(int definition) { return ALL[PcbWiring.type(definition)]; }
}
