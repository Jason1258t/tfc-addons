package miner.tools;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Настройки пояса из компонентов предмета и сравнение подобранного предмета с образцами. */
public final class BeltFilter {
    private BeltFilter() {}

    public enum Group {
        DIRT("minecraft:dirt"),
        PEBBLES("c:stones/loose"),
        COBBLESTONE("c:cobblestones"),
        GRAVEL("c:gravels"),
        SAND("minecraft:sand");

        private final TagKey<net.minecraft.world.item.Item> tag;

        Group(String tag) {
            this.tag = ItemTags.create(ResourceLocation.parse(tag));
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    public static final int ALLOW_LIST = 1 << Group.values().length;
    private static final int GROUP_MASK = ALLOW_LIST - 1;

    public static int settings(ItemStack belt) {
        return belt.getOrDefault(MinerTools.BELT_SETTINGS.get(), 0);
    }

    public static boolean configured(ItemStack belt) {
        return (settings(belt) & GROUP_MASK) != 0 || !samples(belt).isEmpty();
    }

    /** Старые образцы с обобщением становятся группами, остальные остаются точными запретами. */
    static void migrate(ItemStack belt) {
        if (belt.has(MinerTools.BELT_SETTINGS.get())) return;
        int groups = 0;
        List<ItemStack> exact = new java.util.ArrayList<>();
        for (ItemStack sample : samples(belt)) {
            int sampleGroups = anyVariant(belt) ? groups(sample) : 0;
            groups |= sampleGroups;
            if (sampleGroups == 0) exact.add(sample);
        }
        belt.set(MinerTools.FILTER.get(), ItemContainerContents.fromItems(exact));
        belt.set(MinerTools.BELT_SETTINGS.get(), groups);
    }

    private static int groups(ItemStack stack) {
        int mask = 0;
        for (Group group : Group.values()) {
            if (stack.is(group.tag)) mask |= group.bit();
        }
        return mask;
    }

    /**
     * Теги «одного рода»: замшелый камешек и обычный, гравий TFC и ванильный. Работают только при «все породы».
     */
    static final List<TagKey<net.minecraft.world.item.Item>> FAMILIES = List.of(
            ItemTags.create(ResourceLocation.parse("c:stones/loose")),
            ItemTags.create(ResourceLocation.parse("c:gravels")));

    /** Названия пород и почв — последние части id камешков (rock/loose/<порода>) и земли (dirt/<почва>) */
    private static Set<String> variants;

    public static boolean enabled(ItemStack belt) {
        return belt.getOrDefault(MinerTools.ENABLED.get(), true);
    }

    public static boolean anyVariant(ItemStack belt) {
        return belt.getOrDefault(MinerTools.ANY_VARIANT.get(), true);
    }

    public static List<ItemStack> samples(ItemStack belt) {
        List<ItemStack> out = new java.util.ArrayList<>();
        belt.getOrDefault(MinerTools.FILTER.get(), ItemContainerContents.EMPTY).nonEmptyItemsCopy().forEach(out::add);
        return out;
    }

    /** Не подбирать ли {@code drop} из-за этого пояса */
    public static boolean blocks(ItemStack belt, ItemStack drop) {
        if (!enabled(belt)) return false;
        if (belt.has(MinerTools.BELT_SETTINGS.get())) {
            int settings = settings(belt);
            for (ItemStack sample : belt.getOrDefault(MinerTools.FILTER.get(), ItemContainerContents.EMPTY).nonEmptyItems()) {
                if (sample.is(drop.getItem())) return (settings & ALLOW_LIST) == 0;
            }
            return (settings & groups(drop)) != 0;
        }
        boolean any = anyVariant(belt);
        for (ItemStack sample : belt.getOrDefault(MinerTools.FILTER.get(), ItemContainerContents.EMPTY).nonEmptyItems()) {
            if (matches(sample, drop, any)) return true;
        }
        return false;
    }

    public static boolean matches(ItemStack sample, ItemStack drop, boolean any) {
        if (sample.is(drop.getItem())) return true;
        if (!any) return false;
        if (sameKind(BuiltInRegistries.ITEM.getKey(sample.getItem()), BuiltInRegistries.ITEM.getKey(drop.getItem()))) {
            return true;
        }
        for (TagKey<net.minecraft.world.item.Item> tag : FAMILIES) {
            if (sample.is(tag) && drop.is(tag)) return true;
        }
        return false;
    }

    /**
     * tfc:rock/gravel/granite и tfc:rock/gravel/basalt — один вид, разные породы. Отличаться может только последняя
     * часть id, и обе должны быть названиями пород или почв: так руда (tfc:ore/poor_hematite) или металлы
     * (tfc:metal/ingot/copper) не склеиваются в «любую руду» и «любой слиток».
     */
    static boolean sameKind(ResourceLocation a, ResourceLocation b) {
        if (!a.getNamespace().equals(b.getNamespace())) return false;
        int ia = a.getPath().lastIndexOf('/'), ib = b.getPath().lastIndexOf('/');
        if (ia <= 0 || ia != ib || !a.getPath().regionMatches(0, b.getPath(), 0, ia)) return false;
        Set<String> names = variants();
        return names.contains(a.getPath().substring(ia + 1)) && names.contains(b.getPath().substring(ib + 1));
    }

    static Set<String> variants() {
        if (variants == null) {
            Set<String> names = new HashSet<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                String p = id.getPath();
                if (p.startsWith("rock/loose/")) names.add(p.substring("rock/loose/".length()));
                else if (p.startsWith("dirt/")) names.add(p.substring("dirt/".length()));
            }
            variants = names;
        }
        return variants;
    }
}
