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

///
/// Fails a server test if its test function, one of its scheduled callbacks, or one of its sequences throws an [Error].
///
/// Vanilla only catches exceptions around all three, so an error would crash the game test server.
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
    /// Creates a new [UnknownGameTestException] with the message of the given error and the error as its cause.
    ///
    /// @param error The error that the test threw.
    ///
    /// @return The exception to fail the test with.
    ///
    private static UnknownGameTestException failure(Error error) {
        UnknownGameTestException failure = new UnknownGameTestException(error);
        failure.initCause(error); // Legal because GameTestException(String) doesn't set a cause
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
    /// Rethrows an [Error] that the test function throws as an [UnknownGameTestException].
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
    private void failTestOnError(GameTestInstance test, GameTestHelper helper, Operation<Void> original) {
        try {
            original.call(test, helper);
        } catch (Error error) {
            throw GameTestInfoMixin.failure(error);
        }
    }
    
    ///
    /// Rethrows an [Error] that the given callback throws as an [UnknownGameTestException].
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
    private void failCallbackOnError(Runnable callback, Operation<Void> original) {
        try {
            original.call(callback);
        } catch (Error error) {
            throw GameTestInfoMixin.failure(error);
        }
    }
    
    ///
    /// Fails the test with an [UnknownGameTestException] if an [Error] is thrown while the given sequence ticks.
    ///
    /// The sequence will be removed, so it won't run again on the tick the test finishes.
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
            }
    )
    private void failSequenceOnError(GameTestSequence sequence, int tick, Operation<Void> original) {
        try {
            original.call(sequence, tick);
        } catch (Error error) {
            this.fail(GameTestInfoMixin.failure(error));
            this.sequences.remove(sequence);
        }
    }
}
