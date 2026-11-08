package dev.satherov.nexus.test.game.api.component.scenario;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

///
/// The scenario of storage, with a storage system that sorts its items and an ore processing line that rejects its loot.
///
public final class StorageScenario extends SourcedScenario {
    
    private static final int CELL_CAPACITY = 1_000_000;
    private static final int TANK_CAPACITY = 32_000;
    private static final float MINESHAFT_SHARE = 0.003F;
    private static final List<String> NAMES = List.of("Excalibur", "Bob", "Lucky", "Trash", "Keep", "Old Faithful");
    private static final List<String> SHULKER_NAMES = List.of("Ores", "Wood", "Redstone", "Food", "Misc", "Trash");
    private static final List<Item> SHULKERS = List.of(Items.SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.LIME_SHULKER_BOX, Items.RED_SHULKER_BOX);
    private static final List<Item> BULK = List.of(Items.COBBLESTONE, Items.DIAMOND, Items.IRON_INGOT, Items.REDSTONE, Items.OAK_LOG, Items.COAL, Items.EMERALD, Items.GOLD_INGOT);
    private static final List<Item> BUNDLED = List.of(Items.ARROW, Items.TORCH, Items.DIAMOND, Items.EMERALD, Items.BREAD, Items.STRING, Items.REDSTONE);
    private static final WeightedList<Item> PLAYER_GEAR = WeightedList.<Item>builder()
            .add(Items.DIAMOND_SWORD, 3)
            .add(Items.DIAMOND_PICKAXE, 4)
            .add(Items.NETHERITE_AXE, 2)
            .add(Items.DIAMOND_CHESTPLATE, 2)
            .add(Items.NETHERITE_HELMET, 1)
            .add(Items.IRON_PICKAXE, 3)
            .add(Items.BOW, 2)
            .add(Items.CROSSBOW, 1)
            .add(Items.FISHING_ROD, 1)
            .build();
    private static final List<Holder<Potion>> BREWED = List.of(
            Potions.HEALING,
            Potions.STRONG_HEALING,
            Potions.SWIFTNESS,
            Potions.LONG_SWIFTNESS,
            Potions.NIGHT_VISION,
            Potions.FIRE_RESISTANCE,
            Potions.REGENERATION,
            Potions.STRENGTH
    );
    private static final List<ResourceKey<Enchantment>> TRADED_BOOKS = List.of(
            Enchantments.MENDING,
            Enchantments.SHARPNESS,
            Enchantments.PROTECTION,
            Enchantments.EFFICIENCY,
            Enchantments.UNBREAKING,
            Enchantments.FORTUNE,
            Enchantments.LOOTING,
            Enchantments.SILK_TOUCH,
            Enchantments.FEATHER_FALLING,
            Enchantments.POWER
    );
    private static final WeightedList<Item> ORES = WeightedList.<Item>builder()
            .add(Items.RAW_IRON, 20)
            .add(Items.RAW_COPPER, 15)
            .add(Items.RAW_GOLD, 6)
            .add(Items.IRON_ORE, 8)
            .add(Items.DEEPSLATE_IRON_ORE, 8)
            .add(Items.COPPER_ORE, 6)
            .add(Items.DEEPSLATE_GOLD_ORE, 3)
            .add(Items.REDSTONE, 10)
            .add(Items.LAPIS_LAZULI, 6)
            .add(Items.COAL, 12)
            .add(Items.DIAMOND, 2)
            .add(Items.EMERALD, 1)
            .add(Items.QUARTZ, 4)
            .add(Items.IRON_INGOT, 10)
            .add(Items.GOLD_INGOT, 4)
            .add(Items.COPPER_INGOT, 8)
            .add(Items.IRON_NUGGET, 6)
            .add(Items.GOLD_NUGGET, 4)
            .add(Items.AMETHYST_SHARD, 2)
            .add(Items.ANCIENT_DEBRIS, 1)
            .add(Items.COBBLESTONE, 25)
            .add(Items.COBBLED_DEEPSLATE, 20)
            .add(Items.TUFF, 8)
            .add(Items.GRAVEL, 6)
            .build();
    
    private final Loot loot;
    private final HolderSet.Named<Enchantment> enchantingTable;
    private final HolderSet.Named<Enchantment> tradeable;
    private final HolderSet.Named<Enchantment> randomLoot;
    private final WeightedList<Function<RandomSource, ItemStack>> stored;
    private final WeightedList<Function<RandomSource, ItemStack>> mineshaft;
    
