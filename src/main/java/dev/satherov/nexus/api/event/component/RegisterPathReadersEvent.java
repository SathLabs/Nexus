package dev.satherov.nexus.api.event.component;

import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import net.minecraft.core.component.DataComponentType;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

///
/// An event in which mods register the readers of component paths.
///
/// Posted once on the mod bus when the registries freeze, in data generation as well.
///
/// The functions registered by component are asked before the functions registered by codec, each in the order they were registered.
/// Asking stops at the first function that returns a reader.
/// Nexus registers its own functions after the event, so the functions of every mod are asked first.
///
public final class RegisterPathReadersEvent extends Event implements IModBusEvent {
    
    ///
    /// The functions registered by component.
    ///
    private final Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> componentReaders;
    
    ///
    /// The functions registered by codec, which compares its keys by identity.
    ///
    private final Map<Codec<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> codecReaders;
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param componentReaders The functions registered per component, which this event adds to.
    /// @param codecReaders     The functions registered by codec, which this event adds to.
    ///
    @ApiStatus.Internal
    public RegisterPathReadersEvent(
            Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> componentReaders,
            Map<Codec<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> codecReaders
    ) {
        this.componentReaders = componentReaders;
        this.codecReaders = codecReaders;
    }
    
    ///
    /// Registers a function that creates the readers of the paths on the given component.
    ///
    /// The function is called with the steps of every path that is created on the component.
    /// If it returns `null`, the next function will be asked.
    ///
    /// @param component The data component type that the paths start at.
    /// @param readers   The function that creates the reader of a path from its steps.
    ///
    public <VALUE> void register(DataComponentType<VALUE> component, Function<List<ComponentPath.Step>, @Nullable PathReader<VALUE>> readers) {
        this.componentReaders.computeIfAbsent(component, _ -> new ArrayList<>()).add(readers);
    }
    
    ///
    /// Registers a function that creates the readers of the paths on every component with the given codec.
    ///
    /// A component has the codec if the codec was given to its builder or [DataComponentType#codec()] returns it.
    ///
    /// The function is called with the steps of every path that is created on such a component.
    /// If it returns `null`, the next function will be asked.
    ///
    /// @param codec   The DFU codec of the data component types, compared by identity.
    /// @param readers The function that creates the reader of a path from its steps.
    ///
    public <VALUE> void register(Codec<VALUE> codec, Function<List<ComponentPath.Step>, @Nullable PathReader<VALUE>> readers) {
        this.codecReaders.computeIfAbsent(codec, _ -> new ArrayList<>()).add(readers);
    }
}
