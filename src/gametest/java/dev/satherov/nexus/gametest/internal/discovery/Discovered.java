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
import org.jetbrains.annotations.Unmodifiable;
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
/// A discovered test case method, to be checked against the validation constraints.
///
@ApiStatus.Internal
public sealed interface Discovered {
    
    ///
    /// The number of ticks measuring a window takes before any of the actual testing is done.
    ///
    int WINDOW_OVERHEAD = 2;
    
    ///
    /// Every method across all loaded mods that hold the respective annotations, sorted by their identifier.
    ///
    /// An identifier that shows up twice will turn into an [Invalid] result.
    ///
    /// @param annotation The test annotation to search for.
    ///
    /// @return Every method with the given annotation, sorted by their identifier.
    ///
    static @Unmodifiable List<Discovered> all(Class<? extends Annotation> annotation) {
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
    /// Creates either a [Valid] or an [Invalid] result from the given method information.
    ///
    /// @param modId      The namespace id of the mod that owns the class in which the test method is declared.
    /// @param method     The method that is annotated with the test annotation.
    /// @param annotation The test annotation on the method.
    ///
    /// @return Either a [Valid] or an [Invalid] result.
    ///
    private static Discovered of(String modId, Method method, Class<? extends Annotation> annotation) {
        String owner = method.getDeclaringClass().getName();
        Identifier id = Identifier.fromNamespaceAndPath(modId, Discovered.snake(owner.substring(owner.lastIndexOf('.') + 1)) + "/" + Discovered.snake(method.getName()));
        Annotation test = Objects.requireNonNull(method.getAnnotation(annotation));
        String violation = Discovered.getFirstConstraintViolation(method, test);
        return violation == null ?
                new Valid(id, method, test, method.getAnnotation(Measured.class)) :
                new Invalid(id, violation, Discovered.required(test));
    }
    
    ///
    /// Gets a method from one annotation data entry that FML found.
    ///
    /// @param found The annotation data entry.
    ///
    /// @return The method that the scan entry refers to.
    ///
    /// @throws IllegalStateException  If the class that the annotation data says owns the method doesn't actually declare the method.
    /// @throws ClassNotFoundException If the class that the annotation data says owns the method could not be loaded.
    ///
    @SneakyThrows(ClassNotFoundException.class)
    private static Method method(ModFileScanData.AnnotationData found) {
        String memberName = found.memberName();
        int descriptor = memberName.indexOf('(');
        Class<?> owner = Class.forName(found.clazz().getClassName(), false, Discovered.class.getClassLoader());
        for (Method candidate : owner.getDeclaredMethods()) {
            if (
                    candidate.getName().equals(memberName.substring(0, descriptor)) &&  // Do the method names match?
                            Type.getMethodDescriptor(candidate).equals(memberName.substring(descriptor)) // Do the JVM method descriptors match?
            ) {
                return candidate;
            }
        }
        
        throw new IllegalStateException("'" + memberName + "' is not declared by " + owner.getName());
    }
    
    ///
    /// The given name converted to snake case with `$` replaced by `.`.
    ///
    /// @param name The name to convert to snake case.
    ///
    /// @return The converted name.
    ///
    private static String snake(String name) {
        return name.replace('$', '.')
                .replaceAll("(?<=[^.])(?=[A-Z])", "_")
                .toLowerCase(Locale.ROOT);
    }
    
    ///
    /// Gets the first constraint that the method or its annotation violates or `null` if everything is fine.
    ///
    /// @param method The annotated method to check.
    /// @param test   The test annotation on the method to check.
    ///
    /// @return The first constraint that the method or its annotation violates or `null` if everything is fine.
    ///
    /// @throws IllegalArgumentException If the annotation is not a test annotation.
    ///
    @SuppressWarnings("ConstantValue") // If a programmer isn't dumb, the max values cannot ever be negative/not positive since the IDE will yell at you, but we better check regardless.
    private static @Nullable String getFirstConstraintViolation(Method method, Annotation test) {
        Class<?> declaringClass = method.getDeclaringClass();
        String className = declaringClass.getName();
        if (!Modifier.isPublic(declaringClass.getModifiers())) {
            return "Class '" + className + "' is not public";
        }
        
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            return "Method '" + className + "#" + method.getName() + "' is not public";
        }
        
        if (!Modifier.isStatic(modifiers)) {
            return "Method '" + className + "#" + method.getName() + "' is not static";
        }
        
        if (method.getReturnType() != void.class) {
            return "Method '" + className + "#" + method.getName() + "' does not return void";
        }
        
        Class<?> parameter = switch (test) {
            case ServerTest _ -> GameTestHelper.class;
            case ClientTest _ -> Client.class;
            default -> throw new IllegalArgumentException(test.annotationType().getName() + " is not a test annotation");
        };
        
        if (method.getParameterCount() != 1) {
            return "Found more than one parameter, " + method.getParameterCount() + " total";
        }
        
        Class<?> firstParameter = method.getParameterTypes()[0];
        if (firstParameter != parameter) {
            return "Parameter is not a '" + parameter.getSimpleName() + "', found '" + firstParameter.getSimpleName() + "' instead";
        }
        
        Measured measured = method.getAnnotation(Measured.class);
        if (measured != null && measured.value() <= 0) {
            return "Measured#value is not positive";
        }
        
        return switch (test) {
            case ServerTest server when server.maxTicks() <= 0 -> "ServerTest#maxTicks is not positive";
            case ServerTest server when measured != null && server.maxTicks() < measured.value() + Discovered.WINDOW_OVERHEAD -> "ServerTest#maxTicks is too short for Measured#value";
            case ServerTest server when server.setupTicks() < 0 -> "ServerTest#setupTicks is negative";
            case ServerTest server when Identifier.tryParse(server.structure()) == null -> "ServerTest#structure is not an identifier";
            case ClientTest client when client.maxFrames() <= 0 -> "ClientTest#maxFrames is not positive";
            default -> null;
        };
    }
    
