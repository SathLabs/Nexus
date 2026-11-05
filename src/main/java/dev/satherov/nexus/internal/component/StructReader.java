package dev.satherov.nexus.internal.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.component.Literal;
import dev.satherov.nexus.api.component.Operator;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

///
/// A path reader that reads a Nexus struct through the getters of its bound fields.
///
@ApiStatus.Internal
@RequiredArgsConstructor
@Accessors(fluent = true)
public final class StructReader implements PathReader {
    
    ///
    /// The component the path starts at.
    ///
    private final DataComponentType<?> type;
    
    ///
    /// The getters of the bound fields along the path composed into one, from the value of the component down.
    ///
    /// A primitive field is boxed.
    ///
    private final Function<Object, Object> getter;
    
    ///
    /// The kind of the field at the end of the path.
    ///
    @Getter
    private final Kind kind;
    
    ///
    /// Applies the getter to the component and compares the field by its kind.
    ///
    @Override
    public boolean compare(DataComponentGetter holder, Operator operator, Literal literal) {
        Object component = holder.get(this.type);
        if (component == null) {
            return false;
        }
        
        Object value = this.getter.apply(component);
        return switch (this.kind) {
            case NUMBER -> literal instanceof Literal.Numeric(double expected) && PathReader.passes(operator, ((Number) value).doubleValue(), expected);
            case TEXT -> literal instanceof Literal.Text(String expected) && PathReader.passes(operator, ((String) value).compareTo(expected), 0);
            case FLAG -> literal instanceof Literal.Flag(boolean expected) && PathReader.passes(operator, (Boolean) value == expected ? 0 : 1, 0);
            case OTHER, UNKNOWN -> false;
        };
    }
}
