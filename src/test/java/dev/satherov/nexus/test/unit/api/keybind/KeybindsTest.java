package dev.satherov.nexus.test.unit.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

///
/// Checks the keys and categories a mod declares, their translations and the order the keys are kept in.
///
public class KeybindsTest {
    
    private static final Keybinds KEYBINDS = Nexus.getMod().getKeybinds();
    private static final Identifier TOOLS = KeybindsTest.KEYBINDS.category("keybinds_test_tools", "Tools");
    
    private static ListAssert<String> assertEnglishOf(String key) {
        return Assertions.assertThat(Nexus.getMod().getTranslations().getAll()
                .stream()
                .filter(translation -> translation.getKey().equals(key))
                .map(Translation::getEnglish)
                .toList());
    }
    
    @Test
    public void declaresTheCategoryWithItsEnglishName() {
        Assertions.assertThat(KeybindsTest.TOOLS).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "keybinds_test_tools"));
        KeybindsTest.assertEnglishOf("key.category.nexus.keybinds_test_tools").containsExactly("Tools");
    }
    
    @Test
    public void declaresTheKeyWithItsEnglishNameOnRegister() {
        Keybind keybind = KeybindsTest.KEYBINDS.key("keybinds_test_dash", "Dash")
                .category(KeybindsTest.TOOLS)
                .register();
        
        Assertions.assertThat(keybind.getName()).isEqualTo("key.nexus.keybinds_test_dash");
        KeybindsTest.assertEnglishOf("key.nexus.keybinds_test_dash").containsExactly("Dash");
    }
    
    @Test
    public void refusesASecondKeyOfOneName() {
        KeybindsTest.KEYBINDS.key("keybinds_test_twice", "Twice").category(KeybindsTest.TOOLS).register();
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.key("keybinds_test_twice", "Again").category(KeybindsTest.TOOLS).register())
                .isInstanceOf(IllegalStateException.class);
        KeybindsTest.assertEnglishOf("key.nexus.keybinds_test_twice").containsExactly("Twice");
    }
    
    @Test
    public void refusesASecondCategoryOfOneName() {
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.category("keybinds_test_tools", "Other tools")).isInstanceOf(IllegalStateException.class);
        KeybindsTest.assertEnglishOf("key.category.nexus.keybinds_test_tools").containsExactly("Tools");
    }
    
    @Test
    public void refusesAKeyNameNoIdentifierPathTakes() {
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.key("Keybinds Test", "Invalid")).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesACategoryNameNoIdentifierPathTakes() {
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.category("Keybinds Test", "Invalid")).isInstanceOf(IllegalArgumentException.class);
        KeybindsTest.assertEnglishOf("key.category.nexus.Keybinds Test").isEmpty();
    }
    
    @Test
    public void declaresTheDefaultCategoryOnceAndOnlyWhenAKeyUsesIt() {
        Identifier main = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "main");
        String displayName = Nexus.getMod().getContainer().getModInfo().getDisplayName();
        
        KeybindsTest.KEYBINDS.key("keybinds_test_default_opened", "Opened").onClient(() -> () -> { });
        KeybindsTest.KEYBINDS.key("keybinds_test_default_elsewhere", "Elsewhere").category(KeybindsTest.TOOLS).register();
        KeybindsTest.assertEnglishOf("key.category.nexus.main").isEmpty();
        Assertions.assertThat(KeybindsTest.KEYBINDS.getCategories()).doesNotContain(main);
        
        KeybindsTest.KEYBINDS.key("keybinds_test_default_first", "First").register();
        KeybindsTest.KEYBINDS.key("keybinds_test_default_second", "Second").register();
        KeybindsTest.assertEnglishOf("key.category.nexus.main").containsExactly(displayName);
        Assertions.assertThat(KeybindsTest.KEYBINDS.getCategories()).containsOnlyOnce(main);
    }
    
    @Test
    public void keepsTheKeysInDeclarationOrder() {
        Keybind first = KeybindsTest.KEYBINDS.key("keybinds_test_order_first", "First").category(KeybindsTest.TOOLS).register();
        Keybind second = KeybindsTest.KEYBINDS.key("keybinds_test_order_second", "Second").category(KeybindsTest.TOOLS).register();
        Keybind third = KeybindsTest.KEYBINDS.key("keybinds_test_order_third", "Third").category(KeybindsTest.TOOLS).register();
        Assertions.assertThat(KeybindsTest.KEYBINDS.getAll()).containsSubsequence(first, second, third);
    }
}
