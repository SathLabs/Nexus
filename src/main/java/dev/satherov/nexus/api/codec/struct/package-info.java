///
/// The codecs of structs and the fields they are made of.
///
/// A [StructCodec] is created from [StructField]s and a constructor called with their values.
/// - [StructField]: The part of a struct that one codec encodes.
/// - [StructCodec]: The codec of a struct, with its short form, recipe serializer, and DFU map codec.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.codec.struct;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
