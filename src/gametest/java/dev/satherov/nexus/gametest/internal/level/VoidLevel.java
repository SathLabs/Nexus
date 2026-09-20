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
/// The empty level both runs test in: the `nexus_gametest:void` preset, creative, with commands on.
///
@UtilityClass
@ApiStatus.Internal
public class VoidLevel {

    ///
    /// The key of the `nexus_gametest:void` world preset.
    ///
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("nexus_gametest", "void"));
    ///
    /// The generation options: a fixed seed, no structures and no bonus chest.
    ///
    public static final WorldOptions OPTIONS = new WorldOptions(0L, false, false);

    ///
    /// Settings for a fresh level of the given name.
    ///
    /// @param name The name of the level.
    ///
    /// @return Settings for a fresh level of the given name.
    ///
    public static LevelSettings settings(String name) {
        return new LevelSettings(name, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
    }

    ///
    /// The preset's dimensions from the loaded registries.
    ///
    /// @param registries The loaded registries.
    ///
    /// @return The preset's dimensions.
    ///
    public static WorldDimensions dimensions(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(VoidLevel.PRESET).value().createWorldDimensions();
    }
}
