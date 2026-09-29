package dev.satherov.nexus.test.unit.api.text.tooltip;

import dev.satherov.nexus.api.text.Translations;
import dev.satherov.nexus.api.text.tooltip.TooltipBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

///
/// Checks the colors of the lines a builder adds and the sections it runs for each flag.
///
public class TooltipBuilderTest {

    private final List<Component> lines = new ArrayList<>();

    private final Translations translations = new Translations("examplemod");

    @Test
    public void graysALineWithoutColor() {
        this.builder(TooltipFlag.NORMAL).line(Component.literal("Mana"));
        Assertions.assertThat(this.lines)
                .singleElement()
                .extracting(Component::toFlatList)
                .isEqualTo(List.of(Component.literal("Mana").withStyle(ChatFormatting.GRAY)));
    }

    @Test
    public void leavesTheGivenLineUnchanged() {
        MutableComponent line = Component.literal("Mana");
        this.builder(TooltipFlag.NORMAL).line(line);
        Assertions.assertThat(line.getStyle()).isEqualTo(Style.EMPTY);
    }

    @Test
    public void keepsTheColorOfAColoredLine() {
        this.builder(TooltipFlag.NORMAL).line(Component.literal("Mana").withStyle(ChatFormatting.AQUA));
        Assertions.assertThat(this.lines)
                .singleElement()
                .extracting(Component::toFlatList)
                .isEqualTo(List.of(Component.literal("Mana").withStyle(ChatFormatting.AQUA)));
    }

    @Test
    public void graysATranslatableLine() {
        this.builder(TooltipFlag.NORMAL).line(this.translations.define("tooltip", "mana", "Mana"));
        Assertions.assertThat(this.lines)
                .singleElement()
                .extracting(Component::toFlatList)
                .isEqualTo(List.of(Component.literal("Mana").withStyle(ChatFormatting.GRAY)));
    }

    @Test
    public void showsAnEntryWithAGrayLabelAndAWhitePlainValue() {
        this.builder(TooltipFlag.NORMAL).entry(this.translations.define("tooltip", "mana", "Mana"), 42);
        Component entry = this.lines.getFirst();
        Assertions.assertThat(entry.getString()).isEqualTo("Mana: 42");
        Assertions.assertThat(entry.toFlatList()).contains(Component.literal("Mana").withStyle(ChatFormatting.GRAY), Component.literal("42").withStyle(ChatFormatting.WHITE));
    }

    @Test
    public void showsAnEntryWithAWhiteComponentValue() {
        this.builder(TooltipFlag.NORMAL).entry(this.translations.define("tooltip", "element", "Element"), Component.literal("Fire"));
        Component entry = this.lines.getFirst();
        Assertions.assertThat(entry.getString()).isEqualTo("Element: Fire");
        Assertions.assertThat(entry.toFlatList()).contains(Component.literal("Element").withStyle(ChatFormatting.GRAY), Component.literal("Fire").withStyle(ChatFormatting.WHITE));
    }

    @Test
    public void keepsTheColorOfAColoredComponentValue() {
        this.builder(TooltipFlag.NORMAL).entry(this.translations.define("tooltip", "element", "Element"), Component.literal("Fire").withStyle(ChatFormatting.RED));
        Assertions.assertThat(this.lines.getFirst().toFlatList()).contains(Component.literal("Fire").withStyle(ChatFormatting.RED));
    }

    @Test
    public void leavesTheGivenComponentValueUnchanged() {
        MutableComponent value = Component.literal("Fire");
        this.builder(TooltipFlag.NORMAL).entry(this.translations.define("tooltip", "element", "Element"), value);
        Assertions.assertThat(value.getStyle()).isEqualTo(Style.EMPTY);
    }

    @Test
    public void showsAnEntryWithAWhiteTranslatableValue() {
        this.builder(TooltipFlag.NORMAL).entry(this.translations.define("tooltip", "element", "Element"), this.translations.define("element", "water", "Water"));
        Component entry = this.lines.getFirst();
        Assertions.assertThat(entry.getString()).isEqualTo("Element: Water");
        Assertions.assertThat(entry.toFlatList()).contains(Component.literal("Element").withStyle(ChatFormatting.GRAY), Component.literal("Water").withStyle(ChatFormatting.WHITE));
    }

    @Test
    public void addsAnEmptyLine() {
        this.builder(TooltipFlag.NORMAL).blank();
        Assertions.assertThat(this.lines).extracting(Component::getString).containsExactly("");
    }

    @Test
    public void runsTheShiftSectionWhileShiftIsHeld() {
        this.builder(new ShiftHeld()).onShift(section -> section.line(Component.literal("Details")));
        Assertions.assertThat(this.lines).extracting(Component::getString).containsExactly("Details");
    }

    @Test
    public void hintsAtTheSneakKeyWithoutShift() {
        this.builder(TooltipFlag.NORMAL).onShift(section -> section.line(Component.literal("Details")));
        Assertions.assertThat(this.lines).hasSize(1);
        Component hint = this.lines.getFirst();
        Assertions.assertThat(hint.getStyle().getColor()).isEqualTo(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY));
        Assertions.assertThat(hint.getContents()).isInstanceOfSatisfying(TranslatableContents.class, contents -> {
            Assertions.assertThat(contents.getKey()).isEqualTo("tooltip.nexus.hold_shift");
            Assertions.assertThat(contents.getArgs()).containsExactly(Component.keybind("key.sneak"));
        });
    }

    @Test
    public void runsTheAdvancedSectionWithAdvancedTooltips() {
        this.builder(TooltipFlag.ADVANCED).onAdvanced(section -> section.line(Component.literal("Details")));
        Assertions.assertThat(this.lines).extracting(Component::getString).containsExactly("Details");
    }

    @Test
    public void skipsTheAdvancedSectionWithoutAdvancedTooltips() {
        this.builder(TooltipFlag.NORMAL).onAdvanced(section -> section.line(Component.literal("Details")));
        Assertions.assertThat(this.lines).isEmpty();
    }

    private TooltipBuilder builder(TooltipFlag flag) {
        return new TooltipBuilder(Item.TooltipContext.EMPTY, flag, this.lines::add);
    }

    private static final class ShiftHeld implements TooltipFlag {

        @Override
        public boolean isAdvanced() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean hasShiftDown() {
            return true;
        }
    }
}
