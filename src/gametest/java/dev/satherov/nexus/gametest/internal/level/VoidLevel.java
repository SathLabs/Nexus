package dev.satherov.nexus.gametest.internal.level;

import lombok.experimental.UtilityClass;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import org.jetbrains.annotations.ApiStatus;

///
/// Holds the presets and the config for the empty void level test world.
///
@UtilityClass
@ApiStatus.Internal
public class VoidLevel {
    
    ///
    /// The resource key of the `nexus_gametest:void` world preset.
    ///
    /// Found in the data directory of the gametest source set's resource folder.
    ///
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("nexus_gametest", "void"));
    
    ///
    /// The generation options for every world.
    ///
    /// The seed is fixed to 0 to ensure the same world every time.
    /// (Technically this doesn't matter since we're in a void world without biomes anyway, but considering minecraft's random sources are super weird, we better stick it to just 0)
    ///
    public static final WorldOptions OPTIONS = new WorldOptions(0L, false, false);
    
    ///
    /// The level settings to create the new world with, using the given name for the created level.
    ///
    /// @param name The name of the level.
    ///
    /// @return The settings for creating a fresh test world.
    ///
    public static LevelSettings settings(String name) {
        return new LevelSettings(name, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
    }
    
    ///
    /// The dimensions of the void preset, loaded from the registries.
    ///
    /// @param registries The registry lookup provider.
    ///
    /// @return The dimensions of the void preset.
    ///
    public static WorldDimensions dimensions(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(VoidLevel.PRESET).value().createWorldDimensions();
    }
}
