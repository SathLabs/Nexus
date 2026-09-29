package dev.satherov.nexus.internal.text.tooltip;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.event.text.tooltip.AttachTooltipsEvent;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.text.tooltip.TooltipBuilder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.event.entity.player.FluidTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiConsumer;

///
/// The dispatch of the tooltip attachments of every mod that has a handle.
///
@UtilityClass
@ApiStatus.Internal
public class TooltipDispatch {
    
    ///
    /// Listens on the event bus of the given mod for [RegisterTooltipAppendersEvent], posts an [AttachTooltipsEvent] there
    /// and then registers what it collected:
    /// - The item attachments as one appender, after the item's own hover text.
    /// - The fluid attachments as one listener for [FluidTooltipEvent] on the game bus.
    ///
    /// If a side got no attachment, nothing will be registered for it.
    ///
    /// @param mod The handle of the mod.
    ///
    public static void listen(NexusMod mod) {
        IEventBus bus = mod.getEventBus();
        bus.addListener(RegisterTooltipAppendersEvent.class, registration -> {
            AttachTooltipsEvent event = new AttachTooltipsEvent(mod);
            bus.post(event);
            
            BiConsumer<ItemStack, TooltipBuilder> items = event.getItemAttachments();
            if (items != null) {
                registration.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, _, _, flag, lines) -> items.accept(
                        stack,
                        new TooltipBuilder(context, flag, lines)
                ));
            }
            
            BiConsumer<FluidStack, TooltipBuilder> fluids = event.getFluidAttachments();
            if (fluids != null) {
                NeoForge.EVENT_BUS.addListener(FluidTooltipEvent.class, tooltip -> fluids.accept(
                        tooltip.getFluidStack(),
                        new TooltipBuilder(tooltip.getContext(), tooltip.getFlags(), tooltip.getToolTip()::add)
                ));
            }
        });
    }
}
