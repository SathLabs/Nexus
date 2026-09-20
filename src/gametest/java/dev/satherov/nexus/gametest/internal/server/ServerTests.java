package dev.satherov.nexus.gametest.internal.server;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Consumer;

///
/// Registers every discovered server test: its function on [RegisterEvent], its instance on [RegisterGameTestsEvent].
///
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = "nexus_gametest")
public class ServerTests {

    ///
    /// The empty environment every unmeasured server test runs in.
    ///
    private static final Identifier ENVIRONMENT = Identifier.fromNamespaceAndPath("nexus_gametest", "default");

    ///
    /// Every discovered test the run selects, the invalid ones included.
    ///
    private static final Lazy<List<Discovered>> TESTS = Lazy.of(ServerTests::selected);

    ///
    /// The measurements of the run, shared by every window it registers.
    ///
    private static final Lazy<Measurements> MEASUREMENTS = Lazy.of(ServerTests::measurements);

    ///
    /// Registers the function of every selected test, and the baseline where the run writes one.
    ///
    /// @param event The registry event the functions are registered on.
    ///
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            for (Discovered test : ServerTests.TESTS.get()) {
                functions.register(test.id(), ServerTests.function(test));
            }

            if (ServerTests.hasBaseline()) {
                functions.register(Measurements.BASELINE, TickWindow.baseline(ServerTests.MEASUREMENTS.get()));
            }
        });
    }

    ///
    /// The window of a measured test, the discovered method of a plain one, a failure with the reason of an invalid one.
    ///
    /// @param test The discovered test.
    ///
    /// @return The function the test runs as.
    ///
    private static Consumer<GameTestHelper> function(Discovered test) {
        return switch (test) {
            case Discovered.Valid valid when ServerTests.isMeasured(valid) -> new TickWindow(valid, ServerTests.MEASUREMENTS.get());
            case Discovered.Valid valid -> valid::invoke;
            case Discovered.Invalid(_, String reason, _) -> helper -> helper.fail(reason);
        };
    }

    ///
    /// Registers every selected test against the function of the same id, and the baseline where the run writes one.
    ///
    /// @param event The event the tests and their environments are registered on.
    ///
    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(ServerTests.ENVIRONMENT);

        for (Discovered test : ServerTests.TESTS.get()) {
            // An environment of its own has vanilla batch the test alone, so its window holds no other test's cost.
            Holder<TestEnvironmentDefinition<?>> environment = ServerTests.isMeasured(test) ? event.registerEnvironment(test.id()) : shared;
            ServerTests.registerTest(event, test.id(), ServerTests.data(test, environment));
        }

        if (ServerTests.hasBaseline()) {
            int window = ServerTests.MEASUREMENTS.get().baselineLength();
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                    event.registerEnvironment(Measurements.BASELINE),
                    Identifier.parse(ServerTest.DEFAULT_STRUCTURE),
                    window + Discovered.WINDOW_OVERHEAD,
                    0,
                    false
            );

            ServerTests.registerTest(event, Measurements.BASELINE, data);
        }
    }

    ///
    /// Registers the instance of the id against the function registered under it.
    ///
    /// @param event The event the test is registered on.
    /// @param id    The id of the test.
    /// @param data  The data the test is registered with.
    ///
    private static void registerTest(RegisterGameTestsEvent event, Identifier id, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    ///
    /// The annotation's data for a valid test, one tick in the default structure for an invalid one.
    ///
    /// @param test        The discovered test.
    /// @param environment The environment the test runs in.
    ///
    /// @return The data the test is registered with.
    ///
    private static TestData<Holder<TestEnvironmentDefinition<?>>> data(Discovered test, Holder<TestEnvironmentDefinition<?>> environment) {
        if (test instanceof Discovered.Valid(_, _, ServerTest annotation, _)) {
            return new TestData<>(
                    environment,
                    Identifier.parse(annotation.structure()),
                    annotation.maxTicks(),
                    annotation.setupTicks(),
                    annotation.required(),
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    annotation.skyAccess(),
                    0
            );
        }

        return new TestData<>(environment, Identifier.parse(ServerTest.DEFAULT_STRUCTURE), 1, 0, test.required());
    }

    ///
    /// If the test records a window of its own.
    ///
    /// @param test The discovered test.
    ///
    /// @return `true` if the test records a window of its own.
    ///
    private static boolean isMeasured(Discovered test) {
        return test instanceof Discovered.Valid valid && valid.measured() != null;
    }

    ///
    /// If the run writes a baseline: it takes a measured test for the baseline to be compared against.
    ///
    /// @return `true` if the run writes a baseline.
    ///
    private static boolean hasBaseline() {
        return ServerTests.MEASUREMENTS.get().baselineLength() > 0;
    }

    ///
    /// Every discovered test the run selects, the invalid ones included.
    ///
    /// @return Every discovered test the run selects, the invalid ones included.
    ///
    private static List<Discovered> selected() {
        RunOptions options = RunOptions.fromProperties();
        return Discovered.all(ServerTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();
    }

    ///
    /// The measurements of the run, written next to its report.
    ///
    /// @return The measurements of the run.
    ///
    private static Measurements measurements() {
        RunOptions options = RunOptions.fromProperties();
        return new Measurements(options.report().toAbsolutePath().getParent(), options.compare(), ServerTests.TESTS.get());
    }
}
