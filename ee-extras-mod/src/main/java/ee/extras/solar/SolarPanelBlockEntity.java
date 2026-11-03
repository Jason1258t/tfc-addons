package ee.extras.solar;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import ee.extras.EeExtras;
import ee.extras.generator.GeneratorDevice;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/** До 240 Вт при 48 В днём; ночью, под крышей и в дождь выработка падает. */
public class SolarPanelBlockEntity extends SmartBlockEntity {
    public static final double MAX_POWER = 240;
    public static final double NOMINAL_VOLTAGE = 48;

    private GeneratorDevice device;
    private double factor;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.SOLAR_PANEL_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (device == null || !device.isValid()) device = DevicesSavedData.load(sl).getDevice(worldPosition, GeneratorDevice.class);
        if (device == null) return;
        factor = sunlight(sl);
        device.setSupply(MAX_POWER * factor, factor > 0.02 ? NOMINAL_VOLTAGE : 0);
    }

    private double sunlight(ServerLevel level) {
        if (!level.isDay() || !level.canSeeSky(worldPosition.above())) return 0;
        int sky = level.getBrightness(LightLayer.SKY, worldPosition.above());
        double weather = level.isThundering() ? 0.1 : level.isRaining() ? 0.35 : 1;
        return Math.max(0, Math.min(1, sky / 15.0 * weather));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
}
