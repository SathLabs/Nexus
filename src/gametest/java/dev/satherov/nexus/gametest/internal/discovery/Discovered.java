package dev.satherov.nexus.gametest.internal.discovery;

import lombok.SneakyThrows;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/// A method carrying a test annotation, checked against that annotation's constraints.
@ApiStatus.Internal
public sealed interface Discovered {

    /// Every method carrying the annotation across all loaded mods, in id order; an id declared twice is one [Invalid].
    static List<Discovered> all(Class<? extends Annotation> annotation) {
        Map<Identifier, Discovered> byId = new TreeMap<>();
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            List<IModInfo> mods = scan.getIModInfoData().stream().flatMap(file -> file.getMods().stream()).toList();
            if (mods.isEmpty()) {
                continue;
            }

            String modId = mods.getFirst().getModId();
            scan.getAnnotatedBy(annotation, ElementType.METHOD)
                    .map(found -> Discovered.of(modId, Discovered.method(found), annotation))
                    .forEach(found -> byId.merge(found.id(), found, Discovered::twice));
        }

        return List.copyOf(byId.values());
    }

    private static Discovered of(String modId, Method method, Class<? extends Annotation> annotation) {
        String owner = method.getDeclaringClass().getName();
        Identifier id = Identifier.fromNamespaceAndPath(modId, Discovered.snake(owner.substring(owner.lastIndexOf('.') + 1)) + "/" + Discovered.snake(method.getName()));
        Annotation test = Objects.requireNonNull(method.getAnnotation(annotation));
        String reason = Discovered.reason(method, test);
        return reason == null ? new Valid(id, method, test, method.getAnnotation(Measured.class)) : new Invalid(id, reason, Discovered.required(test));
    }

    // FML records a method as its name followed by its descriptor.
    @SneakyThrows(ClassNotFoundException.class)
    private static Method method(ModFileScanData.AnnotationData found) {
        int descriptor = found.memberName().indexOf('(');
        Class<?> owner = Class.forName(found.clazz().getClassName(), false, Discovered.class.getClassLoader());
        for (Method candidate : owner.getDeclaredMethods()) {
            if (candidate.getName().equals(found.memberName().substring(0, descriptor)) && Type.getMethodDescriptor(candidate).equals(found.memberName().substring(descriptor))) {
                return candidate;
            }
        }

        throw new IllegalStateException("'" + found.memberName() + "' is not declared by " + owner.getName());
    }

    private static String snake(String name) {
        return name.replace('$', '.').replaceAll("(?<=[^.])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
    }

    private static @Nullable String reason(Method method, Annotation test) {
        if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return "class is not public";
        }

        if (!Modifier.isPublic(method.getModifiers())) {
            return "method is not public";
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            return "method is not static";
        }

        if (method.getReturnType() != void.class) {
            return "method does not return void";
        }

        Class<?> parameter = switch (test) {
            case ServerTest _ -> GameTestHelper.class;
            case ClientTest _ -> Client.class;
            default -> throw new IllegalArgumentException(test.annotationType().getName() + " is not a test annotation");
        };
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != parameter) {
            return "parameter is not a single " + parameter.getSimpleName();
        }

        Measured measured = method.getAnnotation(Measured.class);
        if (measured != null && measured.value() <= 0) {
            return "Measured#value is not positive";
        }

        return switch (test) {
            case ServerTest server when server.maxTicks() <= 0 -> "ServerTest#maxTicks is not positive";
            case ServerTest server when server.setupTicks() < 0 -> "ServerTest#setupTicks is negative";
            case ClientTest client when client.maxFrames() <= 0 -> "ClientTest#maxFrames is not positive";
            default -> null;
        };
    }

    private static boolean required(Annotation test) {
        return switch (test) {
            case ServerTest server -> server.required();
            case ClientTest client -> client.required();
            default -> throw new IllegalArgumentException(test.annotationType().getName() + " is not a test annotation");
        };
    }

    private static Discovered twice(Discovered first, Discovered second) {
        return new Invalid(first.id(), "declared twice", first.required() || second.required());
    }

    Identifier id();

    /// If a failure of the test fails the run.
    boolean required();

    /// A method that meets the constraints; `measured` is `null` when the method carries no [Measured].
    record Valid(Identifier id, Method method, Annotation annotation, @Nullable Measured measured) implements Discovered {

        @Override
        public boolean required() {
            return Discovered.required(this.annotation);
        }
    }

    /// A method that does not; the run reports it as a failed test with the reason.
    record Invalid(Identifier id, String reason, boolean required) implements Discovered { }
}
