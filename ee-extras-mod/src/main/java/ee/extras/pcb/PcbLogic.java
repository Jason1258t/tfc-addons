package ee.extras.pcb;

import ee.extras.pcb.PcbCircuitDevice.Kind;

/** Цоколёвка и состояние простых микросхем; питание всегда занимает последние два вывода. */
final class PcbLogic {
    private PcbLogic() {}
    static boolean chip(Kind kind) { return kind == Kind.D_FLIP_FLOP || kind == Kind.COUNTER || kind == Kind.REGISTER || kind == Kind.DECODER; }
    static int inputs(Kind kind) { return switch (kind) { case D_FLIP_FLOP -> 3; case COUNTER, DECODER -> 2; case REGISTER -> 5; default -> 0; }; }
    static int outputs(Kind kind) { return switch (kind) { case D_FLIP_FLOP -> 2; case COUNTER, REGISTER, DECODER -> 4; default -> 0; }; }
    static int pins(Kind kind) { return inputs(kind) + outputs(kind) + 2; }
    static String pinName(Kind kind,int pin) {
        if (pin == pins(kind)-2) return "Питание + (3–6 В)";
        if (pin == pins(kind)-1) return "Земля";
        return switch (kind) {
            case D_FLIP_FLOP -> new String[]{"D","Такт","Сброс","Q","¬Q"}[pin];
            case COUNTER -> pin == 0 ? "Счёт" : pin == 1 ? "Сброс" : "Q"+(pin-2);
            case REGISTER -> pin < 4 ? "D"+pin : pin == 4 ? "Запись" : "Q"+(pin-5);
            case DECODER -> pin < 2 ? "A"+pin : "Y"+(pin-2);
            default -> throw new IllegalArgumentException("Не микросхема");
        };
    }
    static int next(Kind kind,int word,int inputs,int previous) {
        return switch (kind) {
            case D_FLIP_FLOP -> (inputs&4)!=0 ? 0 : rising(inputs,previous,1) ? inputs&1 : word;
            case COUNTER -> (inputs&2)!=0 ? 0 : rising(inputs,previous,0) ? (word+1)&15 : word;
            case REGISTER -> rising(inputs,previous,4) ? inputs&15 : word;
            case DECODER -> 1<<(inputs&3);
            default -> word;
        };
    }
    private static boolean rising(int inputs,int previous,int bit) { return (inputs & ~previous & (1<<bit)) != 0; }
    static boolean output(Kind kind,int word,int output) {
        return kind == Kind.D_FLIP_FLOP ? output == 0 ? (word&1)!=0 : (word&1)==0 : (word & (1<<output)) != 0;
    }
}
