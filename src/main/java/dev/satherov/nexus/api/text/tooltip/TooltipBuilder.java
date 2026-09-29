package dev.satherov.nexus.api.text.tooltip;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.internal.text.tooltip.TooltipDispatch;
import dev.satherov.nexus.internal.text.tooltip.TooltipText;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

///
/// A builder that adds lines to the tooltip of an item.
///
public final class TooltipBuilder {

    ///
    /// The context of the tooltip being built.
    ///
    @Getter
    private final Item.TooltipContext context;

    ///
    /// The flag of the tooltip being built, including the held modifier keys.
    ///
    @Getter
    private final TooltipFlag flag;

    ///
    /// The consumer of every added line, called once per line in the order they were added.
    ///
    private final Consumer<Component> lines;

    ///
    /// Creates a builder that passes every added line to the given consumer.
    ///
    /// Should only ever be called from [TooltipDispatch#listen(NexusMod)].
    ///
    /// @param context The context of the tooltip.
    /// @param flag    The flag of the tooltip.
    /// @param lines   The consumer of every added line.
    ///
    @ApiStatus.Internal
    public TooltipBuilder(Item.TooltipContext context, TooltipFlag flag, Consumer<Component> lines) {
        this.context = context;
        this.flag = flag;
        this.lines = lines;
    }

    ///
    /// Adds the given line.
    ///
    /// If the given line doesn't have a color, it will be shown gray.
    ///
    /// @param line The line to add.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder line(Component line) {
        this.lines.accept(line.getStyle().getColor() == null ? line.copy().withStyle(ChatFormatting.GRAY) : line);
        return this;
    }

    ///
    /// Adds the component of the given key as a gray line.
    ///
    /// @param line The key to add as a line.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder line(Translatable line) {
        return this.line(line.component());
    }

    ///
    /// Adds a `<label>: <value>` line, with the label gray and the value white.
    ///
    /// The value is added in one of the following ways:
    /// - A [Component] is added as it is.
    /// - A [Translatable] is added as its component.
    /// - Any other value is added as its string.
    ///
    /// @param label The key of the label.
    /// @param value The value to show after the label.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder entry(Translatable label, Object value) {
        MutableComponent text = switch (value) {
            case Component component -> Component.empty().append(component);
            case Translatable translatable -> translatable.component();
            default -> Component.literal(value.toString());
        };
        this.lines.accept(label.component()
                .append(": ")
                .withStyle(ChatFormatting.GRAY)
                .append(text.withStyle(ChatFormatting.WHITE))
        );
        return this;
    }

    ///
    /// Adds an empty line.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder blank() {
        this.lines.accept(Component.empty());
        return this;
    }

    ///
    /// Shows the given section if shift is held.
    ///
    /// If shift is not held, a dark gray hint to hold the sneak key for details will be added instead.
    ///
    /// @param section The section of lines to add, if shift is held.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder onShift(Consumer<TooltipBuilder> section) {
        if (this.flag.hasShiftDown()) {
            section.accept(this);
        } else {
            this.lines.accept(TooltipText.HOLD_SHIFT.with("key", Component.keybind("key.sneak")).withStyle(ChatFormatting.DARK_GRAY));
        }

        return this;
    }

    ///
    /// Shows the given section if advanced tooltips are on.
    ///
    /// @param section The section of lines to add, if advanced tooltips are on.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public TooltipBuilder onAdvanced(Consumer<TooltipBuilder> section) {
        if (this.flag.isAdvanced()) {
            section.accept(this);
        }

        return this;
    }
}
