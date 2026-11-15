package tfc.destroy.extras.heat;

import blusunrize.immersiveengineering.api.tool.ExternalHeaterHandler;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import ie.extras.AnalogHeaterBlock;
import ie.extras.AnalogHeaterBlockEntity;
import ie.extras.Config;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.config.DestroyConfigs;

public interface VatElectricHeat {
    int IE_FE_PER_TICK = 20;

    void destroyExtras$acceptIeHeat(long gameTime);

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ExternalHeaterHandler.CAPABILITY, DestroyBlockEntityTypes.VAT_SIDE.get(),
            (wall, face) -> (available, redstone) -> {
                var level = wall.getLevel();
                // Направление назначается при сборке, поэтому проверяем его при вызове даже у кешированного capability.
                if (!redstone || available < IE_FE_PER_TICK || level == null || level.isClientSide()
                    || face == null || face != wall.direction || wall.getVatOptional().isEmpty()) return 0;
                ((VatElectricHeat) wall).destroyExtras$acceptIeHeat(level.getGameTime());
                return IE_FE_PER_TICK;
            });
    }

    // Масштаб Destroy: верхняя ступень электрического нагрева равна обычной горелке.
    static float maximumPower() {
        return DestroyConfigs.server().blocks.blazeBurnerHeatingPower.getF();
    }

    static float eePower(ResistiveHeaterBlockEntity heater, Direction face) {
        return face == Direction.UP ? maximumPower() * Mth.clamp(heater.heat, 0f, 1f) : 0f;
    }

    static float analogPower(BlockEntity heater, Direction face, float vatKelvin) {
        return ModList.get().isLoaded("ie_extras") ? Analog.power(heater, face, vatKelvin) : 0f;
    }

    // Загружается только при установленном IE Extras.
    final class Analog {
        private Analog() {}

        static float power(BlockEntity blockEntity, Direction face, float vatKelvin) {
            if (!(blockEntity instanceof AnalogHeaterBlockEntity heater)
                || heater.getBlockState().getValue(AnalogHeaterBlock.FACING) != face) return 0f;
            float difference = heater.temperature() - (vatKelvin - 273.15f);
            return maximumPower() * Mth.clamp(difference / Config.maxTemperature(), 0f, 1f);
        }
    }
}
