package dev.satherov.nexus.api.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Map;

///
/// A value that stands for one declared translation key.
///
/// If the current language doesn't have the key, the created components will show the English default.
///
public interface Translatable {
    
    ///
    /// The declared key this value stands for.
    ///
    /// @return The declared key.
    ///
    Translation getTranslation();
    
    ///
    /// Creates a component of the key with no arguments.
    ///
    /// @return The created component.
    ///
    default MutableComponent component() {
        Translation translation = this.getTranslation();
        return Component.translatableWithFallback(translation.getKey(), translation.getPattern());
    }
    
    ///
    /// Creates a component of the key with its one argument.
    ///
    /// Uses the given value depending on its type:
    /// - A [Component], [Number], [Boolean], or [String] as it is.
    /// - A [Translatable] as its [#component()].
    /// - Anything else as a literal component of its [Object#toString()].
    ///
    /// @param name  The name of the placeholder.
    /// @param value The argument for the placeholder.
    ///
    /// @return The created component.
    ///
    /// @throws IllegalArgumentException If the given name is not the only placeholder of the key.
    ///
    default MutableComponent with(String name, Object value) {
        Translation translation = this.getTranslation();
        if (!translation.getPlaceholders().equals(List.of(name))) {
            throw new IllegalArgumentException("'" + name + "' is not the only placeholder of '" + translation.getKey() + "'");
        }
        
        return Component.translatableWithFallback(translation.getKey(), translation.getPattern(), Translatable.toArgument(value));
    }
    
    ///
    /// Creates a component of the key with every argument by name.
    ///
    /// Uses every argument depending on its type:
    /// - A [Component], [Number], [Boolean], or [String] as it is.
    /// - A [Translatable] as its [#component()].
    /// - Anything else as a literal component of its [Object#toString()].
    ///
    /// @param arguments The arguments, keyed by the name of their placeholder.
    ///
    /// @return The created component.
    ///
    /// @throws IllegalArgumentException If a name is not a placeholder of the key, or if a placeholder has no argument.
    ///
    default MutableComponent with(Map<String, Object> arguments) {
        Translation translation = this.getTranslation();
        List<String> placeholders = translation.getPlaceholders();
        for (String name : arguments.keySet()) {
            if (!placeholders.contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is not a placeholder of '" + translation.getKey() + "'");
            }
        }
        
        Object[] values = new Object[placeholders.size()];
        for (int i = 0; i < values.length; i++) {
            Object value = arguments.get(placeholders.get(i));
            if (value == null) {
                throw new IllegalArgumentException("Placeholder '" + placeholders.get(i) + "' of '" + translation.getKey() + "' has no argument");
            }
            
            values[i] = Translatable.toArgument(value);
        }
        
        return Component.translatableWithFallback(translation.getKey(), translation.getPattern(), values);
    }
    
    ///
    /// Converts the given value into an argument that vanilla accepts.
    ///
    private static Object toArgument(Object value) {
        return switch (value) {
            case Component _, Number _, Boolean _, String _ -> value;
            case Translatable translatable -> translatable.component();
            default -> Component.literal(value.toString());
        };
    }
}
