package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;
import java.util.Objects;

/**
 * A provider's published rate limit, copied as is from its documentation, and the share of it we use.
 *
 * <p>Our limiter counts in fixed blocks of {@code period}, starting when the app starts. The provider may count
 * another way: a rolling window, or blocks with other edges. Any window of {@code period} on the provider's side
 * touches at most two of our blocks. With half the limit per block we therefore never send more than {@code calls}
 * in any of its windows, however it counts and whatever the period is. Bursts of up to half the limit pass at once.
 *
 * <p>Edge case: a limit of 1 still gives a share of 1, not 0, and then the guarantee no longer holds.
 *
 * @param calls  calls the provider allows per period, as published
 * @param period the provider's period, as published
 */
public record ProviderLimit(int calls, Duration period) {

    public ProviderLimit {
        if (calls < 1) {
            throw new IllegalArgumentException("A rate limit allows at least 1 call: " + calls);
        }
        Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("A rate-limit period must be positive: " + period);
        }
    }

    /** The calls we allow ourselves per block of {@link #period()}: half the provider's limit, at least 1. */
    public int callsPerBlock() {
        return Math.max(1, calls / 2);
    }
}
