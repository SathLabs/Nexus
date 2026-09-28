package dev.satherov.nexus.test.unit.api.codec.result;

import dev.satherov.nexus.api.codec.result.CodecResult;
import dev.satherov.nexus.api.codec.result.NexusCodecException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

///
/// Checks what a success and a failure give through every method of [CodecResult].
///
public class CodecResultTest {

    private final NexusCodecException exception = new NexusCodecException("expected a number, found \"many\"");
    private final CodecResult<Integer> success = new CodecResult.Success<>(5);
    private final CodecResult<Integer> failure = new CodecResult.Failure<>(this.exception);

    @Test
    public void isSuccessOnlyForSuccess() {
        Assertions.assertThat(this.success.isSuccess()).isTrue();
        Assertions.assertThat(this.failure.isSuccess()).isFalse();
    }

    @Test
    public void orElseGivesValueOnSuccess() {
        Assertions.assertThat(this.success.orElse(7)).isEqualTo(5);
    }

    @Test
    public void orElseGivesFallbackOnFailure() {
        Assertions.assertThat(this.failure.orElse(7)).isEqualTo(7);
    }

    @Test
    public void orElseGetGivesValueWithoutCallingFallbackOnSuccess() {
        List<NexusCodecException> seen = new ArrayList<>();
        Integer value = this.success.orElseGet(held -> {
            seen.add(held);
            return 7;
        });

        Assertions.assertThat(value).isEqualTo(5);
        Assertions.assertThat(seen).isEmpty();
    }

    @Test
    public void orElseGetCallsFallbackWithHeldExceptionOnFailure() {
        List<NexusCodecException> seen = new ArrayList<>();
        Integer value = this.failure.orElseGet(held -> {
            seen.add(held);
            return 7;
        });

        Assertions.assertThat(value).isEqualTo(7);
        Assertions.assertThat(seen).singleElement().isSameAs(this.exception);
    }

    @Test
    public void orElseThrowGivesValueOnSuccess() {
        Assertions.assertThat(this.success.orElseThrow()).isEqualTo(5);
    }

    @Test
    public void orElseThrowThrowsHeldExceptionOnFailure() {
        Assertions.assertThatThrownBy(this.failure::orElseThrow).isSameAs(this.exception);
    }

    @Test
    public void mapAppliesMapperOnSuccess() {
        CodecResult<String> mapped = this.success.map(value -> "#" + value);

        Assertions.assertThat(mapped.isSuccess()).isTrue();
        Assertions.assertThat(mapped.orElseThrow()).isEqualTo("#5");
    }

    @Test
    public void mapKeepsHeldExceptionWithoutCallingMapperOnFailure() {
        List<Integer> seen = new ArrayList<>();
        CodecResult<String> mapped = this.failure.map(value -> {
            seen.add(value);
            return "#" + value;
        });

        Assertions.assertThat(mapped.isSuccess()).isFalse();
        Assertions.assertThatThrownBy(mapped::orElseThrow).isSameAs(this.exception);
        Assertions.assertThat(seen).isEmpty();
    }

    @Test
    public void ifSuccessRunsOnlyOnSuccess() {
        List<Integer> seen = new ArrayList<>();
        this.success.ifSuccess(seen::add);
        this.failure.ifSuccess(seen::add);

        Assertions.assertThat(seen).containsExactly(5);
    }

    @Test
    public void ifFailureRunsOnlyOnFailure() {
        List<NexusCodecException> seen = new ArrayList<>();
        this.success.ifFailure(seen::add);
        this.failure.ifFailure(seen::add);

        Assertions.assertThat(seen).singleElement().isSameAs(this.exception);
    }
}
