package dev.satherov.nexus.internal.persistence.archive;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// A folder of an archive that lays out the entry of each key as one file.
///
/// @param root The folder of the archive, absolute and normalized.
///
@ApiStatus.Internal
public record ArchiveFolder(Path root) {

    ///
    /// The pattern of one escaped character, which is `%` and the four lowercase hex digits of the character.
    ///
    private static final Pattern ESCAPED = Pattern.compile("%([0-9a-f]{4})");

    ///
    /// The pattern of a segment that is a device name reserved by Windows, alone or followed by `.` and anything.
    ///
    private static final Pattern DEVICE = Pattern.compile("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?", Pattern.DOTALL);

    ///
    /// Escapes every character of the given segment other than lowercase letters, digits, `_`, `-`, and inner `.`, and escapes the following as well:
    /// - The `.` that starts a trailing `.dat` or `.retired`.
    /// - The first character of a device name reserved by Windows.
    ///
    /// @param segment The segment to escape.
    ///
    /// @return The escaped segment.
    ///
    private static String escape(String segment) {
        boolean device = ArchiveFolder.DEVICE.matcher(segment).matches();
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char character = segment.charAt(i);
            boolean plain = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '_' || character == '-';
            boolean innerDot = character == '.' && i > 0 && i < segment.length() - 1 && !Stream.of(State.values()).map(State::getSuffix).toList().contains(segment.substring(i));
            if ((plain || innerDot) && !(device && i == 0)) {
                escaped.append(character);
            } else {
                escaped.append('%').append(HexFormat.of().toHexDigits(character));
            }
        }

        return escaped.toString();
    }

    ///
    /// Unescapes the given name of a file or folder.
    ///
    /// @param name The escaped name, without its suffix.
    ///
    /// @return The segment, or `null` if no segment escapes to exactly that name.
    ///
    private static @Nullable String unescape(String name) {
        String segment = ArchiveFolder.ESCAPED.matcher(name).replaceAll(match -> Matcher.quoteReplacement(String.valueOf((char) HexFormat.fromHexDigits(match.group(1)))));
        return !segment.isEmpty() && ArchiveFolder.escape(segment).equals(name) ? segment : null;
    }

    ///
    /// Resolves the file of the given segments in the given state.
    ///
    /// Every escaped segment but the last will be a folder, and the last will be the name of the file in front of the suffix of the state.
    ///
    /// @param segments The segments of the key.
    /// @param state    The state of the entry.
    ///
    /// @return The file below the folder of the archive.
    ///
    public Path file(List<String> segments, State state) {
        String path = segments.stream()
                .map(ArchiveFolder::escape)
                .collect(Collectors.joining("/"));

        return this.root.resolve(path + state.getSuffix());
    }

    ///
    /// Gets the segments of the key of the given file if all the following is true:
    /// - The name of the file ends with `.dat` or `.retired`.
    /// - Every name of its path below the folder of the archive unescapes to a segment.
    ///
    /// @param file The file below the folder of the archive.
    ///
    /// @return The segments, or empty if the file isn't the file of a key.
    ///
    public Optional<List<String>> segments(Path file) {
        String name = file.getFileName().toString();
        State state = name.endsWith(State.RETIRED.getSuffix()) ? State.RETIRED : State.ACTIVE;
        if (!name.endsWith(state.getSuffix())) {
            return Optional.empty();
        }

        List<String> escapedNames = new ArrayList<>();
        this.root.relativize(file).forEach(part -> escapedNames.add(part.toString()));
        escapedNames.set(escapedNames.size() - 1, name.substring(0, name.length() - state.getSuffix().length()));
        List<String> segments = new ArrayList<>();
        for (String escaped : escapedNames) {
            String segment = ArchiveFolder.unescape(escaped);
            if (segment == null) {
                return Optional.empty();
            }

            segments.add(segment);
        }

        return Optional.of(List.copyOf(segments));
    }

    ///
    /// The state that an entry may have.
    ///
    @Getter
    @RequiredArgsConstructor
    public enum State {

        ///
        /// The entry is active and kept in a file that ends with `.dat`.
        ///
        ACTIVE(".dat"),

        ///
        /// The entry is retired and kept in a file that ends with `.retired`.
        ///
        RETIRED(".retired");

        ///
        /// The suffix of the file of an entry in this state.
        ///
        private final String suffix;
    }
}
