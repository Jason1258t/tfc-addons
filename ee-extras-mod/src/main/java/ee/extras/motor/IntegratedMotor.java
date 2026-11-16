package ee.extras.motor;

/** Электрическая модель двигателя EE 1.1.2 с отдельным номиналом для встроенного привода. */
public final class IntegratedMotor {
    public static final double NOMINAL_VOLTAGE = 300.0;
    private static final double LOAD_FACTOR = 0.8;
    private static final double IDLE_FACTOR = 6.0;

    private final double baseResistance;

    public IntegratedMotor(double nominalStress) {
        if (!Double.isFinite(nominalStress) || nominalStress <= 0) {
            throw new IllegalArgumentException("Номинальная мощность вращения должна быть положительной");
        }
        baseResistance = NOMINAL_VOLTAGE * NOMINAL_VOLTAGE / nominalStress;
    }

    /** Сопротивление в симуляторе EE зависит от загрузки всей сети Create. */
    public double resistance(float currentStress, float availableStress) {
        double load = availableStress > 0 ? (double) currentStress / availableStress : 0.0;
        if (Double.isNaN(load)) load = 0.0;
        load = Math.clamp(load, 0.05, 1.0);
        return LOAD_FACTOR * Math.min(IDLE_FACTOR * baseResistance, baseResistance / load);
    }

    /** Доступные SU из действующего напряжения на клеммах, включая просадку в проводах. */
    public double availableStress(double rmsVoltage) {
        return rmsVoltage * rmsVoltage / baseResistance;
    }

    /** Create умножает мощность источника на модуль генерируемых оборотов. */
    public float capacityPerRpm(double rmsVoltage, float generatedRpm) {
        return generatedRpm == 0 ? 0 : (float) (availableStress(rmsVoltage) / Math.abs(generatedRpm));
    }
}
