package io.github.cenksimsek1988.aviation.airport;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A validated four-letter ICAO airport code, always upper case. The rule is ICAO's own: Doc 7910 (Location
 * Indicators) defines a location indicator as "a four-letter code group formulated in accordance with rules prescribed
 * by ICAO". National identifiers with digits, such as the FAA's "1G4", are not ICAO codes.
 *
 * <p>The record checks the code when it is created, so every {@code IcaoCode} is valid, whether it came from a
 * caller or from a provider.
 *
 * <p>Validating at the edge also keeps caller input out of the provider's query: the upstream {@code ids}
 * parameter accepts lists and wildcards, which a four-letter code cannot express.
 */
public record IcaoCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{4}");

    public IcaoCode {
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidIcaoCodeException("An ICAO airport code is exactly four letters A-Z");
        }
    }

    /** Parses caller input: trims it and upper-cases it with {@link Locale#ROOT}, so a Turkish locale cannot turn "i" into "İ". */
    public static IcaoCode parse(@Nullable String raw) {
        if (raw == null) {
            throw new InvalidIcaoCodeException("An ICAO airport code is required");
        }
        return new IcaoCode(raw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
