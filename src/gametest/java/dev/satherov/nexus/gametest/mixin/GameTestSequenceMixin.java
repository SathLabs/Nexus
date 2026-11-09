package dev.satherov.nexus.gametest.mixin;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

///
/// Turns an [AssertionError] thrown in a sequence step into a failed vanilla assertion.
///
/// Any other [Error] passes through to [GameTestInfoMixin].
///
@Mixin(GameTestSequence.class)
public abstract class GameTestSequenceMixin {
    
    ///
    /// The test that the sequence belongs to.
    ///
    @Shadow
    @Final
    private GameTestInfo parent;
    
    ///
    /// Rethrows an [AssertionError] that the given assertion throws as a [GameTestAssertException] with the same message,
    /// the current tick, and the error as its cause.
    ///
    /// The sequence then handles it like a failed vanilla assertion:
    /// - A step of `succeedWhen`, `succeedIf`, `failIf`, or `thenWaitUntil` is retried until it passes or the test times out.
    /// - A step of `thenExecute` fails the test at once.
    ///
    /// @param assertion The assertion of the sequence step.
    /// @param original  The wrapped `run` of the assertion.
    ///
    @WrapOperation(
            method = { "tick", "executeWithoutFail" },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Runnable;run()V"
            )
    )
    private void failAsAssertion(Runnable assertion, Operation<Void> original) {
        try {
            original.call(assertion);
        } catch (AssertionError error) {
            Component message = Component.literal(Objects.requireNonNullElseGet(error.getMessage(), error::toString));
            GameTestAssertException failure = new GameTestAssertException(message, ((GameTestInfoAccess) this.parent).invokeGetTick());
            failure.initCause(error); // Legal because GameTestException(String) doesn't set a cause
            throw failure;
        }
    }
}
