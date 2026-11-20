package dev.satherov.nexus.test.unit;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

///
/// Checks the client rule of the library over every class a dedicated server can load: a client class appears in a
/// common class only as a type argument, as the parameter of a method whose body runs on the client alone, or behind a
/// method reference or a plain `new` inside a lambda.
///
/// A client class is one the Minecraft jar's manifest marks as client-only, which is what FML's dist cleaner masks on a
/// dedicated server, a class of a client-only library or NeoForge's client package, and a class of Nexus that sits in
/// a package named `client`, ends its name in `Client`, or subscribes to the client dist only. Every other class of
/// Nexus is common, except mixins, which are applied to their targets and never loaded on their own, and the unit tests.
///
public class ClientClassesTest {

    private static final String NEXUS = "dev.satherov.nexus.";
    private static final String UNIT_TESTS = "dev.satherov.nexus.test.unit.";
    private static final String CLIENT = "client";
    private static final String MIXIN = "mixin";
    private static final String LAMBDA = "lambda$";
    private static final String CONSTRUCTOR = "<init>";

    private static final Attributes.Name DISTS = new Attributes.Name("Minecraft-Dists");
    private static final Attributes.Name DIST = new Attributes.Name("Minecraft-Dist");

    private static final List<String> CLIENT_PREFIXES = List.of(
            "net.neoforged.neoforge.client.",
            "org.lwjgl.",
            "net.irisshaders.",
            "net.caffeinemc."
    );

    private static final Set<String> MINECRAFT_CLIENT_CLASSES = ClientClassesTest.readMinecraftClientClasses();
    private static final List<Path> ROOTS = ClientClassesTest.roots(Nexus.class, ServerTest.class, ClientClassesTest.class);
    private static final JavaClasses CLASSES = new ClassFileImporter().importPaths(ClientClassesTest.ROOTS);

    @Test
    public void readsTheClientClassesOfMinecraft() {
        Assertions.assertThat(ClientClassesTest.MINECRAFT_CLIENT_CLASSES)
                .contains("net.minecraft.client.Minecraft", "net.minecraft.client.player.LocalPlayer")
                .doesNotContain("net.minecraft.world.entity.player.Player");
    }

