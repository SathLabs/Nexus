package dev.satherov.nexus.test.game.api.component.scenario;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

///
/// The scenario of farms, with a mob farm, a fishing farm, a raid farm, and a bartering farm.
///
public final class FarmScenario extends SourcedScenario {
    
    private static final int LOOTING = 3;
    private static final float EQUIPMENT_DROP_CHANCE = 0.085F + 0.01F * FarmScenario.LOOTING;
    private static final float RAID_ENCHANT_ODDS = 0.25F;
    private static final float SPAWNER_SHARE = 0.4F;
    private static final DifficultyInstance DIFFICULTY = new DifficultyInstance(Difficulty.HARD, 10_000_000L, 3_600_000L, 1.0F);
    private static final String OMINOUS_BANNER = "block.minecraft.ominous_banner";
    private static final List<BlockPos> SPAWNERS = List.of(
            new BlockPos(-312, 41, 1_088),
            new BlockPos(-298, 41, 1_088),
            new BlockPos(-312, 41, 1_102),
            new BlockPos(-298, 41, 1_102)
    );
    private static final int[] PILLAGERS = { 0, 4, 3, 3, 4, 4, 4, 2 };
    private static final int[] VINDICATORS = { 0, 0, 2, 0, 1, 4, 2, 5 };
    private static final int[] EVOKERS = { 0, 0, 0, 0, 0, 1, 1, 2 };
    private static final int[] WITCHES = { 0, 0, 0, 0, 3, 0, 0, 1 };
    private static final int[] RAVAGERS = { 0, 0, 0, 1, 0, 1, 0, 2 };
    private static final WeightedList<Hostile> HOSTILES = WeightedList.<Hostile>builder()
            .add(Hostile.ZOMBIE, 100)
            .add(Hostile.SKELETON, 80)
            .add(Hostile.STRAY, 20)
            .add(Hostile.SPIDER, 100)
            .add(Hostile.CREEPER, 100)
            .add(Hostile.ENDERMAN, 10)
            .add(Hostile.WITCH, 5)
            .build();
    private static final List<Item> ZOMBIE_RARES = List.of(Items.IRON_INGOT, Items.CARROT, Items.POTATO);
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final WeightedList<Item> WITCH_LOOT = WeightedList.<Item>builder()
            .add(Items.GLOWSTONE_DUST)
            .add(Items.SUGAR)
            .add(Items.SPIDER_EYE)
            .add(Items.GLASS_BOTTLE)
            .add(Items.GUNPOWDER)
            .add(Items.STICK, 2)
            .build();
    private static final WeightedList<Holder<Potion>> WITCH_POTIONS = WeightedList.<Holder<Potion>>builder()
            .add(Potions.HEALING, 7)
            .add(Potions.WATER_BREATHING, 1)
            .add(Potions.FIRE_RESISTANCE, 1)
            .add(Potions.SWIFTNESS, 1)
            .build();
    private static final WeightedList<Species> FISH = WeightedList.<Species>builder()
            .add(new Species(Items.COD, 1.5D, 45.0D), 60)
            .add(new Species(Items.SALMON, 3.5D, 65.0D), 25)
            .add(new Species(Items.TROPICAL_FISH, 0.3D, 12.0D), 2)
            .add(new Species(Items.PUFFERFISH, 1.0D, 30.0D), 13)
            .build();
    private static final WeightedList<Item> FISHING_JUNK = WeightedList.<Item>builder()
            .add(Items.LILY_PAD, 17)
            .add(Items.LEATHER_BOOTS, 10)
            .add(Items.LEATHER, 10)
            .add(Items.BONE, 10)
            .add(Items.POTION, 10)
            .add(Items.STRING, 5)
            .add(Items.FISHING_ROD, 2)
            .add(Items.BOWL, 10)
            .add(Items.STICK, 5)
            .add(Items.INK_SAC, 1)
            .add(Items.TRIPWIRE_HOOK, 10)
            .add(Items.ROTTEN_FLESH, 10)
            .build();
    private static final List<Item> FISHING_TREASURE = List.of(Items.NAME_TAG, Items.SADDLE, Items.BOW, Items.FISHING_ROD, Items.BOOK, Items.NAUTILUS_SHELL);
    
    private final Loot loot;
    private final HolderGetter<BannerPattern> patterns;
    private final HolderSet.Named<Enchantment> randomLoot;
    private final WeightedList<Function<RandomSource, ItemStack>> catches;
    private final WeightedList<Function<RandomSource, ItemStack>> barters;
    
