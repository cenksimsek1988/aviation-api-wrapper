package io.github.cenksimsek1988.aviation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cenksimsek1988.aviation.provider.ProviderException.Scope;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The guard with Resilience4j set up as in application.properties, but with short waits. */
class ProviderGuardTest {

    private static final Duration PERIOD = Duration.ofSeconds(60);

    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .failureRateThreshold(100)
            .permittedNumberOfCallsInHalfOpenState(1)
            .recordExceptions(ProviderUnavailableException.class, ProviderBrokenException.class)
            .ignoreExceptions(RequestNotPermitted.class)
            .build());
    private final RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(ProviderUnreachableException.class)
            .build());
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void endpointFailurePausesOnlyThatEndpoint() {
        ProviderGuard guard = guard(new ProviderLimit(1000, PERIOD), "a", "b");

        assertThatThrownBy(() -> guard.call("a", failing(endpointFailure())))
                .isInstanceOfSatisfying(ProviderUnavailableException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(PERIOD));
        assertThatThrownBy(() -> guard.call("a", counting("never")))
                .isInstanceOfSatisfying(ProviderUnavailableException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(PERIOD));

        assertThat(guard.call("b", counting("answer"))).isEqualTo("answer");
        assertThat(calls).as("the failing call and the call to b; the paused call never ran").hasValue(2);
    }

    @Test
    void providerFailurePausesEveryEndpoint() {
        ProviderGuard guard = guard(new ProviderLimit(1000, PERIOD), "a", "b");

        assertThatThrownBy(() -> guard.call("a", failing(
                new ProviderUnavailableException("429", Scope.PROVIDER, (Throwable) null))))
                .isInstanceOf(ProviderUnavailableException.class);

        assertThatThrownBy(() -> guard.call("b", counting("never"))).isInstanceOf(ProviderUnavailableException.class);
        assertThat(calls).as("only the failing call ran").hasValue(1);
        assertThat(breakers.circuitBreaker("test-b").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void onlyACallThatNeverGotOutIsRetried() {
        ProviderGuard unreachable = guard(new ProviderLimit(1000, PERIOD), "a");
        ProviderGuard failing = new ProviderGuard("other", new ProviderLimit(1000, PERIOD), breakers, retries,
                RateLimiterRegistry.ofDefaults());

        assertThatThrownBy(() -> unreachable.call("a", failing(new ProviderUnreachableException("refused", null))))
                .isInstanceOf(ProviderUnreachableException.class);
        assertThat(calls).as("first attempt and two retries").hasValue(3);

        calls.set(0);
        assertThatThrownBy(() -> failing.call("a", failing(endpointFailure())))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(calls).as("no retry").hasValue(1);
    }

    @Test
    void successfulRetryDoesNotPause() {
        ProviderGuard guard = guard(new ProviderLimit(1000, PERIOD), "a");

        String answer = guard.call("a", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ProviderUnreachableException("refused", null);
            }
            return "answer";
        });

        assertThat(answer).isEqualTo("answer");
        assertThat(guard.call("a", counting("again"))).isEqualTo("again");
    }

    @Test
    void callOverBudgetFailsAtOnceAndDoesNotPause() {
        ProviderGuard guard = guard(new ProviderLimit(4, PERIOD), "a");

        guard.call("a", counting("1"));
        guard.call("a", counting("2"));

        assertThatThrownBy(() -> guard.call("a", counting("3")))
                .isInstanceOfSatisfying(ProviderUnavailableException.class, e -> {
                    assertThat(e.scope()).isEqualTo(Scope.PROVIDER);
                    assertThat(e.retryAfter()).isEqualTo(PERIOD);
                });
        assertThat(calls).hasValue(2);
        assertThat(breakers.circuitBreaker("test-a").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void afterThePauseTheNextCallIsTheTest() throws InterruptedException {
        ProviderGuard guard = guard(new ProviderLimit(1000, Duration.ofMillis(200)), "a");
        assertThatThrownBy(() -> guard.call("a", failing(endpointFailure())))
                .isInstanceOf(ProviderUnavailableException.class);

        Thread.sleep(300);

        assertThat(guard.call("a", counting("back"))).isEqualTo("back");
        assertThat(breakers.circuitBreaker("test-a").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ProviderGuard guard(ProviderLimit limit, String... endpoints) {
        ProviderGuard guard = new ProviderGuard("test", limit, breakers, retries, RateLimiterRegistry.ofDefaults());
        for (String endpoint : endpoints) {
            guard.register(endpoint);
        }
        return guard;
    }

    private Supplier<String> counting(String answer) {
        return () -> {
            calls.incrementAndGet();
            return answer;
        };
    }

    private Supplier<String> failing(ProviderException failure) {
        return () -> {
            calls.incrementAndGet();
            throw failure;
        };
    }

    private static ProviderUnavailableException endpointFailure() {
        return new ProviderUnavailableException("500", Scope.ENDPOINT, (Throwable) null);
    }
}
