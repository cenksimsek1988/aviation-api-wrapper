package io.github.cenksimsek1988.aviation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorization per endpoint, with Spring Security's method security: each endpoint names the authority it needs
 * ({@code @PreAuthorize}, see {@link Authorities}).
 *
 * <p>For now there is no login: every caller is anonymous, and anonymous callers get the authorities in
 * {@code api.security.anonymous-authorities} (by default {@value Authorities#AIRPORTS_READ}), so everyone may read.
 * The annotations are still checked on every call. Real authentication later means two changes: an OAuth2 resource
 * server here, and an empty list of anonymous authorities (docs/next-steps.md, "Adding authentication").
 *
 * <p>CSRF protection is off: it guards browser cookies and sessions, and this API has neither. It comes back with a
 * cookie-based login.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityProperties properties) {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .anonymous(anonymous -> anonymous.authorities(properties.anonymousAuthorities().toArray(String[]::new)))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
