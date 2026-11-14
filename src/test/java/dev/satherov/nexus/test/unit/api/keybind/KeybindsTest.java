package dev.satherov.nexus.test.unit.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

///
/// Checks the keys and categories a mod declares, their translations, their payloads and the order the keys are kept in.
///
public class KeybindsTest {
    
    private static final Keybinds KEYBINDS = Nexus.getMod().getKeybinds();
    private static final Identifier MAIN = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "main");
    
    private static @Nullable Identifier tools;
    private static @Nullable Throwable secondCategoryRefusal;
    private static @Nullable Keybind dash;
    private static @Nullable Throwable secondKeyRefusal;
    private static boolean mainDeclaredBeforeItsFirstKey;
    private static List<Keybind> ordered = List.of();
    private static @Nullable Keybind unsynced;
    private static @Nullable Keybind existing;
    private static @Nullable Throwable payloadClashRefusal;
    
    private static ListAssert<String> assertEnglishOf(String key) {
        return Assertions.assertThat(Nexus.getMod().getTranslations().getAll()
                .stream()
                .filter(translation -> translation.getKey().equals(key))
                .map(Translation::getEnglish)
                .toList());
    }
    
    private static boolean isTranslated(String key) {
        return Nexus.getMod().getTranslations().getAll().stream().anyMatch(translation -> translation.getKey().equals(key));
    }
    
    @Test
    public void declaresTheCategoryWithItsEnglishName() {
        Assertions.assertThat(KeybindsTest.tools).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "keybinds_test_tools"));
        KeybindsTest.assertEnglishOf("key.category.nexus.keybinds_test_tools").containsExactly("Tools");
    }
    
    @Test
    public void declaresTheKeyWithItsEnglishNameOnRegister() {
        Assertions.assertThat(Objects.requireNonNull(KeybindsTest.dash).getName()).isEqualTo("key.nexus.keybinds_test_dash");
        KeybindsTest.assertEnglishOf("key.nexus.keybinds_test_dash").containsExactly("Dash");
    }
    
    @Test
    public void refusesASecondKeyOfOneName() {
        Assertions.assertThat(KeybindsTest.secondKeyRefusal).isInstanceOf(IllegalStateException.class);
        KeybindsTest.assertEnglishOf("key.nexus.keybinds_test_twice").containsExactly("Twice");
    }
    
    @Test
    public void refusesASecondCategoryOfOneName() {
        Assertions.assertThat(KeybindsTest.secondCategoryRefusal).isInstanceOf(IllegalStateException.class);
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
        Assertions.assertThat(KeybindsTest.mainDeclaredBeforeItsFirstKey).isFalse();
        KeybindsTest.assertEnglishOf("key.category.nexus.main").containsExactly(Nexus.getMod().getContainer().getModInfo().getDisplayName());
        Assertions.assertThat(KeybindsTest.KEYBINDS.getCategories()).containsOnlyOnce(KeybindsTest.MAIN);
    }
    
    @Test
    public void keepsTheKeysInDeclarationOrder() {
        Assertions.assertThat(KeybindsTest.ordered).hasSize(3);
        Assertions.assertThat(KeybindsTest.KEYBINDS.getAll()).containsSubsequence(KeybindsTest.ordered);
    }
    
    @Test
    public void refusesTheServerReadersWithoutSyncHeld() {
        Keybind unsynced = Objects.requireNonNull(KeybindsTest.unsynced);
        //noinspection DataFlowIssue The readers refuse before they read the player.
        ServerPlayer player = null;
        
        Assertions.assertThatThrownBy(() -> unsynced.isDown(player)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> unsynced.getHeldTicks(player)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> unsynced.getProgress(player)).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesASecondServerAction() {
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.key("keybinds_test_two_actions", "Two actions").onServer(_ -> { }).onServer(_ -> { }))
                .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesTwoExistingMappingsOnOnePayload() {
        Assertions.assertThat(KeybindsTest.payloadClashRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key.keybinds_test_Clash")
                .hasMessageContaining("key.keybinds_test_clash");
    }
    
    @Test
    public void declaresNothingForAnExistingMapping() {
        Assertions.assertThat(Objects.requireNonNull(KeybindsTest.existing).getName()).isEqualTo("key.keybinds_test_existing");
        Assertions.assertThat(KeybindsTest.isTranslated("key.keybinds_test_existing")).isFalse();
    }
    
    @Test
    public void refusesAKeyAndACategoryAfterThePayloadHandlersEvent() {
        Identifier tools = Objects.requireNonNull(KeybindsTest.tools);
        
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.key("keybinds_test_late", "Late").category(tools).register()).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.existing("key.keybinds_test_late").register()).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> KeybindsTest.KEYBINDS.category("keybinds_test_late", "Late")).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(KeybindsTest.isTranslated("key.nexus.keybinds_test_late")).isFalse();
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier tools = keybinds.category("keybinds_test_tools", "Tools");
            KeybindsTest.tools = tools;
            KeybindsTest.secondCategoryRefusal = Assertions.catchThrowable(() -> keybinds.category("keybinds_test_tools", "Other tools"));
            
            KeybindsTest.dash = keybinds.key("keybinds_test_dash", "Dash").category(tools).register();
            keybinds.key("keybinds_test_twice", "Twice").category(tools).register();
            KeybindsTest.secondKeyRefusal = Assertions.catchThrowable(() -> keybinds.key("keybinds_test_twice", "Again").category(tools).register());
            
            keybinds.key("keybinds_test_default_opened", "Opened").onClient(() -> () -> { });
            keybinds.key("keybinds_test_default_elsewhere", "Elsewhere").category(tools).register();
            KeybindsTest.mainDeclaredBeforeItsFirstKey = keybinds.getCategories().contains(KeybindsTest.MAIN) || KeybindsTest.isTranslated("key.category.nexus.main");
            keybinds.key("keybinds_test_default_first", "First").register();
            keybinds.key("keybinds_test_default_second", "Second").register();
            
            KeybindsTest.ordered = List.of(
                    keybinds.key("keybinds_test_order_first", "First").category(tools).register(),
                    keybinds.key("keybinds_test_order_second", "Second").category(tools).register(),
                    keybinds.key("keybinds_test_order_third", "Third").category(tools).register()
            );
            
            KeybindsTest.unsynced = keybinds.key("keybinds_test_unsynced", "Unsynced").category(tools).onServer(_ -> { }).register();
            KeybindsTest.existing = keybinds.existing("key.keybinds_test_existing").onServer(_ -> { }).register();
            keybinds.existing("key.keybinds_test_Clash").onServer(_ -> { }).register();
            KeybindsTest.payloadClashRefusal = Assertions.catchThrowable(() -> keybinds.existing("key.keybinds_test_clash").syncHeld().register());
        }
    }
}
