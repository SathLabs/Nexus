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
import net.neoforged.neoforge.attachment.AttachmentInternals;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// Checks the values that attachment entries read from, write to, update, and remove from a holder, the form their types save them in,
/// and that a value their codec refuses leaves the other attachments of the holder to load, save, and copy.
///
public class AttachmentBuilderTest {

    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    private static final StructCodec<Counter, Access.Plain> CHECKED = NexusCodec.struct(
            "checked",
            NexusCodec.INT.validate(count -> count < 0 ? "The count is negative" : null).field("count", Counter::count),
            Counter::new
    );
    
    private static final Format<Counter> FORMAT = Format.of(AttachmentBuilderTest.CODEC).upgradeFrom(0, AttachmentBuilderTest::renameStacks);

    private static @Nullable AttachmentEntry<Counter> counter;
    private static @Nullable AttachmentEntry<Counter> persisted;
    private static @Nullable AttachmentEntry<Counter> formatted;
    private static @Nullable AttachmentEntry<Counter> formatAfterCodec;
    private static @Nullable AttachmentEntry<Counter> codecAfterFormat;
    private static @Nullable AttachmentEntry<Counter> checked;
    private static @Nullable AttachmentEntry<List<Integer>> list;

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
    public void updateWithTheSameValueSetsItAgain() {
        AttachmentEntry<List<Integer>> list = AttachmentBuilderTest.list();
        PlainHolder holder = new PlainHolder();
        List<Integer> current = list.get(holder);
        Assertions.assertThat(list.update(holder, AttachmentBuilderTest::addOne)).isSameAs(current);
        Assertions.assertThat(list.get(holder)).containsExactly(1);
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

    @Test
    public void loadsTheOtherAttachmentsBesideOneTheCodecRefuses() {
        AttachmentEntry<Counter> checked = AttachmentBuilderTest.checked();
        AttachmentEntry<Counter> persisted = AttachmentBuilderTest.persisted();
        CompoundTag saved = new CompoundTag();
        saved.put(checked.getId().toString(), AttachmentBuilderTest.tag("count", -1));
        saved.put(persisted.getId().toString(), AttachmentBuilderTest.tag("count", 5));
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        PlainHolder loaded = new PlainHolder();
        loaded.load(TagValueInput.create(reporter, AttachmentBuilderTest.REGISTRIES, saved));
        Assertions.assertThat(reporter.isEmpty()).isTrue();
        Assertions.assertThat(checked.has(loaded)).isFalse();
        Assertions.assertThat(persisted.get(loaded)).isEqualTo(new Counter(5));
    }
    
    @Test
    public void savesTheOtherAttachmentsBesideOneTheCodecCannotWrite() {
        AttachmentEntry<Counter> checked = AttachmentBuilderTest.checked();
        AttachmentEntry<Counter> persisted = AttachmentBuilderTest.persisted();
        PlainHolder holder = new PlainHolder();
        checked.set(holder, new Counter(-1));
        persisted.set(holder, new Counter(5));
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        CompoundTag saved = AttachmentBuilderTest.save(holder, reporter);
        Assertions.assertThat(reporter.isEmpty()).isTrue();
        Assertions.assertThat(saved.contains(checked.getId().toString())).isFalse();
        Assertions.assertThat(saved.get(persisted.getId().toString())).isEqualTo(AttachmentBuilderTest.tag("count", 5));
    }
    
    @Test
    public void copiesTheOtherAttachmentsBesideOneTheCodecCannotWrite() {
        AttachmentEntry<Counter> checked = AttachmentBuilderTest.checked();
        AttachmentEntry<Counter> persisted = AttachmentBuilderTest.persisted();
        AttachmentHolder.AsField from = new AttachmentHolder.AsField(new PlainHolder());
        checked.set(from, new Counter(-1));
        persisted.set(from, new Counter(5));
        AttachmentHolder.AsField to = new AttachmentHolder.AsField(new PlainHolder());
        AttachmentInternals.copyChunkAttachmentsOnPromotion(AttachmentBuilderTest.REGISTRIES, from, to);
        Assertions.assertThat(checked.has(to)).isFalse();
        Assertions.assertThat(persisted.get(to)).isEqualTo(new Counter(5));
    }
    
    private static CompoundTag save(PlainHolder holder) {
        return AttachmentBuilderTest.save(holder, ProblemReporter.DISCARDING);
    }
    
    private static CompoundTag save(PlainHolder holder, ProblemReporter reporter) {
        TagValueOutput output = TagValueOutput.createWithContext(reporter, AttachmentBuilderTest.REGISTRIES);
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
    
    private static List<Integer> addOne(List<Integer> values) {
        values.add(1);
        return values;
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
    
    private static AttachmentEntry<Counter> checked() {
        return Objects.requireNonNull(AttachmentBuilderTest.checked);
    }
    
    private static AttachmentEntry<List<Integer>> list() {
        return Objects.requireNonNull(AttachmentBuilderTest.list);
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
            AttachmentBuilderTest.checked = registrations.attachment("attachment_builder_test_checked", () -> new Counter(1))
                    .codec(AttachmentBuilderTest.CHECKED)
                    .register();
            AttachmentBuilderTest.list = registrations.<List<Integer>>attachment("attachment_builder_test_list", ArrayList::new).register();
        }
    }
}
