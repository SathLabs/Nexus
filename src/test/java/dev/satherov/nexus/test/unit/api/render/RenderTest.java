package dev.satherov.nexus.test.unit.api.render;

import dev.satherov.nexus.api.render.Render;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

///
/// Checks that the common class of the render feature names no client class outside of a generic argument.
///
public class RenderTest {
    
    private static final List<String> CLIENT_PACKAGES = List.of("com.mojang.blaze3d.", "net.minecraft.client.");
    
    @Test
    public void methodsNameNoClientClass() {
        List<String> violations = new ArrayList<>();
        for (Method method : Render.class.getDeclaredMethods()) {
            Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()))
                    .filter(RenderTest::isClient)
                    .forEach(type -> violations.add(method.getName() + " names " + type.getName()));
        }
        
        Assertions.assertThat(violations).as("the methods of Render that name a client class").isEmpty();
    }
    
    @Test
    public void fieldsNameNoClientClass() {
        List<String> violations = new ArrayList<>();
        for (Field field : Render.class.getDeclaredFields()) {
            if (RenderTest.isClient(field.getType())) {
                violations.add(field.getName() + " is a " + field.getType().getName());
            }
        }
        
        Assertions.assertThat(violations).as("the fields of Render that are a client class").isEmpty();
    }
    
    private static boolean isClient(Class<?> type) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        
        String name = component.getName();
        return RenderTest.CLIENT_PACKAGES.stream().anyMatch(name::startsWith);
    }
}
