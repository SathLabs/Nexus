package dev.satherov.nexus.test.game.impl.component;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.path.TagReader;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;
import dev.satherov.nexus.internal.component.PathReaders;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Objects;

///
/// Checks that the readers of vanilla components pass what encoding the component of a real item stack and reading the tag passes.
///
public class VanillaReadersSamples {
    
    private static final List<Comparison.Literal> LEVELS = List.of(
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(1.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Text("3"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<Comparison.Literal> ITEMS = List.of(
            new Comparison.Literal.Text("minecraft:stone"),
            new Comparison.Literal.Text("minecraft:diamond"),
            new Comparison.Literal.Text("minecraft:air"),
            new Comparison.Literal.Text("stone"),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Flag(true)
    );
    
    @ServerTest
    public static void enchantmentsMatchTheEncoding(GameTestHelper helper) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
        sword.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 1);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, sword.get(DataComponents.ENCHANTMENTS));
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        
        for (String path : List.of("['minecraft:sharpness']", "['minecraft:unbreaking']", "['minecraft:mending']")) {
            VanillaReadersSamples.assertMatchesTheEncoding(DataComponents.ENCHANTMENTS, path, VanillaReadersSamples.LEVELS, sword, plain);
            VanillaReadersSamples.assertMatchesTheEncoding(DataComponents.STORED_ENCHANTMENTS, path, VanillaReadersSamples.LEVELS, book);
        }
        
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.ENCHANTMENTS, "['minecraft:sharpness']", new Comparison.Literal.Numeric(3.0D), sword)).isTrue();
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.STORED_ENCHANTMENTS, "['minecraft:unbreaking']", new Comparison.Literal.Numeric(1.0D), book)).isTrue();
        helper.succeed();
    }
    
    @ServerTest
    public static void enchantmentsOfAnotherRegistrySetReadAsAbsent(GameTestHelper helper) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(Holder.Reference.createStandAlone(new HolderOwner<>() { }, Enchantments.SHARPNESS), 3);
        ItemEnchantments enchantments = mutable.toImmutable();
        PathReader<ItemEnchantments> reader = VanillaReadersSamples.reader(DataComponents.ENCHANTMENTS, "['minecraft:sharpness']");
        
        Assertions.assertThat(Encoding.encode(VanillaAdapters.ofRegistryDfu(DataComponents.ENCHANTMENTS.codecOrThrow()), enchantments)).isNull();
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            Assertions.assertThat(reader.bind(new Comparison(operator, new Comparison.Literal.Numeric(3.0D))).test(enchantments)).as(operator.getSymbol()).isFalse();
        }
        
        helper.succeed();
    }
    
    @ServerTest
    public static void containerSlotsMatchTheEncoding(GameTestHelper helper) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ItemStack.EMPTY, new ItemStack(Items.STONE), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 3), ItemStack.EMPTY)));
        ItemStack empty = new ItemStack(Items.SHULKER_BOX);
        empty.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        
        for (String path : List.of("[0].item.id", "[1].item.id", "[2].item.id", "[4].item.id", "[*].item.id")) {
            VanillaReadersSamples.assertMatchesTheEncoding(DataComponents.CONTAINER, path, VanillaReadersSamples.ITEMS, box, empty);
        }
        
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.CONTAINER, "[0].item.id", new Comparison.Literal.Text("minecraft:stone"), box)).isTrue();
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.CONTAINER, "[1].item.id", new Comparison.Literal.Text("minecraft:diamond"), box)).isTrue();
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.CONTAINER, "[*].item.id", new Comparison.Literal.Text("minecraft:diamond"), box)).isTrue();
        helper.succeed();
    }
    
    @ServerTest
    public static void bundleItemsMatchTheEncoding(GameTestHelper helper) {
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STONE)),
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIAMOND, 2))
        )));
        ItemStack empty = new ItemStack(Items.BUNDLE);
        empty.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        
        for (String path : List.of("[0].id", "[1].id", "[2].id", "[*].id")) {
            VanillaReadersSamples.assertMatchesTheEncoding(DataComponents.BUNDLE_CONTENTS, path, VanillaReadersSamples.ITEMS, bundle, empty);
        }
        
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.BUNDLE_CONTENTS, "[1].id", new Comparison.Literal.Text("minecraft:diamond"), bundle)).isTrue();
        Assertions.assertThat(VanillaReadersSamples.test(DataComponents.BUNDLE_CONTENTS, "[*].id", new Comparison.Literal.Text("minecraft:stone"), bundle)).isTrue();
        helper.succeed();
    }
    
    private static <VALUE> PathReader<VALUE> reader(DataComponentType<VALUE> type, String path) {
        PathReader<VALUE> reader = PathReaders.resolve(type, ComponentPath.of(type, path).getAllSteps());
        Assertions.assertThat(reader).as(path).isNotInstanceOf(Traversal.EncodedReader.class);
        return reader;
    }
    
    private static <VALUE> boolean test(DataComponentType<VALUE> type, String path, Comparison.Literal literal, ItemStack stack) {
        return VanillaReadersSamples.reader(type, path).bind(new Comparison(Comparison.Operator.EQUAL, literal)).test(Objects.requireNonNull(stack.get(type)));
    }
    
    private static <VALUE> void assertMatchesTheEncoding(DataComponentType<VALUE> type, String path, List<Comparison.Literal> literals, ItemStack... stacks) {
        PathReader<VALUE> reader = VanillaReadersSamples.reader(type, path);
        TagReader tag = new TagReader(ComponentPath.of(type, path).getAllSteps());
        NexusCodec<VALUE, ?> codec = VanillaAdapters.ofRegistryDfu(type.codecOrThrow());
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            for (Comparison.Literal literal : literals) {
                if (operator.isOrdered() && (!reader.isOrdered() || literal instanceof Comparison.Literal.Flag)) {
                    continue;
                }
                
                Comparison comparison = new Comparison(operator, literal);
                for (ItemStack stack : stacks) {
                    VALUE value = Objects.requireNonNull(stack.get(type));
                    Tag encoded = Objects.requireNonNull(Encoding.encode(codec, value));
                    Assertions.assertThat(reader.bind(comparison).test(value)).as("%s %s %s on %s", path, operator.getSymbol(), literal, value).isEqualTo(tag.test(encoded, comparison));
                }
            }
        }
    }
}
