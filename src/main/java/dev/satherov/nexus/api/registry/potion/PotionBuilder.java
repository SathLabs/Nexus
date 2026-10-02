package dev.satherov.nexus.api.registry.potion;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.text.Translations;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.ItemLike;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// A builder of one potion, named in its potion, splash, lingering, and arrow forms.
///
/// @param <PARENT> The type of what [#end()] returns.
///
public final class PotionBuilder<PARENT> extends EntryBuilder<Potion, Potion, PotionEntry, PARENT, PotionBuilder<PARENT>> {
    
    ///
    /// The English name of the effect of the potion.
    ///
    private final String english;
    
    ///
    /// The suppliers of the effects of the potion.
    ///
    private final List<Supplier<MobEffectInstance>> effects;
    
    ///
    /// The registrations of the brewing recipes, called with the event and the entry, in call order.
    ///
    private final List<BiConsumer<RegisterBrewingRecipesEvent, PotionEntry>> recipes = new ArrayList<>();
    
    ///
    /// Starts the potion `<modid>:<name>` of the given effects, with `<modid>.<name>` as the name of the potion.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The potion is named under the four keys that vanilla derives from the name of the potion:
    /// - `item.minecraft.potion.effect.<modid>.<name>` as `Potion of <english>`.
    /// - `item.minecraft.splash_potion.effect.<modid>.<name>` as `Splash Potion of <english>`.
    /// - `item.minecraft.lingering_potion.effect.<modid>.<name>` as `Lingering Potion of <english>`.
    /// - `item.minecraft.tipped_arrow.effect.<modid>.<name>` as `Arrow of <english>`.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name of the effect, `<english>` in the names.
    /// @param parent        What [#end()] returns.
    /// @param effects       The suppliers of the effects, each called once inside the registry event.
    ///
    @SafeVarargs
    public PotionBuilder(Registrations registrations, String name, String english, PARENT parent, Supplier<MobEffectInstance>... effects) {
        super(registrations, Registries.POTION, name, parent);
        this.english = english;
        this.effects = List.of(effects);
    }
    
    ///
    /// Registers a brewing recipe that turns the given base potion into this one with the given ingredient.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// The base potion and the ingredient are resolved once the brewing recipes are registered, so they may still be
    /// unbound entries.
    ///
    /// @param base       The holder of the potion to brew from.
    /// @param ingredient The ingredient to brew with.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PotionBuilder<PARENT> brewing(Holder<Potion> base, ItemLike ingredient) {
        this.checkUnregistered();
        this.recipes.add((event, potion) -> event.getBuilder().addMix(base, ingredient.asItem(), potion));
        return this;
    }
    
    ///
    /// Creates the potion of the effects, named `<modid>.<name>`.
    ///
    /// @return The created potion.
    ///
    @Override
    protected Potion create() {
        return new Potion(
                this.getKey().identifier().toLanguageKey(),
                this.effects.stream()
                        .map(Supplier::get)
                        .toArray(MobEffectInstance[]::new)
        );
    }
    
    ///
    /// Creates the entry for the given key, declares the four names of the potion, and adds a listener for every
    /// brewing recipe to the game event bus.
    ///
    /// @param key The key of the potion.
    ///
    /// @return The created entry.
    ///
    @Override
    protected PotionEntry createEntry(ResourceKey<Potion> key) {
        Translations translations = this.getRegistrations().getMod().getTranslations();
        String name = key.identifier().toLanguageKey();
        translations.defineKey("item.minecraft.potion.effect." + name, "Potion of " + this.english);
        translations.defineKey("item.minecraft.splash_potion.effect." + name, "Splash Potion of " + this.english);
        translations.defineKey("item.minecraft.lingering_potion.effect." + name, "Lingering Potion of " + this.english);
        translations.defineKey("item.minecraft.tipped_arrow.effect." + name, "Arrow of " + this.english);
        
        PotionEntry entry = new PotionEntry(key, this);
        this.recipes.forEach(recipe -> NeoForge.EVENT_BUS.addListener(RegisterBrewingRecipesEvent.class, event -> recipe.accept(event, entry)));
        return entry;
    }
}
