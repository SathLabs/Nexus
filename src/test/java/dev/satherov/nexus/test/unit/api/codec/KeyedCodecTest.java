package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.KeyedCodec;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.NexusCodecException;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

///
/// Checks keyed codecs on compound tags and on the value inputs and outputs of vanilla.
///
public class KeyedCodecTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(KeyedCodecTest.REGISTRIES);
    
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> DIRT = BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT);
    
    private static final KeyedCodec<Integer, Access.Plain> COUNT = NexusCodec.INT.keyed("count", 1);
    private static final KeyedCodec<Holder<Item>, Access.Registries> ITEM = NexusCodec.holder(Registries.ITEM).keyed("item", KeyedCodecTest.STONE);
    
    @Test
    public void writesAndReadsOnATag() {
        CompoundTag tag = new CompoundTag();
        KeyedCodecTest.COUNT.write(CodecFormat.NBT, tag, 64);
        
        Assertions.assertThat(tag.keySet()).containsExactly("count");
        Assertions.assertThat(tag.get("count")).isEqualTo(NexusCodec.INT.encode(CodecFormat.NBT, 64));
        Assertions.assertThat(KeyedCodecTest.COUNT.read(CodecFormat.NBT, tag)).isEqualTo(64);
    }
    
    @Test
    public void readsTheFallbackIfTheKeyIsMissing() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("amount", 64);
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        
        Assertions.assertThat(KeyedCodecTest.COUNT.read(CodecFormat.NBT, tag)).isEqualTo(1);
        Assertions.assertThat(KeyedCodecTest.COUNT.read(TagValueInput.create(problems, KeyedCodecTest.REGISTRIES, tag))).isEqualTo(1);
        Assertions.assertThat(problems.isEmpty()).isTrue();
    }
    
    @Test
    public void throwsTheFailureOfTheCodecOnABadValueOnATag() {
        Tag bad = StringTag.valueOf("many");
        CompoundTag tag = new CompoundTag();
        tag.put("count", bad);
        NexusCodecException direct = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.INT.decode(CodecFormat.NBT, bad));
        
        Assertions.assertThatThrownBy(() -> KeyedCodecTest.COUNT.read(CodecFormat.NBT, tag))
                .isInstanceOf(NexusCodecException.class)
                .hasMessage(direct.getMessage());
    }
    
    @Test
    public void reportsABadValueOnAnInputAndReadsTheFallback() {
        Tag bad = StringTag.valueOf("many");
        CompoundTag tag = new CompoundTag();
        tag.put("count", bad);
        NexusCodecException direct = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.INT.decode(CodecFormat.NBT, bad));
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        
        Assertions.assertThat(KeyedCodecTest.COUNT.read(TagValueInput.create(problems, KeyedCodecTest.REGISTRIES, tag))).isEqualTo(1);
        Assertions.assertThat(KeyedCodecTest.descriptionsOf(problems)).singleElement().asString().contains(direct.getMessage());
    }
    
    @Test
    public void writesAndReadsThroughAnOutputAndAnInput() {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, KeyedCodecTest.REGISTRIES);
        KeyedCodecTest.COUNT.write(output, 64);
        CompoundTag tag = output.buildResult();
        
        Assertions.assertThat(tag.keySet()).containsExactly("count");
        Assertions.assertThat(tag.get("count")).isEqualTo(NexusCodec.INT.encode(CodecFormat.NBT, 64));
        Assertions.assertThat(KeyedCodecTest.COUNT.read(TagValueInput.create(problems, KeyedCodecTest.REGISTRIES, tag))).isEqualTo(64);
        Assertions.assertThat(problems.isEmpty()).isTrue();
    }
    
    @Test
    public void writesAndReadsAHolderOnATag() {
        CompoundTag tag = new CompoundTag();
        KeyedCodecTest.ITEM.write(KeyedCodecTest.NBT, tag, KeyedCodecTest.DIRT);
        
        Assertions.assertThat(tag.get("item")).isEqualTo(StringTag.valueOf("minecraft:dirt"));
        Assertions.assertThat(KeyedCodecTest.ITEM.read(KeyedCodecTest.NBT, tag)).isEqualTo(KeyedCodecTest.DIRT);
        Assertions.assertThat(KeyedCodecTest.ITEM.read(KeyedCodecTest.NBT, new CompoundTag())).isEqualTo(KeyedCodecTest.STONE);
    }
    
    @Test
    public void writesAndReadsAHolderThroughAnOutputAndAnInput() {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, KeyedCodecTest.REGISTRIES);
        KeyedCodecTest.ITEM.write(output, KeyedCodecTest.DIRT);
        CompoundTag tag = output.buildResult();
        
        Assertions.assertThat(tag.get("item")).isEqualTo(StringTag.valueOf("minecraft:dirt"));
        Assertions.assertThat(KeyedCodecTest.ITEM.read(TagValueInput.create(problems, KeyedCodecTest.REGISTRIES, tag))).isEqualTo(KeyedCodecTest.DIRT);
        Assertions.assertThat(KeyedCodecTest.ITEM.read(TagValueInput.create(problems, KeyedCodecTest.REGISTRIES, new CompoundTag()))).isEqualTo(KeyedCodecTest.STONE);
        Assertions.assertThat(problems.isEmpty()).isTrue();
    }
    
    @Test
    public void reportsAHolderOnAnOutputWithoutRegistriesAndStoresNothing() {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithoutContext(problems);
        KeyedCodecTest.ITEM.write(output, KeyedCodecTest.DIRT);
        
        Assertions.assertThat(KeyedCodecTest.descriptionsOf(problems)).hasSize(1);
        Assertions.assertThat(output.buildResult().isEmpty()).isTrue();
    }
    
    private static List<String> descriptionsOf(ProblemReporter.Collector problems) {
        List<String> descriptions = new ArrayList<>();
        problems.forEach((_, problem) -> descriptions.add(problem.description()));
        return descriptions;
    }
}
