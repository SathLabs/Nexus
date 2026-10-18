package dev.satherov.nexus.test.game.api.registry.tab;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

///
/// Checks the titles, icons, and contents of the creative tabs that tab builders register.
///
public class TabBuilderSamples {
    
    private static final Comparator<ItemEntry<? extends Item>> REVERSED = (first, second) -> second.getName().compareTo(first.getName());
    
    private static @Nullable TabEntry sorted;
    private static @Nullable TabEntry listed;
    private static @Nullable ItemEntry<Item> sortedA;
    private static @Nullable ItemEntry<Item> sortedB;
    private static @Nullable ItemEntry<Item> sortedC;
    private static @Nullable ItemEntry<Item> rejected;
    private static @Nullable ItemEntry<Item> listedA;
    private static @Nullable ItemEntry<Item> listedB;
    private static @Nullable TabEntry doubled;
    private static @Nullable ItemEntry<Item> doubledItem;
    
    @ServerTest
    public static void titlesTheTabWithItsKey(GameTestHelper helper) {
        TabEntry sorted = Objects.requireNonNull(TabBuilderSamples.sorted);
        Component title = sorted.value().getDisplayName();
        helper.assertValueEqual(sorted.getTranslation().getKey(), "itemGroup.nexus.tab_builder_samples_sorted", "translation key");
        helper.assertTrue(
                title.getContents() instanceof TranslatableContents contents && contents.getKey().equals(sorted.getTranslation().getKey()),
                "The title is not the translation of the tab"
        );
        helper.assertValueEqual(title.getString(), "Sorted Samples", "title");
        helper.succeed();
    }
    
    @ServerTest
    public static void editsTheVanillaBuilderAfterTheTitle(GameTestHelper helper) {
        helper.assertValueEqual(Objects.requireNonNull(TabBuilderSamples.listed).value().getDisplayName().getString(), "Edited Title", "title");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheGivenIcon(GameTestHelper helper) {
        helper.assertTrue(Objects.requireNonNull(TabBuilderSamples.sorted).value().getIconItem().is(Items.EMERALD), "The icon is not the given item");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheFirstItemWithoutAnIcon(GameTestHelper helper) {
        TabBuilderSamples.buildTabs(helper);
        ItemStack icon = Objects.requireNonNull(TabBuilderSamples.listed).value().getIconItem();
        helper.assertTrue(Objects.requireNonNull(TabBuilderSamples.listedB).is(icon), "The icon is not the first item of the tab");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheEntriesThePredicateAcceptsInOrder(GameTestHelper helper) {
        TabBuilderSamples.buildTabs(helper);
        helper.assertValueEqual(
                Objects.requireNonNull(TabBuilderSamples.sorted).getItems(),
                List.of(
                        Objects.requireNonNull(TabBuilderSamples.sortedC),
                        Objects.requireNonNull(TabBuilderSamples.sortedB),
                        Objects.requireNonNull(TabBuilderSamples.sortedA)
                ),
                "items"
        );
        helper.assertFalse(Objects.requireNonNull(TabBuilderSamples.sorted).getItems().contains(TabBuilderSamples.rejected), "The tab shows the rejected item");
        helper.succeed();
    }
    
    @ServerTest
    public static void addsTheExtraStacksAfterTheModItems(GameTestHelper helper) {
        TabBuilderSamples.buildTabs(helper);
        helper.assertValueEqual(
                Objects.requireNonNull(TabBuilderSamples.sorted).value().getDisplayItems().stream().map(ItemStack::getItem).toList(),
                List.of(
                        Objects.requireNonNull(TabBuilderSamples.sortedC).asItem(),
                        Objects.requireNonNull(TabBuilderSamples.sortedB).asItem(),
                        Objects.requireNonNull(TabBuilderSamples.sortedA).asItem(),
                        Items.DIAMOND
                ),
                "display items"
        );
        helper.succeed();
    }
    
    @ServerTest
    public static void ordersTheEntriesRecordedInTheTab(GameTestHelper helper) {
        TabBuilderSamples.buildTabs(helper);
        helper.assertValueEqual(
                Objects.requireNonNull(TabBuilderSamples.listed).getItems(),
                List.of(Objects.requireNonNull(TabBuilderSamples.listedB), Objects.requireNonNull(TabBuilderSamples.listedA)),
                "items"
        );
        helper.succeed();
    }
    
    @ServerTest
    public static void showsAnItemTheTabAlreadyHasOnce(GameTestHelper helper) {
        TabBuilderSamples.buildTabs(helper);
        TabEntry doubled = Objects.requireNonNull(TabBuilderSamples.doubled);
        ItemEntry<Item> item = Objects.requireNonNull(TabBuilderSamples.doubledItem);
        helper.assertValueEqual(doubled.value().getDisplayItems().stream().filter(item::is).count(), 1L, "number of stacks of the item");
        helper.assertValueEqual(doubled.getItems(), List.of(item), "items");
        helper.succeed();
    }
    
    private static void buildTabs(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            TabEntry sorted = registrations.tab("tab_builder_samples_sorted", "Sorted Samples")
                    .icon(Items.EMERALD)
                    .contents(entry -> entry.getName().startsWith("tab_builder_samples_sorted_"))
                    .order(TabBuilderSamples.REVERSED)
                    .extra((_, output) -> output.accept(Items.DIAMOND))
                    .register();
            TabEntry listed = registrations.tab("tab_builder_samples_listed", "Listed Samples")
                    .order(TabBuilderSamples.REVERSED)
                    .properties(builder -> builder.title(Component.literal("Edited Title")))
                    .register();
            
            TabBuilderSamples.sorted = sorted;
            TabBuilderSamples.listed = listed;
            TabBuilderSamples.sortedA = registrations.item("tab_builder_samples_sorted_a", "Sorted A").noTab().register();
            TabBuilderSamples.sortedB = registrations.item("tab_builder_samples_sorted_b", "Sorted B").noTab().register();
            TabBuilderSamples.sortedC = registrations.item("tab_builder_samples_sorted_c", "Sorted C").noTab().register();
            TabBuilderSamples.rejected = registrations.item("tab_builder_samples_rejected", "Rejected").tab(sorted).register();
            TabBuilderSamples.listedA = registrations.item("tab_builder_samples_listed_a", "Listed A").tab(listed).register();
            TabBuilderSamples.listedB = registrations.item("tab_builder_samples_listed_b", "Listed B").tab(listed).register();
            
            TabEntry doubled = registrations.tab("tab_builder_samples_doubled", "Doubled Samples")
                    .properties(builder -> builder.displayItems((_, output) -> output.accept(Objects.requireNonNull(TabBuilderSamples.doubledItem))))
                    .extra((_, output) -> output.accept(Objects.requireNonNull(TabBuilderSamples.doubledItem)))
                    .register();
            TabBuilderSamples.doubled = doubled;
            TabBuilderSamples.doubledItem = registrations.item("tab_builder_samples_doubled_item", "Doubled Item").tab(doubled).register();
        }
    }
}
