package io.github.cenksimsek1988.aviation.airport;

import static io.github.cenksimsek1988.aviation.testing.TestAirports.airport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cenksimsek1988.aviation.provider.ProviderDataException;
import io.github.cenksimsek1988.aviation.provider.ProviderException;
import io.github.cenksimsek1988.aviation.provider.ProviderUnavailableException;
import io.github.cenksimsek1988.aviation.testing.FakeAirportProvider;
import io.github.cenksimsek1988.aviation.testing.FakeTicker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The cache rules, on a clock the test moves; background refreshes run at once on the calling thread. */
class AirportServiceTest {

    private static final IcaoCode LIPE = IcaoCode.parse("LIPE");
    private static final Duration MAX_AGE = Duration.ofHours(1);
    private static final Duration KEEP_STALE = Duration.ofDays(7);

    private final FakeTicker ticker = new FakeTicker();
    private final FakeAirportProvider provider = new FakeAirportProvider();
    private final AirportService service = new AirportService(provider,
            new AirportCacheProperties(100, KEEP_STALE), ticker, Runnable::run, new SimpleMeterRegistry());

    @Test
    void foundAirportIsServedFromCacheWhileFresh() {
        provider.found(airport("LIPE", "BOLOGNA"), MAX_AGE);

        service.findByIcao(LIPE);
        ticker.advance(Duration.ofMinutes(20));
        AirportService.Lookup second = service.findByIcao(LIPE);

        assertThat(second.airport().name()).isEqualTo("BOLOGNA");
        assertThat(second.age()).isEqualTo(Duration.ofMinutes(20));
        assertThat(second.freshFor()).isEqualTo(MAX_AGE);
        assertThat(provider.calls(LIPE)).isEqualTo(1);
    }

    @Test
    void staleAirportIsServedAtOnceAndRefreshedInTheBackground() {
        provider.found(airport("LIPE", "OLD NAME"), MAX_AGE);
        service.findByIcao(LIPE);
        provider.found(airport("LIPE", "NEW NAME"), MAX_AGE);
        ticker.advance(MAX_AGE.plusMinutes(1));

        AirportService.Lookup stale = service.findByIcao(LIPE);
        AirportService.Lookup refreshed = service.findByIcao(LIPE);

        assertThat(stale.airport().name()).isEqualTo("OLD NAME");
        assertThat(refreshed.airport().name()).isEqualTo("NEW NAME");
        assertThat(refreshed.age()).isZero();
        assertThat(provider.calls(LIPE)).isEqualTo(2);
    }

    @Test
    void staleAirportIsServedWhileProviderIsDownThenDropped() {
        provider.found(airport("LIPE", "BOLOGNA"), MAX_AGE);
        service.findByIcao(LIPE);
        provider.failWith(unavailable());
        ticker.advance(Duration.ofDays(3));

        AirportService.Lookup stale = service.findByIcao(LIPE);
        ticker.advance(MAX_AGE.plus(KEEP_STALE).minus(Duration.ofDays(3)));

        assertThat(stale.airport().name()).isEqualTo("BOLOGNA");
        assertThat(stale.age()).isEqualTo(Duration.ofDays(3));
        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void refreshWithBadDataKeepsTheGoodCopyWithoutExtendingIt() {
        provider.found(airport("LIPE", "BOLOGNA"), MAX_AGE);
        service.findByIcao(LIPE);
        provider.bad(LIPE, MAX_AGE);
        ticker.advance(Duration.ofDays(2));

        AirportService.Lookup kept = service.findByIcao(LIPE);
        ticker.advance(MAX_AGE.plus(KEEP_STALE).minus(Duration.ofDays(2)));

        assertThat(kept.airport().name()).isEqualTo("BOLOGNA");
        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(ProviderDataException.class);
    }

    @Test
    void notFoundIsCachedForTheProvidersMaxAge() {
        provider.notFound(LIPE, MAX_AGE);

        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(AirportNotFoundException.class);
        ticker.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> service.findByIcao(LIPE))
                .isInstanceOfSatisfying(AirportNotFoundException.class, e -> {
                    assertThat(e.age()).isEqualTo(Duration.ofMinutes(59));
                    assertThat(e.freshFor()).isEqualTo(MAX_AGE);
                });
        assertThat(provider.calls(LIPE)).isEqualTo(1);

        ticker.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(AirportNotFoundException.class);
        assertThat(provider.calls(LIPE)).isEqualTo(2);
    }

    @Test
    void badEntryIsCachedAndTellsHowLongItLasts() {
        provider.bad(LIPE, MAX_AGE);

        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(ProviderDataException.class);
        ticker.advance(Duration.ofMinutes(45));

        assertThatThrownBy(() -> service.findByIcao(LIPE))
                .isInstanceOfSatisfying(ProviderDataException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(15)));
        assertThat(provider.calls(LIPE)).isEqualTo(1);
    }

    @Test
    void failureWithNothingCachedReachesTheCallerAndIsNotCached() {
        provider.failWith(unavailable());

        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> service.findByIcao(LIPE)).isInstanceOf(ProviderUnavailableException.class);
        assertThat(provider.calls(LIPE)).isEqualTo(2);
    }

    private static ProviderUnavailableException unavailable() {
        return new ProviderUnavailableException("down", ProviderException.Scope.PROVIDER, Duration.ofSeconds(60));
    }
}
