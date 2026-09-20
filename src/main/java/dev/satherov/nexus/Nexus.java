package dev.satherov.nexus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

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
    /// Creates the mod, which registers nothing yet.
    ///
    /// @param bus       The mod's own event bus.
    /// @param container The mod's container.
    ///
    public Nexus(IEventBus bus, ModContainer container) { }
}
