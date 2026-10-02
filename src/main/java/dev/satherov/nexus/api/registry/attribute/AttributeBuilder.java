package dev.satherov.nexus.api.registry.attribute;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.entity.EntityEntry;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

///
/// A builder of one ranged entity attribute.
///
/// @param <PARENT> The type of what [#end()] returns.
///
public final class AttributeBuilder<PARENT> extends EntryBuilder<Attribute, RangedAttribute, AttributeEntry, PARENT, AttributeBuilder<PARENT>> {
    
    ///
    /// The `attribute.name.<modid>.<name>` translation, declared on the first call.
    ///
    private final Supplier<Translation> translation;
    
    ///
    /// The default value.
    ///
    private final double initial;
    
    ///
    /// The minimum value.
    ///
    private final double min;
    
    ///
    /// The maximum value.
    ///
    private final double max;
    
    ///
    /// The suppliers of the entity types that get the attribute, in call order.
    ///
    private final List<Supplier<? extends EntityType<? extends LivingEntity>>> types = new ArrayList<>();
    
    ///
    /// If the attribute is synced to the clients.
    ///
    private boolean synced;
    
    ///
    /// The sentiment of the attribute.
    ///
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;
    
    ///
    /// Starts the ranged attribute `<modid>:<name>` under `attribute.name.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The attribute will have the translation key as its description id.
    ///
    /// If the minimum value is above the maximum value, or the default value isn't between them, creating the attribute
    /// inside the registry event will throw an [IllegalArgumentException].
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param initial       The default value.
    /// @param min           The minimum value.
    /// @param max           The maximum value.
    ///
    public AttributeBuilder(Registrations registrations, String name, String english, PARENT parent, double initial, double min, double max) {
        super(registrations, Registries.ATTRIBUTE, name, parent);
        this.initial = initial;
        this.min = min;
        this.max = max;
        
        this.translation = Suppliers.memoize(() -> this.define("attribute.name", english));
    }
    
    ///
    /// Syncs the value of the attribute on each entity to the clients.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttributeBuilder<PARENT> synced() {
        this.checkUnregistered();
        this.synced = true;
        return this;
    }
    
    ///
    /// Sets if a rise of the attribute is good, bad, or neither for the entity.
    ///
    /// Defaults to [Attribute.Sentiment#POSITIVE].
    ///
    /// @param sentiment The sentiment of the attribute.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttributeBuilder<PARENT> sentiment(Attribute.Sentiment sentiment) {
        this.checkUnregistered();
        this.sentiment = sentiment;
        return this;
    }
    
    ///
    /// Adds the attribute with its default value to every entity of the given types.
    /// Stacks with previous calls, in the order they were written.
    ///
    /// A type may be given as the [EntityEntry] of a type of living entities, or as `() -> EntityType.ZOMBIE`.
    ///
    /// @param types The suppliers of the entity types, called once when the attributes of the entity types are modified.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @SafeVarargs
    @CanIgnoreReturnValue
    public final AttributeBuilder<PARENT> on(Supplier<? extends EntityType<? extends LivingEntity>>... types) {
        this.checkUnregistered();
        Collections.addAll(this.types, types);
        return this;
    }
    
    ///
    /// Creates the ranged attribute with the translation key as its description id, synced and with the sentiment.
    ///
    /// @return The created attribute.
    ///
    @Override
    protected RangedAttribute create() {
        RangedAttribute attribute = new RangedAttribute(this.translation.get().getKey(), this.initial, this.min, this.max);
        attribute.setSyncable(this.synced).setSentiment(this.sentiment);
        return attribute;
    }
    
    ///
    /// Creates the entry and declares its name `attribute.name.<modid>.<name>`, and adds a listener to the event bus of
    /// the mod that adds the attribute to the entity types of [#on(Supplier...)].
    ///
    /// @param key The key of the attribute.
    ///
    /// @return The created entry.
    ///
    @Override
    protected AttributeEntry createEntry(ResourceKey<Attribute> key) {
        AttributeEntry entry = new AttributeEntry(key, this, this.translation.get());
        this.getRegistrations().getMod().getEventBus().addListener(EntityAttributeModificationEvent.class, event -> this.types.forEach(type -> event.add(type.get(), entry)));
        return entry;
    }
}
