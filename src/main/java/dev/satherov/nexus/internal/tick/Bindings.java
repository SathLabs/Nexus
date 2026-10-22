package dev.satherov.nexus.internal.tick;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.Ticker;
import dev.satherov.nexus.mixin.tick.BlockEntityMixin;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

///
/// The tickers bound to block entities, by level and block entity, across all mods.
///
/// The entries of a level are touched by its own thread only.
/// The map of levels is safe from both threads of the integrated server.
///
/// @see BlockEntityMixin
///
@UtilityClass
@ApiStatus.Internal
public class Bindings {
    
    ///
    /// The bound tickers by the scheduler that bound them, by their block entity, by its level.
    ///
    private static final Map<Level, Map<BlockEntity, Map<Scheduler, Ticker>>> TICKERS_BY_LEVEL = new ConcurrentHashMap<>();
    
    ///
    /// The bound tickers by block entity in the level of the given block entity, or `null` if it has no level or nothing was bound there.
    ///
    private static @Nullable Map<BlockEntity, Map<Scheduler, Ticker>> getTickersByEntity(BlockEntity entity) {
        return ObjectMapping.map(entity.getLevel(), Bindings.TICKERS_BY_LEVEL::get);
    }
    
    ///
    /// Records the ticker as bound to the block entity by the scheduler.
    ///
    /// @param entity    The block entity, which is in a level.
    /// @param scheduler The scheduler that bound the ticker.
    /// @param ticker    The bound ticker.
    ///
    public static void add(BlockEntity entity, Scheduler scheduler, Ticker ticker) {
        //noinspection DataFlowIssue The scope only binds block entities in its level.
        Bindings.TICKERS_BY_LEVEL
                .computeIfAbsent(entity.getLevel(), _ -> new HashMap<>())
                .computeIfAbsent(entity, _ -> new HashMap<>())
                .put(scheduler, ticker);
    }
    
    ///
    /// Gets the active ticker that the scheduler bound to the block entity.
    ///
    /// @param entity    The block entity.
    /// @param scheduler The scheduler.
    ///
    /// @return The ticker, or `null` if the scheduler didn't bind one or it is inactive.
    ///
    public static @Nullable Ticker find(BlockEntity entity, Scheduler scheduler) {
        Map<Scheduler, Ticker> tickers = ObjectMapping.map(Bindings.getTickersByEntity(entity), tickersByEntity -> tickersByEntity.get(entity));
        Ticker ticker = ObjectMapping.map(tickers, tickersByScheduler -> tickersByScheduler.get(scheduler));
        return ObjectMapping.mapIf(ticker, Ticker::isActive, Function.identity());
    }
    
    ///
    /// Cancels and forgets every ticker bound to the block entity.
    ///
    /// @param entity The block entity.
    ///
    public static void drop(BlockEntity entity) {
        Map<Scheduler, Ticker> tickers = ObjectMapping.map(Bindings.getTickersByEntity(entity), tickersByEntity -> tickersByEntity.remove(entity));
        if (tickers != null) {
            tickers.values().forEach(Ticker::cancel);
        }
    }
    
    ///
    /// Forgets every binding of the level.
    ///
    /// Their tickers end with the scope of the level.
    ///
    /// @param level The level.
    ///
    public static void drop(Level level) {
        Bindings.TICKERS_BY_LEVEL.remove(level);
    }
}
