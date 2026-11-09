package dev.satherov.nexus.test.game.api.component.scenario;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Builds the item stacks of the scenarios the way the game and the mods would.
///
/// Every item stack holds its own instances of its component values.
/// Every enchantment map gets levels on three enchantments that no rule reads, taken from a serial that counts up from 1,024 and never resets.
///
public final class Loot {
    
    public static final List<Identifier> RARITIES = Loot.ids("common", "uncommon", "rare", "epic", "mythic");
    public static final List<Identifier> GEMS = Loot.ids(
            "core/ballast",
            "core/breach",
            "core/brawlers",
            "core/combatant",
            "core/guardian",
            "core/lightning",
            "core/lunar",
            "core/samurai",
            "core/slipstream",
            "core/solar",
            "core/splendor",
            "core/tyrannical",
            "core/warlord",
            "overworld/earth",
            "overworld/royalty",
            "overworld/verdant_ruin",
            "the_nether/blood_lord",
            "the_nether/inferno",
            "the_nether/molten_breach",
            "the_end/endersurge",
            "the_end/mageslayer"
    );
    public static final List<Identifier> MELEE_AFFIXES = Loot.ids(
            "melee/attribute/violent",
            "melee/attribute/berserking",
            "melee/attribute/forceful",
            "melee/attribute/giant_slaying",
            "melee/attribute/graceful",
            "melee/attribute/intricate",
            "melee/attribute/lacerating",
            "melee/attribute/murderous",
            "melee/attribute/piercing",
            "melee/attribute/vampiric",
            "melee/mob_effect/elusive",
            "melee/mob_effect/weakening",
            "melee/cleaving",
            "melee/executing",
            "melee/festive",
            "melee/thunderstruck"
    );
    public static final WeightedList<Item> ORDINARY = WeightedList.<Item>builder()
            .add(Items.COBBLESTONE, 10)
            .add(Items.OAK_LOG, 6)
            .add(Items.SPRUCE_PLANKS, 5)
            .add(Items.GLASS, 3)
            .add(Items.TORCH, 4)
            .add(Items.BREAD, 4)
            .add(Items.COOKED_BEEF, 4)
            .add(Items.IRON_INGOT, 5)
            .add(Items.GOLD_INGOT, 3)
            .add(Items.REDSTONE, 5)
            .add(Items.COAL, 5)
            .add(Items.LAPIS_LAZULI, 2)
            .add(Items.DIAMOND, 2)
            .add(Items.EMERALD, 2)
            .add(Items.STRING, 3)
            .add(Items.BONE, 3)
            .add(Items.ROTTEN_FLESH, 3)
            .add(Items.GUNPOWDER, 2)
            .add(Items.ARROW, 4)
            .add(Items.GRAVEL, 3)
            .build();
    
