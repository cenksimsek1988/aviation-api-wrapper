package io.github.cenksimsek1988.aviation.provider;

import org.jspecify.annotations.Nullable;

/**
 * The call never reached the provider: the connection was refused or timed out, or the host name did not resolve.
 * This is the only failure we retry at once: the provider never saw the call, so a retry cannot count twice against
 * its rate limit.
 */
public class ProviderUnreachableException extends ProviderUnavailableException {

    public ProviderUnreachableException(String message, @Nullable Throwable cause) {
        super(message, Scope.PROVIDER, cause);
    }
}
