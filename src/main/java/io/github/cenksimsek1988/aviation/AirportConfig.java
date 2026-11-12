package io.github.cenksimsek1988.aviation;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.cenksimsek1988.aviation.airport.AirportCacheProperties;
import io.github.cenksimsek1988.aviation.airport.AirportProvider;
import io.github.cenksimsek1988.aviation.airport.AirportService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the lookup: the one airport provider behind the cache. To switch provider, register a different
 * {@link AirportProvider} bean; nothing here changes.
 *
 * <p>Background refreshes run on Spring Boot's own task executor ({@code applicationTaskExecutor}, with its default
 * size and thread names), so every thread pool follows one pattern.
 */
@Configuration(proxyBeanMethods = false)
class AirportConfig {

    @Bean
    AirportService airportService(AirportProvider provider, AirportCacheProperties cache,
                                  @Qualifier("applicationTaskExecutor") Executor refreshExecutor,
                                  MeterRegistry meterRegistry) {
        return new AirportService(provider, cache, Ticker.systemTicker(), refreshExecutor, meterRegistry);
    }
}
