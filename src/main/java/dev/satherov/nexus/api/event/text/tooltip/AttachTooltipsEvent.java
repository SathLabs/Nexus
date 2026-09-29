package dev.satherov.nexus.api.event.text.tooltip;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.text.tooltip.TooltipBuilder;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

///
/// An event that attaches lines to the tooltips of item and fluid stacks.
///
/// Posted once on the event bus of every mod that has a [NexusMod] handle, when neoforge registers its tooltip appenders.
///
public final class AttachTooltipsEvent extends Event implements IModBusEvent {
    
    ///
    /// The handle of the mod whose event bus this event is posted on.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The attachments to item stacks, in order, oldest first.
    ///
    private final List<BiConsumer<ItemStack, TooltipBuilder>> itemAttachments = new ArrayList<>();
    
    ///
    /// The attachments to fluid stacks, in order, oldest first.
    ///
    private final List<BiConsumer<FluidStack, TooltipBuilder>> fluidAttachments = new ArrayList<>();
    
    ///
    /// Creates the event for the given mod.
    ///
    /// Should only ever be called by Nexus itself, when it dispatches the tooltips of a mod.
    ///
    /// @param mod The handle of the mod whose event bus this event is posted on.
    ///
    @ApiStatus.Internal
    public AttachTooltipsEvent(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// Attaches lines to the tooltip of every stack of the given item.
    ///
    /// The lines are added after the item's own hover text and before the lines of the stack's data components.
    /// Stacks with previous calls to [#item(ItemLike, BiConsumer)] and [#items(Predicate, BiConsumer)], in the order they were written.
    ///
    /// @param item       The item whose stacks get the lines.
    /// @param attachment The action that adds the lines, called with the stack and the builder of its tooltip.
    ///
    public void item(ItemLike item, BiConsumer<ItemStack, TooltipBuilder> attachment) {
        this.items(stack -> stack.is(item.asItem()), attachment);
    }
    
    ///
    /// Attaches lines to the tooltip of every item stack the given filter accepts.
    ///
    /// The lines are added after the item's own hover text and before the lines of the stack's data components.
    /// Stacks with previous calls to [#item(ItemLike, BiConsumer)] and [#items(Predicate, BiConsumer)], in the order they were written.
    ///
    /// @param filter     The filter the stacks have to pass to get the lines.
    /// @param attachment The action that adds the lines, called with the stack and the builder of its tooltip.
    ///
    public void items(Predicate<ItemStack> filter, BiConsumer<ItemStack, TooltipBuilder> attachment) {
        this.itemAttachments.add(AttachTooltipsEvent.restrict(filter, attachment));
    }
    
    ///
    /// Attaches lines to the tooltip of every stack of the given fluid.
    ///
    /// The lines are added after the fluid's own hover text.
    /// Stacks with previous calls to [#fluid(Fluid, BiConsumer)] and [#fluids(Predicate, BiConsumer)], in the order they were written.
    ///
    /// @param fluid      The fluid whose stacks get the lines.
    /// @param attachment The action that adds the lines, called with the stack and the builder of its tooltip.
    ///
    public void fluid(Fluid fluid, BiConsumer<FluidStack, TooltipBuilder> attachment) {
        this.fluids(stack -> stack.is(fluid), attachment);
    }
    
    ///
    /// Attaches lines to the tooltip of every fluid stack the given filter accepts.
    ///
    /// The lines are added after the fluid's own hover text.
    /// Stacks with previous calls to [#fluid(Fluid, BiConsumer)] and [#fluids(Predicate, BiConsumer)], in the order they were written.
    ///
    /// @param filter     The filter the stacks have to pass to get the lines.
    /// @param attachment The action that adds the lines, called with the stack and the builder of its tooltip.
    ///
    public void fluids(Predicate<FluidStack> filter, BiConsumer<FluidStack, TooltipBuilder> attachment) {
        this.fluidAttachments.add(AttachTooltipsEvent.restrict(filter, attachment));
    }
    
    ///
    /// Wraps the given attachment so that it only runs for the stacks the given filter accepts.
    ///
    private static <T> BiConsumer<T, TooltipBuilder> restrict(Predicate<T> filter, BiConsumer<T, TooltipBuilder> attachment) {
        return (stack, builder) -> {
            if (filter.test(stack)) {
                attachment.accept(stack, builder);
            }
        };
    }
    
    ///
    /// Every attachment to item stacks as one attachment, in order, oldest first, or `null` if there was none.
    ///
    /// @return The attachments to item stacks as one attachment, or `null` if there was none.
    ///
    @ApiStatus.Internal
    public @Nullable BiConsumer<ItemStack, TooltipBuilder> getItemAttachments() {
        return AttachTooltipsEvent.combine(this.itemAttachments);
    }
    
    ///
    /// Every attachment to fluid stacks as one attachment, in order, oldest first, or `null` if there was none.
    ///
    /// @return The attachments to fluid stacks as one attachment, or `null` if there was none.
    ///
    @ApiStatus.Internal
    public @Nullable BiConsumer<FluidStack, TooltipBuilder> getFluidAttachments() {
        return AttachTooltipsEvent.combine(this.fluidAttachments);
    }

    ///
    /// Combines a copy of the given attachments into one attachment that runs them in order, or `null` if there are none.
    ///
    private static <T> @Nullable BiConsumer<T, TooltipBuilder> combine(List<BiConsumer<T, TooltipBuilder>> attachments) {
        if (attachments.isEmpty()) {
            return null;
        }

        List<BiConsumer<T, TooltipBuilder>> snapshot = List.copyOf(attachments);
        return (stack, builder) -> snapshot.forEach(attachment -> attachment.accept(stack, builder));
    }
}
