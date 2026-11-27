package ee.extras.machine;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.Containers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Щитковые органы управления без клемм и участия в электрической симуляции. */
final class MachineControls {
    final KineticBlockEntity owner;
    final PanelAttachment[] modules = new PanelAttachment[2];
    final Direction[] faces = {Direction.SOUTH, Direction.SOUTH};
    private CompoundTag pending;
    MachineControls(KineticBlockEntity owner) { this.owner = owner; }
    static void register() { NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, MachineControls::interact); }

    private static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof MotorMachine machine)) return;
        var controls = machine.drive().controls;
        controls.tick();
        var hit = event.getHitVec();
        if (!hit.getDirection().getAxis().isHorizontal()) return;
        var point = hit.getLocation().subtract(event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());
        if (point.y < .35 || point.y > .8) return;
        double x = switch (hit.getDirection()) {
            case SOUTH -> point.x;
            case NORTH -> 1 - point.x;
            case EAST -> 1 - point.z;
            default -> point.z;
        };
        if (x > .4 && x < .6) return;
        int slot = x < 0.5 ? 0 : 1;
        var module = controls.modules[slot];
        var stack = event.getItemStack();
        var player = event.getEntity();
        var type = slot == 0 ? CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get() : CEEPanelAttachmentTypes.ESTOP.get();
        if (module == null) {
            if (stack.getItem() != type.item.asItem()) return;
            if (!event.getLevel().isClientSide) {
                module = type.createNew(event.getPos(), new InWorldNode[0], event.getLevel(),
                        ElectricalPanelSlot.FULL_SLOT, hit.getDirection(), event.getLevel().registryAccess());
                if (module instanceof EStopPanelAttachment stop) stop.isClosed = true;
                controls.modules[slot] = module;
                controls.faces[slot] = hit.getDirection();
                if (!player.isCreative()) stack.shrink(1);
            }
        } else {
            if (controls.faces[slot] != hit.getDirection()) return;
            if (!event.getLevel().isClientSide) {
                if (AllItems.WRENCH.isIn(stack) && player.isShiftKeyDown()) {
                    if (!player.isCreative()) player.getInventory().placeItemBackInInventory(module.defaultDroppedStack());
                    controls.modules[slot] = null;
                } else if (module instanceof CutOffSwitchPanelAttachment toggle) {
                    if (stack.getItem() == type.item.asItem() || stack.getItem() instanceof DyeItem)
                        module.onInteract(stack, player, event.getHand(), hit);
                    else if (stack.isEmpty()) toggle.isClosed = !toggle.isClosed;
                } else if (stack.isEmpty()) module.onInteract(stack, player, event.getHand(), hit);
            }
        }
        if (!event.getLevel().isClientSide) {
            controls.owner.setChanged();
            controls.owner.sendData();
            if (!controls.enabled()) machine.drive().setRunning(false);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    void tick() {
        if (pending == null || owner.getLevel() == null) return;
        for (int i = 0; i < modules.length; i++) {
            modules[i] = null;
            if (!pending.contains("Slot" + i)) continue;
            var data = pending.getCompound("Slot" + i);
            var face = Direction.byName(data.getString("Face"));
            faces[i] = face != null && face.getAxis().isHorizontal() ? face : Direction.SOUTH;
            var type = i == 0 ? CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get() : CEEPanelAttachmentTypes.ESTOP.get();
            var module = type.createNew(owner.getBlockPos(), new InWorldNode[0], owner.getLevel(),
                    ElectricalPanelSlot.FULL_SLOT, faces[i], owner.getLevel().registryAccess());
            module.read(data, false, owner.getLevel().registryAccess());
            modules[i] = module;
        }
        pending = null;
    }
    boolean enabled() {
        tick();
        return (modules[0] == null || ((CutOffSwitchPanelAttachment) modules[0]).isClosed)
                && (modules[1] == null || ((EStopPanelAttachment) modules[1]).isClosed);
    }
    void drop() {
        tick();
        if (owner.getLevel() == null || owner.getLevel().isClientSide) return;
        for (var module : modules) if (module != null) {
            var pos = owner.getBlockPos();
            Containers.dropItemStack(owner.getLevel(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    module.defaultDroppedStack());
        }
    }
    void write(CompoundTag tag, boolean clientPacket) {
        tick();
        if (pending != null) { tag.put("MachineControls", pending.copy()); return; }
        var data = new CompoundTag();
        for (int i = 0; i < modules.length; i++) if (modules[i] != null) {
            var module = new CompoundTag();
            modules[i].write(module, clientPacket, owner.getLevel().registryAccess());
            module.putString("Face", faces[i].getName());
            data.put("Slot" + i, module);
        }
        tag.put("MachineControls", data);
    }
    void read(CompoundTag tag, boolean clientPacket) {
        pending = tag.getCompound("MachineControls").copy();
        tick();
    }
}
