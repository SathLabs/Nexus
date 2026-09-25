package dev.satherov.nexus.api.codec;

///
/// An error found at one value during an encode or decode.
///
/// @param path    The path from the top-level value to the value at fault, such as `entries[2].cost` or `prices['stone']`,
///                or empty if the top-level value is at fault.
/// @param message The message of the error, such as `expected a number, found "many"`.
///
public record CodecError(String path, String message) { }
