package dev.satherov.nexus.api.keybind.activation;

///
/// The state of a key on one tick.
///
/// @param down        Whether the key is down on this tick.
/// @param wasDown     Whether the key was down on the tick before.
/// @param heldTicks   The number of ticks the key has been down, `1` on the tick it goes down and `0` while it is up.
/// @param idleTicks   The number of ticks the key has been up. On the tick it goes down, the length of the gap that ended.
/// @param interrupted Whether the key went up on this tick without the player letting go.
///
public record KeyTick(boolean down, boolean wasDown, int heldTicks, int idleTicks, boolean interrupted) { }
