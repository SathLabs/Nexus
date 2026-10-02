package dev.satherov.nexus.api.registry.sound;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.function.Function;

///
/// A builder of one sound event.
///
/// @param <PARENT> The type of what [#end()] returns.
///
public final class SoundBuilder<PARENT> extends EntryBuilder<SoundEvent, SoundEvent, SoundEntry, PARENT, SoundBuilder<PARENT>> {
    
    ///
    /// The English subtitle.
    ///
    private final String english;
    
    ///
    /// The factory of the sound event, called with the identifier.
    ///
    private Function<Identifier, SoundEvent> factory = SoundEvent::createVariableRangeEvent;
    
    ///
    /// Starts the sound event `<modid>:<name>` with the subtitle under `subtitles.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English subtitle.
    /// @param parent        What [#end()] returns.
    ///
    public SoundBuilder(Registrations registrations, String name, String english, PARENT parent) {
        super(registrations, Registries.SOUND_EVENT, name, parent);
        this.english = english;
    }
    
    ///
    /// Gives the sound event the given fixed range, which is the same at every volume.
    ///
    /// Defaults to 16 blocks, or 16 times the volume for a volume above `1`.
    ///
    /// @param range The range, in blocks.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public SoundBuilder<PARENT> range(float range) {
        this.checkUnregistered();
        this.factory = id -> SoundEvent.createFixedRangeEvent(id, range);
        return this;
    }
    
    ///
    /// Creates the sound event of the identifier with the range.
    ///
    /// @return The created sound event.
    ///
    @Override
    protected SoundEvent create() {
        return this.factory.apply(this.getKey().identifier());
    }
    
    ///
    /// Creates the entry and declares its subtitle `subtitles.<modid>.<name>`.
    ///
    /// @param key The key of the sound event.
    ///
    /// @return The created entry.
    ///
    @Override
    protected SoundEntry createEntry(ResourceKey<SoundEvent> key) {
        return new SoundEntry(key, this, this.define("subtitles", this.english));
    }
}
