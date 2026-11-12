package io.github.cenksimsek1988.aviation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MaxAgeTest {

    private static final Duration FALLBACK = Duration.ofMinutes(5);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "max-age=3600|3600",
            "public, max-age=60|60",
            "Max-Age=\"120\", must-revalidate|120",
            "s-maxage=10, max-age=5|5"})
    void readsMaxAge(String header, long seconds) {
        assertThat(MaxAge.from(header, FALLBACK)).isEqualTo(Duration.ofSeconds(seconds));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "no-cache", "s-maxage=10", "max-age=soon"})
    void fallsBackWithoutReadableMaxAge(String header) {
        assertThat(MaxAge.from(header, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    void cutsVeryLongMaxAge() {
        assertThat(MaxAge.from("max-age=9999999999", FALLBACK)).isEqualTo(MaxAge.LONGEST);
    }
}
