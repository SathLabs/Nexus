package dev.satherov.nexus.internal.render.shader;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;

import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.FileUtil;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

///
/// The pipelines created from the program files of the mods, and the reload listener that compiles them.
///
/// A program file is read from the jar of its mod when its pipeline is created.
/// On every resource reload, both stages of every program are generated from the templates of its base,
/// have their includes expanded and are compiled after the shader manager.
///
@Slf4j
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Programs extends SimplePreparableReloadListener<Map<RenderPipeline, Programs.Text>> {
    
    ///
    /// The key of the reload listener.
    ///
    private static final Identifier KEY = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "programs");
    
    ///
    /// The state of a pipeline created without one, which has a depth test with depth write, no blend, and culling.
    ///
    private static final RenderPipeline.Snippet DEFAULT_STATE = RenderPipeline.builder().withDepthStencilState(DepthStencilState.DEFAULT).buildSnippet();
    
    ///
    /// The program file of every pipeline created from one.
    ///
    private static final Map<RenderPipeline, ProgramFile> FILE_BY_PIPELINE = new ConcurrentHashMap<>();
    
    ///
    /// The pipelines whose program could not be compiled at the last reload.
    ///
    /// Should only ever be touched on the client thread.
    ///
    private static final Set<RenderPipeline> BROKEN = new HashSet<>();
    
    ///
    /// Reads the program file `assets/<modid>/nexus/shaders/<name>.glsl` from the jar of the given mod and builds its pipeline.
    ///
    /// The pipeline is built as follows:
    /// - The location and the identifier of both shaders are `<modid>:nexus/<name>`.
    /// - The vertex format is the program's, with the vertex mode of the given state, or quads if the state doesn't set one.
    /// - The uniform blocks and samplers are those of the given state, then those of the base, and then those the program file declares, each once.
    /// - The blend, depth, cull, and defines are those of the given state, or a depth test with depth write, no blend, and culling if there is none.
    ///
    /// May be called from parallel mod constructors.
    ///
    /// @param mod   The handle of the mod.
    /// @param name  The name of the program.
    /// @param state The state of the pipeline, or `null` to use the default state.
    ///
    /// @return The pipeline of the program.
    ///
    /// @throws IllegalArgumentException If the jar of the mod has no such program file or the program file fails [Program#parse(Identifier, String)] or [Program#format()].
    /// @throws UncheckedIOException     If the program file could not be read.
    ///
    public static RenderPipeline create(NexusMod mod, String name, RenderPipeline.@Nullable Snippet state) {
        Identifier location = mod.id("nexus/" + name);
        Identifier file = mod.id("nexus/shaders/" + name + ".glsl");
        byte[] text;
        try {
            text = ModList.get().getModFileById(mod.getModId()).getFile().getContents().readFile("assets/" + file.getNamespace() + "/" + file.getPath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the program '" + file + "'", exception);
        }
        
        if (text == null) {
            throw new IllegalArgumentException("Mod '" + mod.getModId() + "' has no program '" + file + "'");
        }
        
        Program program = Program.parse(file, new String(text, StandardCharsets.UTF_8));
        RenderPipeline.Snippet snippet = Objects.requireNonNullElse(state, Programs.DEFAULT_STATE);
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet)
                .withLocation(location)
                .withVertexShader(location)
                .withFragmentShader(location)
                .withVertexFormat(program.format(), snippet.vertexFormatMode().orElse(VertexFormat.Mode.QUADS));
        
        Set<String> stateBlocks = snippet.uniforms().orElse(List.of()).stream().map(RenderPipeline.UniformDescription::name).collect(Collectors.toSet());
        Set<String> stateSamplers = Set.copyOf(snippet.samplers().orElse(List.of()));
        Sets.difference(Sets.union(program.getBase().getBlocks(), program.getBlocks()), stateBlocks).forEach(block -> builder.withUniform(block, UniformType.UNIFORM_BUFFER));
        Sets.difference(Sets.union(program.getBase().getSamplers(), program.getSamplers()), stateSamplers).forEach(builder::withSampler);
        RenderPipeline pipeline = builder.build();
        Programs.FILE_BY_PIPELINE.put(pipeline, new ProgramFile(file, program));
        return pipeline;
    }
    
    ///
    /// Generates the given stage of the given program file from the template of its base and expands its includes.
    ///
    /// @param manager The resource manager of the reload.
    /// @param file    The program file.
    /// @param stage   The stage to generate.
    ///
    /// @return The text of the stage.
    ///
    /// @throws IOException If the template could not be read.
    ///
    private static String expand(ResourceManager manager, ProgramFile file, ShaderType stage) throws IOException {
        String template;
        try (InputStream input = manager.open(file.program().getBase().template(stage))) {
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        return String.join("", new Includes(manager, file.id()).process(file.program().generate(stage, template)));
    }
    
    ///
    /// The uniform blocks the program file of the given pipeline declares.
    ///
    /// @param pipeline The render pipeline.
    ///
    /// @return The names of the uniform blocks, or an empty set if the pipeline was not created from a program file.
    ///
    public static @Unmodifiable Set<String> blocks(RenderPipeline pipeline) {
        ProgramFile file = Programs.FILE_BY_PIPELINE.get(pipeline);
        return file == null ? Set.of() : file.program().getBlocks();
    }
    
    ///
    /// If the program of the given pipeline could not be compiled at the last reload.
    ///
    /// @param pipeline The render pipeline.
    ///
    /// @return `true` if the program could not be compiled, or `false` if the pipeline was not created from a program file.
    ///
    public static boolean isBroken(RenderPipeline pipeline) {
        return Programs.BROKEN.contains(pipeline);
    }
    
    ///
    /// Registers the reload listener of the programs to run directly after the shader manager.
    ///
    /// @param event The registration event of the client reload listeners.
    ///
    public static void register(AddClientReloadListenersEvent event) {
        event.addListener(Programs.KEY, new Programs());
        event.addDependency(VanillaClientListeners.SHADERS, Programs.KEY);
        // Going before the sound manager puts us directly after the shader manager's cache clear,
        // so no frame can draw and cache our pipelines as invalid before we compile them.
        event.addDependency(Programs.KEY, VanillaClientListeners.SOUNDS);
    }
    
    ///
    /// Generates both stages of every program from the templates of its base and expands their includes.
    ///
    /// If the templates of a program could not be read, it will be logged and left out.
    ///
    /// @param manager  The resource manager of the reload.
    /// @param profiler The profiler of the reload.
    ///
    /// @return The text of every program by its pipeline.
    ///
    @Override
    protected Map<RenderPipeline, Text> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<RenderPipeline, Text> textByPipeline = new HashMap<>();
        Programs.FILE_BY_PIPELINE.forEach((pipeline, file) -> {
            try {
                textByPipeline.put(pipeline, new Text(Programs.expand(manager, file, ShaderType.VERTEX), Programs.expand(manager, file, ShaderType.FRAGMENT)));
            } catch (IOException exception) {
                Programs.log.error("Could not read the templates of the program '{}'", file.id(), exception);
            }
        });
        return textByPipeline;
    }
    
    ///
    /// Compiles every program.
    ///
    /// A program that could not be compiled is logged and marked as broken until the next reload.
    ///
    /// @param preparations The text of every program by its pipeline.
    /// @param manager      The resource manager of the reload.
    /// @param profiler     The profiler of the reload.
    ///
    @Override
    protected void apply(Map<RenderPipeline, Text> preparations, ResourceManager manager, ProfilerFiller profiler) {
        GpuDevice device = RenderSystem.getDevice();
        Programs.BROKEN.clear();
        Programs.FILE_BY_PIPELINE.forEach((pipeline, file) -> {
            Text text = preparations.get(pipeline);
            // If the templates could not be read, prepare already logged it.
            if (text == null) {
                Programs.BROKEN.add(pipeline);
                return;
            }
            
            ShaderSource source = (_, type) -> switch (type) {
                case VERTEX -> text.vertex();
                case FRAGMENT -> text.fragment();
            };
            if (!device.precompilePipeline(pipeline, source).isValid()) {
                Programs.BROKEN.add(pipeline);
                Programs.log.error("Could not compile the program '{}' of the pipeline '{}'", file.id(), pipeline.getLocation());
            }
        });
    }
    
    ///
    /// The text of both stages of a program, with its includes expanded.
    ///
    /// @param vertex   The text of the vertex stage.
    /// @param fragment The text of the fragment stage.
    ///
    public record Text(String vertex, String fragment) { }
    
    ///
    /// A parsed program file.
    ///
    /// @param id      The identifier of the program file.
    /// @param program The parsed program.
    ///
    private record ProgramFile(Identifier id, Program program) { }
    
    ///
    /// The include expansion of one stage of a program file, read through the resource manager of the reload.
    ///
    /// An include in angle brackets `<ns:path>` is read from `ns:shaders/include/path`, and one in quotes relative to the folder of the program file.
    ///
    private static final class Includes extends GlslPreprocessor {
        
        ///
        /// The resource manager of the reload.
        ///
        private final ResourceManager manager;
        
        ///
        /// The folder of the program file.
        ///
        private final Identifier folder;
        
        ///
        /// The includes expanded so far.
        ///
        private final Set<Identifier> included = new HashSet<>();
        
        ///
        /// Should only ever be called from [Programs#expand(ResourceManager, ProgramFile, ShaderType)].
        ///
        /// @param manager The resource manager of the reload.
        /// @param file    The identifier of the program file.
        ///
        private Includes(ResourceManager manager, Identifier file) {
            this.manager = manager;
            this.folder = file.withPath(FileUtil::getFullResourcePath);
        }
        
        ///
        /// Reads the given include, or returns `null` if this stage already includes it.
        ///
        /// If the include could not be found or read, it will be replaced by an `#error` directive that fails the compilation.
        ///
        /// @param isRelative If the include is relative to the program file.
        /// @param path       The path of the include.
        ///
        /// @return The text of the include, or `null` if this stage already includes it.
        ///
        @Override
        public @Nullable String applyImport(boolean isRelative, String path) {
            Identifier id;
            try {
                id = isRelative ? this.folder.withPath(parent -> FileUtil.normalizeResourcePath(parent + path)) : Identifier.parse(path).withPrefix("shaders/include/");
            } catch (IdentifierException exception) {
                return "#error " + exception.getMessage();
            }
            
            if (!this.included.add(id)) {
                return null;
            }
            
            Optional<Resource> resource = this.manager.getResource(id);
            if (resource.isEmpty()) {
                return "#error Could not find the include '" + id + "'";
            }
            
            try (InputStream input = resource.get().open()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "#error Could not read the include '" + id + "', " + exception.getMessage();
            }
        }
    }
}
