package ee.extras.pcb;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class PcbClient {
    private PcbClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(PcbItems.CIRCUIT_BE.get(), context -> new PcbBoardRenderer()));
        modBus.addListener((RegisterMenuScreensEvent event) -> event.register(PcbItems.EDITOR_MENU.get(), PcbEditorScreen::new));
    }
}
