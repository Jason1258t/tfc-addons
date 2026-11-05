package ee.extras.converter;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.EeExtras;
import ee.extras.cable.DuplexInputBlock;

/**
 * Кабельный преобразователь: как потребляющая половина двустороннего, но вместо двух проводов — один двухжильный кабель.
 * Ставится задней гранью на машину IE (или другой блок, принимающий FE). Для кабеля — как изолятор: все кабели соединены
 * жила к жиле, поэтому от него кабель идёт дальше к следующему станку. Плита и ввод — как у отвода фазы (art/cables.py).
 */
public class CableConverterBlock extends DuplexInputBlock {
    public CableConverterBlock(Properties properties) {
        super(properties);
    }

    /** Устройство — наследник устройства соединителя EE (тип getDevice у BundledWireTerminationBlock закреплён) */
    @Override
    @SuppressWarnings("unchecked")
    public SimulatedDeviceType<BundledWireTerminationDevice> getDevice() {
        return (SimulatedDeviceType<BundledWireTerminationDevice>) (SimulatedDeviceType<?>) EeExtras.CABLE_CONVERTER_DEVICE.get();
    }
}
