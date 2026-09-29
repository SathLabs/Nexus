package dev.satherov.nexus.test.unit.api.text;

import dev.satherov.nexus.api.text.Translation;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

///
/// Checks how a translation resolves its placeholders and which arguments its components accept.
///
public class TranslationTest {
    
    private static final String KEY = "tooltip.examplemod.mana";
    
    @Test
    public void writesEveryPlaceholderAsItsPosition() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} of {{max}} mana");
        Assertions.assertThat(translation.getPattern()).isEqualTo("Costs %1$s of %2$s mana");
    }
    
    @Test
    public void givesARepeatedPlaceholderTheSamePosition() {
        Translation translation = new Translation(TranslationTest.KEY, "{{cost}} of {{max}}, {{cost}} again");
        Assertions.assertThat(translation.getPattern()).isEqualTo("%1$s of %2$s, %1$s again");
    }
    
    @Test
    public void escapesALiteralPercent() {
        Translation translation = new Translation(TranslationTest.KEY, "{{chance}}% chance, 100% sure");
        Assertions.assertThat(translation.getPattern()).isEqualTo("%1$s%% chance, 100%% sure");
    }
    
    @Test
    public void keepsTextWithoutPlaceholders() {
        Translation translation = new Translation(TranslationTest.KEY, "Mana");
        Assertions.assertThat(translation.getPattern()).isEqualTo("Mana");
        Assertions.assertThat(translation.getPlaceholders()).isEmpty();
    }
    
    @Test
    public void listsPlaceholdersInOrderOfFirstAppearance() {
        Translation translation = new Translation(TranslationTest.KEY, "{{second}} before {{first}}, then {{second}}");
        Assertions.assertThat(translation.getPlaceholders()).containsExactly("second", "first");
    }
    
    @Test
    public void refusesOneArgumentForAKeyWithoutPlaceholders() {
        Translation translation = new Translation(TranslationTest.KEY, "Mana");
        Assertions.assertThatThrownBy(() -> translation.with("cost", 5)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesOneArgumentForAKeyWithSeveralPlaceholders() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} of {{max}} mana");
        Assertions.assertThatThrownBy(() -> translation.with("cost", 5)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesOneArgumentUnderAnotherName() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} mana");
        Assertions.assertThatThrownBy(() -> translation.with("max", 5)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesAnArgumentForAnUnknownPlaceholder() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} mana");
        Assertions.assertThatThrownBy(() -> translation.with(Map.of("cost", 5, "max", 10))).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesAPlaceholderWithoutArgument() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} of {{max}} mana");
        Assertions.assertThatThrownBy(() -> translation.with(Map.of("cost", 5))).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void createsAComponentWithoutArguments() {
        Translation translation = new Translation(TranslationTest.KEY, "Mana");
        Assertions.assertThat(translation.component().getContents()).isInstanceOfSatisfying(TranslatableContents.class, contents -> {
            Assertions.assertThat(contents.getKey()).isEqualTo(TranslationTest.KEY);
            Assertions.assertThat(contents.getArgs()).isEmpty();
        });
    }
    
    @Test
    public void createsAComponentWithItsOneArgument() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} mana");
        Assertions.assertThat(translation.with("cost", 5).getContents()).isInstanceOfSatisfying(TranslatableContents.class, contents -> {
            Assertions.assertThat(contents.getKey()).isEqualTo(TranslationTest.KEY);
            Assertions.assertThat(contents.getArgs()).containsExactly(5);
        });
    }
    
    @Test
    public void createsAComponentWithTheArgumentsInPlaceholderOrder() {
        Translation translation = new Translation(TranslationTest.KEY, "{{max}} at most, {{cost}} now");
        Assertions.assertThat(translation.with(Map.of("cost", 5, "max", 10)).getContents()).isInstanceOfSatisfying(TranslatableContents.class, contents -> {
            Assertions.assertThat(contents.getKey()).isEqualTo(TranslationTest.KEY);
            Assertions.assertThat(contents.getArgs()).containsExactly(10, 5);
        });
    }
    
    @Test
    public void showsTheEnglishDefaultWithoutArguments() {
        Translation translation = new Translation(TranslationTest.KEY, "100% sure");
        Assertions.assertThat(translation.component().getString()).isEqualTo("100% sure");
    }
    
    @Test
    public void showsTheEnglishDefaultWithItsOneArgument() {
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} mana");
        Assertions.assertThat(translation.with("cost", 5).getString()).isEqualTo("Costs 5 mana");
    }
    
    @Test
    public void showsAnyOtherArgumentAsItsText() {
        Translation translation = new Translation(TranslationTest.KEY, "Made of {{material}}");
        Assertions.assertThat(translation.with("material", Identifier.fromNamespaceAndPath("examplemod", "crystal")).getString()).isEqualTo("Made of examplemod:crystal");
    }
    
    @Test
    public void showsATranslatableArgumentAsItsComponent() {
        Translation item = new Translation("item.examplemod.crystal", "Crystal");
        Translation translation = new Translation(TranslationTest.KEY, "Costs {{cost}} {{item}}");
        Assertions.assertThat(translation.with(Map.of("cost", 5, "item", item)).getString()).isEqualTo("Costs 5 Crystal");
    }
    
    @Test
    public void showsTheEnglishDefaultIfTheLanguageDoesNotHaveTheKey() {
        Translation translation = new Translation(TranslationTest.KEY, "{{cost}}% of {{max}}, still {{cost}}");
        Assertions.assertThat(translation.with(Map.of("cost", 5, "max", 10)).getString()).isEqualTo("5% of 10, still 5");
    }
}
