package io.github.cenksimsek1988.aviation.provider.awc;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.github.cenksimsek1988.aviation.airport.AirportProvider;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.provider.MaxAge;
import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import io.github.cenksimsek1988.aviation.provider.ProviderBrokenException;
import io.github.cenksimsek1988.aviation.provider.ProviderException;
import io.github.cenksimsek1988.aviation.provider.ProviderException.Scope;
import io.github.cenksimsek1988.aviation.provider.ProviderGuard;
import io.github.cenksimsek1988.aviation.provider.ProviderUnavailableException;
import io.github.cenksimsek1988.aviation.provider.ProviderUnreachableException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Airport lookups at aviationweather.gov, endpoint {@value #ENDPOINT}.
 *
 * <p>The HTTP call runs inside the provider's {@link ProviderGuard}, and its outcome is sorted there, so that every
 * failure counts for the circuit breaker. The answer is judged outside the guard, by {@link AwcAirportMapper}: a bad
 * value of one airport never pauses the provider.
 *
 * <table>
 *   <caption>How a provider answer is sorted</caption>
 *   <tr><th>Answer</th><th>Result</th><th>Scope</th></tr>
 *   <tr><td>200</td><td>the airport, or a bad entry</td><td></td></tr>
 *   <tr><td>204</td><td>not found</td><td></td></tr>
 *   <tr><td>no connection (refused, DNS, connect timeout)</td><td>unreachable, retried</td><td>provider</td></tr>
 *   <tr><td>429, 502, 503, 504</td><td>unavailable</td><td>provider</td></tr>
 *   <tr><td>401, 403</td><td>broken (blocked)</td><td>provider</td></tr>
 *   <tr><td>read timeout, other I/O failure, 500 and other 5xx</td><td>unavailable</td><td>endpoint</td></tr>
 *   <tr><td>404, other 4xx, other 2xx or 3xx, unreadable body, a required key missing</td><td>broken</td>
 *       <td>endpoint</td></tr>
 * </table>
 */
public class AwcAirportProvider implements AirportProvider {

    static final String ENDPOINT = "airport";

    private final AwcClient client;
    private final ProviderGuard guard;
    private final Duration defaultFreshFor;

    AwcAirportProvider(AwcClient client, ProviderGuard guard, Duration defaultFreshFor) {
        this.client = client;
        this.guard = guard;
        this.defaultFreshFor = defaultFreshFor;
        guard.register(ENDPOINT);
    }

    @Override
    public ProviderAnswer<Airport> findByIcao(IcaoCode icao) {
        ResponseEntity<List<@Nullable AwcAirport>> response = guard.call(ENDPOINT, () -> fetch(icao));
        // All Cache-Control lines, joined: HTTP allows the header to come split over several lines.
        Duration freshFor = MaxAge.from(response.getHeaders().getCacheControl(), defaultFreshFor);
        return AwcAirportMapper.toAnswer(icao, response.getBody(), freshFor);
    }

    private ResponseEntity<List<@Nullable AwcAirport>> fetch(IcaoCode icao) {
        ResponseEntity<List<@Nullable AwcAirport>> response;
        try {
            response = client.airports(icao.value(), "json");
        } catch (RestClientResponseException e) {
            throw forStatus(e.getStatusCode(), icao, e);
        } catch (ResourceAccessException e) {
            throw forIoFailure(icao, e);
        } catch (RestClientException e) {
            throw new ProviderBrokenException("aviationweather.gov sent an unreadable answer for " + icao,
                    Scope.ENDPOINT, e);
        }
        HttpStatusCode status = response.getStatusCode();
        if (status.value() != HttpStatus.OK.value() && status.value() != HttpStatus.NO_CONTENT.value()) {
            throw forStatus(status, icao, null);
        }
        return response;
    }

    static ProviderException forStatus(HttpStatusCode status, IcaoCode icao, @Nullable Throwable cause) {
        String message = "aviationweather.gov answered " + status.value() + " for " + icao;
        int code = status.value();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            return new ProviderUnavailableException(message, Scope.PROVIDER, cause);
        }
        if (code == 401 || code == 403) {
            return new ProviderBrokenException(message + " (access refused)", Scope.PROVIDER, cause);
        }
        if (status.is5xxServerError()) {
            return new ProviderUnavailableException(message, Scope.ENDPOINT, cause);
        }
        return new ProviderBrokenException(message, Scope.ENDPOINT, cause);
    }

    /** Spring wraps every I/O failure in {@link ResourceAccessException}; the cause tells whether the call got out. */
    static ProviderException forIoFailure(IcaoCode icao, ResourceAccessException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
                return new ProviderUnreachableException("aviationweather.gov could not be reached for " + icao, e);
            }
        }
        return new ProviderUnavailableException("aviationweather.gov did not answer in time for " + icao,
                Scope.ENDPOINT, e);
    }
}
