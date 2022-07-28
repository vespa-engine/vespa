// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.restapi.ApplicationRequestToDiscFilterRequestWrapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import static com.yahoo.jdisc.http.HttpResponse.Status.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 * @author jonmv
 */
public class ControllerAuthorizationFilterTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void operator() {
        ControllerTester tester = new ControllerTester();
        SecurityContext securityContext = new SecurityContext(() -> "operator", Set.of(Role.hostedOperator()));
        ControllerAuthorizationFilter filter = createFilter(tester);

        assertIsAllowed(invokeFilter(filter, createRequest(Method.POST, "/zone/v2/path", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.PUT, "/application/v4/user", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.GET, "/zone/v1/path", securityContext)));
    }

    @Test
    void supporter() {
        ControllerTester tester = new ControllerTester();
        SecurityContext securityContext = new SecurityContext(() -> "operator", Set.of(Role.hostedSupporter()));
        ControllerAuthorizationFilter filter = createFilter(tester);

        assertIsForbidden(invokeFilter(filter, createRequest(Method.POST, "/zone/v2/path", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.GET, "/zone/v1/path", securityContext)));
    }

    @Test
    void unprivileged() {
        ControllerTester tester = new ControllerTester();
        SecurityContext securityContext = new SecurityContext(() -> "user", Set.of(Role.everyone()));
        ControllerAuthorizationFilter filter = createFilter(tester);

        assertIsForbidden(invokeFilter(filter, createRequest(Method.POST, "/zone/v2/path", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.PUT, "/application/v4/user", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.GET, "/zone/v1/path", securityContext)));
    }

    @Test
    void unprivilegedInPublic() {
        ControllerTester tester = new ControllerTester(SystemName.Public);
        SecurityContext securityContext = new SecurityContext(() -> "user", Set.of(Role.everyone()));

        ControllerAuthorizationFilter filter = createFilter(tester);
        assertIsForbidden(invokeFilter(filter, createRequest(Method.POST, "/zone/v2/path", securityContext)));
        assertIsForbidden(invokeFilter(filter, createRequest(Method.PUT, "/application/v4/user", securityContext)));
        assertIsAllowed(invokeFilter(filter, createRequest(Method.GET, "/zone/v1/path", securityContext)));
    }

    @Test
    void hostedDeveloper() {
        ControllerTester tester = new ControllerTester();
        TenantName tenantName = TenantName.defaultName();
        SecurityContext securityContext = new SecurityContext(() -> "user", Set.of(Role.hostedDeveloper(tenantName)));

        ControllerAuthorizationFilter filter = createFilter(tester);
        assertIsAllowed(invokeFilter(filter, createRequest(Method.POST, "/application/v4/tenant/" + tenantName.value() + "/application/app/instance/default/environment/dev/region/region/deploy", securityContext)));
        assertIsForbidden(invokeFilter(filter, createRequest(Method.POST, "/application/v4/tenant/" + tenantName.value() + "/application/app/instance/default/environment/prod/region/region/deploy", securityContext)));
        assertIsForbidden(invokeFilter(filter, createRequest(Method.POST, "/application/v4/tenant/" + tenantName.value() + "/application/app/submit", securityContext)));
    }

    private static void assertIsAllowed(Optional<AuthorizationResponse> response) {
        assertFalse(response.isPresent(),
                    "Expected no response from filter, but got \"" +
                    response.map(r -> r.message + "\" (" + r.statusCode + ")").orElse(""));
    }

    private static void assertIsForbidden(Optional<AuthorizationResponse> response) {
        assertTrue(response.isPresent(), "Expected a response from filter");
        assertEquals(FORBIDDEN, response.get().statusCode, "Invalid status code");
    }

    private static ControllerAuthorizationFilter createFilter(ControllerTester tester) {
        return new ControllerAuthorizationFilter(tester.controller());
    }

    private static Optional<AuthorizationResponse> invokeFilter(ControllerAuthorizationFilter filter,
                                                                DiscFilterRequest request) {
        MockResponseHandler responseHandlerMock = new MockResponseHandler();
        filter.filter(request, responseHandlerMock);
        return Optional.ofNullable(responseHandlerMock.getResponse())
                       .map(response -> new AuthorizationResponse(response.getStatus(), getErrorMessage(responseHandlerMock)));
    }

    private static DiscFilterRequest createRequest(Method method, String path, SecurityContext securityContext) {
        Request request = new Request(path, new byte[0], Request.Method.valueOf(method.name()), securityContext.principal());
        request.getAttributes().put(SecurityContext.ATTRIBUTE_NAME, securityContext);
        return new ApplicationRequestToDiscFilterRequestWrapper(request);
    }

    private static String getErrorMessage(MockResponseHandler responseHandler) {
        try {
            return mapper.readTree(responseHandler.readAll()).get("message").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class AuthorizationResponse {
        final int statusCode;
        final String message;

        AuthorizationResponse(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }
    }

}
