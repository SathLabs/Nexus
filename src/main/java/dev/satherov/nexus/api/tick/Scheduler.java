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
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

///
/// The tick scopes and budgets of one mod.
///
/// Every method runs on the thread of the side it names.
///
/// Can be reached through [NexusMod#getScheduler()].
///
public final class Scheduler {
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The game bus listeners of the scheduler, which hold its scopes.
    ///
    private final Ticks ticks;
    
    ///
    /// The names of the budgets created so far, without the mod id.
    ///
    private final Set<String> budgetNames = new HashSet<>();
    
    ///
    /// The client listeners of the scheduler, which hold its client scope, or `null` if [#client()] was never called.
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
    /// The scope of the given level, on the level's side.
    ///
    /// The scope stays the same object while the level is loaded.
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
    /// The scope of the running server, created on first call.
    ///
    /// @return The scope of the running server.
    ///
    /// @throws IllegalStateException If no server is running.
    ///
    public TickScope server() {
        if (Ticks.getRunningServer() == null) throw new IllegalStateException("No server is running");
        return this.ticks.getServerScope(() -> new TickScope(this, LogicalSide.SERVER));
    }
    
    ///
    /// The scope of the client's world session, created on first call and ending on logout.
    ///
    /// @return The scope of the client's world session.
    ///
    /// @throws IllegalStateException If called on a dedicated server or while the client is not in a world.
    ///
    public TickScope client() {
        if (!FMLEnvironment.getDist().isClient()) throw new IllegalStateException("A dedicated server has no client");
        if (this.clientTicks == null) {
            this.clientTicks = new ClientTicks(this);
        }
        
        return this.clientTicks.getScope();
    }
    
    ///
    /// Ticks the ticker that this mod bound to the given block entity on the next pass of its level at the latest, whatever it waits for.
    ///
    /// Does nothing if this mod didn't bind a ticker to the block entity.
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
    /// @param minimum The time spent on every pass.
    /// @param maximum The time spent on a pass while the tick has time left.
    ///
    /// @return The created budget.
    ///
    /// @throws IllegalArgumentException If the name is already taken on this scheduler, a duration is negative, or the minimum exceeds the maximum.
    ///
    public Budget budget(String name, Duration minimum, Duration maximum) {
        String modId = this.mod.getModId();
        if (minimum.isNegative() || maximum.isNegative()) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' has the negative minimum '" + minimum + "' or maximum '" + maximum + "'");
        }
        
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' has the minimum '" + minimum + "' above its maximum '" + maximum + "'");
        }
        
        if (!this.budgetNames.add(name)) {
            throw new IllegalArgumentException("Budget '" + name + "' of '" + modId + "' already exists");
        }
        
        return new Budget(this, name, minimum, maximum);
    }
}