    public FarmScenario(RegistryAccess registries) {
        this.loot = new Loot(registries);
        this.patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        this.randomLoot = this.loot.enchantments(EnchantmentTags.ON_RANDOM_LOOT);
        this.catches = WeightedList.<Function<RandomSource, ItemStack>>builder()
                .add(FarmScenario::fish, 85)
                .add(FarmScenario::fishingJunk, 4)
                .add(this::fishingTreasure, 11)
                .build();
        
        Holder<Enchantment> soulSpeed = this.loot.enchantment(Enchantments.SOUL_SPEED);
        this.barters = WeightedList.<Function<RandomSource, ItemStack>>builder()
                .add(random -> this.loot.enchantRandomly(random, Items.ENCHANTED_BOOK, soulSpeed), 5)
                .add(random -> this.loot.enchantRandomly(random, Items.IRON_BOOTS, soulSpeed), 8)
                .add(_ -> PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE), 8)
                .add(_ -> PotionContents.createItemStack(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE), 8)
                .add(_ -> PotionContents.createItemStack(Items.POTION, Potions.WATER), 10)
                .add(random -> Loot.stack(random, Items.IRON_NUGGET, 10, 36), 10)
                .add(random -> Loot.stack(random, Items.ENDER_PEARL, 2, 4), 10)
                .add(_ -> new ItemStack(Items.DRIED_GHAST), 10)
                .add(random -> Loot.stack(random, Items.STRING, 3, 9), 20)
                .add(random -> Loot.stack(random, Items.QUARTZ, 5, 12), 20)
                .add(_ -> new ItemStack(Items.OBSIDIAN), 40)
                .add(random -> Loot.stack(random, Items.CRYING_OBSIDIAN, 1, 3), 40)
                .add(_ -> new ItemStack(Items.FIRE_CHARGE), 40)
                .add(random -> Loot.stack(random, Items.LEATHER, 2, 4), 40)
                .add(random -> Loot.stack(random, Items.SOUL_SAND, 2, 8), 40)
                .add(random -> Loot.stack(random, Items.NETHER_BRICK, 2, 8), 40)
                .add(random -> Loot.stack(random, Items.SPECTRAL_ARROW, 6, 12), 40)
                .add(random -> Loot.stack(random, Items.GRAVEL, 8, 16), 40)
                .add(random -> Loot.stack(random, Items.BLACKSTONE, 8, 16), 40)
                .build();
        
