package dev.satherov.nexus.test.game.api.component.scenario;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

///
/// The scenario of Apotheosis, with gear sorted by rarity and affix and gems sorted by purity, loose and in sockets.
///
public final class ApotheosisScenario extends SourcedScenario {
    
    private static final String PURITY = "[*].item.components['apotheosis:purity']";
    private static final String GEM = "[*].item.components['apotheosis:gem']";
    private static final Set<TestComponents.Purity> FLAWLESS_OR_BETTER = EnumSet.of(TestComponents.Purity.FLAWLESS, TestComponents.Purity.PERFECT);
    
    private final Loot loot;
    
    public ApotheosisScenario(RegistryAccess registries) {
        this.loot = new Loot(registries);
        
        Identifier common = Loot.RARITIES.get(0);
        Identifier uncommon = Loot.RARITIES.get(1);
        Identifier rare = Loot.RARITIES.get(2);
        Identifier epic = Loot.RARITIES.get(3);
        Identifier mythic = Loot.RARITIES.get(4);
        Identifier violent = Loot.MELEE_AFFIXES.getFirst();
        Identifier solar = Identifier.fromNamespaceAndPath("apotheosis", "core/solar");
        this.add(
                new Source("apotheosis", 1_000, 10_000, (random, drops) -> drops.add(random.nextInt(10) < 7 ? this.loot.affixGear(random) : Loot.gem(random))),
                new Rule(
                        "apotheosis_mythic",
                        SourcedScenario.rarity(mythic),
                        stack -> mythic.equals(stack.get(TestComponents.RARITY)),
                        true
                ),
                new Rule(
                        "apotheosis_rare_or_better",
                        ComponentFilter.anyOf(List.of(SourcedScenario.rarity(rare), SourcedScenario.rarity(epic), SourcedScenario.rarity(mythic))),
                        stack -> stack.get(TestComponents.RARITY) instanceof Identifier rarity && List.of(rare, epic, mythic).contains(rarity),
                        true
                ),
                new Rule(
                        "apotheosis_violent",
                        SourcedScenario.affix(violent, 0.5D),
                        stack -> SourcedScenario.affixLevel(stack, violent) >= 0.5D,
                        true
                ),
                new Rule(
                        "apotheosis_strong_affix",
                        SourcedScenario.compare(TestComponents.AFFIXES, "[*]", Comparison.Operator.GREATER_OR_EQUAL, 0.9D),
                        stack -> ApotheosisScenario.hasAffixOf(stack, 0.9D),
                        true
                ),
                new Rule(
                        "apotheosis_perfect_gems",
                        SourcedScenario.compare(TestComponents.PURITY, "", Comparison.Operator.EQUAL, "perfect"),
                        stack -> stack.get(TestComponents.PURITY) == TestComponents.Purity.PERFECT,
                        true
                ),
                new Rule(
                        "apotheosis_socketed_flawless",
                        ComponentFilter.anyOf(List.of(
                                SourcedScenario.compare(TestComponents.SOCKETED_GEMS, ApotheosisScenario.PURITY, Comparison.Operator.EQUAL, "flawless"),
                                SourcedScenario.compare(TestComponents.SOCKETED_GEMS, ApotheosisScenario.PURITY, Comparison.Operator.EQUAL, "perfect")
                        )),
                        stack -> ApotheosisScenario.socketsGem(stack, gem -> ApotheosisScenario.FLAWLESS_OR_BETTER.contains(gem.get(TestComponents.PURITY))),
                        true
                ),
                new Rule(
                        "apotheosis_socketed_solar",
                        SourcedScenario.compare(TestComponents.SOCKETED_GEMS, ApotheosisScenario.GEM, Comparison.Operator.EQUAL, solar.toString()),
                        stack -> ApotheosisScenario.socketsGem(stack, gem -> solar.equals(gem.get(TestComponents.GEM))),
                        false
                ),
                new Rule(
                        "apotheosis_many_sockets",
                        SourcedScenario.compare(TestComponents.SOCKETS, "", Comparison.Operator.GREATER_OR_EQUAL, 2),
                        stack -> stack.getOrDefault(TestComponents.SOCKETS, 0) >= 2,
                        false
                ),
                new Rule(
                        "apotheosis_salvage",
                        ComponentFilter.allOf(List.of(
                                SourcedScenario.rarity(common).or(SourcedScenario.rarity(uncommon)),
                                ComponentFilter.has(TestComponents.SOCKETED_GEMS).negate()
                        )),
                        stack -> stack.get(TestComponents.RARITY) instanceof Identifier rarity &&
                                List.of(common, uncommon).contains(rarity) &&
                                !stack.has(TestComponents.SOCKETED_GEMS),
                        false
                ),
                new Rule(
                        "apotheosis_keep",
                        ComponentFilter.anyOf(List.of(
                                SourcedScenario.rarity(mythic),
                                SourcedScenario.rarity(epic).and(SourcedScenario.compare(TestComponents.AFFIXES, "[*]", Comparison.Operator.GREATER_OR_EQUAL, 0.8D)),
                                SourcedScenario.compare(TestComponents.SOCKETED_GEMS, ApotheosisScenario.PURITY, Comparison.Operator.EQUAL, "perfect"),
                                SourcedScenario.compare(TestComponents.PURITY, "", Comparison.Operator.EQUAL, "flawless"),
                                SourcedScenario.compare(TestComponents.PURITY, "", Comparison.Operator.EQUAL, "perfect")
                        )),
                        stack -> {
                            Identifier rarity = stack.get(TestComponents.RARITY);
                            return mythic.equals(rarity) ||
                                    (epic.equals(rarity) && ApotheosisScenario.hasAffixOf(stack, 0.8D)) ||
                                    ApotheosisScenario.socketsGem(stack, gem -> gem.get(TestComponents.PURITY) == TestComponents.Purity.PERFECT) ||
                                    ApotheosisScenario.FLAWLESS_OR_BETTER.contains(stack.get(TestComponents.PURITY));
                        },
                        false
                )
        );
    }
    
    private static boolean hasAffixOf(ItemStack stack, double level) {
        for (float value : SourcedScenario.affixes(stack).values()) {
            if (value >= level) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean socketsGem(ItemStack stack, Predicate<ItemStackTemplate> gem) {
        for (ItemStackTemplate template : stack.getOrDefault(TestComponents.SOCKETED_GEMS, ItemContainerContents.EMPTY).nonEmptyItems()) {
            if (gem.test(template)) {
                return true;
            }
        }
        
        return false;
    }
}
