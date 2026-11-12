package io.github.cenksimsek1988.aviation.airport.api;

import io.github.cenksimsek1988.aviation.airport.AirportNotFoundException;
import io.github.cenksimsek1988.aviation.airport.InvalidIcaoCodeException;
import io.github.cenksimsek1988.aviation.provider.ProviderException;
import io.github.cenksimsek1988.aviation.requestid.RequestIdFilter;
import io.github.cenksimsek1988.aviation.security.Authorities;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns failures into RFC 9457 problem details. Each body carries the request ID, so a caller can quote it and an
 * operator can find the matching log lines. A body says only what the caller needs; the details stay in the log.
 *
 * <p>Logging: a provider failure was already logged once where it happened (WARN or ERROR, one line), so each request
 * that ends in 503 logs at DEBUG only. Only a failure of our own code logs at ERROR with a stack trace.
 *
 * <p>Each handler documents its answer ({@code @ApiResponse}); springdoc adds them to every operation of
 * {@link AirportController}.
 *
 * <p>Ordered first: Spring Boot's problem-details advice also matches exception causes, so for a provider answer
 * that failed JSON parsing it would claim the nested parse error, fail to handle the outer exception, and answer 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AirportController.class)
class AirportApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AirportApiExceptionHandler.class);

    @ExceptionHandler(InvalidIcaoCodeException.class)
    @ApiResponse(responseCode = "400", description = "Not a four-letter ICAO code")
    ResponseEntity<ProblemDetail> invalidCode(InvalidIcaoCodeException e) {
        log.debug("Rejected lookup: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Invalid ICAO code", e.getMessage()));
    }

    /** "Not found" is cached like an airport, so it carries the same caching headers. */
    @ExceptionHandler(AirportNotFoundException.class)
    @ApiResponse(responseCode = "404", description = "No airport with this code; cached like an airport", headers = {
            @Header(name = HttpHeaders.CACHE_CONTROL, description = "The provider's max-age for this answer"),
            @Header(name = HttpHeaders.AGE, description = "Seconds since the answer was fetched from the provider")})
    ResponseEntity<ProblemDetail> notFound(AirportNotFoundException e) {
        log.debug("Lookup found nothing: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.maxAge(e.freshFor()).cachePublic())
                .header(HttpHeaders.AGE, String.valueOf(e.age().toSeconds()))
                .body(problem(HttpStatus.NOT_FOUND, "Airport not found", e.getMessage()));
    }

    @ExceptionHandler(ProviderException.class)
    @ApiResponse(responseCode = "503", description = "The data provider gave no usable answer and nothing usable is "
            + "cached; try again after Retry-After seconds",
            headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"))
    ResponseEntity<ProblemDetail> providerFailed(ProviderException e) {
        log.debug("Lookup failed, answering 503: {}", e.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        Duration retryAfter = e.retryAfter();
        if (retryAfter != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(wholeSecondsUp(retryAfter)));
        }
        return response.body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Airport data provider unavailable",
                "The airport data provider gave no usable answer. Try again later."));
    }

    /** Method security refused the call: the caller lacks the endpoint's authority. */
    @ExceptionHandler(AccessDeniedException.class)
    @ApiResponse(responseCode = "403", description = "The caller lacks the authority " + Authorities.AIRPORTS_READ)
    ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException e) {
        log.debug("Lookup refused: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "Forbidden", "The caller may not read airport data."));
    }

    /** A failure of our own code: the only case logged with a stack trace, because only then it shows the bug. */
    @ExceptionHandler(RuntimeException.class)
    @ApiResponse(responseCode = "500", description = "A failure of this service itself")
    ResponseEntity<ProblemDetail> ownFailure(RuntimeException e) {
        log.error("Lookup failed in this service; answering 500", e);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                        "This service failed. Quote the request ID when you report it."));
    }

    private static long wholeSecondsUp(Duration duration) {
        long seconds = duration.toSeconds();
        return duration.toNanosPart() > 0 || seconds == 0 ? seconds + 1 : seconds;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return problem;
    }
}
