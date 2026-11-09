package dev.satherov.nexus.test.game.extension.component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;
import dev.satherov.nexus.extension.component.MemoizedComponentMap;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.component.LookupGeneration;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

///
/// Checks the result of a filter that a patched data component map stores.
///
public class MemoizedComponentMapSamples {
    
    private static final ComponentFilter DAMAGED = ComponentFilter.has(DataComponents.DAMAGE);
    private static final ComponentFilter NAMED = ComponentFilter.has(DataComponents.CUSTOM_NAME);
    private static final BiPredicate<ComponentFilter, DataComponentGetter> HAS_DAMAGE = (_, holder) -> holder.has(DataComponents.DAMAGE);
    private static final BiPredicate<ComponentFilter, DataComponentGetter> IS_DAMAGED_FILTER = (filter, _) -> filter == MemoizedComponentMapSamples.DAMAGED;
    private static final Comparison AT_LEAST_ZERO = new Comparison(Comparison.Operator.GREATER_OR_EQUAL, new Comparison.Literal.Numeric(0.0D));
    private static final Comparison AT_MOST_TEN = new Comparison(Comparison.Operator.LESS_OR_EQUAL, new Comparison.Literal.Numeric(10.0D));
    private static final Map<String, PathReader<Integer>> READERS = Map.of(
            "direct", new CountingReader(PathReader.Cost.DIRECT),
            "scan", new CountingReader(PathReader.Cost.SCAN)
    );
    
    private static @Nullable DataComponentEntry<Integer> level;
    private static int reads;
    
