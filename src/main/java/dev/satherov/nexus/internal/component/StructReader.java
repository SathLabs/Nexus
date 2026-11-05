package dev.satherov.nexus.internal.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.component.Literal;
import dev.satherov.nexus.api.component.Operator;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Function;

///
/// A path reader that follows the getters of the bound fields of a Nexus struct and encodes nothing.
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
    /// The getters of the fields on the path, from the value of the component down.
    ///
    private final List<Function<Object, Object>> getters;
    
    ///
    /// The kind of the field at the end of the path.
    ///
    @Getter
    private final Kind kind;
    
    ///
    /// Applies the getters to the value of the component and compares the result by its kind.
    ///
    @Override
    public boolean compare(DataComponentGetter holder, Operator operator, Literal literal) {
        Object value = holder.get(this.type);
        if (value == null) {
            return false;
        }
        
        for (int i = 0; i < this.getters.size(); i++) {
            value = this.getters.get(i).apply(value);
        }
        
        return switch (this.kind) {
            case NUMBER -> literal instanceof Literal.Numeric(double expected) && PathReader.passes(operator, Double.compare(((Number) value).doubleValue(), expected));
            case TEXT -> literal instanceof Literal.Text(String expected) && PathReader.passes(operator, ((String) value).compareTo(expected));
            case FLAG -> literal instanceof Literal.Flag(boolean expected) && PathReader.passes(operator, (Boolean) value == expected ? 0 : 1);
            case OTHER, UNKNOWN -> false;
        };
    }
}
