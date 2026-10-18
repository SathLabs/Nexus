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

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

///
/// ATTRIBUTE builder of one entity attribute.
///
/// @param <ATTRIBUTE> The type of the attribute.
/// @param <PARENT>    The type of what [#end()] returns.
///
public final class AttributeBuilder<ATTRIBUTE extends Attribute, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            Attribute,
            ATTRIBUTE,
            AttributeEntry<ATTRIBUTE>,
            PARENT,
            AttributeBuilder<ATTRIBUTE, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The `attribute.name.<modid>.<name>` translation, declared on the first call.
    ///
    private final Supplier<Translation> translation;
    
    ///
    /// The factory of the attribute, given its description id.
    ///
    private final Function<String, ATTRIBUTE> factory;
    
    ///
    /// The suppliers of the entity types that get the attribute.
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
    /// Starts the attribute `<modid>:<name>` under `attribute.name.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The attribute will have the translation key as its description id.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the attribute, called with the description id inside the registry event.
    ///
    public AttributeBuilder(Registrations registrations, String name, String english, PARENT parent, Function<String, ATTRIBUTE> factory) {
        super(registrations, Registries.ATTRIBUTE, name, parent);
        this.factory = factory;
        
        this.translation = Suppliers.memoize(() -> this.define("attribute.name", english));
    }
    
    ///
    /// Syncs the value of the attribute on each entity to the client.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttributeBuilder<ATTRIBUTE, PARENT> synced() {
        this.checkUnregistered();
        this.synced = true;
        return this;
    }
    
    ///
    /// Sets if an increased value of the attribute is good, bad, or neutral for the entity.
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
    public AttributeBuilder<ATTRIBUTE, PARENT> sentiment(Attribute.Sentiment sentiment) {
        this.checkUnregistered();
        this.sentiment = sentiment;
        return this;
    }
    
    ///
    /// Adds the attribute with its default value to every entity of the given types.
    ///
    /// ATTRIBUTE type may be given as the [EntityEntry] of a type of living entities, or simply `() -> EntityType.EXAMPLE`.
    ///
    /// @param types The suppliers of the entity types, called once when the attributes of the entity types are modified.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @SafeVarargs
    @CanIgnoreReturnValue
    public final AttributeBuilder<ATTRIBUTE, PARENT> on(Supplier<? extends EntityType<? extends LivingEntity>>... types) {
        this.checkUnregistered();
        Collections.addAll(this.types, types);
        return this;
    }
    
    ///
    /// Creates the attribute with the translation key as its description id, synced and with the sentiment.
    ///
    /// @return The created attribute.
    ///
    @Override
    protected ATTRIBUTE create() {
        ATTRIBUTE attribute = this.factory.apply(this.translation.get().getKey());
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
    protected AttributeEntry<ATTRIBUTE> createEntry(ResourceKey<Attribute> key) {
        AttributeEntry<ATTRIBUTE> entry = new AttributeEntry<>(key, this, this.translation.get());
        this.getRegistrations()
                .getMod()
                .getEventBus()
                .addListener(EntityAttributeModificationEvent.class, event -> this.types.forEach(type -> event.add(type.get(), entry)));
        return entry;
    }
}
