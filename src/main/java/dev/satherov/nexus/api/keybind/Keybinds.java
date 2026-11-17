package dev.satherov.nexus.api.keybind;

import lombok.Getter;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.event.keybind.KeyReceivedEvent;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.network.Network;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.keybind.HeldKeys;
import dev.satherov.nexus.internal.keybind.KeySignal;
import dev.satherov.nexus.internal.keybind.client.ClientKeys;
import dev.satherov.zelqro.mapping.BooleanMapping;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// The keys and key categories that one mod declares.
///
/// Every key and every category should be declared inside the mod's constructor.
///
/// Can be accessed through [NexusMod#getKeybinds()].
///
public final class Keybinds {
    
    ///
    /// The name of the category a key is listed in unless its builder sets another.
    ///
    private static final String DEFAULT_CATEGORY = "main";
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The identifier `<modid>:main` of the default category.
    ///
    private final Identifier defaultCategory;
    
    ///
    /// Every registered key by the name of its mapping, in the order they were registered.
    ///
    private final Map<String, Keybind> keybindsByName = new LinkedHashMap<>();
    
    ///
    /// Every declared category, in the order they were declared.
    ///
    private final List<Identifier> categories = new ArrayList<>();
    
    ///
    /// The server tick each key went down on, per player.
    ///
    private final HeldKeys heldKeys = new HeldKeys();
    
    ///
    /// If the key mappings were already registered or the payload handlers event already ran.
    ///
    private boolean locked;
    
    ///
    /// Should only ever be called from [NexusMod#getKeybinds()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Keybinds(NexusMod mod) {
        this.mod = mod;
        
        this.defaultCategory = mod.id(Keybinds.DEFAULT_CATEGORY);
        this.locked = Network.hasPayloadEventRun();
        
