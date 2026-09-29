package dev.satherov.nexus.test.game.api.event.text.tooltip;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.text.tooltip.AttachTooltipsEvent;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DiscFragmentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

///
/// Checks that attached lines show up in the tooltips of the stacks they were attached to, in place, and nowhere else.
///
public class AttachTooltipsEventSamples {
    
    private static final String FIRST = "First attached line";
    private static final String SECOND = "Second attached line";
    private static final String FLUID = "Attached fluid line";
    private static final String LORE = "Lore line";
    
    @ServerTest
    public static void attachesBetweenHoverTextAndComponents(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DISC_FRAGMENT_5);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(AttachTooltipsEventSamples.LORE))));
        List<String> expected = List.of(
                stack.getHoverName().getString(),
                ((DiscFragmentItem) Items.DISC_FRAGMENT_5).getDisplayName().getString(),
                AttachTooltipsEventSamples.FIRST,
                AttachTooltipsEventSamples.SECOND,
                AttachTooltipsEventSamples.LORE
        );

        helper.assertValueEqual(expected, AttachTooltipsEventSamples.lines(stack, helper), "tooltip lines");
        helper.succeed();
    }
    
    @ServerTest
    public static void skipsOtherItems(GameTestHelper helper) {
        List<String> lines = AttachTooltipsEventSamples.lines(new ItemStack(Items.STICK), helper);
        helper.assertFalse(lines.contains(AttachTooltipsEventSamples.FIRST) || lines.contains(AttachTooltipsEventSamples.SECOND), "The stick got the lines of the disc fragment");
        helper.succeed();
    }
    
    @ServerTest
    public static void attachesToFluidStacks(GameTestHelper helper) {
        FluidStack stack = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        List<String> expected = List.of(stack.getHoverName().getString(), AttachTooltipsEventSamples.FLUID);
        helper.assertValueEqual(expected, AttachTooltipsEventSamples.lines(stack, helper), "tooltip lines");
        helper.succeed();
    }
    
    @ServerTest
    public static void skipsOtherFluids(GameTestHelper helper) {
        List<String> lines = AttachTooltipsEventSamples.lines(new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME), helper);
        helper.assertFalse(lines.contains(AttachTooltipsEventSamples.FLUID), "The lava got the line of the water");
        helper.succeed();
    }
    
    private static List<String> lines(ItemStack stack, GameTestHelper helper) {
        return stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
    }
    
    private static List<String> lines(FluidStack stack, GameTestHelper helper) {
        return stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Attachments {
        
        @SubscribeEvent
        public static void onAttach(AttachTooltipsEvent event) {
            event.item(Items.DISC_FRAGMENT_5, (_, builder) -> builder.line(Component.literal(AttachTooltipsEventSamples.FIRST)));
            event.items(stack -> stack.is(Items.DISC_FRAGMENT_5), (_, builder) -> builder.line(Component.literal(AttachTooltipsEventSamples.SECOND)));
            event.fluid(Fluids.WATER, (_, builder) -> builder.line(Component.literal(AttachTooltipsEventSamples.FLUID)));
        }
    }
}
