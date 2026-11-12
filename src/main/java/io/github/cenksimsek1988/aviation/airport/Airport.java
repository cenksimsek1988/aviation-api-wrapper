package io.github.cenksimsek1988.aviation.airport;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An airport as this service knows it, independent of any provider's wire format and of the API's JSON.
 *
 * <p>The record checks what it can: the ICAO code is a valid {@link IcaoCode} and the position lies on Earth. So
 * an {@code Airport} is valid wherever it is used, also when its data came from a provider.
 *
 * <p>It carries no provider name. Which provider answered is an internal detail: it is in the logs and metrics, not
 * in the data callers get, and callers do not depend on it.
 *
 * @param icao     four-letter ICAO code
 * @param iata     three-letter IATA code, or null when the airport has none
 * @param name     name as the provider spells it, or null
 * @param region   state or province code, or null
 * @param country  country code as the provider sends it (ISO 3166-1 alpha-2 for aviationweather.gov), or null
 * @param location position and elevation
 * @param runways  runways; empty, never null, when the provider lists none
 */
public record Airport(
        IcaoCode icao,
        @Nullable String iata,
        @Nullable String name,
        @Nullable String region,
        @Nullable String country,
        Location location,
        List<Runway> runways) {

    public Airport {
        Objects.requireNonNull(icao, "icao");
        Objects.requireNonNull(location, "location");
        runways = List.copyOf(runways);
    }

    /**
     * Position in WGS84 degrees. Latitude is checked to be within -90..90 and longitude within -180..180, so callers
     * never need to check them again.
     *
     * @param latitude  degrees north (negative: south)
     * @param longitude degrees east (negative: west)
     * @param elevation field elevation above mean sea level, or null when the provider gives none
     */
    public record Location(double latitude, double longitude, @Nullable Quantity elevation) {

        public Location {
            if (!(latitude >= -90 && latitude <= 90)) {
                throw new IllegalArgumentException("Latitude must be within -90..90 degrees: " + latitude);
            }
            if (!(longitude >= -180 && longitude <= 180)) {
                throw new IllegalArgumentException("Longitude must be within -180..180 degrees: " + longitude);
            }
        }
    }

    /**
     * One runway.
     *
     * @param designator both ends, for example "12/30", or null when the provider gives none
     * @param length     length, or null when the provider gives none or an unreadable one
     * @param width      width, or null when the provider gives none or an unreadable one
     * @param surface    the provider's surface code, for example "H" for hard, or null
     */
    public record Runway(@Nullable String designator, @Nullable Quantity length, @Nullable Quantity width,
                         @Nullable String surface) {
    }
}
