package dev.satherov.nexus.test.unit.api.registry.attachment;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attachment.AttachmentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
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
    
    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    
    private static @Nullable AttachmentEntry<Counter> counter;
    private static @Nullable AttachmentEntry<Counter> persisted;
    
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
    
    private static CompoundTag save(PlainHolder holder) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        holder.serializeAttachments(output);
        return output.buildResult();
    }
    
    private static AttachmentEntry<Counter> counter() {
        return Objects.requireNonNull(AttachmentBuilderTest.counter);
    }
    
    private static AttachmentEntry<Counter> persisted() {
        return Objects.requireNonNull(AttachmentBuilderTest.persisted);
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
        }
    }
}
