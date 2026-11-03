package ee.extras.solar;

import com.simibubi.create.foundation.block.IBE;
import ee.extras.EeExtras;
import ee.extras.Layout;
import ee.extras.PowerBlock;
import ee.extras.Tier;
import ee.extras.generator.GeneratorDevice;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Плоская солнечная панель с двумя клеммами постоянного тока. */
public class SolarPanelBlock extends PowerBlock<GeneratorDevice> implements IBE<SolarPanelBlockEntity> {
    public SolarPanelBlock(Properties properties) {
        super(properties, Layout.COMPACT_GENERATOR, Tier.LIGHT, () -> EeExtras.SOLAR_PANEL_DEVICE.get());
    }

    @Override
    public Class<SolarPanelBlockEntity> getBlockEntityClass() {
        return SolarPanelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SolarPanelBlockEntity> getBlockEntityType() {
        return EeExtras.SOLAR_PANEL_BE.get();
    }
}
