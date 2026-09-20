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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

///
/// A method carrying a test annotation, checked against that annotation's constraints.
///
@ApiStatus.Internal
public sealed interface Discovered {

    ///
    /// The ticks a measured window costs beside its samples.
    /// internal.server.TickWindow reads it from here so the packages do not reference each other.
    ///
    int WINDOW_OVERHEAD = 2;

    ///
    /// Every method carrying the annotation across all loaded mods, in id order.
    /// An id declared twice is one [Invalid].
    ///
    /// @param annotation The test annotation to look for.
    ///
    /// @return Every method carrying the annotation, in id order.
    ///
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

    ///
    /// The method as a [Valid], or as an [Invalid] carrying the constraint it breaks.
    ///
    /// @param modId      The id of the mod that declares the method.
    /// @param method     The annotated method.
    /// @param annotation The test annotation the method carries.
    ///
    /// @return The method as a [Valid], or as an [Invalid] carrying the constraint it breaks.
    ///
    private static Discovered of(String modId, Method method, Class<? extends Annotation> annotation) {
        String owner = method.getDeclaringClass().getName();
        Identifier id = Identifier.fromNamespaceAndPath(modId, Discovered.snake(owner.substring(owner.lastIndexOf('.') + 1)) + "/" + Discovered.snake(method.getName()));
        Annotation test = Objects.requireNonNull(method.getAnnotation(annotation));
        String reason = Discovered.reason(method, test);
        return reason == null ? new Valid(id, method, test, method.getAnnotation(Measured.class)) : new Invalid(id, reason, Discovered.required(test));
    }

    ///
    /// The method the scan found, looked up on the class that declares it.
    ///
    /// FML records a method as its name followed by its descriptor.
    ///
    /// @param found The method the scan recorded.
    ///
    /// @return The method, looked up on the class that declares it.
    ///
    /// @throws IllegalStateException  If the class does not declare the method the scan recorded.
    /// @throws ClassNotFoundException If the class the scan recorded can't be loaded.
    ///
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

    ///
    /// The name in snake case, with the `$` of a nested class turned into a `.`.
    ///
    /// @param name The name to convert.
    ///
    /// @return The name in snake case, with the `$` of a nested class turned into a `.`.
    ///
    private static String snake(String name) {
        return name.replace('$', '.').replaceAll("(?<=[^.])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
    }

    ///
    /// The first constraint of the annotation the method breaks, or `null` if it breaks none.
    ///
    /// @param method The annotated method.
    /// @param test   The test annotation the method carries.
    ///
    /// @return The first constraint of the annotation the method breaks, or `null` if it breaks none.
    ///
    /// @throws IllegalArgumentException If the annotation is not a test annotation.
    ///
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
            case ServerTest server when measured != null && server.maxTicks() < measured.value() + Discovered.WINDOW_OVERHEAD -> "ServerTest#maxTicks is too short for Measured#value";
            case ServerTest server when server.setupTicks() < 0 -> "ServerTest#setupTicks is negative";
            case ServerTest server when Identifier.tryParse(server.structure()) == null -> "ServerTest#structure is not an id";
            case ClientTest client when client.maxFrames() <= 0 -> "ClientTest#maxFrames is not positive";
            default -> null;
        };
    }

    ///
    /// The `required` element of the annotation, whichever test annotation it is.
    ///
    /// @param test The test annotation the element is read from.
    ///
    /// @return The `required` element of the annotation.
    ///
    /// @throws IllegalArgumentException If the annotation is not a test annotation.
    ///
    private static boolean required(Annotation test) {
        return switch (test) {
            case ServerTest server -> server.required();
            case ClientTest client -> client.required();
            default -> throw new IllegalArgumentException(test.annotationType().getName() + " is not a test annotation");
        };
    }

    ///
    /// One [Invalid] for an id two methods declare, required if either of them is.
    ///
    /// @param first  The test of the id.
    /// @param second The other test of the same id.
    ///
    /// @return One [Invalid] for the id, required if either of them is.
    ///
    private static Discovered twice(Discovered first, Discovered second) {
        return new Invalid(first.id(), "declared twice", first.required() || second.required());
    }

    ///
    /// The id of the test, `<modid>:<class>/<method>` in snake case.
    ///
    /// @return The id of the test, `<modid>:<class>/<method>` in snake case.
    ///
    Identifier id();

    ///
    /// If a failure of the test fails the run.
    ///
    /// @return `true` if a failure of the test fails the run.
    ///
    boolean required();

    ///
    /// A method that meets the constraints.
    ///
    /// @param id         The test's id.
    /// @param method     The test method.
    /// @param annotation The [ServerTest] or [ClientTest] the method carries.
    /// @param measured   The [Measured] the method carries, or `null` if it carries none.
    ///
    record Valid(Identifier id, Method method, Annotation annotation, @Nullable Measured measured) implements Discovered {

        ///
        /// Reads the `required` element of whichever test annotation the method carries.
        ///
        /// @return `true` if a failure of the test fails the run.
        ///
        @Override
        public boolean required() {
            return Discovered.required(this.annotation);
        }

        ///
        /// Calls the method with the parameter its annotation requires: a [GameTestHelper] for a [ServerTest], a [Client] for a [ClientTest].
        /// Discovery has already checked that the method takes that parameter.
        ///
        /// Rethrows what the test threw, untouched, so a run reports the failure and not the reflective call around it.
        ///
        /// @param parameter The [GameTestHelper] or [Client] the method takes.
        ///
        @SneakyThrows
        public void invoke(Object parameter) {
            try {
                this.method.invoke(null, parameter);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    ///
    /// A method that does not.
    /// The run records it as a failed test with the reason.
    ///
    /// @param id       The test's id.
    /// @param reason   The constraint the method breaks.
    /// @param required If the failure fails the run.
    ///
    record Invalid(Identifier id, String reason, boolean required) implements Discovered { }
}