    @ServerTest
    public static void everyWriteDropsTheStoredResult(GameTestHelper helper) {
        TypedDataComponent<Integer> damage = new TypedDataComponent<>(DataComponents.DAMAGE, 1);
        DataComponentMap components = DataComponentMap.builder().set(DataComponents.DAMAGE, 1).build();
        DataComponentPatch patch = DataComponentPatch.builder().set(DataComponents.DAMAGE, 1).build();
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "set", MemoizedComponentMapSamples.undamaged(), map -> map.set(DataComponents.DAMAGE, 1));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "typed set", MemoizedComponentMapSamples.undamaged(), map -> map.set(damage));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "set of all", MemoizedComponentMapSamples.undamaged(), map -> map.setAll(components));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "removal", MemoizedComponentMapSamples.damaged(), map -> map.remove(DataComponents.DAMAGE));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "applied patch", MemoizedComponentMapSamples.undamaged(), map -> map.applyPatch(patch));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "restored patch", MemoizedComponentMapSamples.damaged(), map -> map.restorePatch(DataComponentPatch.EMPTY));
        MemoizedComponentMapSamples.assertWriteIsSeen(helper, "cleared patch", MemoizedComponentMapSamples.damaged(), PatchedDataComponentMap::clearPatch);
        helper.succeed();
    }
    
    @ServerTest
    public static void writeToACopyIsSeenOnlyOnTheCopy(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        PatchedDataComponentMap original = MemoizedComponentMapSamples.undamaged();
        CountingTester originalTester = new CountingTester(original, MemoizedComponentMapSamples.HAS_DAMAGE);
        helper.assertValueEqual(originalTester.results(damaged, damaged), List.of(false, false), "results of the original");
        
        PatchedDataComponentMap copy = original.copy();
        CountingTester copyTester = new CountingTester(copy, MemoizedComponentMapSamples.HAS_DAMAGE);
        helper.assertValueEqual(copyTester.results(damaged, damaged), List.of(false, false), "results of the copy before the write");
        helper.assertValueEqual(copyTester.evaluations, 2, "evaluations of the copy before the write");
        
        copy.set(DataComponents.DAMAGE, 1);
        helper.assertValueEqual(originalTester.results(damaged), List.of(false), "result of the original after the write");
        helper.assertValueEqual(originalTester.evaluations, 2, "evaluations of the original after the write");
        helper.assertValueEqual(copyTester.results(damaged), List.of(true), "result of the copy after the write");
        helper.assertValueEqual(copyTester.evaluations, 3, "evaluations of the copy after the write");
        helper.succeed();
    }
    
    @ServerTest
    public static void everyFilterGetsItsOwnResult(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        ComponentFilter named = MemoizedComponentMapSamples.NAMED;
        CountingTester tester = new CountingTester(MemoizedComponentMapSamples.undamaged(), MemoizedComponentMapSamples.IS_DAMAGED_FILTER);
        helper.assertValueEqual(
                tester.results(damaged, damaged, damaged, named, named, named, damaged, damaged, damaged),
                List.of(true, true, true, false, false, false, true, true, true),
                "results of the filters"
        );
        helper.assertValueEqual(tester.evaluations, 6, "evaluations");
        helper.succeed();
    }
    
    @ServerTest
    public static void alternatingFiltersNeverStore(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        ComponentFilter named = MemoizedComponentMapSamples.NAMED;
        CountingTester tester = new CountingTester(MemoizedComponentMapSamples.undamaged(), MemoizedComponentMapSamples.IS_DAMAGED_FILTER);
        helper.assertValueEqual(tester.results(damaged, named, damaged, named, damaged), List.of(true, false, true, false, true), "results of the alternating filters");
        helper.assertValueEqual(tester.evaluations, 5, "evaluations of the alternating filters");
        helper.assertValueEqual(tester.results(damaged, damaged), List.of(true, true), "results of the same filter in a row");
        helper.assertValueEqual(tester.evaluations, 6, "evaluations of the same filter in a row");
        helper.succeed();
    }
    
    @ServerTest
    public static void anotherHolderDoesNotGetTheStoredResult(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        PatchedDataComponentMap map = MemoizedComponentMapSamples.undamaged();
        CountingTester tester = new CountingTester(map, (_, holder) -> holder == map);
        helper.assertValueEqual(tester.results(damaged, damaged, damaged), List.of(true, true, true), "results for the map");
        helper.assertValueEqual(tester.test(damaged, DataComponentMap.EMPTY), false, "result for another holder");
        helper.assertValueEqual(tester.evaluations, 3, "evaluations");
        helper.succeed();
    }
    
    @ServerTest
    public static void tagUpdateDropsTheStoredResult(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        CountingTester tester = new CountingTester(MemoizedComponentMapSamples.undamaged(), MemoizedComponentMapSamples.HAS_DAMAGE);
        int generation = LookupGeneration.current();
        helper.assertValueEqual(tester.results(damaged, damaged, damaged), List.of(false, false, false), "results before the tag update");
        helper.assertValueEqual(tester.evaluations, 2, "evaluations before the tag update");
        helper.assertValueEqual(LookupGeneration.current(), generation, "generation before the tag update");
        
        MemoizedComponentMapSamples.updateTags(helper);
        helper.assertTrue(LookupGeneration.current() != generation, "The tag update didn't start a new generation");
        helper.assertValueEqual(tester.results(damaged), List.of(false), "result after the tag update");
        helper.assertValueEqual(tester.evaluations, 3, "evaluations after the tag update");
        helper.succeed();
    }
    
    @ServerTest
    public static void writeDuringTheEvaluationIsSeen(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        PatchedDataComponentMap map = MemoizedComponentMapSamples.undamaged();
        CountingTester tester = new CountingTester(map, (_, holder) -> {
            boolean before = holder.has(DataComponents.DAMAGE);
            map.set(DataComponents.DAMAGE, 1);
            return before;
        });
        helper.assertValueEqual(tester.results(damaged, damaged, damaged), List.of(false, true, true), "results");
        helper.assertValueEqual(tester.evaluations, 3, "evaluations");
        helper.succeed();
    }
    
    @ServerTest
    public static void tagUpdateDuringTheEvaluationIsSeen(GameTestHelper helper) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        CountingTester tester = new CountingTester(MemoizedComponentMapSamples.undamaged(), (_, holder) -> {
            MemoizedComponentMapSamples.updateTags(helper);
            return holder.has(DataComponents.DAMAGE);
        });
        helper.assertValueEqual(tester.results(damaged, damaged, damaged), List.of(false, false, false), "results");
        helper.assertValueEqual(tester.evaluations, 3, "evaluations");
        helper.succeed();
    }
    
    @ServerTest
    public static void resultOfTwoDirectReadsIsStored(GameTestHelper helper) {
        ComponentFilter atLeastZero = MemoizedComponentMapSamples.compare("direct", MemoizedComponentMapSamples.AT_LEAST_ZERO);
        ComponentFilter atMostTen = MemoizedComponentMapSamples.compare("direct", MemoizedComponentMapSamples.AT_MOST_TEN);
        MemoizedComponentMapSamples.assertReadsOfTheThirdTest(helper, "all-of", atLeastZero.and(atMostTen), 0);
        MemoizedComponentMapSamples.assertReadsOfTheThirdTest(helper, "any-of", atLeastZero.or(atMostTen), 0);
        helper.succeed();
    }
    
    @ServerTest
    public static void resultOfOneDirectReadIsNeverStored(GameTestHelper helper) {
        ComponentFilter atLeastZero = MemoizedComponentMapSamples.compare("direct", MemoizedComponentMapSamples.AT_LEAST_ZERO);
        MemoizedComponentMapSamples.assertReadsOfTheThirdTest(helper, "compare", atLeastZero, 1);
        MemoizedComponentMapSamples.assertReadsOfTheThirdTest(helper, "negated compare", atLeastZero.negate(), 1);
        helper.succeed();
    }
    
    @ServerTest
    public static void resultOfAScanReadIsStored(GameTestHelper helper) {
        ComponentFilter atLeastZero = MemoizedComponentMapSamples.compare("scan", MemoizedComponentMapSamples.AT_LEAST_ZERO);
        MemoizedComponentMapSamples.assertReadsOfTheThirdTest(helper, "compare", atLeastZero, 0);
        helper.succeed();
    }
    
    private static PatchedDataComponentMap undamaged() {
        return new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }
    
    private static PatchedDataComponentMap damaged() {
        PatchedDataComponentMap map = MemoizedComponentMapSamples.undamaged();
        map.set(DataComponents.DAMAGE, 1);
        return map;
    }
    
    private static void updateTags(GameTestHelper helper) {
        //noinspection UnstableApiUsage The event of the client stands in for any update of the tags.
        NeoForge.EVENT_BUS.post(new TagsUpdatedEvent.ClientPacketReceived(helper.getLevel().registryAccess(), false));
    }
    
    private static void assertWriteIsSeen(GameTestHelper helper, String write, PatchedDataComponentMap map, Consumer<PatchedDataComponentMap> action) {
        ComponentFilter damaged = MemoizedComponentMapSamples.DAMAGED;
        CountingTester tester = new CountingTester(map, MemoizedComponentMapSamples.HAS_DAMAGE);
        boolean before = map.has(DataComponents.DAMAGE);
        helper.assertValueEqual(tester.results(damaged, damaged, damaged), List.of(before, before, before), "results before the " + write);
        helper.assertValueEqual(tester.evaluations, 2, "evaluations before the " + write);
        
        action.accept(map);
        helper.assertValueEqual(tester.results(damaged), List.of(!before), "result after the " + write);
        helper.assertValueEqual(tester.evaluations, 3, "evaluations after the " + write);
    }
    
    private static DataComponentEntry<Integer> level() {
        return Objects.requireNonNull(MemoizedComponentMapSamples.level);
    }
    
    private static ComponentFilter compare(String reader, Comparison comparison) {
        return ComponentFilter.compare(MemoizedComponentMapSamples.level().path(reader), comparison);
    }
    
    private static void assertReadsOfTheThirdTest(GameTestHelper helper, String kind, ComponentFilter filter, int expected) {
        ItemStack stack = new ItemStack(Items.STICK);
        MemoizedComponentMapSamples.level().set(stack, 3);
        boolean first = filter.test(stack);
        helper.assertValueEqual(filter.test(stack), first, "result of the second test of the " + kind);
        
        int before = MemoizedComponentMapSamples.reads;
        helper.assertValueEqual(filter.test(stack), first, "result of the third test of the " + kind);
        helper.assertValueEqual(MemoizedComponentMapSamples.reads - before, expected, "reads of the third test of the " + kind);
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CountingTester {
        
        private final PatchedDataComponentMap map;
        private final BiPredicate<ComponentFilter, DataComponentGetter> evaluation;
        private int evaluations;
        
        private List<Boolean> results(ComponentFilter... filters) {
            return Arrays.stream(filters).map(filter -> this.test(filter, this.map)).toList();
        }
        
        private boolean test(ComponentFilter filter, DataComponentGetter holder) {
            return ((MemoizedComponentMap) (Object) this.map).nexus$test(filter, holder, this::evaluate);
        }
        
        private boolean evaluate(ComponentFilter filter, DataComponentGetter holder) {
            this.evaluations++;
            return this.evaluation.test(filter, holder);
        }
    }
    
    private record CountingReader(PathReader.Cost cost) implements PathReader<Integer> {
        
        @Override
        public PathReader.Cost getCost() {
            return this.cost;
        }
        
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        @Override
        public Predicate<Integer> bind(Comparison comparison) {
            return value -> {
                MemoizedComponentMapSamples.reads++;
                return comparison.test(value.doubleValue());
            };
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            MemoizedComponentMapSamples.level = Nexus.getMod().getRegistrations().component("memoized_component_map_samples_level", NexusCodec.INT).register();
        }
        
        @SubscribeEvent
        public static void onRegisterPathReaders(RegisterPathReadersEvent event) {
            event.register(
                    MemoizedComponentMapSamples.level().value(),
                    steps -> steps.size() == 1 && steps.getFirst() instanceof ComponentPath.Step.Key(String name) ? MemoizedComponentMapSamples.READERS.get(name) : null
            );
        }
    }
}
