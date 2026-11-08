package dev.satherov.nexus.api.component;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.internal.component.PathReaders;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

///
/// A path from the value of a data component to a part of it.
///
/// Two paths are equal if they have the same component and the same steps.
///
/// The reader of a path is chosen when the path is created, so a path is meant to be created once and reused.
///
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ComponentPath {
    
    ///
    /// The codec of a path in the text form of [#toString()].
    ///
    /// Decoding picks the registered component with the longest identifier that the text starts with.
    ///
    public static final NexusCodec<ComponentPath, Access.Plain> CODEC = NexusCodec.STRING.flatXmap(ComponentPath::fromText, ComponentPath::toString);
    
    ///
    /// The pattern of a key that doesn't have to be quoted.
    ///
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    
    ///
    /// The pattern of one step, where only a key at the very start of the input may stand without a dot in front of it.
    ///
    private static final Pattern STEP = Pattern.compile("(?:^|\\.)(?<key>" + ComponentPath.PLAIN_KEY.pattern() + ")|\\[(?:(?<number>[0-9]{1,9})|\\*|'(?<quoted>(?:[^'\\\\]|\\\\['\\\\])*)')]");
    
    ///
    /// The data component type that the path starts at.
    ///
    @Getter
    private final DataComponentType<?> component;
    
    ///
    /// The steps from the value of the component to the part.
    ///
    private final @Unmodifiable List<Step> steps;
    
    ///
    /// The reader of the path, chosen when the path was created.
    ///
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    @Getter(AccessLevel.PACKAGE)
    private final PathReader<?> reader;
    
    ///
    /// Creates the path with the given steps on the given component.
    ///
    /// The steps follow each other with nothing in between, and each is one of four kinds:
    /// - A plain key is one or more ASCII letters, digits, `_`, or `-`, with a dot in front of it unless it's the first step.
    /// - A list index is a number of at most nine digits in brackets.
    /// - A `*` in brackets passes if any element of a list or any value of a map passes the rest of the path.
    /// - Any other key stands in single quotes inside brackets, and a backslash in front of a quote or another backslash makes that character part of the key.
    ///
    /// If the steps are empty, the path reads the whole value.
    /// If the steps are malformed, the exception will report the index at which the first step that could not be read starts.
    ///
    /// @param component The data component type that the path starts at.
    /// @param steps     The steps from the value of the component to the part.
    ///
    /// @return The path.
    ///
    /// @throws IllegalArgumentException If the steps are malformed, the component is transient, or no value of the component can hold the steps.
    /// @throws IllegalStateException    If the readers of component paths are not registered yet, which happens when the registries freeze.
    ///
    public static ComponentPath of(DataComponentType<?> component, String steps) {
        if (steps.startsWith(".")) throw ComponentPath.malformed(steps, 0);
        return ComponentPath.of(component, ComponentPath.parse(steps, 0));
    }
    
    ///
    /// Creates the path with the given steps on the given component, with the reader chosen for them.
    ///
    private static ComponentPath of(DataComponentType<?> component, List<Step> steps) {
        if (component.isTransient()) {
            throw new IllegalArgumentException("The component '" + Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component) + "' is transient");
        }
        
        return new ComponentPath(component, steps, PathReaders.resolve(component, steps));
    }
    
    ///
    /// Reads a path from its text form, as [#CODEC] decodes it.
    ///
    private static ComponentPath fromText(String text) {
        int bracket = text.indexOf('[');
        for (int end = bracket < 0 ? text.length() : bracket; end > 0; end = text.lastIndexOf('.', end - 1)) {
            DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.tryParse(text.substring(0, end)));
            if (component != null) {
                return ComponentPath.of(component, ComponentPath.parse(text, end));
            }
        }
        
        throw new IllegalArgumentException("Could not find a registered component that '" + text + "' starts with");
    }
    
    ///
    /// Parses the steps that start at the given index of the given text.
    ///
    private static @Unmodifiable List<Step> parse(String text, int start) {
        Matcher matcher = ComponentPath.STEP.matcher(text).useAnchoringBounds(false);
        List<Step> steps = new ArrayList<>();
        for (int index = start; index < text.length(); index = matcher.end()) {
            if (!matcher.region(index, text.length()).lookingAt()) {
                throw ComponentPath.malformed(text, index);
            }
            
            String key = matcher.group("key");
            String quoted = matcher.group("quoted");
            String number = matcher.group("number");
            if (key != null) {
                steps.add(new Step.Key(key));
            } else if (quoted != null) {
                steps.add(new Step.Key(quoted.replaceAll("\\\\(.)", "$1")));
            } else if (number != null) {
                steps.add(new Step.Index(Integer.parseInt(number)));
            } else {
                steps.add(new Step.Wildcard());
            }
        }
        
        return List.copyOf(steps);
    }
    
    ///
    /// Creates the failure of steps that could not be parsed past the given index.
    ///
    private static IllegalArgumentException malformed(String text, int index) {
        return new IllegalArgumentException("Could not parse the path '" + text + "' at index '" + index + "'");
    }
    
    ///
    /// The steps from the value of the component to the part.
    ///
    /// @return The steps, or an empty list if the path reads the whole value.
    ///
    public @Unmodifiable List<Step> getAllSteps() {
        return this.steps;
    }
    
    ///
    /// The text form of this path, which is the identifier of the component followed by the steps.
    ///
    /// A key is written as a plain key with a dot in front of it wherever it can be one, and in quotes otherwise.
    ///
    /// If the component is not registered, `[unregistered]` will be written in place of its identifier.
    ///
    /// @return The text form.
    ///
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, this.component));
        for (Step step : this.steps) {
            switch (step) {
                case Step.Key(String name) when ComponentPath.PLAIN_KEY.matcher(name).matches() -> text.append('.').append(name);
                case Step.Key(String name) -> text.append("['").append(name.replace("\\", "\\\\").replace("'", "\\'")).append("']");
                case Step.Index(int index) -> text.append('[').append(index).append(']');
                case Step.Wildcard() -> text.append("[*]");
            }
        }
        
        return text.toString();
    }
    
    ///
    /// A step in a path.
    ///
    public sealed interface Step {
        
        ///
        /// A key of a struct, a record, or a map.
        ///
        /// The key of a map is the string that it encodes to.
        ///
        /// @param name The key.
        ///
        record Key(String name) implements Step { }
        
        ///
        /// An index of a list.
        ///
        /// @param index The zero-based index in the list.
        ///
        record Index(int index) implements Step {
            
            ///
            /// Creates a new index step.
            ///
            /// @throws IllegalArgumentException If the index is negative.
            ///
            public Index {
                if (index < 0) {
                    throw new IllegalArgumentException("The index '" + index + "' is negative");
                }
            }
        }
        
        ///
        /// A step that passes if any element of a list or any value of a map passes the rest of the path.
        ///
        record Wildcard() implements Step { }
    }
}