    ///
    /// If the test is marked as `required`.
    ///
    /// @param test The test annotation we check from.
    ///
    /// @return If the test is marked as `required`.
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
    /// Creates an [Invalid] result for an identifier that was declared twice.
    ///
    /// @param first  The test with the duplicate identifier.
    /// @param second The second test with the same duplicate identifier.
    ///
    /// @return One [Invalid] result with the identifier and the `required` status merged.
    ///
    private static Discovered twice(Discovered first, Discovered second) {
        return new Invalid(first.id(), "Declared twice", first.required() || second.required());
    }
    
    ///
    /// The identifier of the test, `<modid>:<class>/<method>` in snake case.
    ///
    /// @return The identifier of the test, `<modid>:<class>/<method>` in snake case.
    ///
    Identifier id();
    
    ///
    /// If, when this test fails, the entire run fails, or we simply skip it.
    ///
    /// @return `true` if a failure of the test fails the entire run.
    ///
    boolean required();
    
    ///
    /// A method that matches all constraints.
    ///
    /// @param id         The test's identifier.
    /// @param method     The test method.
    /// @param annotation The [ServerTest] or [ClientTest] annotation on the method.
    /// @param measured   The [Measured] annotation on the method, or `null` if it doesn't have it.
    ///
    record Valid(Identifier id, Method method, Annotation annotation, @Nullable Measured measured) implements Discovered {
        
        ///
        /// Checks the `required` flag from the annotation of this test's method.
        ///
        /// @return `true` if a failure of the test fails the entire run.
        ///
        @Override
        public boolean required() {
            return Discovered.required(this.annotation);
        }
        
        ///
        /// Invokes the test method with the respective parameter that the method requires:
        /// - [GameTestHelper] for a [ServerTest].
        /// - [Client] for a [ClientTest].
        ///
        /// Any failure of the test will be rethrown as its cause to ensure we see the actual failure and not just the reflective call around it.
        ///
        /// @param parameter The [GameTestHelper] or [Client] parameter that the test method takes.
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
    /// A test method that failed the constraint check.
    ///
    /// @param id        The test case's identifier.
    /// @param violation The first constraint that the test case failed.
    /// @param required  If this test causes the run to fail or is skipped.
    ///
    record Invalid(Identifier id, String violation, boolean required) implements Discovered { }
}
