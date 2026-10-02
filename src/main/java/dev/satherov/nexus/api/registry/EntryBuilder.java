package dev.satherov.nexus.api.registry;

import lombok.Getter;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.datagen.ProviderKind;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.EntryRegister;

import net.neoforged.bus.api.IEventBus;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

///
/// A builder of one object of a registry and the sub-objects that come with it.
///
/// @param <REGISTRY> The type of the objects of the registry.
/// @param <VALUE>    The type of the object.
/// @param <ENTRY>    The type of the entry that [#register()] returns.
/// @param <PARENT>   The type of what [#end()] returns.
/// @param <SELF>     The type of this builder.
///
public abstract class EntryBuilder<REGISTRY, VALUE extends REGISTRY, ENTRY extends RegistryEntry<REGISTRY, VALUE>, PARENT, SELF extends EntryBuilder<REGISTRY, VALUE, ENTRY, PARENT, SELF>> {
    
    ///
    /// The registrations this builder registers through.
    ///
    @Getter
    private final Registrations registrations;
    
    ///
    /// The key the object registers under.
    ///
    @Getter
    private final ResourceKey<REGISTRY> key;
    
    ///
    /// What [#end()] returns.
    ///
    private final PARENT parent;
    
    ///
    /// The tags recorded for datagen, in call order.
    ///
    private final Set<TagKey<REGISTRY>> tags = new LinkedHashSet<>();
    
    ///
    /// The facts recorded for datagen by their kind.
    ///
    private final Map<ProviderKind<?>, Object> factsByKind = new HashMap<>();
    
    ///
    /// The callbacks that run with the object right after it is created, in call order.
    ///
    private final List<Consumer<VALUE>> callbacks = new ArrayList<>();
    
    ///
    /// The code that runs on the client once the object is registered, in call order.
    ///
    private final List<ClientOnly<BiConsumer<IEventBus, ENTRY>>> clientCode = new ArrayList<>();
    
    ///
    /// If [#register()] was called or another builder took over this one.
    ///
    private boolean registered;
    
    ///
    /// The builder that took over this one, or `null` if none did.
    ///
    private @Nullable EntryBuilder<REGISTRY, VALUE, ? extends ENTRY, ?, ?> successor;
    
    ///
    /// Starts the object `<modid>:<name>` in the given registry.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param registry      The key of the registry.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    ///
    protected EntryBuilder(Registrations registrations, ResourceKey<? extends Registry<REGISTRY>> registry, String name, PARENT parent) {
        this.registrations = registrations;
        this.parent = parent;
        
        this.key = registrations.getMod().key(registry, name);
    }
    
    ///
    /// Takes over the object of the given builder, with its key and everything recorded on it so far.
    ///
    /// The given builder will throw on every later call that records something or registers.
    ///
    /// @param from   The builder to take over.
    /// @param parent What [#end()] returns.
    ///
    /// @throws IllegalStateException If the given builder already registered or another builder took it over.
    ///
    protected EntryBuilder(EntryBuilder<REGISTRY, VALUE, ? super ENTRY, ?, ?> from, PARENT parent) {
        from.checkUnregistered();
        this.registrations = from.registrations;
        this.key = from.key;
        this.parent = parent;
        
        this.tags.addAll(from.tags);
        this.factsByKind.putAll(from.factsByKind);
        this.callbacks.addAll(from.callbacks);
        from.clientCode.forEach(code -> this.clientCode.add(() -> code.get()::accept));
        from.registered = true;
        from.successor = this;
    }
    
    ///
    /// The name, the path of the identifier.
    ///
    /// @return The name.
    ///
    public String getName() {
        return this.key.identifier().getPath();
    }
    
    ///
    /// The tags recorded for datagen, in call order.
    ///
    /// @return The recorded tags.
    ///
    public @Unmodifiable Set<TagKey<REGISTRY>> getTags() {
        return Collections.unmodifiableSequencedSet(new LinkedHashSet<>(this.tags));
    }
    
    ///
    /// The fact recorded for datagen under the given kind, or `null` if none was recorded.
    ///
    /// @param kind The kind of the fact.
    ///
    /// @return The recorded fact, or `null` if none was recorded.
    ///
    public <FACT> @Nullable FACT getDatagen(ProviderKind<FACT> kind) {
        //noinspection unchecked A fact is only ever recorded under a kind of its own type.
        return (FACT) this.factsByKind.get(kind);
    }
    
    ///
    /// Records the given tags for datagen.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param tags The tags of the object.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @SafeVarargs
    @CanIgnoreReturnValue
    public final SELF tag(TagKey<REGISTRY>... tags) {
        this.checkUnregistered();
        Collections.addAll(this.tags, tags);
        return this.self();
    }
    
