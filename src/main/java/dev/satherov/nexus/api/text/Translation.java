package dev.satherov.nexus.api.text;

import lombok.Getter;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

///
/// A translation key that a mod declared, with its English default.
///
/// The English default writes an argument as a placeholder with the pattern `{{<name>}}`, where `<name>` is made of `a-z`, `A-Z`, `0-9`, and `_`.
///
public final class Translation implements Translatable {
    
    ///
    /// The regex of a placeholder, with the name of the placeholder as its first group.
    ///
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    
    ///
    /// The full key.
    ///
    /// E.g.: `tooltip.examplemod.mana`.
    ///
    @Getter
    private final String key;
    
    ///
    /// The English default, with its placeholders.
    ///
    @Getter
    private final String english;
    
    ///
    /// The English default in vanilla's positional form, as the language file writes it.
    /// - Every placeholder becomes `%<n>$s`, where `<n>` is the position of its name in [#getPlaceholders()], starting at `1`.
    /// - A literal `%` becomes `%%`.
    ///
    @Getter
    private final String pattern;
    
    ///
    /// The names of the placeholders, in the order they first appear in the English default.
    ///
    private final List<String> placeholders;
    
    ///
    /// Creates the translation of the given key and resolves the placeholders of the given English default.
    ///
    /// Should only ever be called from [Translations#define(String, String, String)].
    ///
    /// @param key     The full key.
    /// @param english The English default, with its placeholders.
    ///
    @ApiStatus.Internal
    public Translation(String key, String english) {
        this.key = key;
        this.english = english;
        
        List<String> placeholders = new ArrayList<>();
        this.pattern = Translation.PLACEHOLDER.matcher(english.replace("%", "%%")).replaceAll(match -> {
            String name = match.group(1);
            if (!placeholders.contains(name)) {
                placeholders.add(name);
            }
            
            return "%" + (placeholders.indexOf(name) + 1) + "\\$s";
        });
        
        this.placeholders = List.copyOf(placeholders);
    }
    
    ///
    /// The names of the placeholders, in the order they first appear in the English default.
    ///
    /// @return The names of the placeholders.
    ///
    public @Unmodifiable List<String> getPlaceholders() {
        return this.placeholders;
    }
    
    ///
    /// @return This translation.
    ///
    @Override
    public Translation getTranslation() {
        return this;
    }
}
