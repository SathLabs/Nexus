package dev.satherov.nexus.api.registry.effect;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

import java.util.function.Supplier;

///
/// A builder of one mob effect.
///
/// @param <EFFECT> The type of the mob effect.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class EffectBuilder<EFFECT extends MobEffect, PARENT> extends EntryBuilder<MobEffect, EFFECT, EffectEntry<EFFECT>, PARENT, EffectBuilder<EFFECT, PARENT>> {
    
    ///
    /// The English name of the mob effect.
    ///
    private final String english;
    
    ///
    /// The factory of the mob effect.
    ///
    private final Supplier<EFFECT> factory;
    
    ///
    /// Starts the mob effect `<modid>:<name>` of the given factory, under `effect.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the mob effect, called once inside the registry event.
    ///
    public EffectBuilder(Registrations registrations, String name, String english, PARENT parent, Supplier<EFFECT> factory) {
        super(registrations, Registries.MOB_EFFECT, name, parent);
        this.english = english;
        this.factory = factory;
    }
    
    ///
    /// Creates the mob effect with the factory.
    ///
    /// @return The created mob effect.
    ///
    @Override
    protected EFFECT create() {
        return this.factory.get();
    }
    
    ///
    /// Creates the entry for the given key under `effect.<modid>.<name>`.
    ///
    /// @param key The key of the mob effect.
    ///
    /// @return The created entry.
    ///
    @Override
    protected EffectEntry<EFFECT> createEntry(ResourceKey<MobEffect> key) {
        return new EffectEntry<>(key, this, this.define("effect", this.english));
    }
}