    ///
    /// Runs the given callback with the object right after it is created inside its registry event.
    ///
    /// Called with all previous callbacks, in order, oldest first.
    ///
    /// @param callback The callback, called with the created object.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public SELF onRegister(Consumer<VALUE> callback) {
        this.checkUnregistered();
        this.callbacks.add(callback);
        return this.self();
    }
    
    ///
    /// Records the given fact under the given kind for datagen.
    ///
    /// Replaces previous calls for the same kind.
    ///
    /// @param kind The kind of the fact.
    /// @param fact The fact.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public <FACT> SELF datagen(ProviderKind<FACT> kind, FACT fact) {
        this.checkUnregistered();
        this.factsByKind.put(kind, fact);
        return this.self();
    }
    
    ///
    /// Runs the given code on the client once the object is registered, with the mod bus and the entry.
    /// Stacks with previous calls, in the order they were written.
    ///
    /// Meant to be used for adding a listener for any client event.
    ///
    /// Never runs on a dedicated server.
    ///
    /// @param code The client code, called with the mod bus and the entry.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public SELF client(ClientOnly<BiConsumer<IEventBus, ENTRY>> code) {
        this.checkUnregistered();
        this.clientCode.add(code);
        return this.self();
    }
    
    ///
    /// This builder as its own type.
    ///
    private SELF self() {
        //noinspection unchecked Every builder is the type it declares as its own.
        return (SELF) this;
    }
    
    ///
    /// Registers the object, its sub-objects, and the defaults that no builder was opened for.
    ///
    /// @return The still unbound entry of the object.
    ///
    /// @throws IllegalStateException If [#end()] returns another builder, which registers this one, if this was already called, if another builder took over this one, or if the registry event of the registry already ran.
    ///
    @CanIgnoreReturnValue
    public ENTRY register() {
        if (this.parent instanceof EntryBuilder<?, ?, ?, ?, ?>) {
            throw new IllegalStateException("'" + this.key.identifier() + "' is registered by its parent through end().register()");
        }
        
        return this.registerEntry();
    }
    
    ///
    /// Registers the given sub-object, whose own [#register()] throws.
    ///
    /// If another builder took over the given one, the builder that took over last registers instead, with everything
    /// recorded on it.
    ///
    /// Should only ever be called from [#createEntry(ResourceKey)].
    ///
    /// @param builder The builder of the sub-object.
    ///
    /// @return The still unbound entry of the sub-object.
    ///
    /// @throws IllegalStateException If the sub-object was already registered or the registry event of its registry already ran.
    ///
    @CanIgnoreReturnValue
    protected static <SUB extends RegistryEntry<?, ?>> SUB registerSubObject(EntryBuilder<?, ?, SUB, ?, ?> builder) {
        EntryBuilder<?, ?, ? extends SUB, ?, ?> successor = builder.successor;
        return successor == null ? builder.registerEntry() : EntryBuilder.registerSubObject(successor);
    }
    
    ///
    /// Registers the object and its sub-objects.
    ///
    private ENTRY registerEntry() {
        this.checkUnregistered();
        EntryRegister<REGISTRY> register = (EntryRegister<REGISTRY>) this.registrations.getRegister(this.key.registryKey());
        // Check before creating the entry, or a late call would leave the listeners and sub-objects of the entry behind.
        register.checkUnfired(this.key.identifier());
        ENTRY entry = this.createEntry(this.key);
        register.add(entry, () -> {
            VALUE value = this.create();
            this.callbacks.forEach(callback -> callback.accept(value));
            return value;
        });
        
        this.registered = true;
        this.clientCode.forEach(code -> register.addClientHook(bus -> code.get().accept(bus, entry)));
        return entry;
    }
    
    ///
    /// Throws an [IllegalStateException] if [#register()] was already called or another builder took over this one.
    ///
    /// Meant to be used for the setters of a mod's own builders.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    protected final void checkUnregistered() {
        if (this.registered) {
            throw new IllegalStateException("'" + this.key.identifier() + "' is already registered");
        }
    }
    
    ///
    /// The parent of this builder.
    ///
    /// Nothing will be registered until the outermost [#register()] is called.
    ///
    /// @return The parent.
    ///
    public PARENT end() {
        return this.parent;
    }
    
    ///
    /// Declares the key `<category>.<modid>.<name>` with the given English name in the translations of the mod.
    /// - `<category>` is the given category.
    /// - `<modid>` is the id of the mod.
    /// - `<name>` is the name of this builder.
    ///
    /// @param category The first part of the key.
    /// @param english  The English name.
    ///
    /// @return The declared translation.
    ///
    /// @throws IllegalStateException If the key is already declared.
    ///
    @CanIgnoreReturnValue
    protected Translation define(String category, String english) {
        return this.registrations.getMod().getTranslations().define(category, this.getName(), english);
    }
    
    ///
    /// Creates the object.
    ///
    /// Called once, inside the registry event, after the identifier was set on its properties.
    ///
    /// @return The created object.
    ///
    @ForOverride
    protected abstract VALUE create();
    
    ///
    /// Creates the still unbound entry for the given key and registers the sub-objects and the defaults that no
    /// builder was opened for.
    ///
    /// Should only ever be called from [#register()].
    ///
    /// @param key The key of the object.
    ///
    /// @return The created entry.
    ///
    @ForOverride
    protected abstract ENTRY createEntry(ResourceKey<REGISTRY> key);
}
