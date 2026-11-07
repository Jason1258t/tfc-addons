package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.*;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Пять независимых источников → пять секций одной полосы; сборка и контакты штатными путями. */
final class PcbLightsTest {
    private static BlockPos BOARD = new BlockPos(8,-60,24), TABLE = BOARD.west(2);
    private static BlockPos source(int section) { return BOARD.offset(4+section*2,-2,6); }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcblights").requires(s -> s.hasPermission(2))
                .then(Commands.literal("build").executes(c -> build(c.getSource(),false)))
                .then(Commands.literal("reverse").executes(c -> build(c.getSource(),true)))
                .then(Commands.literal("power").then(Commands.argument("mask",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,31))
                        .executes(c -> { power(c.getSource().getLevel(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"mask")); return 1; })))
                .then(Commands.literal("expect").then(Commands.argument("mask",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,31))
                        .executes(c -> expect(c.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"mask")))))
                .then(Commands.literal("reload").executes(c -> {
                    var level=c.getSource().getLevel(); var device=DevicesSavedData.load(level).getDevice(BOARD,PcbCircuitDevice.class);
                    var tag=new net.minecraft.nbt.CompoundTag(); device.write(tag); device.read(tag);
                    var serialized=tag.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND);
                    check(serialized.size()==6 && serialized.getCompound(0).getInt("ModuleType")==19
                            && serialized.getCompound(4).getInt("ModuleIndex")==103,"метаданные полосы сохраняются"); return 1;
                })));
    }

    private static void check(boolean ok,String message) { if (!ok) throw new IllegalStateException("Световая полоса: "+message); }

    static int buildAt(CommandSourceStack source,BlockPos board) { BOARD=board; TABLE=BOARD.west(2); return build(source,false); }

    @SuppressWarnings("unchecked")
    private static int build(CommandSourceStack source,boolean reverse) {
        var level=source.getLevel(); var player=FakePlayerFactory.getMinecraft(level);
        DevTest.place(level,TABLE,PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var editor=(PcbEditorBlockEntity)level.getBlockEntity(TABLE); rotatedLayouts(editor); editor.clear(2);
        var menu=new PcbEditorMenu(82,player.getInventory(),ContainerLevelAccess.create(level,TABLE),editor);
        menu.clickMenuButton(player,1040+19);
        for(int i=0;i<5;i++) menu.clickMenuButton(player,PcbEditorMenu.OHMS);
        menu.clickMenuButton(player,103);
        check(editor.topology().getIntArray("ElectricalParts")[103]!=0,"установка 2×5 через меню");
        menu.clickMenuButton(player,1040+2); menu.clickMenuButton(player,215);
        for(int row=6;row<=10;row++) {
            for(int x=0;x<16;x++) {
                if(x==7 || x==8) continue;
                editor.setCell(0,row*16+x,x==0 || x==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
            }
        }
        // Соседние параллельные дорожки разводятся инструментом «Разрыв»; площадки уже изолированы модулем.
        menu.clickMenuButton(player,PcbEditorMenu.SELECT_TOOL+8);
        for(int row=6;row<10;row++) for(int x=0;x<16;x++) {
            if(x>=6 && x<=9) continue;
            check(menu.clickMenuButton(player,3000+(row*16+x)*4+3),"разрыв между соседними дорожками");
        }
        for(int x=0;x<16;x++) {
            if(x==7 || x==8) continue;
            editor.setCell(0,13*16+x,x==0 || x==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
        }
        var topology=PcbTopology.normalize(editor.topology(),2);
        check(topology.getByteArray("TraceCuts").length==PcbTopology.CELLS*PcbTopology.MAX_LAYERS,"разрывы сохраняются");
        List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(internalClass("PcbWiring"),null,"compile",topology);
        check(parts!=null && parts.size()==6,"пять секций и кнопка компилируются");
        for(int i=0;i<5;i++) check(parts.get(i).kind()==PcbCircuitDevice.Kind.LED_STRIP && parts.get(i).a()==2*i && parts.get(i).b()==2*i+1,"независимая секция "+i);
        try {
            var requirements=internalClass("PcbAssembly").getDeclaredMethod("requirements",List.class); requirements.setAccessible(true);
            var items=(Map<Item,Integer>)requirements.invoke(null,parts);
            check(items.size()==2 && items.get(PcbItems.LED_STRIP.get())==1 && items.get(Items.STONE_BUTTON)==1,"одна полоса, а не пять предметов");
            var consume=internalClass("PcbAssembly").getDeclaredMethod("consume",Player.class,List.class); consume.setAccessible(true);
            boolean creative=player.getAbilities().instabuild; player.getAbilities().instabuild=false;
            player.getInventory().clearContent(); player.getInventory().setItem(0,PcbItems.LED_STRIP.toStack()); player.getInventory().setItem(1,new ItemStack(Items.STONE_BUTTON));
            check((boolean)consume.invoke(null,player,parts) && player.getInventory().isEmpty(),"сборка расходует ровно модуль и кнопку");
            check(!(boolean)consume.invoke(null,player,parts),"повторная бесплатная сборка запрещена");
            player.getAbilities().instabuild=creative;
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        DevTest.place(level,BOARD,Blocks.AIR.defaultBlockState()); DevTest.place(level,BOARD.below(),Blocks.STONE.defaultBlockState());
        var stack=PcbItems.L2_BOARD.toStack(); topology.putBoolean("Assembled",true); stack.set(PcbItems.PCB_TOPOLOGY.get(),topology);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(BOARD.below()).add(0,.5,0),Direction.UP,BOARD.below(),false);
        check(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"готовая плата устанавливается предметом");
        var board=(PcbCircuitBlockEntity)level.getBlockEntity(BOARD);
        check((int)PcbModulesTest.call(PcbCircuitBlockEntity.class,board,"componentOrdinal",215)==5,"кнопка после пяти секций имеет правильный номер");
        for(int i=0;i<5;i++) {
            DevTest.place(level,source(i),Blocks.AIR.defaultBlockState()); DevTest.place(level,source(i),CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            boolean flip=reverse && i==2;
            check(DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),source(i),0,BOARD,2*i+(flip?1:0)),"тонкий провод +");
            check(DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),source(i),1,BOARD,2*i+(flip?0:1)),"тонкий провод −");
        }
        power(level,0);
        source.sendSuccess(() -> Component.literal("Полоса: пять независимых секций, десять контактов, разрывы, расход одного модуля и установка платы — успешно"),false);
        return 1;
    }

    private static Class<?> internalClass(String name) {
        try { return Class.forName("ee.extras.pcb."+name); } catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    private static void rotatedLayouts(PcbEditorBlockEntity editor) {
        for (int rotation=0;rotation<4;rotation++) {
            editor.clear(2);
            int definition=PcbModulesTest.definition(19,rotation);
            check(editor.setPart(0,85,definition),"поворот корпуса "+rotation);
            var tag=editor.topology();
            int[] pins=new int[10];
            for(int pin=0;pin<10;pin++) pins[pin]=PcbModulesTest.call(internalClass("PcbWiring"),null,"pinCell",85,definition,pin);
            for(int pin=0;pin<8;pin++) {
                boolean connected=PcbModulesTest.call(PcbTopology.class,null,"connected",tag,0,pins[pin],pins[pin+2]);
                check(!connected,"соседние площадки изолированы при повороте "+rotation);
            }
            int width=PcbModulesTest.call(internalClass("PcbFootprint"),null,"width",definition);
            int height=PcbModulesTest.call(internalClass("PcbFootprint"),null,"height",definition);
            int[] owner=new int[256]; java.util.Arrays.fill(owner,-1);
            for(int pin=0;pin<10;pin++) {
                int x=pins[pin]%16,y=pins[pin]/16;
                int dx=x<5?-1:x>=5+width?1:0,dy=y<5?-1:y>=5+height?1:0;
                while(x>=0 && y>=0 && x<16 && y<16) {
                    int cell=y*16+x;
                    check(owner[cell]<0,"дорожки не пересекаются"); owner[cell]=pin;
                    editor.setCell(0,cell,x==0 || y==0 || x==15 || y==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
                    x+=dx; y+=dy;
                }
            }
            for(int cell=0;cell<256;cell++) {
                if(owner[cell]<0) continue;
                for(int direction:new int[]{1,3}) {
                    int next=PcbModulesTest.call(PcbTopology.class,null,"neighbor",cell,direction);
                    if(next<0 || owner[next]<0 || owner[next]==owner[cell]) continue;
                    PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",tag,0,cell,direction,true);
                }
            }
            List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(internalClass("PcbWiring"),null,"compile",PcbTopology.normalize(tag,2));
            check(parts!=null && parts.size()==5,"пять ветвей при повороте "+rotation);
            var nodes=new java.util.HashSet<Integer>();
            for(var part:parts) check(nodes.add(part.a()) && nodes.add(part.b()),"десять независимых контактов при повороте "+rotation);
        }
        editor.clear(2);
    }

    static void power(ServerLevel level,int mask) {
        for(int i=0;i<5;i++) {
            int volts=(mask&(1<<i))!=0?24:0;
            var be=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(source(i));
            be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(volts*1000);
            var battery=DevicesSavedData.load(level).getDevice(source(i),CreativeBatteryDevice.class); battery.voltage=volts; battery.acFrequency=0;
        }
    }

    private static int expect(CommandSourceStack source,int mask) {
        var level=source.getLevel(); var board=(PcbCircuitBlockEntity)level.getBlockEntity(BOARD);
        long actual=PcbModulesTest.call(PcbCircuitBlockEntity.class,board,"indicatorSections",0,5);
        check(actual==mask,"маска "+actual+" вместо "+mask);
        check(!level.getBlockState(BOARD).getValue(PcbCircuitBlock.BURNT),"штатное питание не сжигает плату");
        source.sendSuccess(() -> Component.literal("Полоса: маска секций "+actual+" — верно"),false); return 1;
    }
}
