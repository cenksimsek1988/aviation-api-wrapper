package io.github.cenksimsek1988.aviation.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IcaoCodeTest {

    @Test
    void parseTrimsAndUpperCases() {
        assertThat(IcaoCode.parse(" lipe ").value()).isEqualTo("LIPE");
    }

    @Test
    void parseIgnoresTurkishDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            // With the default locale, "i".toUpperCase() would be the dotted "İ" and fail validation.
            assertThat(IcaoCode.parse("limc").value()).isEqualTo("LIMC");
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "LIP", "LIPEX", "LIP1", "LI E", "KJFK,EGLL", "K*", "ÄBCD"})
    void parseRejectsAnythingButFourLetters(String raw) {
        assertThatThrownBy(() -> IcaoCode.parse(raw)).isInstanceOf(InvalidIcaoCodeException.class);
    }
}
