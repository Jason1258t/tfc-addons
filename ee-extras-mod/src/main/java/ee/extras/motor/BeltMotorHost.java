package ee.extras.motor;

/** Связь существующей блок-сущности ремня с двигателем на той же позиции. */
public interface BeltMotorHost {
    void eeExtras$drive(float rpm, float capacityPerRpm);
    void eeExtras$readMotorPower(BeltMotorDevice device, double rmsVoltage);
    float eeExtras$networkStress();
    float eeExtras$networkCapacity();
}
