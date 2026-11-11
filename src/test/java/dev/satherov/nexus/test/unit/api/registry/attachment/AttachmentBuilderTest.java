package dev.satherov.nexus.test.unit.api.registry.attachment;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attachment.AttachmentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the values that attachment entries read from, write to, and remove from a holder, and the form their types save them in.
///
public class AttachmentBuilderTest {

    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    private static final Format<Counter> FORMAT = Format.of(AttachmentBuilderTest.CODEC).upgradeFrom(0, AttachmentBuilderTest::renameStacks);

    private static @Nullable AttachmentEntry<Counter> counter;
    private static @Nullable AttachmentEntry<Counter> persisted;
    private static @Nullable AttachmentEntry<Counter> formatted;
    private static @Nullable AttachmentEntry<Counter> formatAfterCodec;
    private static @Nullable AttachmentEntry<Counter> codecAfterFormat;

    @Test
    public void createsTheInitialValueOnTheFirstRead() {
        AttachmentEntry<Counter> counter = AttachmentBuilderTest.counter();
        PlainHolder holder = new PlainHolder();
        Assertions.assertThat(counter.has(holder)).isFalse();
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(1));
        Assertions.assertThat(counter.has(holder)).isTrue();
    }
    
    @Test
    public void readsTheValueThatWasSet() {
        AttachmentEntry<Counter> counter = AttachmentBuilderTest.counter();
        PlainHolder holder = new PlainHolder();
        counter.set(holder, new Counter(5));
        Assertions.assertThat(counter.has(holder)).isTrue();
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(5));
    }
    
    @Test
    public void replacesTheValueThatWasSet() {
        AttachmentEntry<Counter> counter = AttachmentBuilderTest.counter();
        PlainHolder holder = new PlainHolder();
        counter.set(holder, new Counter(5));
        counter.set(holder, new Counter(6));
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(6));
    }
    
    @Test
    public void removesTheValue() {
        AttachmentEntry<Counter> counter = AttachmentBuilderTest.counter();
        PlainHolder holder = new PlainHolder();
        counter.set(holder, new Counter(5));
        Assertions.assertThat(counter.remove(holder)).isEqualTo(new Counter(5));
        Assertions.assertThat(counter.has(holder)).isFalse();
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(1));
    }
    
    @Test
    public void removesNothingFromAHolderWithoutTheValue() {
        PlainHolder holder = new PlainHolder();
        Assertions.assertThat(AttachmentBuilderTest.counter().remove(holder)).isNull();
    }
    
    @Test
    public void savesAndLoadsTheValueWithTheCodec() {
        AttachmentEntry<Counter> persisted = AttachmentBuilderTest.persisted();
        PlainHolder holder = new PlainHolder();
        persisted.set(holder, new Counter(5));
        CompoundTag saved = AttachmentBuilderTest.save(holder);
        CompoundTag expected = new CompoundTag();
        expected.putInt("count", 5);
        Assertions.assertThat(saved.get(persisted.getId().toString())).isEqualTo(expected);
        
        PlainHolder loaded = new PlainHolder();
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, saved));
        Assertions.assertThat(persisted.has(loaded)).isTrue();
        Assertions.assertThat(persisted.get(loaded)).isEqualTo(new Counter(5));
    }
    
    @Test
    public void savesNothingWithoutACodec() {
        AttachmentEntry<Counter> counter = AttachmentBuilderTest.counter();
        PlainHolder holder = new PlainHolder();
        counter.set(holder, new Counter(5));
        Assertions.assertThat(AttachmentBuilderTest.save(holder).contains(counter.getId().toString())).isFalse();
    }

    @Test
    public void savesTheValueFlatBesideTheVersionOfTheFormat() {
        AttachmentEntry<Counter> formatted = AttachmentBuilderTest.formatted();
        PlainHolder holder = new PlainHolder();
        formatted.set(holder, new Counter(5));
        CompoundTag saved = AttachmentBuilderTest.save(holder);
        Assertions.assertThat(saved.get(formatted.getId().toString())).isEqualTo(AttachmentBuilderTest.versioned(5));
        Assertions.assertThat(formatted.get(AttachmentBuilderTest.load(saved))).isEqualTo(new Counter(5));
    }

    @Test
    public void loadsAMapWithoutAVersionThroughTheSteps() {
        AttachmentEntry<Counter> formatted = AttachmentBuilderTest.formatted();
        CompoundTag saved = new CompoundTag();
        saved.put(formatted.getId().toString(), AttachmentBuilderTest.tag("stacks", 5));
        PlainHolder loaded = AttachmentBuilderTest.load(saved);
        Assertions.assertThat(formatted.has(loaded)).isTrue();
        Assertions.assertThat(formatted.get(loaded)).isEqualTo(new Counter(5));
    }

    @Test
    public void formatReplacesAnEarlierCodec() {
        AttachmentEntry<Counter> formatAfterCodec = AttachmentBuilderTest.formatAfterCodec();
        PlainHolder holder = new PlainHolder();
        formatAfterCodec.set(holder, new Counter(5));
        Assertions.assertThat(AttachmentBuilderTest.save(holder).get(formatAfterCodec.getId().toString())).isEqualTo(AttachmentBuilderTest.versioned(5));
    }

    @Test
    public void codecReplacesAnEarlierFormat() {
        AttachmentEntry<Counter> codecAfterFormat = AttachmentBuilderTest.codecAfterFormat();
        PlainHolder holder = new PlainHolder();
        codecAfterFormat.set(holder, new Counter(5));
        Assertions.assertThat(AttachmentBuilderTest.save(holder).get(codecAfterFormat.getId().toString())).isEqualTo(AttachmentBuilderTest.tag("count", 5));
    }

    private static CompoundTag save(PlainHolder holder) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, AttachmentBuilderTest.REGISTRIES);
        holder.serializeAttachments(output);
        return output.buildResult();
    }

    private static PlainHolder load(CompoundTag saved) {
        PlainHolder holder = new PlainHolder();
        holder.load(TagValueInput.create(ProblemReporter.DISCARDING, AttachmentBuilderTest.REGISTRIES, saved));
        return holder;
    }

    private static CompoundTag tag(String key, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, count);
        return tag;
    }

    private static CompoundTag versioned(int count) {
        CompoundTag versioned = AttachmentBuilderTest.tag("count", count);
        versioned.putInt("version", 1);
        return versioned;
    }

    private static Tag renameStacks(Tag tag) {
        CompoundTag renamed = ((CompoundTag) tag).copy();
        renamed.put("count", Objects.requireNonNull(renamed.get("stacks")));
        renamed.remove("stacks");
        return renamed;
    }

    private static AttachmentEntry<Counter> counter() {
        return Objects.requireNonNull(AttachmentBuilderTest.counter);
    }
    
    private static AttachmentEntry<Counter> persisted() {
        return Objects.requireNonNull(AttachmentBuilderTest.persisted);
    }

    private static AttachmentEntry<Counter> formatted() {
        return Objects.requireNonNull(AttachmentBuilderTest.formatted);
    }

    private static AttachmentEntry<Counter> formatAfterCodec() {
        return Objects.requireNonNull(AttachmentBuilderTest.formatAfterCodec);
    }

    private static AttachmentEntry<Counter> codecAfterFormat() {
        return Objects.requireNonNull(AttachmentBuilderTest.codecAfterFormat);
    }

    private record Counter(int count) { }
    
    private static final class PlainHolder extends AttachmentHolder {
        
        public void load(ValueInput input) {
            this.deserializeAttachments(input);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            AttachmentBuilderTest.counter = registrations.attachment("attachment_builder_test_counter", () -> new Counter(1)).register();
            AttachmentBuilderTest.persisted = registrations.attachment("attachment_builder_test_persisted", () -> new Counter(1))
                    .copyOnDeath()
                    .codec(AttachmentBuilderTest.CODEC)
                    .sync(AttachmentBuilderTest.CODEC)
                    .register();
            AttachmentBuilderTest.formatted = registrations.attachment("attachment_builder_test_formatted", () -> new Counter(1))
                    .format(AttachmentBuilderTest.FORMAT)
                    .register();
            AttachmentBuilderTest.formatAfterCodec = registrations.attachment("attachment_builder_test_format_after_codec", () -> new Counter(1))
                    .codec(AttachmentBuilderTest.CODEC)
                    .format(AttachmentBuilderTest.FORMAT)
                    .register();
            AttachmentBuilderTest.codecAfterFormat = registrations.attachment("attachment_builder_test_codec_after_format", () -> new Counter(1))
                    .format(AttachmentBuilderTest.FORMAT)
                    .codec(AttachmentBuilderTest.CODEC)
                    .register();
        }
    }
}
