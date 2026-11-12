package io.github.cenksimsek1988.aviation.testing;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.github.cenksimsek1988.aviation.airport.AirportProvider;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * A working stand-in for a provider: it answers what a test tells it, fails when told to, and counts its calls.
 * Without an answer set for a code it answers "not found".
 */
public final class FakeAirportProvider implements AirportProvider {

    private final Map<IcaoCode, ProviderAnswer<Airport>> answers = new ConcurrentHashMap<>();
    private final Map<IcaoCode, AtomicInteger> calls = new ConcurrentHashMap<>();
    private volatile @Nullable RuntimeException failure;

    public void found(Airport airport, Duration freshFor) {
        answers.put(airport.icao(), new ProviderAnswer.Found<>(airport, freshFor));
    }

    public void bad(IcaoCode icao, Duration freshFor) {
        answers.put(icao, new ProviderAnswer.Bad<>("test: bad value", freshFor));
    }

    public void notFound(IcaoCode icao, Duration freshFor) {
        answers.put(icao, new ProviderAnswer.NotFound<>(freshFor));
    }

    /** From now on, every call fails with {@code failure}. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public int calls(IcaoCode icao) {
        AtomicInteger count = calls.get(icao);
        return count == null ? 0 : count.get();
    }

    @Override
    public ProviderAnswer<Airport> findByIcao(IcaoCode icao) {
        calls.computeIfAbsent(icao, code -> new AtomicInteger()).incrementAndGet();
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        return answers.getOrDefault(icao, new ProviderAnswer.NotFound<>(Duration.ofHours(1)));
    }
}
