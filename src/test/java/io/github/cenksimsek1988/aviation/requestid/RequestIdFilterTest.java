package io.github.cenksimsek1988.aviation.requestid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void keepsASafeCallerIdAndReplacesAnUnsafeOne() throws Exception {
        assertThat(run(request("10.0.0.1", 50000, "abc-123")).get(RequestIdFilter.MDC_KEY)).isEqualTo("abc-123");
        assertThat(run(request("10.0.0.1", 50000, "bad\nid")).get(RequestIdFilter.MDC_KEY))
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void sessionIsAShortHashThatFollowsOneConnection() throws Exception {
        String first = run(request("10.0.0.1", 50000, null)).get(RequestIdFilter.SESSION_MDC_KEY);
        String again = run(request("10.0.0.1", 50000, null)).get(RequestIdFilter.SESSION_MDC_KEY);
        String otherPort = run(request("10.0.0.1", 50001, null)).get(RequestIdFilter.SESSION_MDC_KEY);
        String ipv6 = run(request("2001:db8::1", 50000, null)).get(RequestIdFilter.SESSION_MDC_KEY);

        assertThat(first).matches("[0-9a-f]{12}").isEqualTo(again).isNotEqualTo(otherPort);
        assertThat(ipv6).matches("[0-9a-f]{12}");
        assertThat(first).doesNotContain("10.0.0.1");
    }

    @Test
    void clearsTheLoggingContextAfterTheRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("10.0.0.1", 50000, "abc-123"), response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get(RequestIdFilter.SESSION_MDC_KEY)).isNull();
    }

    /** Runs the filter and returns what the logging context held during the request. */
    private Map<String, String> run(MockHttpServletRequest request) throws Exception {
        Map<String, String> seen = new HashMap<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seen.put(RequestIdFilter.MDC_KEY, MDC.get(RequestIdFilter.MDC_KEY));
            seen.put(RequestIdFilter.SESSION_MDC_KEY, MDC.get(RequestIdFilter.SESSION_MDC_KEY));
        });
        return seen;
    }

    private static MockHttpServletRequest request(String address, int port, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/airports/LIPE");
        request.setRemoteAddr(address);
        request.setRemotePort(port);
        if (requestId != null) {
            request.addHeader(RequestIdFilter.HEADER, requestId);
        }
        return request;
    }
}
