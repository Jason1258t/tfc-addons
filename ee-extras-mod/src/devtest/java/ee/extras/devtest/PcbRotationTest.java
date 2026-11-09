package ee.extras.devtest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.commands.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Blocks;
/** Настоящая цепочка двигатель → вал → датчик с независимой проверкой шкалы. */
final class PcbRotationTest {
 static void register(CommandDispatcher<CommandSourceStack> d){var root=Commands.literal("pcbrotation").requires(s->s.hasPermission(2));
 for(boolean demo:new boolean[]{true,false})root.then(Commands.literal(demo?"demo":"isolated").executes(c->{PcbSensorTest.rotation=true;return PcbSensorTest.build(c.getSource(),false,demo);}));
 root.then(Commands.literal("speed").then(Commands.argument("rpm",IntegerArgumentType.integer(-256,256)).executes(c->{speed(c.getSource(),IntegerArgumentType.getInteger(c,"rpm"));return 1;})));
 root.then(Commands.literal("missing").executes(c->{DevTest.place(c.getSource().getLevel(),PcbBridgeTest.BOARD.below(),Blocks.STONE.defaultBlockState());return 1;}));
 root.then(Commands.literal("check").then(Commands.argument("rpm",IntegerArgumentType.integer(-256,256)).executes(c->{int rpm=IntegerArgumentType.getInteger(c,"rpm");var m=PcbSensorTest.measurement(c.getSource());PcbBridgeTest.check(Math.abs(m.getDouble("Reading")-rpm)<.01,"фактические обороты "+m);PcbBridgeTest.check(Math.abs(m.getDouble("Fraction")-(.5+.4*rpm/256.0))<1e-9,"независимая шкала вращения "+m);return PcbSensorTest.report(c.getSource(),true);} )));
 d.register(root); }
 static void speed(CommandSourceStack s,int rpm){var l=s.getLevel();var shaft=PcbBridgeTest.BOARD.below();var motor=shaft.below();
 if(!l.getBlockState(shaft).is(AllBlocks.SHAFT.get()))DevTest.place(l,shaft,AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
 if(!(l.getBlockEntity(motor) instanceof CreativeMotorBlockEntity))DevTest.place(l,motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
 ((CreativeMotorBlockEntity)l.getBlockEntity(motor)).generatedSpeed.setValue(rpm);
 }
}
