package dev.satherov.nexus.api.render.outline;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.internal.render.outline.OutlineRules;

import net.neoforged.fml.loading.FMLEnvironment;

import org.jetbrains.annotations.ApiStatus;

///
/// The block outline rules of one mod.
///
/// Both methods may be called from common code at any time, and a rule registered later is asked after one registered earlier.
/// Both do nothing on a dedicated server.
///
/// Can be accessed through [Render#getOutlines()].
///
public final class Outlines {
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Outlines(NexusMod mod) { }
    
    ///
    /// Adds a rule whose outline is drawn instead of vanilla's.
    ///
    /// The first replacing rule of any mod that returns one wins.
    ///
    /// @param rule The rule to add.
    ///
    public void replace(OutlineRule rule) {
        if (FMLEnvironment.getDist().isClient()) {
            OutlineRules.INSTANCE.replace(rule);
        }
    }
    
    ///
    /// Adds a rule whose outline is drawn beside vanilla's or the replacement.
    ///
    /// Every adding rule is asked.
    ///
    /// @param rule The rule to add.
    ///
    public void add(OutlineRule rule) {
        if (FMLEnvironment.getDist().isClient()) {
            OutlineRules.INSTANCE.add(rule);
        }
    }
}
