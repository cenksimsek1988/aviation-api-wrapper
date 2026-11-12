package io.github.cenksimsek1988.aviation;

import static io.github.cenksimsek1988.aviation.testing.ApiCalls.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.cenksimsek1988.aviation.testing.ApiCalls;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** A provider nobody listens for: the connection is refused, retried, and then the whole provider pauses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "spring.http.serviceclient.awc.base-url=http://127.0.0.1:1",
        "resilience4j.retry.instances.awc.wait-duration=10ms"
})
class UnreachableProviderIT {

    @Autowired
    CircuitBreakerRegistry breakers;

    @LocalServerPort
    int port;

    @Test
    void unreachableProviderIs503AndPausesIt() throws Exception {
        HttpResponse<String> response = ApiCalls.lookup(port, "LIPE");

        assertProblem(response, 503, "Airport data provider unavailable");
        assertThat(response.headers().firstValue("Retry-After")).isPresent();
        assertThat(breakers.circuitBreaker("awc-airport").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
