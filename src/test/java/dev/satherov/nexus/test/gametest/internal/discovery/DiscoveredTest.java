package dev.satherov.nexus.test.gametest.internal.discovery;

import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

///
/// Checks that [Discovered] derives ids, accepts the samples that meet the constraints, and rejects the rest with a violation.
///
public class DiscoveredTest {

    private static final Map<Identifier, Discovered> SERVER = DiscoveredTest.byId(Discovered.all(ServerTest.class));

    private static final Map<Identifier, Discovered> CLIENT = DiscoveredTest.byId(Discovered.all(ClientTest.class));

    private static Map<Identifier, Discovered> byId(List<Discovered> found) {
        return found.stream().collect(Collectors.toMap(Discovered::id, Function.identity()));
    }

    private static Identifier sample(String method) {
        return Identifier.fromNamespaceAndPath("nexus", "discovery_samples/" + method);
    }

    @Test
    void listsInIdOrder() {
        Assertions.assertThat(Discovered.all(ServerTest.class)).isSortedAccordingTo(Comparator.comparing(Discovered::id));
    }

    @Test
    void acceptsMethodMeetingConstraints() throws NoSuchMethodException {
        Method valid = DiscoverySamples.class.getMethod("valid", GameTestHelper.class);
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("valid"))).isEqualTo(new Discovered.Valid(DiscoveredTest.sample("valid"), valid, valid.getAnnotation(ServerTest.class), null));
    }

    @Test
    void keepsMeasuredAnnotation() {
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("measured")))
                .asInstanceOf(InstanceOfAssertFactories.type(Discovered.Valid.class))
                .extracting(Discovered.Valid::measured)
                .extracting(Measured::value)
                .isEqualTo(5);
    }

    @Test
    void acceptsClientTestWithClientParameter() {
        Assertions.assertThat(DiscoveredTest.CLIENT.get(DiscoveredTest.sample("client"))).isInstanceOf(Discovered.Valid.class);
        Assertions.assertThat(DiscoveredTest.SERVER).doesNotContainKey(DiscoveredTest.sample("client"));
    }

    @Test
    void rejectsWithReason() {
        Assertions.assertThat(DiscoveredTest.SERVER.get(Identifier.fromNamespaceAndPath("nexus", "discovery_samples.hidden/method"))).isEqualTo(new Discovered.Invalid(Identifier.fromNamespaceAndPath("nexus", "discovery_samples.hidden/method"), "class is not public", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("not_public"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("not_public"), "method is not public", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("not_static"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("not_static"), "method is not static", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("not_void"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("not_void"), "method does not return void", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("wrong_parameter"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("wrong_parameter"), "parameter is not a single GameTestHelper", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("no_ticks"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("no_ticks"), "ServerTest#maxTicks is not positive", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("negative_setup"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("negative_setup"), "ServerTest#setupTicks is negative", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("wrong_structure"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("wrong_structure"), "ServerTest#structure is not an id", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("empty_window"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("empty_window"), "Measured#value is not positive", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("window_overruns_ticks"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("window_overruns_ticks"), "ServerTest#maxTicks is too short for Measured#value", false));
        Assertions.assertThat(DiscoveredTest.SERVER.get(DiscoveredTest.sample("same_id"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("same_id"), "declared twice", false));
        Assertions.assertThat(DiscoveredTest.CLIENT.get(DiscoveredTest.sample("no_frames"))).isEqualTo(new Discovered.Invalid(DiscoveredTest.sample("no_frames"), "ClientTest#maxFrames is not positive", false));
    }
}
