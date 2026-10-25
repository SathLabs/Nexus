package dev.satherov.nexus.api.tick;

import lombok.Getter;

import dev.satherov.nexus.api.tick.ticker.Next;
import dev.satherov.nexus.api.tick.ticker.Tickable;
import dev.satherov.nexus.internal.tick.TickStatistics;

import net.minecraft.CrashReportCategory;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

///
/// One ticker that a scope can tick at whatever rate its return values specify.
///
/// Can be started through [TickScope#ticker(String, Tickable)] and [TickScope#ticker(BlockEntity, Tickable)].
///
public final class Ticker {
    
    ///
    /// The scope that ticks the ticker.
    ///
    private final TickScope scope;
    
    ///
    /// The name that the profiler section, the statistics, and the crash report use.
    ///
    /// Will be `<modid>:<name>`, or the identifier of the block entity type if bound.
    ///
    @Getter
    private final String name;
    
    ///
    /// The block entity that this ticker is bound to, or `null` if it isn't.
    ///
    private final @Nullable BlockEntity entity;
    
    ///
    /// The tick function of the ticker.
    ///
    private final Tickable tickable;
    
    ///
    /// The game tick of the last execution, or the last tick before the first if it never ticked before.
    ///
    private long lastTick;
    
    ///
    /// The number of ticks until a skipped tick is retried, which is the number that the last tick returned, or `1` if none ever did.
    ///
    private int interval = 1;
    
    ///
    /// The next tick that this ticker will run on, or [Long#MAX_VALUE] while it sleeps.
    ///
    long due;
    
    ///
    /// `true` until the ticker was canceled or dropped by its scope.
    ///
    @Getter
    private boolean active = true;
    
    ///
    /// Should only ever be called from [TickScope#ticker(String, Tickable)] and [TickScope#ticker(BlockEntity, Tickable)].
    ///
    /// @param scope    The scope that ticks the ticker.
    /// @param name     The name of the ticker.
    /// @param entity   The block entity the ticker is bound to, or `null` if it is not bound.
    /// @param tickable The tick of the ticker.
    ///
    Ticker(TickScope scope, String name, @Nullable BlockEntity entity, Tickable tickable) {
        this.scope = scope;
        this.name = name;
        this.entity = entity;
        this.tickable = tickable;
        
        this.lastTick = scope.passes;
        this.due = scope.passes + 1;
    }
    
    ///
    /// Ticks the ticker on the current pass if it wasn't called yet or on the next if it already was.
    ///
    /// If called during its own tick, the return value is ignored.
    ///
    /// Does nothing once the ticker is inactive.
    ///
    public void wake() {
        this.due = Math.min(this.due, this.scope.passes + 1);
    }
    
    ///
    /// Drops the ticker.
    ///
    /// If called from inside its own tick, the ticker will be dropped after that tick finishes and the return value ignored.
    ///
    /// Does nothing once the ticker is inactive.
    ///
    public void cancel() {
        this.active = false;
    }
    
    ///
    /// Executes the tickables tick function of the current scope and updates the passes for the next call.
    ///
    /// If the ticker is bound to a block entity, and the level does not tick block entities at its position,
    /// the tick is skipped and retried after [#interval] passes.
    ///
    /// If [TickStatistics#isEnabled()] is `true`, the duration will be written into them.
    ///
    void tick() {
        long pass = this.scope.passes;
        //noinspection DataFlowIssue A bound block entity is always in a level.
        if (this.entity != null && !this.entity.getLevel().shouldTickBlocksAt(this.entity.getBlockPos())) {
            this.due = pass + this.interval;
            return;
        }
        
        int elapsed = Math.clamp(pass - this.lastTick, 1, Integer.MAX_VALUE);
        this.lastTick = pass;
        
        // Since a wake during a tick execution may call the wake-up function, which forces a call on the next tick,
        // we must set due to some larger value, or it would have a smaller number than the current pass.
        this.due = Long.MAX_VALUE;
        boolean recording = TickStatistics.isEnabled();
        long start = recording ? Util.getNanos() : 0L;
        Next next = this.tickable.tick(elapsed);
        if (recording) {
            TickStatistics.record(this.name, Util.getNanos() - start);
        }
        
        long requested = switch (next) {
            case Next.In(int ticks) -> {
                this.interval = ticks;
                yield pass + ticks;
            }
            case Next.Sleep() -> Long.MAX_VALUE;
        };
        this.due = Math.min(this.due, requested);
    }
    
    ///
    /// Adds the name of the ticker to the given crash report category, or the type and position of its block entity if it is bound.
    ///
    void fillCrashReportCategory(CrashReportCategory category) {
        if (this.entity == null) {
            category.setDetail("Name", this.name);
        } else {
            category.setDetail("Block entity type", this.name).setDetail("Position", this.entity.getBlockPos().toShortString());
        }
    }
}
