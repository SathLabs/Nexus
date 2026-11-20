package dev.satherov.nexus.internal.render.shader;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.render.shader.syntax.Item;
import dev.satherov.nexus.internal.render.shader.syntax.Parser;
import dev.satherov.zelqro.mapping.ObjectMapping;

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

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
    /// The define that removes the overlay from the entity base.
    ///
    private static final String NO_OVERLAY = "NO_OVERLAY";
    
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
    /// - The uniform blocks and samplers are those of the given state, then those of the base, and then those the program file and its includes declare, each once.
    /// - The entity base also has the overlay sampler `Sampler1`, unless the state or the program file defines `NO_OVERLAY`.
    /// - The blend, depth, cull, and defines are those of the given state, or a depth test with depth write, no blend, and culling if there is none.
    ///
    /// The includes are read from the jars of their mods as well, and their declarations inside conditionals count.
    /// An include of vanilla, one that no jar has, or one that is only whitespace declares nothing.
    ///
    /// May be called from parallel mod constructors.
    ///
    /// @param mod   The handle of the mod.
    /// @param name  The name of the program.
    /// @param state The state of the pipeline, or `null` to use the default state.
    ///
    /// @return The pipeline of the program.
    ///
    /// @throws IllegalArgumentException If the jar of the mod has no such program file, if the program file fails [Program#parse(Identifier, String)] or [Program#format()], or if an include fails [Parser#parse(Identifier, String)].
    /// @throws UncheckedIOException     If the program file or an include could not be read.
    ///
    public static RenderPipeline create(NexusMod mod, String name, RenderPipeline.@Nullable Snippet state) {
        Identifier location = mod.id("nexus/" + name);
        Identifier file = mod.id("nexus/shaders/" + name + ".glsl");
        String text = Programs.read(file);
        if (text == null) {
            throw new IllegalArgumentException("Mod '" + mod.getModId() + "' has no program '" + file + "'");
        }
        
        Program program = Program.parse(file, text);
        Set<String> blocks = new HashSet<>(program.getBlocks());
        Set<String> samplers = new HashSet<>(program.getSamplers());
        for (Item item : Programs.includedItems(file, text)) {
            switch (item) {
                case Item.Block(String block, _, _) -> blocks.add(block);
                case Item.Sampler(String sampler, _, _) -> samplers.add(sampler);
                case Item.Directive _, Item.Conditional _, Item.Function _, Item.Input _, Item.Output _, Item.Declaration _ -> {
                }
            }
        }
        
        RenderPipeline.Snippet snippet = Objects.requireNonNullElse(state, Programs.DEFAULT_STATE);
        // Vanilla declares the overlay sampler on each entity pipeline and not in its entity snippet.
        if (program.getBase() == Base.ENTITY && !program.isDefined(Programs.NO_OVERLAY) && !Programs.defines(snippet, Programs.NO_OVERLAY)) {
            samplers.add(VanillaSampler.OVERLAY.getGlslName());
        }
        
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet)
                .withLocation(location)
                .withVertexShader(location)
                .withFragmentShader(location)
                .withVertexFormat(program.format(), snippet.vertexFormatMode().orElse(VertexFormat.Mode.QUADS));
        
        Set<String> stateBlocks = snippet.uniforms().orElse(List.of()).stream().map(RenderPipeline.UniformDescription::name).collect(Collectors.toSet());
        Set<String> stateSamplers = Set.copyOf(snippet.samplers().orElse(List.of()));
        Sets.difference(Sets.union(program.getBase().getBlocks(), blocks), stateBlocks).forEach(block -> builder.withUniform(block, UniformType.UNIFORM_BUFFER));
        Sets.difference(Sets.union(program.getBase().getSamplers(), samplers), stateSamplers).forEach(builder::withSampler);
        RenderPipeline pipeline = builder.build();
        Programs.FILE_BY_PIPELINE.put(pipeline, new ProgramFile(file, program, Set.copyOf(blocks)));
        return pipeline;
    }
    
    ///
    /// Reads the given file from the jar of the mod whose id is the namespace of the file.
    ///
    /// @param file The identifier of the file, with a path inside the `assets/<modid>` folder of the jar.
    ///
    /// @return The text of the file, or `null` if no mod has that id or its jar doesn't have the file.
    ///
    /// @throws UncheckedIOException If the file could not be read.
    ///
    private static @Nullable String read(Identifier file) {
        if (!ModList.get().isLoaded(file.getNamespace())) {
            return null;
        }
        
        byte[] text;
        try {
            text = ModList.get().getModFileById(file.getNamespace()).getFile().getContents().readFile("assets/" + file.getNamespace() + "/" + file.getPath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the file '" + file + "'", exception);
        }
        
        return ObjectMapping.mapNonNull(text, bytes -> new String(bytes, StandardCharsets.UTF_8));
    }
    
    ///
    /// Reads every include of the given program file and every include of those from the jars of their mods and parses their file-scope items.
    ///
    /// An include of vanilla, one that no jar has, or one that is only whitespace will be left out.
    ///
    /// @param file The identifier of the program file.
    /// @param text The text of the program file.
    ///
    /// @return The items of every include, with the items of every conditional in place of the conditional.
    ///
    /// @throws IllegalArgumentException If an include fails [Parser#parse(Identifier, String)].
    /// @throws UncheckedIOException     If an include could not be read.
    ///
    private static List<Item> includedItems(Identifier file, String text) {
        List<Item> items = new ArrayList<>();
        Includes includes = new Includes(file, id -> {
            // Vanilla's includes declare no sampler and only blocks that every base or vanilla itself declares.
            String include = id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? null : Programs.read(id);
            // Vanilla's preprocessor would throw on an include that is only whitespace.
            if (include == null || include.isBlank()) {
                return null;
            }
            
            Programs.addFlattened(items, Parser.parse(id, include));
            return include;
        });
        // Only the includes that the expansion reads matter here, so its text is dropped.
        includes.process(text);
        return items;
    }
    
    ///
    /// Adds the given items to the given list, with the items of every conditional in place of the conditional.
    ///
    /// @param list  The list to add the items to.
    /// @param items The items.
    ///
    private static void addFlattened(List<Item> list, List<Item> items) {
        for (Item item : items) {
            if (item instanceof Item.Conditional(List<Item> nested, _, _)) {
                Programs.addFlattened(list, nested);
            } else {
                list.add(item);
            }
        }
    }
    
    ///
    /// If the defines of the given state define the given macro.
    ///
    /// @param state The state of the pipeline.
    /// @param macro The name of the macro.
    ///
    /// @return `true` if the state defines the macro as a flag or with a value.
    ///
    private static boolean defines(RenderPipeline.Snippet state, String macro) {
        return state.shaderDefines()
                .map(defines -> defines.flags().contains(macro) || defines.values().containsKey(macro))
                .orElse(false);
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
        
        return String.join("", new Includes(file.id(), id -> Programs.readInclude(manager, id)).process(file.program().generate(stage, template)));
    }
    
    ///
    /// Reads the given include through the given resource manager.
    ///
    /// If the include could not be found or read, it will be replaced by an `#error` directive that fails the compilation.
    /// If the include is only whitespace, it will be read as empty.
    ///
    /// @param manager The resource manager of the reload.
    /// @param id      The identifier of the include.
    ///
    /// @return The text of the include, an empty string, or an `#error` directive.
    ///
    private static String readInclude(ResourceManager manager, Identifier id) {
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            return "#error Could not find the include '" + id + "'";
        }
        
        try (InputStream input = resource.get().open()) {
            String include = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            // Vanilla's preprocessor would throw on an include that is only whitespace, and it skips an empty one.
            return include.isBlank() ? "" : include;
        } catch (IOException exception) {
            return "#error Could not read the include '" + id + "', " + exception.getMessage();
        }
    }
    
    ///
    /// The uniform blocks the program file of the given pipeline and its includes declare.
    ///
    /// @param pipeline The render pipeline.
    ///
    /// @return The names of the uniform blocks, or an empty set if the pipeline was not created from a program file.
    ///
    public static @Unmodifiable Set<String> blocks(RenderPipeline pipeline) {
        ProgramFile file = Programs.FILE_BY_PIPELINE.get(pipeline);
        return file == null ? Set.of() : file.blocks();
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
    /// If the templates of a program could not be read or its stages could not be generated, it will be logged and left out.
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
            } catch (RuntimeException exception) {
                Programs.log.error("Could not generate the stages of the program '{}'", file.id(), exception);
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
            // If the stages could not be generated, prepare already logged it.
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
    /// @param blocks  The uniform blocks that the program file and its includes declare.
    ///
    private record ProgramFile(Identifier id, Program program, @Unmodifiable Set<String> blocks) { }
    
    ///
    /// The include expansion of a program file, which reads every include through its reader.
    ///
    /// An include in angle brackets `<ns:path>` is read from `ns:shaders/include/path`, and one in quotes relative to the folder of the program file.
    /// An include in quotes that leads out of the namespace will be replaced by an `#error` directive that fails the compilation.
    ///
    private static final class Includes extends GlslPreprocessor {
        
        ///
        /// The folder of the program file.
        ///
        private final Identifier folder;
        
        ///
        /// The reader of an include, called with its identifier.
        ///
        private final Function<Identifier, @Nullable String> reader;
        
        ///
        /// The includes expanded so far.
        ///
        private final Set<Identifier> included = new HashSet<>();
        
        ///
        /// Should only ever be called from [Programs#includedItems(Identifier, String)] and [Programs#expand(ResourceManager, ProgramFile, ShaderType)].
        ///
        /// @param file   The identifier of the program file.
        /// @param reader The reader of an include, called with its identifier.
        ///
        private Includes(Identifier file, Function<Identifier, @Nullable String> reader) {
            this.folder = file.withPath(FileUtil::getFullResourcePath);
            this.reader = reader;
        }
        
        ///
        /// Reads the given include, or returns `null` if the program file already includes it.
        ///
        /// @param isRelative If the include is relative to the program file.
        /// @param path       The path of the include.
        ///
        /// @return The text of the include, an `#error` directive if the path is invalid, or `null` if the program file already includes it or the reader returned `null`.
        ///
        @Override
        public @Nullable String applyImport(boolean isRelative, String path) {
            String location = isRelative ? FilenameUtils.normalize(this.folder.getPath() + path, true) : path;
            if (location == null) {
                return "#error The include '" + path + "' leads out of the namespace '" + this.folder.getNamespace() + "'";
            }
            
            Identifier id;
            try {
                id = isRelative ? this.folder.withPath(location) : Identifier.parse(location).withPrefix("shaders/include/");
            } catch (IdentifierException exception) {
                return "#error " + exception.getMessage();
            }
            
            if (!this.included.add(id)) {
                return null;
            }
            
            return this.reader.apply(id);
        }
    }
}