    public StorageScenario(RegistryAccess registries) {
        this.loot = new Loot(registries);
        this.enchantingTable = this.loot.enchantments(EnchantmentTags.IN_ENCHANTING_TABLE);
        this.tradeable = this.loot.enchantments(EnchantmentTags.TRADEABLE);
        this.randomLoot = this.loot.enchantments(EnchantmentTags.ON_RANDOM_LOOT);
        this.stored = WeightedList.<Function<RandomSource, ItemStack>>builder()
                .add(random -> Loot.randomStack(random, Loot.ORDINARY), 60)
                .add(this::playerGear, 10)
                .add(this::storedBook, 5)
                .add(random -> PotionContents.createItemStack(random.nextBoolean() ? Items.POTION : Items.SPLASH_POTION, Util.getRandom(StorageScenario.BREWED, random)), 3)
                .add(StorageScenario::shulker, 5)
                .add(StorageScenario::bundle, 2)
                .add(StorageScenario::energyCell, 4)
                .add(StorageScenario::tank, 4)
                .add(Loot::packItem, 5)
                .add(StorageScenario::renamed, 2)
                .build();
        this.mineshaft = WeightedList.<Function<RandomSource, ItemStack>>builder()
                .add(_ -> new ItemStack(Items.GOLDEN_APPLE), 20)
                .add(_ -> new ItemStack(Items.NAME_TAG), 30)
                .add(random -> this.loot.enchantRandomly(random, Items.ENCHANTED_BOOK, this.randomLoot.getRandomElement(random).orElseThrow()), 10)
                .add(_ -> new ItemStack(Items.IRON_PICKAXE), 5)
                .add(random -> Loot.stack(random, Items.RAIL, 4, 8), 20)
                .add(random -> Loot.stack(random, Items.TORCH, 1, 16), 15)
                .build();
        
        Holder<Enchantment> mending = this.loot.enchantment(Enchantments.MENDING);
        Holder<Enchantment> sharpness = this.loot.enchantment(Enchantments.SHARPNESS);
        this.add(
                new Source("storage", 1_000, 10_000, (random, drops) -> drops.add(this.stored.getRandomOrThrow(random).apply(random))),
                new Rule(
                        "storage_mending_books",
                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.MENDING, 1),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, mending) >= 1,
                        true
                ),
                new Rule(
                        "storage_sharpness_books",
                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.SHARPNESS, 5),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, sharpness) >= 5,
                        true
                ),
                new Rule(
                        "storage_damaged_tools",
                        SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, 200),
                        stack -> SourcedScenario.damage(stack) > 200,
                        true
                ),
                new Rule(
                        "storage_charged_cells",
                        SourcedScenario.compare(TestComponents.ENERGY, "", Comparison.Operator.GREATER_OR_EQUAL, StorageScenario.CELL_CAPACITY),
                        stack -> stack.getOrDefault(TestComponents.ENERGY, 0L) >= StorageScenario.CELL_CAPACITY,
                        true
                ),
                new Rule(
                        "storage_empty_cells",
                        SourcedScenario.compare(TestComponents.ENERGY, "", Comparison.Operator.LESS_OR_EQUAL, 0),
                        stack -> stack.get(TestComponents.ENERGY) instanceof Long energy && energy <= 0L,
                        true
                ),
                new Rule(
                        "storage_full_tanks",
                        SourcedScenario.compare(TestComponents.FLUID, "amount", Comparison.Operator.GREATER_OR_EQUAL, StorageScenario.TANK_CAPACITY / 2),
                        stack -> stack.getOrDefault(TestComponents.FLUID, SimpleFluidContent.EMPTY).getAmount() >= StorageScenario.TANK_CAPACITY / 2,
                        true
                ),
                new Rule(
                        "storage_lava_tanks",
                        SourcedScenario.compare(TestComponents.FLUID, "id", Comparison.Operator.EQUAL, "minecraft:lava"),
                        stack -> stack.getOrDefault(TestComponents.FLUID, SimpleFluidContent.EMPTY).is(Fluids.LAVA),
                        false
                ),
                new Rule(
                        "storage_healing_potions",
                        SourcedScenario.compare(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, "minecraft:strong_healing"),
                        stack -> SourcedScenario.isPotion(stack, Potions.STRONG_HEALING),
                        true
                ),
                new Rule(
                        "storage_shulker_diamonds",
                        SourcedScenario.compare(DataComponents.CONTAINER, "[*].item.id", Comparison.Operator.EQUAL, "minecraft:diamond"),
                        stack -> SourcedScenario.holds(stack, DataComponents.CONTAINER, Items.DIAMOND),
                        true
                ),
                new Rule(
                        "storage_shulker_name",
                        SourcedScenario.compare(DataComponents.CUSTOM_NAME, "", Comparison.Operator.EQUAL, "Ores"),
                        stack -> SourcedScenario.isNamed(stack, "Ores"),
                        true
                ),
                new Rule(
                        "storage_route",
                        SourcedScenario.compare(DataComponents.CUSTOM_DATA, "kubejs.category", Comparison.Operator.EQUAL, "currency"),
                        stack -> SourcedScenario.customData(stack).getCompoundOrEmpty("kubejs").getStringOr("category", "").equals("currency"),
                        false
                ),
                new Rule(
                        "storage_valuables",
                        ComponentFilter.anyOf(List.of(
                                ComponentFilter.allOf(List.of(
                                        ComponentFilter.has(DataComponents.CUSTOM_NAME),
                                        SourcedScenario.compare(DataComponents.CUSTOM_NAME, "", Comparison.Operator.EQUAL, "Trash").negate()
                                )),
                                SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.MENDING, 1),
                                SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.SHARPNESS, 5),
                                SourcedScenario.compare(DataComponents.CONTAINER, "[*].item.id", Comparison.Operator.EQUAL, "minecraft:diamond"),
                                SourcedScenario.compare(DataComponents.BUNDLE_CONTENTS, "[*].id", Comparison.Operator.EQUAL, "minecraft:diamond"),
                                SourcedScenario.compare(DataComponents.CUSTOM_DATA, "kubejs.category", Comparison.Operator.EQUAL, "relic")
                        )),
                        stack -> (stack.has(DataComponents.CUSTOM_NAME) && !SourcedScenario.isNamed(stack, "Trash")) ||
                                SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, mending) >= 1 ||
                                SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, sharpness) >= 5 ||
                                SourcedScenario.holds(stack, DataComponents.CONTAINER, Items.DIAMOND) ||
                                StorageScenario.bundles(stack, Items.DIAMOND) ||
                                SourcedScenario.customData(stack).getCompoundOrEmpty("kubejs").getStringOr("category", "").equals("relic"),
                        false
                )
        );
        
        Source ores = new Source("ore_processing", 3_000, 50_000, (random, drops) -> drops.add(this.processed(random)));
        this.add(
                ores,
                new Rule(
                        "ore_processing_plain",
                        ComponentFilter.anyOf(List.of(
                                ComponentFilter.has(DataComponents.CUSTOM_NAME),
                                ComponentFilter.has(DataComponents.CUSTOM_DATA),
                                ComponentFilter.has(DataComponents.STORED_ENCHANTMENTS)
                        )).negate(),
                        stack -> !stack.has(DataComponents.CUSTOM_NAME) && !stack.has(DataComponents.CUSTOM_DATA) && !stack.has(DataComponents.STORED_ENCHANTMENTS),
                        false
                ),
                new Rule(
                        "ore_processing_rejects",
                        ComponentFilter.has(DataComponents.STORED_ENCHANTMENTS).or(ComponentFilter.has(DataComponents.DAMAGE)),
                        stack -> stack.has(DataComponents.STORED_ENCHANTMENTS) || stack.has(DataComponents.DAMAGE),
                        false
                )
        );
        this.addRejectOnly(
                ores,
                new Rule(
                        "ore_processing_named",
                        ComponentFilter.has(DataComponents.CUSTOM_NAME),
                        stack -> stack.has(DataComponents.CUSTOM_NAME),
                        true
                ),
                new Rule(
                        "ore_processing_shulker_diamonds",
                        SourcedScenario.compare(DataComponents.CONTAINER, "[*].item.id", Comparison.Operator.EQUAL, "minecraft:diamond"),
                        stack -> SourcedScenario.holds(stack, DataComponents.CONTAINER, Items.DIAMOND),
                        true
                ),
                new Rule(
                        "ore_processing_charged_cells",
                        SourcedScenario.compare(TestComponents.ENERGY, "", Comparison.Operator.GREATER_OR_EQUAL, StorageScenario.CELL_CAPACITY),
                        stack -> stack.getOrDefault(TestComponents.ENERGY, 0L) >= StorageScenario.CELL_CAPACITY,
                        true
                )
        );
    }
    
    private static boolean bundles(ItemStack stack, Item item) {
        for (ItemStackTemplate template : stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items()) {
            if (template.is(item)) {
                return true;
            }
        }
        
        return false;
    }
    
    private static ItemStack shulker(RandomSource random) {
        boolean sorted = random.nextFloat() < 0.7F;
        Item bulk = Util.getRandom(StorageScenario.BULK, random);
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 1 + random.nextInt(27); slot > 0; slot--) {
            contents.add(sorted ? new ItemStack(bulk, bulk.getDefaultMaxStackSize()) : Loot.randomStack(random, Loot.ORDINARY));
        }
        
        ItemStack shulker = new ItemStack(Util.getRandom(StorageScenario.SHULKERS, random));
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        if (random.nextFloat() < 0.4F) {
            shulker.set(DataComponents.CUSTOM_NAME, Component.literal(Util.getRandom(StorageScenario.SHULKER_NAMES, random)));
        }
        
        return shulker;
    }
    
    private static ItemStack bundle(RandomSource random) {
        List<ItemStackTemplate> items = new ArrayList<>();
        for (int i = 1 + random.nextInt(6); i > 0; i--) {
            items.add(new ItemStackTemplate(Util.getRandom(StorageScenario.BUNDLED, random), 1 + random.nextInt(8)));
        }
        
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
        return bundle;
    }
    
    private static ItemStack energyCell(RandomSource random) {
        float roll = random.nextFloat();
        ItemStack cell = new ItemStack(Items.SPYGLASS);
        cell.set(TestComponents.ENERGY, roll < 0.25F ? 0L : (long) (roll < 0.6F ? StorageScenario.CELL_CAPACITY : random.nextInt(StorageScenario.CELL_CAPACITY)));
        return cell;
    }
    
    private static ItemStack tank(RandomSource random) {
        float roll = random.nextFloat();
        ItemStack tank = new ItemStack(Items.BUCKET);
        if (roll < 0.2F) {
            tank.set(TestComponents.FLUID, SimpleFluidContent.EMPTY);
            return tank;
        }
        
        int amount = random.nextFloat() < 0.4F ? StorageScenario.TANK_CAPACITY : 1 + random.nextInt(StorageScenario.TANK_CAPACITY);
        tank.set(TestComponents.FLUID, SimpleFluidContent.copyOf(new FluidStack(roll < 0.6F ? Fluids.WATER : Fluids.LAVA, amount)));
        return tank;
    }
    
    private static ItemStack renamed(RandomSource random) {
        ItemStack stack = Loot.randomStack(random, Loot.ORDINARY);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(Util.getRandom(StorageScenario.NAMES, random)));
        return stack;
    }
    
    private ItemStack processed(RandomSource random) {
        if (random.nextFloat() < StorageScenario.MINESHAFT_SHARE) {
            return this.mineshaft.getRandomOrThrow(random).apply(random);
        }
        
        return Loot.randomStack(random, StorageScenario.ORES);
    }
    
    private ItemStack playerGear(RandomSource random) {
        ItemStack gear = new ItemStack(StorageScenario.PLAYER_GEAR.getRandomOrThrow(random));
        if (random.nextFloat() < 0.8F) {
            gear = this.loot.enchantAtCost(random, gear, 30, this.enchantingTable);
        }
        
        gear.setDamageValue(random.nextInt(gear.getMaxDamage() / 2));
        if (random.nextFloat() < 0.1F) {
            gear.set(DataComponents.CUSTOM_NAME, Component.literal(Util.getRandom(StorageScenario.NAMES, random)));
        }
        
        return gear;
    }
    
    private ItemStack storedBook(RandomSource random) {
        if (random.nextBoolean()) {
            return this.loot.enchantRandomly(random, Items.ENCHANTED_BOOK, this.tradeable.getRandomElement(random).orElseThrow());
        }
        
        Holder<Enchantment> traded = this.loot.enchantment(Util.getRandom(StorageScenario.TRADED_BOOKS, random));
        return this.loot.enchant(new ItemStack(Items.ENCHANTED_BOOK), traded, traded.value().getMaxLevel());
    }
}
