package ee.extras.pcb;

import ee.extras.pcb.PcbCircuitDevice.Kind;

/** Общий каталог: редактор, монтаж и модели используют одно описание модуля. */
final class PcbModules {
    enum PinType {
        CONTACT("К", 0xFFE8CD94), ANALOG("А", 0xFF87D3C5), LOGIC("Л", 0xFFB0B6F5), POWER("П", 0xFFE9A175), GROUND("З", 0xFFABC4AC), BUS("Ш", 0xFFD3A8DE);
        final String symbol;
        final int color;
        PinType(String symbol, int color) { this.symbol = symbol; this.color = color; }
    }
    record Module(Kind kind, String name, String model, int width, int height, int level, int sections) {
        Module(Kind kind, String name, String model, int width, int height, int level) { this(kind, name, model, width, height, level, 1); }
        int pins() { return PcbLogic.chip(kind) ? PcbLogic.pins(kind) : kind == Kind.CLOCK || kind == Kind.STEP_UP_TRANSFORMER || kind == Kind.BRIDGE_RECTIFIER ? 4 : sections > 1 ? 2 * sections : kind == Kind.RELAY || kind == Kind.LATCHING_RELAY || kind == Kind.COMPARATOR ? 5 : kind == Kind.POTENTIOMETER || kind == Kind.SWITCH || kind == Kind.THREE_POSITION_SWITCH || kind == Kind.TRANSISTOR || kind == Kind.MECHANICAL_TIMER ? 3 : kind.controlled() ? 4 : 2; }
        String pinName(int pin) {
            if (kind == Kind.BRIDGE_RECTIFIER) return switch (pin) { case 0 -> "~A"; case 1 -> "~B"; case 2 -> "+"; default -> "−"; };
            if (kind == Kind.STEP_UP_TRANSFORMER) return switch (pin) { case 0 -> "Вход ~ A (до 48 В)"; case 1 -> "Вход ~ B"; case 2 -> "Выход ВВ ~ A"; default -> "Выход ВВ ~ B"; };
            if (kind.supply()) return pin == 0 ? "Выход ~ A" : "Выход ~ B";
            if (PcbLogic.chip(kind)) return PcbLogic.pinName(kind,pin);
            if (sections > 1) return "Секция " + (pin / 2 + 1) + (pin % 2 == 0 ? " +" : " −");
            if (kind == Kind.COMPARATOR) return switch (pin) { case 0 -> "Вход"; case 1 -> "Порог"; case 2 -> "Выход"; case 3 -> "Питание + (3–6 В)"; default -> "Земля"; };
            if (kind == Kind.THREE_POSITION_SWITCH) return switch (pin) { case 0 -> "Общий"; case 1 -> "Левый I"; default -> "Правый II"; };
            if (kind == Kind.MECHANICAL_TIMER) return switch (pin) { case 0 -> "A / возврат взвода"; case 1 -> "B"; default -> "Взвод"; };
            if (kind == Kind.SWITCH) return switch (pin) { case 0 -> "Общий"; case 1 -> "НО"; default -> "НЗ"; };
            if (kind == Kind.TRANSISTOR) return switch (pin) { case 0 -> "Коллектор"; case 1 -> "Эмиттер"; default -> "База"; };
            if (kind == Kind.POTENTIOMETER) return switch (pin) { case 0 -> "Верх"; case 1 -> "Низ"; default -> "Движок"; };
            if (kind == Kind.CLOCK) return switch (pin) { case 0 -> "Разрешение"; case 1 -> "Выход"; case 2 -> "Питание + (3–6 В)"; default -> "Земля"; };
            if (kind == Kind.LATCHING_RELAY) return switch (pin) { case 0 -> "Общий"; case 1 -> "НО"; case 2 -> "SET"; case 3 -> "Обмотки −"; default -> "RESET"; };
            if (kind == Kind.RELAY) return switch (pin) { case 0 -> "Общий"; case 1 -> "НО"; case 2 -> "Обмотка +"; case 3 -> "Обмотка −"; default -> "НЗ"; };
            if (kind.controlled()) return switch (pin) { case 0 -> "Контакт A"; case 1 -> "Контакт B"; case 2 -> "Управление +"; default -> "Управление −"; };
            if (kind.led() || kind == Kind.DIODE) return pin == 0 ? "Анод +" : "Катод −";
            return pin == 0 ? "A" : "B";
        }
        PinType pinType(int pin) {
            if (kind == Kind.BRIDGE_RECTIFIER) return pin == 3 ? PinType.GROUND : PinType.POWER;
            if (kind.supply() || kind == Kind.STEP_UP_TRANSFORMER) return PinType.POWER;
            if (PcbLogic.chip(kind)) return pin == pins()-2 ? PinType.POWER : pin == pins()-1 ? PinType.GROUND : PinType.LOGIC;
            if (kind == Kind.COMPARATOR) return pin < 2 ? PinType.ANALOG : pin == 2 ? PinType.LOGIC : pin == 3 ? PinType.POWER : PinType.GROUND;
            if (kind == Kind.TRANSISTOR && pin == 2) return PinType.LOGIC;
            if (kind == Kind.POTENTIOMETER || kind == Kind.VOLTMETER) return PinType.ANALOG;
            if (kind == Kind.CLOCK) return pin < 2 ? PinType.LOGIC : pin == 2 ? PinType.POWER : PinType.GROUND;
            return PinType.CONTACT;
        }
        String pinLabel(int pin) { return pinType(pin).symbol + " " + pinName(pin); }
        String section() {
            return switch (kind) {
                case BUTTON, SWITCH, THREE_POSITION_SWITCH, REDSTONE_IN, POTENTIOMETER -> "Управление";
                case LAMP, LED, LED_PANEL, LED_STRIP, REDSTONE_OUT, VOLTMETER, AMMETER -> "Индикация";
                case RESISTOR, RHEOSTAT, CAPACITOR, FUSE -> "Пассивные";
                case ONBOARD_TRANSFORMER, STEP_UP_TRANSFORMER, BRIDGE_RECTIFIER -> "Питание";
                case MECHANICAL_TIMER, CLOCK -> "Время";
                case COMPARATOR, D_FLIP_FLOP, COUNTER, REGISTER, DECODER -> "Логика";
                default -> "Коммутация";
            };
        }
        boolean matches(String query) {
            StringBuilder text = new StringBuilder(name).append(' ').append(section()).append(' ')
                    .append(width).append('×').append(height).append(' ').append(width).append('x').append(height);
            if (kind == Kind.STEP_UP_TRANSFORMER) text.append(" высоковольтный вв переменный AC");
            if (kind.supply()) text.append(" бп блок питания вход 300 кабель переменный");
            for (int pin = 0; pin < pins(); pin++) text.append(' ').append(pinLabel(pin));
            String haystack = text.toString().toLowerCase(java.util.Locale.ROOT);
            for (String word : query.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+"))
                if (!haystack.contains(word)) return false;
            return true;
        }
        boolean resistanceSetting() {
            return switch (kind) { case RESISTOR, RELAY, LATCHING_RELAY, LAMP, LED, LED_PANEL, LED_STRIP, CAPACITOR, RHEOSTAT, POTENTIOMETER, MECHANICAL_TIMER, CLOCK -> true; default -> false; };
        }
        boolean thresholdSetting() {
            return switch (kind) { case ONBOARD_TRANSFORMER, RELAY, LATCHING_RELAY, LAMP, LED, LED_PANEL, LED_STRIP, FUSE, VOLTMETER, AMMETER, TRANSISTOR, REDSTONE_OUT -> true; default -> false; };
        }
        boolean timed() { return kind == Kind.MECHANICAL_TIMER || kind == Kind.CLOCK; }
        boolean adjustable() { return kind == Kind.RHEOSTAT || kind == Kind.POTENTIOMETER || kind == Kind.THREE_POSITION_SWITCH; }
        boolean input() { return kind.supply() || kind == Kind.STEP_UP_TRANSFORMER || kind == Kind.BUTTON || kind == Kind.SWITCH || adjustable() || timed(); }
    }

