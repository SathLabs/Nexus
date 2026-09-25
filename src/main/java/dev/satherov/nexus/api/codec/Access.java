package dev.satherov.nexus.api.codec;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

///
/// The access a codec needs from its format.
///
/// A codec runs on every format whose access is its own or a subtype of it.
/// Only ever used as a type argument.
///
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract sealed class Access permits Access.Plain {

    ///
    /// The access of a format without registries.
    ///
    /// Every codec that needs no registry carries it.
    ///
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static sealed class Plain extends Access permits Access.Registries { }

    ///
    /// The access of a format with registries.
    ///
    /// A codec that carries it only runs on a format built with registries.
    ///
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Registries extends Access.Plain { }
}
