package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.format.CodecFormat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

///
/// Measures how long the codecs take against their DFU and stream codec twins over the parity cases, and writes the
/// result to a report.
///
public class SpeedTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(SpeedTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(SpeedTest.REGISTRIES);
    private static final RegistryOps<JsonElement> JSON_OPS = RegistryOps.create(JsonOps.INSTANCE, SpeedTest.REGISTRIES);
    private static final RegistryOps<Tag> NBT_OPS = RegistryOps.create(NbtOps.INSTANCE, SpeedTest.REGISTRIES);
    private static final long WARMUP_NANOS = 30_000_000L;
    private static final long MEASURE_NANOS = 40_000_000L;
    private static final int ROUNDS = 3;
    private static final double ALLOWED_SLOWDOWN = 1.25D;
    private static final Path REPORT = Path.of(System.getProperty("nexus.codec.speed.report", "build/reports/codec/speed.md"));
    private static final List<Measurement> MEASUREMENTS = new ArrayList<>();
    
    private static int sink;
    
    @Test
    public void encodesJsonAtLeastAsFastAsDfu() {
        this.measure("json encode", SpeedTest::encodeJson, SpeedTest::encodeJsonDfu);
    }
    
    @Test
    public void decodesJsonAtLeastAsFastAsDfu() {
        this.measure("json decode", SpeedTest::decodeJson, SpeedTest::decodeJsonDfu);
    }
    
    @Test
    public void encodesNbtAtLeastAsFastAsDfu() {
        this.measure("nbt encode", SpeedTest::encodeNbt, SpeedTest::encodeNbtDfu);
    }
    
    @Test
    public void decodesNbtAtLeastAsFastAsDfu() {
        this.measure("nbt decode", SpeedTest::decodeNbt, SpeedTest::decodeNbtDfu);
    }
    
    @Test
    public void encodesNettyAtLeastAsFastAsStreamCodecs() {
        this.measure("netty encode", SpeedTest::encodeNetty, SpeedTest::encodeNettyStream);
    }
    
    @Test
    public void decodesNettyAtLeastAsFastAsStreamCodecs() {
        this.measure("netty decode", SpeedTest::decodeNetty, SpeedTest::decodeNettyStream);
    }
    
    @AfterAll
    public static void writeReport() {
        List<String> lines = new ArrayList<>(List.of("# Codec speed", "", "Nanoseconds per value, the best of three alternating rounds, less is better. The typical row is the geometric mean of the ratios of the operation, and an outlier past twice the twin is marked.", "", "| Operation | Case | Nexus | Twin | Nexus / Twin |", "|---|---|---:|---:|---:|"));
        SpeedTest.MEASUREMENTS.stream().map(Measurement::row).forEach(lines::add);
        try {
            Files.createDirectories(SpeedTest.REPORT.toAbsolutePath().getParent());
            Files.write(SpeedTest.REPORT, lines);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write the speed report to '" + SpeedTest.REPORT.toAbsolutePath() + "'", failure);
        }
    }
    
    private void measure(String operation, Function<ParityCases.Case<?>, Runnable> nexusFor, Function<ParityCases.Case<?>, Runnable> twinFor) {
        double logRatios = 0.0D;
        for (ParityCases.Case<?> parity : ParityCases.ALL) {
            Runnable nexus = nexusFor.apply(parity);
            Runnable twin = twinFor.apply(parity);
            SpeedTest.runFor(SpeedTest.WARMUP_NANOS, nexus);
            SpeedTest.runFor(SpeedTest.WARMUP_NANOS, twin);
            double nexusNanos = Double.MAX_VALUE;
            double twinNanos = Double.MAX_VALUE;
            for (int round = 0; round < SpeedTest.ROUNDS; round++) {
                nexusNanos = Math.min(nexusNanos, SpeedTest.nanosPerValue(parity, nexus));
                twinNanos = Math.min(twinNanos, SpeedTest.nanosPerValue(parity, twin));
            }
            
            SpeedTest.MEASUREMENTS.add(new Measurement(operation, parity.name(), nexusNanos, twinNanos));
            logRatios += Math.log(nexusNanos / twinNanos);
        }
        
        double typicalRatio = Math.exp(logRatios / ParityCases.ALL.size());
        SpeedTest.MEASUREMENTS.add(new Measurement(operation, "typical", typicalRatio, 1.0D));
        Assertions.assertThat(typicalRatio).as("%s, nexus takes %.2f times as long as the twin over a typical case", operation, typicalRatio).isLessThanOrEqualTo(SpeedTest.ALLOWED_SLOWDOWN);
    }
    
    private static double nanosPerValue(ParityCases.Case<?> parity, Runnable operation) {
        long runs = SpeedTest.runFor(SpeedTest.MEASURE_NANOS, operation);
        return (double) SpeedTest.MEASURE_NANOS / (runs * parity.values().size());
    }
    
    private static long runFor(long nanos, Runnable operation) {
        long end = System.nanoTime() + nanos;
        long runs = 0L;
        do {
            operation.run();
            runs++;
        } while (System.nanoTime() < end);
        return runs;
    }
    
    private static <T> Runnable encodeJson(ParityCases.Case<T> parity) {
        return () -> {
            for (T value : parity.values()) {
                SpeedTest.sink += parity.codec().encode(SpeedTest.JSON, value).hashCode();
            }
        };
    }
    
    private static <T> Runnable encodeJsonDfu(ParityCases.Case<T> parity) {
        return () -> {
            for (T value : parity.values()) {
                SpeedTest.sink += parity.dfu().encodeStart(SpeedTest.JSON_OPS, value).getOrThrow().hashCode();
            }
        };
    }
    
    private static <T> Runnable decodeJson(ParityCases.Case<T> parity) {
        List<JsonElement> inputs = SpeedTest.jsonOf(parity);
        return () -> {
            for (JsonElement input : inputs) {
                SpeedTest.sink += parity.codec().decode(SpeedTest.JSON, input).hashCode();
            }
        };
    }
    
    private static <T> Runnable decodeJsonDfu(ParityCases.Case<T> parity) {
        List<JsonElement> inputs = SpeedTest.jsonOf(parity);
        return () -> {
            for (JsonElement input : inputs) {
                SpeedTest.sink += parity.dfu().parse(SpeedTest.JSON_OPS, input).getOrThrow().hashCode();
            }
        };
    }
    
    private static <T> Runnable encodeNbt(ParityCases.Case<T> parity) {
        return () -> {
            for (T value : parity.values()) {
                SpeedTest.sink += parity.codec().encode(SpeedTest.NBT, value).hashCode();
            }
        };
    }
    
    private static <T> Runnable encodeNbtDfu(ParityCases.Case<T> parity) {
        return () -> {
            for (T value : parity.values()) {
                SpeedTest.sink += parity.dfu().encodeStart(SpeedTest.NBT_OPS, value).getOrThrow().hashCode();
            }
        };
    }
    
    private static <T> Runnable decodeNbt(ParityCases.Case<T> parity) {
        List<Tag> inputs = SpeedTest.nbtOf(parity);
        return () -> {
            for (Tag input : inputs) {
                SpeedTest.sink += parity.codec().decode(SpeedTest.NBT, input).hashCode();
            }
        };
    }
    
    private static <T> Runnable decodeNbtDfu(ParityCases.Case<T> parity) {
        List<Tag> inputs = SpeedTest.nbtOf(parity);
        return () -> {
            for (Tag input : inputs) {
                SpeedTest.sink += parity.dfu().parse(SpeedTest.NBT_OPS, input).getOrThrow().hashCode();
            }
        };
    }
    
    private static <T> Runnable encodeNetty(ParityCases.Case<T> parity) {
        return () -> {
            RegistryFriendlyByteBuf buffer = SpeedTest.buffer();
            for (T value : parity.values()) {
                parity.codec().encode(CodecFormat.netty(buffer), value);
            }
            
            SpeedTest.sink += buffer.writerIndex();
            buffer.release();
        };
    }
    
    private static <T> Runnable encodeNettyStream(ParityCases.Case<T> parity) {
        return () -> {
            RegistryFriendlyByteBuf buffer = SpeedTest.buffer();
            for (T value : parity.values()) {
                parity.stream().encode(buffer, value);
            }
            
            SpeedTest.sink += buffer.writerIndex();
            buffer.release();
        };
    }
    
    private static <T> Runnable decodeNetty(ParityCases.Case<T> parity) {
        RegistryFriendlyByteBuf buffer = SpeedTest.encoded(parity);
        return () -> {
            buffer.readerIndex(0);
            for (int i = 0; i < parity.values().size(); i++) {
                SpeedTest.sink += parity.codec().decode(CodecFormat.netty(buffer)).hashCode();
            }
        };
    }
    
    private static <T> Runnable decodeNettyStream(ParityCases.Case<T> parity) {
        RegistryFriendlyByteBuf buffer = SpeedTest.encoded(parity);
        return () -> {
            buffer.readerIndex(0);
            for (int i = 0; i < parity.values().size(); i++) {
                SpeedTest.sink += parity.stream().decode(buffer).hashCode();
            }
        };
    }
    
    private static <T> List<JsonElement> jsonOf(ParityCases.Case<T> parity) {
        return parity.values().stream().map(value -> parity.dfu().encodeStart(SpeedTest.JSON_OPS, value).getOrThrow()).toList();
    }
    
    private static <T> List<Tag> nbtOf(ParityCases.Case<T> parity) {
        return parity.values().stream().map(value -> parity.dfu().encodeStart(SpeedTest.NBT_OPS, value).getOrThrow()).toList();
    }
    
    private static <T> RegistryFriendlyByteBuf encoded(ParityCases.Case<T> parity) {
        RegistryFriendlyByteBuf buffer = SpeedTest.buffer();
        for (T value : parity.values()) {
            parity.stream().encode(buffer, value);
        }
        
        return buffer;
    }
    
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), SpeedTest.REGISTRIES);
    }
    
    private record Measurement(String operation, String name, double nexusNanos, double twinNanos) {
        
        private String row() {
            double ratio = this.nexusNanos / this.twinNanos;
            return String.format(Locale.ROOT, "| %s | %s | %.0f | %.0f | %.2f%s |", this.operation, this.name, this.nexusNanos, this.twinNanos, ratio, ratio > 2.0D ? " !" : "");
        }
    }
}
