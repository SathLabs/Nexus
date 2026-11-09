package dev.satherov.nexus.test.unit.impl.component;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.extension.component.ComponentTypeCodec;
import dev.satherov.nexus.impl.component.VanillaReaders;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.path.TagReader;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;
import dev.satherov.nexus.internal.component.PathReaders;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

///
/// Checks that the readers of vanilla components pass what encoding the component and reading the tag passes, and the paths they decline.
///
public class VanillaReadersTest {
    
    private static final HolderOwner<Enchantment> OWNER = new HolderOwner<>() { };
    private static final Holder<Enchantment> SHARPNESS = Holder.Reference.createStandAlone(VanillaReadersTest.OWNER, ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness")));
    private static final Holder<Enchantment> UNBREAKING = Holder.Reference.createStandAlone(VanillaReadersTest.OWNER, ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("unbreaking")));
    
    private static final List<Comparison.Literal> LEVEL_LITERALS = List.of(
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(1.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Text("3"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<Component> NAMES = List.of(
            Component.literal("Sword"),
            Component.literal(""),
            Component.literal("Sword").withStyle(ChatFormatting.RED),
            Component.literal("Sword").append("s"),
            Component.literal("item.minecraft.diamond_sword"),
            Component.translatable("item.minecraft.diamond_sword"),
            Component.translatable("item.minecraft.diamond_sword").withStyle(ChatFormatting.BOLD),
            Component.translatable("item.minecraft.diamond_sword", "Sword"),
            Component.translatable("item.minecraft.diamond_sword").append("s"),
            Component.literal("Sword").append(Component.translatable("item.minecraft.diamond_sword")),
            Component.keybind("key.jump")
    );
    private static final List<Comparison.Literal> NAME_LITERALS = List.of(
            new Comparison.Literal.Text("Sword"),
            new Comparison.Literal.Text("Swords"),
            new Comparison.Literal.Text(""),
            new Comparison.Literal.Text("item.minecraft.diamond_sword"),
            new Comparison.Literal.Text("key.jump"),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<PotionContents> POTIONS = List.of(
            new PotionContents(Potions.SWIFTNESS),
            new PotionContents(Potions.LONG_SWIFTNESS),
            PotionContents.EMPTY,
            new PotionContents(Optional.empty(), Optional.of(0xFF0000), List.of(), Optional.of("custom")),
            new PotionContents(Holder.direct(new Potion("direct")))
    );
    private static final List<Comparison.Literal> POTION_LITERALS = List.of(
            new Comparison.Literal.Text("minecraft:swiftness"),
            new Comparison.Literal.Text("minecraft:long_swiftness"),
            new Comparison.Literal.Text("swiftness"),
            new Comparison.Literal.Text("direct"),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Flag(true)
    );
    
    private static final ItemAttributeModifiers ATTRIBUTES = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.withDefaultNamespace("damage"), 5.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ARMOR, new AttributeModifier(Identifier.withDefaultNamespace("armor"), -1.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.ARMOR)
            .build();
    private static final List<Comparison.Literal> AMOUNT_LITERALS = List.of(
            new Comparison.Literal.Numeric(5.0D),
            new Comparison.Literal.Numeric(-1.5D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Text("5"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<Comparison.Literal> TAG_LITERALS = List.of(
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(1.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Numeric(2.5D),
            new Comparison.Literal.Text("Sword"),
            new Comparison.Literal.Text("two"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<Integer> DAMAGES = List.of(0, 5, Integer.MAX_VALUE, -1, Integer.MIN_VALUE);
    private static final List<Comparison.Literal> DAMAGE_LITERALS = List.of(
            new Comparison.Literal.Numeric(5.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Numeric(-1.0D),
            new Comparison.Literal.Text("5"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final ItemStackTemplate STONE = new ItemStackTemplate(Items.STONE);
    private static final ItemStackTemplate DIAMONDS = new ItemStackTemplate(Items.DIAMOND, 64);
    private static final ItemStackTemplate SWORD = new ItemStackTemplate(Items.DIAMOND_SWORD, DataComponentPatch.builder()
            .set(DataComponents.DAMAGE, 5)
            .set(DataComponents.CUSTOM_NAME, Component.literal("Sword"))
            .set(DataComponents.ATTRIBUTE_MODIFIERS, VanillaReadersTest.ATTRIBUTES)
            .set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putInt("level", 3)))
            .set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE)
            .build());
    private static final ItemStackTemplate STRIPPED = new ItemStackTemplate(Items.DIAMOND_SWORD, 2, DataComponentPatch.builder()
            .remove(DataComponents.DAMAGE)
            .remove(DataComponents.CUSTOM_NAME)
            .build());
    private static final List<String> ITEM_PATHS = List.of(
            "id",
            "count",
            "components['minecraft:damage']",
            "components['minecraft:custom_name']",
            "components['minecraft:attribute_modifiers'][0].amount",
            "components['minecraft:custom_data'].level",
            "components['minecraft:custom_data'][*]"
    );
    private static final List<Comparison.Literal> ITEM_LITERALS = List.of(
            new Comparison.Literal.Text("minecraft:stone"),
            new Comparison.Literal.Text("minecraft:diamond_sword"),
            new Comparison.Literal.Text("Sword"),
            new Comparison.Literal.Numeric(64.0D),
            new Comparison.Literal.Numeric(5.0D),
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(2.0D),
            new Comparison.Literal.Numeric(1.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Flag(true)
    );
    private static final DataComponentType<ItemContainerContents> MOD_CONTAINER = DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC).build();
    private static final DataComponentType<BundleContents> MOD_BUNDLE = DataComponentType.<BundleContents>builder().persistent(BundleContents.CODEC).build();
    
    @Test
    public void damageMatchesTheEncoding() {
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.DAMAGE, "", VanillaReadersTest.DAMAGE_LITERALS, VanillaReadersTest.DAMAGES);
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.DAMAGE, "", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(5.0D), 5)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, new Comparison.Literal.Numeric(5.0D), Integer.MAX_VALUE)).isTrue();
    }
    
    @Test
    public void readsANegativeDamageAsAbsent() {
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            for (int damage : List.of(-1, Integer.MIN_VALUE)) {
                Assertions.assertThat(VanillaReadersTest.test(DataComponents.DAMAGE, "", operator, new Comparison.Literal.Numeric(-1.0D), damage)).as("%s on %s", operator.getSymbol(), damage).isFalse();
            }
        }
    }
    
    @Test
    public void declinesDamagePathsWithSteps() {
        for (String path : List.of("x", "[0]", "[*]")) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.DAMAGE, path)).as(path).isNull();
        }
    }
    
    @Test
    public void readsEveryEnchantmentAsAbsentWithoutTheRegistry() {
        Assertions.assertThat(Encoding.owner(Registries.ENCHANTMENT)).isNull();
        
        for (DataComponentType<ItemEnchantments> type : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            for (String path : List.of("['minecraft:sharpness']", "['minecraft:unbreaking']", "['minecraft:mending']")) {
                VanillaReadersTest.assertMatchesTheEncoding(type, path, VanillaReadersTest.LEVEL_LITERALS, List.of(VanillaReadersTest.enchantments(), ItemEnchantments.EMPTY));
            }
            
            Assertions.assertThat(VanillaReadersTest.test(type, "['minecraft:sharpness']", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D), VanillaReadersTest.enchantments())).isFalse();
        }
    }
    
    @Test
    public void declinesEnchantmentPathsThatAreNotOneFullIdentifier() {
        for (String path : List.of("", "sharpness", "['minecraft:Sharpness']", "[':sharpness']", "['minecraft:sharpness'].level", "[*]", "[0]")) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.ENCHANTMENTS, path)).as(path).isNull();
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.STORED_ENCHANTMENTS, path)).as(path).isNull();
        }
    }
    
    @Test
    public void namesMatchTheEncodingAsPlainText() {
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.CUSTOM_NAME, "", VanillaReadersTest.NAME_LITERALS, VanillaReadersTest.NAMES);
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.ITEM_NAME, "", VanillaReadersTest.NAME_LITERALS, VanillaReadersTest.NAMES);
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "", Comparison.Operator.EQUAL, new Comparison.Literal.Text("Sword"), Component.literal("Sword"))).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "", Comparison.Operator.LESS, new Comparison.Literal.Text("Swords"), Component.literal("Sword"))).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "", Comparison.Operator.EQUAL, new Comparison.Literal.Text("Sword"), Component.literal("Sword").withStyle(ChatFormatting.RED))).isFalse();
    }
    
    @Test
    public void namesMatchTheEncodingByTranslationKey() {
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.CUSTOM_NAME, "translate", VanillaReadersTest.NAME_LITERALS, VanillaReadersTest.NAMES);
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.ITEM_NAME, "translate", VanillaReadersTest.NAME_LITERALS, VanillaReadersTest.NAMES);
        
        Component styled = Component.translatable("item.minecraft.diamond_sword").withStyle(ChatFormatting.BOLD);
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "translate", Comparison.Operator.EQUAL, new Comparison.Literal.Text("item.minecraft.diamond_sword"), styled)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "translate", Comparison.Operator.EQUAL, new Comparison.Literal.Text("item.minecraft.diamond_sword"), Component.literal("item.minecraft.diamond_sword"))).isFalse();
    }
    
    @Test
    public void namesWithContentsOtherThanTranslatableHaveNoTranslationKey() {
        List<Component> others = List.of(
                Component.literal("item.minecraft.diamond_sword").withStyle(ChatFormatting.RED),
                Component.literal("Sword").append(Component.translatable("item.minecraft.diamond_sword")),
                Component.keybind("item.minecraft.diamond_sword")
        );
        
        for (Component name : others) {
            for (Comparison.Operator operator : Comparison.Operator.values()) {
                for (String key : List.of("item.minecraft.diamond_sword", "")) {
                    Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_NAME, "translate", operator, new Comparison.Literal.Text(key), name)).as("%s '%s' on %s", operator.getSymbol(), key, name).isFalse();
                }
            }
        }
    }
    
    @Test
    public void declinesNamePathsOtherThanTheTextAndTheTranslationKey() {
        for (String path : List.of("text", "translate.key", "translate[0]", "[0]", "[*]", "extra[0].text", "color")) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.CUSTOM_NAME, path)).as(path).isNull();
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.ITEM_NAME, path)).as(path).isNull();
        }
    }
    
    @Test
    public void potionsMatchTheEncoding() {
        VanillaReadersTest.assertMatchesTheEncoding(DataComponents.POTION_CONTENTS, "potion", VanillaReadersTest.POTION_LITERALS, VanillaReadersTest.POTIONS);
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, new Comparison.Literal.Text("minecraft:swiftness"), new PotionContents(Potions.SWIFTNESS))).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Text("minecraft:swiftness"), PotionContents.EMPTY)).isFalse();
    }
    
    @Test
    public void declinesPotionPathsOtherThanThePotion() {
        for (String path : List.of("", "potion.id", "potion[0]", "custom_color", "custom_effects[0].id", "[0]")) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.POTION_CONTENTS, path)).as(path).isNull();
        }
    }
    
    @Test
    public void attributeAmountsMatchTheEncoding() {
        for (String path : List.of("[0].amount", "[1].amount", "[2].amount")) {
            VanillaReadersTest.assertMatchesTheEncoding(DataComponents.ATTRIBUTE_MODIFIERS, path, VanillaReadersTest.AMOUNT_LITERALS, List.of(VanillaReadersTest.ATTRIBUTES, ItemAttributeModifiers.EMPTY));
        }
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.ATTRIBUTE_MODIFIERS, "[1].amount", Comparison.Operator.LESS, new Comparison.Literal.Numeric(0.0D), VanillaReadersTest.ATTRIBUTES)).isTrue();
    }
    
    @Test
    public void readsTheAmountOfAModifierWhoseAttributeDoesNotEncode() {
        ItemAttributeModifiers.Entry entry = new ItemAttributeModifiers.Entry(
                Holder.direct(new RangedAttribute("attribute.name.direct", 0.0D, 0.0D, 10.0D)),
                new AttributeModifier(Identifier.withDefaultNamespace("direct"), 2.0D, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY,
                new ItemAttributeModifiers.Display.Default()
        );
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.ATTRIBUTE_MODIFIERS, "[0].amount", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(2.0D), new ItemAttributeModifiers(List.of(entry)))).isTrue();
    }
    
    @Test
    public void declinesAttributePathsOtherThanTheAmountAtAnIndex() {
        for (String path : List.of("", "[0]", "[*].amount", "[0].id", "[0].amount.x", "amount")) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.ATTRIBUTE_MODIFIERS, path)).as(path).isNull();
        }
    }
    
    @Test
    public void customDataMatchesTheEncoding() {
        CompoundTag nested = new CompoundTag();
        nested.putDouble("a", 2.5D);
        nested.putString("b", "two");
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(1));
        list.add(StringTag.valueOf("two"));
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "Sword");
        tag.putInt("count", 3);
        tag.putBoolean("flag", true);
        tag.putByteArray("bytes", new byte[]{ 0, 1 });
        tag.putIntArray("ints", new int[]{ 3, 1 });
        tag.put("list", list);
        tag.put("nested", nested);
        List<CustomData> values = List.of(CustomData.of(tag), CustomData.EMPTY);
        
        for (DataComponentType<CustomData> type : List.of(DataComponents.CUSTOM_DATA, DataComponents.BUCKET_ENTITY_DATA)) {
            for (String path : List.of("", "name", "count", "flag", "bytes[1]", "bytes[*]", "ints[0]", "list[0]", "list[1]", "list[2]", "list[*]", "nested.a", "nested[*]", "count.x", "missing")) {
                VanillaReadersTest.assertMatchesTheEncoding(type, path, VanillaReadersTest.TAG_LITERALS, values);
            }
        }
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_DATA, "nested.a", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(2.5D), CustomData.of(tag))).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CUSTOM_DATA, "flag", Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true), CustomData.of(tag))).isTrue();
    }
    
    @Test
    public void containerItemsMatchTheEncoding() {
        ItemContainerContents contents = VanillaReadersTest.container(null, VanillaReadersTest.STONE, null, null, VanillaReadersTest.SWORD, VanillaReadersTest.DIAMONDS, VanillaReadersTest.STRIPPED);
        for (DataComponentType<ItemContainerContents> type : List.of(DataComponents.CONTAINER, VanillaReadersTest.MOD_CONTAINER)) {
            for (String slot : List.of("[0]", "[1]", "[2]", "[3]", "[4]", "[*]")) {
                for (String part : VanillaReadersTest.ITEM_PATHS) {
                    VanillaReadersTest.assertMatchesTheEncoding(type, slot + ".item." + part, VanillaReadersTest.ITEM_LITERALS, List.of(contents, ItemContainerContents.EMPTY));
                }
            }
        }
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CONTAINER, "[1].item.components['minecraft:damage']", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(5.0D), contents)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CONTAINER, "[2].item.count", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(64.0D), contents)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.CONTAINER, "[*].item.components['minecraft:attribute_modifiers'][0].amount", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(5.0D), contents)).isTrue();
    }
    
    @Test
    public void bundleItemsMatchTheEncoding() {
        BundleContents bundle = new BundleContents(List.of(VanillaReadersTest.STONE, VanillaReadersTest.SWORD, VanillaReadersTest.DIAMONDS, VanillaReadersTest.STRIPPED));
        for (DataComponentType<BundleContents> type : List.of(DataComponents.BUNDLE_CONTENTS, VanillaReadersTest.MOD_BUNDLE)) {
            for (String index : List.of("[0]", "[1]", "[2]", "[3]", "[4]", "[*]")) {
                for (String part : VanillaReadersTest.ITEM_PATHS) {
                    VanillaReadersTest.assertMatchesTheEncoding(type, index + "." + part, VanillaReadersTest.ITEM_LITERALS, List.of(bundle, BundleContents.EMPTY));
                }
            }
        }
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, "[1].components['minecraft:custom_name']", Comparison.Operator.EQUAL, new Comparison.Literal.Text("Sword"), bundle)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, "[3].count", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(2.0D), bundle)).isTrue();
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, "[*].components['minecraft:custom_data'].level", Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D), bundle)).isTrue();
    }
    
    @Test
    public void resolvesTheContainerAndBundleReadersByCodec() {
        for (DataComponentType<ItemContainerContents> type : List.of(DataComponents.CONTAINER, VanillaReadersTest.MOD_CONTAINER)) {
            for (String path : List.of("[0].item.id", "[*].item.count", "[1].item.components['minecraft:damage']")) {
                Assertions.assertThat(PathReaders.resolve(type, VanillaReadersTest.steps(path))).as("%s on %s", path, type).isEqualTo(VanillaReadersTest.reader(type, path));
            }
        }
        
        for (DataComponentType<BundleContents> type : List.of(DataComponents.BUNDLE_CONTENTS, VanillaReadersTest.MOD_BUNDLE)) {
            for (String path : List.of("[0].id", "[*].count", "[1].components['minecraft:damage']")) {
                Assertions.assertThat(PathReaders.resolve(type, VanillaReadersTest.steps(path))).as("%s on %s", path, type).isEqualTo(VanillaReadersTest.reader(type, path));
            }
        }
    }
    
    @Test
    public void readsWhatTheCodecLeavesOutOfAnItemAsAbsent() {
        BundleContents bundle = new BundleContents(List.of(VanillaReadersTest.STONE, VanillaReadersTest.STRIPPED));
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            for (double number : new double[]{ 0.0D, 1.0D, 5.0D }) {
                for (String path : List.of("[0].count", "[0].components['minecraft:damage']", "[1].components['minecraft:damage']", "[*].components['minecraft:damage']")) {
                    Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, path, operator, new Comparison.Literal.Numeric(number), bundle)).as("%s %s %s", path, operator.getSymbol(), number).isFalse();
                }
            }
        }
    }
    
    @Test
    public void readsACountOrComponentThatFailsToEncodeAsAbsent() {
        BundleContents bundle = new BundleContents(List.of(
                new ItemStackTemplate(Items.STONE, 100),
                new ItemStackTemplate(Items.STONE, -1),
                new ItemStackTemplate(Items.DIAMOND_SWORD, DataComponentPatch.builder().set(DataComponents.DAMAGE, -1).build())
        ));
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            for (double number : new double[]{ -1.0D, 0.0D, 100.0D }) {
                for (String path : List.of("[0].count", "[1].count", "[*].count", "[2].components['minecraft:damage']", "[*].components['minecraft:damage']")) {
                    Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, path, operator, new Comparison.Literal.Numeric(number), bundle)).as("%s %s %s", path, operator.getSymbol(), number).isFalse();
                }
            }
        }
    }
    
    @Test
    public void readsTheIdentifierOfAnItemWhoseCountDoesNotEncode() {
        BundleContents bundle = new BundleContents(List.of(new ItemStackTemplate(Items.STONE, 100)));
        
        Assertions.assertThat(VanillaReadersTest.test(DataComponents.BUNDLE_CONTENTS, "[0].id", Comparison.Operator.EQUAL, new Comparison.Literal.Text("minecraft:stone"), bundle)).isTrue();
    }
    
    @Test
    public void declinesContainerPathsOtherThanAPartOfTheItemInASlot() {
        for (String path : List.of(
                "",
                "[0]",
                "[0].item",
                "[0].slot",
                "[0].id",
                "[0].count",
                "[0].item.id.x",
                "[0].item.count[0]",
                "['a'].item.id",
                "item.id",
                "[0].item.components",
                "[0].item.components[*]",
                "[0].item.components[0]",
                "[0].item.components['damage']",
                "[0].item.components['!minecraft:damage']",
                "[0].item.components['nexus:missing']",
                "[0].item.components['minecraft:creative_slot_lock']"
        )) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.CONTAINER, path)).as(path).isNull();
        }
    }
    
    @Test
    public void declinesBundlePathsOtherThanAPartOfAnItem() {
        for (String path : List.of(
                "",
                "[0]",
                "[0].item.id",
                "[0].id.x",
                "[0].count[0]",
                "['a'].id",
                "id",
                "[0].components",
                "[0].components[*]",
                "[0].components[0]",
                "[0].components['damage']",
                "[0].components['!minecraft:damage']",
                "[0].components['nexus:missing']",
                "[0].components['minecraft:creative_slot_lock']"
        )) {
            Assertions.assertThat(VanillaReadersTest.reader(DataComponents.BUNDLE_CONTENTS, path)).as(path).isNull();
        }
    }
    
    @Test
    public void declaresTheOrderAndCostOfEveryRead() {
        VanillaReadersTest.assertDeclares(DataComponents.DAMAGE, "", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.ENCHANTMENTS, "['minecraft:sharpness']", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CUSTOM_NAME, "", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.CUSTOM_NAME, "translate", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.POTION_CONTENTS, "potion", false, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.ATTRIBUTE_MODIFIERS, "[0].amount", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.CONTAINER, "[0].item.id", false, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CONTAINER, "[*].item.id", false, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CONTAINER, "[0].item.count", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CONTAINER, "[0].item.components['minecraft:damage']", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CONTAINER, "[*].item.components['minecraft:potion_contents'].potion", false, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].id", false, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[*].id", false, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].count", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].components['minecraft:damage']", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].components['minecraft:potion_contents'].potion", false, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].components['minecraft:enchantments']['minecraft:sharpness']", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[0].components['minecraft:custom_data'][*]", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.BUNDLE_CONTENTS, "[*].components['minecraft:damage']", true, PathReader.Cost.SCAN);
        VanillaReadersTest.assertDeclares(DataComponents.CUSTOM_DATA, "nested.a", true, PathReader.Cost.DIRECT);
        VanillaReadersTest.assertDeclares(DataComponents.CUSTOM_DATA, "list[*]", true, PathReader.Cost.SCAN);
    }
    
    private static ItemEnchantments enchantments() {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(VanillaReadersTest.SHARPNESS, 3);
        enchantments.set(VanillaReadersTest.UNBREAKING, 1);
        return enchantments.toImmutable();
    }
    
    private static ItemContainerContents container(@Nullable ItemStackTemplate... slots) {
        ListTag encoded = new ListTag();
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] instanceof ItemStackTemplate item) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.put("item", ItemStackTemplate.CODEC.encodeStart(NbtOps.INSTANCE, item).getOrThrow());
                encoded.add(entry);
            }
        }
        
        return ItemContainerContents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }
    
    private static List<ComponentPath.Step> steps(String path) {
        // Custom data takes any steps, so parsing the path here never creates a reader on the component under test.
        return ComponentPath.of(DataComponents.CUSTOM_DATA, path).getAllSteps();
    }
    
    private static <VALUE> @Nullable PathReader<VALUE> reader(DataComponentType<VALUE> type, String path) {
        Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byComponent = new IdentityHashMap<>();
        Map<Codec<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byCodec = new IdentityHashMap<>();
        VanillaReaders.register(new RegisterPathReadersEvent(byComponent, byCodec));
        List<ComponentPath.Step> steps = VanillaReadersTest.steps(path);
        Codec<?> built = Objects.requireNonNullElse(((ComponentTypeCodec) type).nexus$getCodec(), type.codecOrThrow());
        for (Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>> readers : Iterables.concat(
                byComponent.getOrDefault(type, List.of()),
                byCodec.getOrDefault(built, List.of())
        )) {
            PathReader<?> reader = readers.apply(steps);
            if (reader != null) {
                //noinspection unchecked The function was registered for this type or for its codec.
                return (PathReader<VALUE>) reader;
            }
        }
        
        return null;
    }
    
    private static <VALUE> boolean test(DataComponentType<VALUE> type, String path, Comparison.Operator operator, Comparison.Literal literal, VALUE value) {
        return Objects.requireNonNull(VanillaReadersTest.reader(type, path)).bind(new Comparison(operator, literal)).test(value);
    }
    
    private static <VALUE> void assertMatchesTheEncoding(DataComponentType<VALUE> type, String path, List<Comparison.Literal> literals, List<VALUE> values) {
        PathReader<VALUE> reader = Objects.requireNonNull(VanillaReadersTest.reader(type, path));
        TagReader tag = new TagReader(VanillaReadersTest.steps(path));
        NexusCodec<VALUE, ?> codec = VanillaAdapters.ofRegistryDfu(type.codecOrThrow());
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            for (Comparison.Literal literal : literals) {
                if (operator.isOrdered() && (!reader.isOrdered() || literal instanceof Comparison.Literal.Flag)) {
                    continue;
                }
                
                Comparison comparison = new Comparison(operator, literal);
                for (VALUE value : values) {
                    Tag encoded = Encoding.encode(codec, value);
                    boolean expected = encoded != null && tag.test(encoded, comparison);
                    Assertions.assertThat(reader.bind(comparison).test(value)).as("%s %s %s on %s", path, operator.getSymbol(), literal, value).isEqualTo(expected);
                }
            }
        }
    }
    
    private static void assertDeclares(DataComponentType<?> type, String path, boolean ordered, PathReader.Cost cost) {
        PathReader<?> reader = Objects.requireNonNull(VanillaReadersTest.reader(type, path));
        Assertions.assertThat(reader.isOrdered()).as(path).isEqualTo(ordered);
        Assertions.assertThat(reader.getCost()).as(path).isEqualTo(cost);
    }
}
