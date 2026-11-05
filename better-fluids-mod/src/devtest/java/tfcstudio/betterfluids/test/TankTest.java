package tfcstudio.betterfluids.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.UUID;

@Mod("better_fluids_devtest")
public final class TankTest {
    private static final BlockPos BASE = new BlockPos(0, 70, 0);

    public TankTest() {
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("betterfluidstest")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("build").executes(ctx -> {
                build(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: слой 2×2, 112 вёдер, медный бак отдельно"), false);
                return 1;
            }))
            .then(Commands.literal("reload").executes(ctx -> {
                var level = ctx.getSource().getLevel();
                var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, BASE.above().east().south(), Direction.UP);
                check(handler != null && handler.getTankCapacity(0) == 112_000, "Ёмкость после перезапуска");
                check(handler.getFluidInTank(0).getAmount() == 112_000, "Вода после перезапуска");
                ctx.getSource().sendSuccess(() -> Component.literal("OK: 112 вёдер воды после перезапуска"), false);
                return 1;
            })));
    }

    private static void build(ServerLevel level) {
        var item = (BlockItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("better_fluids:iron_tank"));
        for (BlockPos pos : BlockPos.betweenClosed(BASE.west(), BASE.offset(2, 2, 2))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(BASE, BASE.east().south())) {
            level.setBlockAndUpdate(pos, item.getBlock().defaultBlockState());
        }
        level.setBlockAndUpdate(BASE.west(), AllBlocks.FLUID_TANK.getDefaultState());
        var player = FakePlayerFactory.get(level, new GameProfile(
            UUID.fromString("3f5a2c82-1446-42fc-85e7-0ce518a3dc9b"), "TankTest"));
        player.setPos(4, 70, 4);
        var stack = new ItemStack(item, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(BASE).add(0, 0.5, 0), Direction.UP, BASE, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        check(item.place(context).consumesAction(), "Установка слоя");
        check(stack.isEmpty(), "Израсходовано четыре секции");

        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, BASE.above().east().south(), Direction.UP);
        check(handler != null && handler.getTankCapacity(0) == 112_000, "Ёмкость слоя 2×2×2");
        check(handler.fill(new FluidStack(Fluids.WATER, 113_000), FluidAction.EXECUTE) == 112_000, "Заполнение через верхнюю секцию");
        var copper = level.getCapability(Capabilities.FluidHandler.BLOCK, BASE.west(), Direction.UP);
        check(copper != null && copper.getTankCapacity(0) == FluidTankBlockEntity.getCapacityMultiplier()
            && copper.getFluidInTank(0).isEmpty(), "Медный бак остался отдельным");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
