package dev.satherov.nexus.internal.tick;

import lombok.Synchronized;
import lombok.experimental.UtilityClass;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;

///
/// Per-ticker timing by ticker name, off until switched on and safe from both threads of the integrated server.
/// The command feature is its reader.
///
@UtilityClass
@ApiStatus.Internal
public class TickStatistics {
    
    ///
    /// The recorded statistics by the name of their ticker.
    ///
    private static final Map<String, LongSummaryStatistics> STATISTICS_BY_NAME = new HashMap<>();
    
    ///
    /// If ticks are being recorded.
    ///
    private static volatile boolean enabled;
    
    ///
    /// @return `true` if ticks are being recorded.
    ///
    public static boolean isEnabled() {
        return TickStatistics.enabled;
    }
    
    ///
    /// Starts or stops recording.
    /// Stopping keeps what was recorded.
    ///
    /// @param enabled `true` to start recording or `false` to stop it.
    ///
    public static void setEnabled(boolean enabled) {
        TickStatistics.enabled = enabled;
    }
    
    ///
    /// Adds one tick's duration to the ticker's statistics, if enabled.
    ///
    /// @param name  The name of the ticker.
    /// @param nanos The duration of the tick in nanoseconds.
    ///
    @Synchronized
    public static void record(String name, long nanos) {
        if (!TickStatistics.enabled) {
            return;
        }
        
        TickStatistics.STATISTICS_BY_NAME.computeIfAbsent(name, _ -> new LongSummaryStatistics()).accept(nanos);
    }
    
    ///
    /// A copy of every ticker's statistics by name.
    ///
    /// @return The copies of the statistics by the name of their ticker.
    ///
    @Synchronized
    public static Map<String, LongSummaryStatistics> getAll() {
        Map<String, LongSummaryStatistics> copies = new HashMap<>(TickStatistics.STATISTICS_BY_NAME);
        copies.replaceAll((_, statistics) -> new LongSummaryStatistics(statistics.getCount(), statistics.getMin(), statistics.getMax(), statistics.getSum()));
        return copies;
    }
    
    ///
    /// Forgets everything recorded.
    ///
    @Synchronized
    public static void reset() {
        TickStatistics.STATISTICS_BY_NAME.clear();
    }
}
