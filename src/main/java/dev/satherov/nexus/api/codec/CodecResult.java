package dev.satherov.nexus.api.codec;

import java.util.function.Consumer;
import java.util.function.Function;

///
/// An outcome of an encode or decode that holds either the value or the exception the throwing form would have thrown.
///
/// @param <T> The type of the value.
///
public sealed interface CodecResult<T> {

    ///
    /// If this result holds a value.
    ///
    /// @return `true` if this result holds a value.
    ///
    default boolean isSuccess() {
        return this instanceof CodecResult.Success<T>;
    }

    ///
    /// Gets the value, or the given fallback if this result is a failure.
    ///
    /// @param fallback The value to use if this result is a failure.
    ///
    /// @return The value, or the fallback if this result is a failure.
    ///
    default T orElse(T fallback) {
        return switch (this) {
            case CodecResult.Success(T value) -> value;
            case CodecResult.Failure(_) -> fallback;
        };
    }

    ///
    /// Gets the value, or what the given fallback makes of the failure.
    ///
    /// @param fallback The function that turns the failure into a value, called only if this result is a failure.
    ///
    /// @return The value, or what the fallback returned if this result is a failure.
    ///
    default T orElseGet(Function<? super CodecException, ? extends T> fallback) {
        return switch (this) {
            case CodecResult.Success(T value) -> value;
            case CodecResult.Failure(CodecException failure) -> fallback.apply(failure);
        };
    }

    ///
    /// Gets the value, or throws the failure.
    ///
    /// @return The value.
    ///
    /// @throws CodecException If this result is a failure.
    ///
    default T orElseThrow() {
        return switch (this) {
            case CodecResult.Success(T value) -> value;
            case CodecResult.Failure(CodecException failure) -> throw failure;
        };
    }

    ///
    /// Applies the given mapper to the value.
    ///
    /// @param mapper The function that turns the value into the new value, called only if this result holds a value.
    ///
    /// @return A result holding the new value, or the same failure if this result is a failure.
    ///
    default <R> CodecResult<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case CodecResult.Success(T value) -> new CodecResult.Success<>(mapper.apply(value));
            case CodecResult.Failure(CodecException failure) -> new CodecResult.Failure<>(failure);
        };
    }

    ///
    /// Runs the given action with the value.
    ///
    /// Does nothing if this result is a failure.
    ///
    /// @param action The action to run with the value.
    ///
    default void ifSuccess(Consumer<? super T> action) {
        if (this instanceof CodecResult.Success(T value)) {
            action.accept(value);
        }
    }

    ///
    /// Runs the given action with the failure.
    ///
    /// Does nothing if this result holds a value.
    ///
    /// @param action The action to run with the failure.
    ///
    default void ifFailure(Consumer<? super CodecException> action) {
        if (this instanceof CodecResult.Failure(CodecException failure)) {
            action.accept(failure);
        }
    }

    ///
    /// A result that holds a value.
    ///
    /// @param value The encoded or decoded value.
    /// @param <T>   The type of the value.
    ///
    record Success<T>(T value) implements CodecResult<T> { }

    ///
    /// A result that holds the exception the throwing form would have thrown.
    ///
    /// @param failure The exception that holds every error found.
    /// @param <T>     The type of the value the encode or decode would have returned.
    ///
    record Failure<T>(CodecException failure) implements CodecResult<T> { }
}
