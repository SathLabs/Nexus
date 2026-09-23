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
/// Registers all server tests to the registry event.
///
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = "nexus_gametest")
public class ServerTests {
    
    ///
    /// The empty environment that every test that is not measured runs in.
    ///
    private static final Identifier ENVIRONMENT = Identifier.fromNamespaceAndPath("nexus_gametest", "default");
    
    ///
    /// All found tests that were selected with the current run options.
    ///
    private static final Lazy<List<Discovered>> TESTS = Lazy.of(ServerTests::selected);
    
    ///
    /// The manager for the measurements of the run.
    ///
    private static final Lazy<Measurements> MEASUREMENTS = Lazy.of(ServerTests::measurements);
    
    ///
    /// Registers the function of every selected test, and the baseline if the run has one.
    ///
    /// @param event The registry event that the functions are registered with.
    ///
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, register -> {
            for (Discovered test : ServerTests.TESTS.get()) {
                register.register(test.id(), ServerTests.function(test));
            }
            
            if (ServerTests.hasBaseline()) {
                register.register(Measurements.BASELINE, TickWindow.baseline(ServerTests.MEASUREMENTS.get()));
            }
        });
    }
    
    ///
    /// Decides what to actually register for each discovered test:
    /// - [Discovered.Valid] when the test is measured will register a [TickWindow].
    /// - [Discovered.Valid] otherwise will register the test method invoker.
    /// - [Discovered.Invalid] will register a failure invoker.
    ///
    /// @param test The discovered test.
    ///
    /// @return What the test runs as.
    ///
    private static Consumer<GameTestHelper> function(Discovered test) {
        return switch (test) {
            case Discovered.Valid valid when ServerTests.isMeasured(valid) -> new TickWindow(valid, ServerTests.MEASUREMENTS.get());
            case Discovered.Valid valid -> valid::invoke;
            case Discovered.Invalid(_, String reason, _) -> helper -> helper.fail(reason);
        };
    }
    
    ///
    /// Registers every selected test with its environment, and the baseline if the run has one.
    ///
    /// @param event The event that the tests are registered with.
    ///
    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(ServerTests.ENVIRONMENT);
        
        for (Discovered test : ServerTests.TESTS.get()) {
            // Measures need to get their own environment because vanilla will give each of those a separate time window to prevent outside interference from other tests.
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
    /// Registers a given [TestData] to the neoforge event.
    ///
    /// @param event The event that the test will be registered with.
    /// @param id    The identifier of the test.
    /// @param data  The data of the test that is registered.
    ///
    private static void registerTest(RegisterGameTestsEvent event, Identifier id, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }
    
    ///
    /// Creates the test data for a given discovered test and its environment.
    ///
    /// @param test        The discovered test.
    /// @param environment The environment that the test runs in.
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
    /// If a given test needs to run in a separate time window of its own.
    ///
    /// @param test A discovered test.
    ///
    /// @return `true` if the test needs to run in a separate time window.
    ///
    private static boolean isMeasured(Discovered test) {
        return test instanceof Discovered.Valid valid && valid.measured() != null;
    }
    
    ///
    /// If the run has any baseline measurements.
    ///
    /// @return `true` if the run has a baseline.
    ///
    private static boolean hasBaseline() {
        return ServerTests.MEASUREMENTS.get().baselineLength() > 0;
    }
    
    ///
    /// Every discovered test that the run selects.
    ///
    /// @return Every discovered test that the run selects.
    ///
    private static List<Discovered> selected() {
        RunOptions options = RunOptions.fromProperties();
        return Discovered.all(ServerTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();
    }
    
    ///
    /// The measurements of the run, written into the same directory as its final result.
    ///
    /// @return The measurements of the run.
    ///
    private static Measurements measurements() {
        RunOptions options = RunOptions.fromProperties();
        return new Measurements(options.report().toAbsolutePath().getParent(), options.compare(), ServerTests.TESTS.get());
    }
}
