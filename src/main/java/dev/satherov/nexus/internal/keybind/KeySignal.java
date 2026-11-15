package dev.satherov.nexus.internal.keybind;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import io.netty.handler.codec.DecoderException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

///
/// The payload value of one key, a firing with its carried values or the key going down or up.
///
@ApiStatus.Internal
public sealed interface KeySignal {
    
    ///
    /// Creates the stream codec of the signals of a key with the given carried values.
    ///
    /// Writes a tag byte and then, for a firing, each value through its own codec in order.
    ///
    /// @param carried The stream codecs of the carried values, in the order the values are read.
    ///
    /// @return The stream codec of the signals.
    ///
    static StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec(List<StreamCodec<? super RegistryFriendlyByteBuf, ?>> carried) {
        return StreamCodec.of((buffer, signal) -> KeySignal.encode(buffer, signal, carried), buffer -> KeySignal.decode(buffer, carried));
    }
    
    ///
    /// Writes the tag of the given signal and then the values of a firing, each through the codec at its index.
    ///
    private static void encode(RegistryFriendlyByteBuf buffer, KeySignal signal, List<StreamCodec<? super RegistryFriendlyByteBuf, ?>> carried) {
        switch (signal) {
            case KeySignal.Fired(List<Object> values) -> {
                buffer.writeByte(KeySignal.Fired.TAG);
                for (int i = 0; i < carried.size(); i++) {
                    //noinspection unchecked The value at each index was read for the codec at that index.
                    StreamCodec<? super RegistryFriendlyByteBuf, Object> codec = (StreamCodec<? super RegistryFriendlyByteBuf, Object>) carried.get(i);
                    codec.encode(buffer, values.get(i));
                }
            }
            case KeySignal.Down _ -> buffer.writeByte(KeySignal.Down.TAG);
            case KeySignal.Up _ -> buffer.writeByte(KeySignal.Up.TAG);
        }
    }
    
    ///
    /// Reads the tag of a signal and then the values of a firing, each through the codec at its index.
    ///
    private static KeySignal decode(RegistryFriendlyByteBuf buffer, List<StreamCodec<? super RegistryFriendlyByteBuf, ?>> carried) {
        byte tag = buffer.readByte();
        return switch (tag) {
            case KeySignal.Fired.TAG -> {
                List<Object> values = new ArrayList<>(carried.size());
                for (StreamCodec<? super RegistryFriendlyByteBuf, ?> codec : carried) {
                    values.add(codec.decode(buffer));
                }
                
                yield new KeySignal.Fired(values);
            }
            case KeySignal.Down.TAG -> KeySignal.Down.INSTANCE;
            case KeySignal.Up.TAG -> KeySignal.Up.INSTANCE;
            default -> throw new DecoderException("Unknown key signal tag " + tag);
        };
    }
    
    ///
    /// The signal of a key that fired.
    ///
    /// @param values The carried values, in the order of their codecs.
    ///
    record Fired(@Unmodifiable List<Object> values) implements KeySignal {
        
        ///
        /// The tag byte of a firing.
        ///
        private static final byte TAG = 0;
        
        ///
        /// Copies the carried values into an unmodifiable list.
        ///
        public Fired {
            values = List.copyOf(values);
        }
    }
    
    ///
    /// The signal of a key going down.
    ///
    enum Down implements KeySignal {
        ///
        /// The one signal of a key going down.
        ///
        INSTANCE;
        
        ///
        /// The tag byte of a key going down.
        ///
        private static final byte TAG = 1;
    }
    
    ///
    /// The signal of a key going up.
    ///
    enum Up implements KeySignal {
        ///
        /// The one signal of a key going up.
        ///
        INSTANCE;
        
        ///
        /// The tag byte of a key going up.
        ///
        private static final byte TAG = 2;
    }
}
