package dev.satherov.nexus.api.keybind;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.keybind.KeyDefinition;
import dev.satherov.nexus.internal.keybind.client.ClientKeys;

import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.resources.Identifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /// Every registered key by the name it was declared with, in the order they were registered.
    ///
    private final Map<String, Keybind> keybindsByName = new LinkedHashMap<>();
    
    ///
    /// Every declared category, in the order they were declared.
    ///
    private final List<Identifier> categories = new ArrayList<>();
    
    ///
    /// If the key mappings were already registered.
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
        
        if (FMLEnvironment.getDist().isClient()) {
            ClientKeys.attach(this);
        }
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
    /// @throws IllegalStateException    If the mod already declared the category, or if the key mappings were already registered.
    ///
    public Identifier category(String name, String english) {
        if (!Identifier.isValidPath(name)) {
            throw new IllegalArgumentException("Category name '" + name + "' of mod '" + this.mod.getModId() + "' is not a valid identifier path");
        }
        
        if (this.locked) {
            throw new IllegalStateException("Category '" + name + "' of mod '" + this.mod.getModId() + "' is declared after the key mappings were registered");
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
    /// Marks the key mappings as registered.
    ///
    /// Declaring a category or registering a key throws from then on.
    ///
    /// Should only ever be called from [ClientKeys], once the key mappings are registered.
    ///
    @ApiStatus.Internal
    public void lock() {
        this.locked = true;
    }
    
    ///
    /// Registers the key of the given definition and declares its translation key with the given English default.
    ///
    /// Declares the default category too if the key is the first one registered in it.
    ///
    /// Should only ever be called from [BindingBuilder#register()].
    ///
    /// @param definition The definition of the key.
    /// @param english    The English default of the key's name.
    ///
    /// @return The registered key.
    ///
    /// @throws IllegalStateException If the mod already registered a key of that name, or if the key mappings were already registered.
    ///
    @ApiStatus.Internal
    public Keybind register(KeyDefinition definition, String english) {
        if (this.locked) {
            throw new IllegalStateException("Key '" + definition.name() + "' of mod '" + this.mod.getModId() + "' is registered after the key mappings were registered");
        }
        
        if (this.keybindsByName.containsKey(definition.name())) {
            throw new IllegalStateException("Key '" + definition.name() + "' of mod '" + this.mod.getModId() + "' is already registered");
        }
        
        Translation translation = this.mod.getTranslations().define("key", definition.name(), english);
        if (definition.category().equals(this.defaultCategory) && !this.categories.contains(this.defaultCategory)) {
            this.declareCategory(Keybinds.DEFAULT_CATEGORY, this.mod.getContainer().getModInfo().getDisplayName());
        }
        
        Keybind keybind = new Keybind(translation.getKey(), definition);
        this.keybindsByName.put(definition.name(), keybind);
        return keybind;
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
