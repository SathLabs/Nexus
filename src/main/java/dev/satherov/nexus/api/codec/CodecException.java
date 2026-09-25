package dev.satherov.nexus.api.codec;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

///
/// An exception that holds every error found during an encode or decode.
///
public final class CodecException extends RuntimeException {

    ///
    /// The first line of the message, or `null` if there is none.
    ///
    private final @Nullable String header;

    ///
    /// The errors, in the order the message lists them.
    ///
    private final List<CodecError> errors;

    ///
    /// Creates an exception with one error at an empty path.
    ///
    /// Meant to be used for a function in `flatXmap` or a `MapKey` that refuses a value.
    ///
    /// @param message The message of the error.
    ///
    public CodecException(String message) {
        this(List.of(new CodecError("", message)));
    }

    ///
    /// Creates an exception holding the given errors.
    ///
    /// @param errors The errors, in the order the message should list them.
    ///
    public CodecException(List<CodecError> errors) {
        this.header = null;
        this.errors = List.copyOf(errors);
    }

    ///
    /// Creates an exception holding the given errors, with the given header as the first line of its message.
    ///
    /// @param header The first line of the message, such as `Could not decode 'recipe' from JSON`.
    /// @param errors The errors, in the order the message should list them.
    ///
    public CodecException(String header, List<CodecError> errors) {
        this.header = header;
        this.errors = List.copyOf(errors);
    }

    ///
    /// The errors, in the order the message lists them.
    ///
    /// @return The errors, in the order the message lists them.
    ///
    @Unmodifiable
    public List<CodecError> errors() {
        return this.errors;
    }

    ///
    /// Renders every error on a line of its own, as its path and its message, below the header if there is one.
    ///
    /// @return The rendered errors.
    ///
    @Override
    public String getMessage() {
        if (this.header == null) {
            return this.errors.stream()
                    .map(CodecException::render)
                    .collect(Collectors.joining("\n"));
        }

        return this.errors.stream()
                .map(CodecException::render)
                .collect(Collectors.joining("\n  ", this.header + ":\n  ", ""));
    }

    ///
    /// Renders the given error as its path and its message, or as its message alone if the path is empty.
    ///
    private static String render(CodecError error) {
        return error.path().isEmpty() ? error.message() : error.path() + ": " + error.message();
    }
}
