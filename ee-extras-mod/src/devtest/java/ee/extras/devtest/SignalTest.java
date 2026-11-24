package ee.extras.devtest;

import ee.extras.redstone.*;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.AnalogLeverPanelAttachment;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import com.mojang.brigadier.CommandDispatcher;

/** Проверяется настоящий граф EE, включая сохранение, разрыв, каналы и прибор щитка. */
final class SignalTest {
    static final BlockPos PANEL = new BlockPos(1,-58,1), A = new BlockPos(9,-58,1), B = new BlockPos(12,-58,1), C = new BlockPos(15,-58,1);
    static InWorldNode node(BlockPos pos) { return new InWorldNode(Signals.BASE,pos); }
    static SignalDevice device(net.minecraft.server.level.ServerLevel l, BlockPos p) { return DevicesSavedData.load(l).getDevice(p,SignalDevice.class); }
    static InWorldNode panel(net.minecraft.server.level.ServerLevel l) {
        var be=(ElectricalPanelBlockEntity)l.getBlockEntity(PANEL);
        for(var a:be.getAttachments()) if(a instanceof AnalogLeverPanelAttachment) return new InWorldNode(Signals.BASE+a.slot.ordinal(),PANEL);
        throw new IllegalStateException("SIGNAL FAIL: рычаг отсутствует");
    }
    static void connect(net.minecraft.server.level.ServerLevel l, InWorldNode a, InWorldNode b) {
        for(var n:new InWorldNode[]{a,b}) {
            var state=l.getBlockState(n.sourcePos());
            ((ElectricalDeviceBlock<?>)state.getBlock()).ensureNodesExist(l,n.sourcePos(),state);
        }
        InfrastructureSavedData.load(l).connect(a,b,Signals.WIRE.get());
    }
    static void require(boolean ok,String why) { if(!ok) throw new IllegalStateException("SIGNAL FAIL: "+why); }
    static int build(CommandSourceStack src) {
        var l=src.getLevel();
        DevTest.place(l,PANEL,CEEBlocks.ELECTRICAL_PANEL.getDefaultState());
        var be=(ElectricalPanelBlockEntity)l.getBlockEntity(PANEL);
        var type=CEERegistries.PANEL_ATTACHMENT_TYPE.get(ResourceLocation.parse("electroenergetics:analog_lever"));
        require(type!=null,"тип рычага");
        var slot=ElectricalPanelSlot.QUARTER_LEFT_UPPER;
        var a=type.createNew(PANEL,type.mode.getNodesFor(PANEL,slot),l,slot,be.getBlockState().getValue(ElectricalPanelBlock.FACING),l.registryAccess());
        be.getAttachments()[slot.ordinal()]=a;
        ((AnalogLeverPanelAttachment)a).redstoneSignal=11;
        be.attachmentUpdate();
        for(var p:new BlockPos[]{A,B,C}) {DevTest.place(l,p,Signals.CONNECTOR.get().defaultBlockState());device(l,p).output=true;}
        device(l,B).channel=DyeColor.RED.getId();
        connect(l,panel(l),node(A));connect(l,node(A),node(B));connect(l,node(B),node(C));connect(l,node(A),node(C));
        // Сплошная стена пересекает путь; её наличие не запрещает соединение.
        for(int y=-60;y<=-55;y++)for(int z=0;z<4;z++)l.setBlockAndUpdate(new BlockPos(5,y,z),Blocks.STONE.defaultBlockState());
        DevicesSavedData.load(l).setDirty();
        src.sendSuccess(()->Component.literal("SIGNAL BUILD: рычаг 11, стена, цикл, белые выходы A/C, красный B"),false);return 1;
    }
    static int check(CommandSourceStack src,int expected) {
        var l=src.getLevel();
        require(device(l,A).signal==expected,"A="+device(l,A).signal+", ожидалось "+expected);
        require(device(l,C).signal==expected,"C="+device(l,C).signal);
        require(device(l,B).signal==0,"красный канал смешан с белым");
        require(l.getSignal(A,Direction.DOWN)==expected,"сигнал блока");
        require((InfrastructureSavedData.load(l).getConnections(panel(l)).isEmpty())==(expected==0),"связи щитка");
        src.sendSuccess(()->Component.literal("SIGNAL PASS: A/C="+expected+", B=0, сигнал блока, сохранённый граф"),false);return 1;
    }
    static int channels(CommandSourceStack src) {
        var l=src.getLevel();
        var input=new BlockPos(18,-58,1);var output=new BlockPos(22,-58,1);
        DevTest.place(l,input,Signals.CONNECTOR.get().defaultBlockState());
        DevTest.place(l,output,Signals.CONNECTOR.get().defaultBlockState());
        var in=device(l,input);var out=device(l,output);out.output=true;
        connect(l,node(input),node(output));
        l.setBlockAndUpdate(input.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        for(int ch=0;ch<16;ch++) {
            in.channel=ch;out.channel=ch;
            Signals.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true,l));
            require(out.signal==15,"канал "+ch);
            out.channel=(ch+1)%16;
            Signals.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true,l));
            require(out.signal==0,"утечка канала "+ch);
        }
        var stack=new net.minecraft.world.item.ItemStack(Signals.SPOOL.get(),3);
        var player=CapTest.player(l,stack);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var panelNode=panel(l);
        var face=l.getBlockState(PANEL).getValue(ElectricalPanelBlock.FACING);
        for(var n:new InWorldNode[]{panelNode,node(input)}) {
            var hit=new net.minecraft.world.phys.BlockHitResult(n.getPosition(l),n.sourcePos().equals(PANEL)?face:Direction.UP,n.sourcePos(),false);
            var result=stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            require(result.consumesAction(),"клик катушкой");
        }
        require(stack.getCount()==2,"расход катушки");
        require(InfrastructureSavedData.load(l).isConnected(panelNode,node(input)),"катушка не создала провод");
        in.channel=0;out.channel=0;
        Signals.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true,l));
        require(device(l,A).signal==15,"максимум двух источников");
        l.setBlockAndUpdate(input.below(),Blocks.AIR.defaultBlockState());
        Signals.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true,l));
        require(device(l,A).signal==11,"сброс более сильного источника");
        InfrastructureSavedData.load(l).removeConnectionNoDrops(new InWorldNodeConnection(panelNode,node(input)));
        src.sendSuccess(()->Component.literal("SIGNAL PASS: 16 каналов, соседний = 0, вход блока, 2 клика, расход 1 катушки, максимум 15 → 11"),false);return 1;
    }
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("signaltest")
            .then(Commands.literal("channels").executes(c->channels(c.getSource())))
            .then(Commands.literal("build").executes(c->build(c.getSource())))
            .then(Commands.literal("check").executes(c->check(c.getSource(),11)))
            .then(Commands.literal("negative").executes(c->check(c.getSource(),10)))
            .then(Commands.literal("off").executes(c->{var l=c.getSource().getLevel();var data=InfrastructureSavedData.load(l);for(var edge:data.getConnections(panel(l)))data.removeConnectionNoDrops(edge);return 1;}))
            .then(Commands.literal("checkoff").executes(c->check(c.getSource(),0))));
    }
}
