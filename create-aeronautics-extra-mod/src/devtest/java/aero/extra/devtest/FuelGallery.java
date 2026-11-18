package aero.extra.devtest;

import aero.extra.AeroExtra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import tfc.stand.scene.Scene;

final class FuelGallery {
    static void startIfRequested() {
        if (!Scene.requested("aero-fuel-gallery")) return;
        Scene scene = new Scene("aero-fuel-gallery");

        scene.server("двигатель, насос, порт и пассивное крепление", c -> {
            BlockState engine = facing(AeroExtra.TRANSPORT_ENGINE.get(), Direction.NORTH);
            BlockState pump = facing(AeroExtra.FUEL_PUMP.get(), Direction.EAST);
            Block portBlock = block("create_aeronautics_extra:fuel_port");
            BlockState port = facing(portBlock, Direction.WEST);
            BlockState relay = mount(portBlock);
            c.set(c.at(-3, 0, 0), engine);
            c.set(c.at(-1, 0, 0), pump);
            c.set(c.at(0, 0, 0), facing(AeroExtra.TRANSPORT_ENGINE.get(), Direction.EAST));
            c.set(c.at(2, 0, 0), block("create:fluid_tank").defaultBlockState());
            c.set(c.at(3, 0, 0), port);
            c.set(c.at(5, 0, 0), Blocks.SMOOTH_STONE.defaultBlockState());
            c.set(c.at(5, 1, 0), relay);
        });
        scene.until("все блоки видны клиенту", 600, () -> {
            var level = Minecraft.getInstance().level;
            var origin = scene.origin();
            Block port = block("create_aeronautics_extra:fuel_port");
            return level.getBlockState(origin.offset(-3, 0, 0)).is(AeroExtra.TRANSPORT_ENGINE.get())
                    && level.getBlockState(origin.offset(-1, 0, 0)).is(AeroExtra.FUEL_PUMP.get())
                    && level.getBlockState(origin).is(AeroExtra.TRANSPORT_ENGINE.get())
                    && level.getBlockState(origin.offset(2, 0, 0)).is(block("create:fluid_tank"))
                    && level.getBlockState(origin.offset(3, 0, 0)).equals(facing(port, Direction.WEST))
                    && level.getBlockState(origin.offset(5, 1, 0)).equals(mount(port));
        });
        scene.gui(false);
        scene.camera(1.5, 0.6, 0.3, 9.5, 12, 21);
        scene.shot("01-all-blocks");
        scene.camera(-0.35, 0.6, 0.4, 4, 25, 30);
        scene.shot("02-pump-and-engine");
        scene.server("шесть направлений транспортного двигателя", c -> {
            int index = 0;
            for (Direction direction : Direction.values()) {
                c.set(c.at(-5 + index * 2, 0, 5), facing(AeroExtra.TRANSPORT_ENGINE.get(), direction));
                index++;
            }
        });
        scene.ticks(30);
        scene.camera(0.5, 0.6, 5.5, 9.5, 15, 28);
        scene.shot("06-engine-orientations");
        scene.camera(3.9, 0.7, 0.35, 4.8, -20, 24);
        scene.shot("03-tank-port-and-mount");
        scene.server("трасса из двух патрубков", c -> {
            var origin = c.at(-5, 2, -4);
            FuelHoseCheck.build(c.level(), origin);
            scene.check("рукав установлен через крепление", FuelHoseCheck.canRun(c.level(), origin));
            c.player().setItemInHand(InteractionHand.MAIN_HAND, FuelHoseCheck.hose(8));
        });
        scene.ticks(40);
        scene.camera(0, 2.4, -4.0, 11, 12, 21);
        scene.shot("04-hose-route");
        scene.gui(true);
        scene.client("инвентарь с гибким патрубком", () -> {
            var mc = Minecraft.getInstance();
            mc.setScreen(new InventoryScreen(mc.player));
        });
        scene.ticks(10);
        scene.shot("05-hose-inventory");
        scene.start();
    }

    private static BlockState mount(Block port) {
        BooleanProperty relay = (BooleanProperty) port.getStateDefinition().getProperty("relay");
        return facing(port, Direction.DOWN).setValue(relay, true);
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(BlockStateProperties.FACING, direction);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }
}
