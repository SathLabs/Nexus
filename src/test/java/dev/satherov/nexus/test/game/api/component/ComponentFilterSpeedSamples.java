package dev.satherov.nexus.test.game.api.component;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.measurement.Timing;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.test.game.api.component.model.CompetitorModel;
import dev.satherov.nexus.test.game.api.component.model.CreateAttributeModel;
import dev.satherov.nexus.test.game.api.component.model.FtbFilterModel;
import dev.satherov.nexus.test.game.api.component.model.IntegratedTunnelsModel;
import dev.satherov.nexus.test.game.api.component.model.LogisticsNetworksModel;
import dev.satherov.nexus.test.game.api.component.model.PipezModel;
import dev.satherov.nexus.test.game.api.component.scenario.ApotheosisScenario;
import dev.satherov.nexus.test.game.api.component.scenario.FarmScenario;
import dev.satherov.nexus.test.game.api.component.scenario.ModdedLootScenario;
import dev.satherov.nexus.test.game.api.component.scenario.Rule;
import dev.satherov.nexus.test.game.api.component.scenario.Scenario;
import dev.satherov.nexus.test.game.api.component.scenario.StorageScenario;

import net.neoforged.neoforge.transfer.item.ItemResource;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.EncoderCache;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.item.ItemStack;

import com.google.common.cache.Cache;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

///
/// Measures the speed of component filters against a handwritten direct read and models of the filters of five other mods.
///
/// The samples run only in a run whose selector names them.
///
/// One cell times the first common storage rule on item resources.
/// Every other cell times a rule of the farm, storage, modded loot, or Apotheosis scenario in one [Scenario.Flow].
///
/// Before timing, every series has to agree with the direct read on each item stack of one untimed pass.
/// The check set of a rule has to pass at least 5 item stacks, or none if the rule is reject-only.
///
/// Each cell runs its series for 28 passes in a balanced Williams order, and each series is measured by its median pass.
/// Every timed pass of a series has to count as many passing item stacks as the direct read.
/// Before every series, an array of 256 MB is read and the encoder cache of vanilla is emptied, so each series starts cold.
///
/// A cell with a common rule fails unless the median of the filter is strictly below that of every model that checks part of the rule with its own code.
/// Every other cell and model is only reported.
///
@Slf4j
public class ComponentFilterSpeedSamples {
    
    private static final int PASSES = 28;
    private static final int MIN_CHECK_PASSED = 5;
    private static final int FILTER = 0;
    private static final int DIRECT = 1;
    private static final List<Supplier<CompetitorModel>> MODELS = List.of(
            PipezModel::new,
            LogisticsNetworksModel::new,
            FtbFilterModel::new,
            IntegratedTunnelsModel::new,
            CreateAttributeModel::new
    );
    // Since the number of series is odd, the rows are the cyclic square of 0 1 6 2 5 3 4 and the same rows reversed.
    // Every 14 rows, each series then runs twice in every position and twice right after every other series.
    private static final int[][] ORDERS = ComponentFilterSpeedSamples.williamsOrders(ComponentFilterSpeedSamples.MODELS.size() + 2);
    // The array has to be larger than the L3 cache.
    private static final long[] EVICTION = new long[256 * 1024 * 1024 / Long.BYTES];
    // Vanilla caches component encodings globally, so a series could otherwise read an encoding that another one wrote.
    private static final Cache<?, ?> ENCODINGS = ComponentFilterSpeedSamples.encoderCache();
    
    // Nothing reads the sum, but the JIT could drop the eviction pass if it went nowhere.
    private static long evictionSum;
    
    private static int[][] williamsOrders(int series) {
        int[] first = new int[series];
        for (int position = 1; position < series; position++) {
            first[position] = position % 2 == 1 ? (position + 1) / 2 : series - position / 2;
        }
        
        int[][] orders = new int[2 * series][series];
        for (int row = 0; row < series; row++) {
            for (int position = 0; position < series; position++) {
                orders[row][position] = (first[position] + row) % series;
                orders[series + row][series - 1 - position] = orders[row][position];
            }
        }
        
        return orders;
    }
    
