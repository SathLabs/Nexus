package dev.satherov.nexus.test.game.api.component.model;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

///
/// A model of the attribute filter of Create that tests an item stack the way its 1.21.1 source does, ported to 26.1.
///
/// The model is cheaper than the code of Create in these ways:
/// - The filter tests an item stack in one call, while the filter of Create forwards it to an overload with a flag for NBT.
/// - A singleton attribute is one predicate of the item stack, while Create wraps it in a predicate of the item stack and the level.
/// - The inversion of an attribute is a `boolean`, while Create unboxes a `Boolean`.
/// - The registry access is read once per filter, while Create reads the registry access of the level for every name it parses.
/// - The name attribute does the work of 1.21.1's `Component.Serializer.fromJson`, which 26.1 doesn't have, with gson's [JsonParser] and [ComponentSerialization#CODEC].
///   It leaves out the check of that method for a `null` element.
///
/// The model is dearer than the code of Create in these ways:
/// - A filter that Create has no attribute for runs after the attributes with the component filter, which Create can't do at all.
/// - The name attribute compares the flattened name after a lenient JSON parse, so on an item stack with a custom name the model returns what Nexus returns after running Create's check.
/// - The enchantment attribute reads the stored enchantments of an enchanted book and the enchantments of any other item.
///   If it reads another component than the filter does, the model returns what Nexus returns after running Create's check.
/// - The damaged attribute counts damage only on an item that can take damage, so on any other item the model returns what Nexus returns after running Create's check.
/// - Nexus reads the generation of a book from its encoded form, which leaves out a generation of `0`, so a filter on the original generation passes no book.
///   If the filter is on the original generation, the model returns what Nexus returns on every book after running Create's check.
/// - Every correction checks on each item stack if Create's answer can be kept.
///
public final class CreateAttributeModel implements CompetitorModel {
    
    private static AttributeFilter translate(ComponentFilter filter) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("There is no running server to read the registries of");
        
