package dev.satherov.nexus.api.render.shape;

import net.minecraft.resources.Identifier;

///
/// A sprite of an atlas.
///
/// The sprite is looked up when a shape showing it is drawn.
/// If the texture is not in the atlas, the missing texture will be shown.
/// If the atlas is not a texture atlas, the whole texture at its identifier will be shown instead.
///
/// @param atlas   The identifier of the atlas.
/// @param texture The texture inside the atlas, without `textures/` and `.png`.
///
public record Sprite(Identifier atlas, Identifier texture) {
    
    ///
    /// Creates a sprite of the given texture in the blocks atlas.
    ///
    /// @param texture The texture inside the blocks atlas, without `textures/` and `.png`.
    ///
    /// @return The sprite.
    ///
    public static Sprite blocks(Identifier texture) {
        return new Sprite(Identifier.withDefaultNamespace("textures/atlas/blocks.png"), texture);
    }
    
    ///
    /// Creates a sprite of the given texture in the items atlas.
    ///
    /// @param texture The texture inside the items atlas, without `textures/` and `.png`.
    ///
    /// @return The sprite.
    ///
    public static Sprite items(Identifier texture) {
        return new Sprite(Identifier.withDefaultNamespace("textures/atlas/items.png"), texture);
    }
}