    @SneakyThrows
    private static Cache<?, ?> encoderCache() {
        Field encoder = DataComponents.class.getDeclaredField("ENCODER_CACHE");
        encoder.setAccessible(true);
        Field cache = EncoderCache.class.getDeclaredField("cache");
        cache.setAccessible(true);
        return (Cache<?, ?>) cache.get(encoder.get(null));
    }
    
    private static void timeScenario(GameTestHelper helper, Function<RegistryAccess, Scenario> factory) {
        long start = System.nanoTime();
        RegistryAccess registries = helper.getLevel().registryAccess();
        int rules = factory.apply(registries).getAllRules().size();
        // Every check set and every cell gets a scenario of its own, so no timed series starts on caches that another one warmed.
        for (int index = 0; index < rules; index++) {
            Scenario scenario = factory.apply(registries);
            ComponentFilterSpeedSamples.checkRule(helper, scenario, scenario.getAllRules().get(index));
        }
        
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < rules; index++) {
            for (Scenario.Flow flow : Scenario.Flow.values()) {
                Scenario scenario = factory.apply(registries);
                Rule rule = scenario.getAllRules().get(index);
                IntFunction<Batch> batches = pass -> Batch.of(scenario.stacks(rule, flow, pass));
                String cell = rule.name() + "_" + flow.name().toLowerCase(Locale.ROOT);
                // The check batch is the pass after the timed ones, so a flow cell checks agreement on stacks that no timed series reads.
                rows.add(ComponentFilterSpeedSamples.timeCell(helper, cell, rule, batches, batches.apply(ComponentFilterSpeedSamples.PASSES)));
            }
        }
        
