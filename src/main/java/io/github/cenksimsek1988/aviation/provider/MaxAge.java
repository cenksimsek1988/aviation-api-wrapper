package io.github.cenksimsek1988.aviation.provider;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Reads the fresh time a provider gives in its {@code Cache-Control} header (RFC 9111). */
public final class MaxAge {

    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*\"?(\\d{1,10})\"?\\s*(?:,|$)");
    /** Longer fresh times are cut to this, so time arithmetic cannot overflow. */
    static final Duration LONGEST = Duration.ofDays(365);

    private MaxAge() {
    }

    /**
     * Returns the {@code max-age} of a {@code Cache-Control} value, at most {@link #LONGEST}, or {@code fallback} when
     * the header is missing or has no readable {@code max-age}. Other directives are ignored.
     */
    public static Duration from(@Nullable String cacheControl, Duration fallback) {
        if (cacheControl == null) {
            return fallback;
        }
        Matcher matcher = MAX_AGE.matcher(cacheControl.toLowerCase(Locale.ROOT));
        if (!matcher.find()) {
            return fallback;
        }
        Duration maxAge = Duration.ofSeconds(Long.parseLong(matcher.group(1)));
        return maxAge.compareTo(LONGEST) > 0 ? LONGEST : maxAge;
    }
}
