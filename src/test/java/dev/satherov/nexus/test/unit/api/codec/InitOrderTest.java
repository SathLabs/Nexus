package dev.satherov.nexus.test.unit.api.codec;

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

///
/// Checks that every constant of [NexusCodec] is set if a [MapKey] is used before any codec.
///
public class InitOrderTest {
    
    @Test
    public void stringKeyFirstLeavesEveryConstantSet() throws ReflectiveOperationException {
        IsolatedLoader loader = new IsolatedLoader();
        Object key = loader.isolated(MapKey.class).getField("STRING").get(null);
        
        Assertions.assertThat(loader.unsetConstants()).isEmpty();
        Assertions.assertThat(loader.roundTrip(key, Map.of("count", 1))).isEqualTo(Map.of("count", 1));
    }
    
    @Test
    public void enumKeyFirstLeavesEveryConstantSet() throws ReflectiveOperationException {
        IsolatedLoader loader = new IsolatedLoader();
        Object key = loader.isolated(MapKey.class).getMethod("enumOf", Class.class).invoke(null, DayOfWeek.class);
        
        Assertions.assertThat(loader.unsetConstants()).isEmpty();
        Assertions.assertThat(loader.roundTrip(key, Map.of(DayOfWeek.MONDAY, 1))).isEqualTo(Map.of(DayOfWeek.MONDAY, 1));
    }
    
    private static final class IsolatedLoader extends ClassLoader {
        
        private static final List<String> ISOLATED = List.of("dev.satherov.nexus.api.", "dev.satherov.nexus.internal.");
        
        private IsolatedLoader() {
            super(InitOrderTest.class.getClassLoader());
        }
        
        private Class<?> isolated(Class<?> type) throws ClassNotFoundException {
            Class<?> isolated = this.loadClass(type.getName());
            Assertions.assertThat(isolated).isNotSameAs(type);
            return isolated;
        }
        
        private List<String> unsetConstants() throws ReflectiveOperationException {
            List<String> unset = new ArrayList<>();
            for (Field constant : this.isolated(NexusCodec.class).getFields()) {
                if (constant.get(null) == null) {
                    unset.add(constant.getName());
                }
            }
            
            return unset;
        }
        
        private Object roundTrip(Object key, Map<?, ?> map) throws ReflectiveOperationException {
            Class<?> codecType = this.isolated(NexusCodec.class);
            Class<?> formatType = this.isolated(CodecFormat.class);
            Object codec = codecType.getMethod("mapOf", this.isolated(MapKey.class), codecType).invoke(null, key, codecType.getField("INT").get(null));
            Object json = formatType.getField("JSON").get(null);
            Object encoded = codecType.getMethod("encode", formatType, Object.class).invoke(codec, json, map);
            return codecType.getMethod("decode", formatType, Object.class).invoke(codec, json, encoded);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (IsolatedLoader.ISOLATED.stream().noneMatch(name::startsWith)) {
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
