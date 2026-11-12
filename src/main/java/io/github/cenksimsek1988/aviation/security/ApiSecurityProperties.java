package io.github.cenksimsek1988.aviation.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API security settings.
 *
 * @param anonymousAuthorities authorities every caller gets while there is no login; at least one (Spring Security
 *                             needs it), for example {@value Authorities#AIRPORTS_READ} to let everyone read
 */
@ConfigurationProperties("api.security")
public record ApiSecurityProperties(List<String> anonymousAuthorities) {
}
