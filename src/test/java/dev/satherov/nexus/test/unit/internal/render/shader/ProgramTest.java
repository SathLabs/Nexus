package dev.satherov.nexus.test.unit.internal.render.shader;

import dev.satherov.nexus.internal.render.shader.Base;
import dev.satherov.nexus.internal.render.shader.Program;

import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;

///
/// Checks how program files are parsed, laid out as vertex formats, and generated into the text of both stages.
///
public class ProgramTest {
    
    private static final Identifier ID = Identifier.fromNamespaceAndPath("nexus", "program_test");
    private static final List<ShaderType> STAGES = List.of(ShaderType.VERTEX, ShaderType.FRAGMENT);
    
    private static Program parse(Base base, String... lines) {
        return Program.parse(ProgramTest.ID, "#nexus base " + base.name().toLowerCase(Locale.ROOT) + "\n" + String.join("\n", lines));
    }
    
    private static String template(Base base, ShaderType stage) throws IOException {
        Identifier template = base.template(stage);
        String path = "/assets/" + template.getNamespace() + "/" + template.getPath();
        try (InputStream stream = ProgramTest.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(stream, path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    private static String generate(Program program, ShaderType stage) throws IOException {
        return program.generate(stage, ProgramTest.template(program.getBase(), stage));
    }
    
    private static String main(String text) {
        return text.substring(text.indexOf("void main()"));
    }
    
    private static List<String> lines(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
    
    private static String fetch(Base base) {
        return switch (base) {
            case POSITION_COLOR -> "vec4 color = vertexColor;";
            case POSITION_TEX_COLOR -> "vec4 color = texture(Sampler0, texCoord0) * vertexColor;";
            case ENTITY -> "vec4 color = texture(Sampler0, texCoord0);";
        };
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void readsTheBase(Base base) {
        Assertions.assertThat(ProgramTest.parse(base).getBase()).isEqualTo(base);
    }
    
    @Test
    public void throwsWithoutBaseDirective() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> Program.parse(ProgramTest.ID, "vec4 tint() {\n    return vec4(1.0);\n}"));
    }
    
    @Test
    public void throwsOnUnknownBase() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> Program.parse(ProgramTest.ID, "#nexus base terrain"));
    }
    
    @Test
    public void throwsOnVersionDirective() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ProgramTest.parse(Base.ENTITY, "#version 330"));
    }
    
    @Test
    public void readsTheDeclaredBlocksAndSamplers() {
        Program program = ProgramTest.parse(
                Base.ENTITY,
                "layout(std140) uniform ProgramTestTint {",
                "    vec4 Tint;",
                "};",
                "uniform sampler2D ProgramTestNoise;"
        );
        Assertions.assertThat(program.getBlocks()).containsExactly("ProgramTestTint");
        Assertions.assertThat(program.getSamplers()).containsExactly("ProgramTestNoise");
    }
    
    @Test
    public void throwsOnMisspelledHook() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ProgramTest.parse(Base.ENTITY, "void nexus_vertx() {", "}"));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ProgramTest.parse(Base.ENTITY, "vec4 nexus_albedos(vec4 albedo) {", "    return albedo;", "}"));
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void throwsOnRedeclaredBaseInput(Base base) {
        for (String name : base.getFormat().getElementAttributeNames()) {
            Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ProgramTest.parse(base, "in vec4 " + name + ";"));
        }
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void formatIsTheBaseFormatWithoutInputs(Base base) {
        Assertions.assertThat(ProgramTest.parse(base).format()).isEqualTo(base.getFormat());
    }
    
    @Test
    public void formatAppendsTheInputsInFileOrder() {
        VertexFormat base = Base.ENTITY.getFormat();
        VertexFormat format = ProgramTest.parse(Base.ENTITY, "in vec2 ProgramTestWobble;", "in int ProgramTestLayer;", "in ivec3 ProgramTestCell;").format();
        List<String> names = Stream.concat(base.getElementAttributeNames().stream(), Stream.of("ProgramTestWobble", "ProgramTestLayer", "ProgramTestCell")).toList();
        Assertions.assertThat(format.getElementAttributeNames()).containsExactlyElementsOf(names);
        for (VertexFormatElement element : base.getElements()) {
            Assertions.assertThat(format.getOffset(element)).isEqualTo(base.getOffset(element));
        }
        
        Assertions.assertThat(format.getElementMapping().get("ProgramTestWobble"))
                .extracting(VertexFormatElement::type, VertexFormatElement::count)
                .containsExactly(VertexFormatElement.Type.FLOAT, 2);
        Assertions.assertThat(format.getElementMapping().get("ProgramTestLayer"))
                .extracting(VertexFormatElement::type, VertexFormatElement::count)
                .containsExactly(VertexFormatElement.Type.INT, 1);
        Assertions.assertThat(format.getElementMapping().get("ProgramTestCell"))
                .extracting(VertexFormatElement::type, VertexFormatElement::count)
                .containsExactly(VertexFormatElement.Type.INT, 3);
        Assertions.assertThat(format.getVertexSize()).isEqualTo(base.getVertexSize() + 8 + 4 + 12);
    }
    
    @Test
    public void sharesAnElementAcrossPrograms() {
        VertexFormat first = ProgramTest.parse(Base.POSITION_COLOR, "in vec3 ProgramTestShared;").format();
        VertexFormat second = ProgramTest.parse(Base.ENTITY, "in vec3 ProgramTestShared;").format();
        Assertions.assertThat(first.getElementMapping().get("ProgramTestShared")).isNotNull().isEqualTo(second.getElementMapping().get("ProgramTestShared"));
    }
    
    @Test
    public void throwsOnInputNameTakenByAnotherType() {
        Assertions.assertThat(ProgramTest.parse(Base.ENTITY, "in vec3 ProgramTestTaken;").format().getElementAttributeNames()).contains("ProgramTestTaken");
        Program fewer = ProgramTest.parse(Base.ENTITY, "in vec2 ProgramTestTaken;");
        Program integer = ProgramTest.parse(Base.ENTITY, "in ivec3 ProgramTestTaken;");
        Assertions.assertThatIllegalArgumentException().isThrownBy(fewer::format);
        Assertions.assertThatIllegalArgumentException().isThrownBy(integer::format);
    }
    
    @Test
    public void sharesAnElementAcrossThreads() throws Exception {
        int threads = 16;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<VertexFormat>> formats = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                Program program = ProgramTest.parse(Base.POSITION_COLOR, "in vec4 ProgramTestParallel;");
                formats.add(executor.submit(() -> {
                    barrier.await();
                    return program.format();
                }));
            }
        }
        
        Set<VertexFormatElement> elements = new HashSet<>();
        for (Future<VertexFormat> format : formats) {
            elements.add(format.get().getElementMapping().get("ProgramTestParallel"));
        }
        
        Assertions.assertThat(elements).hasSize(1).doesNotContainNull();
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void generatesTheBaseForAnEmptyProgram(Base base) throws IOException {
        Program program = ProgramTest.parse(base);
        for (ShaderType stage : ProgramTest.STAGES) {
            String generated = ProgramTest.generate(program, stage);
            List<String> vanilla = ProgramTest.lines(ProgramTest.template(base, stage)).stream()
                    .filter(line -> !line.startsWith("#nexus"))
                    .toList();
            Assertions.assertThat(generated).startsWith("#version 330\n").doesNotContain("#nexus", "nexus_");
            Assertions.assertThat(ProgramTest.lines(generated))
                    .filteredOn(line -> !line.startsWith("#define NEXUS_"))
                    .containsExactlyElementsOf(vanilla);
        }
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void putsTheDefinesBeforeTheBaseDeclarations(Base base) throws IOException {
        Program program = ProgramTest.parse(base, "#define NO_OVERLAY", "float programTestHoist() {", "    return 1.0;", "}");
        for (ShaderType stage : ProgramTest.STAGES) {
            List<String> lines = ProgramTest.lines(ProgramTest.generate(program, stage));
            Assertions.assertThat(lines).containsOnlyOnce("#define NO_OVERLAY");
            Assertions.assertThat(lines.subList(0, lines.indexOf("#define NO_OVERLAY"))).allMatch(line -> line.equals("#version 330") || line.startsWith("#define NEXUS_"));
        }
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void keepsAConditionalDefineAtTheDeclarations(Base base) throws IOException {
        Program program = ProgramTest.parse(base, "#ifdef NEXUS_FRAGMENT", "#define PROGRAM_TEST_X", "#endif");
        for (ShaderType stage : ProgramTest.STAGES) {
            List<String> template = ProgramTest.lines(ProgramTest.template(base, stage));
            String aboveAnchor = template.get(template.indexOf("#nexus declarations") - 1);
            List<String> lines = ProgramTest.lines(ProgramTest.generate(program, stage));
            int define = lines.indexOf("#define PROGRAM_TEST_X");
            Assertions.assertThat(lines).containsOnlyOnce("#define PROGRAM_TEST_X");
            Assertions.assertThat(lines.subList(define - 1, define + 2)).containsExactly("#ifdef NEXUS_FRAGMENT", "#define PROGRAM_TEST_X", "#endif");
            Assertions.assertThat(define).isGreaterThan(lines.indexOf(aboveAnchor)).isLessThan(lines.indexOf("void main() {"));
        }
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void callsTheVertexHookLastInTheVertexMain(Base base) throws IOException {
        String vertex = ProgramTest.generate(ProgramTest.parse(base, "void nexus_vertex() {", "}"), ShaderType.VERTEX);
        Assertions.assertThat(vertex.indexOf("void nexus_vertex()")).isNotNegative().isLessThan(vertex.indexOf("void main()"));
        Assertions.assertThat(ProgramTest.main(vertex)).containsOnlyOnce("nexus_vertex").containsPattern("nexus_vertex\\(\\);\\s*}\\s*$");
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void wrapsTheFetchWithTheAlbedoHook(Base base) throws IOException {
        String fragment = ProgramTest.generate(ProgramTest.parse(base, "vec4 nexus_albedo(vec4 albedo) {", "    return albedo;", "}"), ShaderType.FRAGMENT);
        String main = ProgramTest.main(fragment);
        Assertions.assertThat(fragment.indexOf("vec4 nexus_albedo(")).isNotNegative().isLessThan(fragment.indexOf("void main()"));
        Assertions.assertThat(main).containsOnlyOnce("nexus_albedo").containsPattern(Pattern.quote(ProgramTest.fetch(base)) + "\\s*color = nexus_albedo\\(color\\);");
        Assertions.assertThat(main.indexOf("nexus_albedo")).isLessThan(main.indexOf("ColorModulator"));
    }
    
    @ParameterizedTest
    @EnumSource(Base.class)
    public void splitsTheProgramBetweenStages(Base base) throws IOException {
        Program program = ProgramTest.parse(
                base,
                "#define PROGRAM_TEST_SCALE 2.0",
                "#moj_import <nexus:program_test.glsl>",
                "in vec2 ProgramTestSplit;",
                "out vec2 programTestWobble;",
                "flat out int programTestLayer;",
                "float programTestScale(float value) {",
                "    return value * PROGRAM_TEST_SCALE;",
                "}",
                "void nexus_vertex() {",
                "    programTestWobble = ProgramTestSplit;",
                "    programTestLayer = 1;",
                "}",
                "vec4 nexus_albedo(vec4 albedo) {",
                "    return albedo * programTestScale(programTestWobble.x);",
                "}"
        );
        String vertex = ProgramTest.generate(program, ShaderType.VERTEX);
        String fragment = ProgramTest.generate(program, ShaderType.FRAGMENT);
        Assertions.assertThat(vertex).contains(
                "#define NEXUS_VERTEX",
                "#define PROGRAM_TEST_SCALE 2.0",
                "#moj_import <nexus:program_test.glsl>",
                "in vec2 ProgramTestSplit;",
                "out vec2 programTestWobble;",
                "flat out int programTestLayer;",
                "float programTestScale(float value) {",
                "void nexus_vertex() {"
        ).doesNotContain("NEXUS_FRAGMENT", "nexus_albedo");
        Assertions.assertThat(fragment).contains(
                "#define NEXUS_FRAGMENT",
                "#define PROGRAM_TEST_SCALE 2.0",
                "#moj_import <nexus:program_test.glsl>",
                "in vec2 programTestWobble;",
                "flat in int programTestLayer;",
                "float programTestScale(float value) {",
                "vec4 nexus_albedo(vec4 albedo) {"
        ).doesNotContain("NEXUS_VERTEX", "ProgramTestSplit", "out vec2 programTestWobble;", "nexus_vertex");
        Assertions.assertThat(vertex.indexOf("#define NEXUS_VERTEX")).isLessThan(vertex.indexOf("float programTestScale("));
        Assertions.assertThat(fragment.indexOf("#define NEXUS_FRAGMENT")).isLessThan(fragment.indexOf("float programTestScale("));
    }
}
