package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * A data provider could not give a usable answer. Every failure that comes from a provider is temporary for our
 * callers: the API answers 503 with {@code Retry-After}. Only a failure of our own logic is permanent (500), and it
 * is never one of these.
 *
 * <ul>
 *   <li>{@link ProviderUnavailableException}: the provider cannot answer now (timeout, 5xx, 429, a pause, our
 *       budget used up). {@link ProviderUnreachableException} is the case where the call never reached it.</li>
 *   <li>{@link ProviderBrokenException}: the provider answered in a way that needs a fix or restart on its side
 *       (changed format, blocked us, endpoint gone).</li>
 *   <li>{@link ProviderDataException}: the answer was readable, but a required value in it was bad.</li>
 * </ul>
 */
public abstract class ProviderException extends RuntimeException {

    /** Where a failure belongs: to the whole provider, or to one of its endpoints only. */
    public enum Scope {
        /** The provider as a whole: its rate limit, our access to it, its host. Pauses every endpoint. */
        PROVIDER,
        /** One endpoint of the provider. Pauses that endpoint only. */
        ENDPOINT
    }

    private final Scope scope;
    private @Nullable Duration retryAfter;

    protected ProviderException(String message, Scope scope, @Nullable Duration retryAfter,
                                @Nullable Throwable cause) {
        super(message, cause);
        this.scope = scope;
        this.retryAfter = retryAfter;
    }

    public Scope scope() {
        return scope;
    }

    /** How long a caller should wait before trying again, or null when unknown. */
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    /** Set by {@link ProviderGuard}, which knows how long the provider stays paused. */
    void retryAfter(Duration retryAfter) {
        this.retryAfter = retryAfter;
    }
}
