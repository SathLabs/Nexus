package dev.satherov.nexus.api.codec.result;

///
/// An error found at one value during encoding or decoding.
///
/// @param path    The path from the top-level value to the value at fault, or empty if the top-level value is at fault.
/// @param message The message of the error.
///
public record CodecError(String path, String message) { }
