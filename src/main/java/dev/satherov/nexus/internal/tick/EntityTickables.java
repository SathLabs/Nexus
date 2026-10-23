package dev.satherov.nexus.internal.tick;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.ticker.EntityTickable;
import dev.satherov.nexus.mixin.tick.LevelMixin;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

///
/// The tickables that the block entity builders declared, by block entity type.
///
/// @see LevelMixin
///
@UtilityClass
@ApiStatus.Internal
public class EntityTickables {
    
    ///
    /// The declared tickables by their block entity type.
    ///
    private static final Map<BlockEntityType<?>, Declaration<?>> DECLARATIONS_BY_TYPE = new HashMap<>();
    
    ///
    /// Records the tickables of the given block entity type.
    ///
    /// @param type      The block entity type.
    /// @param scheduler The scheduler of the mod that declared the tickables.
    /// @param server    The tickable on the server, or `null` if the block entity has no ticker there.
    /// @param client    The tickable on the client, or `null` if the block entity has no ticker there.
    ///
    public static <ENTITY extends BlockEntity> void register(
            BlockEntityType<ENTITY> type,
            Scheduler scheduler,
            @Nullable EntityTickable<ENTITY> server,
            @Nullable ClientOnly<EntityTickable<ENTITY>> client
    ) {
        EntityTickables.DECLARATIONS_BY_TYPE.put(type, new Declaration<>(scheduler, server, client));
    }
    
    ///
    /// Binds the tickable declared for the type of the given block entity and the side of its level to the block entity, if there is one.
    ///
    /// Does nothing if the level of the block entity is not one that the running server or the client currently holds.
    ///
    /// @param entity The loaded block entity.
    ///
    public static void bind(BlockEntity entity) {
        Declaration<?> declaration = EntityTickables.DECLARATIONS_BY_TYPE.get(entity.getType());
        Level level = entity.getLevel();
        if (declaration != null && level != null && Ticks.isHeld(level)) {
            declaration.bind(entity, level);
        }
    }
    
    ///
    /// The tickables of one block entity type.
    ///
    /// @param scheduler The scheduler of the mod that declared the tickables.
    /// @param server    The tickable on the server, or `null` if the block entity has no ticker there.
    /// @param client    The tickable on the client, or `null` if the block entity has no ticker there.
    /// @param <ENTITY>  The class of the block entity.
    ///
    private record Declaration<ENTITY extends BlockEntity>(Scheduler scheduler, @Nullable EntityTickable<ENTITY> server, @Nullable ClientOnly<EntityTickable<ENTITY>> client) {
        
        ///
        /// Binds the tickable of the given level's side to the given block entity in that level, if there is one and the scheduler didn't bind a ticker to the block entity yet.
        ///
        private void bind(BlockEntity entity, Level level) {
            EntityTickable<ENTITY> tickable = level.isClientSide() ? ObjectMapping.map(this.client, ClientOnly::get) : this.server;
            // A block entity that is set into its level again loads a second time without being removed.
            if (tickable == null || Bindings.find(entity, this.scheduler) != null) {
                return;
            }
            
            //noinspection unchecked The block entity is of the type that the tickable was declared for.
            ENTITY typed = (ENTITY) entity;
            this.scheduler.in(level).ticker(entity, elapsed -> tickable.tick(typed, elapsed));
        }
    }
}
