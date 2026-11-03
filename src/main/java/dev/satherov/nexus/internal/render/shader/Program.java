package dev.satherov.nexus.internal.render.shader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;

import dev.satherov.nexus.internal.render.shader.syntax.Item;
import dev.satherov.nexus.internal.render.shader.syntax.Parser;
import dev.satherov.zelqro.utils.StringUtils;

import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// A parsed program file.
///
/// A program is one GLSL file that builds on a [Base] with the directive `#nexus base <name>`, and may define two hooks:
/// - `void nexus_vertex()`: Runs as the last statement of the base's vertex main function.
/// - `vec4 nexus_albedo(vec4 albedo)`: Wraps the color the base fetches, before the color modulator, lighting, overlay, and fog.
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Program {
    
    ///
    /// The anchor in a template that the declarations of the stage replace.
    ///
    private static final String DECLARATIONS = "#nexus declarations";
    
    ///
    /// The prefix of every hook function.
    ///
    private static final String HOOK_PREFIX = "nexus_";
    
    ///
    /// The vertex format element of every input name that a program declared so far.
    ///
    /// Should only ever be accessed from [#element(Identifier, Input)].
    ///
    private static final Map<String, VertexFormatElement> ELEMENTS_BY_NAME = new HashMap<>();
    
    ///
    /// The identifier of the program file.
    ///
    private final Identifier id;
    
    ///
    /// The base of the program.
    ///
    @Getter
    private final Base base;
    
    ///
    /// The inputs that the program adds to the vertex format of its base.
    ///
    private final List<Input> inputs;
    
    ///
    /// The uniform blocks that the program file declares.
    ///
    private final @Unmodifiable Set<String> blocks;
    
    ///
    /// The samplers that the program file declares.
    ///
    private final @Unmodifiable Set<String> samplers;
    
    ///
    /// The hooks that the program file defines.
    ///
    private final Set<Hook> hooks;
    
    ///
    /// The `#define` lines of the program file outside of any conditional, each ending in a line break.
    ///
    private final String defines;
    
    ///
    /// The text that replaces the declarations anchor of each stage.
    ///
    private final Map<ShaderType, String> declarationsByStage;
    
    ///
    /// Parses the given text of a program file.
    ///
    /// Every file-scope item of the text is copied into the declarations of one or both stages:
    /// - The base directive is left out.
    /// - A `#define` outside of any conditional is copied into the defines of both stages.
    /// - An input and the vertex hook are copied into the vertex stage only.
    /// - The albedo hook is copied into the fragment stage only.
    /// - An output is copied into the vertex stage as it is and into the fragment stage as an input.
    /// - Everything else is copied into both stages.
    ///
    /// @param id   The identifier of the program file, used in the failure messages.
    /// @param text The text of the program file.
    ///
    /// @return The parsed program.
    ///
    /// @throws IllegalArgumentException - If [Parser#parse(Identifier, String)] could not read the text.
    /// - If the text has no base directive, more than one, or one of an unknown base.
    /// - If the text has a `#nexus` directive that is not a base directive.
    /// - If the text has a `#version` directive.
    /// - If a function starts with `nexus_` and is not a hook.
    /// - If an input declares more than one variable or has a type other than `float`, `int`, and their vectors.
    /// - If an input has the name of an input of the base.
    /// - If a conditional holds a `#nexus` or `#version` directive, a function starting with `nexus_`, an input, an output, a uniform block, or a sampler.
    ///
    public static Program parse(Identifier id, String text) {
        Base base = null;
        List<Item.Input> declared = new ArrayList<>();
        Set<String> blocks = new HashSet<>();
        Set<String> samplers = new HashSet<>();
        Set<Hook> hooks = EnumSet.noneOf(Hook.class);
        StringBuilder defines = new StringBuilder();
        List<String> vertex = new ArrayList<>();
        List<String> fragment = new ArrayList<>();
        for (Item item : Parser.parse(id, text)) {
            switch (item) {
                case Item.Directive(String name, String directive, int line) when name.equals("nexus") -> base = Program.readBase(id, base, directive, line);
                case Item.Directive(String name, _, int line) when name.equals("version") -> throw new IllegalArgumentException(StringUtils.format("The program '%s' has a version directive on line '%d'", id, line));
                case Item.Directive(String name, String directive, _) when name.equals("define") -> defines.append(directive).append('\n');
                case Item.Conditional(List<Item> items, String conditional, _) -> {
                    Program.checkConditional(id, items);
                    vertex.add(conditional);
                    fragment.add(conditional);
                }
                case Item.Function(String name, String function, int line) when name.startsWith(Program.HOOK_PREFIX) -> {
                    Hook hook = Hook.byFunction(id, name, line);
                    hooks.add(hook);
                    (hook.stage == ShaderType.VERTEX ? vertex : fragment).add(function);
                }
                case Item.Input input -> {
                    declared.add(input);
                    vertex.add(input.text());
                }
                case Item.Output(String output, String fragmentText, _) -> {
                    vertex.add(output);
                    fragment.add(fragmentText);
                }
                case Item.Block(String name, String block, _) -> {
                    blocks.add(name);
                    vertex.add(block);
                    fragment.add(block);
                }
                case Item.Sampler(String name, String sampler, _) -> {
                    samplers.add(name);
                    vertex.add(sampler);
                    fragment.add(sampler);
                }
                case Item.Directive _, Item.Function _, Item.Declaration _ -> {
                    vertex.add(item.text());
                    fragment.add(item.text());
                }
            }
        }
        
        if (base == null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has no base directive", id));
        }
        
        List<Input> inputs = new ArrayList<>();
        for (Item.Input input : declared) {
            if (base.getFormat().getElementAttributeNames().contains(input.name())) {
                throw new IllegalArgumentException(StringUtils.format("The input '%s' on line '%d' of the program '%s' is already an input of the base '%s'", input.name(), input.line(), id, base));
            }
            
            inputs.add(Input.of(id, input));
        }
        
        return new Program(
                id,
                base,
                List.copyOf(inputs),
                Set.copyOf(blocks),
                Set.copyOf(samplers),
                Set.copyOf(hooks),
                defines.toString(),
                Map.of(ShaderType.VERTEX, String.join("\n", vertex), ShaderType.FRAGMENT, String.join("\n", fragment))
        );
    }
    
    ///
    /// Reads the base of the given `#nexus` directive.
    ///
    /// @param program   The identifier of the program file, used in the failure messages.
    /// @param previous  The base of an earlier directive, or `null` if there was none.
    /// @param directive The text of the directive.
    /// @param line      The line of the directive, used in the failure messages.
    ///
    /// @return The base.
    ///
    /// @throws IllegalArgumentException If the directive is not a base directive, if the program already has a base, or if the base is unknown.
    ///
    private static Base readBase(Identifier program, @Nullable Base previous, String directive, int line) {
        String[] words = directive.substring(1).strip().split("\\s+");
        if (words.length != 3 || !words[0].equals("nexus") || !words[1].equals("base")) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has the unknown directive '%s' on line '%d'", program, directive, line));
        }
        
        if (previous != null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has another base directive on line '%d'", program, line));
        }
        
        Base base = Base.byName(words[2]);
        if (base == null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' builds on the unknown base '%s' on line '%d'", program, words[2], line));
        }
        
        return base;
    }
    
    ///
    /// Checks the given items of a conditional, nested conditionals included, for items that are not allowed inside a conditional.
    ///
    /// @param program The identifier of the program file, used in the failure messages.
    /// @param items   The items of the conditional.
    ///
    /// @throws IllegalArgumentException If an item is a `#nexus` or `#version` directive, a function starting with `nexus_`, an input, an output, a uniform block, or a sampler.
    ///
    private static void checkConditional(Identifier program, List<Item> items) {
        for (Item item : items) {
            String held = switch (item) {
                case Item.Conditional(List<Item> nested, _, _) -> {
                    Program.checkConditional(program, nested);
                    yield null;
                }
                case Item.Directive(String name, _, _) when name.equals("nexus") || name.equals("version") -> "the directive '#" + name + "'";
                case Item.Function(String name, _, _) when name.startsWith(Program.HOOK_PREFIX) -> "the function '" + name + "'";
                case Item.Input(_, String name, _, _) -> "the input '" + name + "'";
                case Item.Output _ -> "an output";
                case Item.Block(String name, _, _) -> "the uniform block '" + name + "'";
                case Item.Sampler(String name, _, _) -> "the sampler '" + name + "'";
                case Item.Directive _, Item.Function _, Item.Declaration _ -> null;
            };
            
            if (held != null) {
                throw new IllegalArgumentException(StringUtils.format("The program '%s' has %s inside a conditional on line '%d'", program, held, item.line()));
            }
        }
    }
    
    ///
    /// Gets the vertex format element of the given input, creating it if no program declared the name of the input yet.
    ///
    /// @param program The identifier of the program file, used in the failure messages.
    /// @param input   The input.
    ///
    /// @return The vertex format element.
    ///
    /// @throws IllegalArgumentException If the element of the name has another type or count than the input, or if every vertex format element identifier is taken.
    ///
    @Synchronized
    private static VertexFormatElement element(Identifier program, Input input) {
        VertexFormatElement element;
        try {
            element = Program.ELEMENTS_BY_NAME.computeIfAbsent(input.name(), _ -> VertexFormatElement.register(VertexFormatElement.findNextId(), 0, input.type(), false, input.count()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException(StringUtils.format("Could not create an element for the input '%s' of the program '%s', every element identifier is taken", input.name(), program), exception);
        }
        
        if (element.type() != input.type() || element.count() != input.count()) {
            throw new IllegalArgumentException(StringUtils.format(
                    "The input '%s' of the program '%s' is a '%dx%s', but another program declared it as a '%dx%s'",
                    input.name(),
                    program,
                    input.count(),
                    input.type(),
                    element.count(),
                    element.type()
            ));
        }
        
        return element;
    }
    
    ///
    /// Creates the vertex format of the program, with the elements of the base and then the inputs in file order,
    /// or returns the format of the base if there are no inputs.
    ///
    /// Every program that declares an input of the same name, type, and count shares its element.
    ///
    /// May be called from any thread.
    ///
    /// @return The vertex format.
    ///
    /// @throws IllegalArgumentException If another program declared an input of the same name with another type or count, or if every vertex format element identifier is taken.
    ///
    public VertexFormat format() {
        VertexFormat baseFormat = this.base.getFormat();
        if (this.inputs.isEmpty()) {
            return baseFormat;
        }
        
        VertexFormat.Builder builder = VertexFormat.builder();
        baseFormat.getElementMapping().forEach(builder::add);
        // The entity format ends in a byte of padding, and the inputs have to start after it.
        builder.padding(baseFormat.getVertexSize() - baseFormat.getElements().stream().mapToInt(VertexFormatElement::byteSize).sum());
        for (Input input : this.inputs) {
            builder.add(input.name(), Program.element(this.id, input));
        }
        
        return builder.build();
    }
    
    ///
    /// The uniform blocks that the program file declares.
    ///
    /// @return The names of the uniform blocks.
    ///
    public @Unmodifiable Set<String> getBlocks() {
        return this.blocks;
    }
    
    ///
    /// The samplers that the program file declares.
    ///
    /// @return The names of the samplers.
    ///
    public @Unmodifiable Set<String> getSamplers() {
        return this.samplers;
    }
    
    ///
    /// Generates the text of the given stage from the given template, leaving every `#moj_import` directive as it is.
    ///
    /// The anchors of the template are replaced:
    /// - `#nexus declarations`: The declarations of the stage in file order.
    /// - `#nexus vertex`: The call to the vertex hook, or nothing if the program doesn't define it.
    /// - `#nexus albedo`: The call to the albedo hook, or nothing if the program doesn't define it.
    ///
    /// The define `NEXUS_VERTEX` or `NEXUS_FRAGMENT` and then the defines of the program file are inserted directly after the `#version` line.
    ///
    /// @param stage    The shader stage.
    /// @param template The text of the template of the base for the given stage.
    ///
    /// @return The text of the stage.
    ///
    /// @throws IllegalArgumentException If the stage is not the vertex or the fragment stage.
    ///
    public String generate(ShaderType stage, String template) {
        String declarations = this.declarationsByStage.get(stage);
        if (declarations == null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has no '%s' stage", this.id, stage.getName()));
        }
        
        int versionEnd = template.indexOf('\n') + 1;
        String header = "#define NEXUS_" + StringUtils.upper(stage.getName()) + "\n" + this.defines;
        String text = template.substring(0, versionEnd) + header + template.substring(versionEnd).replace(Program.DECLARATIONS, declarations);
        for (Hook hook : Hook.values()) {
            text = text.replace(hook.anchor, this.hooks.contains(hook) ? hook.call : "");
        }
        
        return text;
    }
    
    ///
    /// An input that a program adds to the vertex format of its base.
    ///
    /// @param name  The name of the input.
    /// @param type  The type of each component of the input.
    /// @param count The number of components of the input.
    ///
    private record Input(String name, VertexFormatElement.Type type, @Range(from = 1, to = 4) int count) {
        
        ///
        /// Creates a new input from the given input declaration.
        ///
        /// @param program     The identifier of the program file, used in the failure message.
        /// @param declaration The input declaration.
        ///
        /// @return The input.
        ///
        /// @throws IllegalArgumentException If the type of the declaration is not `float`, `int`, or one of their vectors.
        ///
        private static Input of(Identifier program, Item.Input declaration) {
            String name = declaration.name();
            return switch (declaration.type()) {
                case "float" -> new Input(name, VertexFormatElement.Type.FLOAT, 1);
                case "vec2" -> new Input(name, VertexFormatElement.Type.FLOAT, 2);
                case "vec3" -> new Input(name, VertexFormatElement.Type.FLOAT, 3);
                case "vec4" -> new Input(name, VertexFormatElement.Type.FLOAT, 4);
                case "int" -> new Input(name, VertexFormatElement.Type.INT, 1);
                case "ivec2" -> new Input(name, VertexFormatElement.Type.INT, 2);
                case "ivec3" -> new Input(name, VertexFormatElement.Type.INT, 3);
                case "ivec4" -> new Input(name, VertexFormatElement.Type.INT, 4);
                default -> throw new IllegalArgumentException(StringUtils.format("The input '%s' on line '%d' of the program '%s' has the unsupported type '%s'", name, declaration.line(), program, declaration.type()));
            };
        }
    }
    
    ///
    /// A function of a program that the main function of the base calls in one stage.
    ///
    @RequiredArgsConstructor
    private enum Hook {
        
        ///
        /// The vertex hook.
        ///
        VERTEX("nexus_vertex", ShaderType.VERTEX, "#nexus vertex", "nexus_vertex();"),
        
        ///
        /// The albedo hook.
        ///
        ALBEDO("nexus_albedo", ShaderType.FRAGMENT, "#nexus albedo", "color = nexus_albedo(color);");
        
        ///
        /// The name of the hook function.
        ///
        private final String function;
        
        ///
        /// The stage whose declarations hold the hook function.
        ///
        private final ShaderType stage;
        
        ///
        /// The anchor in a template that the call replaces.
        ///
        private final String anchor;
        
        ///
        /// The statement that calls the hook function.
        ///
        private final String call;
        
        ///
        /// Gets the hook of the given function.
        ///
        /// @param program  The identifier of the program file, used in the failure message.
        /// @param function The name of the function.
        /// @param line     The line of the function, used in the failure message.
        ///
        /// @return The hook.
        ///
        /// @throws IllegalArgumentException If no hook has the name of the given function.
        ///
        private static Hook byFunction(Identifier program, String function, int line) {
            return Arrays.stream(Hook.values())
                    .filter(hook -> hook.function.equals(function))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(StringUtils.format("The function '%s' on line '%d' of the program '%s' is not a hook", function, line, program)));
        }
    }
}
