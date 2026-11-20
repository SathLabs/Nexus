package dev.satherov.nexus.test.unit;

import dev.satherov.nexus.Nexus;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

///
/// Checks that the library holds no static state beyond the fields listed here, since one JVM runs every mod that uses
/// Nexus and static state is shared between all of them.
///
/// A static field counts as state when it isn't final, or when its type is a collection, a map, an array or an atomic
/// holder, whatever it was initialized with. A new one is either moved into the per-mod object it belongs to, or added
/// to [#ALLOWED] with the reason it has to live once per JVM, which a review then reads.
///
public class StaticStateTest {

    private static final String MAIN = "dev.satherov.nexus.";
    private static final List<String> OTHER_SOURCE_SETS = List.of("dev.satherov.nexus.test.", "dev.satherov.nexus.gametest.");

    private static final List<String> STATE_PACKAGES = List.of("java.util.concurrent.atomic", "it.unimi.dsi.fastutil");
    private static final List<Class<?>> STATE_TYPES = List.of(Collection.class, Map.class);

    ///
    /// The static state the library keeps on purpose, by field and the reason it lives once per JVM.
    ///
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            // Filled once while the class initializes and never written after.
            Map.entry("dev.satherov.nexus.api.persistence.format.Format.KEYS", "constant"),
            Map.entry("dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters.EQUALS_MAPS", "constant"),
            Map.entry("dev.satherov.nexus.internal.codec.CollectionCodecs$KeyReader.EQUALITY_MAPS", "constant"),
            Map.entry("dev.satherov.nexus.internal.persistence.player.DetachedPlayer.CARRIED_KEYS", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.shape.MeshBrush.CORNERS", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.shader.syntax.Parser.PRECISION_QUALIFIERS", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.shader.syntax.Lexer.OPERATORS", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.shader.VanillaBlock.BY_GLSL_NAME", "constant"),
            Map.entry("dev.satherov.nexus.internal.component.FilterCodec.CASE_KEYS", "constant"),
            Map.entry("dev.satherov.nexus.internal.component.FilterCodec.SYMBOLS", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.LINE_PIPELINES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.FILL_PIPELINES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.SPRITE_PIPELINES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.SHAPE_PIPELINES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.LINE_TYPES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.FILL_TYPES", "constant"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.SHAPE_TYPES", "constant"),
            // Shared between mods by design: one answer for the whole game.
            Map.entry("dev.satherov.nexus.api.mod.NexusMod.TAKEN", "a mod id has one handle per JVM"),
            Map.entry("dev.satherov.nexus.api.persistence.PlayerData.COPIES_BY_PLAYER", "a nested edit of one player shares the copy across every mod"),
            Map.entry("dev.satherov.nexus.internal.text.tooltip.TooltipDispatch.ITEM_ATTACHMENTS", "one appender runs the attachments of every mod"),
            Map.entry("dev.satherov.nexus.internal.text.tooltip.TooltipDispatch.FLUID_ATTACHMENTS", "one appender runs the attachments of every mod"),
            Map.entry("dev.satherov.nexus.internal.render.shader.Program.ELEMENTS", "vertex format elements are shared by type and count"),
            Map.entry("dev.satherov.nexus.internal.registry.EntryRegister.FIRED_REGISTRIES", "the registry events fire once per game"),
            Map.entry("dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters.UNHASHABLE", "a codec is logged once per JVM"),
            Map.entry("dev.satherov.nexus.internal.render.iris.IrisBridge.linkageWarned", "the Iris linkage is warned about once per JVM"),
            // State of the one game the JVM runs: a level, a server or the client's session.
            Map.entry("dev.satherov.nexus.internal.tick.Bindings.TICKERS_BY_LEVEL", "keyed by level"),
            Map.entry("dev.satherov.nexus.internal.tick.Ticks.UNLOADED_LEVELS", "keyed by level"),
            Map.entry("dev.satherov.nexus.internal.tick.EntityTickables.DECLARATIONS_BY_TYPE", "keyed by block entity type"),
            Map.entry("dev.satherov.nexus.internal.tick.TickStatistics.STATISTICS_BY_NAME", "the dev statistics of the game"),
            Map.entry("dev.satherov.nexus.internal.tick.TickStatistics.enabled", "the dev statistics of the game"),
            Map.entry("dev.satherov.nexus.internal.tick.client.ClientSession.CLIENT_TICKS", "the client's session"),
            Map.entry("dev.satherov.nexus.internal.tick.client.ClientSession.loggedIn", "the client's session"),
            Map.entry("dev.satherov.nexus.internal.tick.client.ClientSession.switching", "the client's session"),
            Map.entry("dev.satherov.nexus.internal.tick.client.ClientSession.recorded", "the client's session"),
            Map.entry("dev.satherov.nexus.internal.tick.client.ClientSession.loads", "the client's session"),
            Map.entry("dev.satherov.nexus.api.persistence.Persistence.started", "the server's start"),
            Map.entry("dev.satherov.nexus.api.network.Network.fired", "the payload registration fires once per game"),
            Map.entry("dev.satherov.nexus.internal.component.LookupGeneration.owner", "the server or client the lookups were built for"),
            Map.entry("dev.satherov.nexus.internal.component.PathReaders.modReaders", "collected once from every mod"),
            Map.entry("dev.satherov.nexus.internal.component.PathReaders.nexusReaders", "collected once from every mod"),
            Map.entry("dev.satherov.nexus.internal.component.PathReaders.longestIdentifierLength", "collected once from every mod"),
            Map.entry("dev.satherov.nexus.internal.component.PathReaders.extendedComponents", "collected once from every mod"),
            Map.entry("dev.satherov.nexus.internal.component.PathReaders.collected", "collected once from every mod"),
            Map.entry("dev.satherov.nexus.internal.keybind.client.ClientKeys.mappingsRegistered", "the key mapping event fires once per game"),
            // The render state of the one client window, filled from every mod's registrations.
            Map.entry("dev.satherov.nexus.internal.render.shader.Programs.FILE_BY_PIPELINE", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.shader.Programs.BROKEN", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.SETUPS", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.ShapePipelines.STATES", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.WAITING_PIPELINES", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.PIPELINES", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.HOOKS", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.WAITING_HUD_LAYERS", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.pipelinesRegistered", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.RenderDispatch.hudLayersRegistered", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.PackOverlay.armed", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.PackOverlay.seeded", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.PackOverlay.depthCopied", "the client window"),
            Map.entry("dev.satherov.nexus.internal.render.client.PackOverlay.formatWarned", "the client window")
    );

    @Test
    public void keepsNoStaticStateBeyondTheAllowedFields() {
        List<String> found = new ArrayList<>();
        for (JavaClass type : StaticStateTest.mainClasses()) {
            for (JavaField field : type.getFields()) {
                if (StaticStateTest.isState(field)) {
                    found.add(field.getFullName());
                }
            }
        }

        Assertions.assertThat(found).as("Static state not in ALLOWED").isSubsetOf(StaticStateTest.ALLOWED.keySet());
        Assertions.assertThat(StaticStateTest.ALLOWED.keySet()).as("Fields in ALLOWED that hold no static state anymore").isSubsetOf(found);
    }

    private static boolean isState(JavaField field) {
        Set<JavaModifier> modifiers = field.getModifiers();
        if (!modifiers.contains(JavaModifier.STATIC) || modifiers.contains(JavaModifier.SYNTHETIC) || StaticStateTest.isEnumConstant(field)) {
            return false;
        }

        if (!modifiers.contains(JavaModifier.FINAL)) {
            return true;
        }

        JavaClass type = field.getRawType();
        if (type.isArray() || StaticStateTest.STATE_PACKAGES.stream().anyMatch(type.getPackageName()::startsWith)) {
            return true;
        }

        return StaticStateTest.STATE_TYPES.stream().anyMatch(type::isAssignableTo);
    }

    private static boolean isEnumConstant(JavaField field) {
        return field.getOwner().isEnum() && field.getRawType().equals(field.getOwner());
    }

    private static List<JavaClass> mainClasses() {
        JavaClasses classes = new ClassFileImporter().importPath(StaticStateTest.rootOf(Nexus.class));
        return classes.stream()
                .filter(type -> type.getName().startsWith(StaticStateTest.MAIN))
                .filter(type -> StaticStateTest.OTHER_SOURCE_SETS.stream().noneMatch(type.getName()::startsWith))
                .toList();
    }

    private static Path rootOf(Class<?> member) {
        String resource = member.getName().replace('.', '/') + ".class";
        URL url = Objects.requireNonNull(StaticStateTest.class.getClassLoader().getResource(resource), resource);
        Path root;
        try {
            root = Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Could not locate " + url, exception);
        }

        for (int segment = 0; segment < resource.split("/").length; segment++) {
            root = root.getParent();
        }

        return root;
    }
}
