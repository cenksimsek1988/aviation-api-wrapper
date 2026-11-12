package io.github.cenksimsek1988.aviation.testing;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** A clock for the cache that moves only when a test says so. */
public final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    public void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Override
    public long read() {
        return nanos.get();
    }
}
