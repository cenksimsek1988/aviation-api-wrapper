package io.github.cenksimsek1988.aviation.provider.awc;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.airport.Quantity;
import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates the aviationweather.gov wire model into the service's {@link Airport}, and judges each value.
 *
 * <ul>
 *   <li>Required values (ICAO code, latitude, longitude): if one is missing, unreadable or invalid, the whole answer
 *       is {@link ProviderAnswer.Bad}: it is cached as a bad entry and callers get 503 until it expires.</li>
 *   <li>Optional values (IATA code, name, region, country, elevation, runway size and surface): an unreadable one
 *       becomes null, with a warning, and the airport is still returned.</li>
 * </ul>
 *
 * <p>Units come from the provider's documentation, not from the data: {@code elev} is metres, a runway
 * {@code dimension} is feet. A change of unit at the provider is handled here, and only here.
 */
final class AwcAirportMapper {

    static final String METRES = "m";
    static final String FEET = "ft";

    private static final Logger log = LoggerFactory.getLogger(AwcAirportMapper.class);
    /** Up to 9 digits each: a longer number is not a runway size, and it is treated as unreadable. */
    private static final Pattern DIMENSION = Pattern.compile("(\\d{1,9})\\s*x\\s*(\\d{1,9})");

    private AwcAirportMapper() {
    }

    /** Judges the provider's answer to the lookup of {@code asked}. */
    static ProviderAnswer<Airport> toAnswer(IcaoCode asked, @Nullable List<@Nullable AwcAirport> found,
                                            Duration freshFor) {
        if (found == null || found.isEmpty()) {
            return new ProviderAnswer.NotFound<>(freshFor);
        }
        if (found.size() > 1) {
            log.warn("aviationweather.gov sent {} airports for {}; using the one with that code", found.size(), asked);
        }
        AwcAirport match = found.stream()
                .filter(Objects::nonNull)
                .filter(airport -> airport.icaoId() != null && asked.value().equalsIgnoreCase(airport.icaoId().strip()))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return new ProviderAnswer.Bad<>("the answer holds no airport with code " + asked, freshFor);
        }
        try {
            return new ProviderAnswer.Found<>(toAirport(match), freshFor);
        } catch (IllegalArgumentException e) {
            return new ProviderAnswer.Bad<>(e.getMessage(), freshFor);
        }
    }

    private static Airport toAirport(AwcAirport wire) {
        IcaoCode icao = IcaoCode.parse(wire.icaoId());
        Airport.Location location = new Airport.Location(
                requiredNumber(wire.lat(), "latitude", icao).doubleValue(),
                requiredNumber(wire.lon(), "longitude", icao).doubleValue(),
                elevation(wire.elev(), icao));
        return new Airport(
                icao,
                clean(wire.iataId()),
                clean(wire.name()),
                clean(wire.state()),
                clean(wire.country()),
                location,
                toRunways(wire.runways(), icao));
    }

    private static List<Airport.Runway> toRunways(@Nullable List<AwcAirport.@Nullable Runway> runways, IcaoCode icao) {
        if (runways == null) {
            return List.of();
        }
        List<Airport.Runway> mapped = new ArrayList<>(runways.size());
        for (AwcAirport.Runway wire : runways) {
            if (wire == null) {
                log.warn("aviationweather.gov sent an empty runway entry for {}; skipped", icao);
                continue;
            }
            mapped.add(toRunway(wire, icao));
        }
        return mapped;
    }

    private static Airport.Runway toRunway(AwcAirport.Runway wire, IcaoCode icao) {
        String dimension = clean(wire.dimension());
        Matcher size = DIMENSION.matcher(dimension == null ? "" : dimension.toLowerCase(Locale.ROOT));
        boolean known = size.matches();
        if (!known && dimension != null) {
            log.warn("aviationweather.gov sent an unreadable runway size '{}' for {} runway {}; left empty",
                    loggable(dimension), icao, loggable(wire.id()));
        }
        return new Airport.Runway(
                clean(wire.id()),
                known ? new Quantity(new BigDecimal(size.group(1)), FEET) : null,
                known ? new Quantity(new BigDecimal(size.group(2)), FEET) : null,
                clean(wire.surface()));
    }

    private static BigDecimal requiredNumber(@Nullable String value, String field, IcaoCode icao) {
        BigDecimal number = number(value);
        if (number == null) {
            throw new IllegalArgumentException(
                    "airport " + icao + " has no readable " + field + ": " + loggable(value));
        }
        return number;
    }

    /** Elevation in metres; an unreadable value is left empty, with a warning. */
    private static @Nullable Quantity elevation(@Nullable String value, IcaoCode icao) {
        BigDecimal metres = number(value);
        if (metres == null && clean(value) != null) {
            log.warn("aviationweather.gov sent an unreadable elevation '{}' for {}; left empty", loggable(value), icao);
        }
        return metres == null ? null : new Quantity(metres, METRES);
    }

    /** The number in {@code value}, with its digits as sent; null when there is none. */
    private static @Nullable BigDecimal number(@Nullable String value) {
        try {
            return value == null ? null : new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Trims padding (for example "JOHN F KENNEDY INTL "); the provider writes a missing value as null, "" or "-". */
    private static @Nullable String clean(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    /** Provider text as it may appear in a log line: control characters become '?', at most 64 characters. */
    private static String loggable(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        String safe = value.replaceAll("\\p{Cntrl}", "?");
        return safe.length() <= 64 ? safe : safe.substring(0, 64) + "...";
    }
}
