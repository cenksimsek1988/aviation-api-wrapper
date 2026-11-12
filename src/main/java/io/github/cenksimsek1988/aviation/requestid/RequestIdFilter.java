package io.github.cenksimsek1988.aviation.requestid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts two values into the logging context (MDC), so every log line of a request carries them:
 *
 * <ul>
 *   <li>{@code requestId}: the caller's {@code X-Request-Id} when it is safe, otherwise a new UUID. It is also sent
 *       back in the response header and in error bodies, so a caller can quote it.</li>
 *   <li>{@code session}: one caller connection, as a short hash of the client address and port. The same connection
 *       gives the same value, so its requests are easy to follow in the logs, but the address itself is never
 *       logged. The hash key is random per start, so values cannot be matched across restarts or instances.</li>
 * </ul>
 *
 * <p>A caller's ID is accepted only as 1-64 characters of [A-Za-z0-9._-], so it cannot inject line breaks or markup
 * into logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String SESSION_MDC_KEY = "session";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String HMAC = "HmacSHA256";
    /** 12 hex characters: enough to tell connections apart. Without the key, which stays in memory, no way back. */
    private static final int SESSION_LENGTH = 12;

    private final SecretKeySpec sessionKey;

    public RequestIdFilter() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.sessionKey = new SecretKeySpec(key, HMAC);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOf(request);
        MDC.put(MDC_KEY, requestId);
        MDC.put(SESSION_MDC_KEY, sessionOf(request));
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(SESSION_MDC_KEY);
        }
    }

    private static String requestIdOf(HttpServletRequest request) {
        String supplied = request.getHeader(HEADER);
        if (supplied != null && SAFE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    /** {@code address:port}, an IPv6 address in brackets; behind a proxy the address is the forwarded client's. */
    private String sessionOf(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        String connection = (address.indexOf(':') >= 0 ? "[" + address + "]" : address) + ":" + request.getRemotePort();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(sessionKey);
            byte[] hash = mac.doFinal(connection.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, SESSION_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every Java runtime", e);
        }
    }
}
