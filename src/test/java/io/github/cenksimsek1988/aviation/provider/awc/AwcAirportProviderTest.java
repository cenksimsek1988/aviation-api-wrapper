package io.github.cenksimsek1988.aviation.provider.awc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.provider.ProviderException;
import io.github.cenksimsek1988.aviation.provider.ProviderException.Scope;
import io.github.cenksimsek1988.aviation.provider.ProviderUnavailableException;
import io.github.cenksimsek1988.aviation.provider.ProviderUnreachableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;

/** How a provider answer is sorted; the whole path over HTTP is in AirportApiIT. */
class AwcAirportProviderTest {

    private static final IcaoCode LIPE = IcaoCode.parse("LIPE");

    @ParameterizedTest
    @CsvSource({
            "429, ProviderUnavailableException, PROVIDER",
            "502, ProviderUnavailableException, PROVIDER",
            "503, ProviderUnavailableException, PROVIDER",
            "504, ProviderUnavailableException, PROVIDER",
            "401, ProviderBrokenException, PROVIDER",
            "403, ProviderBrokenException, PROVIDER",
            "500, ProviderUnavailableException, ENDPOINT",
            "501, ProviderUnavailableException, ENDPOINT",
            "404, ProviderBrokenException, ENDPOINT",
            "400, ProviderBrokenException, ENDPOINT",
            "301, ProviderBrokenException, ENDPOINT",
            "202, ProviderBrokenException, ENDPOINT"})
    void sortsStatus(int status, String kind, Scope scope) {
        ProviderException failure = AwcAirportProvider.forStatus(HttpStatusCode.valueOf(status), LIPE, null);

        assertThat(failure.getClass().getSimpleName()).isEqualTo(kind);
        assertThat(failure.scope()).isEqualTo(scope);
    }

    @Test
    void callThatNeverGotOutIsUnreachable() {
        assertThat(io(new ConnectException("refused"))).isInstanceOf(ProviderUnreachableException.class);
        assertThat(io(new HttpConnectTimeoutException("connect timed out")))
                .isInstanceOf(ProviderUnreachableException.class);
    }

    @Test
    void callWithoutAnswerInTimeIsUnavailableForTheEndpoint() {
        for (IOException cause : new IOException[] {new HttpTimeoutException("timed out"), new IOException("reset")}) {
            ProviderException failure = io(cause);
            assertThat(failure).isExactlyInstanceOf(ProviderUnavailableException.class);
            assertThat(failure.scope()).isEqualTo(Scope.ENDPOINT);
        }
    }

    private static ProviderException io(IOException cause) {
        return AwcAirportProvider.forIoFailure(LIPE, new ResourceAccessException("I/O error", cause));
    }
}
