package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import org.jetbrains.annotations.ApiStatus;

///
/// The only class that uses client render events.
/// - Registers the pipelines of the library and the mods, the HUD layers, the debug renderers, and the entity glow modifier.
/// - Runs the scenes, the draw hooks, the glows, and the outline rules at their points of the frame.
/// - Starts them on login and ends them on logout.
///
/// Should only ever be loaded on the client.
///
@UtilityClass
@ApiStatus.Internal
public class RenderDispatch {

    ///
    /// Subscribes on the mod bus of Nexus and the game bus.
    ///
    /// Should only ever be called once from [Nexus], behind the dist check.
    /// The registrations of every [Render] are only queued.
    ///
    /// @param nexusBus The mod bus of Nexus.
    ///
    public static void listen(IEventBus nexusBus) {
        nexusBus.addListener(RegisterRenderPipelinesEvent.class, event -> ShapePipelines.register(event::registerPipeline));
    }
}
