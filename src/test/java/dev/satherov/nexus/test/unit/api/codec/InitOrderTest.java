package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.result.CodecResult;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.internal.codec.Traversal;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

///
/// Checks that no codec class can initialize [NexusCodec], which would leave its constants `null` if a [MapKey] is used first and could deadlock two threads.
///
public class InitOrderTest {
    
    private static final String NEXUS_CODEC = "dev/satherov/nexus/api/codec/NexusCodec";
    
    private static final List<Class<?>> PACKAGES = List.of(Traversal.class, MapKey.class, CodecFormat.class, StructCodec.class, CodecResult.class);
    
    @Test
    public void nexusCodecDeclaresNoInstanceMethodWithABody() {
        Assertions.assertThat(NexusCodec.class.getDeclaredMethods())
                .filteredOn(InitOrderTest::hasInstanceBody)
                .isEmpty();
    }
    
    @Test
    public void noCodecClassReadsOrCallsTheStaticMembersOfNexusCodec() throws IOException, URISyntaxException {
        List<String> classFiles = new ArrayList<>();
        for (Class<?> member : InitOrderTest.PACKAGES) {
            classFiles.addAll(InitOrderTest.classFilesBeside(member));
        }
        
        List<String> offenders = new ArrayList<>();
        for (String classFile : classFiles) {
            if (InitOrderTest.initializesNexusCodec(classFile)) {
                offenders.add(classFile);
            }
        }
        
        Assertions.assertThat(classFiles).contains(
                "dev/satherov/nexus/internal/codec/Scalars.class",
                "dev/satherov/nexus/internal/codec/CollectionCodecs.class",
                "dev/satherov/nexus/internal/codec/vanilla/VanillaTypes.class",
                "dev/satherov/nexus/api/codec/key/MapKey.class"
        );
        Assertions.assertThat(offenders).isEmpty();
    }
    
    private static boolean hasInstanceBody(Method method) {
        return !Modifier.isStatic(method.getModifiers()) && !Modifier.isAbstract(method.getModifiers());
    }
    
    private static List<String> classFilesBeside(Class<?> member) throws IOException, URISyntaxException {
        String resource = member.getName().replace('.', '/') + ".class";
        URL url = Objects.requireNonNull(InitOrderTest.class.getClassLoader().getResource(resource), resource);
        Path directory = Path.of(url.toURI()).getParent();
        String prefix = resource.substring(0, resource.lastIndexOf('/') + 1);
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".class"))
                    .map(file -> prefix + directory.relativize(file).toString().replace(File.separatorChar, '/'))
                    .toList();
        }
    }
    
    private static boolean initializesNexusCodec(String classFile) throws IOException {
        byte[] bytes;
        try (InputStream stream = Objects.requireNonNull(InitOrderTest.class.getClassLoader().getResourceAsStream(classFile), classFile)) {
            bytes = stream.readAllBytes();
        }
        
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            Optional<CodeModel> code = method.code();
            if (code.isEmpty()) {
                continue;
            }
            
            for (CodeElement element : code.get()) {
                String owner = switch (element) {
                    case FieldInstruction field when field.opcode() == Opcode.GETSTATIC || field.opcode() == Opcode.PUTSTATIC -> field.owner().asInternalName();
                    case InvokeInstruction invoke when invoke.opcode() == Opcode.INVOKESTATIC -> invoke.owner().asInternalName();
                    default -> "";
                };
                if (owner.equals(InitOrderTest.NEXUS_CODEC)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
