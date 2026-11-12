package io.github.cenksimsek1988.aviation;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.cenksimsek1988.aviation.testing.ApiCalls.assertProblem;
import static io.github.cenksimsek1988.aviation.testing.TestAirports.awcJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import io.github.cenksimsek1988.aviation.testing.ApiCalls;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole service over real HTTP, with WireMock standing in for aviationweather.gov. Each test uses its own ICAO
 * code, so the shared cache cannot carry one test's answer into another, and each test starts with closed circuit
 * breakers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "spring.http.serviceclient.awc.read-timeout=300ms",
        "provider.awc.rate-limit.calls=1000",
        "resilience4j.retry.instances.awc.wait-duration=10ms"
})
class AirportApiIT {

    private static final String PROVIDER_PATH = "/api/data/airport";
    private static final String ONE_HOUR = "max-age=3600";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @RegisterExtension
    static final WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.awc.base-url", provider::baseUrl);
    }

    private final int port;
    private final int managementPort;
    private final CircuitBreakerRegistry breakers;

    @Autowired
    AirportApiIT(@LocalServerPort int port, @Value("${local.management.port}") int managementPort,
                 CircuitBreakerRegistry breakers) {
        this.port = port;
        this.managementPort = managementPort;
        this.breakers = breakers;
    }

    @BeforeEach
    void resetProviderAndBreakers() {
        provider.resetAll();
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void returnsTheAirportWithTheProvidersCachingHeadersAndCachesIt() throws Exception {
        // Cache-Control over two header lines, as HTTP allows; max-age differs from the 1 h used without one.
        stubLookup("LIPE", okJson(lipeJson()).withHeader("Cache-Control", "public", "max-age=600"));

        HttpResponse<String> response = lookup("lipe");
        lookup("LIPE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("max-age=600, public");
        assertThat(response.headers().firstValue("Age")).hasValueSatisfying(
                age -> assertThat(Integer.parseInt(age)).isBetween(0, 1));
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"icao": "LIPE", "iata": "BLQ", "name": "BOLOGNA/BORGO PANIGALE AIRPORT", "region": "BO",
                 "country": "IT",
                 "location": {"latitude": 44.5308, "longitude": 11.297, "elevation": {"value": 38, "unit": "m"}},
                 "runways": [{"designator": "12/30", "length": {"value": 9196, "unit": "ft"},
                              "width": {"value": 148, "unit": "ft"}, "surface": "H"}]}
                """));
        provider.verify(1, getRequestedFor(urlPathEqualTo(PROVIDER_PATH))
                .withQueryParam("ids", equalTo("LIPE"))
                .withQueryParam("format", equalTo("json"))
                .withHeader("User-Agent", containing("aviation-api-wrapper")));
    }

    @Test
    void unknownAirportIs404AndCached() throws Exception {
        stubLookup("ZZZZ", noContent().withHeader("Cache-Control", ONE_HOUR));

        HttpResponse<String> response = lookup("ZZZZ");
        lookup("ZZZZ");

        assertProblem(response, 404, "Airport not found");
        assertThat(response.headers().firstValue("Cache-Control")).contains("max-age=3600, public");
        provider.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void invalidCodeIs400AndProviderIsNotCalled() throws Exception {
        HttpResponse<String> response = lookup("LIP1");

        assertProblem(response, 400, "Invalid ICAO code");
        provider.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void providerFailureIs503WithoutRetryAndPausesLookups() throws Exception {
        // A 429 also guards the JDK HTTP client setting: Apache HttpClient would repeat it once by itself.
        stubLookup("LIMJ", status(429));

        HttpResponse<String> first = lookup("LIMJ");
        HttpResponse<String> other = lookup("LIRP");

        assertProblem(first, 503, "Airport data provider unavailable");
        assertThat(retryAfter(first)).isBetween(55, 60);
        assertProblem(other, 503, "Airport data provider unavailable");
        provider.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void slowProviderIs503WithoutRetry() throws Exception {
        stubLookup("LIMF", okJson(awcJson("LIMF")).withFixedDelay(1_000));

        assertProblem(lookup("LIMF"), 503, "Airport data provider unavailable");
        provider.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void missingRequiredKeyIs503AndPausesTheEndpoint() throws Exception {
        stubLookup("LIBD", okJson(awcJson("LIBD").replace("\"lat\":41.8,", "")));

        assertProblem(lookup("LIBD"), 503, "Airport data provider unavailable");
        assertProblem(lookup("LIBD"), 503, "Airport data provider unavailable");
        provider.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void oversizedProviderAnswerIs503() throws Exception {
        stubLookup("LIRZ", okJson(awcJson("LIRZ").replace("\"name\":\"TEST AIRPORT\"",
                "\"name\":\"" + "X".repeat(100_000) + "\"")));

        assertProblem(lookup("LIRZ"), 503, "Airport data provider unavailable");
    }

    @Test
    void healthDetailsOnTheManagementPortProbesAlsoOnTheApiPort() throws Exception {
        HttpResponse<String> health = ApiCalls.get(managementPort, "/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(JsonPath.parse(health.body())
                .read("$.components.circuitBreakers.details['awc-airport'].status", String.class)).isNotBlank();
        assertThat(ApiCalls.get(port, "/actuator/health").statusCode()).isEqualTo(404);
        assertThat(ApiCalls.get(port, "/livez").statusCode()).isEqualTo(200);
        assertThat(ApiCalls.get(port, "/readyz").statusCode()).isEqualTo(200);
    }

    @Test
    void apiDocsListEveryAnswer() throws Exception {
        HttpResponse<String> docs = ApiCalls.get(port, "/v3/api-docs");
        Map<String, Object> responses = JsonPath.parse(docs.body())
                .read("$.paths['/api/v1/airports/{icao}'].get.responses");

        assertThat(responses).containsOnlyKeys("200", "400", "403", "404", "500", "503");
        Map<String, Object> errorContent = JsonPath.parse(docs.body())
                .read("$.paths['/api/v1/airports/{icao}'].get.responses['503'].content");
        assertThat(errorContent).containsOnlyKeys("application/problem+json");
    }

    private HttpResponse<String> lookup(String icao) throws Exception {
        return ApiCalls.lookup(port, icao);
    }

    private static int retryAfter(HttpResponse<String> response) {
        return Integer.parseInt(response.headers().firstValue("Retry-After").orElseThrow());
    }

    private static void stubLookup(String icao, ResponseDefinitionBuilder answer) {
        provider.stubFor(WireMock.get(urlPathEqualTo(PROVIDER_PATH))
                .withQueryParam("ids", equalTo(icao))
                .willReturn(answer));
    }

    /** A real answer of aviationweather.gov, saved from the live API. */
    private static String lipeJson() throws IOException {
        try (InputStream in = Objects.requireNonNull(AirportApiIT.class.getResourceAsStream("/awc/airport-LIPE.json"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
