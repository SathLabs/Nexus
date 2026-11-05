package dev.satherov.nexus.api.component;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.component.PathReader;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

///
/// A path to a value inside a component, made of the component and the keys and indices down to the value.
///
/// The string form is the identifier of the component followed by the path.
///
/// A path is resolved against its component once, when it is created.
/// Two paths are equal if they have the same component and the same keys and indices.
///
@EqualsAndHashCode
@Accessors(fluent = true)
public final class ComponentPath {
    
    ///
    /// The codec of a path in its string form.
    ///
    /// The component is the longest prefix that is a registered component and ends at a `.`, a `[`, or the end of the form.
    ///
    /// Decoding fails if no such component exists or the rest is not a valid path of that component.
    /// Encoding fails if the component is not registered.
    ///
    public static final NexusCodec<ComponentPath, Access.Plain> CODEC = NexusCodec.STRING.flatXmap(ComponentPath::decode, ComponentPath::encode);
    
    ///
    /// The pattern of one step of a path, which is a dotted key, an index, or a quoted key.
    ///
    private static final Pattern STEP = Pattern.compile("\\G(?:(?:^|(?!^)\\.)(?<key>[\\w-]+)|\\[(?<index>\\d{1,9})]|\\['(?<quoted>(?:[^'\\\\]|\\\\.)*)'])");

    ///
    /// The pattern of a key written after a dot in the string form.
    ///
    private static final Pattern PLAIN = Pattern.compile("[\\w-]+");
    
    ///
    /// The component the path starts at.
    ///
    @Getter
    private final DataComponentType<?> type;
    
    ///
    /// The keys and indices down to the value.
    ///
    private final List<PathReader.Step> steps;
    
    ///
    /// The reader of the value at the path.
    ///
    @EqualsAndHashCode.Exclude
    private final PathReader reader;
    
    ///
    /// Creates the path of the given steps below the given component and resolves it.
    ///
    private ComponentPath(DataComponentType<?> type, List<PathReader.Step> steps) {
        if (type.isTransient()) {
            throw new IllegalArgumentException("The component '" + type + "' is transient");
        }
        
        this.type = type;
        this.steps = steps;
        
        this.reader = PathReader.resolve(type, steps);
    }
    
    ///
    /// Creates the path of the whole value of the given component.
    ///
    /// @param type The component the path starts at.
    ///
    /// @return The path of the whole value.
    ///
    /// @throws IllegalArgumentException If the component is transient.
    ///
    public static ComponentPath of(DataComponentType<?> type) {
        return new ComponentPath(type, List.of());
    }
    
    ///
    /// Creates the path of the value below the given component at the given path, written as:
    /// - Keys joined by `.`.
    /// - An index as `[<index>]`.
    /// - A key as `['<key>']` if it holds anything other than ASCII letters, digits, `_`, and `-`, with `\` before every `'` and `\` in it.
    ///
    /// If the component wasn't built by Nexus, the keys and indices can't be checked until a value is read, and a missing one will fail the comparison.
    ///
    /// @param type The component the path starts at.
    /// @param path The keys and indices down to the value.
    ///
    /// @return The path of the value.
    ///
    /// @throws IllegalArgumentException - If the path is malformed.
    /// - If the component is transient.
    /// - If a Nexus struct on the path doesn't have one of the keys or indices.
    /// - If the path continues below a number, a string, or a boolean of a Nexus struct.
    ///
    public static ComponentPath of(DataComponentType<?> type, String path) {
        return new ComponentPath(type, ComponentPath.parse(path));
    }
    
    ///
    /// Reads a path from its string form.
    ///
    private static ComponentPath decode(String form) {
        for (int end = form.length(); end > 0; end--) {
            if (end < form.length() && form.charAt(end) != '.' && form.charAt(end) != '[') {
                continue;
            }
            
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.tryParse(form.substring(0, end)));
            if (type == null) {
                continue;
            }
            
            if (end == form.length()) {
                return ComponentPath.of(type);
            }
            
            return ComponentPath.of(type, form.charAt(end) == '.' ? form.substring(end + 1) : form.substring(end));
        }
        
