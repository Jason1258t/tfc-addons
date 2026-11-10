package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ee.extras.pcb.*;
import ee.extras.cable.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Две полноценные платы и девять штатных проводов EE. */
@net.neoforged.fml.common.EventBusSubscriber(modid="ee_extras_devtest")
final class PcbConnectorTest {
 static BlockPos TX=new BlockPos(21230,300,-11477),RX=TX.east(3);
 static final int SOCKET=55;
 static final int[] CONTROL={2,27,66,91,130,155,194,219}, RX_CONTROL={18,27,82,91,146,155,210,219}, LED={52,12,116,76,180,140,244,204};
 static void check(boolean ok,String text){PcbBridgeTest.check(ok,"Разъём: "+text);}
 static PcbCircuitDevice device(CommandSourceStack s,BlockPos at){return DevicesSavedData.load(s.getLevel()).getDevice(at,PcbCircuitDevice.class);}
 static CompoundTag saved(CommandSourceStack s,BlockPos at){var t=new CompoundTag();device(s,at).write(t);return t;}
 static List<PcbCircuitDevice.Part> parts(CompoundTag t){return PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);}
 static PcbCircuitDevice.Part connector(CommandSourceStack s,BlockPos at){return parts(topology(s,at)).stream().filter(p->p.kind()==PcbCircuitDevice.Kind.CONNECTOR_8).findFirst().orElseThrow();}
 static CompoundTag topology(CommandSourceStack s,BlockPos at){return PcbModulesTest.call(PcbCircuitBlockEntity.class,s.getLevel().getBlockEntity(at),"topology");}
 static CompoundTag design(boolean receive){
  var t=PcbTopology.create(2);int[] nets=new int[512];Arrays.fill(nets,-1);boolean[] blocked=new boolean[512];var ends=new ArrayList<List<Integer>>();for(int i=0;i<32;i++)ends.add(new ArrayList<>());
  PcbRegulatorDemo.add(t,nets,blocked,ends,SOCKET,35,new int[]{8,9,10,11,12,13,14,15,3},0,0);
  for(int i=0;i<8;i++) {
   add(t,nets,blocked,ends,receive?RX_CONTROL[i]:CONTROL[i],receive?22:3,receive?new int[]{8+i,3,16+i,4,3}:new int[]{8+i,4,3},i%2==0?2:0);
   if(receive)PcbRegulatorDemo.add(t,nets,blocked,ends,LED[i],13,new int[]{16+i,3},3300,2);
  }
  for(int[] pair:new int[][]{{0,4},{240,3}}){nets[pair[0]]=nets[pair[0]+256]=pair[1];ends.get(pair[1]).add(pair[0]);}
  routeAll(nets,blocked,ends);
  for(int i=0;i<512;i++)if(nets[i]>=0){int c=i%256;PcbTopology.set(t,i/256,c,nets[c]>=0&&nets[c]==nets[c+256]?PcbTopology.VIA:PcbTopology.TRACE);}
  for(int l=0;l<2;l++)for(int c=0;c<256;c++)for(int d:new int[]{1,3}){int n=d==1?(c%16==15?-1:c+1):(c>=240?-1:c+16);if(n>=0&&nets[l*256+c]>=0&&nets[l*256+n]>=0&&nets[l*256+c]!=nets[l*256+n])PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",t,l,c,d,true);}
  PcbTopology.set(t,0,0,PcbTopology.TERMINAL);PcbTopology.set(t,0,240,PcbTopology.TERMINAL);
  check(PcbTopology.validate(t)==null,"проект "+PcbTopology.validate(t));check(parts(t)!=null,"компиляция проекта");return t;
 }
 static CompoundTag supplyDesign(){
  var t=PcbTopology.create(2);int[] nets=new int[512];Arrays.fill(nets,-1);boolean[] blocked=new boolean[512];var ends=new ArrayList<List<Integer>>();for(int i=0;i<5;i++)ends.add(new ArrayList<>());
  PcbRegulatorDemo.add(t,nets,blocked,ends,17,27,new int[]{0,1},0,9);
  PcbRegulatorDemo.add(t,nets,blocked,ends,23,29,new int[]{0,1,2,3},0,0);
  PcbRegulatorDemo.add(t,nets,blocked,ends,84,7,new int[]{2,3},100,0);
  PcbRegulatorDemo.add(t,nets,blocked,ends,89,30,new int[]{2,3,4,3},0,0);
  for(int[] pair:new int[][]{{159,4},{224,3}}){nets[pair[0]]=nets[pair[0]+256]=pair[1];ends.get(pair[1]).add(pair[0]);}
  routeAll(nets,blocked,ends);
  finish(t,nets);PcbTopology.set(t,0,159,PcbTopology.TERMINAL);PcbTopology.set(t,0,224,PcbTopology.TERMINAL);check(PcbTopology.validate(t)==null,"проект источника");return t;
 }
    static void add(CompoundTag t,int[] nets,boolean[] blocked,List<List<Integer>> endpoints,int anchor,int type,int[] pinNets,int rotation) {
        int def=PcbModulesTest.definition(type,rotation);PcbTopology.set(t,0,anchor,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,anchor,def);
        Object module;try{module=PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbModules"),null,"get",def);}catch(Exception e){throw new IllegalStateException(e);}
        int width=PcbModulesTest.call(module.getClass(),module,"width"),height=PcbModulesTest.call(module.getClass(),module,"height");
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int cell=anchor+y*16+x;PcbBridgeTest.check(nets[cell]<0,"корпус пересёк вывод");blocked[cell]=true;}
        for(int pin=0;pin<pinNets.length;pin++) {int cell=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",anchor,def,pin);int net=pinNets[pin];PcbBridgeTest.check(!blocked[cell]&&(nets[cell]<0||nets[cell]==net),"пересечение выводов "+cell);nets[cell]=nets[cell+256]=net;endpoints.get(net).add(cell);}
    }
 static void routeAll(int[] nets,boolean[] blocked,List<List<Integer>> ends){
  int[] fixed=nets.clone(),usage=new int[512],history=new int[512];
  var order=new ArrayList<Integer>();for(int n=0;n<ends.size();n++)if(!ends.get(n).isEmpty())order.add(n);
  BitSet[] routes=new BitSet[ends.size()];for(int n:order)routes[n]=new BitSet(512);
  var random=new Random(30120);
  for(int iteration=0;iteration<2000;iteration++){
   Collections.shuffle(order,random);
   for(int net:order){
    for(int c=routes[net].nextSetBit(0);c>=0;c=routes[net].nextSetBit(c+1))usage[c]--;
    var tree=new BitSet(512);int first=ends.get(net).get(0);tree.set(first);tree.set(first+256);
    var points=new ArrayList<>(ends.get(net));Collections.shuffle(points,random);
    for(int target:points){if(tree.get(target+256))continue;
     double[] distance=new double[512];Arrays.fill(distance,Double.POSITIVE_INFINITY);int[] previous=new int[512];Arrays.fill(previous,-1);
     record Visit(int cell,double cost){}var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost));distance[target+256]=0;queue.add(new Visit(target+256,0));int reached=-1;
     while(!queue.isEmpty()) {var visit=queue.remove();int at=visit.cell();if(visit.cost()!=distance[at])continue;if(tree.get(at)){reached=at;break;}int cell=at%256;int[] neighbors={cell%16>0?at-1:-1,cell%16<15?at+1:-1,cell>=16?at-16:-1,cell<240?at+16:-1,at^256};
      for(int next:neighbors){if(next<0||blocked[next]||fixed[next]>=0&&fixed[next]!=net)continue;double cost=visit.cost()+1+(next==(at^256)?1:0)+history[next]+usage[next]*(10+iteration*2);if(cost<distance[next]){distance[next]=cost;previous[next]=at;queue.add(new Visit(next,cost));}}
     }
     if(reached<0)throw new IllegalStateException("Недостижимый вывод сети "+net);
     for(int at=reached;at>=0;at=previous[at]){tree.set(at);if(fixed[at^256]==net)tree.set(at^256);}
    }
    routes[net]=tree;for(int c=tree.nextSetBit(0);c>=0;c=tree.nextSetBit(c+1))usage[c]++;
   }
   boolean solved=true;for(int c=0;c<512;c++)if(usage[c]>1){history[c]+=usage[c]-1;solved=false;}
   if(solved){System.arraycopy(fixed,0,nets,0,512);for(int net:order)for(int c=routes[net].nextSetBit(0);c>=0;c=routes[net].nextSetBit(c+1))nets[c]=net;System.out.println("РАЗЪЁМ разводка, итераций "+(iteration+1));return;}
  }
  throw new IllegalStateException("Не удалось развести демонстрационную плату");
 }
 static void finish(CompoundTag t,int[] nets){for(int i=0;i<512;i++)if(nets[i]>=0){int c=i%256;PcbTopology.set(t,i/256,c,nets[c]>=0&&nets[c]==nets[c+256]?PcbTopology.VIA:PcbTopology.TRACE);}for(int l=0;l<2;l++)for(int c=0;c<256;c++)for(int d:new int[]{1,3}){int n=d==1?(c%16==15?-1:c+1):(c>=240?-1:c+16);if(n>=0&&nets[l*256+c]>=0&&nets[l*256+n]>=0&&nets[l*256+c]!=nets[l*256+n])PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",t,l,c,d,true);}}
 static void place(CommandSourceStack s,BlockPos at,CompoundTag t){DevTest.place(s.getLevel(),at,Blocks.AIR.defaultBlockState());var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);DevTest.place(s.getLevel(),at,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));PcbModulesTest.call(PcbCircuitBlockEntity.class,s.getLevel().getBlockEntity(at),"setBoard",stack);PcbModulesTest.call(PcbCircuitDevice.class,device(s,at),"setCircuit",parts(t),PcbCircuitDevice.Rating.L2,((int[])PcbModulesTest.call(PcbTopology.class,null,"contacts",t)).length);}
 static int build(CommandSourceStack s){for(boolean receive:new boolean[]{false,true}){
  var at=receive?RX:TX;var l=s.getLevel();place(s,at,design(receive));place(s,at.north(3),supplyDesign());
  DevTest.place(l,at.north(6),Extras.eeTermination());DevTest.place(l,at.north(9),CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,true));var end=Extras.end(l,at.north(6));DevTest.wire(l,at.north(9),0,at.north(6),end.conductor(1).id());DevTest.wire(l,at.north(9),1,at.north(6),end.conductor(2).id());check(CableLaying.connect(l,CableKind.DUPLEX,end,CableEnd.of(l,at.north(3),l.getBlockState(at.north(3)))),"ввод питания");power(s,receive,300);
  DevTest.wire(l,at.north(3),0,at,0);DevTest.wire(l,at.north(3),1,at,1);
 }for(int i=0;i<9;i++)link(s,i,true);return mask(s,0);}

 static void power(CommandSourceStack s,boolean rx,double voltage){var previous=PcbBridgeTest.BATTERY;PcbBridgeTest.BATTERY=(rx?RX:TX).north(9);PcbBridgeTest.power(s,voltage,true);PcbBridgeTest.BATTERY=previous;}

 static void link(CommandSourceStack s,int pin,boolean connect){var network=InfrastructureSavedData.load(s.getLevel());var a=new InWorldNode(PcbConnector.node(SOCKET,pin),TX);var b=new InWorldNode(PcbConnector.node(SOCKET,pin),RX);if(connect){PcbItems.CIRCUIT_BLOCK.get().ensureNodesExist(s.getLevel(),TX,s.getLevel().getBlockState(TX));PcbItems.CIRCUIT_BLOCK.get().ensureNodesExist(s.getLevel(),RX,s.getLevel().getBlockState(RX));if(!network.isConnected(a,b))network.connect(a,b,CableWires.PCB_LEAD.get());}else network.removeConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(a,b));}
 static int mask(CommandSourceStack s,int mask){var list=saved(s,TX).getList("Circuit",Tag.TAG_COMPOUND);long switches=saved(s,TX).getLong("Switches");int ordinal=0;for(int i=0;i<list.size();i++){var p=list.getCompound(i);for(int bit=0;bit<8;bit++)if(p.getInt("ModuleIndex")==CONTROL[bit]&&p.getString("Kind").equals("SWITCH")){if(((switches&(1L<<i))!=0)!=((mask&(1<<bit))!=0))PcbModulesTest.call(PcbCircuitDevice.class,device(s,TX),"interactComponent",ordinal,s.getLevel().getGameTime(),false);}if(!p.getString("Kind").equals("TRACE"))ordinal++;}return 1;}
 static double voltage(CommandSourceStack s,BlockPos at,int a,int b){return Math.sqrt(PcbBridgeTest.RESULTS.get(s.getLevel()).getVoltageAtSqr(at,a,b));}
 static int report(CommandSourceStack s,int expected){var r=connector(s,RX);int mask=0;var snap=saved(s,RX); // Ниже проверяем реальные узлы, флаги — только дополнительно.
  var values=new ArrayList<Double>();for(int i=0;i<8;i++){double v=voltage(s,RX,r.pins()[i],r.pins()[8]);values.add(v);if(v>2.5)mask|=1<<i;}
  check(lit(s)==expected,"индикаторы соответствуют слову "+lit(s)+" / "+expected);
  check(mask==expected,"ожидание "+expected+", получено "+mask+" "+values);check(!snap.getBoolean("Burnt")&&!saved(s,TX).getBoolean("Burnt"),"платы целы");
  for(var at:new BlockPos[]{TX.north(3),RX.north(3)}){var reg=parts(topology(s,at)).stream().filter(p->p.kind()==PcbCircuitDevice.Kind.VOLTAGE_REGULATOR).findFirst().orElseThrow();double v=voltage(s,at,reg.coilA(),reg.coilB());check(v>4.97&&v<5.03,"стабилизированные5В "+at+" "+v);}
  s.sendSuccess(()->Component.literal("РАЗЪЁМ OK mask="+expected+" линии="+values+" RX свет="+lit(s)),false);return 1;
 }
 static int lit(CommandSourceStack s){var board=(PcbCircuitBlockEntity)s.getLevel().getBlockEntity(RX);var state=board.getUpdateTag(s.getLevel().registryAccess()).getLong("LitIndicators");var list=saved(s,RX).getList("Circuit",Tag.TAG_COMPOUND);int ordinal=0,mask=0;for(int i=0;i<list.size();i++){var p=list.getCompound(i);for(int bit=0;bit<8;bit++)if(p.getString("Kind").equals("LED")&&p.getInt("ModuleIndex")==LED[bit]&&(state&(1L<<ordinal))!=0)mask|=1<<bit;if(!p.getString("Kind").equals("TRACE"))ordinal++;}return mask;}
 static int audit(CommandSourceStack s){var net=InfrastructureSavedData.load(s.getLevel());for(var at:new BlockPos[]{TX,RX}){var part=connector(s,at);check(Arrays.stream(part.pins()).distinct().count()==9,"девять отдельных дорожек");var block=(PcbCircuitBlock)s.getLevel().getBlockState(at).getBlock();for(int i=0;i<9;i++){int node=PcbConnector.node(SOCKET,i);check(block.isNodeAccessible(s.getLevel(),at,s.getLevel().getBlockState(at),node),"доступность контакта");check(block.getNodePosition(s.getLevel(),at,s.getLevel().getBlockState(at),node)!=null,"координата контакта");}}
 for(int i=0;i<9;i++)for(int j=0;j<9;j++)check(net.isConnected(new InWorldNode(PcbConnector.node(SOCKET,i),TX),new InWorldNode(PcbConnector.node(SOCKET,j),RX))==(i==j),"соответствие проводов "+i+"→"+j);
 s.sendSuccess(()->Component.literal("РАЗЪЁМ граф81/81 OK"),false);return 1;}
 static int measure(CommandSourceStack s){var results=PcbBridgeTest.RESULTS.get(s.getLevel());for(var at:new BlockPos[]{TX,RX}){var p=connector(s,at);int[] pins=p.pins();var currents=new ArrayList<Double>();var levels=new ArrayList<Double>();double sum=0;var g=results.getVoltages(new InWorldNode(pins[8],at));for(int i=0;i<9;i++){var a=results.getVoltages(new InWorldNode(PcbConnector.node(SOCKET,i),at));var b=results.getVoltages(new InWorldNode(pins[i],at));double amp=0,v=0;check(a.length>0,"отсчёты контакта");for(int k=0;k<a.length;k++){amp+=(a[k]-b[k])/.01/a.length;v+=(b[k]-g[k])/a.length;}currents.add(amp);levels.add(v);sum+=amp;}check(Math.abs(sum)<1e-5,"Кирхгоф разъёма "+sum);var supply=at.north(3);double rail=voltage(s,at,0,1);check(Double.isFinite(rail),"конечное питание");String text="РАЗЪЁМ ИЗМЕРЕНИЕ "+at+" V="+levels+" I="+currents+" питание="+rail+" стабилизатор="+saved(s,supply).getCompound("RegulatorPower")+" свет="+((PcbCircuitBlockEntity)s.getLevel().getBlockEntity(at)).getUpdateTag(s.getLevel().registryAccess()).getLong("LitIndicators");System.out.println(text);s.sendSuccess(()->Component.literal(text),false);}return 1;}
 static int load(CommandSourceStack s,double ohms){var t=PcbTopology.create(2);PcbTopology.set(t,0,80,PcbTopology.TERMINAL);PcbTopology.set(t,0,95,PcbTopology.TERMINAL);int def=PcbModulesTest.definition(0,0);PcbTopology.set(t,0,87,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,87,def);for(int c=81;c<95;c++)if(c!=87)PcbTopology.set(t,0,c,PcbTopology.TRACE);var param=new CompoundTag();param.putDouble("Nominal",ohms);try{PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbParameters"),null,"put",t,87,param);}catch(Exception e){throw new IllegalStateException(e);}place(s,RX.east(3),t);DevTest.wire(s.getLevel(),RX,PcbConnector.node(SOCKET,0),RX.east(3),0);DevTest.wire(s.getLevel(),RX,PcbConnector.node(SOCKET,8),RX.east(3),1);return 1;}
 static int bank(CommandSourceStack s,double ohms,int selected){var t=PcbTopology.create(2);for(int i=0;i<8;i++){int row=2*i,anchor=row*16+2;PcbTopology.set(t,0,row*16,PcbTopology.TERMINAL);PcbTopology.set(t,0,row*16+1,PcbTopology.TRACE);PcbTopology.set(t,0,anchor,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,anchor,PcbModulesTest.definition(0,0));PcbTopology.set(t,0,row*16+3,PcbTopology.TRACE);var param=new CompoundTag();param.putDouble("Nominal",selected<0||selected==i?ohms:1e9);try{PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbParameters"),null,"put",t,anchor,param);}catch(Exception e){throw new IllegalStateException(e);}}
 for(int y=0;y<16;y++)PcbTopology.set(t,0,y*16+4,PcbTopology.TRACE);for(int x=5;x<15;x++)PcbTopology.set(t,0,240+x,PcbTopology.TRACE);PcbTopology.set(t,0,255,PcbTopology.TERMINAL);check(PcbTopology.validate(t)==null,"плата нагрузок");var at=RX.east(3);place(s,at,t);for(int i=0;i<9;i++)DevTest.wire(s.getLevel(),RX,PcbConnector.node(SOCKET,i),at,i);return 1;}
 static String branches(Object builderNode,com.george_vi.electroenergetics.simulation.CircuitBuilder builder){if(builderNode==null)return "нет узла";var adj=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)PcbOptoTest.field(builderNode,"adjacency");var values=new ArrayList<String>();for(int id:adj.keySet()){var node=builder.getNode(id);var ep=(com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties)adj.get(id);values.add(PcbOptoTest.field(node,"node")+" R="+ep.resistance());}return values.toString();}
 static int graph(CommandSourceStack s){var net=InfrastructureSavedData.load(s.getLevel());var builder=PcbBridgeTest.RESULTS.get(s.getLevel()).circuitBuilder;for(int i=0;i<9;i++){var a=new InWorldNode(PcbConnector.node(SOCKET,i),TX);var b=new InWorldNode(PcbConnector.node(SOCKET,i),RX);var text="РАЗЪЁМ ГРАФ "+i+" wire="+net.isConnected(a,b)+" TX="+branches(builder.getNode(a),builder)+" RX="+branches(builder.getNode(b),builder);System.out.println(text);s.sendSuccess(()->Component.literal(text),false);}return 1;}
 static int temperatures(CommandSourceStack s){var net=InfrastructureSavedData.load(s.getLevel());var values=new ArrayList<Double>();for(int i=0;i<9;i++){var c=new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new InWorldNode(PcbConnector.node(SOCKET,i),TX),new InWorldNode(PcbConnector.node(SOCKET,i),RX));var data=net.getConnectionData(c);check(data!=null,"провод "+i+" существует");var results=PcbBridgeTest.RESULTS.get(s.getLevel());var a=results.getVoltages(c.node1());var b=results.getVoltages(c.node2());double square=0;check(a.length>0&&a.length==b.length,"отсчёты провода");for(int tick=0;tick<a.length;tick++)square+=(a[tick]-b[tick])*(a[tick]-b[tick])/a.length;double measured=net.wireSimulationState.getConnection(c).getVoltageOnWire(results,c.node1(),c.node2());check(Math.abs(Math.abs(measured)-Math.sqrt(square))<1e-9,"расчёт нагрева использует действительное напряжение провода "+i);double temperature=((Number)PcbOptoTest.field(data,"temperature")).doubleValue();values.add(temperature);check(Double.isFinite(temperature)&&temperature<=.001,"нет ложного нагрева "+i+" "+temperature);}s.sendSuccess(()->Component.literal("РАЗЪЁМ температуры9проводов "+values),false);return 1;}
 static int cacheCheck(CommandSourceStack s){var results=PcbBridgeTest.RESULTS.get(s.getLevel());for(var at:new BlockPos[]{TX,RX})for(int i=0;i<9;i++){var node=new InWorldNode(PcbConnector.node(SOCKET,i),at);check(results.circuitBuilder.allNodes().stream().filter(n->n.node.equals(node)).count()==1,"внешний узел не дублируется "+node);}var actualNode=new InWorldNode(PcbConnector.node(SOCKET,1),RX);var actual=results.circuitBuilder.getNode(actualNode);int wrong=(actual.ordinal+1)%results.circuitBuilder.allNodes().size();check(!results.circuitBuilder.allNodes().get(wrong).node.equals(actualNode),"заведомо чужой индекс");check(results.getNodeID(actualNode,wrong)==actual.ordinal,"невалидный кеш индекса исправлен");check(results.getNodeID(actualNode,actual.ordinal)==actual.ordinal,"валидный кеш сохранён");check(results.getNodeID(actualNode,-1)==actual.ordinal,"отсутствующий кеш");check(results.getNodeID(new InWorldNode(9999999,RX),wrong)==-1,"неизвестный узел не подменён чужим");s.sendSuccess(()->Component.literal("РАЗЪЁМ кеш узлов4/4 OK"),false);return 1;}
 static final BlockPos HEAT=new BlockPos(21240,300,-11470);
 static int wireHeat(CommandSourceStack s){
  var l=s.getLevel();var previous=PcbBridgeTest.BATTERY;
  for(int side=0;side<2;side++){var at=side==0?HEAT:HEAT.east(3);DevTest.place(l,at,Blocks.AIR.defaultBlockState());DevTest.place(l,at,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,false));PcbBridgeTest.BATTERY=at;PcbBridgeTest.power(s,side==0?300:0,false);((com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?>)l.getBlockState(at).getBlock()).ensureNodesExist(l,at,l.getBlockState(at));}
  PcbBridgeTest.BATTERY=previous;var net=InfrastructureSavedData.load(l);
  for(int i=0;i<2;i++)net.connect(new InWorldNode(i,HEAT),new InWorldNode(i,HEAT.east(3)),CableWires.PCB_LEAD.get());
  check(net.isConnected(new InWorldNode(0,HEAT),new InWorldNode(0,HEAT.east(3))),"провод настоящего КЗ создан");return 1;
 }

 static int geometry(CommandSourceStack s){for(int rot=0;rot<4;rot++)for(boolean mirror:new boolean[]{false,true}){var t=PcbTopology.create(2);int def=PcbModulesTest.definition(35,rot);if(mirror)def=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"mirror",def);int anchor=34;PcbTopology.set(t,0,anchor,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,anchor,def);for(int i=0;i<9;i++){int cell=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",anchor,def,i);check(cell>=0,"вывод после поворота");PcbTopology.set(t,0,cell,PcbTopology.TRACE);}var parts=parts(t);check(parts!=null&&parts.size()==1&&Arrays.stream(parts.get(0).pins()).distinct().count()==9,"независимость при повороте/отражении");}
 check(s.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_connector_8")).isPresent(),"рецепт загружен");return 1;}
 static int samples;static boolean sampling;static final double[] minRail={10,10},maxRail={0,0};
 @net.neoforged.bus.api.SubscribeEvent public static void sample(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event){if(!sampling||samples>=100||event.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return;int index=0;for(var at:new BlockPos[]{TX,RX}){var a=event.results.getVoltages(new InWorldNode(0,at));var b=event.results.getVoltages(new InWorldNode(1,at));if(a.length==0||a.length!=b.length)return;for(int i=0;i<a.length;i++){double v=a[i]-b[i];minRail[index]=Math.min(minRail[index],v);maxRail[index]=Math.max(maxRail[index],v);}index++;}samples++;}
 static int samplesCheck(CommandSourceStack s){sampling=false;check(samples==100,"100 независимых отсчётов питания "+samples);for(int i=0;i<2;i++)check(minRail[i]>=4.97&&maxRail[i]<=5.03,"пульсации5В "+minRail[i]+"…"+maxRail[i]);s.sendSuccess(()->Component.literal("РАЗЪЁМ 100 отсчётов5В: TX "+minRail[0]+"…"+maxRail[0]+", RX "+minRail[1]+"…"+maxRail[1]),false);return 1;}
 static void register(CommandDispatcher<CommandSourceStack>d){var r=Commands.literal("pcbconnector").requires(s->s.hasPermission(2));r.then(Commands.literal("demo").executes(c->build(c.getSource())));
 for(String cmd:new String[]{"mask","expect","disconnect","connect"})r.then(Commands.literal(cmd).then(Commands.argument("n",IntegerArgumentType.integer(0,cmd.equals("mask")||cmd.equals("expect")?255:8)).executes(c->{var s=c.getSource();int n=IntegerArgumentType.getInteger(c,"n");if(cmd.equals("mask"))return mask(s,n);if(cmd.equals("expect"))return report(s,n);link(s,n,cmd.equals("connect"));return 1;})));
 for(boolean rx:new boolean[]{false,true})for(boolean on:new boolean[]{false,true})r.then(Commands.literal((rx?"rx":"tx")+(on?"-on":"-off")).executes(c->{power(c.getSource(),rx,on?300:0);return 1;}));
 for(boolean rx:new boolean[]{false,true})for(boolean connected:new boolean[]{false,true})r.then(Commands.literal((rx?"rx":"tx")+(connected?"-restore":"-cut")).executes(c->{var at=rx?RX:TX;var net=InfrastructureSavedData.load(c.getSource().getLevel());var a=new InWorldNode(0,at.north(3));var b=new InWorldNode(0,at);if(connected)net.connect(a,b,CableWires.PCB_LEAD.get());else net.removeConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(a,b));return 1;}));
 r.then(Commands.literal("short-check").executes(c->{var s=c.getSource();check(saved(s,TX).getBoolean("Burnt")||saved(s,RX).getBoolean("Burnt"),"защита при КЗ сработала");s.sendSuccess(()->Component.literal("РАЗЪЁМ КЗ: TX burnt="+saved(s,TX).getBoolean("Burnt")+", RX burnt="+saved(s,RX).getBoolean("Burnt")),false);return 1;}));
 r.then(Commands.literal("expect-dark").executes(c->{check(lit(c.getSource())==0,"индикаторы обесточены");return measure(c.getSource());}));
 r.then(Commands.literal("temperatures").executes(c->temperatures(c.getSource())));
 r.then(Commands.literal("cache-check").executes(c->cacheCheck(c.getSource())));
 r.then(Commands.literal("wireheat").executes(c->wireHeat(c.getSource())));
 r.then(Commands.literal("wireheat-check").executes(c->{var l=c.getSource().getLevel();var net=InfrastructureSavedData.load(l);check(!net.isConnected(new InWorldNode(0,HEAT),new InWorldNode(0,HEAT.east(3)))||!net.isConnected(new InWorldNode(1,HEAT),new InWorldNode(1,HEAT.east(3))),"штатное разрушение EE от настоящего тока не отключено");DevTest.place(l,HEAT,Blocks.AIR.defaultBlockState());DevTest.place(l,HEAT.east(3),Blocks.AIR.defaultBlockState());c.getSource().sendSuccess(()->Component.literal("РАЗЪЁМ настоящее тепловое разрушение EE OK"),false);return 1;}));
 r.then(Commands.literal("graph").executes(c->graph(c.getSource())));
 r.then(Commands.literal("audit").executes(c->audit(c.getSource())));
 r.then(Commands.literal("measure").executes(c->measure(c.getSource())));
 r.then(Commands.literal("geometry").executes(c->geometry(c.getSource())));
 r.then(Commands.literal("load").then(Commands.argument("ohms",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(.01,1e9)).executes(c->load(c.getSource(),com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"ohms")))));
 r.then(Commands.literal("load-bank").executes(c->bank(c.getSource(),4700,-1)));
 r.then(Commands.literal("bank-one").then(Commands.argument("n",IntegerArgumentType.integer(0,7)).executes(c->bank(c.getSource(),1000,IntegerArgumentType.getInteger(c,"n")))));
 r.then(Commands.literal("unload").executes(c->{DevTest.place(c.getSource().getLevel(),RX.east(3),Blocks.AIR.defaultBlockState());return 1;}));
 r.then(Commands.literal("sample-start").executes(c->{samples=0;sampling=true;Arrays.fill(minRail,10);Arrays.fill(maxRail,0);return 1;}));
 r.then(Commands.literal("sample-check").executes(c->samplesCheck(c.getSource())));
 r.then(Commands.literal("reload").executes(c->{for(var at:new BlockPos[]{TX,RX})device(c.getSource(),at).read(saved(c.getSource(),at));return 1;}));
 r.then(Commands.literal("export").executes(c->{PcbBridgeTest.BOARD=TX;return PcbBridgeTest.export(c.getSource());}));
 d.register(r);}
}