        ComponentFilterSpeedSamples.report(helper, rows, start);
    }
    
    private static void checkRule(GameTestHelper helper, Scenario scenario, Rule rule) {
        int passed = ComponentFilterSpeedSamples.agreedCount(helper, ComponentFilterSpeedSamples.series(rule), Batch.of(scenario.checkStacks(rule)), rule.name() + "_check");
        if (scenario.isRejectOnly(rule)) {
            helper.assertTrue(passed == 0, "The rule '" + rule.name() + "' passed '" + passed + "' stacks of its check set instead of '0'");
        } else {
            helper.assertTrue(
                    passed >= ComponentFilterSpeedSamples.MIN_CHECK_PASSED,
                    String.format(Locale.ROOT, "The rule '%s' passed '%d' stacks of its check set instead of at least '%d'", rule.name(), passed, ComponentFilterSpeedSamples.MIN_CHECK_PASSED)
            );
        }
    }
    
    private static Row timeCell(GameTestHelper helper, String cell, Rule rule, IntFunction<Batch> batches, Batch check) {
        List<Series> series = ComponentFilterSpeedSamples.series(rule);
        int passed = ComponentFilterSpeedSamples.agreedCount(helper, series, check, cell);
        long[][] nanos = new long[series.size()][ComponentFilterSpeedSamples.PASSES];
        int[] counts = new int[series.size()];
        for (int pass = 0; pass < ComponentFilterSpeedSamples.PASSES; pass++) {
            Batch batch = batches.apply(pass);
            for (int index : ComponentFilterSpeedSamples.ORDERS[pass % ComponentFilterSpeedSamples.ORDERS.length]) {
                ComponentFilterSpeedSamples.evictCaches();
                long start = System.nanoTime();
                counts[index] = series.get(index).count().applyAsInt(batch);
                nanos[index][pass] = System.nanoTime() - start;
            }
            
            for (int index = 0; index < series.size(); index++) {
                if (counts[index] != counts[ComponentFilterSpeedSamples.DIRECT]) {
                    helper.fail(String.format(
                            Locale.ROOT,
                            "The series '%s' passed '%d' stacks of the pass '%d' in the cell '%s' instead of the direct read's '%d'",
                            series.get(index).name(),
                            counts[index],
                            pass,
                            cell,
                            counts[ComponentFilterSpeedSamples.DIRECT]
                    ));
                }
            }
        }
        
        Map<String, Long> models = new LinkedHashMap<>();
        for (int index = 0; index < series.size(); index++) {
            Timing.record(helper, cell + "_" + series.get(index).name(), nanos[index]);
            if (index > ComponentFilterSpeedSamples.DIRECT) {
                models.put(series.get(index).name(), ComponentFilterSpeedSamples.median(nanos[index]));
            }
        }
        
        return new Row(
                cell,
                rule.common(),
                check.stacks().length,
                passed,
                ComponentFilterSpeedSamples.median(nanos[ComponentFilterSpeedSamples.FILTER]),
                ComponentFilterSpeedSamples.median(nanos[ComponentFilterSpeedSamples.DIRECT]),
                models,
                series.stream()
                        .filter(Series::gated)
                        .map(Series::name)
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
    
    private static List<Series> series(Rule rule) {
        Predicate<DataComponentGetter> filter = rule.filter()::test;
        Predicate<ItemStack> direct = rule.direct();
        List<Series> series = new ArrayList<>(List.of(
                new Series("filter", batch -> ComponentFilterSpeedSamples.count(batch.holders(), filter)),
                new Series("direct", batch -> ComponentFilterSpeedSamples.count(batch.stacks(), direct))
        ));
        for (Supplier<CompetitorModel> factory : ComponentFilterSpeedSamples.MODELS) {
            CompetitorModel model = factory.get();
            Predicate<ItemStack> test = model.build(rule.filter());
            series.add(new Series(model.getName(), batch -> ComponentFilterSpeedSamples.count(batch.stacks(), test), model.expresses(rule.filter())));
        }
        
        return series;
    }
    
    private static int agreedCount(GameTestHelper helper, List<Series> series, Batch batch, String cell) {
        int passed = 0;
        for (int index = 0; index < batch.stacks().length; index++) {
            Batch single = batch.slice(index);
            int expected = series.get(ComponentFilterSpeedSamples.DIRECT).count().applyAsInt(single);
            for (Series candidate : series) {
                if (candidate.count().applyAsInt(single) != expected) {
                    ItemStack stack = batch.stacks()[index];
                    helper.fail(String.format(
                            Locale.ROOT,
                            "The series '%s' disagrees with the direct read on the stack '%s' with the patch '%s' in the cell '%s'",
                            candidate.name(),
                            stack,
                            stack.getComponentsPatch(),
                            cell
                    ));
                }
            }
            
            passed += expected;
        }
        
        return passed;
    }
    
    private static void report(GameTestHelper helper, List<Row> rows, long start) {
        ComponentFilterSpeedSamples.log.info(
                "The '{}' cells took '{}' ms:{}",
                rows.size(),
                (System.nanoTime() - start) / TimeUtil.NANOSECONDS_PER_MILLISECOND,
                ComponentFilterSpeedSamples.table(rows)
        );
        List<String> losses = new ArrayList<>();
        for (Row row : rows) {
            List<String> unbeaten = row.models().entrySet().stream()
                    .filter(model -> row.gated().contains(model.getKey()))
                    .filter(model -> model.getValue() <= row.filter())
                    .map(Map.Entry::getKey)
                    .toList();
            if (row.common() && !unbeaten.isEmpty()) {
                losses.add("'" + row.cell() + "' (" + String.join(", ", unbeaten) + ")");
            }
        }
        
        helper.assertTrue(losses.isEmpty(), "The filter's median pass is not below that of every model in the cells " + String.join(", ", losses));
        helper.succeed();
    }
    
    private static String table(List<Row> rows) {
        Row first = rows.getFirst();
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%n%-48s %-6s %7s %7s %12s %12s", "cell", "common", "stacks", "passed", "filter_ns", "direct_ns"));
        for (String model : first.models().keySet()) {
            table.append(String.format(Locale.ROOT, " %22s", model + "_ns"));
        }
        
        table.append(String.format(Locale.ROOT, " %14s", "filter/direct"));
        for (String model : first.models().keySet()) {
            table.append(String.format(Locale.ROOT, " %26s", "filter/" + model));
        }
        
        for (Row row : rows) {
            table.append(String.format(Locale.ROOT, "%n%-48s %-6s %7d %7d %12d %12d", row.cell(), row.common(), row.stacks(), row.passed(), row.filter(), row.direct()));
            for (long model : row.models().values()) {
                table.append(String.format(Locale.ROOT, " %22d", model));
            }
            
            table.append(String.format(Locale.ROOT, " %14.3f", (double) row.filter() / row.direct()));
            for (Map.Entry<String, Long> model : row.models().entrySet()) {
                table.append(String.format(Locale.ROOT, " %25.3f%s", (double) row.filter() / model.getValue(), row.gated().contains(model.getKey()) ? " " : "*"));
            }
        }
        
        table.append(String.format(Locale.ROOT, "%n* The model checks no part of the rule with its own code, so the gate leaves it out."));
        return table.toString();
    }
    
    private static <HOLDER> int count(HOLDER[] holders, Predicate<? super HOLDER> test) {
        int passed = 0;
        for (HOLDER holder : holders) {
            // Every series calls its test from here, so the filter pays the same dispatch as the models.
            if (test.test(holder)) {
                passed++;
            }
        }
        
        return passed;
    }
    
    private static void evictCaches() {
        long sum = 0L;
        for (long value : ComponentFilterSpeedSamples.EVICTION) {
            sum += value;
        }
        
        ComponentFilterSpeedSamples.evictionSum = sum;
        ComponentFilterSpeedSamples.ENCODINGS.invalidateAll();
    }
    
    private static long median(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
    }
    
    @ServerTest(explicit = true)
    public static void timesFarmRules(GameTestHelper helper) {
        ComponentFilterSpeedSamples.timeScenario(helper, FarmScenario::new);
    }
    
    @ServerTest(explicit = true)
    public static void timesStorageRules(GameTestHelper helper) {
        ComponentFilterSpeedSamples.timeScenario(helper, StorageScenario::new);
    }
    
    @ServerTest(explicit = true)
    public static void timesModdedLootRules(GameTestHelper helper) {
        ComponentFilterSpeedSamples.timeScenario(helper, ModdedLootScenario::new);
    }
    
    @ServerTest(explicit = true)
    public static void timesApotheosisRules(GameTestHelper helper) {
        ComponentFilterSpeedSamples.timeScenario(helper, ApotheosisScenario::new);
    }
    
    @ServerTest(explicit = true)
    public static void timesItemResources(GameTestHelper helper) {
        long start = System.nanoTime();
        Scenario scenario = new StorageScenario(helper.getLevel().registryAccess());
        Rule rule = scenario.getAllRules().stream()
                .filter(Rule::common)
                .findFirst()
                .orElseThrow();
        ItemStack[] stacks = scenario.stacks(rule, Scenario.Flow.SCAN, 0).toArray(ItemStack[]::new);
        Batch batch = new Batch(stacks, Arrays.stream(stacks).map(ItemResource::of).toArray(ItemResource[]::new));
        Row row = ComponentFilterSpeedSamples.timeCell(helper, rule.name() + "_resources", rule, _ -> batch, batch);
        ComponentFilterSpeedSamples.report(helper, List.of(row), start);
    }
    
    private record Series(String name, ToIntFunction<Batch> count, boolean gated) {
        
        private Series(String name, ToIntFunction<Batch> count) {
            this(name, count, false);
        }
    }
    
    private record Batch(ItemStack[] stacks, DataComponentGetter[] holders) {
        
        private static Batch of(List<ItemStack> stacks) {
            ItemStack[] tested = stacks.toArray(ItemStack[]::new);
            return new Batch(tested, tested);
        }
        
        private Batch slice(int index) {
            return new Batch(new ItemStack[]{ this.stacks[index] }, new DataComponentGetter[]{ this.holders[index] });
        }
    }
    
    private record Row(String cell, boolean common, int stacks, int passed, long filter, long direct, Map<String, Long> models, Set<String> gated) { }
}
