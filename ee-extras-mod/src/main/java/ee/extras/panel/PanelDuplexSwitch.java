package ee.extras.panel;

import com.george_vi.electroenergetics.content.cut_off_switch.SwitchingBehaviour;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CutOffSwitchPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

final class PanelDuplexSwitch extends CutOffSwitchPanelAttachment {
    private SwitchingBehaviour secondPole;

    PanelDuplexSwitch(PanelAttachmentType type) {
        super(type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridge(bridges, 0, behaviour);
        bridge(bridges, 1, secondPole);
    }

    private void bridge(BridgeCollector bridges, int pole, SwitchingBehaviour contact) {
        double resistance = contact.resistance();
        if (resistance < 1.0E10)
            bridges.bridge(nodes[pole], nodes[pole + 3], ElectricalProperties.resistor(resistance));
    }

    @Override
    public void postTick(SimulationResults results) {
        behaviour.isClosed = isClosed;
        behaviour.postTick(results.getVoltageAt(nodes[0], nodes[3]), getCenter(), level);
        secondPole.isClosed = isClosed;
        secondPole.postTick(results.getVoltageAt(nodes[1], nodes[4]), getCenter(), level);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        secondPole = new SwitchingBehaviour(tag.getCompound("SecondPole"));
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.write(tag, clientPacket, registries);
        tag.put("SecondPole", secondPole.write());
    }
}
