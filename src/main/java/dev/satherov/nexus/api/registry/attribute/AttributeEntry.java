package dev.satherov.nexus.api.registry.attribute;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// ATTRIBUTE holder of one entity attribute that a builder registered, bound once the attribute registry event has run.
///
/// @param <ATTRIBUTE> The type of the attribute.
///
public final class AttributeEntry<ATTRIBUTE extends Attribute> extends RegistryEntry<Attribute, ATTRIBUTE> implements Translatable {
    
    ///
    /// The `attribute.name.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// Should only ever be called from [AttributeBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the attribute.
    /// @param builder     The builder that registers the attribute.
    /// @param translation The `attribute.name.<modid>.<name>` translation.
    ///
    @ApiStatus.Internal
    public AttributeEntry(ResourceKey<Attribute> key, AttributeBuilder<ATTRIBUTE, ?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// The current value of the attribute on the given entity, or the default value if the entity doesn't have the
    /// attribute.
    ///
    /// @param entity The entity.
    ///
    /// @return The current value, or the default value if the entity doesn't have the attribute.
    ///
    public double get(LivingEntity entity) {
        AttributeInstance instance = this.instance(entity);
        return instance == null ? this.value().getDefaultValue() : instance.getValue();
    }
    
    ///
    /// The instance of the attribute on the given entity, or `null` if the entity doesn't have the attribute.
    ///
    /// @param entity The entity.
    ///
    /// @return The instance of the attribute, or `null` if the entity doesn't have the attribute.
    ///
    public @Nullable AttributeInstance instance(LivingEntity entity) {
        return entity.getAttribute(this);
    }
    
    ///
    /// The `attribute.name.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
