package io.github.cenksimsek1988.aviation.airport;

/** The caller sent something that is not a four-letter ICAO code. */
public class InvalidIcaoCodeException extends IllegalArgumentException {

    public InvalidIcaoCodeException(String message) {
        super(message);
    }
}