        Context context = new Context(server.registryAccess(), new CompetitorModel.Unmemoized());
        return switch (filter) {
            case ComponentFilter.AllOf all -> new AttributeFilter(Mode.WHITELIST_CONJ, CreateAttributeModel.entries(all.getAllFilters(), context));
            case ComponentFilter.AnyOf any -> new AttributeFilter(Mode.WHITELIST_DISJ, CreateAttributeModel.entries(any.getAllFilters(), context));
            case ComponentFilter.Not not -> new AttributeFilter(
                    Mode.BLACKLIST,
                    CreateAttributeModel.entries(not.getFilter() instanceof ComponentFilter.AnyOf any ? any.getAllFilters() : List.of(not.getFilter()), context)
            );
            case ComponentFilter.Has _, ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> new AttributeFilter(
                    Mode.WHITELIST_DISJ,
                    CreateAttributeModel.entries(List.of(filter), context)
            );
        };
    }
    
    private static List<Entry> entries(List<ComponentFilter> filters, Context context) {
        List<Entry> entries = new ArrayList<>();
        List<Entry> leftovers = new ArrayList<>();
        for (ComponentFilter filter : filters) {
            Predicate<ItemStack> attribute = CreateAttributeModel.attribute(filter instanceof ComponentFilter.Not not ? not.getFilter() : filter, context);
            if (attribute != null) {
                entries.add(new Entry(attribute, filter instanceof ComponentFilter.Not));
            } else {
                leftovers.add(new Entry(new Leftover(filter, context.unmemoized()), false));
            }
        }
        
        entries.addAll(leftovers);
        return entries;
    }
    
    private static @Nullable Predicate<ItemStack> attribute(ComponentFilter filter, Context context) {
        return switch (filter) {
            case ComponentFilter.Has has when has.getComponent() == DataComponents.CUSTOM_NAME -> stack -> stack.has(DataComponents.CUSTOM_NAME);
            case ComponentFilter.Has has when has.getComponent() == DataComponents.FOOD -> stack -> stack.has(DataComponents.FOOD);
            case ComponentFilter.Exact exact when (
                    exact.getValue().type() == DataComponents.CUSTOM_NAME &&
                    exact.getValue().value() instanceof Component name &&
                    !name.getString().isEmpty()
            ) -> CreateAttributeModel.hasName(name.getString(), filter, context);
            case ComponentFilter.Compare compare -> CreateAttributeModel.comparedAttribute(compare, context);
            case ComponentFilter.Has _, ComponentFilter.Exact _, ComponentFilter.Matching _, ComponentFilter.AllOf _, ComponentFilter.AnyOf _, ComponentFilter.Not _ -> null;
        };
    }
    
    private static @Nullable Predicate<ItemStack> comparedAttribute(ComponentFilter.Compare compare, Context context) {
        DataComponentType<?> component = compare.getPath().getComponent();
        List<ComponentPath.Step> steps = compare.getPath().getAllSteps();
        Comparison comparison = compare.getComparison();
        boolean equality = comparison.operator() == Comparison.Operator.EQUAL;
        if (component == DataComponents.CUSTOM_NAME && steps.isEmpty() && equality && comparison.literal() instanceof Comparison.Literal.Text(String text) && !text.isEmpty()) {
            return CreateAttributeModel.hasName(text, compare, context);
        }
        
        if (
                component == DataComponents.WRITTEN_BOOK_CONTENT &&
                steps.equals(List.of(new ComponentPath.Step.Key("author"))) &&
                equality &&
                comparison.literal() instanceof Comparison.Literal.Text(String author) &&
                !author.isEmpty()
        ) {
            return stack -> CreateAttributeModel.bookAuthor(stack).equals(author);
        }
        
        if (
                component == DataComponents.WRITTEN_BOOK_CONTENT &&
                steps.equals(List.of(new ComponentPath.Step.Key("generation"))) &&
                equality &&
                comparison.literal() instanceof Comparison.Literal.Numeric(double number) &&
                number >= 0 &&
                number == (int) number
        ) {
            int generation = (int) number;
            Predicate<ItemStack> attribute = stack -> CreateAttributeModel.bookGeneration(stack) == generation;
            return generation == 0 ? CreateAttributeModel.corrected(attribute, stack -> !stack.has(DataComponents.WRITTEN_BOOK_CONTENT), compare, context.unmemoized()) : attribute;
        }
        
        if (component == DataComponents.DAMAGE && steps.isEmpty() && CreateAttributeModel.isAboveZeroTest(comparison)) {
            return CreateAttributeModel.corrected(ItemStack::isDamaged, ItemStack::isDamageableItem, compare, context.unmemoized());
        }
        
        boolean stored = component == DataComponents.STORED_ENCHANTMENTS;
        if (
                (stored || component == DataComponents.ENCHANTMENTS) &&
                steps.size() == 1 &&
                steps.getFirst() instanceof ComponentPath.Step.Key(String key) &&
                Identifier.tryParse(key) instanceof Identifier id &&
                id.toString().equals(key) &&
                CreateAttributeModel.isAboveZeroTest(comparison) &&
                context.registries().get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null) instanceof Holder<Enchantment> enchantment
        ) {
            return CreateAttributeModel.corrected(
                    stack -> EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().contains(enchantment),
                    stack -> stack.is(Items.ENCHANTED_BOOK) == stored,
                    compare,
                    context.unmemoized()
            );
        }
        
        return null;
    }
    
    private static Predicate<ItemStack> hasName(String name, ComponentFilter filter, Context context) {
        return CreateAttributeModel.corrected(
                stack -> CreateAttributeModel.customName(stack, context.registries()).equals(name),
                stack -> !stack.has(DataComponents.CUSTOM_NAME),
                filter,
                context.unmemoized()
        );
    }
    
    private static Predicate<ItemStack> corrected(Predicate<ItemStack> attribute, Predicate<ItemStack> agrees, ComponentFilter filter, CompetitorModel.Unmemoized unmemoized) {
        return stack -> {
            boolean applies = attribute.test(stack);
            return agrees.test(stack) ? applies : unmemoized.test(filter, stack);
        };
    }
    
    private static boolean isAboveZeroTest(Comparison comparison) {
        return switch (comparison.operator()) {
            case GREATER, GREATER_OR_EQUAL, NOT_EQUAL -> comparison.test(1.0D) && !comparison.test(0.0D);
            case EQUAL, LESS, LESS_OR_EQUAL -> false;
        };
    }
    
    private static String customName(ItemStack stack, RegistryAccess registries) {
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            return "";
        }
        
        try {
            String flat = stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString();
            return ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(flat))
                    .getOrThrow(JsonParseException::new)
                    .getString();
        } catch (JsonParseException _) {
            return "";
        }
    }
    
    private static String bookAuthor(ItemStack stack) {
        if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            return "";
        }
        
        //noinspection DataFlowIssue The stack has the component.
        return stack.get(DataComponents.WRITTEN_BOOK_CONTENT).author();
    }
    
    private static int bookGeneration(ItemStack stack) {
        if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            return -1;
        }
        
        //noinspection DataFlowIssue The stack has the component.
        return stack.get(DataComponents.WRITTEN_BOOK_CONTENT).generation();
    }
    
    @Override
    public String getName() {
        return "create_attribute";
    }
    
    @Override
    public Predicate<ItemStack> build(ComponentFilter filter) {
        return CreateAttributeModel.translate(filter);
    }
    
    @Override
    public boolean expresses(ComponentFilter filter) {
        return CreateAttributeModel.translate(filter).entries().stream().anyMatch(entry -> !(entry.attribute() instanceof Leftover));
    }
    
    private enum Mode {
        WHITELIST_DISJ,
        WHITELIST_CONJ,
        BLACKLIST
    }
    
    private record Entry(Predicate<ItemStack> attribute, boolean inverted) { }
    
    private record Context(RegistryAccess registries, CompetitorModel.Unmemoized unmemoized) { }
    
    private record Leftover(ComponentFilter filter, CompetitorModel.Unmemoized unmemoized) implements Predicate<ItemStack> {
        
        @Override
        public boolean test(ItemStack stack) {
            return this.unmemoized.test(this.filter, stack);
        }
    }
    
    private record AttributeFilter(Mode mode, List<Entry> entries) implements Predicate<ItemStack> {
        
        @Override
        public boolean test(ItemStack stack) {
            for (Entry entry : this.entries) {
                boolean matches = entry.attribute().test(stack) != entry.inverted();
                if (matches && this.mode != Mode.WHITELIST_CONJ) {
                    return this.mode == Mode.WHITELIST_DISJ;
                }
                
                if (!matches && this.mode == Mode.WHITELIST_CONJ) {
                    return false;
                }
            }
            
            return this.mode != Mode.WHITELIST_DISJ;
        }
    }
}
