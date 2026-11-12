package io.github.cenksimsek1988.aviation.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Calls the running service over real HTTP, and checks its problem-details answers. */
public final class ApiCalls {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private ApiCalls() {
    }

    public static HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> lookup(int port, String icao) throws IOException, InterruptedException {
        return get(port, "/api/v1/airports/" + icao);
    }

    /** An RFC 9457 answer with this status and title, carrying the same request ID as its header. */
    public static void assertProblem(HttpResponse<String> response, int status, String title) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        DocumentContext json = JsonPath.parse(response.body());
        assertThat(json.read("$.status", Integer.class)).isEqualTo(status);
        assertThat(json.read("$.title", String.class)).isEqualTo(title);
        assertThat(json.read("$.requestId", String.class))
                .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
    }
}
