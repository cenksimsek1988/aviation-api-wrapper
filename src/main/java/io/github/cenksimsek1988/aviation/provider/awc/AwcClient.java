package io.github.cenksimsek1988.aviation.provider.awc;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The aviationweather.gov data API (Aviation Weather Center, AWC), declared as a Spring HTTP interface. Spring Boot
 * builds the client from the HTTP service group {@value AwcConfig#GROUP}: base URL, timeouts and the User-Agent come
 * from {@code spring.http.serviceclient.awc.*}. Another AWC endpoint is one more method here.
 *
 * <p>It returns the whole {@link ResponseEntity}: the adapter needs the status (204 means "not found") and the
 * {@code Cache-Control} header (how long the answer stays fresh). Error statuses arrive as Spring's
 * {@code RestClientResponseException}; the adapter sorts them.
 */
@HttpExchange(url = "/api/data", accept = MediaType.APPLICATION_JSON_VALUE)
interface AwcClient {

    /** {@code GET /api/data/airport?ids=<ICAO>&format=json}: a JSON array, or 204 when no airport matches. */
    @GetExchange("/airport")
    ResponseEntity<List<@Nullable AwcAirport>> airports(@RequestParam("ids") String ids, @RequestParam("format") String format);
}
