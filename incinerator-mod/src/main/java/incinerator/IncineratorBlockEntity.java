package incinerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Мусоросжигатель: ёмкость предметов, которая принимает любое количество чего угодно в любую ячейку и уничтожает его.
 * Слот 0 — «топка», всегда пустой; слоты 1–4 — накопленная гарь, только на выход. С каждого сожжённого предмета
 * с шансом {@link #RESIDUE_CHANCE} остаётся один случайный предмет тега incinerator:residue — если не подан редстоун
 * и есть место (4 стопки); иначе гарь просто пропадает.
 */
public class IncineratorBlockEntity extends BlockEntity {
    public static final float RESIDUE_CHANCE = 0.15f;
    static final int RESIDUE_SLOTS = 4;

    final ItemStackHandler residue = new ItemStackHandler(RESIDUE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private long lastEffect;

    final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1 + RESIDUE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? ItemStack.EMPTY : residue.getStackInSlot(slot - 1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate) burn(stack);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 ? ItemStack.EMPTY : residue.extractItem(slot - 1, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    };

    public IncineratorBlockEntity(BlockPos pos, BlockState state) {
        super(Incinerator.INCINERATOR_BE.get(), pos, state);
    }

    /** Сжечь стопку (вызывающий сам убирает её из мира); с каждого предмета — шанс гари */
    public void burn(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel server)) return;
        if (!getBlockState().getValue(IncineratorBlock.POWERED)) {
            var tag = BuiltInRegistries.ITEM.getTag(Incinerator.RESIDUE);
            if (tag.isPresent() && tag.get().size() > 0) {
                for (int i = 0; i < stack.getCount(); i++) {
                    if (server.random.nextFloat() >= RESIDUE_CHANCE) continue;
                    tag.get().getRandomElement(server.random)
                            .ifPresent(item -> ItemHandlerHelper.insertItemStacked(residue, new ItemStack(item), false));
                }
            }
        }
        // Дым и треск не чаще раза в полсекунды — поток из шлюза не должен заваливать клиентов частицами
        long now = server.getGameTime();
        if (now - lastEffect >= 10) {
            lastEffect = now;
            BlockPos p = getBlockPos();
            server.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.01);
            server.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.95, p.getZ() + 0.5, 2, 0.25, 0.02, 0.25, 0.01);
            server.playSound(null, p, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.9f + server.random.nextFloat() * 0.2f);
        }
    }

    /** Сколько гари накоплено (для проверочного мода) */
    public int residueCount() {
        int n = 0;
        for (int i = 0; i < RESIDUE_SLOTS; i++) n += residue.getStackInSlot(i).getCount();
        return n;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Residue", residue.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Residue")) residue.deserializeNBT(registries, tag.getCompound("Residue"));
    }
}
