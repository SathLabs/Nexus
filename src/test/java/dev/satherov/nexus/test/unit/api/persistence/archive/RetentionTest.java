package dev.satherov.nexus.test.unit.api.persistence.archive;

import dev.satherov.nexus.api.persistence.archive.NameKey;
import dev.satherov.nexus.api.persistence.archive.Retention;
import dev.satherov.nexus.api.persistence.archive.TimedKey;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

///
/// Checks which keys each retention rule drops from the sorted keys of one group.
///
public class RetentionTest {

    private static final Instant NOW = Instant.now();

    @Test
    public void foreverDropsNothing() {
        Assertions.assertThat(Retention.<NameKey>forever().drop(RetentionTest.names("a", "b", "c"))).isEmpty();
    }

    @Test
    public void newestKeepsTheGreatestKeys() {
        Retention<NameKey> rule = Retention.newest(() -> 2);

        Assertions.assertThat(rule.drop(RetentionTest.names("a", "b", "c", "d"))).containsExactlyInAnyOrderElementsOf(RetentionTest.names("a", "b"));
        Assertions.assertThat(rule.drop(RetentionTest.names("a"))).isEmpty();
    }

    @Test
    public void newestReadsTheCountOnEveryRun() {
        AtomicInteger count = new AtomicInteger(2);
        Retention<NameKey> rule = Retention.newest(count::get);
        Assertions.assertThat(rule.drop(RetentionTest.names("a", "b", "c"))).containsExactlyInAnyOrderElementsOf(RetentionTest.names("a"));

        count.set(1);
        Assertions.assertThat(rule.drop(RetentionTest.names("a", "b", "c"))).containsExactlyInAnyOrderElementsOf(RetentionTest.names("a", "b"));
    }

    @Test
    public void maxAgeDropsTheKeysOlderThanTheDays() {
        Stamp old = RetentionTest.stamp(Duration.ofDays(8));
        Stamp recent = RetentionTest.stamp(Duration.ofDays(6));

        Assertions.assertThat(Retention.<Stamp>maxAge(() -> 7).drop(List.of(old, recent))).containsExactly(old);
    }

    @Test
    public void maxAgeReadsTheDaysOnEveryRun() {
        AtomicInteger days = new AtomicInteger(7);
        Retention<Stamp> rule = Retention.maxAge(days::get);
        Stamp old = RetentionTest.stamp(Duration.ofDays(8));
        Stamp recent = RetentionTest.stamp(Duration.ofDays(6));
        Assertions.assertThat(rule.drop(List.of(old, recent))).containsExactly(old);

        days.set(5);
        Assertions.assertThat(rule.drop(List.of(old, recent))).containsExactlyInAnyOrder(old, recent);
    }

    @Test
    public void andDropsWhatEitherRuleDrops() {
        Retention<Stamp> rule = Retention.<Stamp>newest(() -> 2).and(Retention.maxAge(() -> 7));
        Stamp old = RetentionTest.stamp(Duration.ofDays(9));
        Stamp third = RetentionTest.stamp(Duration.ofHours(3));
        Stamp second = RetentionTest.stamp(Duration.ofHours(2));
        Stamp first = RetentionTest.stamp(Duration.ofHours(1));

        Assertions.assertThat(rule.drop(List.of(third, second, first))).containsExactly(third);
        Assertions.assertThat(rule.drop(List.of(old, first))).containsExactly(old);
    }

    @Test
    public void onlyShowsTheRuleTheKeysPassingTheFilter() {
        Retention<NameKey> rule = Retention.<NameKey>newest(() -> 1).only(key -> key.name().startsWith("a"));

        Assertions.assertThat(rule.drop(RetentionTest.names("a1", "a2", "b1", "b2"))).containsExactly(new NameKey("a1"));
    }

    private static List<NameKey> names(String... names) {
        return Stream.of(names).map(NameKey::new).toList();
    }

    private static Stamp stamp(Duration age) {
        return new Stamp(RetentionTest.NOW.minus(age));
    }

    private record Stamp(Instant time) implements TimedKey<Stamp> {

        @Override
        public List<String> segments() {
            return List.of(Long.toString(this.time.getEpochSecond()));
        }
    }
}
