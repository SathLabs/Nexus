package dev.satherov.nexus.internal.codec.vanilla;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.HolderCodecs;
import dev.satherov.nexus.internal.codec.Scalars;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.codec.struct.Structs;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.TypedInstance;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

///
/// Utility for the codecs behind [VanillaCodecs] that are more than a composition of other codecs.
///
@UtilityClass
@ApiStatus.Internal
public class VanillaTypes {
    
    ///
    /// The key of the identifier of the block of a block state.
    ///
    private static final String NAME = "Name";
    
    ///
    /// The key of the object of the property values of a block state.
    ///
    private static final String PROPERTIES = "Properties";
    
    ///
    /// The codec behind [VanillaCodecs#COMPOUND_TAG].
    ///
    public static final Traversal<CompoundTag, Access.Plain> COMPOUND_TAG = new Traversal<>("COMPOUND_TAG") {
        
        ///
        /// Appends the tag with its type on the network and converts it into a value of the format otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, CompoundTag value) {
            if (!operations.isPositional()) {
                return NbtOps.INSTANCE.convertTo(operations.dynamicOps(), value);
            }
            
            V buffer = operations.emptyObject();
            try {
                FriendlyByteBuf.writeNbt((FriendlyByteBuf) buffer, value);
            } catch (RuntimeException failure) {
                throw new NexusCodecException("Could not write tag to the buffer: " + failure);
            }
            
            return buffer;
        }
        
