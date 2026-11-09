package dev.satherov.nexus.test.game.api.component.scenario;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

///
/// The scenario of modded loot, with gear shaped like Apotheosis's, tools shaped like Tinkers', backpacks, and the items of a modpack.
///
public final class ModdedLootScenario extends SourcedScenario {
    
    private static final int UPGRADE_SLOTS = 3;
    private static final List<Item> TOOLS = List.of(Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_SWORD);
    private static final WeightedList<ToolMaterial> TOOL_MATERIALS = WeightedList.<ToolMaterial>builder()
            .add(new ToolMaterial("tconstruct:wood", new TestComponents.ToolStats(60, 0.0F, 2.0F, "minecraft:wood")), 8)
            .add(new ToolMaterial("tconstruct:rock", new TestComponents.ToolStats(130, 1.0F, 4.0F, "minecraft:stone")), 12)
            .add(new ToolMaterial("tconstruct:flint", new TestComponents.ToolStats(85, 1.25F, 3.5F, "minecraft:stone")), 10)
            .add(new ToolMaterial("tconstruct:copper", new TestComponents.ToolStats(210, 0.5F, 5.0F, "minecraft:iron")), 10)
            .add(new ToolMaterial("tconstruct:iron", new TestComponents.ToolStats(250, 2.0F, 6.0F, "minecraft:iron")), 14)
            .add(new ToolMaterial("tconstruct:pig_iron", new TestComponents.ToolStats(580, 2.5F, 6.0F, "minecraft:iron")), 8)
            .add(new ToolMaterial("tconstruct:rose_gold", new TestComponents.ToolStats(175, 1.0F, 9.0F, "minecraft:gold")), 6)
            .add(new ToolMaterial("tconstruct:slimesteel", new TestComponents.ToolStats(1_040, 2.5F, 6.0F, "minecraft:diamond")), 8)
            .add(new ToolMaterial("tconstruct:cobalt", new TestComponents.ToolStats(800, 2.25F, 6.5F, "minecraft:diamond")), 8)
            .add(new ToolMaterial("tconstruct:manyullyn", new TestComponents.ToolStats(1_250, 3.5F, 6.5F, "minecraft:netherite")), 6)
            .add(new ToolMaterial("tconstruct:hepatizon", new TestComponents.ToolStats(900, 2.5F, 10.0F, "minecraft:netherite")), 5)
            .add(new ToolMaterial("tconstruct:queens_slime", new TestComponents.ToolStats(1_650, 2.0F, 6.0F, "minecraft:netherite")), 5)
            .build();
    private static final List<String> TOOL_UPGRADES = List.of(
            "tconstruct:reinforced",
            "tconstruct:haste",
            "tconstruct:sharpness",
            "tconstruct:luck",
            "tconstruct:magnetic",
            "tconstruct:knockback",
            "tconstruct:diamond",
            "tconstruct:emerald"
    );
    
    private final Loot loot;
    private final HolderSet.Named<Enchantment> randomLoot;
    private final WeightedList<Function<RandomSource, ItemStack>> drops;
    
