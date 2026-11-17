package dev.satherov.nexus.gametest.mixin;

import net.minecraft.gametest.framework.GameTestException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.UnknownGameTestException;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.Objects;

///
/// Fails a server test with a message, whatever its test function, a scheduled callback, or a sequence throws.
///
/// Vanilla only catches exceptions around the test function and the callbacks, and only failed assertions around a sequence,
/// so anything else would crash the game test server. A failure without a message would crash it as well once it is logged.
///
@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {
    
    ///
    /// The sequences of the test that are still ticking.
    ///
    @Shadow
    @Final
    public Collection<GameTestSequence> sequences;
    
    ///
    /// Creates a new [UnknownGameTestException] with the message of the given throwable and the throwable as its cause.
    ///
    /// A throwable without a message is described by its class name instead.
    /// A [GameTestException] already fails the test as it is, so it is returned unchanged.
    ///
    /// @param thrown The throwable that the test threw.
    ///
    /// @return The exception to fail the test with.
    ///
    private static GameTestException failure(Throwable thrown) {
        if (thrown instanceof GameTestException exception) {
            return exception;
        }
        
        // The description of the failure reads the message of its reason, and NeoForge throws if that is null.
        String message = Objects.requireNonNullElseGet(thrown.getMessage(), thrown::toString);
        UnknownGameTestException failure = new UnknownGameTestException(new RuntimeException(message, thrown));
        failure.initCause(thrown); // Legal because GameTestException(String) doesn't set a cause
        return failure;
    }
    
    ///
    /// Fails the test with the given exception.
    ///
    /// @param error The exception that the test fails with.
    ///
    @Shadow
    public abstract void fail(GameTestException error);
    
    ///
    /// Rethrows anything but a [GameTestException] that the test function throws as an [UnknownGameTestException].
    ///
    /// @param test     The test that is started.
    /// @param helper   The helper that the test function receives.
    /// @param original The wrapped `run` of the test.
    ///
    @WrapOperation(
            method = "startTest",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/gametest/framework/GameTestInstance;run(Lnet/minecraft/gametest/framework/GameTestHelper;)V"
            )
    )
    private void failTestOnThrowable(GameTestInstance test, GameTestHelper helper, Operation<Void> original) {
        try {
            original.call(test, helper);
        } catch (Throwable thrown) {
            throw GameTestInfoMixin.failure(thrown);
        }
    }
    
    ///
    /// Rethrows anything but a [GameTestException] that the given callback throws as an [UnknownGameTestException].
    ///
    /// Applies to every callback scheduled with `runAtTickTime`, `runAfterDelay`, `runBeforeTestEnd`, `onEachTick`, or `failIfEver`.
    ///
    /// Vanilla then fails the test and removes the callback, so it won't run again.
    ///
    /// @param callback The callback that is due on this tick.
    /// @param original The wrapped `run` of the callback.
    ///
    @WrapOperation(
            method = "tickInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Runnable;run()V"
            )
    )
    private void failCallbackOnThrowable(Runnable callback, Operation<Void> original) {
        try {
            original.call(callback);
        } catch (Throwable thrown) {
            throw GameTestInfoMixin.failure(thrown);
        }
    }
    
    ///
    /// Fails the test if anything is thrown while the given sequence ticks.
    ///
    /// Vanilla catches a failed assertion inside the sequence, so only what it lets through gets here.
    /// The sequence will be replaced with an empty one, so it won't run again on the tick the test finishes.
    /// It isn't removed, since vanilla fails a test that is past its timeout with no sequence left as timed out,
    /// which would replace the failure on the tick the test finishes.
    ///
    /// Both lambdas of `tickInternal` are targets, so the injection does not depend on how they are numbered.
    /// Requires both of its targets, so the harness fails to load if one of them is renamed.
    ///
    /// @param sequence The sequence that ticks.
    /// @param tick     The tick of the test.
    /// @param original The wrapped tick of the sequence.
    ///
    @WrapOperation(
            method = { "lambda$tickInternal$0", "lambda$tickInternal$1" },
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/gametest/framework/GameTestSequence;tickAndFailIfNotComplete(I)V"
                    ),
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/gametest/framework/GameTestSequence;tickAndContinue(I)V"
                    )
            },
            require = 2
    )
    private void failSequenceOnThrowable(GameTestSequence sequence, int tick, Operation<Void> original) {
        try {
            original.call(sequence, tick);
        } catch (Throwable thrown) {
            this.fail(GameTestInfoMixin.failure(thrown));
            this.sequences.remove(sequence);
            this.sequences.add(new GameTestSequence((GameTestInfo) (Object) this));
        }
    }
}