        throw new NexusCodecException("Unknown component in '" + form + "'");
    }
    
    ///
    /// Writes the string form of the given path, or throws if its component isn't registered.
    ///
    private static String encode(ComponentPath path) {
        if (BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(path.type) == null) {
            throw new NexusCodecException("The component of '" + path + "' is not registered");
        }
        
        return path.toString();
    }
    
    ///
    /// Parses the keys and indices of the given path.
    ///
    private static List<PathReader.Step> parse(String path) {
        List<PathReader.Step> steps = new ArrayList<>();
        Matcher matcher = ComponentPath.STEP.matcher(path);
        int end = 0;
        while (matcher.find()) {
            String key = matcher.group("key");
            String index = matcher.group("index");
            if (key != null) {
                steps.add(new PathReader.Step.Key(key));
            } else if (index != null) {
                steps.add(new PathReader.Step.Index(Integer.parseInt(index)));
            } else {
                steps.add(new PathReader.Step.Key(matcher.group("quoted").replaceAll("\\\\(.)", "$1")));
            }
            
            end = matcher.end();
        }
        
        if (steps.isEmpty() || end < path.length()) {
            throw new IllegalArgumentException("The path '" + path + "' is malformed at index '" + end + "'");
        }
        
        return List.copyOf(steps);
    }
    
    ///
    /// Creates a filter that compares the value at this path against the given number with the given operator.
    ///
    /// The value and the number are compared numerically, whatever the number type of the value is.
    ///
    /// If the holder doesn't have a value at this path or the value isn't a number, the filter will fail.
    ///
    /// @param operator The comparison of the value against the number.
    /// @param literal  The number to compare the value against.
    ///
    /// @return The filter.
    ///
    public ComponentFilter.Comparison compare(Operator operator, Number literal) {
        return new ComponentFilter.Comparison(this, operator, new Literal.Numeric(literal.doubleValue()));
    }
    
    ///
    /// Creates a filter that compares the value at this path against the given string with the given operator.
    ///
    /// The ordered operators compare the strings lexically.
    ///
    /// If the holder doesn't have a value at this path or the value isn't a string, the filter will fail.
    ///
    /// @param operator The comparison of the value against the string.
    /// @param literal  The string to compare the value against.
    ///
    /// @return The filter.
    ///
    public ComponentFilter.Comparison compare(Operator operator, String literal) {
        return new ComponentFilter.Comparison(this, operator, new Literal.Text(literal));
    }
    
    ///
    /// Creates a filter that compares the value at this path against the given boolean with the given operator.
    ///
    /// If the holder doesn't have a value at this path or the value isn't a boolean, the filter will fail.
    ///
    /// @param operator The comparison of the value against the boolean.
    /// @param literal  The boolean to compare the value against.
    ///
    /// @return The filter.
    ///
    /// @throws IllegalArgumentException If the operator is ordered.
    ///
    public ComponentFilter.Comparison compare(Operator operator, boolean literal) {
        return new ComponentFilter.Comparison(this, operator, new Literal.Flag(literal));
    }
    
    ///
    /// The kind of value this path reads, or [PathReader.Kind#UNKNOWN] if the kind is only seen once a value is read.
    ///
    /// Should only ever be called from [ComponentFilter.Comparison#Comparison(ComponentPath, Operator, Literal)].
    ///
    /// @return The kind of value this path reads.
    ///
    PathReader.Kind kind() {
        return this.reader.kind();
    }
    
    ///
    /// Compares the value of the given holder at this path against the given literal with the given operator.
    ///
    /// An absent component, a missing key, or a value of another kind fails.
    ///
    /// Should only ever be called from [ComponentFilter.Comparison#test(DataComponentGetter)].
    ///
    /// @param holder   The holder of the component.
    /// @param operator The comparison of the value against the literal.
    /// @param literal  The literal to compare the value against.
    ///
    /// @return `true` if the value at this path passes the operator against the literal.
    ///
    boolean test(DataComponentGetter holder, Operator operator, Literal literal) {
        return this.reader.compare(holder, operator, literal);
    }
    
    ///
    /// The string form, the identifier of the component and then the path.
    ///
    /// @return The string form.
    ///
    @Override
    public String toString() {
        StringBuilder form = new StringBuilder(Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, this.type));
        for (PathReader.Step step : this.steps) {
            switch (step) {
                case PathReader.Step.Key(String name) when ComponentPath.PLAIN.matcher(name).matches() -> form.append('.').append(name);
                case PathReader.Step.Key(String name) -> form.append("['").append(name.replace("\\", "\\\\").replace("'", "\\'")).append("']");
                case PathReader.Step.Index(int index) -> form.append('[').append(index).append(']');
            }
        }
        
        return form.toString();
    }
}
