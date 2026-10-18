package dev.satherov.nexus.test.game.api.registry.item;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

///
/// Checks the items that item builders register, their stacks, their translations, and the tabs they show in.
///
public class ItemBuilderSamples {
    
    private static @Nullable TabEntry tab;
    private static @Nullable ItemEntry<Item> tabbed;
    private static @Nullable ItemEntry<Item> vanilla;
    private static @Nullable ItemEntry<Item> hidden;
    private static @Nullable ItemEntry<Item> defaulted;
    private static @Nullable ItemEntry<Item> renamed;
    private static @Nullable ItemEntry<Item> edited;
    private static @Nullable ItemEntry<Item> replaced;
    private static @Nullable ItemEntry<Item> described;
    
    @ServerTest
    public static void createsStacksOfTheItem(GameTestHelper helper) {
        ItemEntry<Item> tabbed = Objects.requireNonNull(ItemBuilderSamples.tabbed);
        ItemStack one = tabbed.stack();
        helper.assertValueEqual(one.getItem(), tabbed.asItem(), "item of the stack");
        helper.assertValueEqual(one.getCount(), 1, "count of the stack of one");
        helper.assertValueEqual(tabbed.stack(5).getCount(), 5, "count of the stack of five");
        helper.assertTrue(tabbed.is(one), "The entry is not the item of its own stack");
        helper.assertFalse(tabbed.is(new ItemStack(Items.STICK)), "The entry is the item of a stick");
        helper.succeed();
    }
    
    @ServerTest
    public static void namesTheItemAfterItsKey(GameTestHelper helper) {
        ItemEntry<Item> tabbed = Objects.requireNonNull(ItemBuilderSamples.tabbed);
        helper.assertValueEqual(tabbed.getTranslation().getKey(), "item.nexus.item_builder_samples_tabbed", "translation key");
        helper.assertValueEqual(tabbed.getTranslation().getEnglish(), "Tabbed Sample", "English name");
        helper.assertValueEqual(tabbed.asItem().getDescriptionId(), "item.nexus.item_builder_samples_tabbed", "description id");
        helper.succeed();
    }
    
    @ServerTest
    public static void namedReplacesTheEnglishName(GameTestHelper helper) {
        ItemEntry<Item> renamed = Objects.requireNonNull(ItemBuilderSamples.renamed);
        helper.assertValueEqual(renamed.getTranslation().getKey(), "item.nexus.item_builder_samples_renamed", "translation key");
        helper.assertValueEqual(renamed.getTranslation().getEnglish(), "Renamed Sample", "English name");
        helper.assertFalse(
                Nexus.getMod().getTranslations().getAll().stream().map(Translation::getEnglish).anyMatch("Original Sample"::equals),
                "The English name before the rename was declared"
        );
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesThePropertiesEditsInOrder(GameTestHelper helper) {
        ItemStack stack = Objects.requireNonNull(ItemBuilderSamples.edited).stack();
        helper.assertValueEqual(stack.getMaxStackSize(), 16, "maximum stack size");
        helper.assertValueEqual(stack.get(DataComponents.RARITY), Rarity.RARE, "rarity");
        helper.succeed();
    }
    
    @ServerTest
    public static void replacingThePropertiesDropsEarlierEdits(GameTestHelper helper) {
        ItemStack stack = Objects.requireNonNull(ItemBuilderSamples.replaced).stack();
        helper.assertValueEqual(stack.getMaxStackSize(), 64, "maximum stack size");
        helper.assertValueEqual(stack.get(DataComponents.RARITY), Rarity.RARE, "rarity");
        helper.succeed();
    }
    
    @ServerTest
    public static void anEditOfTheDescriptionWinsOverTheTranslation(GameTestHelper helper) {
        ItemEntry<Item> described = Objects.requireNonNull(ItemBuilderSamples.described);
        helper.assertValueEqual(described.asItem().getDescriptionId(), "item.nexus.edited_description", "description id");
        helper.assertValueEqual(described.getTranslation().getKey(), "item.nexus.item_builder_samples_described", "translation key");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheItemInTheGivenTab(GameTestHelper helper) {
        ItemBuilderSamples.buildTabs(helper);
        TabEntry tab = Objects.requireNonNull(ItemBuilderSamples.tab);
        ItemEntry<Item> tabbed = Objects.requireNonNull(ItemBuilderSamples.tabbed);
        helper.assertValueEqual(tabbed.getTabs(), Set.of(tab.getKey()), "tabs");
        helper.assertTrue(tab.getItems().contains(tabbed), "The tab doesn't list the item");
        helper.assertTrue(tab.value().contains(tabbed.stack()), "The tab doesn't show the item");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheItemInAVanillaTab(GameTestHelper helper) {
        ItemBuilderSamples.buildTabs(helper);
        ItemStack stack = Objects.requireNonNull(ItemBuilderSamples.vanilla).stack();
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INGREDIENTS).contains(stack), "The vanilla tab doesn't show the item");
        helper.assertFalse(Objects.requireNonNull(ItemBuilderSamples.tab).value().contains(stack), "The tab of the mod shows the item of the vanilla tab");
        helper.succeed();
    }
    
    @ServerTest
    public static void keepsAnItemWithoutTabsOutOfEveryTab(GameTestHelper helper) {
        ItemBuilderSamples.buildTabs(helper);
        ItemEntry<Item> hidden = Objects.requireNonNull(ItemBuilderSamples.hidden);
        ItemStack stack = hidden.stack();
        helper.assertTrue(hidden.getTabs().isEmpty(), "The item without tabs has tabs");
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.stream().noneMatch(tab -> tab.contains(stack)), "A tab shows the item without tabs");
        helper.succeed();
    }
    
