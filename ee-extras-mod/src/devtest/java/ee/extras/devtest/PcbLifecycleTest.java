package ee.extras.devtest;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import ee.extras.pcb.PcbCircuitDevice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Сравнивает реальные сохранённые поля платы; эталоны переживают новый процесс сервера. */
final class PcbLifecycleTest {
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcblifecycle").requires(s -> s.hasPermission(2))
                .then(Commands.literal("tickets")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                            var source = c.getSource();
                            var pos = BlockPosArgument.getBlockPos(c, "pos");
                            var manager = source.getLevel().getChunkSource().chunkMap.getDistanceManager();
                            long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                            String ticket = PcbModulesTest.call(net.minecraft.server.level.DistanceManager.class,
                                    manager, "getTicketDebugString", chunk);
                            success(source, "Заявки чанка " + pos.toShortString() + ": " + ticket
                                    + ", Loaded=" + (source.getLevel().hasChunkAt(pos) ? 1 : 0)
                                    + ", BlockTicking=" + (manager.inBlockTickingRange(chunk) ? 1 : 0));
                            return 1;
                        })))
                .then(Commands.literal("chunk")
                        .then(Commands.argument("loaded", BoolArgumentType.bool())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                                    boolean expected = BoolArgumentType.getBool(c, "loaded");
                                    require(c.getSource().getLevel().hasChunkAt(pos) == expected,
                                            "состояние чанка " + pos + ", ожидалось loaded=" + expected);
                                    success(c.getSource(), "Чанк " + pos.toShortString() + ": Loaded=" + (expected ? 1 : 0));
                                    return 1;
                                }))))
                .then(Commands.literal("remember")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                                            var source = c.getSource();
                                            var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                                            String key = StringArgumentType.getString(c, "key");
                                            var actual = snapshot(source, pos);
                                            require(actual.contains(key), "нет поля " + key);
                                            var saved = new CompoundTag();
                                            saved.putIntArray("Position", new int[]{pos.getX(), pos.getY(), pos.getZ()});
                                            saved.putString("Dimension", source.getLevel().dimension().location().toString());
                                            saved.putString("Key", key);
                                            saved.put("Value", actual.get(key).copy());
                                            source.getServer().getCommandStorage().set(id(StringArgumentType.getString(c, "id")), saved);
                                            success(source, "Эталон " + StringArgumentType.getString(c, "id") + " сохранён: " + key + ", Saved=1");
                                            return 1;
                                        })))))
                .then(Commands.literal("same")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            var source = c.getSource();
                            String name = StringArgumentType.getString(c, "id");
                            var saved = source.getServer().getCommandStorage().get(id(name));
                            int[] coordinates = saved.getIntArray("Position");
                            require(coordinates.length == 3, "нет эталона " + name);
                            require(source.getLevel().dimension().location().toString().equals(saved.getString("Dimension")),
                                    "другое измерение для " + name);
                            String key = saved.getString("Key");
                            var actual = snapshot(source, new BlockPos(coordinates[0], coordinates[1], coordinates[2]));
                            require(actual.contains(key) && actual.get(key).equals(saved.get("Value")),
                                    name + ": " + key + " изменилось: " + actual.get(key) + ", эталон " + saved.get("Value"));
                            success(source, "Эталон " + name + " совпал: " + key + ", Equal=1");
                            return 1;
                        }))));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("pcb_lifecycle", name);
    }

    private static CompoundTag snapshot(CommandSourceStack source, BlockPos pos) {
        var level = source.getLevel();
        require(level.hasChunkAt(pos), "плата не загружена " + pos);
        var device = DevicesSavedData.load(level).getDevice(pos, PcbCircuitDevice.class);
        require(device != null, "нет платы " + pos);
        var result = new CompoundTag();
        device.write(result);
        require(!result.getBoolean("Burnt"), "плата сгорела " + pos);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("PCB_LIFECYCLE_FAIL: " + message);
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("PCB_LIFECYCLE_OK: " + message), false);
    }
}
