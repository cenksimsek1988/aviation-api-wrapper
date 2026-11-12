package io.github.cenksimsek1988.aviation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderLimitTest {

    @ParameterizedTest
    @CsvSource({"100, 50", "17, 8", "2, 1", "1, 1"})
    void usesHalfTheProvidersLimit(int calls, int ours) {
        assertThat(new ProviderLimit(calls, Duration.ofMinutes(1)).callsPerBlock()).isEqualTo(ours);
    }

    @Test
    void rejectsLimitsThatAllowNothing() {
        assertThatThrownBy(() -> new ProviderLimit(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderLimit(100, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
