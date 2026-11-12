package io.github.cenksimsek1988.aviation.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs every call to one provider through the same protection, with Resilience4j.
 *
 * <ul>
 *   <li>One circuit breaker per endpoint ({@code <provider>-<endpoint>}, settings in
 *       {@code resilience4j.circuitbreaker.configs.default}): one failure pauses the endpoint for the provider's
 *       rate-limit period; after the pause, the next call is the test. A failure of the whole provider
 *       ({@link ProviderException.Scope#PROVIDER}) pauses all its endpoints.</li>
 *   <li>One retry per provider ({@code resilience4j.retry.instances.<provider>}): only for
 *       {@link ProviderUnreachableException}, a call the provider never saw.</li>
 *   <li>One rate limiter per provider, because the provider's limit counts all its endpoints together: half the
 *       published limit per period (see {@link ProviderLimit}). It never waits: a call over the budget fails at once.</li>
 * </ul>
 *
 * <p>Order, outside in: circuit breaker, retry, rate limiter, call. The retries of one call are one outcome for the
 * breaker, and each attempt takes a slot of the budget.
 *
 * <p>Every {@link ProviderException} leaving here carries {@link ProviderException#retryAfter()}: the whole pause,
 * or 1 s ({@code MIN_RETRY_AFTER}) when the endpoint was not paused. A client that waits that long finds the endpoint
 * tried again and the budget refilled.
 */
public final class ProviderGuard {

    private static final Logger log = LoggerFactory.getLogger(ProviderGuard.class);
    private static final Duration MIN_RETRY_AFTER = Duration.ofSeconds(1);

    private final String provider;
    private final Duration pause;
    private final CircuitBreakerRegistry breakers;
    private final CircuitBreakerConfig breakerConfig;
    private final Retry retry;
    private final RateLimiter limiter;
    private final Map<String, CircuitBreaker> endpoints = new ConcurrentHashMap<>();

    public ProviderGuard(String provider, ProviderLimit limit, CircuitBreakerRegistry breakers, RetryRegistry retries,
                         RateLimiterRegistry limiters) {
        this.provider = provider;
        this.pause = limit.period();
        this.breakers = breakers;
        this.breakerConfig = CircuitBreakerConfig.from(breakers.getDefaultConfig()).waitDurationInOpenState(pause).build();
        this.retry = retries.retry(provider);
        this.limiter = limiters.rateLimiter(provider, RateLimiterConfig.custom()
                .limitForPeriod(limit.callsPerBlock())
                .limitRefreshPeriod(limit.period())
                .timeoutDuration(Duration.ZERO)
                .build());
        retry.getEventPublisher().onRetry(event -> log.debug("Provider '{}': attempt {} could not connect ({}); retrying in {} ms",
                provider, event.getNumberOfRetryAttempts(), event.getLastThrowable(),
                event.getWaitInterval().toMillis()));
    }

    /** Creates the endpoint's circuit breaker now, so a pause of the whole provider reaches it before its first call. */
    public void register(String endpoint) {
        breaker(endpoint);
    }

    /** Runs {@code call} for {@code endpoint}; any failure leaves as a {@link ProviderException}. */
    public <T> T call(String endpoint, Supplier<T> call) {
        CircuitBreaker breaker = breaker(endpoint);
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker,
                Retry.decorateSupplier(retry, RateLimiter.decorateSupplier(limiter, call)));
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            log.debug("Provider '{}' endpoint '{}' is paused; call turned away", provider, endpoint);
            throw new ProviderUnavailableException("Provider '" + provider + "' endpoint '" + endpoint + "' is paused",
                    ProviderException.Scope.ENDPOINT, pause);
        } catch (RequestNotPermitted e) {
            log.debug("Provider '{}': request budget used up; call turned away", provider);
            throw new ProviderUnavailableException("Request budget of provider '" + provider + "' is used up",
                    ProviderException.Scope.PROVIDER, pause);
        } catch (ProviderException e) {
            onFailure(endpoint, breaker, e);
            throw e;
        }
    }

    private void onFailure(String endpoint, CircuitBreaker breaker, ProviderException e) {
        boolean wholeProvider = e.scope() == ProviderException.Scope.PROVIDER;
        if (wholeProvider) {
            endpoints.values().stream()
                    .filter(other -> other.getState() == CircuitBreaker.State.CLOSED
                            || other.getState() == CircuitBreaker.State.HALF_OPEN)
                    .forEach(CircuitBreaker::transitionToOpenState);
        }
        boolean paused = breaker.getState() == CircuitBreaker.State.OPEN;
        String what = wholeProvider ? "Provider '" + provider + "' (all endpoints)"
                : "Provider '" + provider + "' endpoint '" + endpoint + "'";
        String outcome = paused ? " paused for " + pause.toSeconds() + " s: " : " failed: ";
        String line = what + outcome + describe(e);
        // A broken provider needs a person; anything else the app rides out alone. One line, no stack trace.
        if (e instanceof ProviderBrokenException) {
            log.error(line);
        } else {
            log.warn(line);
        }
        e.retryAfter(paused ? pause : MIN_RETRY_AFTER);
    }

    private CircuitBreaker breaker(String endpoint) {
        return endpoints.computeIfAbsent(endpoint, name -> {
            CircuitBreaker breaker = breakers.circuitBreaker(provider + "-" + name, breakerConfig);
            breaker.getEventPublisher().onStateTransition(event -> {
                CircuitBreaker.State to = event.getStateTransition().getToState();
                if (to == CircuitBreaker.State.CLOSED) {
                    log.info("Provider '{}' endpoint '{}' answers again", provider, name);
                } else {
                    log.debug("Provider '{}' endpoint '{}' is now {}", provider, name, to);
                }
            });
            return breaker;
        });
    }

    private static String describe(ProviderException e) {
        Throwable cause = e.getCause();
        return cause == null ? e.getMessage()
                : e.getMessage() + " (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")";
    }
}
