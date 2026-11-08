package dev.satherov.nexus;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.internal.component.vanilla.FilterFluidIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterPredicate;
import dev.satherov.nexus.internal.render.client.RenderDispatch;
import dev.satherov.nexus.internal.text.tooltip.TooltipText;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.core.registries.Registries;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// The mod class of the library, constructed by NeoForge when the mod loads.
///
@Mod(Nexus.MOD_ID)
public class Nexus {
    
    ///
    /// The mod id of the library.
    ///
    public static final String MOD_ID = "nexus";
    
    ///
    /// The handle of the library, or `null` until the mod is constructed.
    ///
    private static @Nullable NexusMod mod;
    
    ///
    /// Creates the mod and its handle and registers the `nexus:filter` predicate type, ingredient type, and fluid ingredient type.
    ///
    /// @param bus       The mod's own event bus.
    /// @param container The mod's container.
    ///
    public Nexus(IEventBus bus, ModContainer container) {
        Nexus.mod = NexusMod.of(container);
        TooltipText.init();

        Registrations registrations = Nexus.getMod().getRegistrations();
        registrations.register(Registries.DATA_COMPONENT_PREDICATE_TYPE, "filter", () -> FilterPredicate.TYPE).register();
        registrations.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, "filter", () -> FilterIngredient.TYPE).register();
        registrations.register(NeoForgeRegistries.Keys.FLUID_INGREDIENT_TYPES, "filter", () -> FilterFluidIngredient.TYPE).register();

        if (FMLEnvironment.getDist().isClient()) {
            RenderDispatch.listen(bus);
        }
    }
    
    ///
    /// The handle of the library itself.
    ///
    /// @return The handle.
    /// @throws NullPointerException If the mod has not been constructed yet.
    ///
    public static NexusMod getMod() {
        return Objects.requireNonNull(Nexus.mod, "Nexus has not been constructed yet");
    }
}
