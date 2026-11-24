package ee.extras.devtest;

import ee.extras.redstone.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import tfc.stand.scene.Scene;

/** Галерея всех ориентаций нового коннектора и подключённых красных проводов. */
final class SignalScene {
    static void start() {
        var scene=new Scene("signal-wires");
        scene.server("коннекторы во всех шести направлениях",c->{
            var faces=Direction.values();
            for(int i=0;i<faces.length;i++) {
                var pos=c.at(i*2-5,2,0);
                c.set(pos.relative(faces[i].getOpposite()),Blocks.SMOOTH_STONE.defaultBlockState());
                c.set(pos,Signals.CONNECTOR.get().defaultBlockState().setValue(SignalConnector.FACING,faces[i]));
                if(i>0) SignalTest.connect(c.level(),SignalTest.node(c.at((i-1)*2-5,2,0)),SignalTest.node(pos));
                scene.check("узел "+faces[i],Signals.valid(c.level(),SignalTest.node(pos)));
            }
        });
        scene.ticks(60).gui(false).camera(0,2,0,10,30,20).shot("all-facings")
                .camera(0,2,0,10,210,20).shot("back-facings").start();
    }
    private SignalScene() {}
}
