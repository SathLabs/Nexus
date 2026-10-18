package dev.satherov.nexus.api.registry.particle;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.IEventBus;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

///
/// A builder of one particle type.
///
/// @param <OPTIONS> The type of the options of the particles.
/// @param <TYPE>    The type of the particle type.
/// @param <PARENT>  The type of what [#end()] returns.
///
public final class ParticleBuilder
        // @formatter:off
        <
            OPTIONS extends ParticleOptions,
            TYPE extends ParticleType<OPTIONS>,
            PARENT
        >
        extends EntryBuilder
        <
            ParticleType<?>,
            TYPE,
            ParticleEntry<OPTIONS, TYPE>,
            PARENT,
            ParticleBuilder<OPTIONS, TYPE, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The factory of the particle type.
    ///
    private final Supplier<TYPE> factory;
    
    ///
    /// The registration of the provider, called on the client with the mod bus and the entry,
    /// or `null` if neither [#sprites(ClientOnly)] nor [#provider(ClientOnly)] was called.
    ///
    private @Nullable BiConsumer<IEventBus, ParticleEntry<OPTIONS, TYPE>> provider;
    
    ///
    /// Starts the particle type `<modid>:<name>` of the given factory.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The type has no translation.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the particle type, called once inside the registry event.
    ///
    public ParticleBuilder(Registrations registrations, String name, PARENT parent, Supplier<TYPE> factory) {
        super(registrations, Registries.PARTICLE_TYPE, name, parent);
        this.factory = factory;
    }
    
    ///
    /// Registers the provider of the given factory for the particles of the type on the client, created with the
    /// sprite set of the type.
    ///
    /// Replaces previous calls of this and of [#provider(ClientOnly)].
    ///
    /// @param provider The factory of the provider, called with the sprite set of the type.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public ParticleBuilder<OPTIONS, TYPE, PARENT> sprites(ClientOnly<Function<SpriteSet, ParticleProvider<OPTIONS>>> provider) {
        this.checkUnregistered();
        this.provider = (bus, entry) -> ClientDispatch.particleSprites(bus, entry, provider);
        return this;
    }
    
    ///
    /// Registers the given provider for the particles of the type on the client.
    ///
    /// Replaces previous calls of this and of [#sprites(ClientOnly)].
    ///
    /// Should be used for particles that have no own sprite set.
    ///
    /// @param provider The supplier of the provider.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public ParticleBuilder<OPTIONS, TYPE, PARENT> provider(ClientOnly<Supplier<ParticleProvider<OPTIONS>>> provider) {
        this.checkUnregistered();
        this.provider = (bus, entry) -> ClientDispatch.particleProvider(bus, entry, provider);
        return this;
    }
    
    ///
    /// Creates the type with the factory.
    ///
    /// @return The created type.
    ///
    @Override
    protected TYPE create() {
        return this.factory.get();
    }
    
    ///
    /// Creates the entry for the given key and adds the provider as a client hook if [#sprites(ClientOnly)]
    /// or [#provider(ClientOnly)] was called.
    ///
    /// @param key The key of the particle type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected ParticleEntry<OPTIONS, TYPE> createEntry(ResourceKey<ParticleType<?>> key) {
        ParticleEntry<OPTIONS, TYPE> entry = new ParticleEntry<>(key, this);
        BiConsumer<IEventBus, ParticleEntry<OPTIONS, TYPE>> provider = this.provider;
        if (provider != null) {
            this.addClientHook(bus -> provider.accept(bus, entry));
        }
        
        return entry;
    }
}
