package dev.satherov.nexus.api.registry.effect;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;

///
/// An entry of one mob effect that an [EffectBuilder] registered.
///
/// @param <EFFECT> The type of the mob effect.
///
public final class EffectEntry<EFFECT extends MobEffect> extends RegistryEntry<MobEffect, EFFECT> implements Translatable {
    
    ///
    /// The `effect.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [EffectBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the mob effect.
    /// @param builder     The builder that registers the mob effect.
    /// @param translation The `effect.<modid>.<name>` translation.
    ///
    @ApiStatus.Internal
    public EffectEntry(ResourceKey<MobEffect> key, EffectBuilder<EFFECT, ?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// Creates an instance of this effect with the given duration and amplifier.
    ///
    /// @param duration  The duration in ticks, or [MobEffectInstance#INFINITE_DURATION] for an infinite one.
    /// @param amplifier The amplifier.
    ///
    /// @return The created instance.
    ///
    public MobEffectInstance instance(int duration, int amplifier) {
        return new MobEffectInstance(this, duration, amplifier);
    }
    
    ///
    /// Adds an instance of this effect with the given duration and amplifier to the given entity.
    ///
    /// @param entity    The entity.
    /// @param duration  The duration in ticks, or [MobEffectInstance#INFINITE_DURATION] for an infinite one.
    /// @param amplifier The amplifier.
    ///
    /// @return `true` if the instance was added or changed the instance the entity already had.
    ///
    @CanIgnoreReturnValue
    public boolean apply(LivingEntity entity, int duration, int amplifier) {
        return entity.addEffect(this.instance(duration, amplifier));
    }
    
    ///
    /// @param entity The entity.
    ///
    /// @return `true` if the given entity has this effect.
    ///
    public boolean has(LivingEntity entity) {
        return entity.hasEffect(this);
    }
    
    ///
    /// The `effect.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
