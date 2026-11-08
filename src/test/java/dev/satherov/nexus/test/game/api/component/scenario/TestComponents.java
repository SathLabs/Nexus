package dev.satherov.nexus.test.game.api.component.scenario;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.component.ItemContainerContents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

///
/// The data components that the scenarios set.
///
/// `mekanism:energy` and the components of Apotheosis have their real identifiers and codecs.
/// The other three exist only for the tests:
/// - `nexus:test_tier`: A record with a Nexus struct codec.
/// - `nexus:test_tool`: A record with a DFU codec, shaped like the tool data of Tinkers', which a component filter reads by encoding it.
/// - `nexus:test_fluid`: A [SimpleFluidContent].
///
public final class TestComponents {
    
    public static final DataComponentType<Tool> TOOL = TestComponents.persistent(Tool.CODEC);
    public static final DataComponentType<SimpleFluidContent> FLUID = TestComponents.persistent(SimpleFluidContent.CODEC);
    public static final DataComponentType<Long> ENERGY = TestComponents.persistent(ExtraCodecs.NON_NEGATIVE_LONG);
    public static final DataComponentType<Map<Identifier, Float>> AFFIXES = TestComponents.persistent(Codec.unboundedMap(Identifier.CODEC, Codec.floatRange(0.0F, 2.0F)));
    public static final DataComponentType<Identifier> RARITY = TestComponents.persistent(Identifier.CODEC);
    public static final DataComponentType<Component> AFFIX_NAME = TestComponents.persistent(ComponentSerialization.CODEC);
    public static final DataComponentType<Integer> SOCKETS = TestComponents.persistent(Codec.intRange(0, 16));
    public static final DataComponentType<ItemContainerContents> SOCKETED_GEMS = TestComponents.persistent(ItemContainerContents.CODEC);
    public static final DataComponentType<Identifier> GEM = TestComponents.persistent(Identifier.CODEC);
    public static final DataComponentType<Purity> PURITY = TestComponents.persistent(Purity.CODEC);
    
    private static final StructCodec<Tier, Access.Plain> TIER_CODEC = NexusCodec.struct(
            "tier",
            NexusCodec.INT.field("level", Tier::level),
            NexusCodec.STRING.field("tag", Tier::tag),
            Tier::new
    );
    
    private static @Nullable DataComponentEntry<Tier> tier;
    
    private static <VALUE> DataComponentType<VALUE> persistent(Codec<VALUE> codec) {
        return DataComponentType.<VALUE>builder().persistent(codec).build();
    }
    
    public static DataComponentType<Tier> tier() {
        return Objects.requireNonNull(TestComponents.tier).value();
    }
    
    public record Tier(int level, String tag) { }
    
    public record Tool(List<String> materials, ToolStats stats, List<ToolModifier> modifiers, int upgrades, boolean broken) {
        
        public static final Codec<Tool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("materials").forGetter(Tool::materials),
                ToolStats.CODEC.fieldOf("stats").forGetter(Tool::stats),
                ToolModifier.CODEC.listOf().fieldOf("modifiers").forGetter(Tool::modifiers),
                Codec.INT.fieldOf("upgrades").forGetter(Tool::upgrades),
                Codec.BOOL.optionalFieldOf("broken", false).forGetter(Tool::broken)
        ).apply(instance, Tool::new));
    }
    
    public record ToolStats(int durability, float attackDamage, float miningSpeed, String tier) {
        
        public static final Codec<ToolStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("durability").forGetter(ToolStats::durability),
                Codec.FLOAT.fieldOf("attack_damage").forGetter(ToolStats::attackDamage),
                Codec.FLOAT.fieldOf("mining_speed").forGetter(ToolStats::miningSpeed),
                Codec.STRING.fieldOf("tier").forGetter(ToolStats::tier)
        ).apply(instance, ToolStats::new));
    }
    
    public record ToolModifier(String id, int level) {
        
        public static final Codec<ToolModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(ToolModifier::id),
                Codec.INT.fieldOf("level").forGetter(ToolModifier::level)
        ).apply(instance, ToolModifier::new));
    }
    
    @RequiredArgsConstructor
    public enum Purity implements StringRepresentable {
        CRACKED("cracked"),
        CHIPPED("chipped"),
        FLAWED("flawed"),
        NORMAL("normal"),
        FLAWLESS("flawless"),
        PERFECT("perfect");
        
        public static final Codec<Purity> CODEC = StringRepresentable.fromValues(Purity::values);
        
        @Getter
        private final String serializedName;
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            TestComponents.tier = Nexus.getMod().getRegistrations().component("test_tier", TestComponents.TIER_CODEC).register();
        }
        
        @SubscribeEvent
        public static void onRegister(RegisterEvent event) {
            event.register(Registries.DATA_COMPONENT_TYPE, helper -> {
                helper.register(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "test_tool"), TestComponents.TOOL);
                helper.register(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "test_fluid"), TestComponents.FLUID);
                helper.register(Identifier.fromNamespaceAndPath("mekanism", "energy"), TestComponents.ENERGY);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "affixes"), TestComponents.AFFIXES);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "rarity"), TestComponents.RARITY);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "affix_name"), TestComponents.AFFIX_NAME);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "sockets"), TestComponents.SOCKETS);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "socketed_gems"), TestComponents.SOCKETED_GEMS);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "gem"), TestComponents.GEM);
                helper.register(Identifier.fromNamespaceAndPath("apotheosis", "purity"), TestComponents.PURITY);
            });
        }
    }
}
