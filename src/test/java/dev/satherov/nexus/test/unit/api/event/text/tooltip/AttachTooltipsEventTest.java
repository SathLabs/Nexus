package dev.satherov.nexus.test.unit.api.event.text.tooltip;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.text.tooltip.AttachTooltipsEvent;
import dev.satherov.nexus.api.text.tooltip.TooltipBuilder;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

///
/// Checks the filters and the order of the tooltip attachments an event collects.
///
public class AttachTooltipsEventTest {

    private static final int MANY = 100_000;

    @Test
    public void hasNoAttachmentsWithoutAttaching() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        Assertions.assertThat(event.getItemAttachments()).isNull();
        Assertions.assertThat(event.getFluidAttachments()).isNull();
    }
    
    @Test
    public void keepsItemAttachmentsApartFromFluids() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.items(_ -> true, AttachTooltipsEventTest.line("item"));
        Assertions.assertThat(event.getFluidAttachments()).isNull();
    }
    
    @Test
    public void keepsFluidAttachmentsApartFromItems() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.fluids(_ -> true, AttachTooltipsEventTest.line("fluid"));
        Assertions.assertThat(event.getItemAttachments()).isNull();
    }

    @Test
    public void runsManyItemAttachments() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        IntStream.range(0, AttachTooltipsEventTest.MANY).forEach(_ -> event.items(_ -> true, AttachTooltipsEventTest.line("item")));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getItemAttachments(), ItemStack.EMPTY)).hasSize(AttachTooltipsEventTest.MANY);
    }

    @Test
    public void runsManyFluidAttachments() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        IntStream.range(0, AttachTooltipsEventTest.MANY).forEach(_ -> event.fluids(_ -> true, AttachTooltipsEventTest.line("fluid")));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getFluidAttachments(), FluidStack.EMPTY)).hasSize(AttachTooltipsEventTest.MANY);
    }
    
    @Test
    public void runsItemAttachmentsInAttachOrder() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.items(_ -> true, AttachTooltipsEventTest.line("first"));
        event.items(_ -> true, AttachTooltipsEventTest.line("second"));
        event.items(_ -> true, AttachTooltipsEventTest.line("third"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getItemAttachments(), ItemStack.EMPTY)).containsExactly("first", "second", "third");
    }
    
    @Test
    public void skipsItemStacksTheFilterRejects() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.items(ItemStack::isEmpty, AttachTooltipsEventTest.line("accepted"));
        event.items(stack -> !stack.isEmpty(), AttachTooltipsEventTest.line("rejected"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getItemAttachments(), ItemStack.EMPTY)).containsExactly("accepted");
    }
    
    @Test
    public void skipsStacksOfOtherItems() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.item(Items.STONE, AttachTooltipsEventTest.line("stone"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getItemAttachments(), ItemStack.EMPTY)).isEmpty();
    }
    
    @Test
    public void runsFluidAttachmentsInAttachOrder() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.fluids(_ -> true, AttachTooltipsEventTest.line("first"));
        event.fluids(_ -> true, AttachTooltipsEventTest.line("second"));
        event.fluids(_ -> true, AttachTooltipsEventTest.line("third"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getFluidAttachments(), FluidStack.EMPTY)).containsExactly("first", "second", "third");
    }
    
    @Test
    public void skipsFluidStacksTheFilterRejects() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.fluids(FluidStack::isEmpty, AttachTooltipsEventTest.line("accepted"));
        event.fluids(stack -> !stack.isEmpty(), AttachTooltipsEventTest.line("rejected"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getFluidAttachments(), FluidStack.EMPTY)).containsExactly("accepted");
    }
    
    @Test
    public void skipsStacksOfOtherFluids() {
        AttachTooltipsEvent event = new AttachTooltipsEvent(Nexus.getMod());
        event.fluid(Fluids.WATER, AttachTooltipsEventTest.line("water"));
        Assertions.assertThat(AttachTooltipsEventTest.tooltip(event.getFluidAttachments(), FluidStack.EMPTY)).isEmpty();
    }
    
    private static <T> BiConsumer<T, TooltipBuilder> line(String text) {
        return (_, builder) -> builder.line(Component.literal(text));
    }
    
    private static <T> List<String> tooltip(@Nullable BiConsumer<T, TooltipBuilder> attachments, T stack) {
        List<String> lines = new ArrayList<>();
        Objects.requireNonNull(attachments).accept(stack, new TooltipBuilder(Item.TooltipContext.EMPTY, TooltipFlag.NORMAL, line -> lines.add(line.getString())));
        return lines;
    }
}
