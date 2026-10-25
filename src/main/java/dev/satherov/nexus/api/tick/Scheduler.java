package dev.satherov.nexus.api.tick;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.tick.budget.Budget;
import dev.satherov.nexus.api.tick.ticker.Tickable;
import dev.satherov.nexus.internal.tick.Bindings;
import dev.satherov.nexus.internal.tick.Ticks;
import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

///
/// All tick-scopes and the budgets of one mod.
///
/// Every ticker will be called on the thread of its respected creation side.
///
/// Can be accessed through [NexusMod#getScheduler()].
///
public final class Scheduler {
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The game bus listeners of the scheduler, which is also holding its scopes.
    ///
    private final Ticks ticks;
    
    ///
    /// The names of all budgets created so far.
    ///
    private final Set<String> budgetNames = new HashSet<>();
    
    ///
    /// The client listeners of the scheduler, holding its client scope, or `null` if [#client()] was never called.
    ///
    private @Nullable ClientTicks clientTicks;
    
    ///
    /// Should only ever be called from [NexusMod#getScheduler()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Scheduler(NexusMod mod) {
        this.mod = mod;
        this.ticks = new Ticks();
    }
    
    ///
    /// The scope of the given level, on the level's logical side.
    ///
    /// The scope will be recreated if the level is unloaded.
    ///
    /// @param level The level.
    ///
    /// @return The scope of the level.
    ///
    /// @throws IllegalStateException If the level is not one that the running server or the client currently holds.
    ///
    public TickScope in(Level level) {
        if (!Ticks.isHeld(level)) {
            throw new IllegalStateException("Level '" + level.dimension().identifier() + "' is not held by the running server or the client");
        }
        
        return this.ticks.getLevelScope(level, () -> new TickScope(this, level));
    }
    
    ///
    /// The scope of the running server, created on the first call.
    ///
    /// @return The scope of the running server.
    ///
    /// @throws IllegalStateException If no server is running.
    ///
    public TickScope server() {
        if (Ticks.getRunningServer() == null) {
            throw new IllegalStateException("No server is running");
        }
        
        return this.ticks.getServerScope(() -> new TickScope(this, LogicalSide.SERVER));
    }
    
    ///
    /// The scope of the client's world session, created on the first call and ends when the client logs out.
    ///
    /// @return The scope of the client's world session.
    ///
    /// @throws IllegalStateException If called on a dedicated server or while the client is not in a world.
    ///
    public TickScope client() {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("A dedicated server has no client");
        }
        
        if (this.clientTicks == null) {
            this.clientTicks = new ClientTicks(this);
        }
        
        return this.clientTicks.getScope();
    }
    
    ///
    /// Wakes up the ticker of the given block entity that this ticker owns, if any.
    ///
    /// Does nothing if this mod didn't bind a ticker to this block entity.
    ///
    /// @param entity The block entity.
    ///
    /// @see TickScope#ticker(BlockEntity, Tickable)
    ///
    public void wake(BlockEntity entity) {
        Ticker ticker = Bindings.find(entity, this);
        if (ticker != null) {
            ticker.wake();
        }
    }
    
    ///
    /// Creates a new budget under the name `<modid>:<name>`.
    ///
    /// @param name    The name of the budget, without the mod id.
    /// @param minimum The time spent on every pass, in microseconds.
    /// @param maximum The time spent on a pass while the tick has time left, in microseconds.
    ///
    /// @return The created budget.
    ///
    /// @throws IllegalArgumentException If the name is already taken on this scheduler, the declared time is outside {@value Budget#SMALLEST} or {@value Budget#LARGEST} microseconds,
    /// or the minimum is greater than the maximum.
    ///
    public Budget budget(String name, @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int minimum, @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int maximum) {
        String modId = this.mod.getModId();
        //noinspection ConstantValue Dev stupidity will probably make that happen anyways, so best to guard it :3
        if (minimum < Budget.SMALLEST || maximum > Budget.LARGEST) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' has a minimum '" + minimum + "' smaller than '" + Budget.SMALLEST + "' or maximum '" + maximum + "' greater than '" + Budget.LARGEST + "' microseconds");
        }
        
        if (minimum > maximum) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' has a minimum '" + minimum + "' greater than its maximum '" + maximum + "'");
        }
        
        if (!this.budgetNames.add(name)) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' already exists");
        }
        
        return new Budget(this, name, minimum, maximum);
    }
}
