package ee.extras.wire;

import ee.extras.cable.CableEnd;
import ee.extras.cable.CableKind;
import ee.extras.lantern.LanternConversion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Декоративный провод в руке: клик по прибору — начало или конец, по поверхности — провод растёт до точки клика (путь
 * ищет BlockTrace), по проводу — ответвление или сращивание (пакет Attach с клиента), Shift + ПКМ — сброс. Выбранный
 * конец — компонент WireComponents.SELECTION.
 *
 * Перенесено из Create: Power Grid 0.6.2 (WireItem.connect, mergeWires, useOn, use; IElectric.onWire; Apache-2.0,
 * см. NOTICE). Изменения: события NeoForge вместо Architectury; прибор — блок, принимающий двухжильный кабель; прибор →
 * прибор — тоже по поверхности (висячего провода нет); длина куска ограничена WireConfig.maxLength.
 */
public class SurfaceWireItem extends Item {
    public SurfaceWireItem(Properties properties) {
        super(properties);
    }

    public SurfaceWires.Spec spec() {
        return SurfaceWires.DUPLEX;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || stack.has(WireComponents.SELECTION.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ee_extras.surface_wire.tooltip.conductors", spec().conductors()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ee_extras.surface_wire.tooltip.length", fmt(WireConfig.maxLength()),
                fmt(WireConfig.itemsPerMeter())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ee_extras.surface_wire.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    public static WireEnd selection(ItemStack stack) {
        var tag = stack.get(WireComponents.SELECTION.get());
        return tag == null ? null : WireEnd.load(tag.copyTag());
    }

    public static void select(ItemStack stack, WireEnd end) {
        if (end == null) stack.remove(WireComponents.SELECTION.get());
        else stack.set(WireComponents.SELECTION.get(), net.minecraft.world.item.component.CustomData.of(end.save()));
    }

    /** Прибор, к которому можно вести провод; фонарь и прожектор IE сначала становятся блоками сети EE */
    static WireEnd.Block device(Level level, BlockPos pos, Player player) {
        BlockState st = level.getBlockState(pos);
        if (LanternConversion.isIeLantern(st) || LanternConversion.isIeFloodlight(st)) {
            if (level.isClientSide) return new WireEnd.Block(pos);
            LanternConversion.convert(level, pos, player);
            st = level.getBlockState(pos);
        }
        CableEnd end = CableEnd.of(level, pos, st);
        return end != null && end.kind() == CableKind.DUPLEX ? new WireEnd.Block(pos) : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null || player.isShiftKeyDown() || ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        WireEnd.Block dev = device(level, pos, player);
        WireEnd sel = selection(stack);
        if (level.isClientSide) return dev != null || sel != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (dev != null) {
            if (sel == null) {
                select(stack, dev);
                message(player, "ee_extras.surface_wire.next", ChatFormatting.GRAY);
                return InteractionResult.SUCCESS;
            }
            InteractionResultHolder<SurfaceWireEntity> r = connect(level, stack, player, sel, dev);
            if (r.getResult().consumesAction()) select(stack, null);
            return r.getResult();
        }
        if (sel == null) {
            message(player, "ee_extras.surface_wire.start_hint", ChatFormatting.GRAY);
            return InteractionResult.FAIL;
        }
        Vec3 hit = ctx.getClickLocation().relative(ctx.getClickedFace(), 0.03125);
        InteractionResultHolder<SurfaceWireEntity> r = connect(level, stack, player, sel, new WireEnd.Free(hit));
        if (r.getResult().consumesAction() && r.getObject() != null) select(stack, new WireEnd.WireTip(r.getObject(), true));
        return r.getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(WireComponents.SELECTION.get())) {
            select(stack, null);
            if (!level.isClientSide) message(player, "ee_extras.surface_wire.reset", ChatFormatting.GRAY);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static void message(Player player, String key, ChatFormatting style, Object... args) {
        if (player != null) player.displayClientMessage(Component.translatable(key, args).withStyle(style), true);
    }

    /** Последний отказ (ключ) — для проверочного мода, у FakePlayer сообщений не видно */
    public static String lastFail;

    static InteractionResultHolder<SurfaceWireEntity> fail(Player player, String key) {
        lastFail = key;
        message(player, key, ChatFormatting.RED);
        return InteractionResultHolder.fail(null);
    }

    /** Путь от конца from к to: прибор — вход в его форму у точки кабеля; продолжение края — в ту же сторону */
    static BlockTrace.Result path(Level level, Player player, Vec3 from, WireEnd fromEnd, WireEnd to) {
        Vec3 target = to.position(level);
        Direction continueDir = null;
        if (fromEnd instanceof WireEnd.WireTip tip) {
            SurfaceWireEntity w = tip.wire(level);
            if (w == null || w.segments.isEmpty()) return null;
            continueDir = tip.end() ? w.segments.get(w.segments.size() - 1).direction : w.segments.get(0).direction.getOpposite();
        }
        if (WireEvents.alternate(player)) return BlockTrace.alternatePath(from, target);
        if (to instanceof WireEnd.Block b) return BlockTrace.findPathToDevice(level, from, b.pos(), target, continueDir);
        return BlockTrace.findPath(level, from, target, null, continueDir);
    }

    /** Начальная точка пути: край провода — как есть, прибор и остальное — к центру клетки сетки */
    static Vec3 startPoint(Level level, WireEnd e) {
        Vec3 p = e.position(level);
        return e.type() == WireEnd.Type.WIRE ? p : BlockTrace.alignPosition(p);
    }

    public static InteractionResultHolder<SurfaceWireEntity> connect(Level level, ItemStack stack, Player player, WireEnd e1, WireEnd e2) {
        if (e1.type() == WireEnd.Type.WIRE && e2.type() == WireEnd.Type.WIRE)
            return merge(level, stack, player, (WireEnd.WireTip) e1, (WireEnd.WireTip) e2);
        if (e1.type() == WireEnd.Type.BLOCK && e2.type() == WireEnd.Type.WIRE) {
            WireEnd t = e1;
            e1 = e2;
            e2 = t;
        }
        if (e1 instanceof WireEnd.Block a && e2 instanceof WireEnd.Block b && a.pos().equals(b.pos())) return fail(player, "ee_extras.surface_wire.same_block");
        if (!e1.valid(level)) return fail(player, "ee_extras.surface_wire.lost_selection");
        Vec3 start = startPoint(level, e1);
        Vec3 target = e2.position(level);
        double reach = Math.abs(target.x - start.x) + Math.abs(target.y - start.y) + Math.abs(target.z - start.z);
        double have = e1 instanceof WireEnd.WireTip t && t.wire(level) != null ? t.wire(level).totalLength() : 0;
        if (have + reach > WireConfig.maxLength() + 0.5) return fail(player, "ee_extras.surface_wire.too_long");
        BlockTrace.Result result = path(level, player, start, e1, e2);
        if (result == null || !result.reachedTarget())
            return fail(player, reach * Point.GRID > BlockTrace.MAX_DISTANCE ? "ee_extras.surface_wire.too_far" : "ee_extras.surface_wire.no_path");
        float added = result.length();
        boolean creative = player != null && player.isCreative();

        if (e1 instanceof WireEnd.WireTip tip) {
            SurfaceWireEntity wire = tip.wire(level);
            if (wire == null) return fail(player, "ee_extras.surface_wire.lost_selection");
            if (wire.item() != stack.getItem()) return fail(player, "ee_extras.surface_wire.wrong_type");
            if (wire.totalLength() + added > WireConfig.maxLength()) return fail(player, "ee_extras.surface_wire.too_long");
            int need = Math.max(0, WireConfig.items(wire.totalLength() + added) - wire.itemCount());
            if (!creative && count(player, stack) < need) return fail(player, "ee_extras.surface_wire.missing_items");
            if (level.isClientSide) return InteractionResultHolder.success(null);
            if (!tip.end()) wire.flip();
            wire.extend(result.points(), need);
            if (e2.type().connectable) wire.setEnd2(e2);
            else wire.rewire();
            wire.sync();
            if (!creative) take(player, stack, need);
            return InteractionResultHolder.success(wire);
        }

        if (added > WireConfig.maxLength()) return fail(player, "ee_extras.surface_wire.too_long");
        int need = Math.max(1, WireConfig.items(added));
        if (!creative && count(player, stack) < need) return fail(player, "ee_extras.surface_wire.missing_items");
        if (level.isClientSide) return InteractionResultHolder.success(null);
        SurfaceWireEntity wire = SurfaceWireEntity.create(level, start, stack.getItem(), need, result.points());
        if (player != null && wire.spec().colorable() && player.getOffhandItem().getItem() instanceof DyeItem dye) wire.setColor(dye.getDyeColor());
        if (!level.addFreshEntity(wire)) return fail(player, "ee_extras.surface_wire.failed");
        if (e1.type().connectable) wire.setEnd1(e1);
        if (e2.type().connectable) wire.setEnd2(e2);
        wire.sync();
        if (!creative) take(player, stack, need);
        return InteractionResultHolder.success(wire);
    }

    /** Срастить два провода: путь от края первого к краю второго, второй вливается в первый */
    static InteractionResultHolder<SurfaceWireEntity> merge(Level level, ItemStack stack, Player player, WireEnd.WireTip t1, WireEnd.WireTip t2) {
        SurfaceWireEntity a = t1.wire(level), b = t2.wire(level);
        if (a == null || b == null || a == b) return fail(player, "ee_extras.surface_wire.lost_selection");
        if (a.item() != b.item()) return fail(player, "ee_extras.surface_wire.two_types");
        if (a.item() != stack.getItem()) return fail(player, "ee_extras.surface_wire.wrong_type");
        BlockTrace.Result result = path(level, player, t1.position(level), t1, t2);
        if (result == null || !result.reachedTarget()) return fail(player, "ee_extras.surface_wire.no_path");
        float total = a.totalLength() + b.totalLength() + result.length();
        if (total > WireConfig.maxLength()) return fail(player, "ee_extras.surface_wire.too_long");
        int need = Math.max(0, WireConfig.items(total) - a.itemCount() - b.itemCount());
        boolean creative = player != null && player.isCreative();
        if (!creative && count(player, stack) < need) return fail(player, "ee_extras.surface_wire.missing_items");
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(null);
        // a — продолжаем с конца, b — вливается началом
        if (!t1.end()) a.flip();
        if (t2.end()) b.flip();
        a.dropCores();
        b.dropCores();
        a.extend(result.points(), need);
        a.extend(new ArrayList<>(b.segments), b.itemCount());
        WireEnd far = b.end2();
        b.setItem(b.item(), 0);
        if (far instanceof WireEnd.Junction j) JunctionData.get(sl).replace(j.id(), b.getUUID(), a.getUUID());
        b.forgetEnds();
        b.discard();
        a.setEnd2Raw(far);
        a.rewire();
        a.sync();
        if (!creative) take(player, stack, need);
        return InteractionResultHolder.success(a);
    }

    static int count(Player player, ItemStack like) {
        if (player == null) return Integer.MAX_VALUE;
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(like.getItem())) n += s.getCount();
        if (player.getOffhandItem().is(like.getItem())) n += player.getOffhandItem().getCount();
        return n;
    }

    /** Списать n предметов: сначала из руки, потом из инвентаря (как PlayerUtilities.removeItems Power Grid) */
    static void take(Player player, ItemStack hand, int n) {
        if (player == null || n <= 0) return;
        int t = Math.min(n, hand.getCount());
        hand.shrink(t);
        n -= t;
        for (ItemStack s : player.getInventory().items) {
            if (n <= 0) break;
            if (!s.is(hand.getItem())) continue;
            t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }
}