    static final Module[] ALL = {
            new Module(Kind.RESISTOR, "Резистор", "resistor", 1, 1, 1),
            new Module(Kind.RELAY, "Реле", "relay", 2, 2, 1),
            new Module(Kind.BUTTON, "Кнопка", "button", 2, 2, 1),
            new Module(Kind.SWITCH, "Переключатель", "switch", 2, 3, 1),
            new Module(Kind.LAMP, "Лампа", "light_bulb", 2, 2, 1),
            new Module(Kind.REDSTONE_IN, "Редстоун-вход", "redstone_relay", 1, 1, 1),
            new Module(Kind.REDSTONE_OUT, "Редстоун-выход", "redstone_relay", 1, 1, 1),
            new Module(Kind.CAPACITOR, "Конденсатор", "capacitor", 1, 1, 2),
            new Module(Kind.DIODE, "Диод", "diode", 1, 1, 2),
            new Module(Kind.FUSE, "Предохранитель", "fuse", 1, 1, 1),
            new Module(Kind.VOLTMETER, "Вольтметр", "voltage_gauge", 1, 1, 2),
            new Module(Kind.AMMETER, "Амперметр", "current_gauge", 1, 1, 2),
            new Module(Kind.TRANSISTOR, "Транзистор", "bjt_npn", 1, 1, 2),
            new Module(Kind.LED, "Светодиод", "led", 1, 1, 1),
            new Module(Kind.RHEOSTAT, "Реостат", "rheostat", 2, 4, 1),
            new Module(Kind.POTENTIOMETER, "Ползунок", "potentiometer", 2, 5, 1),
            new Module(Kind.MECHANICAL_TIMER, "Механический таймер", "mechanical_timer", 3, 3, 1),
            new Module(Kind.CLOCK, "Клок", "clock", 2, 2, 2),
            new Module(Kind.LED_PANEL, "Световая панель", "led_panel", 2, 2, 1),
            new Module(Kind.LED_STRIP, "Световая полоса", "led_strip", 2, 5, 1, 5),
            new Module(Kind.LATCHING_RELAY, "Реле SET/RESET", "latching_relay", 2, 3, 1),
            new Module(Kind.THREE_POSITION_SWITCH, "Переключатель на 3 положения", "selector", 2, 3, 1),
            new Module(Kind.COMPARATOR, "Компаратор", "comparator", 2, 2, 2),
            new Module(Kind.D_FLIP_FLOP, "D-триггер", "flip_flop", 2, 2, 2),
            new Module(Kind.COUNTER, "Счётчик 4 бита", "counter", 2, 3, 2),
            new Module(Kind.REGISTER, "Регистр 4 бита", "register", 3, 2, 2),
            new Module(Kind.DECODER, "Дешифратор 2→4", "decoder", 2, 3, 2),
            new Module(Kind.ONBOARD_TRANSFORMER, "Бортовой трансформатор", "onboard_transformer", 3, 3, 1),
            new Module(Kind.STEP_UP_TRANSFORMER, "Повышающий трансформатор ×10", "step_up_transformer", 3, 3, 1),
            new Module(Kind.BRIDGE_RECTIFIER, "Мостовой выпрямитель", "bridge_rectifier", 3, 3, 2)
    };

    private PcbModules() {}
    static Module get(int definition) { return ALL[PcbWiring.type(definition)]; }
}
