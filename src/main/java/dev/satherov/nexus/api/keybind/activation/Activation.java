package dev.satherov.nexus.api.keybind.activation;

import org.jetbrains.annotations.Range;

///
/// A condition that decides on which ticks a key fires.
///
/// An activation has to depend on the given tick alone, since one activation serves every player.
///
@FunctionalInterface
public interface Activation {

    ///
    /// Creates an activation that fires on the tick the key goes down.
    ///
    /// @return The activation on press.
    ///
    static Activation press() {
        return tick -> tick.down() && !tick.wasDown();
    }

    ///
    /// Creates an activation that fires on the tick the player lets the key go.
    ///
    /// Does not fire on a tick that is [KeyTick#interrupted()].
    ///
    /// @return The activation on release.
    ///
    static Activation release() {
        return tick -> !tick.down() && tick.wasDown() && !tick.interrupted();
    }

    ///
    /// Creates an activation that fires once, on the tick the key has been down for the given number of ticks.
    ///
    /// The progress is the held ticks divided by the given number of ticks, capped at `1`.
    ///
    /// @param ticks The number of ticks the key has to be held.
    ///
    /// @return The activation after the hold.
    ///
    /// @throws IllegalArgumentException If the number of ticks is below `1`.
    ///
    static Activation heldFor(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        //noinspection ConstantValue Nothing enforces the range at runtime.
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }

        return new Activation() {

            ///
            /// Fires on the tick the held ticks reach the given number of ticks.
            ///
            @Override
            public boolean fires(KeyTick tick) {
                return tick.heldTicks() == ticks;
            }

            ///
            /// Gets the held ticks divided by the given number of ticks, capped at `1`.
            ///
            @Override
            public float progress(KeyTick tick) {
                return Math.min(1.0F, (float) tick.heldTicks() / ticks);
            }
        };
    }

    ///
    /// Creates an activation that fires on the tick the key goes down and then every given number of ticks while it stays down.
    ///
    /// With an interval of `n` it fires on the held ticks `1`, `1 + n`, `1 + 2n` and so on.
    ///
    /// The progress is the share of the interval that has passed since the last firing, `1` on a tick it fires and `0` while the key is up.
    ///
    /// @param intervalTicks The number of ticks between two firings.
    ///
    /// @return The repeating activation.
    ///
    /// @throws IllegalArgumentException If the interval is below `1`.
    ///
    static Activation repeat(@Range(from = 1, to = Integer.MAX_VALUE) int intervalTicks) {
        //noinspection ConstantValue Nothing enforces the range at runtime.
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("Interval '" + intervalTicks + "' is below 1");
        }

        return new Activation() {

            ///
            /// Fires while the key is down on every held tick that starts an interval.
            ///
            @Override
            public boolean fires(KeyTick tick) {
                return tick.down() && (tick.heldTicks() - 1) % intervalTicks == 0;
            }

            ///
            /// Gets the share of the interval that has passed, `1` on a tick it fires and `0` while the key is up.
            ///
            @Override
            public float progress(KeyTick tick) {
                if (this.fires(tick)) {
                    return 1.0F;
                }

                return tick.down() ? (float) ((tick.heldTicks() - 1) % intervalTicks) / intervalTicks : 0.0F;
            }
        };
    }

    ///
    /// If the key fires on the given tick.
    ///
    /// Called once per client tick for every registered key.
    ///
    /// @param tick The state of the key on this tick.
    ///
    /// @return `true` if the key fires on this tick.
    ///
    boolean fires(KeyTick tick);

    ///
    /// Gets how far the key is toward firing on the given tick, from `0` to `1`.
    ///
    /// Unless overridden, this is `1` while the key is down and `0` while it is up.
    ///
    /// @param tick The state of the key on this tick.
    ///
    /// @return The progress toward firing.
    ///
    default float progress(KeyTick tick) {
        return tick.down() ? 1.0F : 0.0F;
    }
}