        mod.getEventBus().addListener(RegisterPayloadHandlersEvent.class, _ -> this.lock());
        if (FMLEnvironment.getDist().isClient()) {
            ClientKeys.attach(this);
        }
    }
    
    ///
    /// The payload name of a key on the mapping of the given name, lowercased and with every character an identifier path refuses replaced by `_`.
    ///
    private static String toPayloadName(String mappingName) {
        return mappingName.toLowerCase(Locale.ROOT)
                .chars()
                .map(c -> Identifier.validPathChar((char) c) ? c : '_')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
    
    ///
    /// Declares the key category `<modid>:<name>` and its translation key `key.category.<modid>.<name>` with the given English default.
    ///
    /// @param name    The path of the identifier of the category.
    /// @param english The English default of the category's name.
    ///
    /// @return The identifier `<modid>:<name>`, to be passed to [KeybindBuilder#category(Identifier)].
    ///
    /// @throws IllegalArgumentException If the name is not a valid identifier path.
    /// @throws IllegalStateException    If the mod already declared the category, if the key mappings were already registered,
    /// or if the payload handlers event already ran.
    ///
    public Identifier category(String name, String english) {
        if (!Identifier.isValidPath(name)) {
            throw new IllegalArgumentException("Category name '" + name + "' of mod '" + this.mod.getModId() + "' is not a valid identifier path");
        }
        
        if (this.locked) {
            throw new IllegalStateException("Category '" + name + "' of mod '" + this.mod.getModId() + "' is declared after the key mappings were registered or the payload handlers event ran");
        }
        
        if (this.categories.contains(this.mod.id(name))) {
            throw new IllegalStateException("Category '" + name + "' of mod '" + this.mod.getModId() + "' is already declared");
        }
        
        return this.declareCategory(name, english);
    }
    
    ///
    /// Starts a new builder of the key `key.<modid>.<name>` with the given English default.
    ///
    /// The key starts out:
    /// - Unbound.
    /// - Without a modifier.
    /// - In the context [Keybind.Context#IN_GAME].
    /// - In the category `<modid>:main`, which the first key registered in it declares with the display name of the mod.
    ///
    /// @param name    The name of the key.
    /// @param english The English default of the key's name.
    ///
    /// @return The builder of the key.
    ///
    /// @throws IllegalArgumentException If the name is not a valid identifier path.
    ///
    public KeybindBuilder key(String name, String english) {
        if (!Identifier.isValidPath(name)) {
            throw new IllegalArgumentException("Key name '" + name + "' of mod '" + this.mod.getModId() + "' is not a valid identifier path");
        }
        
        return new KeybindBuilder(this, name, english, this.defaultCategory);
    }
    
    ///
    /// Starts a new builder of a key on the mapping of the given name, which the mod did not declare.
    ///
    /// No translation and no category are declared for the key.
    ///
    /// A key that sends to the server does so on the payload `<modid>:keybind/existing/<name>`:
    /// - `<modid>` is the id of the mod.
    /// - `<name>` is the mapping name in lower case, with every character an identifier path refuses replaced by `_`.
    ///
    /// @param mappingName The name of the mapping.
    ///
    /// @return The builder of the key.
    ///
    public MappingBuilder existing(String mappingName) {
        return new MappingBuilder(this, mappingName);
    }
    
    ///
    /// Any keys the mod registered, in the order they were registered.
    ///
    /// @return Any keys the mod registered, in the order they were registered.
    ///
    public @Unmodifiable List<Keybind> getAll() {
        return List.copyOf(this.keybindsByName.values());
    }
    
    ///
    /// Any categories the mod declared, in the order they were declared, the default category included once a key was registered in it.
    ///
    /// @return Any categories the mod declared, in the order they were declared.
    ///
    @ApiStatus.Internal
    public @Unmodifiable List<Identifier> getCategories() {
        return List.copyOf(this.categories);
    }
    
    ///
    /// Locks the keybinds, so declaring a category or registering a key throws from then on.
    ///
    /// Should only ever be called once the key mappings are registered or the payload handlers event ran.
    ///
    @ApiStatus.Internal
    public void lock() {
        this.locked = true;
    }
    
    ///
    /// Registers the key of the given parts.
    ///
    /// A key with a definition gets the mapping name `key.<modid>.<name>` and its translation key with the English default of the definition,
    /// and declares the default category too if the key is the first one registered in it.
    /// A key without one is on the existing mapping of the given name and declares nothing.
    ///
    /// A key with a server action or that syncs its held state gets its own optional payload on the mod's network.
    ///
    /// Should only ever be called from [BindingBuilder#register()].
    ///
    /// @param name         The name the key was declared with, which is the mapping name of a key on an existing mapping.
    /// @param definition   The definition of the key, or `null` if the key is on an existing mapping.
    /// @param activation   The activation the key fires through.
    /// @param clientAction The action run on the client when the key fires, or `null` if none is set.
    /// @param codecs       The codecs of the carried values, in the order the values are read.
    /// @param readers      The readers of the carried values on the client, one per codec.
    /// @param serverAction The action run on the server when a firing arrives, or `null` if none is set.
    /// @param syncHeld     If the key sends its going down and up to the server.
    /// @param version      The version neoforge negotiates for the payload of the key.
    ///
    /// @return The registered key.
    ///
    /// @throws IllegalStateException - If the mod already registered a key of that name.
    /// - If the key mappings were already registered.
    /// - If the payload handlers event already ran.
    /// - If the key is in a category of the mod's namespace that the mod did not declare.
    /// - If the key sends to the server on the same payload as another key of the mod.
    ///
    @ApiStatus.Internal
    public Keybind register(
            String name,
            Keybind.@Nullable Definition definition,
            Activation activation,
            @Nullable ClientOnly<Runnable> clientAction,
            List<NexusCodec<?, ?>> codecs,
            List<ClientOnly<? extends Supplier<?>>> readers,
            @Nullable BiConsumer<ServerPlayer, List<Object>> serverAction,
            boolean syncHeld,
            String version
    ) {
        if (this.locked) {
            throw new IllegalStateException("Key '" + name + "' of mod '" + this.mod.getModId() + "' is registered after the key mappings were registered or the payload handlers event ran");
        }
        
        String mappingName = ObjectMapping.mapNonNullOrElse(definition, own -> "key." + this.mod.getModId() + "." + own.name(), name);
        if (this.keybindsByName.containsKey(mappingName)) {
            throw new IllegalStateException("Key '" + mappingName + "' of mod '" + this.mod.getModId() + "' is already registered");
        }
        
        if (definition != null && this.isUndeclared(definition.category())) {
            throw new IllegalStateException("Key '" + mappingName + "' of mod '" + this.mod.getModId() + "' is in the category '" + definition.category() + "', which the mod did not declare");
        }
        
        String path = ObjectMapping.mapNonNullOrElseGet(definition, own -> "keybind/" + own.name(), () -> "keybind/existing/" + Keybinds.toPayloadName(name));
        ServerboundPayload<KeySignal> payload = BooleanMapping.mapNonNullIfTrue(serverAction != null || syncHeld, () -> this.declarePayload(mappingName, path, codecs, serverAction, version));
        if (definition != null) {
            this.mod.getTranslations().defineKey(mappingName, definition.english());
            if (definition.category().equals(this.defaultCategory) && !this.categories.contains(this.defaultCategory)) {
                this.declareCategory(Keybinds.DEFAULT_CATEGORY, this.mod.getContainer().getModInfo().getDisplayName());
            }
        }
        
        Keybind keybind = new Keybind(mappingName, definition, activation, clientAction, readers, serverAction != null, syncHeld, payload, this.heldKeys);
        this.keybindsByName.put(mappingName, keybind);
        return keybind;
    }
    
    ///
    /// If the given category is in the mod's namespace and is neither the default category nor one the mod declared.
    ///
    private boolean isUndeclared(Identifier category) {
        return category.getNamespace().equals(this.mod.getModId()) && !category.equals(this.defaultCategory) && !this.categories.contains(category);
    }
    
    ///
    /// Declares the optional payload of the key of the given mapping name, which runs the server action over the decoded values on a firing and marks the key as down or up for the sender.
    ///
    /// A firing posts a [KeyReceivedEvent] first and skips the server action if the event is cancelled.
    /// If the player already disconnected, the signal will be dropped.
    ///
    private ServerboundPayload<KeySignal> declarePayload(String mappingName, String path, List<NexusCodec<?, ?>> codecs, @Nullable BiConsumer<ServerPlayer, List<Object>> serverAction, String version) {
        Identifier id = this.mod.id(path);
        for (Keybind existing : this.keybindsByName.values()) {
            ServerboundPayload<KeySignal> payload = existing.getPayload();
            if (payload != null && payload.getId().equals(id)) {
                throw new IllegalStateException("Keys '" + existing.getName() + "' and '" + mappingName + "' of mod '" + this.mod.getModId() + "' would both send on the payload '" + id + "'");
            }
        }
        
        BiConsumer<ServerPlayer, List<Object>> action = Objects.requireNonNullElse(serverAction, (_, _) -> { });
        return this.mod.getNetwork()
                .play(path, KeySignal.codec(codecs.stream().<StreamCodec<? super RegistryFriendlyByteBuf, ?>>map(NexusCodec::asStream).toList()))
                .version(version)
                .optional()
                .toServer((signal, context) -> {
                    // NeoForge still runs a payload that was queued before its player left, after the player was saved and logged out.
                    ServerPlayer player = context.getPlayer();
                    if (player.hasDisconnected()) {
                        return;
                    }
                    
                    switch (signal) {
                        case KeySignal.Fired(List<Object> values) -> {
                            // The key is put into the map right after its payload is declared, so it is there once a firing arrives.
                            Keybind keybind = Objects.requireNonNull(this.keybindsByName.get(mappingName));
                            if (!NeoForge.EVENT_BUS.post(new KeyReceivedEvent(keybind, player)).isCanceled()) {
                                action.accept(player, values);
                            }
                        }
                        case KeySignal.Down _ -> this.heldKeys.press(player, mappingName);
                        case KeySignal.Up _ -> this.heldKeys.release(player, mappingName);
                    }
                });
    }
    
    ///
    /// Declares the translation key of the category of the given name and adds the category.
    ///
    @CanIgnoreReturnValue
    private Identifier declareCategory(String name, String english) {
        this.mod.getTranslations().define("key.category", name, english);
        
        Identifier id = this.mod.id(name);
        this.categories.add(id);
        return id;
    }
}