    @ServerTest
    public static void recordsAnItemWithoutAGivenTabInTheFirstTabOfTheMod(GameTestHelper helper) {
        Registrations registrations = Nexus.getMod().getRegistrations();
        helper.assertValueEqual(
                Objects.requireNonNull(ItemBuilderSamples.defaulted).getTabs(),
                Set.of(registrations.getAll(Registries.CREATIVE_MODE_TAB).getFirst().getKey()),
                "tabs"
        );
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
            TabEntry tab = registrations.tab("item_builder_samples_tab", "Item Builder Samples").register();
            ItemBuilderSamples.tab = tab;
            ItemBuilderSamples.tabbed = registrations.item("item_builder_samples_tabbed", "Tabbed Sample").tab(tab).register();
            ItemBuilderSamples.vanilla = registrations.item("item_builder_samples_vanilla", "Vanilla Sample").tab(CreativeModeTabs.INGREDIENTS).register();
            ItemBuilderSamples.hidden = registrations.item("item_builder_samples_hidden", "Hidden Sample").noTab().register();
            ItemBuilderSamples.defaulted = registrations.item("item_builder_samples_defaulted", "Defaulted Sample").register();
            ItemBuilderSamples.renamed = registrations.item("item_builder_samples_renamed", "Original Sample")
                    .named("Renamed Sample")
                    .noTab()
                    .register();
            ItemBuilderSamples.edited = registrations.item("item_builder_samples_edited", "Edited Sample")
                    .properties(properties -> properties.stacksTo(16))
                    .properties(properties -> properties.rarity(Rarity.RARE))
                    .noTab()
                    .register();
            ItemBuilderSamples.replaced = registrations.item("item_builder_samples_replaced", "Replaced Sample")
                    .properties(properties -> properties.stacksTo(16))
                    .properties(new Item.Properties().rarity(Rarity.RARE))
                    .noTab()
                    .register();
            ItemBuilderSamples.described = registrations.item("item_builder_samples_described", "Described Sample")
                    .properties(properties -> properties.overrideDescription("item.nexus.edited_description"))
                    .noTab()
                    .register();
        }
    }
}
