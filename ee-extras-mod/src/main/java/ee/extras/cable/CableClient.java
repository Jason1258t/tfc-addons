package ee.extras.cable;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;

/** Только клиент: провода, которые EE прислал для отрисовки */
final class CableClient {
    /** Подходит ли к соединителю четырёхжильный кабель — тогда ключ переключает фазу (решает сервер) */
    static boolean hasFourCore(CableEnd end) {
        if (end == null) return false;
        InWorldNode node = end.cable();
        for (Pair<InWorldNodeConnection, WireData> c : WireRenderer.getAllConnections())
            if (c.getFirst().isAny(node) && c.getSecond().wireType() == CableWires.FOUR_CORE.get()) return true;
        return false;
    }

    static boolean hasWires(BlockPos pos, int[] contacts) {
        InWorldNode first = new InWorldNode(contacts[0], pos);
        InWorldNode second = new InWorldNode(contacts[1], pos);
        for (Pair<InWorldNodeConnection, WireData> connection : WireRenderer.getAllConnections()) {
            if (connection.getFirst().isAny(first) || connection.getFirst().isAny(second)) return true;
        }
        return false;
    }

    private CableClient() {
    }
}
