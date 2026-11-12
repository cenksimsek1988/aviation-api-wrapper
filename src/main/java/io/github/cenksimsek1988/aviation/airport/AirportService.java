package io.github.cenksimsek1988.aviation.airport;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import io.github.cenksimsek1988.aviation.provider.ProviderDataException;
import io.github.cenksimsek1988.aviation.provider.ProviderException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up airports through a provider, behind a local cache (Caffeine). Every answer is cached for the provider's
 * own fresh time ({@code Cache-Control: max-age}):
 *
 * <ul>
 *   <li>a found airport: fresh for {@code max-age}; after that, the next lookup still gets it at once and starts one
 *       background refresh. If refreshes keep failing, it is served stale for up to {@code keepStaleFor} more, then
 *       dropped. A refresh that brings bad data keeps the good copy;</li>
 *   <li>"not found": callers get 404 for {@code max-age}, without asking the provider again;</li>
 *   <li>a bad entry (a required value was bad): callers get 503 for {@code max-age}, without asking again.</li>
 * </ul>
 *
 * <p>Caffeine gives each entry its own expiry ({@link Expiry}) but only one refresh time for the whole cache, so the
 * per-entry refresh is done here: each entry keeps its fetch time, and a lookup of an entry older than its fresh time
 * starts {@link LoadingCache#refresh}. The expiry is computed from the entry's own fetch time, so a failed refresh or a
 * kept good copy never extends an entry's life. Concurrent lookups and refreshes of one code share one provider call.
 */
public class AirportService {

    private static final Logger log = LoggerFactory.getLogger(AirportService.class);

    private final LoadingCache<IcaoCode, Entry> cache;
    private final Ticker ticker;
    private final Duration keepStaleFor;

    public AirportService(AirportProvider provider, AirportCacheProperties settings, Ticker ticker,
                          Executor refreshExecutor, MeterRegistry meterRegistry) {
        this.ticker = ticker;
        this.keepStaleFor = settings.keepStaleFor();
        this.cache = Caffeine.newBuilder()
                .maximumSize(settings.maxSize())
                .expireAfter(Expiry.writing((IcaoCode icao, Entry entry) -> timeLeft(entry)))
                .ticker(ticker)
                .executor(refreshExecutor)
                .recordStats()
                .build(new ProviderLoader(provider, ticker));
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "airports");
    }

    /**
     * Returns the airport with its age and fresh time.
     *
     * @throws AirportNotFoundException the provider knows no such airport
     * @throws ProviderException        nothing usable is cached and the provider gave no usable answer; a bad entry
     *                                  comes as {@link ProviderDataException}
     */
    public Lookup findByIcao(IcaoCode icao) {
        Entry entry = cache.get(icao);
        Duration age = Duration.ofNanos(Math.max(0, ticker.read() - entry.fetchedAt()));
        ProviderAnswer<Airport> answer = entry.answer();
        if (answer instanceof ProviderAnswer.Found<Airport> found) {
            if (age.compareTo(found.freshFor()) >= 0) {
                cache.refresh(icao);
            }
            return new Lookup(found.value(), age, found.freshFor());
        }
        if (answer instanceof ProviderAnswer.NotFound<Airport> notFound) {
            throw new AirportNotFoundException(icao, age, notFound.freshFor());
        }
        Duration left = answer.freshFor().minus(age);
        throw new ProviderDataException("The provider has bad data for airport " + icao,
                left.isNegative() ? Duration.ZERO : left);
    }

    /**
     * Time an entry has left, counted from its own fetch time: its fresh time, plus {@code keepStaleFor} for a found
     * airport. Caffeine asks on every write, so a kept good copy or a failed refresh never extends an entry's life.
     */
    private Duration timeLeft(Entry entry) {
        Duration lifetime = entry.answer().freshFor();
        if (entry.answer() instanceof ProviderAnswer.Found<Airport>) {
            lifetime = lifetime.plus(keepStaleFor);
        }
        return Duration.ofNanos(Math.max(0, entry.fetchedAt() + lifetime.toNanos() - ticker.read()));
    }

    /**
     * A found airport, as of {@code age} ago.
     *
     * @param airport  the airport
     * @param age      time since it was fetched from the provider
     * @param freshFor the provider's fresh time for it ({@code max-age}); older than this, it is stale
     */
    public record Lookup(Airport airport, Duration age, Duration freshFor) {
    }

    /** A provider answer and when it was fetched, in {@link Ticker} nanoseconds. */
    record Entry(ProviderAnswer<Airport> answer, long fetchedAt) {
    }

    /**
     * Loads and refreshes entries from the provider; a refresh that brings bad data keeps a good copy. A class, not a
     * record: it is behaviour, not data.
     */
    private static final class ProviderLoader implements CacheLoader<IcaoCode, Entry> {

        private final AirportProvider provider;
        private final Ticker ticker;

        private ProviderLoader(AirportProvider provider, Ticker ticker) {
            this.provider = provider;
            this.ticker = ticker;
        }

        @Override
        public Entry load(IcaoCode icao) {
            return entry(icao, provider.findByIcao(icao));
        }

        @Override
        public Entry reload(IcaoCode icao, Entry cached) {
            ProviderAnswer<Airport> answer;
            try {
                answer = provider.findByIcao(icao);
            } catch (RuntimeException e) {
                // Rethrown: Caffeine keeps the cached entry, and its expiry stays where it was.
                log.debug("Refresh of airport {} failed; serving the cached copy: {}", icao, e.getMessage());
                throw e;
            }
            if (answer instanceof ProviderAnswer.Bad<Airport> bad
                    && cached.answer() instanceof ProviderAnswer.Found<Airport>) {
                log.warn("Refresh of airport {} brought bad data ({}); keeping the good copy", icao, bad.reason());
                return cached;
            }
            return entry(icao, answer);
        }

        private Entry entry(IcaoCode icao, ProviderAnswer<Airport> answer) {
            if (answer instanceof ProviderAnswer.Bad<Airport> bad) {
                log.warn("Airport {}: the provider's data is bad ({}); answering 503 for {} s", icao, bad.reason(),
                        bad.freshFor().toSeconds());
            }
            return new Entry(answer, ticker.read());
        }
    }
}
