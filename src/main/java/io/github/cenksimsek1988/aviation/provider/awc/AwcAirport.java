package io.github.cenksimsek1988.aviation.provider.awc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One element of the JSON array from {@code GET /api/data/airport?ids=<ICAO>&format=json}. Only the fields this
 * service maps are declared; the provider sends more (frequencies, services, tower...), and new fields never break us.
 *
 * <p>The keys we cannot do without are marked {@code required}: if the provider renames or drops one, the whole
 * answer is unreadable, which counts as a broken provider. Any value may still be null: this is the provider's data,
 * judged by {@link AwcAirportMapper}.
 *
 * <p>The numbers are strings: the provider's OpenAPI document declares them as strings, but it sends JSON numbers,
 * and a {@code String} reads both with the digits as sent. So one value that is not a number is a bad value of one
 * airport, not an unreadable answer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AwcAirport(
        @JsonProperty(required = true) @Nullable String icaoId,
        @Nullable String iataId,
        @Nullable String name,
        @Nullable String state,
        @Nullable String country,
        @JsonProperty(required = true) @Nullable String lat,
        @JsonProperty(required = true) @Nullable String lon,
        @Nullable String elev,
        @Nullable List<@Nullable Runway> runways) {

    /** {@code dimension} is "length x width" in feet, for example "9196x148". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Runway(@Nullable String id, @Nullable String dimension, @Nullable String surface) {
    }
}
