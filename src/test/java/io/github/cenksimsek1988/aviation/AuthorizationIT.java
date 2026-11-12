package io.github.cenksimsek1988.aviation;

import static io.github.cenksimsek1988.aviation.testing.ApiCalls.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.cenksimsek1988.aviation.testing.ApiCalls;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The authority annotations are really checked: without {@code SCOPE_airports:read}, a lookup is refused before the
 * provider is asked (the base URL here points nowhere). It also checks that every endpoint names its authority.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "api.security.anonymous-authorities=ROLE_NOBODY",
        "spring.http.serviceclient.awc.base-url=http://127.0.0.1:1"
})
class AuthorizationIT {

    private static final String OUR_PACKAGE = AviationApiApplication.class.getPackageName();

    private final int port;
    private final RequestMappingHandlerMapping handlers;

    @Autowired
    AuthorizationIT(@LocalServerPort int port,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers) {
        this.port = port;
        this.handlers = handlers;
    }

    @Test
    void callerWithoutTheAuthorityIsRefused() throws Exception {
        assertProblem(ApiCalls.lookup(port, "LIPE"), 403, "Forbidden");
    }

    @Test
    void everyEndpointNamesItsAuthority() {
        List<HandlerMethod> ours = handlers.getHandlerMethods().values().stream()
                .filter(method -> method.getBeanType().getPackageName().startsWith(OUR_PACKAGE))
                .toList();

        assertThat(ours).isNotEmpty().allSatisfy(method -> assertThat(method.hasMethodAnnotation(PreAuthorize.class))
                .as("%s names its authority with @PreAuthorize", method).isTrue());
    }
}
