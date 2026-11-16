package ee.extras.devtest;

import ee.extras.pcb.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Двухслойные демонстрации: вся схема хранится дорожками проекта, без невидимых перемычек. */
final class PcbDemoRouting {
    final CompoundTag topology=PcbTopology.create(2);
    final int[] nets=new int[512];
    final boolean[] blocked=new boolean[512];
    final List<List<Integer>> endpoints=new ArrayList<>();
    private final List<int[]> terminals=new ArrayList<>();
    PcbDemoRouting(int count){Arrays.fill(nets,-1);for(int i=0;i<count;i++)endpoints.add(new ArrayList<>());}
    void part(int anchor,int type,int[] pinNets,double nominal,double threshold){PcbRegulatorDemo.add(topology,nets,blocked,endpoints,anchor,type,pinNets,nominal,threshold);}
    static int terminalCell(int pin){return new int[]{1,5,9,13,64,79,128,143,192,207,241,245,249,253}[pin];}
    void terminal(int cell,int net){PcbTopology.set(topology,0,cell,PcbTopology.TERMINAL);nets[cell]=net;blocked[cell+256]=true;terminals.add(new int[]{cell,net});}
    private boolean route(int start,int end,int net){
        int[] previous=new int[512];Arrays.fill(previous,-1);var queue=new ArrayDeque<Integer>();queue.add(start);previous[start]=start;
        while(!queue.isEmpty()&&previous[end]<0){int at=queue.remove(),cell=at%256;int[] next={cell%16>0?at-1:-1,cell%16<15?at+1:-1,cell>=16?at-16:-1,cell<240?at+16:-1,at^256};
            for(int n:next)if(n>=0&&previous[n]<0&&!blocked[n]&&(nets[n]<0||nets[n]==net)){previous[n]=at;queue.add(n);}}
        if(previous[end]<0)return false;int at=end;while(at!=start){nets[at]=net;at=previous[at];}nets[start]=net;return true;
    }
    CompoundTag build(){
        int[] initial=nets.clone();boolean routed=false;int used=0;
        for(int attempt=0;attempt<256&&!routed;attempt++) {
            System.arraycopy(initial,0,nets,0,nets.length);
            var order=new ArrayList<Integer>();for(int net=0;net<endpoints.size();net++)order.add(net);
            if(attempt==0)order.sort((a,b)->Integer.compare(endpoints.get(b).size(),endpoints.get(a).size()));
            else Collections.shuffle(order,new Random(301L+attempt));
            routed=true;
            for(int net:order){var points=endpoints.get(net);if(points.isEmpty())continue;
                for(int i=1;i<points.size();i++)if(!route(points.get(0)+256,points.get(i)+256,net)){routed=false;break;}
                if(!routed)break;
                for(var terminal:terminals)if(terminal[1]==net&&!route(points.get(0)+256,terminal[0],net)){routed=false;break;}
                if(!routed)break;
            }
            used=attempt+1;
        }
        PcbExtraTest.check(routed,"не найден полный маршрут всех видимых сетей за256порядков");
        System.out.println("PCB_VISIBLE_ROUTING attempts="+used+" nets="+endpoints.size());
        for(int i=0;i<512;i++)if(nets[i]>=0&&PcbTopology.get(topology,i/256,i%256)!=PcbTopology.TERMINAL) {
            int cell=i%256;boolean via=nets[cell]>=0&&nets[cell]==nets[cell+256]&&!blocked[cell+256];
            PcbTopology.set(topology,i/256,cell,via?PcbTopology.VIA:PcbTopology.TRACE);
        }
        for(int layer=0;layer<2;layer++)for(int cell=0;cell<256;cell++)for(int direction:new int[]{1,3}) {
            int next=direction==1?(cell%16==15?-1:cell+1):(cell>=240?-1:cell+16);
            if(next>=0&&nets[layer*256+cell]>=0&&nets[layer*256+next]>=0&&nets[layer*256+cell]!=nets[layer*256+next])PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",topology,layer,cell,direction,true);
        }
        String error=PcbTopology.validate(topology);PcbExtraTest.check(error==null,"видимый демонстрационный проект: "+error);return topology;
    }
}