    private static final List<ResourceKey<Enchantment>> DIGITS = List.of(Enchantments.LURE, Enchantments.LUCK_OF_THE_SEA, Enchantments.RIPTIDE);
    private static final int DIGIT_BASE = 255;
    private static final int[] RARITY_COLORS = { 0x808080, 0x33FF33, 0x5555FF, 0xBB00BB, 0xED7014 };
    private static final WeightedList<Integer> RARITY_ROLLS = WeightedList.<Integer>builder()
            .add(0, 40)
            .add(1, 30)
            .add(2, 18)
            .add(3, 9)
            .add(4, 3)
            .build();
    private static final WeightedList<TestComponents.Purity> PURITY_ROLLS = WeightedList.<TestComponents.Purity>builder()
            .add(TestComponents.Purity.CHIPPED, 80)
            .add(TestComponents.Purity.FLAWED, 310)
            .add(TestComponents.Purity.NORMAL, 580)
            .add(TestComponents.Purity.FLAWLESS, 20)
            .add(TestComponents.Purity.PERFECT, 10)
            .build();
    private static final List<Identifier> ARMOR_AFFIXES = Loot.ids(
            "armor/attribute/adamantine",
            "armor/attribute/blessed",
            "armor/attribute/elastic",
            "armor/attribute/fortunate",
            "armor/attribute/ironforged",
            "armor/attribute/stalwart",
            "armor/attribute/steel_touched",
            "armor/attribute/windswept",
            "armor/dmg_reduction/blast_forged",
            "armor/dmg_reduction/runed",
            "armor/mob_effect/nimble",
            "armor/mob_effect/revitalizing"
    );
    private static final List<Identifier> BREAKER_AFFIXES = Loot.ids(
            "breaker/attribute/destructive",
            "breaker/attribute/experienced",
            "breaker/attribute/lengthy",
            "breaker/ability/enlightened",
            "breaker/effect/radial",
            "breaker/enchantment/prosperous",
            "breaker/mob_effect/spelunkers",
            "breaker/mob_effect/swift"
    );
    private static final WeightedList<AffixGear> AFFIX_GEAR = WeightedList.<AffixGear>builder()
            .add(new AffixGear(Items.DIAMOND_SWORD, Loot.MELEE_AFFIXES), 20)
            .add(new AffixGear(Items.NETHERITE_SWORD, Loot.MELEE_AFFIXES), 10)
            .add(new AffixGear(Items.NETHERITE_AXE, Loot.MELEE_AFFIXES), 10)
            .add(new AffixGear(Items.DIAMOND_PICKAXE, Loot.BREAKER_AFFIXES), 10)
            .add(new AffixGear(Items.DIAMOND_CHESTPLATE, Loot.ARMOR_AFFIXES), 15)
            .add(new AffixGear(Items.NETHERITE_HELMET, Loot.ARMOR_AFFIXES), 10)
            .add(new AffixGear(Items.DIAMOND_BOOTS, Loot.ARMOR_AFFIXES), 5)
            .build();
    private static final WeightedList<PackItem> PACK_ITEMS = WeightedList.<PackItem>builder()
            .add(new PackItem("currency", Items.GOLD_NUGGET), 40)
            .add(new PackItem("quest", Items.PAPER), 30)
            .add(new PackItem("key", Items.TRIPWIRE_HOOK), 20)
            .add(new PackItem("relic", Items.HEART_OF_THE_SEA), 10)
            .build();
    private static final List<String> PREFIXES = List.of("Violent", "Elusive", "Graceful", "Murderous", "Vampiric", "Festive");
    private static final List<String> SUFFIXES = List.of("of the Depths", "of Ruin", "of the Forgotten", "of Embers", "of the Wilds");
    
    private static int serial = 1_024;
    
    private final RegistryAccess registries;
    private final HolderLookup.RegistryLookup<Enchantment> enchantments;
    private final List<Holder<Enchantment>> digits;
    
    public Loot(RegistryAccess registries) {
        this.registries = registries;
        this.enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        this.digits = Loot.DIGITS
                .stream()
                .<Holder<Enchantment>>map(this.enchantments::getOrThrow)
                .toList();
    }
    
    private static List<Identifier> ids(String... paths) {
        List<Identifier> ids = new ArrayList<>();
        for (String path : paths) {
            ids.add(Identifier.fromNamespaceAndPath("apotheosis", path));
        }
        
        return List.copyOf(ids);
    }
    
    public static int nextSerial() {
        return Loot.serial++;
    }
    
    public static ItemStack stack(RandomSource random, Item item, int min, int max) {
        return new ItemStack(item, random.nextIntBetweenInclusive(min, max));
    }
    
    public static ItemStack randomStack(RandomSource random, WeightedList<Item> items) {
        Item item = items.getRandomOrThrow(random);
        return new ItemStack(item, 1 + random.nextInt(item.getDefaultMaxStackSize()));
    }
    
    public static ItemStack gem(RandomSource random) {
        ItemStack gem = new ItemStack(Items.EMERALD);
        gem.set(TestComponents.GEM, Util.getRandom(Loot.GEMS, random));
        gem.set(TestComponents.PURITY, Loot.PURITY_ROLLS.getRandomOrThrow(random));
        return gem;
    }
    
    public static ItemStack packItem(RandomSource random) {
        PackItem pack = Loot.PACK_ITEMS.getRandomOrThrow(random);
        CompoundTag kubejs = new CompoundTag();
        kubejs.putString("category", pack.category());
        kubejs.putString("id", "pack:" + pack.category() + "_" + random.nextInt(8));
        CompoundTag data = new CompoundTag();
        data.put("kubejs", kubejs);
        ItemStack stack = new ItemStack(pack.item(), 1 + random.nextInt(pack.item().getDefaultMaxStackSize()));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Pack item"))));
        return stack;
    }
    
