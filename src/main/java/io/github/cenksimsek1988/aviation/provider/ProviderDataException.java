package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;

/**
 * The provider's answer was readable, but a required value in it was bad, for one record only: for example an airport
 * without a valid position. The answer is cached as a bad entry for the provider's fresh time, so the provider is not
 * asked again meanwhile; callers get 503 until it expires. It never pauses the provider.
 */
public class ProviderDataException extends ProviderException {

    public ProviderDataException(String message, Duration retryAfter) {
        super(message, Scope.ENDPOINT, retryAfter, null);
    }
}
