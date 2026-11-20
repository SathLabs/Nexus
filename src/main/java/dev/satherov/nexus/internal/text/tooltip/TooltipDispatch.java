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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

///
/// The dispatch of the tooltip attachments of every mod that has a handle.
///
@UtilityClass
@ApiStatus.Internal
public class TooltipDispatch {
    
    ///
    /// The item attachments of every mod, in the order the mods got [RegisterTooltipAppendersEvent].
    ///
    private static final List<BiConsumer<ItemStack, TooltipBuilder>> ITEM_ATTACHMENTS = new ArrayList<>();
    
    ///
    /// The fluid attachments of every mod, in the order the mods got [RegisterTooltipAppendersEvent].
    ///
    private static final List<BiConsumer<FluidStack, TooltipBuilder>> FLUID_ATTACHMENTS = new ArrayList<>();
    
    ///
    /// Listens on the event bus of the given mod for [RegisterTooltipAppendersEvent], posts an [AttachTooltipsEvent] there
    /// and then adds what it collected to the attachments of every mod.
    ///
    /// The first mod with an attachment for a side registers it once for every mod:
    /// - The item attachments as one appender, after the item's own hover text.
    /// - The fluid attachments as one listener for [FluidTooltipEvent] on the game bus.
    ///
    /// Every tooltip gets one builder for the attachments of every mod, so the hint to hold shift is added once.
    ///
    /// If no mod got an attachment for a side, nothing will be registered for it.
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
                if (TooltipDispatch.ITEM_ATTACHMENTS.isEmpty()) {
                    registration.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, _, _, flag, lines) -> {
                        TooltipBuilder builder = new TooltipBuilder(context, flag, lines);
                        TooltipDispatch.ITEM_ATTACHMENTS.forEach(attachment -> attachment.accept(stack, builder));
                    });
                }
                
                TooltipDispatch.ITEM_ATTACHMENTS.add(items);
            }
            
            BiConsumer<FluidStack, TooltipBuilder> fluids = event.getFluidAttachments();
            if (fluids != null) {
                if (TooltipDispatch.FLUID_ATTACHMENTS.isEmpty()) {
                    NeoForge.EVENT_BUS.addListener(FluidTooltipEvent.class, tooltip -> {
                        TooltipBuilder builder = new TooltipBuilder(tooltip.getContext(), tooltip.getFlags(), tooltip.getToolTip()::add);
                        TooltipDispatch.FLUID_ATTACHMENTS.forEach(attachment -> attachment.accept(tooltip.getFluidStack(), builder));
                    });
                }
                
                TooltipDispatch.FLUID_ATTACHMENTS.add(fluids);
            }
        });
    }
}
