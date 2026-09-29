package dev.satherov.nexus.api.text;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.zelqro.utils.StringUtils;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

///
/// A table that holds every translation key one mod declares.
///
public final class Translations {
    
    ///
    /// The id of the mod that declares the keys.
    ///
    private final String modId;
    
    ///
    /// Every declared translation by its key.
    ///
    private final Map<String, Translation> translationsByKey = new LinkedHashMap<>();
    
    ///
    /// Creates the empty table of the given mod.
    ///
    /// Should only ever be called from [NexusMod#getTranslations()].
    ///
    /// @param modId The mod id.
    ///
    @ApiStatus.Internal
    public Translations(String modId) {
        this.modId = modId;
    }
    
    ///
    /// Declares the key `<category>.<modid>.<name>` with the given English default.
    /// - `<category>` is the given category.
    /// - `<modid>` is the id of the mod.
    /// - `<name>` is the given name.
    ///
    /// @param category The first part of the key.
    /// @param name     The last part of the key.
    /// @param english  The English default, with its placeholders.
    ///
    /// @return The declared translation.
    ///
    /// @throws IllegalStateException If the key is already declared.
    ///
    public Translation define(String category, String name, String english) {
        String key = category + "." + this.modId + "." + name;
        if (this.translationsByKey.containsKey(key)) {
            throw new IllegalStateException("Key '" + key + "' is already declared");
        }
        
        Translation translation = new Translation(key, english);
        this.translationsByKey.put(key, translation);
        return translation;
    }
    
    ///
    /// Declares the key `<category>.<modid>.<name>` for the given enum constant, with the given English default.
    /// - `<category>` is the given category.
    /// - `<modid>` is the id of the mod.
    /// - `<name>` is the name of the given constant in lowercase.
    ///
    /// @param category The first part of the key.
    /// @param constant The enum constant the key is for.
    /// @param english  The English default, with its placeholders.
    ///
    /// @return The declared translation.
    ///
    /// @throws IllegalStateException If the key is already declared.
    ///
    public Translation define(String category, Enum<?> constant, String english) {
        return this.define(category, StringUtils.lower(constant.name()), english);
    }
    
    ///
    /// Every declared translation.
    ///
    /// @return Every declared translation.
    ///
    public @Unmodifiable Collection<Translation> getAll() {
        return List.copyOf(this.translationsByKey.values());
    }
}
