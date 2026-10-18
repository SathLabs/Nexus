package dev.satherov.nexus.test.unit.internal.registry.tab;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.tab.TabContents;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

///
/// Checks which item entries a creative tab shows and in which order.
///
public class TabContentsTest {
    
    private static final ResourceKey<CreativeModeTab> OWN = Nexus.getMod().key(Registries.CREATIVE_MODE_TAB, "tab_contents_test_own");
    private static final ResourceKey<CreativeModeTab> OTHER = Nexus.getMod().key(Registries.CREATIVE_MODE_TAB, "tab_contents_test_other");
    private static final Comparator<ItemEntry<? extends Item>> REVERSED = (first, second) -> second.getName().compareTo(first.getName());
    
    private static @Nullable TabEntry tab;
    
    @Test
    public void showsTheEntriesRecordedInTheTabInRegistrationOrder() {
        ItemEntry<Item> first = TabContentsTest.entry("first", Set.of(TabContentsTest.OWN));
        ItemEntry<Item> other = TabContentsTest.entry("other", Set.of(TabContentsTest.OTHER));
        ItemEntry<Item> both = TabContentsTest.entry("both", Set.of(TabContentsTest.OTHER, TabContentsTest.OWN));
        ItemEntry<Item> hidden = TabContentsTest.entry("hidden", Set.of());
        List<ItemEntry<? extends Item>> entries = List.of(first, other, both, hidden);
        
        Assertions.assertThat(TabContents.select(entries, TabContentsTest.OWN, null, null)).containsExactly(first, both);
    }
    
    @Test
    public void showsTheEntriesRecordedInAVanillaTab() {
        ItemEntry<Item> vanilla = TabContentsTest.entry("vanilla", Set.of(CreativeModeTabs.INGREDIENTS));
        ItemEntry<Item> own = TabContentsTest.entry("own", Set.of(TabContentsTest.OWN));
        
        Assertions.assertThat(TabContents.select(List.of(vanilla, own), CreativeModeTabs.INGREDIENTS, null, null)).containsExactly(vanilla);
    }
    
    @Test
    public void showsTheEntriesThePredicateAcceptsWhateverTheirTabs() {
        ItemEntry<Item> recorded = TabContentsTest.entry("recorded_rejected", Set.of(TabContentsTest.OWN));
        ItemEntry<Item> elsewhere = TabContentsTest.entry("elsewhere_accepted", Set.of(TabContentsTest.OTHER));
        ItemEntry<Item> hidden = TabContentsTest.entry("hidden_accepted", Set.of());
        List<ItemEntry<? extends Item>> entries = List.of(recorded, elsewhere, hidden);
        
        Assertions.assertThat(TabContents.select(entries, TabContentsTest.OWN, entry -> entry.getName().endsWith("_accepted"), null)).containsExactly(elsewhere, hidden);
    }
    
    @Test
    public void ordersTheEntriesThePredicateAccepts() {
        ItemEntry<Item> a = TabContentsTest.entry("a", Set.of());
        ItemEntry<Item> b = TabContentsTest.entry("b", Set.of());
        ItemEntry<Item> c = TabContentsTest.entry("c", Set.of());
        
        Assertions.assertThat(TabContents.select(List.of(b, a, c), TabContentsTest.OWN, _ -> true, TabContentsTest.REVERSED)).containsExactly(c, b, a);
    }
    
    @Test
    public void ordersTheEntriesRecordedInTheTab() {
        ItemEntry<Item> a = TabContentsTest.entry("a", Set.of(TabContentsTest.OWN));
        ItemEntry<Item> b = TabContentsTest.entry("b", Set.of(TabContentsTest.OTHER));
        ItemEntry<Item> c = TabContentsTest.entry("c", Set.of(TabContentsTest.OWN));
        
        Assertions.assertThat(TabContents.select(List.of(a, b, c), TabContentsTest.OWN, null, TabContentsTest.REVERSED)).containsExactly(c, a);
    }
    
    @Test
    public void keepsRegistrationOrderBetweenEntriesTheOrderRanksEqual() {
        ItemEntry<Item> late = TabContentsTest.entry("late", Set.of(TabContentsTest.OWN));
        ItemEntry<Item> second = TabContentsTest.entry("second", Set.of(TabContentsTest.OWN));
        ItemEntry<Item> first = TabContentsTest.entry("first", Set.of(TabContentsTest.OWN));
        Comparator<ItemEntry<? extends Item>> lateLast = Comparator.comparing(entry -> entry.getName().endsWith("_late"));
        
        Assertions.assertThat(TabContents.select(List.of(late, second, first), TabContentsTest.OWN, null, lateLast)).containsExactly(second, first, late);
    }
    
    @Test
    public void showsAnEntryWithoutTabsInTheFirstTabOfTheMod() {
        List<RegistryEntry<CreativeModeTab, ? extends CreativeModeTab>> tabs = Nexus.getMod().getRegistrations().getAll(Registries.CREATIVE_MODE_TAB);
        Assertions.assertThat(tabs).contains(Objects.requireNonNull(TabContentsTest.tab));
        ResourceKey<CreativeModeTab> first = tabs.getFirst().getKey();
        ItemEntry<Item> defaulted = TabContentsTest.entry("defaulted", null);
        
        Assertions.assertThat(defaulted.getTabs()).containsExactly(first);
        Assertions.assertThat(TabContents.select(List.of(defaulted), first, null, null)).containsExactly(defaulted);
        Assertions.assertThat(TabContents.select(List.of(defaulted), TabContentsTest.OTHER, null, null)).isEmpty();
    }
    
    private static ItemEntry<Item> entry(String name, @Nullable Set<ResourceKey<CreativeModeTab>> tabs) {
        Registrations registrations = Nexus.getMod().getRegistrations();
        ItemBuilder<Item, Registrations> builder = registrations.item("tab_contents_test_" + name, name);
        return new ItemEntry<>(builder.getKey(), builder, new Translation("item.nexus." + builder.getName(), name), tabs);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            TabContentsTest.tab = Nexus.getMod().getRegistrations().tab("tab_contents_test_tab", "Tab Contents Test").register();
        }
    }
}
