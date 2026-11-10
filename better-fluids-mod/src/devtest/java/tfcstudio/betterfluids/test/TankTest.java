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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
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
import net.neoforged.neoforge.fluids.FluidUtil;
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
            .then(Commands.literal("tankers").executes(ctx -> {
                String report = Tanker.buildAll(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: " + report), false);
                return 1;
            }))
            .then(Commands.literal("cladding").executes(ctx -> {
                cladding(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: обшивка ставится, слиток на камнерезе даёт оба листа"), false);
                return 1;
            }))
            .then(Commands.literal("tap").executes(ctx -> {
                tap(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: интерфейс наполняет ведро и канистру, снимается без бака"), false);
                return 1;
            }))
            .then(Commands.literal("build").executes(ctx -> {
                build(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: слой 2×2, 112 вёдер, медный бак отдельно"), false);
                return 1;
            }))
            .then(Commands.literal("freeform").executes(ctx -> {
                freeform(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: Г-слой, разделение ключом, новое ответвление"), false);
                return 1;
            }))
            .then(Commands.literal("split_reload").executes(ctx -> {
                var level = ctx.getSource().getLevel();
                var center = BASE.offset(8, 0, 0);
                checkTank(level, center.west(), 28_000, 14_000);
                checkTank(level, center, 28_000, 14_000);
                checkTank(level, center.east(), 14_000, 14_000);
                ctx.getSource().sendSuccess(() -> Component.literal("OK: связи и 42 ведра после перезапуска"), false);
                return 1;
            }))
            .then(Commands.literal("shapes").executes(ctx -> {
                shapes(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal("OK: фасонные секции, 31500 мБ, разделение ключом"), false);
                return 1;
            }))
            .then(Commands.literal("shapes_reload").executes(ctx -> {
                var level = ctx.getSource().getLevel();
                var pos = BASE.offset(2, 0, 8);
                checkTank(level, pos, 31_500, 31_500);
                checkTank(level, pos.east(3), 31_500, 31_500);
                ctx.getSource().sendSuccess(() -> Component.literal("OK: фасонный бак сохранил 31500 мБ"), false);
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

    private static void tap(ServerLevel level) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BetterFluidsTest"));
        var tapItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("better_fluids:fluid_tap"));
        var canItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:jerrycan"));
        var iron = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("better_fluids:iron_tank"));
        for (var block : new net.minecraft.world.level.block.Block[] {AllBlocks.FLUID_TANK.get(), iron}) {
            var pos = BASE.offset(4, 0, 4);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            for (var side : Direction.values()) {
                var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                check(tank != null, "Доступ к баку с " + side);
                tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                check(tank.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE) == 2000, "Подготовка бака");
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false);
                var hand = InteractionHand.MAIN_HAND;
                player.setItemInHand(hand, new ItemStack(tapItem));
                check(player.getMainHandItem().useOn(new UseOnContext(player, hand, hit)).consumesAction(), "Установка с " + side);
                var tapPos = pos.relative(side);
                var tapState = level.getBlockState(tapPos);
                var tapHit = new BlockHitResult(Vec3.atCenterOf(tapPos), side, tapPos, false);
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                tapState.useItemOn(player.getMainHandItem(), level, player, hand, tapHit);
                check(player.getMainHandItem().is(Items.WATER_BUCKET), "Наполнение ведра");
                player.setItemInHand(hand, new ItemStack(canItem));
                tapState.useItemOn(player.getMainHandItem(), level, player, hand, tapHit);
                check(FluidUtil.getFluidContained(player.getMainHandItem()).map(FluidStack::getAmount).orElse(0) == 1000,
                    "Наполнение канистры");
                check(tank.getFluidInTank(0).isEmpty(), "Из бака ушло ровно 2000 мБ");
                level.setBlockAndUpdate(tapPos, Blocks.AIR.defaultBlockState());
            }
            var tapPos = pos.north();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tapItem));
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            check(level.isEmptyBlock(tapPos), "Интерфейс должен сняться при разрушении бака");
        }
    }

    private static void shapes(ServerLevel level) {
        var pos = BASE.offset(2, 0, 8);
        String[] names = {"iron_tank", "iron_tank_plate", "iron_tank_quarter", "iron_tank_bevel"};
        for (int i = 0; i < names.length; i++) {
            level.setBlockAndUpdate(pos.east(i), Blocks.AIR.defaultBlockState());
        }
        for (int i = 0; i < names.length; i++) {
            var id = ResourceLocation.fromNamespaceAndPath("better_fluids", names[i]);
            level.setBlockAndUpdate(pos.east(i), BuiltInRegistries.BLOCK.get(id).defaultBlockState());
        }
        var slab = (BlockItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("better_fluids:iron_tank_plate"));
        var slabPlayer = FakePlayerFactory.getMinecraft(level);
        for (Direction face : Direction.values()) {
            var support = BASE.offset(10, 0, 10);
            var target = support.relative(face);
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            var stack = new ItemStack(slab);
            slabPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atCenterOf(support), face, support, false);
            var place = new BlockPlaceContext(slabPlayer, InteractionHand.MAIN_HAND, stack, hit);
            check(slab.place(place).consumesAction(), "Установка полублока: " + face);
            var placed = level.getBlockState(target);
            check(placed.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                == face.getOpposite(), "Ориентация полублока: " + face);
            checkTank(level, target, 7_000, 0);
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        }
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.east(3), Direction.UP);
        check(port != null && port.getTankCapacity(0) == 31_500, "Ёмкость смешанного бака");
        check(port.fill(new FluidStack(Fluids.WATER, 35_000), FluidAction.EXECUTE) == 31_500,
            "Заполнение смешанного бака");
        var bevel = pos.east(3);
        var player = FakePlayerFactory.getMinecraft(level);
        var hit = new BlockHitResult(Vec3.atCenterOf(bevel), Direction.EAST, bevel, false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        var state = level.getBlockState(bevel);
        var block = (FluidTankBlock) state.getBlock();
        block.onWrenched(state, context);
        checkTank(level, bevel, 7_000, 7_000);
        checkTank(level, pos, 24_500, 24_500);
        block.onWrenched(state, context);
        checkTank(level, pos, 31_500, 31_500);
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

    private static void freeform(ServerLevel level) {
        var item = (BlockItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("better_fluids:iron_tank"));
        var block = (FluidTankBlock) item.getBlock();
        var corner = BASE.offset(4, 0, 0);
        var center = BASE.offset(8, 0, 0);
        for (BlockPos pos : BlockPos.betweenClosed(corner, center.offset(1, 1, 1))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : new BlockPos[]{corner, corner.east(), corner.south(), center, center.west(), center.east()}) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        var player = FakePlayerFactory.get(level, new GameProfile(
            UUID.fromString("3f5a2c82-1446-42fc-85e7-0ce518a3dc9b"), "TankTest"));
        player.setPos(9, 70, 4);
        var stack = new ItemStack(item, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(corner).add(0, 0.5, 0), Direction.UP, corner, false);
        check(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction(), "Установка Г-слоя");
        check(stack.isEmpty() && level.getBlockState(corner.above().east().south()).isAir(), "Расход трёх блоков и пустой угол");
        checkTank(level, corner.above().south(), 84_000, 0);

        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, center, Direction.UP);
        check(port != null && port.fill(new FluidStack(Fluids.WATER, 42_000), FluidAction.EXECUTE) == 42_000, "Заполнение перемычки");
        var side = new BlockHitResult(Vec3.atCenterOf(center), Direction.SOUTH, center, false);
        var wrench = new UseOnContext(player, InteractionHand.MAIN_HAND, side);
        check(block.onWrenched(level.getBlockState(center), wrench).consumesAction(), "Отсоединение ключом");
        checkTank(level, center.west(), 14_000, 14_000);
        checkTank(level, center, 14_000, 14_000);
        checkTank(level, center.east(), 14_000, 14_000);
        check(block.onWrenched(level.getBlockState(center), wrench).consumesAction(), "Восстановление связей ключом");
        checkTank(level, center, 42_000, 42_000);
        block.onWrenched(level.getBlockState(center), wrench);
        level.setBlockAndUpdate(center.west().south(), block.defaultBlockState());
        var extension = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, extension);
        check(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, extension, side)).consumesAction(),
            "Присоединение новой секции к отсоединённой перемычке");
        checkTank(level, center.south(), 28_000, 14_000);
        checkTank(level, center.west(), 28_000, 14_000);
        checkTank(level, center.east(), 14_000, 14_000);
    }

    private static void checkTank(ServerLevel level, BlockPos pos, int capacity, int amount) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        check(handler != null, "Нет порта в " + pos);
        check(handler.getTankCapacity(0) == capacity,
            "Ёмкость в " + pos + ": ожидалось " + capacity + ", получили " + handler.getTankCapacity(0));
        check(handler.getFluidInTank(0).getAmount() == amount, "Количество жидкости в " + pos);
    }

    private static void cladding(ServerLevel level) {
        var recipes = level.getRecipeManager();
        String[][] cases = {{"minecraft:iron_ingot", "white"}, {"tfc:metal/ingot/wrought_iron", "white"},
            {"immersiveengineering:ingot_aluminum", "aluminum"}};
        for (String[] c : cases) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(c[0]));
            if (item == Items.AIR) continue; // TFC/IE нет на стенде — пропуск
            var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(item));
            var outputs = recipes.getRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING, input, level).stream()
                .map(r -> BuiltInRegistries.ITEM.getKey(r.value().getResultItem(level.registryAccess()).getItem()).toString()).toList();
            for (String kind : new String[]{"", "_framed"}) {
                String id = "better_fluids:" + c[1] + "_cladding" + kind;
                check(outputs.contains(id), c[0] + " на камнерезе не даёт " + id + ": " + outputs);
            }
        }
        BlockPos pos = new BlockPos(0, -60, 20);
        for (String name : new String[]{"white_cladding", "white_cladding_framed", "aluminum_cladding", "aluminum_cladding_framed"}) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("better_fluids", name));
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            check(level.getBlockState(pos).is(block) && level.getBlockState(pos).isSolidRender(level, pos), name + " не сплошной блок");
            pos = pos.east();
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
