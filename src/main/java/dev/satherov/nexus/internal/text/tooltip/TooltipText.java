package dev.satherov.nexus.internal.text.tooltip;

import lombok.Getter;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import org.jetbrains.annotations.ApiStatus;

///
/// The tooltip keys of Nexus itself, declared on the handle of the library.
///
@ApiStatus.Internal
public enum TooltipText implements Translatable {
    ///
    /// The hint to hold the sneak key for details, with the keybind component of the sneak key as `{{key}}`.
    ///
    HOLD_SHIFT("Hold {{key}} for details");

    ///
    /// The declared key of the constant.
    ///
    @Getter
    private final Translation translation;

    ///
    /// Declares the key of the constant on the handle of the library, with the given English default.
    ///
    /// @param english The English default, with its placeholders.
    ///
    TooltipText(String english) {
        this.translation = Nexus.getMod().getTranslations().define("tooltip", this, english);
    }
    
    ///
    /// Loads this class.
    ///
    public static void init() { }
}
