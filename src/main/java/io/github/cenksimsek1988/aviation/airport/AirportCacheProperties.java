package io.github.cenksimsek1988.aviation.airport;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local airport cache. How long an entry stays fresh is not set here: it is the provider's own
 * {@code Cache-Control: max-age}, per answer.
 *
 * @param maxSize      entries kept at most; the least used go first (about 1 KB each)
 * @param keepStaleFor how long after it stops being fresh a found airport may still be served while every refresh
 *                     fails; then it is dropped
 */
@ConfigurationProperties("airport.cache")
public record AirportCacheProperties(long maxSize, Duration keepStaleFor) {
}
