package dev.satherov.nexus.test.unit.gametest.internal.discovery;

import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.test.game.gametest.internal.discovery.DiscoverySamples;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// Checks the identifiers and violations of the methods that [Discovered] finds, and how it invokes them.
///
public class DiscoveredTest {
    
    private static final Map<Identifier, Discovered> SERVER = DiscoveredTest.byId(ServerTest.class);
    
    private static final Map<Identifier, Discovered> CLIENT = DiscoveredTest.byId(ClientTest.class);
    
    @Test
    public void derivesTheSnakeCaseIdOfATopLevelMethod() throws NoSuchMethodException {
        Identifier id = DiscoveredTest.sample("meets_constraints");
        Method method = DiscoverySamples.class.getMethod("meetsConstraints", GameTestHelper.class);
        Assertions.assertThat(DiscoveredTest.SERVER.get(id)).isEqualTo(new Discovered.Valid(id, method, Objects.requireNonNull(method.getAnnotation(ServerTest.class)), null));
    }
    
    @Test
    public void joinsANestedClassWithADot() {
        Assertions.assertThat(DiscoveredTest.SERVER).containsKey(Identifier.fromNamespaceAndPath("nexus", "discovery_samples.hidden_class/hidden_method"));
    }
    
    @Test
    public void keepsTheMeasuredAnnotation() {
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("measured")))
                .asInstanceOf(InstanceOfAssertFactories.type(Discovered.Valid.class))
                .extracting(Discovered.Valid::measured)
                .extracting(Measured::value)
                .isEqualTo(5);
    }
    
    @Test
    public void findsOnlyTheMethodsWithTheGivenAnnotation() {
        Assertions.assertThat(DiscoveredTest.CLIENT.get(DiscoveredTest.sample("client"))).isInstanceOf(Discovered.Valid.class);
        Assertions.assertThat(DiscoveredTest.SERVER).doesNotContainKey(DiscoveredTest.sample("client"));
    }
    
    @ParameterizedTest
    @MethodSource("serverViolations")
    public void reportsTheViolationOfAServerTest(Identifier id, String violation) {
        Assertions.assertThat(DiscoveredTest.SERVER.get(id)).isEqualTo(new Discovered.Invalid(id, violation, false));
    }
    
    private static Stream<Arguments> serverViolations() {
        String samples = DiscoverySamples.class.getName();
        return Stream.of(
                Arguments.of(Identifier.fromNamespaceAndPath("nexus", "discovery_samples.hidden_class/hidden_method"), "Class '" + samples + "$HiddenClass' is not public"),
                Arguments.of(DiscoveredTest.sample("not_public"), "Method '" + samples + "#notPublic' is not public"),
                Arguments.of(DiscoveredTest.sample("not_static"), "Method '" + samples + "#notStatic' is not static"),
                Arguments.of(DiscoveredTest.sample("not_void"), "Method '" + samples + "#notVoid' does not return void"),
                Arguments.of(DiscoveredTest.sample("two_parameters"), "Found more than one parameter, 2 total"),
                Arguments.of(DiscoveredTest.sample("takes_a_string"), "Parameter is not a 'GameTestHelper', found 'String' instead"),
                Arguments.of(DiscoveredTest.sample("empty_window"), "Measured#value is not positive"),
                Arguments.of(DiscoveredTest.sample("no_ticks"), "ServerTest#maxTicks is not positive"),
                Arguments.of(DiscoveredTest.sample("window_overruns_ticks"), "ServerTest#maxTicks is too short for Measured#value"),
                Arguments.of(DiscoveredTest.sample("negative_setup"), "ServerTest#setupTicks is negative"),
                Arguments.of(DiscoveredTest.sample("wrong_structure"), "ServerTest#structure is not an identifier"),
                Arguments.of(DiscoveredTest.sample("same_id"), "Declared twice")
        );
    }
    
    @ParameterizedTest
    @MethodSource("clientViolations")
    public void reportsTheViolationOfAClientTest(Identifier id, String violation) {
        Assertions.assertThat(DiscoveredTest.CLIENT.get(id)).isEqualTo(new Discovered.Invalid(id, violation, false));
    }
    
    private static Stream<Arguments> clientViolations() {
        return Stream.of(
                Arguments.of(DiscoveredTest.sample("client_with_helper"), "Parameter is not a 'Client', found 'GameTestHelper' instead"),
                Arguments.of(DiscoveredTest.sample("no_frames"), "ClientTest#maxFrames is not positive")
        );
    }
    
    @Test
    public void sortsByIdentifier() {
        Assertions.assertThat(Discovered.all(ServerTest.class)).isSortedAccordingTo(Comparator.comparing(Discovered::id));
    }
    
    @Test
    public void readsRequiredFromTheAnnotation() {
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("meets_constraints"))).isInstanceOfSatisfying(Discovered.Valid.class, valid -> Assertions.assertThat(valid.required()).isTrue());
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("optional"))).isInstanceOfSatisfying(Discovered.Valid.class, valid -> Assertions.assertThat(valid.required()).isFalse());
    }
    
    @Test
    public void invokePassesTheParameterAndRethrowsTheTestsOwnException() throws NoSuchMethodException {
        Method method = DiscoverySamples.class.getMethod("throwsItsOwnException", GameTestHelper.class);
        Discovered.Valid valid = new Discovered.Valid(DiscoveredTest.sample("throws_its_own_exception"), method, Objects.requireNonNull(method.getAnnotation(ServerTest.class)), null);
        DiscoverySamples.received = new Object();
        
        Assertions.assertThatThrownBy(() -> valid.invoke(null))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Thrown by the sample on purpose");
        Assertions.assertThat(DiscoverySamples.received).isNull();
    }
    
    private static Identifier sample(String method) {
        return Identifier.fromNamespaceAndPath("nexus", "discovery_samples/" + method);
    }
    
    private static Map<Identifier, Discovered> byId(Class<? extends Annotation> annotation) {
        return Discovered.all(annotation)
                .stream()
                .collect(Collectors.toMap(Discovered::id, Function.identity()));
    }
}
