package dev.satherov.nexus.test.unit.internal.tick;

import dev.satherov.nexus.internal.tick.TickStatistics;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.Phaser;

///
/// Checks how tick statistics are recorded, copied, and reset.
///
public class TickStatisticsTest {
    
    private static final String FIRST = "tick_statistics_test_first";
    private static final String SECOND = "tick_statistics_test_second";
    private static final int TICKS_PER_THREAD = 100_000;
    private static final boolean ENABLED_AT_LOAD = TickStatistics.isEnabled();
    
    @BeforeEach
    public void setUp() {
        TickStatistics.setEnabled(false);
        TickStatistics.reset();
    }
    
    @AfterEach
    public void tearDown() {
        TickStatistics.setEnabled(false);
        TickStatistics.reset();
    }
    
    @Test
    public void isOffByDefault() {
        Assertions.assertThat(TickStatisticsTest.ENABLED_AT_LOAD).isFalse();
    }
    
    @Test
    public void recordsNothingWhileOff() {
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        Assertions.assertThat(TickStatistics.getAll()).doesNotContainKey(TickStatisticsTest.FIRST);
    }
    
    @Test
    public void recordsNothingOnceSwitchedOff() {
        TickStatistics.setEnabled(true);
        TickStatistics.setEnabled(false);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        Assertions.assertThat(TickStatistics.getAll()).doesNotContainKey(TickStatisticsTest.FIRST);
    }
    
    @Test
    public void summarizesEveryTickOfATicker() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        TickStatistics.record(TickStatisticsTest.FIRST, 30L);
        TickStatistics.record(TickStatisticsTest.FIRST, 20L);
        Assertions.assertThat(TickStatistics.getAll())
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum, LongSummaryStatistics::getMin, LongSummaryStatistics::getMax)
                .containsExactly(3L, 60L, 10L, 30L);
    }
    
    @Test
    public void keepsTickersApart() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        TickStatistics.record(TickStatisticsTest.SECOND, 20L);
        TickStatistics.record(TickStatisticsTest.SECOND, 40L);
        Map<String, LongSummaryStatistics> statistics = TickStatistics.getAll();
        Assertions.assertThat(statistics)
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum)
                .containsExactly(1L, 10L);
        Assertions.assertThat(statistics)
                .extractingByKey(TickStatisticsTest.SECOND)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum)
                .containsExactly(2L, 60L);
    }
    
    @Test
    public void keepsWhatWasRecordedOnceSwitchedOff() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        TickStatistics.setEnabled(false);
        Assertions.assertThat(TickStatistics.getAll())
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum)
                .containsExactly(1L, 10L);
    }
    
    @Test
    public void forgetsEverythingOnReset() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        TickStatistics.record(TickStatisticsTest.SECOND, 20L);
        TickStatistics.reset();
        Assertions.assertThat(TickStatistics.getAll()).doesNotContainKeys(TickStatisticsTest.FIRST, TickStatisticsTest.SECOND);
    }
    
    @Test
    public void keepsAnEarlierCopyUnchanged() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        Map<String, LongSummaryStatistics> copy = TickStatistics.getAll();
        TickStatistics.record(TickStatisticsTest.FIRST, 20L);
        TickStatistics.record(TickStatisticsTest.SECOND, 30L);
        Assertions.assertThat(copy).doesNotContainKey(TickStatisticsTest.SECOND);
        Assertions.assertThat(copy)
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum)
                .containsExactly(1L, 10L);
    }
    
    @Test
    public void ignoresChangesToACopy() {
        TickStatistics.setEnabled(true);
        TickStatistics.record(TickStatisticsTest.FIRST, 10L);
        TickStatistics.getAll().values().forEach(statistics -> statistics.accept(1_000L));
        Assertions.assertThat(TickStatistics.getAll())
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum, LongSummaryStatistics::getMax)
                .containsExactly(1L, 10L, 10L);
    }
    
    @Test
    public void losesNoTickFromTwoThreads() throws InterruptedException {
        TickStatistics.setEnabled(true);
        Phaser start = new Phaser(2);
        Runnable ticker = () -> {
            start.arriveAndAwaitAdvance();
            for (int i = 0; i < TickStatisticsTest.TICKS_PER_THREAD; i++) {
                TickStatistics.record(TickStatisticsTest.FIRST, 1L);
            }
        };
        
        Thread first = Thread.ofPlatform().start(ticker);
        Thread second = Thread.ofPlatform().start(ticker);
        first.join();
        second.join();
        Assertions.assertThat(TickStatistics.getAll())
                .extractingByKey(TickStatisticsTest.FIRST)
                .extracting(LongSummaryStatistics::getCount, LongSummaryStatistics::getSum)
                .containsExactly(2L * TickStatisticsTest.TICKS_PER_THREAD, 2L * TickStatisticsTest.TICKS_PER_THREAD);
    }
}
