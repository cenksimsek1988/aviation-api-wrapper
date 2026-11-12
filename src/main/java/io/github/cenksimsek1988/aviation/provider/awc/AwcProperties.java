package io.github.cenksimsek1988.aviation.provider.awc;

import io.github.cenksimsek1988.aviation.provider.ProviderLimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Our settings for aviationweather.gov. Its HTTP settings (base URL, timeouts, User-Agent) are Spring Boot's own, in
 * {@code spring.http.serviceclient.awc.*}.
 *
 * @param rateLimit the provider's published limit, copied as is; we use half of it (see {@link ProviderLimit})
 * @param defaultFreshFor how long an answer stays fresh when it comes without {@code Cache-Control: max-age}
 */
@ConfigurationProperties("provider.awc")
public record AwcProperties(@NestedConfigurationProperty ProviderLimit rateLimit, Duration defaultFreshFor) {}
