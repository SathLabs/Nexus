package dev.satherov.nexus.api.registry.datagen;

import net.minecraft.resources.Identifier;

///
/// The key of one fact that a datagen provider reads off an entry.
///
/// @param id     The identifier of the kind.
/// @param <FACT> The type of the fact.
///
public record ProviderKind<FACT>(Identifier id) { }
