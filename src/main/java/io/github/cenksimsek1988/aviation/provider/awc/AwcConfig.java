package io.github.cenksimsek1988.aviation.provider.awc;

import io.github.cenksimsek1988.aviation.provider.ProviderGuard;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires aviationweather.gov: its HTTP interface in the service group {@value #GROUP}, its guard, its adapters. Spring
 * Boot's RestClient builder behind the group adds the HTTP client metrics ({@code http.client.requests}).
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = AwcConfig.GROUP, types = AwcClient.class)
class AwcConfig {

    /** The provider's name: HTTP service group, guard, and the prefix of its circuit breakers. */
    static final String GROUP = "awc";

    @Bean
    ProviderGuard awcGuard(AwcProperties properties, CircuitBreakerRegistry breakers, RetryRegistry retries,
                           RateLimiterRegistry limiters) {
        return new ProviderGuard(GROUP, properties.rateLimit(), breakers, retries, limiters);
    }

    @Bean
    AwcAirportProvider awcAirportProvider(AwcClient client, ProviderGuard awcGuard, AwcProperties properties) {
        return new AwcAirportProvider(client, awcGuard, properties.defaultFreshFor());
    }
}
