package dev.satherov.nexus.api.registry.particle;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;

///
/// An entry of one particle type that a [ParticleBuilder] registered.
///
/// @param <OPTIONS> The type of the options of the particles.
/// @param <TYPE>    The type of the particle type.
///
public final class ParticleEntry<OPTIONS extends ParticleOptions, TYPE extends ParticleType<OPTIONS>> extends RegistryEntry<ParticleType<?>, TYPE> {

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
    /// Sends the given count of particles with the given options at the given position to every player in the level.
    ///
    /// The particles will be scattered around the position with the spread as the standard deviation on each axis.
    /// They will move in random directions with the speed as the standard deviation.
    ///
    /// If the count is `0`, one particle will be sent at the exact position, moving with the spread times the speed as its velocity.
    ///
    /// @param level      The level of the particles.
    /// @param options    The options of the particles.
    /// @param visibility The visibility of the particles.
    /// @param pos        The position of the particles.
    /// @param count      The number of particles.
    /// @param spread     The spread of the particles on each axis.
    /// @param speed      The speed of the particles.
    ///
    /// @return The number of players the particles were sent to.
    ///
    @CanIgnoreReturnValue
    public int spawn(ServerLevel level, OPTIONS options, Visibility visibility, Vec3 pos, int count, Vec3 spread, double speed) {
        return level.sendParticles(
                options,
                visibility.overridesLimiter(options.getType()),
                visibility == Visibility.ALWAYS_SHOW,
                pos.x(),
                pos.y(),
                pos.z(),
                count,
                spread.x(),
                spread.y(),
                spread.z(),
                speed
        );
    }

    ///
    /// Sends the given count of particles with the given options at the given position to every player in the level.
    ///
    /// The particles will be scattered around the position with the spread as the standard deviation on each axis.
    /// They will move in random directions with the speed as the standard deviation.
    ///
    /// If the count is `0`, one particle will be sent at the exact position, moving with the spread times the speed as its velocity.
    ///
    /// @param level   The level of the particles.
    /// @param options The options of the particles.
    /// @param pos     The position of the particles.
    /// @param count   The number of particles.
    /// @param spread  The spread of the particles on each axis.
    /// @param speed   The speed of the particles.
    ///
    /// @return The number of players the particles were sent to.
    ///
    @CanIgnoreReturnValue
    public int spawn(ServerLevel level, OPTIONS options, Vec3 pos, int count, Vec3 spread, double speed) {
        return this.spawn(level, options, Visibility.DEFAULT, pos, count, spread, speed);
    }

    ///
    /// Sends the given count of particles with the given options at the given position to every player in the level.
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
    /// @return The number of players the particles were sent to.
    ///
    @CanIgnoreReturnValue
    public int spawn(ServerLevel level, OPTIONS options, Vec3 pos, int count) {
        return this.spawn(level, options, Visibility.DEFAULT, pos, count, Vec3.ZERO, 0.0D);
    }

    ///
    /// Sends the given count of particles with the given options at the given position to the given player.
    ///
    /// The particles will be scattered around the position with the spread as the standard deviation on each axis.
    /// They will move in random directions with the speed as the standard deviation.
    ///
    /// If the count is `0`, one particle will be sent at the exact position, moving with the spread times the speed as its velocity.
    ///
    /// @param player     The player to send the particles to.
    /// @param options    The options of the particles.
    /// @param visibility The visibility of the particles.
    /// @param pos        The position of the particles.
    /// @param count      The number of particles.
    /// @param spread     The spread of the particles on each axis.
    /// @param speed      The speed of the particles.
    ///
    /// @return `true` if the player was close enough to the position to be sent the particles.
    ///
    @CanIgnoreReturnValue
    public boolean spawn(ServerPlayer player, OPTIONS options, Visibility visibility, Vec3 pos, int count, Vec3 spread, double speed) {
        return player.level().sendParticles(
                player,
                options,
                visibility.overridesLimiter(options.getType()),
                visibility == Visibility.ALWAYS_SHOW,
                pos.x(),
                pos.y(),
                pos.z(),
                count,
                spread.x(),
                spread.y(),
                spread.z(),
                speed
        );
    }

    ///
    /// Sends the given count of particles with the given options at the given position to the given player.
    ///
    /// The particles will be scattered around the position with the spread as the standard deviation on each axis.
    /// They will move in random directions with the speed as the standard deviation.
    ///
    /// If the count is `0`, one particle will be sent at the exact position, moving with the spread times the speed as its velocity.
    ///
    /// @param player  The player to send the particles to.
    /// @param options The options of the particles.
    /// @param pos     The position of the particles.
    /// @param count   The number of particles.
    /// @param spread  The spread of the particles on each axis.
    /// @param speed   The speed of the particles.
    ///
    /// @return `true` if the player was close enough to the position to be sent the particles.
    ///
    @CanIgnoreReturnValue
    public boolean spawn(ServerPlayer player, OPTIONS options, Vec3 pos, int count, Vec3 spread, double speed) {
        return this.spawn(player, options, Visibility.DEFAULT, pos, count, spread, speed);
    }

    ///
    /// Sends the given count of particles with the given options at the given position to the given player.
    ///
    /// The particles will have no spread and no speed.
    ///
    /// If the count is `0`, one particle will still be sent.
    ///
    /// @param player  The player to send the particles to.
    /// @param options The options of the particles.
    /// @param pos     The position of the particles.
    /// @param count   The number of particles.
    ///
    /// @return `true` if the player was close enough to the position to be sent the particles.
    ///
    @CanIgnoreReturnValue
    public boolean spawn(ServerPlayer player, OPTIONS options, Vec3 pos, int count) {
        return this.spawn(player, options, Visibility.DEFAULT, pos, count, Vec3.ZERO, 0.0D);
    }

    ///
    /// @param options The particle options.
    ///
    /// @return `true` if the given options are of this type.
    ///
    public boolean is(ParticleOptions options) {
        return options.getType() == this.value();
    }

    ///
    /// A visibility of particles that decides how far they are sent and on which particle settings they are shown.
    ///
    public enum Visibility {
        ///
        /// The visibility of particles that are sent within 32 blocks and hidden on the minimal particle setting.
        ///
        /// If the type of the options overrides the limiter, the particles will be shown as with [#OVERRIDE_LIMITER].
        ///
        DEFAULT,

        ///
        /// The visibility of particles that are sent within 32 blocks and shown at about one in fifteen on the minimal particle setting.
        ///
        /// If the type of the options overrides the limiter, the particles will be shown as with [#OVERRIDE_LIMITER].
        ///
        ALWAYS_SHOW,

        ///
        /// The visibility of particles that are sent within 512 blocks and shown at any distance on every particle setting.
        ///
        OVERRIDE_LIMITER;

        ///
        /// @param type The type of the particles.
        ///
        /// @return `true` if the particles of the given type are sent and shown as with [#OVERRIDE_LIMITER].
        ///
        private boolean overridesLimiter(ParticleType<?> type) {
            return this == Visibility.OVERRIDE_LIMITER || type.getOverrideLimiter();
        }
    }
}
