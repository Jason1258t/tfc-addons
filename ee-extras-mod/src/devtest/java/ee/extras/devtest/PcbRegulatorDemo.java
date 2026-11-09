package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import ee.extras.pcb.*;
import ee.extras.cable.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Полноценный проект из редактора: никаких скрытых перемычек в скомпилированной схеме. */
final class PcbRegulatorDemo {
    static final int TRANSFORMER=17,BRIDGE=23,CAP=84,REGISTER=165,WRITE=129;
    private static final int[] OUTPUTS={170,173,218,221};
    static CompoundTag topology(boolean capacitor) {
        var t=PcbTopology.create(2);int[] nets=new int[512];Arrays.fill(nets,-1);boolean[] blocked=new boolean[512];var endpoints=new ArrayList<List<Integer>>();for(int i=0;i<10;i++)endpoints.add(new ArrayList<>());
        add(t,nets,blocked,endpoints,TRANSFORMER,27,new int[]{0,1},0,9);
        add(t,nets,blocked,endpoints,BRIDGE,29,new int[]{0,1,2,3},0,0);
        if(capacitor)add(t,nets,blocked,endpoints,CAP,7,new int[]{2,3},100,0);
        add(t,nets,blocked,endpoints,89,30,new int[]{2,3,9,3},0,0);
        add(t,nets,blocked,endpoints,REGISTER,25,new int[]{9,3,9,3,4,5,6,7,8,9,3},0,0);
        add(t,nets,blocked,endpoints,WRITE,3,new int[]{4,9,3},0,0);
        for(int i=0;i<4;i++)add(t,nets,blocked,endpoints,OUTPUTS[i],0,new int[]{5+i,3},1000,0);
        for(int net=0;net<10;net++) {var points=endpoints.get(net);for(int i=1;i<points.size();i++)route(nets,blocked,points.get(0)+256,points.get(i)+256,net);}
        for(int i=0;i<512;i++)if(nets[i]>=0) {
            int cell=i%256;boolean via=nets[cell]>=0&&nets[cell]==nets[cell+256];
            PcbTopology.set(t,i/256,cell,via?PcbTopology.VIA:PcbTopology.TRACE);
        }
        for(int layer=0;layer<2;layer++)for(int cell=0;cell<256;cell++)for(int direction:new int[]{1,3}) {
            int next=direction==1?(cell%16==15?-1:cell+1):(cell>=240?-1:cell+16);
            if(next>=0&&nets[layer*256+cell]>=0&&nets[layer*256+next]>=0&&nets[layer*256+cell]!=nets[layer*256+next])PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",t,layer,cell,direction,true);
        }
        String error=PcbTopology.validate(t);PcbBridgeTest.check(error==null,"демонстрационный проект: "+error);return t;
    }
    static void add(CompoundTag t,int[] nets,boolean[] blocked,List<List<Integer>> endpoints,int anchor,int type,int[] pinNets,double nominal,double threshold) {
        int def=PcbModulesTest.definition(type,0);PcbTopology.set(t,0,anchor,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,anchor,def);
        Object module;try{module=PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbModules"),null,"get",def);}catch(Exception e){throw new IllegalStateException(e);}
        int width=PcbModulesTest.call(module.getClass(),module,"width"),height=PcbModulesTest.call(module.getClass(),module,"height");
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int cell=anchor+y*16+x;PcbBridgeTest.check(nets[cell]<0,"корпус пересёк вывод");blocked[cell]=true;}
        for(int pin=0;pin<pinNets.length;pin++) {int cell=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",anchor,def,pin);int net=pinNets[pin];PcbBridgeTest.check(!blocked[cell]&&(nets[cell]<0||nets[cell]==net),"пересечение выводов "+cell);nets[cell]=nets[cell+256]=net;endpoints.get(net).add(cell);}
        var parameters=new CompoundTag();if(nominal>0)parameters.putDouble("Nominal",nominal);if(threshold>0)parameters.putDouble("Threshold",threshold);
        try{PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbParameters"),null,"put",t,anchor,parameters);}catch(Exception e){throw new IllegalStateException(e);}
    }
    static void route(int[] nets,boolean[] blocked,int start,int end,int net) {
        int[] previous=new int[512];Arrays.fill(previous,-1);var queue=new ArrayDeque<Integer>();queue.add(start);previous[start]=start;
        while(!queue.isEmpty()&&previous[end]<0) {int at=queue.remove();int cell=at%256,layer=at/256;int[] next={cell%16>0?at-1:-1,cell%16<15?at+1:-1,cell>=16?at-16:-1,cell<240?at+16:-1,at^256};
            for(int n:next)if(n>=0&&previous[n]<0&&!blocked[n]&&(nets[n]<0||nets[n]==net)) {previous[n]=at;queue.add(n);}}
        PcbBridgeTest.check(previous[end]>=0,"маршрут сети "+net+" не найден");int at=end;while(at!=start){nets[at]=net;at=previous[at];}nets[start]=net;
    }
    static int build(CommandSourceStack s,boolean capacitor) {
        var l=s.getLevel();var board=PcbBridgeTest.BOARD;var input=board.east(5);PcbBridgeTest.BATTERY=board.east(8);
        for(var pos:new net.minecraft.core.BlockPos[]{board,input,PcbBridgeTest.BATTERY})DevTest.place(l,pos,Blocks.AIR.defaultBlockState());
        var t=topology(capacitor);var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);
        DevTest.place(l,board,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(board),"setBoard",stack);
        List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);
        PcbBridgeTest.check(parts.size()==(capacitor?10:9),"демонстрация содержит только видимые модули: "+parts.size());
        PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"setCircuit",parts,PcbCircuitDevice.Rating.L2,0);
        DevTest.place(l,input,Extras.eeTermination());DevTest.place(l,PcbBridgeTest.BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,true));
        var end=Extras.end(l,input);DevTest.wire(l,PcbBridgeTest.BATTERY,0,input,end.conductor(1).id());DevTest.wire(l,PcbBridgeTest.BATTERY,1,input,end.conductor(2).id());
        PcbBridgeTest.check(CableLaying.connect(l,CableKind.DUPLEX,end,CableEnd.of(l,board,l.getBlockState(board))),"кабель подключён к настоящему вводу трансформатора");PcbBridgeTest.power(s,300,true);
        s.sendSuccess(()->Component.literal("Демонстрация: трансформатор → мост → "+(capacitor?"100 мФ → ":"")+"стабилизатор5В → регистр, данные5, запись переключателем, выходы нагружены1кОм; проект сохранён на плате"),false);return 1;
    }
    static int expect(CommandSourceStack s,int word,boolean powered) {
        var saved=PcbBridgeTest.snapshot(s);var parts=saved.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND);int register=-1;
        for(int i=0;i<parts.size();i++)if(parts.getCompound(i).getString("Kind").equals("REGISTER"))register=i;
        var m=PcbBridgeTest.measurement(s);PcbBridgeTest.check(!saved.getBoolean("Burnt")&&register>=0&&saved.getIntArray("LogicWords")[register]==word,"регистр: "+saved.getIntArray("LogicWords")[Math.max(0,register)]+", "+m);
        if(powered)PcbRegulatorTest.expect(s,5);
        PcbBridgeTest.balance(m);
        var transformer=saved.getCompound("OnboardPower");
        PcbBridgeTest.check(Math.abs(transformer.getDouble("InputWatts")-transformer.getDouble("LossWatts")-m.getDouble("InputWatts"))<1e-5,"энергобаланс всей цепи от трансформатора до моста: "+transformer+", "+m);
        var results=PcbBridgeTest.RESULTS.get(s.getLevel());PcbBridgeTest.check(results!=null,"есть независимые отсчёты EE");var registerPart=parts.getCompound(register);int[] pins=registerPart.getIntArray("Pins");
        double[] ground=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(registerPart.getInt("B"),PcbBridgeTest.BOARD));
        double[] supply=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(registerPart.getInt("A"),PcbBridgeTest.BOARD));
        if(powered)for(int sample=0;sample<supply.length;sample++)PcbBridgeTest.check(supply[sample]-ground[sample]>=4.97&&supply[sample]-ground[sample]<=5.03,"независимое питание регистра5В");
        double independentPower=0;
        for(int sample=0;sample<supply.length;sample++)independentPower+=(supply[sample]-ground[sample])*(supply[sample]-ground[sample])/10000/supply.length;
        for(int output=0;output<4;output++) {
            double[] measured=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(pins[5+output],PcbBridgeTest.BOARD));
            for(int sample=0;sample<measured.length;sample++) {double outputVoltage=measured[sample]-ground[sample];independentPower+=outputVoltage*outputVoltage/1000*1.1/measured.length;double expected=powered&&(word&(1<<output))!=0?(supply[sample]-ground[sample])*1000/1100:0;PcbBridgeTest.check(Math.abs(measured[sample]-ground[sample]-expected)<.03,"реальный нагруженный выход"+output+" не соответствует слову"+word);}
        }
        double capacitorPortPower=0;
        for(int i=0;i<parts.size();i++)if(parts.getCompound(i).getString("Kind").equals("CAPACITOR")){
            var cap=parts.getCompound(i);int maxNodes=4*(PcbTopology.SIZE-1)+PcbTopology.CELLS*PcbTopology.MAX_LAYERS;
            var va=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(cap.getInt("A"),PcbBridgeTest.BOARD));
            var vb=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(cap.getInt("B"),PcbBridgeTest.BOARD));
            var sense=results.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(maxNodes+i,PcbBridgeTest.BOARD));
            for(int j=0;j<va.length;j++)capacitorPortPower+=(va[j]-vb[j])*(sense[j]-vb[j])/.1/va.length;
        }
        PcbBridgeTest.check(Math.abs(m.getDouble("OutputWatts")-capacitorPortPower-PcbRegulatorTest.measurement(s).getDouble("InputWatts"))<1e-5,"баланс моста, внешнего конденсатора и стабилизатора: "+capacitorPortPower+", "+m+", "+PcbRegulatorTest.measurement(s));
        PcbBridgeTest.check(Math.abs(PcbRegulatorTest.measurement(s).getDouble("OutputWatts")-independentPower)<1e-5,"независимая мощность регистра с четырьмя резисторами: "+independentPower+", "+PcbRegulatorTest.measurement(s));
        s.sendSuccess(()->Component.literal("Демонстрация: слово"+word+", "+m),false);return 1;
    }
    static int write(CommandSourceStack s) {
        var saved=PcbBridgeTest.snapshot(s);var parts=saved.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND);
        int ordinal=0;for(int i=0;i<parts.size();i++) {var p=parts.getCompound(i);if(p.getInt("ModuleIndex")==WRITE) {PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"interactComponent",ordinal,s.getLevel().getGameTime(),false);return 1;}if(!p.getString("Kind").equals("TRACE"))ordinal++;}throw new IllegalStateException("Нет переключателя записи");
    }
}