    public Holder<Enchantment> enchantment(ResourceKey<Enchantment> enchantment) {
        return this.enchantments.getOrThrow(enchantment);
    }
    
    public HolderSet.Named<Enchantment> enchantments(TagKey<Enchantment> tag) {
        return this.enchantments.getOrThrow(tag);
    }
    
    public ItemStack enchant(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        stack.enchant(enchantment, level);
        return this.withDigits(stack);
    }
    
    public ItemStack enchantRandomly(RandomSource random, Item item, Holder<Enchantment> enchantment) {
        return this.enchant(new ItemStack(item), enchantment, random.nextIntBetweenInclusive(enchantment.value().getMinLevel(), enchantment.value().getMaxLevel()));
    }
    
    public ItemStack enchantAtCost(RandomSource random, ItemStack stack, int cost, HolderSet<Enchantment> options) {
        return this.withDigits(EnchantmentHelper.enchantItem(random, stack, cost, this.registries, Optional.of(options)));
    }
    
    public ItemStack enchantFromProvider(RandomSource random, ItemStack stack, ResourceKey<EnchantmentProvider> provider, DifficultyInstance difficulty) {
        EnchantmentHelper.enchantItemFromProvider(stack, this.registries, provider, difficulty, random);
        return this.withDigits(stack);
    }
    
    private ItemStack withDigits(ItemStack stack) {
        int serial = Loot.nextSerial();
        EnchantmentHelper.updateEnchantments(stack, enchantments -> {
            if (enchantments.keySet().isEmpty()) {
                return;
            }
            
            // Vanilla's encoder cache matches enchantment maps by equality, so no two maps may be equal.
            int digit = serial;
            for (Holder<Enchantment> enchantment : this.digits) {
                enchantments.set(enchantment, 1 + digit % Loot.DIGIT_BASE);
                digit /= Loot.DIGIT_BASE;
            }
        });
        
        return stack;
    }
    
    public ItemStack affixGear(RandomSource random) {
        AffixGear gear = Loot.AFFIX_GEAR.getRandomOrThrow(random);
        int rarity = Loot.RARITY_ROLLS.getRandomOrThrow(random);
        ItemStack stack = new ItemStack(gear.item());
        if (random.nextFloat() < 0.6F) {
            stack = this.enchantAtCost(random, stack, 10 + random.nextInt(51), this.enchantments(EnchantmentTags.ON_RANDOM_LOOT));
        }
        
        Map<Identifier, Float> affixes = new HashMap<>();
        for (int i = 1 + rarity + random.nextInt(2); i > 0; i--) {
            affixes.put(Util.getRandom(gear.affixes(), random), random.nextFloat());
        }
        
        int sockets = Math.min(4, random.nextInt(2 + rarity));
        List<ItemStack> gems = new ArrayList<>();
        for (int i = random.nextInt(sockets + 1); i > 0; i--) {
            gems.add(Loot.gem(random));
        }
        
        stack.set(TestComponents.RARITY, Loot.RARITIES.get(rarity));
        stack.set(TestComponents.AFFIXES, Map.copyOf(affixes));
        MutableComponent name = Component.translatable("%s %s", Util.getRandom(Loot.PREFIXES, random), Util.getRandom(Loot.SUFFIXES, random));
        stack.set(TestComponents.AFFIX_NAME, name.withColor(Loot.RARITY_COLORS[rarity]));
        if (sockets > 0) {
            stack.set(TestComponents.SOCKETS, sockets);
        }
        
        if (!gems.isEmpty()) {
            stack.set(TestComponents.SOCKETED_GEMS, ItemContainerContents.fromItems(gems));
        }
        
        stack.setDamageValue(random.nextInt(stack.getMaxDamage() / 3));
        return stack;
    }
    
    private record AffixGear(Item item, List<Identifier> affixes) { }
    
    private record PackItem(String category, Item item) { }
}
