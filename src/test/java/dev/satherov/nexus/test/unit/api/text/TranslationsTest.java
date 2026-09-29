package dev.satherov.nexus.test.unit.api.text;

import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.api.text.Translations;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the keys a table declares and the order it keeps them in.
///
public class TranslationsTest {
    
    @Test
    public void buildsTheKeyFromCategoryModIdAndName() {
        Translations translations = new Translations("examplemod");
        Assertions.assertThat(translations.define("tooltip", "mana", "Mana").getKey()).isEqualTo("tooltip.examplemod.mana");
    }
    
    @Test
    public void namesAnEnumKeyAfterTheLowercasedConstant() {
        Translations translations = new Translations("examplemod");
        Assertions.assertThat(translations.define("element", Element.WATER_SOURCE, "Water source").getKey()).isEqualTo("element.examplemod.water_source");
    }
    
    @Test
    public void keepsTheSameNameApartAcrossCategories() {
        Translations translations = new Translations("examplemod");
        translations.define("tooltip", "mana", "Mana");
        Assertions.assertThat(translations.define("message", "mana", "Mana").getKey()).isEqualTo("message.examplemod.mana");
    }
    
    @Test
    public void refusesADuplicateKeyAndKeepsTheFirst() {
        Translations translations = new Translations("examplemod");
        translations.define("tooltip", "mana", "Mana");
        Assertions.assertThatThrownBy(() -> translations.define("tooltip", "mana", "Other mana")).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(translations.getAll()).extracting(Translation::getEnglish).containsExactly("Mana");
    }
    
    @Test
    public void refusesAnEnumKeyThatIsAlreadyDeclaredByName() {
        Translations translations = new Translations("examplemod");
        translations.define("element", "water_source", "Water source");
        Assertions.assertThatThrownBy(() -> translations.define("element", Element.WATER_SOURCE, "Water source")).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void listsEveryKeyInDeclarationOrder() {
        Translations translations = new Translations("examplemod");
        translations.define("tooltip", "mana", "Mana");
        translations.define("element", Element.WATER_SOURCE, "Water source");
        translations.define("message", "cost", "Costs {{cost}} mana");
        Assertions.assertThat(translations.getAll())
                .extracting(Translation::getKey)
                .containsExactly("tooltip.examplemod.mana", "element.examplemod.water_source", "message.examplemod.cost");
    }
    
    private enum Element {
        WATER_SOURCE
    }
}