    @Test
    public void commonFieldsHoldNoClientType() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass type : ClientClassesTest.commonClasses()) {
            for (JavaField field : type.getFields()) {
                if (ClientClassesTest.isClient(field.getRawType())) {
                    offenders.add(field.getFullName() + " holds " + field.getRawType().getName());
                }
            }
        }

        Assertions.assertThat(offenders).as("Common fields of a client type").isEmpty();
    }

    @Test
    public void commonMethodsReturnNoClientType() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass type : ClientClassesTest.commonClasses()) {
            for (JavaMethod method : type.getMethods()) {
                if (ClientClassesTest.isClient(method.getRawReturnType())) {
                    offenders.add(method.getFullName() + " returns " + method.getRawReturnType().getName());
                }
            }
        }

        Assertions.assertThat(offenders).as("Common methods returning a client type").isEmpty();
    }

    @Test
    public void commonEventSubscribersNameNoClientTypeInAMethod() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass type : ClientClassesTest.commonClasses()) {
            if (!type.isAnnotatedWith(EventBusSubscriber.class)) {
                continue;
            }

            for (JavaMethod method : type.getMethods()) {
                for (JavaClass parameter : method.getRawParameterTypes()) {
                    if (ClientClassesTest.isClient(parameter)) {
                        offenders.add(method.getFullName() + " takes " + parameter.getName());
                    }
                }
            }
        }

        Assertions.assertThat(offenders).as("Methods of common event subscribers naming a client type, which the bus resolves on registration").isEmpty();
    }

    @Test
    public void commonLambdasNameNoClientType() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (JavaClass type : ClientClassesTest.commonClasses()) {
            ClassModel model = ClassFile.of().parse(ClientClassesTest.bytesOf(type));
            for (MethodModel method : model.methods()) {
                if (method.methodName().stringValue().startsWith(ClientClassesTest.LAMBDA) && method.flags().has(AccessFlag.SYNTHETIC)) {
                    offenders.addAll(ClientClassesTest.clientNamesIn(type, method));
                }
            }
        }

        Assertions.assertThat(offenders).as("Lambdas of common classes naming a client type, which the server resolves when it links the class").isEmpty();
    }

    private static List<String> clientNamesIn(JavaClass type, MethodModel method) {
        String lambda = type.getName() + "#" + method.methodName().stringValue();
        List<String> offenders = new ArrayList<>();
        for (ClassDesc descriptor : ClientClassesTest.descriptorTypes(method)) {
            if (ClientClassesTest.isClientName(ClientClassesTest.nameOf(descriptor))) {
                offenders.add(lambda + " names " + ClientClassesTest.nameOf(descriptor) + " in its descriptor");
            }
        }

        Optional<CodeModel> code = method.code();
        if (code.isEmpty()) {
            return offenders;
        }

        for (CodeElement element : code.get()) {
            String owner = switch (element) {
                case InvokeInstruction invoke when invoke.opcode() == Opcode.INVOKESPECIAL && invoke.name().equalsString(ClientClassesTest.CONSTRUCTOR) -> "";
                case InvokeInstruction invoke -> ClientClassesTest.nameOf(invoke.owner().asSymbol());
                case FieldInstruction field -> ClientClassesTest.nameOf(field.owner().asSymbol());
                default -> "";
            };
            if (ClientClassesTest.isClientName(owner)) {
                offenders.add(lambda + " reaches into " + owner);
            }
        }

        return offenders;
    }

    private static List<ClassDesc> descriptorTypes(MethodModel method) {
        List<ClassDesc> types = new ArrayList<>(method.methodTypeSymbol().parameterList());
        types.add(method.methodTypeSymbol().returnType());
        return types;
    }

    private static String nameOf(ClassDesc descriptor) {
        ClassDesc element = descriptor;
        while (element.isArray()) {
            element = Objects.requireNonNull(element.componentType());
        }

        if (element.isPrimitive()) {
            return "";
        }

        String internal = element.descriptorString();
        return internal.substring(1, internal.length() - 1).replace('/', '.');
    }

    private static List<JavaClass> commonClasses() {
        return ClientClassesTest.CLASSES.stream()
                .filter(type -> type.getName().startsWith(ClientClassesTest.NEXUS))
                .filter(type -> !type.getName().startsWith(ClientClassesTest.UNIT_TESTS))
                .filter(type -> !ClientClassesTest.packageSegments(type).contains(ClientClassesTest.MIXIN))
                .filter(type -> !ClientClassesTest.isClient(type))
                .toList();
    }

    private static boolean isClient(JavaClass type) {
        JavaClass element = type.getBaseComponentType();
        if (!element.getName().startsWith(ClientClassesTest.NEXUS) || !ClientClassesTest.CLASSES.contain(element.getName())) {
            return ClientClassesTest.isClientName(element.getName());
        }

        if (ClientClassesTest.packageSegments(element).contains(ClientClassesTest.CLIENT)) {
            return true;
        }

        for (JavaClass current = element; current != null; current = current.getEnclosingClass().orElse(null)) {
            if (current.getSimpleName().endsWith("Client") || ClientClassesTest.subscribesToTheClientOnly(current)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isClientName(String name) {
        if (name.isEmpty()) {
            return false;
        }

        if (name.startsWith(ClientClassesTest.NEXUS)) {
            return ClientClassesTest.CLASSES.contain(name) && ClientClassesTest.isClient(ClientClassesTest.CLASSES.get(name));
        }

        return ClientClassesTest.MINECRAFT_CLIENT_CLASSES.contains(name) || ClientClassesTest.CLIENT_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static boolean subscribesToTheClientOnly(JavaClass type) {
        Optional<JavaAnnotation<JavaClass>> subscriber = type.tryGetAnnotationOfType(EventBusSubscriber.class.getName());
        if (subscriber.isEmpty()) {
            return false;
        }

        Optional<Object> dists = subscriber.get().get("value");
        if (dists.isEmpty() || !(dists.get() instanceof JavaEnumConstant[] constants) || constants.length == 0) {
            return false;
        }

        return Arrays.stream(constants).allMatch(constant -> constant.name().equals("CLIENT"));
    }

    private static List<String> packageSegments(JavaClass type) {
        return List.of(type.getPackageName().split("\\."));
    }

    private static byte[] bytesOf(JavaClass type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        for (Path root : ClientClassesTest.ROOTS) {
            Path file = root.resolve(resource);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        }

        throw new IOException("No class file for " + type.getName());
    }

    private static List<Path> roots(Class<?>... members) {
        Set<Path> roots = new HashSet<>();
        for (Class<?> member : members) {
            String resource = member.getName().replace('.', '/') + ".class";
            URL url = Objects.requireNonNull(ClientClassesTest.class.getClassLoader().getResource(resource), resource);
            Path root;
            try {
                root = Path.of(url.toURI());
            } catch (URISyntaxException exception) {
                throw new IllegalStateException("Could not locate " + url, exception);
            }

            for (int segment = 0; segment < resource.split("/").length; segment++) {
                root = root.getParent();
            }

            roots.add(root);
        }

        return List.copyOf(roots);
    }

    private static Set<String> readMinecraftClientClasses() {
        Enumeration<URL> manifests;
        try {
            manifests = ClientClassesTest.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        for (URL url : Collections.list(manifests)) {
            Manifest manifest;
            try (InputStream stream = url.openStream()) {
                manifest = new Manifest(stream);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }

            if (manifest.getMainAttributes().containsKey(ClientClassesTest.DISTS)) {
                return ClientClassesTest.clientClassesOf(manifest);
            }
        }

        throw new IllegalStateException("No manifest on the classpath carries " + ClientClassesTest.DISTS);
    }

    private static Set<String> clientClassesOf(Manifest manifest) {
        Set<String> classes = new HashSet<>();
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            if (ClientClassesTest.CLIENT.equals(entry.getValue().getValue(ClientClassesTest.DIST)) && entry.getKey().endsWith(".class")) {
                classes.add(entry.getKey().substring(0, entry.getKey().length() - ".class".length()).replace('/', '.'));
            }
        }

        return Set.copyOf(classes);
    }
}
