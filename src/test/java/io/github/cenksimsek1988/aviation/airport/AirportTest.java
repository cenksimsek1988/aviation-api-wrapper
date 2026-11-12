package io.github.cenksimsek1988.aviation.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AirportTest {

    @ParameterizedTest
    @CsvSource({"90.1, 0", "-90.1, 0", "0, 180.1", "0, -180.1", "NaN, 0", "0, NaN"})
    void locationOutsideEarthIsRejected(double latitude, double longitude) {
        assertThatThrownBy(() -> new Airport.Location(latitude, longitude, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locationOnTheEdgesIsAccepted() {
        assertThat(new Airport.Location(-90, 180, null).latitude()).isEqualTo(-90);
    }

    @Test
    void quantityNeedsAUnit() {
        assertThatThrownBy(() -> new Quantity(BigDecimal.ONE, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
