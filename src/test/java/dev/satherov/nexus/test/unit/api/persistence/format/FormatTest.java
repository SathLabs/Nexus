package dev.satherov.nexus.test.unit.api.persistence.format;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.format.Format;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.function.UnaryOperator;

///
/// Checks that a format upgrades old tags step by step, refuses steps that leave a gap, and nests through its codec.
///
public class FormatTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(FormatTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(FormatTest.REGISTRIES);
    
    private static final StructCodec<Grave, Access.Plain> GRAVE = NexusCodec.struct(
            "grave",
            NexusCodec.STRING.field("owner", Grave::owner),
            NexusCodec.INT.field("count", Grave::count),
            Grave::new
    );
    
    private static final Format<Grave> FORMAT = Format.of(FormatTest.GRAVE)
            .upgradeFrom(1, tag -> FormatTest.rename(tag, "stacks", "count"))
            .upgradeFrom(0, tag -> FormatTest.rename(tag, "name", "owner"));
    
    private static final Grave STEVE = new Grave("Steve", 3);
    
    @Test
    public void readsEveryVersionThroughTheSteps() {
        Assertions.assertThat(FormatTest.FORMAT.read(FormatTest.grave("name", "stacks"), FormatTest.REGISTRIES)).isEqualTo(FormatTest.STEVE);
        Assertions.assertThat(FormatTest.FORMAT.read(FormatTest.versioned(1, FormatTest.grave("owner", "stacks")), FormatTest.REGISTRIES)).isEqualTo(FormatTest.STEVE);
        Assertions.assertThat(FormatTest.FORMAT.read(FormatTest.versioned(2, FormatTest.grave("owner", "count")), FormatTest.REGISTRIES)).isEqualTo(FormatTest.STEVE);
    }
    
    @Test
    public void refusesATagNewerThanTheFormat() {
        Tag newer = FormatTest.versioned(7, FormatTest.grave("owner", "count"));
        
        Assertions.assertThatThrownBy(() -> FormatTest.FORMAT.read(newer, FormatTest.REGISTRIES))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("7");
    }
    
    @Test
    public void reportsTheVersionThatHasNoStep() {
        Format<Grave> format = Format.of(FormatTest.GRAVE).upgradeFrom(1, tag -> FormatTest.rename(tag, "stacks", "count"));
        
        Assertions.assertThatThrownBy(() -> format.read(FormatTest.grave("name", "stacks"), FormatTest.REGISTRIES))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("0");
    }
    
    @Test
    public void reportsTheStepThatFailed() {
        Format<Grave> format = FormatTest.FORMAT.upgradeFrom(2, tag -> {
            throw new IllegalStateException("corrupt");
        });
        
        Assertions.assertThatThrownBy(() -> format.read(FormatTest.versioned(1, FormatTest.grave("owner", "stacks")), FormatTest.REGISTRIES))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("2");
    }
    
    @Test
    public void refusesAGapWhenBuilt() {
        Assertions.assertThatThrownBy(() -> Format.of(FormatTest.GRAVE).upgradeFrom(2, UnaryOperator.identity())).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> Format.of(FormatTest.GRAVE).upgradeFrom(1, UnaryOperator.identity()).upgradeFrom(3, UnaryOperator.identity()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesADoubledStepWhenBuilt() {
        Assertions.assertThatThrownBy(() -> Format.of(FormatTest.GRAVE).upgradeFrom(1, UnaryOperator.identity()).upgradeFrom(1, UnaryOperator.identity()))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> Format.of(FormatTest.GRAVE).upgradeFrom(0, UnaryOperator.identity()).upgradeFrom(0, UnaryOperator.identity()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesANegativeVersionWhenBuilt() {
        Assertions.assertThatThrownBy(() -> Format.of(FormatTest.GRAVE).upgradeFrom(0, UnaryOperator.identity()).upgradeFrom(-1, UnaryOperator.identity()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void upgradeFromLeavesTheReceiverUnchanged() {
        Format<Grave> upgraded = FormatTest.FORMAT.upgradeFrom(2, tag -> {
            throw new IllegalStateException("corrupt");
        });
        
        Assertions.assertThat(upgraded.getVersion()).isEqualTo(3);
        Assertions.assertThat(FormatTest.FORMAT.getVersion()).isEqualTo(2);
        Assertions.assertThat(FormatTest.FORMAT.read(FormatTest.versioned(1, FormatTest.grave("owner", "stacks")), FormatTest.REGISTRIES)).isEqualTo(FormatTest.STEVE);
    }
    
    @Test
    public void writesTheCurrentVersion() {
        Tag written = FormatTest.FORMAT.write(FormatTest.STEVE, FormatTest.REGISTRIES);
        CompoundTag compound = (CompoundTag) written;
        
        Assertions.assertThat(compound.keySet()).containsExactlyInAnyOrder("version", "data");
        Assertions.assertThat(compound.getIntOr("version", -1)).isEqualTo(2);
        Assertions.assertThat(compound.get("data")).isEqualTo(FormatTest.grave("owner", "count"));
        Assertions.assertThat(FormatTest.FORMAT.read(written, FormatTest.REGISTRIES)).isEqualTo(FormatTest.STEVE);
    }
    
    @Test
    public void codecNestsInsideAStructAndUpgradesOnNbt() {
        StructCodec<Plot, Access.Registries> plot = NexusCodec.struct(
                "plot",
                NexusCodec.STRING.field("name", Plot::name),
                FormatTest.FORMAT.codec().field("grave", Plot::grave),
                Plot::new
        );
        CompoundTag input = new CompoundTag();
        input.putString("name", "garden");
        input.put("grave", FormatTest.versioned(1, FormatTest.grave("owner", "stacks")));
        
        Assertions.assertThat(plot.decode(FormatTest.NBT, input)).isEqualTo(new Plot("garden", FormatTest.STEVE));
    }
    
    @Test
    public void codecOnJsonRefusesAnOldVersion() {
        JsonElement current = JsonParser.parseString("{\"version\": 2, \"data\": {\"owner\": \"Steve\", \"count\": 3}}");
        JsonElement old = JsonParser.parseString("{\"version\": 1, \"data\": {\"owner\": \"Steve\", \"stacks\": 3}}");
        
        Assertions.assertThat(FormatTest.FORMAT.codec().decode(FormatTest.JSON, current)).isEqualTo(FormatTest.STEVE);
        Assertions.assertThatThrownBy(() -> FormatTest.FORMAT.codec().decode(FormatTest.JSON, old)).isInstanceOf(NexusCodecException.class);
    }
    
    private static CompoundTag grave(String ownerKey, String countKey) {
        CompoundTag grave = new CompoundTag();
        grave.putString(ownerKey, "Steve");
        grave.putInt(countKey, 3);
        return grave;
    }
    
    private static CompoundTag versioned(int version, Tag data) {
        CompoundTag versioned = new CompoundTag();
        versioned.putInt("version", version);
        versioned.put("data", data);
        return versioned;
    }
    
    private static Tag rename(Tag tag, String from, String to) {
        CompoundTag renamed = ((CompoundTag) tag).copy();
        renamed.put(to, Objects.requireNonNull(renamed.get(from)));
        renamed.remove(from);
        return renamed;
    }
    
    public record Grave(String owner, int count) { }
    
    public record Plot(String name, Grave grave) { }
}
