package dev.satherov.nexus.api.render.shape;

import net.minecraft.resources.Identifier;

///
/// A sprite of an atlas, resolved when the quad is drawn.
/// A missing sprite draws the missing texture.
///
/// @param atlas   The atlas, such as the blocks atlas.
/// @param texture The texture inside the atlas, without `textures/` and `.png`.
///
public record Sprite(Identifier atlas, Identifier texture) {
    
    ///
    /// Creates a sprite of the given texture in the blocks atlas.
    ///
    /// @param texture The texture inside the atlas, without `textures/` and `.png`.
    ///
    /// @return The sprite in the blocks atlas.
    ///
    public static Sprite blocks(Identifier texture) {
        return new Sprite(Identifier.withDefaultNamespace("textures/atlas/blocks.png"), texture);
    }
    
    ///
    /// Creates a sprite of the given texture in the items atlas.
    ///
    /// @param texture The texture inside the atlas, without `textures/` and `.png`.
    ///
    /// @return The sprite in the items atlas.
    ///
    public static Sprite items(Identifier texture) {
        return new Sprite(Identifier.withDefaultNamespace("textures/atlas/items.png"), texture);
    }
}
