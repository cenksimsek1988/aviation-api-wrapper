package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * The provider cannot answer now: a read timeout, HTTP 5xx, HTTP 429, a pause of its circuit breaker, or our own
 * request budget used up. Trying again later can succeed.
 */
public class ProviderUnavailableException extends ProviderException {

    public ProviderUnavailableException(String message, Scope scope, @Nullable Throwable cause) {
        super(message, scope, null, cause);
    }

    public ProviderUnavailableException(String message, Scope scope, Duration retryAfter) {
        super(message, scope, retryAfter, null);
    }
}
