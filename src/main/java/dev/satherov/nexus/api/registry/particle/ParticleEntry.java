package dev.satherov.nexus.api.registry.particle;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

///
/// An entry of one particle type that a [ParticleBuilder] registered.
///
/// @param <OPTIONS> The type of the options of the particles.
/// @param <TYPE>    The type of the particle type.
///
public final class ParticleEntry<OPTIONS extends ParticleOptions, TYPE extends ParticleType<OPTIONS>> extends RegistryEntry<ParticleType<?>, TYPE> {
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [ParticleBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the particle type.
    /// @param builder The builder that registers the particle type.
    ///
    @ApiStatus.Internal
    public ParticleEntry(ResourceKey<ParticleType<?>> key, ParticleBuilder<OPTIONS, TYPE, ?> builder) {
        super(key, builder);
    }
    
    ///
    /// Sends the given count of particles with the given options at the given position to every player in the level
    /// within 32 blocks of it, or within 512 blocks if the type of the options overrides the limiter.
    ///
    /// The particles will have no spread and no speed.
    ///
    /// If the count is `0`, one particle will still be sent.
    ///
    /// @param level   The level of the particles.
    /// @param options The options of the particles.
    /// @param pos     The position of the particles.
    /// @param count   The number of particles.
    ///
    public void spawn(ServerLevel level, OPTIONS options, Vec3 pos, int count) {
        level.sendParticles(options, options.getType().getOverrideLimiter(), false, pos.x(), pos.y(), pos.z(), count, 0.0D, 0.0D, 0.0D, 0.0D);
    }
    
    ///
    /// @param options The particle options.
    ///
    /// @return `true` if the given options are of this type.
    ///
    public boolean is(ParticleOptions options) {
        return options.getType() == this.value();
    }
}
