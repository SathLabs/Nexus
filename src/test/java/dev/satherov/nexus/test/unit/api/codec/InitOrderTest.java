package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.key.MapKey;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

///
/// Checks that every constant of [NexusCodec] is set if a [MapKey] is used before any codec.
///
public class InitOrderTest {
    
    @Test
    public void stringKeyFirstLeavesEveryConstantSet() throws ReflectiveOperationException {
        IsolatedLoader loader = new IsolatedLoader();
        
        Assertions.assertThat(loader.run(StringKeyFirst.class)).isEqualTo(Map.of("count", 1));
        Assertions.assertThat(loader.unsetConstants()).isEmpty();
    }
    
    @Test
    public void enumKeyFirstLeavesEveryConstantSet() throws ReflectiveOperationException {
        IsolatedLoader loader = new IsolatedLoader();
        
        Assertions.assertThat(loader.run(EnumKeyFirst.class)).isEqualTo(Map.of(DayOfWeek.MONDAY, 1));
        Assertions.assertThat(loader.unsetConstants()).isEmpty();
    }
    
    public static final class StringKeyFirst implements Supplier<Map<?, ?>> {
        
        @Override
        public Map<?, ?> get() {
            NexusCodec<Map<String, Integer>, Access.Plain> codec = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT);
            return codec.decode(CodecFormat.JSON, codec.encode(CodecFormat.JSON, Map.of("count", 1)));
        }
    }
    
    public static final class EnumKeyFirst implements Supplier<Map<?, ?>> {
        
        @Override
        public Map<?, ?> get() {
            NexusCodec<Map<DayOfWeek, Integer>, Access.Plain> codec = NexusCodec.mapOf(MapKey.enumOf(DayOfWeek.class), NexusCodec.INT);
            return codec.decode(CodecFormat.JSON, codec.encode(CodecFormat.JSON, Map.of(DayOfWeek.MONDAY, 1)));
        }
    }
    
    private static final class IsolatedLoader extends ClassLoader {
        
        private static final String ROOT = "dev.satherov.nexus.";
        
        private IsolatedLoader() {
            super(InitOrderTest.class.getClassLoader());
        }
        
        private Object run(Class<? extends Supplier<?>> probe) throws ReflectiveOperationException {
            Supplier<?> isolated = (Supplier<?>) this.loadClass(probe.getName()).getConstructor().newInstance();
            return isolated.get();
        }
        
        private List<String> unsetConstants() throws ReflectiveOperationException {
            List<String> unset = new ArrayList<>();
            for (Field constant : this.loadClass(NexusCodec.class.getName()).getFields()) {
                if (constant.get(null) == null) {
                    unset.add(constant.getName());
                }
            }
            
            return unset;
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(IsolatedLoader.ROOT)) {
                return super.loadClass(name, resolve);
            }
            
            synchronized (this.getClassLoadingLock(name)) {
                Class<?> loaded = this.findLoadedClass(name);
                if (loaded == null) {
                    loaded = this.define(name);
                }
                
                if (resolve) {
                    this.resolveClass(loaded);
                }
                
                return loaded;
            }
        }
        
        private Class<?> define(String name) throws ClassNotFoundException {
            try (InputStream stream = this.getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (stream == null) throw new ClassNotFoundException(name);
                byte[] bytes = stream.readAllBytes();
                return this.defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }
}
