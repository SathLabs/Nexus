package dev.satherov.nexus.test.game.extension.component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.extension.component.MemoizedComponentMap;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.component.LookupGeneration;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

///
/// Checks the result of a costly filter that a patched data component map stores.
///
public class MemoizedComponentMapSamples {
    
    private static final ComponentFilter DAMAGED = ComponentFilter.has(DataComponents.DAMAGE);
    private static final ComponentFilter NAMED = ComponentFilter.has(DataComponents.CUSTOM_NAME);
    private static final BiPredicate<ComponentFilter, DataComponentGetter> HAS_DAMAGE = (_, holder) -> holder.has(DataComponents.DAMAGE);
    private static final BiPredicate<ComponentFilter, DataComponentGetter> IS_DAMAGED_FILTER = (filter, _) -> filter == MemoizedComponentMapSamples.DAMAGED;
    
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
}