        ///
        /// Reads a tag with its type within vanilla's default quota on the network and converts the input into a tag otherwise.
        ///
        @Override
        public <V> CompoundTag read(Operations<V> operations, V input) {
            if (!operations.isPositional()) {
                Tag tag = operations.dynamicOps().convertTo(NbtOps.INSTANCE, input);
                if (!(tag instanceof CompoundTag compound)) {
                    throw CodecErrors.mismatch("an object", input);
                }
                
                return compound;
            }
            
            Tag tag;
            try {
                tag = FriendlyByteBuf.readNbt((FriendlyByteBuf) input, NbtAccounter.defaultQuota());
            } catch (RuntimeException failure) {
                throw new NexusCodecException("Could not read tag from the buffer: " + failure);
            }
            
            if (!(tag instanceof CompoundTag compound)) {
                throw CodecErrors.mismatch("a compound tag", tag);
            }
            
            return compound;
        }
    };
    
    ///
    /// The codec behind [VanillaCodecs#BLOCK_STATE].
    ///
    public static final Traversal<BlockState, Access.Plain> BLOCK_STATE = new Traversal<>("BLOCK_STATE") {
        
        ///
        /// Writes the id of the state as a VarInt on the network, and the block and the value of every property otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, BlockState value) {
            if (operations.isPositional()) {
                int id = Block.BLOCK_STATE_REGISTRY.getId(value);
                if (id == -1) {
                    throw new NexusCodecException("Expected a block state with an id, found '" + value + "'");
                }
                
                return operations.ofVarInt(id);
            }
            
            V object = operations.emptyObject();
            StateDefinition<Block, BlockState> definition = value.getBlock().getStateDefinition();
            if (!definition.isSingletonState()) {
                V properties = operations.emptyObject();
                // DFU writes the properties in the reverse order of their names and the name last, so JSON has to do the same.
                for (Property<?> property : List.copyOf(definition.getProperties()).reversed()) {
                    operations.put(properties, property.getName(), operations.ofString(property.value(value).valueName(), FriendlyByteBuf.MAX_STRING_LENGTH));
                }
                
                operations.put(object, VanillaTypes.PROPERTIES, properties);
            }
            
            return operations.put(object, VanillaTypes.NAME, Scalars.IDENTIFIER.write(operations, BuiltInRegistries.BLOCK.getKey(value.getBlock())));
        }
        
        ///
        /// Reads the id of a state as a VarInt on the network.
        /// Reads a block and the values of its properties otherwise, and throws once with every error.
        ///
        @Override
        public <V> BlockState read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                int id = operations.asVarInt(input);
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                if (state == null) throw CodecErrors.mismatch("a block state id", id);
                return state;
            }
            
            Set<String> keys = operations.keys(input);
            V name = operations.get(input, VanillaTypes.NAME);
            V properties = operations.get(input, VanillaTypes.PROPERTIES);
            List<CodecError> errors = new ArrayList<>();
            Block block = VanillaTypes.collect(errors, VanillaTypes.NAME, () -> {
                Identifier id = Scalars.IDENTIFIER.read(operations, VanillaTypes.required(name));
                return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> VanillaTypes.unknown(BuiltInRegistries.BLOCK.key(), id.toString()));
            });
            
            // An unknown block may have properties, so its `Properties` are neither read nor refused.
            boolean singleton = block != null && block.getStateDefinition().isSingletonState();
            BlockState state = null;
            if (block != null) {
                state = properties == null || singleton ?
                        block.defaultBlockState() :
                        VanillaTypes.collect(errors, VanillaTypes.PROPERTIES, () -> VanillaTypes.withProperties(operations, properties, block.defaultBlockState()));
            }
            
            if (operations.isStrict()) {
                errors.addAll(VanillaTypes.unknownKeys(keys, key -> key.equals(VanillaTypes.NAME) || (key.equals(VanillaTypes.PROPERTIES) && !singleton)));
            }
            
            if (!errors.isEmpty()) {
                throw new NexusCodecException(errors);
            }
            
            //noinspection DataFlowIssue The state was read if no error was collected.
            return state;
        }
    };
    
    ///
    /// The codec behind [VanillaCodecs#ITEM_STACK].
    ///
    public static final Structs.Inlinable<ItemStack, Access.Registries> ITEM_STACK = new StackCodec<>("ITEM_STACK", BuiltInRegistries.ITEM, "count", Item.ABSOLUTE_MAX_STACK_SIZE) {
        
        ///
        /// Will always return `1`, the count vanilla reads if the key is missing.
        ///
        @Override
        protected Integer fallbackAmount() {
            return 1;
        }
        
        ///
        /// @return `true` if the given stack is empty.
        ///
        @Override
        protected boolean isEmpty(ItemStack stack) {
            return stack.isEmpty();
        }
        
        ///
        /// Will always return [ItemStack#EMPTY].
        ///
        @Override
        protected ItemStack empty() {
            return ItemStack.EMPTY;
        }
        
        ///
        /// The count of the given stack.
        ///
        @Override
        protected int amountOf(ItemStack stack) {
            return stack.getCount();
        }
        
        ///
        /// The patch of the components of the given stack.
        ///
        @Override
        protected DataComponentPatch patchOf(ItemStack stack) {
            return stack.getComponentsPatch();
        }
        
        ///
        /// Creates a stack of the given item.
        ///
        @Override
        protected ItemStack create(Holder<Item> holder, int amount, DataComponentPatch patch) {
            return new ItemStack(holder, amount, patch);
        }
    };
    
    ///
    /// The codec behind [VanillaCodecs#FLUID_STACK].
    ///
    public static final Structs.Inlinable<FluidStack, Access.Registries> FLUID_STACK = new StackCodec<>("FLUID_STACK", BuiltInRegistries.FLUID, "amount", Integer.MAX_VALUE) {
        
        ///
        /// @return `true` if the given stack is empty.
        ///
        @Override
        protected boolean isEmpty(FluidStack stack) {
            return stack.isEmpty();
        }
        
        ///
        /// Will always return [FluidStack#EMPTY].
        ///
        @Override
        protected FluidStack empty() {
            return FluidStack.EMPTY;
        }
        
        ///
        /// The amount of the given stack.
        ///
        @Override
        protected int amountOf(FluidStack stack) {
            return stack.getAmount();
        }
        
        ///
        /// The patch of the components of the given stack.
        ///
        @Override
        protected DataComponentPatch patchOf(FluidStack stack) {
            return stack.getComponentsPatch();
        }
        
        ///
        /// Creates a stack of the given fluid.
        ///
        @Override
        protected FluidStack create(Holder<Fluid> holder, int amount, DataComponentPatch patch) {
            return new FluidStack(holder, amount, patch);
        }
    };
    
    ///
    /// The codec of the patch of the components of a stack.
    ///
    private static final Traversal<DataComponentPatch, Access.Registries> PATCH = new PatchCodec();
    
    ///
    /// Creates a codec that runs the first of the given codecs in JSON and NBT and the second on the network.
    ///
    /// @param name       The name of the codec, used in the failure message.
    /// @param keyed      The codec to run in JSON and NBT.
    /// @param positional The codec to run on the network.
    ///
    /// @return The codec that runs both.
    ///
    public static <T, A extends Access.Plain> Traversal<T, A> split(String name, NexusCodec<T, ? super A> keyed, NexusCodec<T, ? super A> positional) {
        Traversal<T, ? super A> onKeyed = (Traversal<T, ? super A>) keyed;
        Traversal<T, ? super A> onNetwork = (Traversal<T, ? super A>) positional;
        return new Traversal<>(name) {
            
            ///
            /// Writes the value with the codec of the kind of the format.
            ///
            @Override
            public <V> V write(Operations<V> operations, T value) {
                return operations.isPositional() ?
                        onNetwork.write(operations, value) :
                        onKeyed.write(operations, value);
            }
            
            ///
            /// Reads a value with the codec of the kind of the format.
            ///
            @Override
            public <V> T read(Operations<V> operations, V input) {
                return operations.isPositional() ?
                        onNetwork.read(operations, input) :
                        onKeyed.read(operations, input);
            }
        };
    }
    
    ///
    /// Creates the codec of the given number of ints, as an int array.
    ///
    /// Fails to read an array with any other number of ints.
    ///
    /// @param size The number of ints.
    ///
    /// @return The codec of the ints.
    ///
    public static Traversal<int[], Access.Plain> ints(int size) {
        return new Traversal<>("ints") {
            
            ///
            /// Writes the ints as an int array.
            ///
            @Override
            public <V> V write(Operations<V> operations, int[] value) {
                return operations.ofIntArray(value);
            }
            
            ///
            /// Reads an int array and checks its size.
            ///
            @Override
            public <V> int[] read(Operations<V> operations, V input) {
                int[] values = operations.asIntArray(input);
                if (values.length != size) {
                    throw CodecErrors.mismatch("a list of " + size + " ints", values.length);
                }
                
                return values;
            }
        };
    }
    
    ///
    /// Sets every property of the given state to the value the given object holds for it and throws once with every error.
    ///
    private static <V> BlockState withProperties(Operations<V> operations, V properties, BlockState state) {
        StateDefinition<Block, BlockState> definition = state.getBlock().getStateDefinition();
        Set<String> keys = operations.keys(properties);
        BlockState result = state;
        List<CodecError> errors = new ArrayList<>();
        for (Property<?> property : definition.getProperties()) {
            V value = operations.get(properties, property.getName());
            if (value == null) {
                continue;
            }
            
            try {
                result = VanillaTypes.withValue(result, property, operations.asString(value, FriendlyByteBuf.MAX_STRING_LENGTH));
            } catch (NexusCodecException failure) {
                errors.addAll(CodecErrors.prefixKey(failure, property.getName()).errors());
            }
        }
        
        if (operations.isStrict()) {
            errors.addAll(VanillaTypes.unknownKeys(keys, key -> definition.getProperty(key) != null));
        }
        
        if (!errors.isEmpty()) throw new NexusCodecException(errors);
        return result;
    }
    
    ///
    /// Sets the given property of the given state to the value of the given name and fails with the names of all values if it's unknown.
    ///
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String name) {
        T value = property.getValue(name).orElseThrow(() -> CodecErrors.unknownName(property.getPossibleValues().stream().map(property::getName).toList(), name));
        return state.setValue(property, value);
    }
    
    ///
    /// Runs the given action and puts the given key in front of the path of every error of a failure.
    ///
    private static <R> R at(String key, Supplier<R> action) {
        try {
            return action.get();
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, key);
        }
    }
    
    ///
    /// Runs the given action or adds the errors of its failure to the given list with the given key in front of their paths and returns `null`.
    ///
    private static <R> @Nullable R collect(List<CodecError> errors, String key, Supplier<R> action) {
        try {
            return action.get();
        } catch (NexusCodecException failure) {
            errors.addAll(CodecErrors.prefixKey(failure, key).errors());
            return null;
        }
    }
    
    ///
    /// Gets the given value and fails if it's `null` since its key is missing.
    ///
    private static <V> V required(@Nullable V value) {
        if (value == null) {
            throw new NexusCodecException("Missing");
        }
        
        return value;
    }
    
    ///
    /// Creates an error at every one of the given keys that the given check refuses.
    ///
    private static List<CodecError> unknownKeys(Set<String> keys, Predicate<String> known) {
        return keys.stream()
                .filter(known.negate())
                .map(key -> new CodecError(key, "Unknown key"))
                .toList();
    }
    
    ///
    /// Creates the failure for an identifier that is not in the given registry.
    ///
    private static NexusCodecException unknown(ResourceKey<? extends Registry<?>> registry, String found) {
        return CodecErrors.mismatch("an element of the registry '" + registry.identifier() + "'", found);
    }
    
    ///
    /// A codec of the patch of the components of a stack, in the layout of [DataComponentPatch#CODEC] and [DataComponentPatch#STREAM_CODEC].
    ///
    /// Every component is written and read with the codec and stream codec of its type.
    ///
    private static final class PatchCodec extends Traversal<DataComponentPatch, Access.Registries> {
        
        ///
        /// The prefix of the key of a removed component in JSON and NBT.
        ///
        private static final String REMOVED = "!";
        
        ///
        /// The codec of a component type, which is its id in the synced registry on the network.
        ///
        private static final Traversal<Holder<DataComponentType<?>>, Access.Registries> TYPE = HolderCodecs.holder(Registries.DATA_COMPONENT_TYPE);
        
        ///
        /// The codec of the value of a removed component, which is an empty object the same as DFU's `Codec.EMPTY`.
        ///
        private static final Traversal<Unit, Access.Plain> REMOVED_VALUE = Scalars.unit(Unit.INSTANCE);
        
        ///
        /// Creates the codec of a patch.
        ///
        private PatchCodec() {
            super("DataComponentPatch");
        }
        
        ///
        /// Writes every component that isn't transient under its identifier, and every removed one under `!` and its identifier, and throws once with every error.
        /// The error of a type that isn't registered is at the patch itself.
        ///
        private static <V> V writeObject(Operations<V> operations, DataComponentPatch patch) {
            RegistryOps<V> ops = PatchCodec.opsOf(operations);
            V object = operations.emptyObject();
            List<CodecError> errors = new ArrayList<>();
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                DataComponentType<?> type = entry.getKey();
                if (type.isTransient()) {
                    continue;
                }
                
                Optional<?> value = entry.getValue();
                String key = null;
                try {
                    key = value.isPresent() ? PatchCodec.keyOf(type) : PatchCodec.REMOVED + PatchCodec.keyOf(type);
                    V encoded = value.map(o -> TypedDataComponent.createUnchecked(type, o).encodeValue(ops).getOrThrow(NexusCodecException::new))
                            .orElseGet(() -> PatchCodec.REMOVED_VALUE.write(operations, Unit.INSTANCE));
                    
                    operations.put(object, key, encoded);
                } catch (NexusCodecException failure) {
                    errors.addAll(key == null ? failure.errors() : CodecErrors.prefixMapKey(failure, key).errors());
                }
            }
            
            if (!errors.isEmpty()) {
                throw new NexusCodecException(errors);
            }
            
            return object;
        }
        
        ///
        /// Reads every key of the given object as a set or a removed component and throws once with every error.
        ///
        private static <V> DataComponentPatch readObject(Operations<V> operations, V input) {
            RegistryOps<V> ops = PatchCodec.opsOf(operations);
            DataComponentPatch.Builder builder = DataComponentPatch.builder();
            Set<DataComponentType<?>> types = new HashSet<>();
            List<CodecError> errors = new ArrayList<>();
            for (String key : operations.keys(input)) {
                try {
                    boolean removed = key.startsWith(PatchCodec.REMOVED);
                    DataComponentType<?> type = PatchCodec.typeOf(removed ? key.substring(PatchCodec.REMOVED.length()) : key);
                    if (!types.add(type)) {
                        throw new NexusCodecException("Duplicate component");
                    }
                    
                    V value = VanillaTypes.required(operations.get(input, key));
                    if (removed) {
                        PatchCodec.REMOVED_VALUE.read(operations, value);
                        builder.remove(type);
                    } else {
                        builder.set(TypedDataComponent.createUnchecked(type, type.codecOrThrow().parse(ops, value).getOrThrow(NexusCodecException::new)));
                    }
                } catch (NexusCodecException failure) {
                    errors.addAll(CodecErrors.prefixMapKey(failure, key).errors());
                }
            }
            
            if (!errors.isEmpty()) {
                throw new NexusCodecException(errors);
            }
            
            return builder.build();
        }
        
        ///
        /// Appends the numbers of set and removed components, then the id and the value of every set one, and then the id of every removed one.
        ///
        private static <V> V writeBuffer(Operations<V> operations, DataComponentPatch patch) {
            V buffer = operations.emptyObject();
            int removed = 0;
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    removed++;
                }
            }
            
            operations.writeCount(patch.size() - removed, Integer.MAX_VALUE);
            operations.writeCount(removed, Integer.MAX_VALUE);
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                Optional<?> value = entry.getValue();
                if (value.isPresent()) {
                    PatchCodec.TYPE.write(operations, BuiltInRegistries.DATA_COMPONENT_TYPE.wrapAsHolder(entry.getKey()));
                    try {
                        PatchCodec.writeValue((RegistryFriendlyByteBuf) buffer, TypedDataComponent.createUnchecked(entry.getKey(), value.get()));
                    } catch (NexusCodecException failure) {
                        throw CodecErrors.prefixMapKey(failure, PatchCodec.keyOf(entry.getKey()));
                    }
                }
            }
            
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    PatchCodec.TYPE.write(operations, BuiltInRegistries.DATA_COMPONENT_TYPE.wrapAsHolder(entry.getKey()));
                }
            }
            
            return buffer;
        }
        
        ///
        /// Reads the numbers of set and removed components, then the id and the value of every set one, and then the id of every removed one.
        ///
        private static <V> DataComponentPatch readBuffer(Operations<V> operations, V input) {
            int present = operations.readCount(Integer.MAX_VALUE);
            int removed = operations.readCount(Integer.MAX_VALUE);
            DataComponentPatch.Builder builder = DataComponentPatch.builder();
            Set<DataComponentType<?>> types = new HashSet<>();
            for (int i = 0; i < present; i++) {
                DataComponentType<?> type = PatchCodec.TYPE.read(operations, input).value();
                try {
                    if (!types.add(type)) {
                        throw new NexusCodecException("Duplicate component");
                    }
                    
                    builder.set(PatchCodec.readValue((RegistryFriendlyByteBuf) input, type));
                } catch (NexusCodecException failure) {
                    throw CodecErrors.prefixMapKey(failure, PatchCodec.keyOf(type));
                }
            }
            
            for (int i = 0; i < removed; i++) {
                DataComponentType<?> type = PatchCodec.TYPE.read(operations, input).value();
                if (!types.add(type)) {
                    throw CodecErrors.prefixMapKey(new NexusCodecException("Duplicate component"), PatchCodec.REMOVED + PatchCodec.keyOf(type));
                }
                
                builder.remove(type);
            }
            
            return builder.build();
        }
        
        ///
        /// Appends the value of the given component with the stream codec of its type.
        ///
        private static <T> void writeValue(RegistryFriendlyByteBuf buffer, TypedDataComponent<T> component) {
            try {
                component.type().streamCodec().encode(buffer, component.value());
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ?
                        refused :
                        new NexusCodecException("Could not write to the buffer, " + failure);
            }
        }
        
        ///
        /// Reads a value of the given type with its stream codec.
        ///
        private static <T> TypedDataComponent<T> readValue(RegistryFriendlyByteBuf buffer, DataComponentType<T> type) {
            try {
                return new TypedDataComponent<>(type, type.streamCodec().decode(buffer));
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ?
                        refused :
                        new NexusCodecException("Could not read from the buffer, " + failure);
            }
        }
        
        ///
        /// Gets the DFU ops of the given keyed format, as registry ops with its registries.
        ///
        private static <V> RegistryOps<V> opsOf(Operations<V> operations) {
            //noinspection DataFlowIssue A codec that needs registries only ever runs on formats with registries.
            return RegistryOps.create(operations.dynamicOps(), operations.lookup());
        }
        
        ///
        /// Gets the identifier of the given component type as a string, and fails if the type isn't registered.
        ///
        private static String keyOf(DataComponentType<?> type) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id == null) {
                throw new NexusCodecException("Expected a registered component type, found '" + type + "'");
            }
            
            return id.toString();
        }
        
        ///
        /// Gets the persistent component type of the given identifier.
        ///
        private static DataComponentType<?> typeOf(String text) {
            Identifier id = Identifier.tryParse(text);
            DataComponentType<?> type = id != null ? BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id) : null;
            if (type == null) {
                throw VanillaTypes.unknown(Registries.DATA_COMPONENT_TYPE, text);
            }
            
            if (type.isTransient()) {
                throw CodecErrors.mismatch("a persistent component type", text);
            }
            
            return type;
        }
        
        ///
        /// Appends the numbers of set and removed components and then the components on the network, and writes an object of them otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, DataComponentPatch value) {
            return operations.isPositional() ?
                    PatchCodec.writeBuffer(operations, value) :
                    PatchCodec.writeObject(operations, value);
        }
        
        ///
        /// Reads the numbers of set and removed components and then the components on the network, and an object of them otherwise.
        ///
        @Override
        public <V> DataComponentPatch read(Operations<V> operations, V input) {
            return operations.isPositional() ?
                    PatchCodec.readBuffer(operations, input) :
                    PatchCodec.readObject(operations, input);
        }
    }
    
    ///
    /// A codec of an item or fluid stack that may be empty, with the identifier of its type, its amount, and the patch of its components.
    ///
    /// In JSON and NBT, an empty stack is an empty object, and a stack inlined into a struct is empty if the struct has none of its keys.
    /// On the network, an empty stack is an amount of `0`.
    ///
    /// @param <S> The type of the stack.
    /// @param <T> The type that a stack holds an amount of, such as [Item].
    ///
    private abstract static class StackCodec<S extends TypedInstance<T>, T> extends Structs.Inlinable<S, Access.Registries> {
        
        ///
        /// The key of the identifier of the type of a stack.
        ///
        private static final String ID = "id";
        
        ///
        /// The key of the patch of the components of a stack.
        ///
        private static final String COMPONENTS = "components";
        
        ///
        /// The built-in registry of the types of a stack, which JSON and NBT read an identifier from.
        ///
        private final DefaultedRegistry<T> registry;
        
        ///
        /// The codec of the type of a stack on the network, which is its id in the synced registry.
        ///
        private final Traversal<Holder<T>, Access.Registries> holder;
        
        ///
        /// The key of the amount of a stack.
        ///
        private final String amountKey;
        
        ///
        /// The largest amount JSON and NBT allow.
        ///
        private final int maxAmount;
        
        ///
        /// The keys of a stack.
        ///
        private final Set<String> keys;
        
        ///
        /// Creates the codec of a stack of the given registry.
        ///
        private StackCodec(String name, DefaultedRegistry<T> registry, String amountKey, int maxAmount) {
            super(name);
            this.registry = registry;
            this.holder = HolderCodecs.holder(registry.key());
            this.amountKey = amountKey;
            this.maxAmount = maxAmount;
            this.keys = Set.of(StackCodec.ID, amountKey, StackCodec.COMPONENTS);
        }
        
        ///
        /// The keys of the identifier, the amount, and the components.
        ///
        @Override
        public Set<String> keys() {
            return this.keys;
        }
        
        ///
        /// Appends the amount, the id of the type, and the patch on the network, and writes an object of the fields otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, S value) {
            if (!operations.isPositional()) {
                V object = operations.emptyObject();
                this.writeFields(operations, object, value);
                return object;
            }
            
            if (this.isEmpty(value)) {
                return operations.ofVarInt(0);
            }
            
            operations.ofVarInt(this.amountOf(value));
            VanillaTypes.at(StackCodec.ID, () -> this.holder.write(operations, value.typeHolder()));
            return VanillaTypes.at(StackCodec.COMPONENTS, () -> VanillaTypes.PATCH.write(operations, this.patchOf(value)));
        }
        
        ///
        /// Writes the identifier of the type, the amount, and a patch that isn't empty into the given object, and nothing for an empty stack.
        /// Throws once with the errors of every field that failed.
        ///
        @Override
        public <V> void writeFields(Operations<V> operations, V object, S value) {
            if (this.isEmpty(value)) {
                return;
            }
            
            List<CodecError> errors = new ArrayList<>();
            VanillaTypes.collect(errors, StackCodec.ID, () -> operations.put(object, StackCodec.ID, this.writeId(operations, value.typeHolder())));
            VanillaTypes.collect(errors, this.amountKey, () -> operations.put(object, this.amountKey, operations.ofInt(this.checkAmount(this.amountOf(value)))));
            DataComponentPatch patch = this.patchOf(value);
            if (!patch.isEmpty()) {
                VanillaTypes.collect(errors, StackCodec.COMPONENTS, () -> operations.put(object, StackCodec.COMPONENTS, VanillaTypes.PATCH.write(operations, patch)));
            }
            
            if (!errors.isEmpty()) {
                throw new NexusCodecException(errors);
            }
        }
        
        ///
        /// Reads the amount, and then the id of the type and the patch if it's positive, on the network.
        /// Reads an empty object as an empty stack and any other object as a stack otherwise.
        ///
        @Override
        public <V> S read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                int amount = operations.asVarInt(input);
                if (amount <= 0) {
                    return this.empty();
                }
                
                Holder<T> holder = VanillaTypes.at(StackCodec.ID, () -> this.holder.read(operations, input));
                return this.create(holder, amount, VanillaTypes.at(StackCodec.COMPONENTS, () -> VanillaTypes.PATCH.read(operations, input)));
            }
            
            Set<String> keys = operations.keys(input);
            if (keys.isEmpty()) {
                return this.empty();
            }
            
            return this.readStack(operations, input, operations.isStrict() ? keys : null);
        }
        
        ///
        /// Reads an empty stack if the given object has none of the keys of a stack, and a stack otherwise.
        ///
        @Override
        public <V> S readFields(Operations<V> operations, V object, @Nullable Set<String> present) {
            if (this.keys.stream().allMatch(key -> operations.get(object, key) == null)) {
                return this.empty();
            }
            
            return this.readStack(operations, object, present);
        }
        
        ///
        /// Reads a stack from the fields of the given object, refuses those of the given keys it doesn't have, and throws once with every error.
        ///
        private <V> S readStack(Operations<V> operations, V object, @Nullable Set<String> present) {
            V id = operations.get(object, StackCodec.ID);
            V amount = operations.get(object, this.amountKey);
            V components = operations.get(object, StackCodec.COMPONENTS);
            
            List<CodecError> errors = new ArrayList<>();
            Holder<T> holder = VanillaTypes.collect(errors, StackCodec.ID, () -> this.readId(operations, VanillaTypes.required(id)));
            Integer count = VanillaTypes.collect(errors, this.amountKey, () -> this.readAmount(operations, amount));
            
            DataComponentPatch patch = components == null ?
                    DataComponentPatch.EMPTY :
                    VanillaTypes.collect(errors, StackCodec.COMPONENTS, () -> VanillaTypes.PATCH.read(operations, components));
            
            if (present != null) {
                errors.addAll(VanillaTypes.unknownKeys(present, this.keys::contains));
            }
            
            if (!errors.isEmpty()) {
                throw new NexusCodecException(errors);
            }
            
            //noinspection DataFlowIssue Every field was read if no error was collected.
            return this.create(holder, count, patch);
        }
        
        ///
        /// Writes the identifier of the given reference holder.
        ///
        private <V> V writeId(Operations<V> operations, Holder<T> holder) {
            ResourceKey<T> key = holder.unwrapKey().orElseThrow(() -> new NexusCodecException("Expected a holder of the registry '" + this.registry.key().identifier() + "', found a direct holder"));
            return Scalars.IDENTIFIER.write(operations, key.identifier());
        }
        
        ///
        /// Reads an identifier and gets its holder in the registry, which is neither the default value nor without its components.
        ///
        private <V> Holder<T> readId(Operations<V> operations, V input) {
            Identifier id = Scalars.IDENTIFIER.read(operations, input);
            Holder<T> holder = this.registry.get(id).orElseThrow(() -> VanillaTypes.unknown(this.registry.key(), id.toString()));
            if (holder.is(this.registry.getDefaultKey())) {
                throw CodecErrors.mismatch("an element other than '" + this.registry.getDefaultKey() + "'", id.toString());
            }
            
            if (!holder.areComponentsBound()) {
                throw new NexusCodecException("Could not use '" + id + "' before its components are bound");
            }
            
            return holder;
        }
        
        ///
        /// Reads the amount from the given input, or takes the fallback amount if the input is `null`.
        ///
        private <V> int readAmount(Operations<V> operations, @Nullable V input) {
            if (input != null) {
                return this.checkAmount(operations.asInt(input));
            }
            
            Integer fallback = this.fallbackAmount();
            if (fallback == null) {
                throw new NexusCodecException("Missing");
            }
            
            return fallback;
        }
        
        ///
        /// Gets the given amount, and fails if it's not in `[1, maxAmount]`.
        ///
        private int checkAmount(int amount) {
            if (amount < 1 || amount > this.maxAmount) {
                throw CodecErrors.outOfRange(1, this.maxAmount, amount);
            }
            
            return amount;
        }
        
        ///
        /// The amount to read if the key of the amount is missing, or `null` if it's required.
        ///
        /// @return The fallback amount, or `null` if the amount is required.
        ///
        protected @Nullable Integer fallbackAmount() {
            return null;
        }
        
        ///
        /// @param stack The stack to check.
        ///
        /// @return `true` if the given stack is empty.
        ///
        protected abstract boolean isEmpty(S stack);
        
        ///
        /// The empty stack.
        ///
        /// @return The empty stack.
        ///
        protected abstract S empty();
        
        ///
        /// The amount of the given stack.
        ///
        /// @param stack The stack, which is not empty.
        ///
        /// @return The amount of the stack.
        ///
        protected abstract int amountOf(S stack);
        
        ///
        /// The patch of the components of the given stack.
        ///
        /// @param stack The stack, which is not empty.
        ///
        /// @return The patch of the stack.
        ///
        protected abstract DataComponentPatch patchOf(S stack);
        
        ///
        /// Creates a stack of the given type.
        ///
        /// @param holder The type of the stack.
        /// @param amount The amount of the stack, which is positive.
        /// @param patch  The patch of the components of the stack.
        ///
        /// @return The stack.
        ///
        protected abstract S create(Holder<T> holder, int amount, DataComponentPatch patch);
    }
}
