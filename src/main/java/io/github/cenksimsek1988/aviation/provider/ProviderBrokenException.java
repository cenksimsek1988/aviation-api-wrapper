package io.github.cenksimsek1988.aviation.provider;

import org.jspecify.annotations.Nullable;

/**
 * The provider answered in a way that needs a fix or a restart on its side, or a change on ours: its JSON no longer
 * has a key we need, the body cannot be read at all, it refuses us (401, 403), the endpoint is gone (404), or another
 * unexpected status. Retrying at once cannot help; a person has to look.
 */
public class ProviderBrokenException extends ProviderException {

    public ProviderBrokenException(String message, Scope scope, @Nullable Throwable cause) {
        super(message, scope, null, cause);
    }
}
