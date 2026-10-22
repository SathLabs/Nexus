package dev.satherov.nexus.internal.tick;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.ticker.EntityTickable;
import dev.satherov.nexus.mixin.tick.LevelMixin;

import net.minecraft.server.level.ServerLevel;
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
    /// Binds the server tickable declared for the type of the given block entity to it, if there is one.
    ///
    /// Does nothing on a client level.
    ///
    /// @param entity The loaded block entity.
    ///
    public static void bind(BlockEntity entity) {
        Declaration<?> declaration = EntityTickables.DECLARATIONS_BY_TYPE.get(entity.getType());
        // TODO Bind the client tickable on a client level once the client has a tick scope.
        if (declaration != null && entity.getLevel() instanceof ServerLevel level) {
            declaration.bindServer(entity, level);
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
        /// Binds the server tickable to the given block entity in the given level, if there is one and the scheduler didn't bind a ticker to the block entity yet.
        ///
        private void bindServer(BlockEntity entity, ServerLevel level) {
            EntityTickable<ENTITY> tickable = this.server;
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
