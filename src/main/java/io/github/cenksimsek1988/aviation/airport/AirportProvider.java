package io.github.cenksimsek1988.aviation.airport;

import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import io.github.cenksimsek1988.aviation.provider.ProviderException;

/**
 * The port to an airport data source. Each provider is one adapter; the service never sees a provider's wire format.
 *
 * <p>Contract: a lookup returns the provider's answer (found, not found, or bad data) with how long it stays fresh,
 * or throws a {@link ProviderException} when the provider gave no usable answer at all.
 */
public interface AirportProvider {

    ProviderAnswer<Airport> findByIcao(IcaoCode icao);
}
