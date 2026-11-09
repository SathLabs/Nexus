package dev.satherov.nexus.test.unit.internal.codec.path;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.internal.codec.path.TagReader;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

///
/// Checks that the tag reader follows keys, indices, and wildcards in an NBT tree and compares the tag it finds.
///
public class TagReaderTest {
    
    private static final Comparison EQUAL_THREE = new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D));
    private static final Comparison NOT_EQUAL_THREE = new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Numeric(3.0D));
    private static final Comparison GREATER_TWO = new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(2.0D));
    private static final Comparison EQUAL_STONE = new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Text("stone"));
    private static final Comparison NOT_EQUAL_STONE = new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Text("stone"));
    private static final Comparison EQUAL_TRUE = new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true));
    private static final Comparison NOT_EQUAL_TRUE = new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Flag(true));
    
    private static final List<Comparison> COMPARISONS = List.of(
            TagReaderTest.EQUAL_THREE,
            TagReaderTest.NOT_EQUAL_THREE,
            TagReaderTest.GREATER_TWO,
            TagReaderTest.EQUAL_STONE,
            TagReaderTest.NOT_EQUAL_STONE,
            TagReaderTest.EQUAL_TRUE,
            TagReaderTest.NOT_EQUAL_TRUE
    );
    
    @Test
    public void noStepsCompareTheRoot() {
        Assertions.assertThat(TagReaderTest.reader().test(IntTag.valueOf(3), TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader().test(IntTag.valueOf(3), TagReaderTest.NOT_EQUAL_THREE)).isFalse();
    }
    
    @Test
    public void keyReadsTheValueOfACompound() {
        CompoundTag root = new CompoundTag();
        root.putInt("count", 3);
        root.putString("name", "stone");
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("count")).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("count")).test(root, TagReaderTest.NOT_EQUAL_THREE)).isFalse();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("name")).test(root, TagReaderTest.EQUAL_STONE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("name")).test(root, TagReaderTest.EQUAL_THREE)).isFalse();
    }
    
    @Test
    public void keysReadNestedCompounds() {
        CompoundTag inner = new CompoundTag();
        inner.putInt("count", 3);
        CompoundTag root = new CompoundTag();
        root.put("inner", inner);
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("inner"), TagReaderTest.key("count")).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("inner")).test(root, TagReaderTest.NOT_EQUAL_THREE)).isFalse();
    }
    
    @Test
    public void indexReadsTheElementOfAList() {
        CompoundTag root = new CompoundTag();
        root.put("values", TagReaderTest.list(IntTag.valueOf(1), IntTag.valueOf(3)));
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("values"), TagReaderTest.index(1)).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("values"), TagReaderTest.index(0)).test(root, TagReaderTest.EQUAL_THREE)).isFalse();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("values"), TagReaderTest.index(0)).test(root, TagReaderTest.NOT_EQUAL_THREE)).isTrue();
    }
    
    @Test
    public void indexReadsTheElementOfAnArrayTag() {
        CompoundTag root = new CompoundTag();
        root.putByteArray("bytes", new byte[]{ 0, 1 });
        root.putIntArray("ints", new int[]{ 1, 3 });
        root.putLongArray("longs", new long[]{ 3L });
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("bytes"), TagReaderTest.index(1)).test(root, TagReaderTest.EQUAL_TRUE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("bytes"), TagReaderTest.index(0)).test(root, TagReaderTest.EQUAL_TRUE)).isFalse();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("ints"), TagReaderTest.index(1)).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("longs"), TagReaderTest.index(0)).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
    }
    
    @Test
    public void wildcardPassesIfAnyElementOfAListPasses() {
        CompoundTag first = new CompoundTag();
        first.putInt("count", 1);
        CompoundTag second = new CompoundTag();
        second.putInt("count", 3);
        CompoundTag root = new CompoundTag();
        root.put("entries", TagReaderTest.list(first, second));
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("entries"), TagReaderTest.any(), TagReaderTest.key("count")).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("entries"), TagReaderTest.any(), TagReaderTest.key("count")).test(root, TagReaderTest.GREATER_TWO)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("entries"), TagReaderTest.any(), TagReaderTest.key("count")).test(root, TagReaderTest.EQUAL_STONE)).isFalse();
    }
    
    @Test
    public void wildcardPassesIfAnyElementOfAnArrayTagPasses() {
        CompoundTag root = new CompoundTag();
        root.putIntArray("ints", new int[]{ 1, 3 });
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("ints"), TagReaderTest.any()).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("ints"), TagReaderTest.any()).test(root, TagReaderTest.GREATER_TWO)).isTrue();
    }
    
    @Test
    public void wildcardPassesIfAnyValueOfACompoundPasses() {
        CompoundTag counts = new CompoundTag();
        counts.putInt("first", 1);
        counts.putInt("second", 3);
        CompoundTag root = new CompoundTag();
        root.put("counts", counts);
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("counts"), TagReaderTest.any()).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("counts"), TagReaderTest.any()).test(root, TagReaderTest.EQUAL_STONE)).isFalse();
    }
    
    @Test
    public void wildcardFailsOnEmptyAndScalarTags() {
        CompoundTag root = new CompoundTag();
        root.put("list", new ListTag());
        root.put("compound", new CompoundTag());
        root.putInt("count", 3);
        
        for (Comparison comparison : TagReaderTest.COMPARISONS) {
            Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("list"), TagReaderTest.any()).test(root, comparison)).as(comparison.toString()).isFalse();
            Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("compound"), TagReaderTest.any()).test(root, comparison)).as(comparison.toString()).isFalse();
            Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("count"), TagReaderTest.any()).test(root, comparison)).as(comparison.toString()).isFalse();
        }
    }
    
    @Test
    public void absentPartsFailEveryComparison() {
        CompoundTag root = new CompoundTag();
        root.putInt("count", 3);
        root.put("values", TagReaderTest.list(IntTag.valueOf(3)));
        root.putIntArray("ints", new int[]{ 3 });
        List<TagReader> absent = List.of(
                TagReaderTest.reader(TagReaderTest.key("missing")),
                TagReaderTest.reader(TagReaderTest.key("values"), TagReaderTest.index(1)),
                TagReaderTest.reader(TagReaderTest.key("values"), TagReaderTest.key("count")),
                TagReaderTest.reader(TagReaderTest.index(0)),
                TagReaderTest.reader(TagReaderTest.key("count"), TagReaderTest.key("count")),
                TagReaderTest.reader(TagReaderTest.key("count"), TagReaderTest.index(0)),
                TagReaderTest.reader(TagReaderTest.key("ints"), TagReaderTest.index(0), TagReaderTest.key("count")),
                TagReaderTest.reader(TagReaderTest.key("ints"), TagReaderTest.any(), TagReaderTest.index(0))
        );
        
        for (TagReader reader : absent) {
            for (Comparison comparison : TagReaderTest.COMPARISONS) {
                Assertions.assertThat(reader.test(root, comparison)).as(reader + " " + comparison).isFalse();
            }
        }
    }
    
    @Test
    public void everyTagTypeComparesTheWayTheComparisonTestsIt() {
        List<Tag> tags = List.of(
                ByteTag.valueOf((byte) 1),
                ByteTag.valueOf((byte) 0),
                ShortTag.valueOf((short) 3),
                IntTag.valueOf(3),
                LongTag.valueOf(3L),
                FloatTag.valueOf(3.0F),
                DoubleTag.valueOf(2.5D),
                StringTag.valueOf("stone"),
                StringTag.valueOf("dirt"),
                new CompoundTag(),
                TagReaderTest.list(IntTag.valueOf(3)),
                new ByteArrayTag(new byte[]{ 1 }),
                new IntArrayTag(new int[]{ 3 }),
                new LongArrayTag(new long[]{ 3L })
        );
        
        for (Tag tag : tags) {
            CompoundTag root = new CompoundTag();
            root.put("part", tag);
            for (Comparison comparison : TagReaderTest.COMPARISONS) {
                Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("part")).test(root, comparison)).as(tag + " " + comparison).isEqualTo(comparison.test(tag));
            }
        }
    }
    
    @Test
    public void arrayElementsCompareTheWayTheirTagsDo() {
        List<CollectionTag> arrays = List.of(
                new ByteArrayTag(new byte[]{ 0, 1, 3 }),
                new IntArrayTag(new int[]{ 1, 3, -2 }),
                new LongArrayTag(new long[]{ 3L, Long.MAX_VALUE })
        );
        
        for (CollectionTag array : arrays) {
            CompoundTag root = new CompoundTag();
            root.put("part", array);
            for (Comparison comparison : TagReaderTest.COMPARISONS) {
                boolean any = false;
                for (int i = 0; i < array.size(); i++) {
                    any |= comparison.test(array.get(i));
                    Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("part"), TagReaderTest.index(i)).test(root, comparison)).as(array + "[" + i + "] " + comparison).isEqualTo(comparison.test(array.get(i)));
                }
                
                Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("part"), TagReaderTest.any()).test(root, comparison)).as(array + "[*] " + comparison).isEqualTo(any);
            }
        }
    }
    
    @Test
    public void tagTypesPassTheComparisonsOfTheirKind() {
        CompoundTag root = new CompoundTag();
        root.put("flag", ByteTag.valueOf(true));
        root.put("float", FloatTag.valueOf(3.0F));
        root.put("text", StringTag.valueOf("stone"));
        root.put("compound", new CompoundTag());
        
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("flag")).test(root, TagReaderTest.EQUAL_TRUE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("float")).test(root, TagReaderTest.EQUAL_THREE)).isTrue();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("text")).test(root, TagReaderTest.NOT_EQUAL_STONE)).isFalse();
        Assertions.assertThat(TagReaderTest.reader(TagReaderTest.key("compound")).test(root, TagReaderTest.NOT_EQUAL_THREE)).isFalse();
    }
    
    private static TagReader reader(ComponentPath.Step... steps) {
        return new TagReader(List.of(steps));
    }
    
    private static ComponentPath.Step key(String name) {
        return new ComponentPath.Step.Key(name);
    }
    
    private static ComponentPath.Step index(int index) {
        return new ComponentPath.Step.Index(index);
    }
    
    private static ComponentPath.Step any() {
        return new ComponentPath.Step.Wildcard();
    }
    
    private static ListTag list(Tag... elements) {
        ListTag list = new ListTag();
        list.addAll(List.of(elements));
        return list;
    }
}
