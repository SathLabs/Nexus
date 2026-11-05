package dev.satherov.nexus.internal.component;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.component.Literal;
import dev.satherov.nexus.api.component.Operator;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// A path reader over any component that wasn't built by Nexus.
///
/// The tag at the end of the steps is kept per value object, read once from the tree in [EncodedValues].
///
@ApiStatus.Internal
@RequiredArgsConstructor
public final class TreeReader implements PathReader {
    
    ///
    /// The component the path starts at.
    ///
    private final DataComponentType<?> type;
    
    ///
    /// The keys and indices down to the value.
    ///
    private final List<Step> steps;
    
    ///
    /// The tag at the end of the steps by value object, or the end tag if a key or index is missing.
    ///
    private final Leaves leaves = new Leaves();
    
    ///
    /// Compares the given scalar against the given literal with the given operator.
    ///
    private static boolean compareScalar(Object value, Operator operator, Literal literal) {
        return switch (literal) {
            case Literal.Numeric(double expected) -> value instanceof Number number && PathReader.passes(operator, number.doubleValue(), expected);
            case Literal.Text(String expected) -> value instanceof String text && PathReader.passes(operator, text.compareTo(expected), 0);
            case Literal.Flag(boolean expected) -> value instanceof Boolean flag && PathReader.passes(operator, flag == expected ? 0 : 1, 0);
        };
    }
    
    ///
    /// Will always return [Kind#UNKNOWN].
    ///
    @Override
    public Kind kind() {
        return Kind.UNKNOWN;
    }
    
    ///
    /// Compares a number, a string, or a boolean directly and anything else through the tag at the end of the steps.
    ///
    @Override
    public boolean compare(DataComponentGetter holder, Operator operator, Literal literal) {
        return this.compareValue(this.type, holder, operator, literal);
    }
    
    ///
    /// Compares the value of the given type on the given holder, reading a byte tag in the tree as a boolean.
    ///
    private <VALUE> boolean compareValue(DataComponentType<VALUE> type, DataComponentGetter holder, Operator operator, Literal literal) {
        VALUE value = holder.get(type);
        if (value == null) {
            return false;
        }
        
        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            return this.steps.isEmpty() && TreeReader.compareScalar(value, operator, literal);
        }
        
        Tag tag = this.leaves.get(value);
        if (tag == null) {
            tag = this.leaves.add(value, Objects.requireNonNullElse(this.find(EncodedValues.tree(type, value)), EndTag.INSTANCE));
        }
        
        return switch (literal) {
            case Literal.Numeric(double expected) -> tag instanceof NumericTag number && PathReader.passes(operator, number.doubleValue(), expected);
            case Literal.Text(String expected) -> tag instanceof StringTag(String text) && PathReader.passes(operator, text.compareTo(expected), 0);
            case Literal.Flag(boolean expected) -> tag instanceof ByteTag(byte flag) && PathReader.passes(operator, (flag != 0) == expected ? 0 : 1, 0);
        };
    }
    
    ///
    /// Gets the tag at the end of the steps below the given tree, or `null` if a key or index is missing.
    ///
    private @Nullable Tag find(Tag tree) {
        Tag tag = tree;
        for (int i = 0; i < this.steps.size(); i++) {
            tag = switch (this.steps.get(i)) {
                case Step.Key(String name) -> tag instanceof CompoundTag compound ? compound.get(name) : null;
                case Step.Index(int index) -> tag instanceof CollectionTag list && index < list.size() ? list.get(index) : null;
            };
        }
        
        return tag;
    }
    
    ///
    /// A weak, identity-keyed table from value object to tag.
    ///
    /// Reads take no lock and read only the volatile buckets and the final fields of an entry. Writes take the lock.
    ///
    /// Guava's weak map, which [EncodedValues] uses, increments an atomic counter on every read, the hottest path of the filter.
    ///
    private static final class Leaves {
        
        ///
        /// The number of buckets the table starts with and never goes below.
        ///
        private static final int MIN_BUCKETS = 16;
        
        ///
        /// The queue that an entry is put on once its value object is gone.
        ///
        private final ReferenceQueue<Object> cleared = new ReferenceQueue<>();
        
        ///
        /// The chains of entries by the identity hash of their value object.
        ///
        private volatile @Nullable Entry[] buckets = new Entry[Leaves.MIN_BUCKETS];
        
        ///
        /// The number of entries in the buckets, including the ones whose value object is gone.
        ///
        private int size;
        
        ///
        /// How many entries have been put on the queue since the buckets were built.
        ///
        private int gone;
        
        ///
        /// Gets the tag of the given value object, or `null` if none was added.
        ///
        private @Nullable Tag get(Object value) {
            Entry[] buckets = this.buckets;
            for (Entry entry = buckets[System.identityHashCode(value) & (buckets.length - 1)]; entry != null; entry = entry.next) {
                if (entry.refersTo(value)) {
                    return entry.tag;
                }
            }
            
            return null;
        }
        
        ///
        /// Adds the given tag of the given value object, or returns the tag that another thread added first.
        ///
        private synchronized Tag add(Object value, Tag tag) {
            Tag existing = this.get(value);
            if (existing != null) {
                return existing;
            }
            
            while (this.cleared.poll() != null) {
                this.gone++;
            }
            
            if (this.gone * 2 > this.size || this.size >= this.buckets.length / 4 * 3) {
                this.rebuild();
            }
            
            Entry[] buckets = this.buckets;
            int index = System.identityHashCode(value) & (buckets.length - 1);
            buckets[index] = new Entry(value, tag, buckets[index], this.cleared);
            this.size++;
            return tag;
        }
        
        ///
        /// Drops every entry whose value object is gone and rebuilds the buckets with at least two per entry.
        ///
        private void rebuild() {
            List<Entry> alive = new ArrayList<>();
            for (Entry chain : this.buckets) {
                for (Entry entry = chain; entry != null; entry = entry.next) {
                    if (!entry.refersTo(null)) {
                        alive.add(entry);
                    }
                }
            }
            
            Entry[] buckets = new Entry[Math.max(Leaves.MIN_BUCKETS, Integer.highestOneBit(alive.size()) * 4)];
            for (Entry entry : alive) {
                Object value = entry.get();
                if (value != null) {
                    int index = System.identityHashCode(value) & (buckets.length - 1);
                    buckets[index] = new Entry(value, entry.tag, buckets[index], this.cleared);
                }
            }
            
            this.buckets = buckets;
            this.size = alive.size();
            this.gone = 0;
        }
        
        ///
        /// An entry of the table with the tag of one value object.
        ///
        private static final class Entry extends WeakReference<Object> {
            
            ///
            /// The tag at the end of the steps below the tree of the value object.
            ///
            private final Tag tag;
            
            ///
            /// The next entry in the same bucket, or `null` if this is the last one.
            ///
            private final @Nullable Entry next;
            
            ///
            /// Creates an entry of the given value object, put on the given queue once the value object is gone.
            ///
            private Entry(Object value, Tag tag, @Nullable Entry next, ReferenceQueue<Object> cleared) {
                super(value, cleared);
                this.tag = tag;
                this.next = next;
            }
        }
    }
}
