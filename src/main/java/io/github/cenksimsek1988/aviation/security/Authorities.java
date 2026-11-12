package io.github.cenksimsek1988.aviation.security;

/**
 * The authorities the endpoints ask for; one per kind of access. Each is named as Spring Security names an OAuth2
 * token scope ({@code SCOPE_} + scope), so a token with the scope {@code airports:read} grants it with no converter.
 */
public final class Authorities {

    /** Read airport data. */
    public static final String AIRPORTS_READ = "SCOPE_airports:read";

    private Authorities() {
    }
}
