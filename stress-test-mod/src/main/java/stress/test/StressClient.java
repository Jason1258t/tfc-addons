package stress.test;

import net.minecraft.client.Minecraft;

/** Только клиент: грузится лишь при FMLEnvironment.dist.isClient() */
final class StressClient {
    static int fps() {
        return Minecraft.getInstance().getFps();
    }

    private StressClient() {
    }
}
