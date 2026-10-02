package dev.satherov.nexus.api.registry.sound;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;

///
/// A holder of one sound event that a builder registered, bound once the sound event registry event has run.
///
public final class SoundEntry extends RegistryEntry<SoundEvent, SoundEvent> implements Translatable {
    
    ///
    /// The `subtitles.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [SoundBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the sound event.
    /// @param builder     The builder that registers the sound event.
    /// @param translation The `subtitles.<modid>.<name>` translation.
    ///
    @ApiStatus.Internal
    public SoundEntry(ResourceKey<SoundEvent> key, SoundBuilder<?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// Plays the sound at the center of the given block with volume and pitch `1`, for every player in range.
    ///
    /// Does nothing on the client.
    ///
    /// @param level  The level to play the sound in.
    /// @param pos    The position of the block to play the sound at.
    /// @param source The source of the sound.
    ///
    public void play(Level level, BlockPos pos, SoundSource source) {
        this.play(level, pos, source, 1.0F, 1.0F);
    }
    
    ///
    /// Plays the sound at the center of the given block, for every player in range.
    ///
    /// Does nothing on the client.
    ///
    /// @param level  The level to play the sound in.
    /// @param pos    The position of the block to play the sound at.
    /// @param source The source of the sound.
    /// @param volume The volume of the sound.
    /// @param pitch  The pitch of the sound, where `1` is the pitch of the sound file.
    ///
    public void play(Level level, BlockPos pos, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos, this.value(), source, volume, pitch);
    }
    
    ///
    /// Plays the sound at the given entity, for every player in range.
    ///
    /// The sound will follow the entity as it moves.
    ///
    /// Does nothing on the client.
    ///
    /// @param entity The entity to play the sound at.
    /// @param source The source of the sound.
    /// @param volume The volume of the sound.
    /// @param pitch  The pitch of the sound, where `1` is the pitch of the sound file.
    ///
    public void play(Entity entity, SoundSource source, float volume, float pitch) {
        entity.level().playSound(null, entity, this.value(), source, volume, pitch);
    }
    
    ///
    /// The `subtitles.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