    public ModdedLootScenario(RegistryAccess registries) {
        this.loot = new Loot(registries);
        this.randomLoot = this.loot.enchantments(EnchantmentTags.ON_RANDOM_LOOT);
        this.drops = WeightedList.<Function<RandomSource, ItemStack>>builder()
                .add(this::affixGear, 5)
                .add(Loot::gem, 1)
                .add(ModdedLootScenario::tinkerTool, 3)
                .add(ModdedLootScenario::backpack, 1)
                .add(Loot::packItem, 3)
                .add(random -> this.loot.enchantAtCost(random, new ItemStack(Items.BOOK), 30, this.randomLoot), 1)
                .build();
        
        Holder<Enchantment> sharpness = this.loot.enchantment(Enchantments.SHARPNESS);
        Holder<Enchantment> unbreaking = this.loot.enchantment(Enchantments.UNBREAKING);
        Holder<Enchantment> vanishing = this.loot.enchantment(Enchantments.VANISHING_CURSE);
        DataComponentType<TestComponents.Tier> tier = TestComponents.tier();
        Identifier common = Loot.RARITIES.get(0);
        Identifier epic = Loot.RARITIES.get(3);
        Identifier mythic = Loot.RARITIES.get(4);
        Identifier violent = Loot.MELEE_AFFIXES.getFirst();
        this.add(
                new Source("modded_loot", 1_000, 5_000, (random, drops) -> drops.add(this.drops.getRandomOrThrow(random).apply(random))),
                new Rule(
                        "modded_loot_epic",
                        SourcedScenario.rarity(epic).or(SourcedScenario.rarity(mythic)),
                        stack -> epic.equals(stack.get(TestComponents.RARITY)) || mythic.equals(stack.get(TestComponents.RARITY)),
                        true
                ),
                new Rule(
                        "modded_loot_violent",
                        SourcedScenario.affix(violent, 0.5D),
                        stack -> SourcedScenario.affixLevel(stack, violent) >= 0.5D,
                        true
                ),
                new Rule(
                        "modded_loot_damaged",
                        SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, 300),
                        stack -> SourcedScenario.damage(stack) > 300,
                        true
                ),
                new Rule(
                        "modded_loot_sockets",
                        SourcedScenario.compare(TestComponents.SOCKETS, "", Comparison.Operator.GREATER_OR_EQUAL, 3),
                        stack -> stack.getOrDefault(TestComponents.SOCKETS, 0) >= 3,
                        false
                ),
                new Rule(
                        "modded_loot_tier",
                        SourcedScenario.compare(tier, "level", Comparison.Operator.GREATER_OR_EQUAL, 3),
                        stack -> stack.get(tier) instanceof TestComponents.Tier(int level, _) && level >= 3,
                        false
                ),
                new Rule(
                        "modded_loot_netherite_tools",
                        SourcedScenario.compare(TestComponents.TOOL, "stats.tier", Comparison.Operator.EQUAL, "minecraft:netherite"),
                        stack -> stack.get(TestComponents.TOOL) instanceof TestComponents.Tool(_, TestComponents.ToolStats(_, _, _, String harvest), _, _, _) &&
                                harvest.equals("minecraft:netherite"),
                        false
                ),
                new Rule(
                        "modded_loot_broken_tools",
                        SourcedScenario.compare(TestComponents.TOOL, "broken", Comparison.Operator.EQUAL, true),
                        stack -> stack.get(TestComponents.TOOL) instanceof TestComponents.Tool(_, _, _, _, boolean broken) && broken,
                        false
                ),
                new Rule(
                        "modded_loot_free_upgrades",
                        SourcedScenario.compare(TestComponents.TOOL, "upgrades", Comparison.Operator.GREATER_OR_EQUAL, 2),
                        stack -> stack.get(TestComponents.TOOL) instanceof TestComponents.Tool(_, _, _, int upgrades, _) && upgrades >= 2,
                        false
                ),
                new Rule(
                        "modded_loot_backpack_diamonds",
                        SourcedScenario.compare(DataComponents.CONTAINER, "[*].item.id", Comparison.Operator.EQUAL, "minecraft:diamond"),
                        stack -> SourcedScenario.holds(stack, DataComponents.CONTAINER, Items.DIAMOND),
                        false
                ),
                new Rule(
                        "modded_loot_relics",
                        SourcedScenario.compare(DataComponents.CUSTOM_DATA, "kubejs.category", Comparison.Operator.EQUAL, "relic"),
                        stack -> SourcedScenario.customData(stack).getCompoundOrEmpty("kubejs").getStringOr("category", "").equals("relic"),
                        false
                ),
                new Rule(
                        "modded_loot_keep",
                        ComponentFilter.anyOf(List.of(
                                SourcedScenario.rarity(mythic),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.rarity(epic),
                                        SourcedScenario.compare(TestComponents.SOCKETS, "", Comparison.Operator.GREATER_OR_EQUAL, 2),
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.VANISHING_CURSE, 1).negate()
                                )),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.affix(violent, 0.8D),
                                        SourcedScenario.compare(DataComponents.DAMAGE, "", Comparison.Operator.GREATER, 500).negate()
                                )),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.SHARPNESS, 5),
                                        SourcedScenario.enchanted(DataComponents.ENCHANTMENTS, Enchantments.UNBREAKING, 3)
                                )),
                                ComponentFilter.allOf(List.of(
                                        SourcedScenario.compare(tier, "level", Comparison.Operator.GREATER_OR_EQUAL, 4),
                                        SourcedScenario.rarity(common).negate()
                                ))
                        )),
                        stack -> {
                            Identifier rarity = stack.get(TestComponents.RARITY);
                            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                            return mythic.equals(rarity) ||
                                    (epic.equals(rarity) && stack.getOrDefault(TestComponents.SOCKETS, 0) >= 2 && enchantments.getLevel(vanishing) == 0) ||
                                    (SourcedScenario.affixLevel(stack, violent) >= 0.8D && SourcedScenario.damage(stack) <= 500) ||
                                    (enchantments.getLevel(sharpness) >= 5 && enchantments.getLevel(unbreaking) >= 3) ||
                                    (stack.get(tier) instanceof TestComponents.Tier(int level, _) && level >= 4 && !common.equals(rarity));
                        },
                        false
                )
        );
    }
    
    private static ItemStack tinkerTool(RandomSource random) {
        ToolMaterial head = ModdedLootScenario.TOOL_MATERIALS.getRandomOrThrow(random);
        List<String> materials = List.of(
                head.id(),
                ModdedLootScenario.TOOL_MATERIALS.getRandomOrThrow(random).id(),
                ModdedLootScenario.TOOL_MATERIALS.getRandomOrThrow(random).id()
        );
        
        List<TestComponents.ToolModifier> modifiers = new ArrayList<>();
        for (int slot = 0; slot < ModdedLootScenario.UPGRADE_SLOTS; slot++) {
            if (random.nextFloat() < 0.45F) {
                modifiers.add(new TestComponents.ToolModifier(Util.getRandom(ModdedLootScenario.TOOL_UPGRADES, random), 1 + random.nextInt(2)));
            }
        }
        
        int durability = head.stats().durability();
        // A Tinkers' tool breaks instead of vanishing once it is worn out, which we take to be every sixth tool in the loot.
        int wear = random.nextInt(durability + durability / 5);
        ItemStack tool = new ItemStack(Util.getRandom(ModdedLootScenario.TOOLS, random));
        tool.set(DataComponents.MAX_DAMAGE, durability);
        tool.setDamageValue(wear);
        tool.set(TestComponents.TOOL, new TestComponents.Tool(
                materials,
                head.stats(),
                List.copyOf(modifiers),
                ModdedLootScenario.UPGRADE_SLOTS - modifiers.size(),
                wear >= durability
        ));
        return tool;
    }
    
    private static ItemStack backpack(RandomSource random) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 5 + random.nextInt(23); slot > 0; slot--) {
            contents.add(Loot.randomStack(random, Loot.ORDINARY));
        }
        
        ListTag upgrades = new ListTag();
        upgrades.add(StringTag.valueOf("pickup"));
        if (random.nextBoolean()) {
            upgrades.add(StringTag.valueOf("magnet"));
        }
        
        CompoundTag backpack = new CompoundTag();
        backpack.putInt("id", Loot.nextSerial());
        backpack.putString("tier", random.nextBoolean() ? "iron" : "gold");
        backpack.put("upgrades", upgrades);
        CompoundTag data = new CompoundTag();
        data.put("backpack", backpack);
        ItemStack stack = new ItemStack(Items.CHEST);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
    
    private ItemStack affixGear(RandomSource random) {
        ItemStack gear = this.loot.affixGear(random);
        if (random.nextFloat() < 0.3F) {
            int level = 1 + random.nextInt(5);
            gear.set(TestComponents.tier(), new TestComponents.Tier(level, "tier_" + level));
        }
        
        return gear;
    }
    
    private record ToolMaterial(String id, TestComponents.ToolStats stats) { }
}
