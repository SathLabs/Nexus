package dev.satherov.nexus.impl.component;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.path.TagReader;
import dev.satherov.nexus.internal.component.PathReaders;

import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.google.common.collect.Comparators;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

///
/// Utility that registers Nexus's readers of the paths in vanilla components.
///
/// A reader passes exactly the values that would pass if the part that the path points to was encoded to NBT and its tag was tested.
/// A path that the getters of the value can't read that way is declined, so the next reader or the codec of the component reads it.
///
/// The readers are registered after [RegisterPathReadersEvent] is posted, so a reader that a mod registers for the same path is tried first.
/// Reading a value allocates nothing if the reader's cost is DIRECT.
///
@UtilityClass
@ApiStatus.Internal
public class VanillaReaders {
    
    ///
    /// Tests the identifier that the name codec of a registry writes for the given holder against the given comparison.
    ///
    /// If the holder doesn't delegate to a reference holder with a resource key, the identifier will read as absent, since the codec fails on such a holder.
    ///
    /// @param holder     The holder to test.
    /// @param comparison The comparison to test the identifier with.
    ///
    /// @return `true` if the identifier passes.
    ///
    private static boolean testName(Holder<?> holder, Comparison comparison) {
        return holder.getDelegate() instanceof Holder.Reference<?> reference && reference.getKey() instanceof ResourceKey<?> key && comparison.test(key.identifier());
    }
    
    ///
    /// Creates the reader of the given steps in enchantments, which reads the level of the enchantment whose identifier is the only step.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps, or `null` if the steps are not one key that is a full identifier.
    ///
    private static @Nullable PathReader<ItemEnchantments> enchantments(List<ComponentPath.Step> steps) {
        if (
                steps.size() == 1 &&
                steps.getFirst() instanceof ComponentPath.Step.Key(String name) &&
                Identifier.tryParse(name) instanceof Identifier enchantment &&
                enchantment.toString().equals(name)
        ) {
            return new EnchantmentLevel(DeferredHolder.create(ResourceKey.create(Registries.ENCHANTMENT, enchantment)));
        }
        
        return null;
    }
    
    ///
    /// Creates the reader of the given steps in text components.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the plain text if the steps are empty, the reader of the translation key if they're the one key `translate`, or `null` otherwise.
    ///
    private static @Nullable PathReader<Component> name(List<ComponentPath.Step> steps) {
        if (steps.isEmpty()) {
            return NameText.INSTANCE;
        }
        
        if (steps.equals(List.of(new ComponentPath.Step.Key("translate")))) {
            return NameTranslationKey.INSTANCE;
        }
        
        return null;
    }
    
    ///
    /// Creates the reader of the given steps in attribute modifiers, which reads the amount of the modifier at an index.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps, or `null` if the steps are not an index followed by the key `amount`.
    ///
    private static @Nullable PathReader<ItemAttributeModifiers> attributes(List<ComponentPath.Step> steps) {
        if (steps.size() == 2 && steps.getFirst() instanceof ComponentPath.Step.Index(int index) && steps.getLast().equals(new ComponentPath.Step.Key("amount"))) {
            return new AttributeAmount(index);
        }
        
        return null;
    }
    
    ///
    /// Creates the reader of the given steps in container contents, which reads a part of the item stack template in one slot or in any slot.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps, or `null` if the steps are not an index or a wildcard followed by the key `item` and steps that [#item(List)] reads.
    ///
    private static @Nullable PathReader<ItemContainerContents> container(List<ComponentPath.Step> steps) {
        if (steps.size() < 2 || !steps.get(1).equals(new ComponentPath.Step.Key("item")) || !(VanillaReaders.item(steps.subList(2, steps.size())) instanceof PathReader<ItemStackTemplate> reader)) {
            return null;
        }
        
        return switch (steps.getFirst()) {
            case ComponentPath.Step.Index(int index) -> new ContainerSlot(index, reader);
            case ComponentPath.Step.Wildcard _ -> new AnyContainerSlot(reader);
            case ComponentPath.Step.Key _ -> null;
        };
    }
    
    ///
    /// Creates the reader of the given steps in bundle contents, which reads a part of one item stack template or of any item stack template.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps, or `null` if the steps are not an index or a wildcard followed by steps that [#item(List)] reads.
    ///
    private static @Nullable PathReader<BundleContents> bundle(List<ComponentPath.Step> steps) {
        if (steps.isEmpty() || !(VanillaReaders.item(steps.subList(1, steps.size())) instanceof PathReader<ItemStackTemplate> reader)) {
            return null;
        }
        
        return switch (steps.getFirst()) {
            case ComponentPath.Step.Index(int index) -> new BundleItem(index, reader);
            case ComponentPath.Step.Wildcard _ -> new AnyBundleItem(reader);
            case ComponentPath.Step.Key _ -> null;
        };
    }
    
