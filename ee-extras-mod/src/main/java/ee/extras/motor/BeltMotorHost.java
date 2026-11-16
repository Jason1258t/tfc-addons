package ee.extras.motor;

import net.minecraft.core.Direction;

/** Связь штатной блок-сущности с установленным приводом. */
public interface BeltMotorHost {
    String MOUNT_SIDE_TAG = "EeExtrasDriveSide";
    Direction eeExtras$mountSide();
    void eeExtras$mountSide(Direction side);
    void eeExtras$drive(float rpm, float capacityPerRpm);
    void eeExtras$readMotorPower(BeltMotorDevice device, double rmsVoltage);
    float eeExtras$networkStress();
    float eeExtras$networkCapacity();
}
