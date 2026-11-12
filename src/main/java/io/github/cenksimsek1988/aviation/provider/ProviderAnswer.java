package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;
import java.util.Objects;

/**
 * What a provider answered for one lookup, with how long the answer stays fresh (the provider's
 * {@code Cache-Control: max-age}). All three kinds are cached for that time, so the provider is asked again only when
 * it expects the data may have changed.
 *
 * @param <T> the kind of data, for example an airport
 */
public sealed interface ProviderAnswer<T> {

    /** How long this answer stays fresh, as the provider said. */
    Duration freshFor();

    /** The provider has the data. */
    record Found<T>(T value, Duration freshFor) implements ProviderAnswer<T> {
        public Found {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(freshFor, "freshFor");
        }
    }

    /** The provider has no data for this key. */
    record NotFound<T>(Duration freshFor) implements ProviderAnswer<T> {
        public NotFound {
            Objects.requireNonNull(freshFor, "freshFor");
        }
    }

    /** The provider answered, but a required value was bad; {@code reason} is for the log, never for callers. */
    record Bad<T>(String reason, Duration freshFor) implements ProviderAnswer<T> {
        public Bad {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(freshFor, "freshFor");
        }
    }
}
