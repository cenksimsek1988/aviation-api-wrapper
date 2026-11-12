package io.github.cenksimsek1988.aviation.airport;

import java.time.Duration;

/**
 * The provider knows no airport with this code. The answer is cached like any other, so it carries its age and fresh
 * time for the HTTP caching headers.
 */
public class AirportNotFoundException extends RuntimeException {

    private final Duration age;
    private final Duration freshFor;

    public AirportNotFoundException(IcaoCode icao, Duration age, Duration freshFor) {
        super("No airport with ICAO code " + icao);
        this.age = age;
        this.freshFor = freshFor;
    }

    public Duration age() {
        return age;
    }

    public Duration freshFor() {
        return freshFor;
    }
}
