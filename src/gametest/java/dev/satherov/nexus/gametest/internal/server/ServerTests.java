package dev.satherov.nexus.gametest.internal.server;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/// Registers every discovered server test: its function on [RegisterEvent], its instance on [RegisterGameTestsEvent].
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = "nexus_gametest")
public class ServerTests {

    /// The empty environment every server test runs in.
    private static final Identifier ENVIRONMENT = Identifier.fromNamespaceAndPath("nexus_gametest", "default");

    /// Registers the function of every selected test.
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            for (Discovered test : ServerTests.selected()) {
                functions.register(test.id(), ServerTests.function(test));
            }
        });
    }

    /// Registers every selected test against the function of the same id.
    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(ServerTests.ENVIRONMENT);

        for (Discovered test : ServerTests.selected()) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, test.id());
            event.registerTest(test.id(), new FunctionGameTestInstance(function, ServerTests.data(test, environment)));
        }
    }

    /// Every discovered test the run selects, the invalid ones included.
    private static List<Discovered> selected() {
        RunOptions options = RunOptions.fromProperties();
        return Discovered.all(ServerTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();
    }

    /// The discovered method for a valid test, a failure with the reason for an invalid one.
    private static Consumer<GameTestHelper> function(Discovered test) {
        return switch (test) {
            case Discovered.Valid(_, Method method, _, _) -> helper -> ServerTests.invoke(method, helper);
            case Discovered.Invalid(_, String reason, _) -> helper -> helper.fail(reason);
        };
    }

    /// Unwraps the failure of the test, so the report names it and not the reflective call.
    @SneakyThrows
    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    /// The annotation's data for a valid test, one tick in the default structure for an invalid one.
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
}
