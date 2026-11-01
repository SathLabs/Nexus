package dev.satherov.nexus.internal.render.shader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

///
/// A parsed program file.
///
/// A program is one GLSL file that builds on a [Base] with the directive `#nexus base <name>`, and may define two hooks:
/// - `void nexus_vertex()`: Runs as the last statement of the base's vertex main function.
/// - `vec4 nexus_albedo(vec4 albedo)`: Wraps the color the base fetches, before the color modulator, lighting, overlay, and fog.
///
/// If the program leaves out a hook, the base will keep its vanilla behavior there.
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
    /// A line comment or a block comment.
    ///
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    
    ///
    /// A run of whitespace.
    ///
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    
    ///
    /// A preprocessor line, with the name of its directive.
    ///
    private static final Pattern PREPROCESSOR = Pattern.compile("# ?(\\w+).*");
    
    ///
    /// The base directive, with the name of the base.
    ///
    private static final Pattern BASE = Pattern.compile("# ?nexus base (\\w+) ?");
    
    ///
    /// A function definition or prototype, with the name of the function.
    ///
    private static final Pattern FUNCTION = Pattern.compile("(?:\\w+(?: ?\\[\\d*])? )+(\\w+) ?\\(.*");
    
    ///
    /// An input declaration, with its type and its name if it declares exactly one variable.
    ///
    private static final Pattern INPUT = Pattern.compile("(?:layout ?\\([^)]*\\) ?)?in (?:(\\w+) (\\w+) ?;)?.*");
    
    ///
    /// An output declaration, with everything before its `out` qualifier and everything after it.
    ///
    private static final Pattern OUTPUT = Pattern.compile("((?:layout ?\\([^)]*\\) ?)?(?:\\w+ )*?)out (.*)");
    
    ///
    /// A uniform block declaration, with the name of the block.
    ///
    private static final Pattern BLOCK = Pattern.compile("(?:layout ?\\([^)]*\\) ?)?uniform (\\w+) ?\\{.*");
    
    ///
    /// A sampler declaration, with the name of the sampler.
    ///
    private static final Pattern SAMPLER = Pattern.compile("uniform [iu]?sampler\\w* (\\w+) ?;");
    
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
    /// @throws IllegalArgumentException - If the text has no base directive, more than one, or one of an unknown base.
    /// - If the text has a `#nexus` directive that is not a base directive.
    /// - If the text has a `#version` directive.
    /// - If a function starts with `nexus_` and is not a hook.
    /// - If an input declares more than one variable or has a type other than `float`, `int`, and their vectors.
    /// - If an input has the name of an input of the base.
    ///
    public static Program parse(Identifier id, String text) {
        Base base = null;
        List<Input> inputs = new ArrayList<>();
        Set<String> blocks = new HashSet<>();
        Set<String> samplers = new HashSet<>();
        Set<Hook> hooks = EnumSet.noneOf(Hook.class);
        StringBuilder defines = new StringBuilder();
        List<String> vertex = new ArrayList<>();
        List<String> fragment = new ArrayList<>();
        int conditionals = 0;
        for (String item : Program.split(text)) {
            String flat = Program.WHITESPACE.matcher(item).replaceAll(" ");
            Matcher preprocessor = Program.PREPROCESSOR.matcher(flat);
            if (preprocessor.matches()) {
                String directive = preprocessor.group(1);
                if (directive.equals("nexus")) {
                    base = Program.readBase(id, base, flat);
                    continue;
                }
                
                if (directive.equals("version")) {
                    throw new IllegalArgumentException(StringUtils.format("The program '%s' has a version directive", id));
                }
                
                if (directive.equals("define") && conditionals == 0) {
                    defines.append(item).append('\n');
                    continue;
                }
                
                conditionals += switch (directive) {
                    case "if", "ifdef", "ifndef" -> 1;
                    case "endif" -> -1;
                    default -> 0;
                };

                vertex.add(item);
                fragment.add(item);
                continue;
            }
            
            Matcher function = Program.FUNCTION.matcher(flat);
            if (function.matches() && function.group(1).startsWith(Program.HOOK_PREFIX)) {
                Hook hook = Hook.byFunction(id, function.group(1));
                hooks.add(hook);
                (hook.stage == ShaderType.VERTEX ? vertex : fragment).add(item);
                continue;
            }
            
            Matcher input = Program.INPUT.matcher(flat);
            if (input.matches()) {
                if (input.group(1) == null) {
                    throw new IllegalArgumentException(StringUtils.format("Could not read the input '%s' of the program '%s'", flat, id));
                }
                
                inputs.add(Input.of(id, input.group(1), input.group(2)));
                vertex.add(item);
                continue;
            }
            
            Matcher output = Program.OUTPUT.matcher(flat);
            if (output.matches()) {
                vertex.add(item);
                fragment.add(output.group(1) + "in " + output.group(2));
                continue;
            }
            
            Matcher block = Program.BLOCK.matcher(flat);
            if (block.matches()) {
                blocks.add(block.group(1));
            }
            
            Matcher sampler = Program.SAMPLER.matcher(flat);
            if (sampler.matches()) {
                samplers.add(sampler.group(1));
            }
            
            vertex.add(item);
            fragment.add(item);
        }
        
        if (base == null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has no base directive", id));
        }
        
        for (Input input : inputs) {
            if (base.getFormat().getElementAttributeNames().contains(input.name())) {
                throw new IllegalArgumentException(StringUtils.format("The input '%s' of the program '%s' is already an input of the base '%s'", input.name(), id, base));
            }
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
    /// @param directive The directive, with every run of whitespace collapsed into one space.
    ///
    /// @return The base.
    ///
    /// @throws IllegalArgumentException If the directive is not a base directive, if the program already has a base,
    /// or if the base is unknown.
    ///
    private static Base readBase(Identifier program, @Nullable Base previous, String directive) {
        Matcher matcher = Program.BASE.matcher(directive);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has the unknown directive '%s'", program, directive));
        }
        
        if (previous != null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has more than one base directive", program));
        }
        
        Base base = Base.byName(matcher.group(1));
        if (base == null) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' builds on the unknown base '%s'", program, matcher.group(1)));
        }
        
        return base;
    }
    
    ///
    /// Splits the given text into its file-scope items, with every comment removed.
    ///
    /// An item is a preprocessor line, a declaration up to its semicolon, or a function definition up to its closing brace.
    ///
    /// @param text The text of a program file.
    ///
    /// @return The items, stripped of their surrounding whitespace.
    ///
    private static List<String> split(String text) {
        String code = Program.COMMENT.matcher(text).replaceAll(" ");
        List<String> items = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '#' && depth == 0 && code.substring(start, i).isBlank()) {
                int end = code.indexOf('\n', i);
                i = end == -1 ? code.length() : end;
                items.add(code.substring(start, i).strip());
                start = i;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                // A function ends at its closing brace, and a uniform block or a struct only at the semicolon after it.
                if (depth == 0 && code.substring(start, code.indexOf('{', start)).strip().endsWith(")")) {
                    items.add(code.substring(start, i + 1).strip());
                    start = i + 1;
                }
            } else if (c == ';' && depth == 0) {
                items.add(code.substring(start, i + 1).strip());
                start = i + 1;
            }
        }
        
        if (!code.substring(start).isBlank()) {
            items.add(code.substring(start).strip());
        }
        
        return items;
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
    /// Creates the vertex format of the program, with the elements of the base and then the inputs in file order.
    ///
    /// Every program that declares an input of the same name, type, and count shares its element.
    ///
    /// If the program declares no inputs, this will return the format of the base.
    ///
    /// May be called from any thread.
    ///
    /// @return The vertex format.
    ///
    /// @throws IllegalArgumentException If another program declared an input of the same name with another type or count,
    /// or if every vertex format element identifier is taken.
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
    /// Generates the text of the given stage from the given template.
    ///
    /// The anchors of the template are replaced:
    /// - `#nexus declarations`: The declarations of the stage in file order.
    /// - `#nexus vertex`: The call to the vertex hook, or nothing if the program doesn't define it.
    /// - `#nexus albedo`: The call to the albedo hook, or nothing if the program doesn't define it.
    ///
    /// The define `NEXUS_VERTEX` or `NEXUS_FRAGMENT` and then the defines of the program file are inserted directly after the `#version` line.
    /// They apply to every conditional of the template the same way a pipeline define does.
    ///
    /// Every `#moj_import` directive will still be a directive.
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
        /// Creates a new input of the given GLSL type.
        ///
        /// @param program The identifier of the program file, used in the failure message.
        /// @param type    The GLSL type of the input.
        /// @param name    The name of the input.
        ///
        /// @return The input.
        ///
        /// @throws IllegalArgumentException If the type is not `float`, `int`, or one of their vectors.
        ///
        private static Input of(Identifier program, String type, String name) {
            return switch (type) {
                case "float" -> new Input(name, VertexFormatElement.Type.FLOAT, 1);
                case "vec2" -> new Input(name, VertexFormatElement.Type.FLOAT, 2);
                case "vec3" -> new Input(name, VertexFormatElement.Type.FLOAT, 3);
                case "vec4" -> new Input(name, VertexFormatElement.Type.FLOAT, 4);
                case "int" -> new Input(name, VertexFormatElement.Type.INT, 1);
                case "ivec2" -> new Input(name, VertexFormatElement.Type.INT, 2);
                case "ivec3" -> new Input(name, VertexFormatElement.Type.INT, 3);
                case "ivec4" -> new Input(name, VertexFormatElement.Type.INT, 4);
                default -> throw new IllegalArgumentException(StringUtils.format("The input '%s' of the program '%s' has the unsupported type '%s'", name, program, type));
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
        ///
        /// @return The hook.
        ///
        /// @throws IllegalArgumentException If no hook has the name of the given function.
        ///
        private static Hook byFunction(Identifier program, String function) {
            return Arrays.stream(Hook.values())
                    .filter(hook -> hook.function.equals(function))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(StringUtils.format("The function '%s' of the program '%s' is not a hook", function, program)));
        }
    }
}