    ///
    /// Creates the reader of the given steps in an item stack template.
    ///
    /// The key `id` reads the identifier of the item, and the key `count` reads the count.
    /// The key `components` followed by the identifier of a component reads the rest of the steps in the value that the patch sets for that component.
    ///
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps, or `null` if they're none of these or the identifier isn't the one that the patch codec writes for a registered persistent component.
    ///
    private static @Nullable PathReader<ItemStackTemplate> item(List<ComponentPath.Step> steps) {
        if (steps.equals(List.of(new ComponentPath.Step.Key("id")))) {
            return ItemId.INSTANCE;
        }
        
        if (steps.equals(List.of(new ComponentPath.Step.Key("count")))) {
            return ItemCount.INSTANCE;
        }
        
        if (
                steps.size() >= 2 &&
                steps.getFirst().equals(new ComponentPath.Step.Key("components")) &&
                steps.get(1) instanceof ComponentPath.Step.Key(String name) &&
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.tryParse(name)) instanceof DataComponentType<?> type &&
                !type.isTransient() &&
                BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) instanceof Identifier key &&
                key.toString().equals(name)
        ) {
            return new ItemComponent<>(type, steps.subList(2, steps.size()));
        }
        
        return null;
    }
    
    ///
    /// Registers every reader of this class to the given event.
    ///
    /// The following readers are registered for a codec, so each one reads every component built on that codec:
    /// - [ItemContainerContents#CODEC]: The reader of container contents.
    /// - [BundleContents#CODEC]: The reader of bundle contents.
    /// - [CustomData#CODEC]: The reader of custom data.
    ///
    /// Should only ever be called from [PathReaders#collect()].
    ///
    /// @param event The event to register the readers to.
    ///
    public static void register(RegisterPathReadersEvent event) {
        event.register(DataComponents.DAMAGE, steps -> steps.isEmpty() ? DamageValue.INSTANCE : null);
        event.register(DataComponents.ENCHANTMENTS, VanillaReaders::enchantments);
        event.register(DataComponents.STORED_ENCHANTMENTS, VanillaReaders::enchantments);
        event.register(DataComponents.CUSTOM_NAME, VanillaReaders::name);
        event.register(DataComponents.ITEM_NAME, VanillaReaders::name);
        event.register(DataComponents.POTION_CONTENTS, steps -> steps.equals(List.of(new ComponentPath.Step.Key("potion"))) ? PotionId.INSTANCE : null);
        event.register(DataComponents.ATTRIBUTE_MODIFIERS, VanillaReaders::attributes);
        event.register(ItemContainerContents.CODEC, VanillaReaders::container);
        event.register(BundleContents.CODEC, VanillaReaders::bundle);
        event.register(CustomData.CODEC, steps -> new CustomDataTag(new TagReader(steps)));
    }
    
    ///
    /// The reader of an item stack's damage at a path without steps.
    ///
    private enum DamageValue implements PathReader<Integer> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the damage as a number.
        ///
        /// Negative damage reads as absent, since the codec fails to encode it.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<Integer> bind(Comparison comparison) {
            return damage -> damage >= 0 && comparison.test(damage);
        }
    }
    
    ///
    /// The reader of the level of one enchantment, which reads a level of `0` as absent.
    ///
    /// @param enchantment The holder of the enchantment, which equals every reference holder with the same resource key.
    ///
    private record EnchantmentLevel(Holder<Enchantment> enchantment) implements PathReader<ItemEnchantments> {
        
        ///
        /// Will always return [PathReader.Cost#SCAN].
        ///
        /// @return [PathReader.Cost#SCAN].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.SCAN;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test that looks up the level of the enchantment in the enchantments.
        ///
        /// The level reads as absent if the stored holder of the enchantment can't be serialized in the registries that encoding uses right now.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemEnchantments> bind(Comparison comparison) {
            return enchantments -> {
                int level = enchantments.getLevel(this.enchantment);
                if (level == 0) {
                    return false;
                }
                
                HolderOwner<Enchantment> owner = Encoding.owner(Registries.ENCHANTMENT);
                if (owner == null) {
                    return false;
                }
                
                for (Holder<Enchantment> stored : enchantments.keySet()) {
                    if (this.enchantment.equals(stored)) {
                        return stored.canSerializeIn(owner) && comparison.test(level);
                    }
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of a text component as plain text.
    ///
    private enum NameText implements PathReader<Component> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the text of a component that the codec writes as plain text.
        ///
        /// A component that doesn't collapse to a string fails because the codec writes it as a compound.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<Component> bind(Comparison comparison) {
            return name -> name.tryCollapseToString() instanceof String text && comparison.test(text);
        }
    }
    
    ///
    /// The reader of the translation key of a text component.
    ///
    private enum NameTranslationKey implements PathReader<Component> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the key of a translatable component.
        ///
        /// A component with other contents fails, because the codec writes a `translate` key only for translatable contents.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<Component> bind(Comparison comparison) {
            return name -> name.getContents() instanceof TranslatableContents translatable && comparison.test(translatable.getKey());
        }
    }
    
    ///
    /// The reader of the identifier of the potion in potion contents.
    ///
    private enum PotionId implements PathReader<PotionContents> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `false`.
        ///
        /// @return `false`.
        ///
        @Override
        public boolean isOrdered() {
            return false;
        }
        
        ///
        /// Creates the test of the identifier of the potion, which fails if the contents don't have a potion.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<PotionContents> bind(Comparison comparison) {
            return contents -> contents.potion().isPresent() && VanillaReaders.testName(contents.potion().get(), comparison);
        }
    }
    
    ///
    /// The reader of the amount of the attribute modifier at one index.
    ///
    /// @param index The index of the modifier.
    ///
    private record AttributeAmount(int index) implements PathReader<ItemAttributeModifiers> {
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the amount, which fails if the index is past the last modifier.
        ///
        /// The amount is tested even if the attribute of the modifier fails to encode, since encoding the amount doesn't encode its sibling.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemAttributeModifiers> bind(Comparison comparison) {
            return attributes -> this.index < attributes.modifiers().size() && comparison.test(attributes.modifiers().get(this.index).modifier().amount());
        }
    }
    
    ///
    /// The reader of a part of the item stack template in one slot of container contents.
    ///
    /// The slots are counted the way the codec writes them, which is only the slots that hold an item.
    ///
    /// @param index  The index of the slot among the slots that hold an item.
    /// @param reader The reader of the part of the item stack template.
    ///
    private record ContainerSlot(int index, PathReader<ItemStackTemplate> reader) implements PathReader<ItemContainerContents> {
        
        ///
        /// Will return the cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        /// @return The cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        @Override
        public PathReader.Cost getCost() {
            return Comparators.max(PathReader.Cost.SCAN, this.reader.getCost());
        }
        
        ///
        /// Will return the same as the reader of the part.
        ///
        /// @return `true` if the reader of the part is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.reader.isOrdered();
        }
        
        ///
        /// Creates the test of the part, which fails if the index is past the last slot that holds an item.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemContainerContents> bind(Comparison comparison) {
            Predicate<ItemStackTemplate> test = this.reader.bind(comparison);
            return contents -> {
                int remaining = this.index;
                for (int slot = 0; slot < contents.items.size(); slot++) {
                    Optional<ItemStackTemplate> item = contents.items.get(slot);
                    if (item.isEmpty()) {
                        continue;
                    }
                    
                    if (remaining == 0) {
                        return test.test(item.get());
                    }
                    
                    remaining--;
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of a part of the item stack template in any slot of container contents.
    ///
    /// @param reader The reader of the part of the item stack template.
    ///
    private record AnyContainerSlot(PathReader<ItemStackTemplate> reader) implements PathReader<ItemContainerContents> {
        
        ///
        /// Will return the cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        /// @return The cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        @Override
        public PathReader.Cost getCost() {
            return Comparators.max(PathReader.Cost.SCAN, this.reader.getCost());
        }
        
        ///
        /// Will return the same as the reader of the part.
        ///
        /// @return `true` if the reader of the part is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.reader.isOrdered();
        }
        
        ///
        /// Creates the test that passes if the part passes in any slot.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemContainerContents> bind(Comparison comparison) {
            Predicate<ItemStackTemplate> test = this.reader.bind(comparison);
            return contents -> {
                for (int slot = 0; slot < contents.items.size(); slot++) {
                    Optional<ItemStackTemplate> item = contents.items.get(slot);
                    if (item.isPresent() && test.test(item.get())) {
                        return true;
                    }
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of a part of the item stack template at one index of bundle contents.
    ///
    /// @param index  The index of the item stack template.
    /// @param reader The reader of the part of the item stack template.
    ///
    private record BundleItem(int index, PathReader<ItemStackTemplate> reader) implements PathReader<BundleContents> {
        
        ///
        /// Will return the cost of the reader of the part.
        ///
        /// @return The cost of the reader of the part.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.reader.getCost();
        }
        
        ///
        /// Will return the same as the reader of the part.
        ///
        /// @return `true` if the reader of the part is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.reader.isOrdered();
        }
        
        ///
        /// Creates the test of the part, which fails if the index is past the last item stack template.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<BundleContents> bind(Comparison comparison) {
            Predicate<ItemStackTemplate> test = this.reader.bind(comparison);
            return bundle -> this.index < bundle.items().size() && test.test(bundle.items().get(this.index));
        }
    }
    
    ///
    /// The reader of a part of any item stack template in bundle contents.
    ///
    /// @param reader The reader of the part of the item stack template.
    ///
    private record AnyBundleItem(PathReader<ItemStackTemplate> reader) implements PathReader<BundleContents> {
        
        ///
        /// Will return the cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        /// @return The cost of the reader of the part, but at least [PathReader.Cost#SCAN].
        ///
        @Override
        public PathReader.Cost getCost() {
            return Comparators.max(PathReader.Cost.SCAN, this.reader.getCost());
        }
        
        ///
        /// Will return the same as the reader of the part.
        ///
        /// @return `true` if the reader of the part is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.reader.isOrdered();
        }
        
        ///
        /// Creates the test that passes if the part of any item stack template passes.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<BundleContents> bind(Comparison comparison) {
            Predicate<ItemStackTemplate> test = this.reader.bind(comparison);
            return bundle -> {
                for (int i = 0; i < bundle.items().size(); i++) {
                    if (test.test(bundle.items().get(i))) {
                        return true;
                    }
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of the item identifier of an item stack template.
    ///
    private enum ItemId implements PathReader<ItemStackTemplate> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `false`.
        ///
        /// @return `false`.
        ///
        @Override
        public boolean isOrdered() {
            return false;
        }
        
        ///
        /// Creates the test of the item identifier.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemStackTemplate> bind(Comparison comparison) {
            return item -> VanillaReaders.testName(item.item(), comparison);
        }
    }
    
    ///
    /// The reader of the count of an item stack template.
    ///
    private enum ItemCount implements PathReader<ItemStackTemplate> {
        ///
        /// The only instance of the reader.
        ///
        INSTANCE;
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        /// @return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the count.
        ///
        /// The codec leaves out a count of `1` and fails to encode a count less than `1` or greater than {@value Item#ABSOLUTE_MAX_STACK_SIZE}, so such a count reads as absent.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemStackTemplate> bind(Comparison comparison) {
            return item -> item.count() > 1 && item.count() <= Item.ABSOLUTE_MAX_STACK_SIZE && comparison.test(item.count());
        }
    }
    
    ///
    /// The reader of a path in the value that the patch of an item stack template sets for one [DataComponentType].
    ///
    /// @param type    The data component type, which is registered and persistent.
    /// @param reader  The reader of the path in the value of the component.
    /// @param <VALUE> The type of the values of the component.
    ///
    private record ItemComponent<VALUE>(DataComponentType<VALUE> type, PathReader<VALUE> reader) implements PathReader<ItemStackTemplate> {
        
        ///
        /// Creates the reader of the given steps in the values of the given data component type.
        ///
        /// @param type  The data component type, which is registered and persistent.
        /// @param steps The steps of the path in the value of the component.
        ///
        private ItemComponent(DataComponentType<VALUE> type, List<ComponentPath.Step> steps) {
            this(type, PathReaders.resolve(type, steps));
        }
        
        ///
        /// Will return the cost of the reader of the path.
        ///
        /// @return The cost of the reader of the path.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.reader.getCost();
        }
        
        ///
        /// Will return the same as the reader of the path.
        ///
        /// @return `true` if the reader of the path is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.reader.isOrdered();
        }
        
        ///
        /// Creates the test of the value that the patch sets for the component.
        ///
        /// If only the prototype of the item has the component or the patch removes it, the component will read as absent.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<ItemStackTemplate> bind(Comparison comparison) {
            Predicate<VALUE> test = this.reader.bind(comparison);
            return item -> item.components().getPatch(this.type) instanceof Optional<VALUE> patched && patched.isPresent() && test.test(patched.get());
        }
    }
    
    ///
    /// The reader of the steps in the tag of custom data.
    ///
    /// @param tag The reader of the steps in the tag.
    ///
    private record CustomDataTag(TagReader tag) implements PathReader<CustomData> {
        
        ///
        /// Will return [PathReader.Cost#SCAN] if the steps have a wildcard, or [PathReader.Cost#DIRECT] otherwise.
        ///
        /// @return [PathReader.Cost#SCAN] or [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.tag.steps().contains(new ComponentPath.Step.Wildcard()) ? PathReader.Cost.SCAN : PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `true`.
        ///
        /// @return `true`.
        ///
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        ///
        /// Creates the test of the tag at the steps in the tag of the custom data.
        ///
        /// @param comparison The comparison to test with.
        ///
        /// @return The test.
        ///
        @Override
        public Predicate<CustomData> bind(Comparison comparison) {
            return data -> this.tag.test(data.tag, comparison);
        }
    }
}