        Holder<Enchantment> protection = this.loot.enchantment(Enchantments.PROTECTION);
        Holder<Enchantment> sharpness = this.loot.enchantment(Enchantments.SHARPNESS);
        Holder<Enchantment> power = this.loot.enchantment(Enchantments.POWER);
        Holder<Enchantment> mending = this.loot.enchantment(Enchantments.MENDING);
        Holder<Enchantment> unbreaking = this.loot.enchantment(Enchantments.UNBREAKING);
        Holder<Enchantment> vanishing = this.loot.enchantment(Enchantments.VANISHING_CURSE);
        Holder<Enchantment> quickCharge = this.loot.enchantment(Enchantments.QUICK_CHARGE);
        Holder<Enchantment> multishot = this.loot.enchantment(Enchantments.MULTISHOT);
        PotionContents healing = new PotionContents(Potions.HEALING);
        this.add(
                new Source("mob_farm", 3_000, 20_000, this::mobKill),
                new Rule(
                        "mob_farm_damaged",
                        SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, 100),
                        stack -> SourcedScenario.damage(stack) > 100,
                        true
                ),
                new Rule(
                        "mob_farm_slowness",
                        SourcedScenario.compare(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, "minecraft:slowness"),
                        stack -> SourcedScenario.isPotion(stack, Potions.SLOWNESS),
                        true
                ),
                new Rule(
                        "mob_farm_enchanted",
                        ComponentFilter.matching(
                                DataComponentPredicates.ENCHANTMENTS,
                                EnchantmentsPredicate.enchantments(List.of(new EnchantmentPredicate(Optional.empty(), MinMaxBounds.Ints.ANY)))
                        ),
                        stack -> !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                        false
                ),
                new Rule(
                        "mob_farm_spawner",
                        SourcedScenario.compare(DataComponents.CUSTOM_DATA, "spawner.entity", Comparison.Operator.EQUAL, "minecraft:zombie"),
                        stack -> SourcedScenario.customData(stack).getCompoundOrEmpty("spawner").getStringOr("entity", "").equals("minecraft:zombie"),
                        false
                ),
                new Rule(
                        "mob_farm_keep",
                        ComponentFilter.anyOf(List.of(
                                FarmScenario.unworn(SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.PROTECTION, 3), 150),
                                FarmScenario.unworn(SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.SHARPNESS, 3), 150),
                                FarmScenario.unworn(SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.POWER, 4), 300),
                                SourcedScenario.compare(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, "minecraft:slowness")
                        )),
                        stack -> {
                            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                            int damage = SourcedScenario.damage(stack);
                            return (enchantments.getLevel(protection) >= 3 && damage <= 150) ||
                                    (enchantments.getLevel(sharpness) >= 3 && damage <= 150) ||
                                    (enchantments.getLevel(power) >= 4 && damage <= 300) ||
                                    SourcedScenario.isPotion(stack, Potions.SLOWNESS);
                        },
                        false
                )
        );
        this.add(
                new Source("fishing_farm", 1_000, 20_000, (random, drops) -> drops.add(this.catches.getRandomOrThrow(random).apply(random))),
                new Rule(
                        "fishing_farm_mending_books",
                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.MENDING, 1),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, mending) >= 1,
                        true
                ),
                new Rule(
                        "fishing_farm_unbreaking_books",
                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.UNBREAKING, 3),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, unbreaking) >= 3,
                        true
                ),
                new Rule(
                        "fishing_farm_damaged",
                        SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, 40),
                        stack -> SourcedScenario.damage(stack) > 40,
                        true
                ),
                new Rule(
                        "fishing_farm_trophy",
                        SourcedScenario.compare(DataComponents.CUSTOM_DATA, "fishing.weight", Comparison.Operator.GREATER_OR_EQUAL, 5),
                        stack -> SourcedScenario.customData(stack).getCompoundOrEmpty("fishing").getDoubleOr("weight", 0.0D) >= 5.0D,
                        false
                ),
                new Rule(
                        "fishing_farm_keep",
                        ComponentFilter.anyOf(List.of(
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.MENDING, 1),
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.VANISHING_CURSE, 1).negate()
                                )),
                                SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.MENDING, 1),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.UNBREAKING, 3),
                                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.VANISHING_CURSE, 1).negate()
                                )),
                                SourcedScenario.compare(DataComponents.CUSTOM_DATA, "fishing.weight", Comparison.Operator.GREATER_OR_EQUAL, 8)
                        )),
                        stack -> {
                            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                            return (enchantments.getLevel(mending) >= 1 && enchantments.getLevel(vanishing) == 0) ||
                                    stored.getLevel(mending) >= 1 ||
                                    (stored.getLevel(unbreaking) >= 3 && stored.getLevel(vanishing) == 0) ||
                                    SourcedScenario.customData(stack).getCompoundOrEmpty("fishing").getDoubleOr("weight", 0.0D) >= 8.0D;
                        },
                        false
                )
        );
        this.add(
                new Source("raid_farm", 2_000, 20_000, this::raidWave),
                new Rule(
                        "raid_farm_ominous",
                        SourcedScenario.compare(DataComponents.ITEM_NAME, "translate", Comparison.Operator.EQUAL, FarmScenario.OMINOUS_BANNER),
                        stack -> stack.get(DataComponents.ITEM_NAME) instanceof Component name &&
                                name.getContents() instanceof TranslatableContents contents &&
                                contents.getKey().equals(FarmScenario.OMINOUS_BANNER),
                        false
                ),
                new Rule(
                        "raid_farm_healing",
                        ComponentFilter.exact(DataComponents.POTION_CONTENTS, healing),
                        stack -> healing.equals(stack.get(DataComponents.POTION_CONTENTS)),
                        true
                ),
                new Rule(
                        "raid_farm_sharp_axes",
                        ComponentFilter.allOf(List.of(
                                SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.SHARPNESS, 1),
                                SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.LESS, 150)
                        )),
                        stack -> SourcedScenario.level(stack, DataComponents.ENCHANTMENTS, sharpness) >= 1 &&
                                stack.get(DataComponents.DAMAGE) instanceof Integer damage &&
                                damage < 150,
                        false
                ),
                new Rule(
                        "raid_farm_keep",
                        ComponentFilter.anyOf(List.of(
                                FarmScenario.unworn(SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.QUICK_CHARGE, 2), 300),
                                FarmScenario.unworn(SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.SHARPNESS, 2), 200),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.MULTISHOT, 1),
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.UNBREAKING, 2)
                                )),
                                ComponentFilter.exact(DataComponents.POTION_CONTENTS, healing)
                        )),
                        stack -> {
                            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                            int damage = SourcedScenario.damage(stack);
                            return (enchantments.getLevel(quickCharge) >= 2 && damage <= 300) ||
                                    (enchantments.getLevel(sharpness) >= 2 && damage <= 200) ||
                                    (enchantments.getLevel(multishot) >= 1 && enchantments.getLevel(unbreaking) >= 2) ||
                                    healing.equals(stack.get(DataComponents.POTION_CONTENTS));
                        },
                        false
                )
        );
        this.add(
                new Source("bartering_farm", 2_000, 10_000, (random, drops) -> drops.add(this.barters.getRandomOrThrow(random).apply(random))),
                new Rule(
                        "bartering_farm_fire_resistance",
                        SourcedScenario.compare(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, "minecraft:fire_resistance"),
                        stack -> SourcedScenario.isPotion(stack, Potions.FIRE_RESISTANCE),
                        true
                ),
                new Rule(
                        "bartering_farm_soul_speed_books",
                        SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.SOUL_SPEED, 3),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, soulSpeed) >= 3,
                        true
                ),
                new Rule(
                        "bartering_farm_keep",
                        ComponentFilter.anyOf(List.of(
                                SourcedScenario.enchanted(DataComponents.STORED_ENCHANTMENTS, Enchantments.SOUL_SPEED, 2),
                                SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.SOUL_SPEED, 3),
                                SourcedScenario.compare(DataComponents.POTION_CONTENTS, "potion", Comparison.Operator.EQUAL, "minecraft:fire_resistance")
                        )),
                        stack -> SourcedScenario.level(stack, DataComponents.STORED_ENCHANTMENTS, soulSpeed) >= 2 ||
                                SourcedScenario.level(stack, DataComponents.ENCHANTMENTS, soulSpeed) >= 3 ||
                                SourcedScenario.isPotion(stack, Potions.FIRE_RESISTANCE),
                        false
                )
        );
    }
    
    private static ComponentFilter unworn(ComponentFilter filter, int damage) {
        return filter.and(SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, damage).negate());
    }
    
    private static int count(RandomSource random, int min, int max) {
        return random.nextIntBetweenInclusive(min, max) + Math.round(FarmScenario.LOOTING * random.nextFloat());
    }
    
    private static void loot(RandomSource random, Item item, int min, int max, List<ItemStack> drops) {
        int count = FarmScenario.count(random, min, max);
        if (count > 0) {
            drops.add(new ItemStack(item, count));
        }
    }
    
    private static void damageOnDrop(RandomSource random, ItemStack equipment) {
        int max = equipment.getMaxDamage();
        equipment.setDamageValue(max - random.nextInt(1 + random.nextInt(Math.max(max - 3, 1))));
    }
    
    private static void wear(RandomSource random, ItemStack stack, float durability) {
        stack.setDamageValue(Mth.floor((1.0F - durability * random.nextFloat()) * stack.getMaxDamage()));
    }
    
    private static void witch(RandomSource random, List<ItemStack> drops) {
        for (int roll = random.nextIntBetweenInclusive(1, 3); roll > 0; roll--) {
            FarmScenario.loot(random, FarmScenario.WITCH_LOOT.getRandomOrThrow(random), 0, 2, drops);
        }
        
        FarmScenario.loot(random, Items.REDSTONE, 4, 8, drops);
        // A witch only drops a potion if it dies while drinking one, which we take to be every other witch.
        if (random.nextBoolean() && random.nextFloat() < FarmScenario.EQUIPMENT_DROP_CHANCE) {
            drops.add(PotionContents.createItemStack(Items.POTION, FarmScenario.WITCH_POTIONS.getRandomOrThrow(random)));
        }
    }
    
    private static ItemStack fish(RandomSource random) {
        Species species = FarmScenario.FISH.getRandomOrThrow(random);
        double size = Math.exp(0.5D * random.nextGaussian());
        CompoundTag fishing = new CompoundTag();
        fishing.putDouble("weight", Math.round(species.kilograms() * size * 100.0D) / 100.0D);
        fishing.putDouble("length", Math.round(species.centimeters() * Math.cbrt(size) * 10.0D) / 10.0D);
        CompoundTag data = new CompoundTag();
        data.put("fishing", fishing);
        ItemStack fish = new ItemStack(species.item());
        fish.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return fish;
    }
    
    private static ItemStack fishingJunk(RandomSource random) {
        Item junk = FarmScenario.FISHING_JUNK.getRandomOrThrow(random);
        if (junk == Items.POTION) {
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        
        ItemStack stack = new ItemStack(junk, junk == Items.INK_SAC ? 10 : 1);
        if (stack.isDamageableItem()) {
            FarmScenario.wear(random, stack, 0.9F);
        }
        
        return stack;
    }
    
    private void mobKill(RandomSource random, List<ItemStack> drops) {
        Hostile hostile = FarmScenario.HOSTILES.getRandomOrThrow(random);
        int first = drops.size();
        switch (hostile) {
            case ZOMBIE -> {
                FarmScenario.loot(random, Items.ROTTEN_FLESH, 0, 2, drops);
                if (random.nextFloat() < 0.025F + 0.01F * FarmScenario.LOOTING) {
                    drops.add(new ItemStack(Util.getRandom(FarmScenario.ZOMBIE_RARES, random)));
                }
                
                this.armor(random, drops);
                if (random.nextFloat() < 0.05F) {
                    int weapon = random.nextInt(6);
                    this.equipment(random, new ItemStack(weapon == 0 ? Items.IRON_SWORD : (weapon == 1 ? Items.IRON_SPEAR : Items.IRON_SHOVEL)), 0.25F, drops);
                }
            }
            case SKELETON, STRAY -> {
                FarmScenario.loot(random, Items.ARROW, 0, 2, drops);
                FarmScenario.loot(random, Items.BONE, 0, 2, drops);
                int tipped = hostile == Hostile.STRAY ? FarmScenario.count(random, 0, 1) : 0;
                if (tipped > 0) {
                    ItemStack arrows = new ItemStack(Items.TIPPED_ARROW, tipped);
                    arrows.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
                    drops.add(arrows);
                }
                
                this.armor(random, drops);
                this.equipment(random, new ItemStack(Items.BOW), 0.25F, drops);
            }
            case SPIDER -> {
                FarmScenario.loot(random, Items.STRING, 0, 2, drops);
                FarmScenario.loot(random, Items.SPIDER_EYE, -1, 1, drops);
            }
            case CREEPER -> FarmScenario.loot(random, Items.GUNPOWDER, 0, 2, drops);
            case ENDERMAN -> FarmScenario.loot(random, Items.ENDER_PEARL, 0, 1, drops);
            case WITCH -> FarmScenario.witch(random, drops);
        }
        
        List<ItemStack> kill = drops.subList(first, drops.size());
        for (ItemStack drop : kill) {
            if (drop.isDamageableItem()) {
                drop.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("lore.loot_origin.dropped_by", hostile.type().getDescription()))));
            }
        }
        
        if (random.nextFloat() >= FarmScenario.SPAWNER_SHARE) {
            return;
        }
        
        BlockPos pos = Util.getRandom(FarmScenario.SPAWNERS, random);
        CompoundTag spawner = new CompoundTag();
        spawner.putString("entity", EntityType.getKey(hostile.type()).toString());
        spawner.putIntArray("pos", new int[]{ pos.getX(), pos.getY(), pos.getZ() });
        CompoundTag data = new CompoundTag();
        data.put("spawner", spawner);
        for (ItemStack drop : kill) {
            drop.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }
    
    private void armor(RandomSource random, List<ItemStack> drops) {
        if (random.nextFloat() >= 0.15F) {
            return;
        }
        
        int type = random.nextInt(3);
        for (int i = 0; i < 3; i++) {
            if (random.nextFloat() < 0.1087F) {
                type++;
            }
        }
        
        for (EquipmentSlot slot : FarmScenario.ARMOR_SLOTS) {
            if (slot != EquipmentSlot.HEAD && random.nextFloat() < 0.1F) {
                return;
            }
            
            this.equipment(random, new ItemStack(Objects.requireNonNull(Mob.getEquipmentForSlot(slot, type))), 0.5F, drops);
        }
    }
    
    private void equipment(RandomSource random, ItemStack equipment, float enchantChance, List<ItemStack> drops) {
        // We roll the drop before vanilla's spawn enchanting, so equipment that never drops is never enchanted.
        if (random.nextFloat() >= FarmScenario.EQUIPMENT_DROP_CHANCE) {
            return;
        }
        
        if (random.nextFloat() < enchantChance) {
            this.loot.enchantFromProvider(random, equipment, VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, FarmScenario.DIFFICULTY);
        }
        
        FarmScenario.damageOnDrop(random, equipment);
        drops.add(equipment);
    }
    
    private ItemStack fishingTreasure(RandomSource random) {
        ItemStack treasure = new ItemStack(Util.getRandom(FarmScenario.FISHING_TREASURE, random));
        if (treasure.isDamageableItem()) {
            FarmScenario.wear(random, treasure, 0.25F);
        }
        
        if (treasure.isDamageableItem() || treasure.is(Items.BOOK)) {
            return this.loot.enchantAtCost(random, treasure, 30, this.randomLoot);
        }
        
        return treasure;
    }
    
    private void raidWave(RandomSource random, List<ItemStack> drops) {
        int wave = 1 + random.nextInt(8);
        int group = Math.min(wave, 7);
        drops.add(Raid.getOminousBannerInstance(this.patterns));
        for (int i = FarmScenario.PILLAGERS[group] + random.nextInt(3); i > 0; i--) {
            this.pillager(random, wave, drops);
        }
        
        for (int i = FarmScenario.VINDICATORS[group] + random.nextInt(3); i > 0; i--) {
            this.vindicator(random, wave, drops);
        }
        
        for (int i = FarmScenario.EVOKERS[group]; i > 0; i--) {
            drops.add(new ItemStack(Items.TOTEM_OF_UNDYING));
            FarmScenario.loot(random, Items.EMERALD, 0, 1, drops);
        }
        
        for (int i = FarmScenario.WITCHES[group] + (wave > 2 && wave != 4 ? random.nextInt(2) : 0); i > 0; i--) {
            FarmScenario.witch(random, drops);
        }
        
        for (int i = FarmScenario.RAVAGERS[group]; i > 0; i--) {
            drops.add(new ItemStack(Items.SADDLE));
        }
    }
    
    private void pillager(RandomSource random, int wave, List<ItemStack> drops) {
        if (random.nextFloat() >= FarmScenario.EQUIPMENT_DROP_CHANCE) {
            return;
        }
        
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        if (wave > 3 && random.nextFloat() <= FarmScenario.RAID_ENCHANT_ODDS) {
            ResourceKey<EnchantmentProvider> provider = wave > 5 ? VanillaEnchantmentProviders.RAID_PILLAGER_POST_WAVE_5 : VanillaEnchantmentProviders.RAID_PILLAGER_POST_WAVE_3;
            this.loot.enchantFromProvider(random, crossbow, provider, FarmScenario.DIFFICULTY);
        } else if (random.nextFloat() < 0.25F) {
            this.loot.enchantFromProvider(random, crossbow, VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, FarmScenario.DIFFICULTY);
        }
        
        if (random.nextFloat() < 0.3F) {
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStackTemplate(Items.ARROW)));
        }
        
        FarmScenario.damageOnDrop(random, crossbow);
        drops.add(crossbow);
    }
    
    private void vindicator(RandomSource random, int wave, List<ItemStack> drops) {
        FarmScenario.loot(random, Items.EMERALD, 0, 1, drops);
        if (random.nextFloat() >= FarmScenario.EQUIPMENT_DROP_CHANCE) {
            return;
        }
        
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        if (random.nextFloat() <= FarmScenario.RAID_ENCHANT_ODDS) {
            ResourceKey<EnchantmentProvider> provider = wave > 5 ? VanillaEnchantmentProviders.RAID_VINDICATOR_POST_WAVE_5 : VanillaEnchantmentProviders.RAID_VINDICATOR;
            this.loot.enchantFromProvider(random, axe, provider, FarmScenario.DIFFICULTY);
        }
        
        FarmScenario.damageOnDrop(random, axe);
        drops.add(axe);
    }
    
    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    private enum Hostile {
        ZOMBIE(EntityType.ZOMBIE),
        SKELETON(EntityType.SKELETON),
        STRAY(EntityType.STRAY),
        SPIDER(EntityType.SPIDER),
        CREEPER(EntityType.CREEPER),
        ENDERMAN(EntityType.ENDERMAN),
        WITCH(EntityType.WITCH);
        
        private final EntityType<?> type;
    }
    
    private record Species(Item item, double kilograms, double centimeters) { }
}
