// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import static com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static com.yahoo.jdisc.http.HttpResponse.Status.FORBIDDEN;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class ControllerAuthorizationFilterTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final AthenzUser USER = user("john");
    private static final AthenzUser HOSTED_OPERATOR = user("hosted-operator");
    private static final AthenzDomain TENANT_DOMAIN = new AthenzDomain("tenantdomain");
    private static final AthenzService TENANT_ADMIN = new AthenzService(TENANT_DOMAIN, "adminservice");
    private static final AthenzService TENANT_PIPELINE = HostedAthenzIdentities.from(new ScrewdriverId("12345"));
    private static final TenantId TENANT = new TenantId("mytenant");
    private static final ApplicationId APPLICATION = new ApplicationId("myapp");

    @Test
    public void white_listed_operations_are_allowed() {
        ControllerAuthorizationFilter filter = createFilter(new ControllerTester());
        assertIsAllowed(invokeFilter(filter, createRequest(PUT, "/application/v4/user", USER)));
        assertIsAllowed(invokeFilter(filter, createRequest(POST, "/application/v4/tenant/john", USER)));
        assertIsAllowed(invokeFilter(filter, createRequest(DELETE, "/screwdriver/v1/jobsToRun", USER)));
    }

    @Test
    public void only_hosted_operator_can_access_operator_apis() {
        ControllerTester controllerTester = new ControllerTester();
        controllerTester.athenzDb().hostedOperators.add(HOSTED_OPERATOR);

        ControllerAuthorizationFilter filter = createFilter(controllerTester);

        List<AthenzIdentity> allowed = singletonList(HOSTED_OPERATOR);
        List<AthenzIdentity> forbidden = singletonList(USER);

        testApiAccess(PUT, "/application/v4/tenant/mytenant/application/myapp/deploying",
                      allowed, forbidden, filter);
        testApiAccess(POST, "/screwdriver/v1/trigger/tenant/mytenant/application/myapp/",
                      allowed, forbidden, filter);
        testApiAccess(DELETE, "/provision/v2/provision/enqueue",
                      allowed, forbidden, filter);
    }

    @Test
    public void only_hosted_operator_or_tenant_admin_can_access_tenant_admin_apis() {
        ControllerTester controllerTester = new ControllerTester();
        controllerTester.athenzDb().hostedOperators.add(HOSTED_OPERATOR);
        controllerTester.createTenant(TENANT.id(), TENANT_DOMAIN.getName(), null);
        controllerTester.athenzDb().domains.get(TENANT_DOMAIN).admins.add(TENANT_ADMIN);

        ControllerAuthorizationFilter filter = createFilter(controllerTester);

        List<AthenzIdentity> allowed = asList(HOSTED_OPERATOR, TENANT_ADMIN);
        List<AthenzIdentity> forbidden = singletonList(USER);

        testApiAccess(DELETE, "/application/v4/tenant/mytenant",
                      allowed, forbidden, filter);
        testApiAccess(POST, "/application/v4/tenant/mytenant/application/myapp/environment/perf/region/myregion/instance/default/deploy",
                      allowed, forbidden, filter);
        testApiAccess(PUT, "/application/v4/tenant/mytenant/application/myapp/environment/prod/region/myregion/instance/default/global-rotation/override",
                      allowed, forbidden, filter);
    }

    @Test
    public void only_hosted_operator_and_screwdriver_project_with_deploy_role_can_access_tenant_pipeline_apis() {
        ControllerTester controllerTester = new ControllerTester();
        controllerTester.athenzDb().hostedOperators.add(HOSTED_OPERATOR);
        controllerTester.createTenant(TENANT.id(), TENANT_DOMAIN.getName(), null);
        controllerTester.createApplication(TENANT, APPLICATION.id(), "default", 12345);
        AthenzDbMock.Domain domainMock = controllerTester.athenzDb().domains.get(TENANT_DOMAIN);
        domainMock.admins.add(TENANT_ADMIN);
        domainMock.applications.get(APPLICATION).addRoleMember(ApplicationAction.deploy, TENANT_PIPELINE);

        ControllerAuthorizationFilter filter = createFilter(controllerTester);

        List<AthenzIdentity> allowed = asList(HOSTED_OPERATOR, TENANT_PIPELINE);
        List<AthenzIdentity> forbidden = asList(TENANT_ADMIN, USER);

        testApiAccess(POST, "/application/v4/tenant/mytenant/application/myapp/environment/prod/region/myregion/instance/default/deploy",
                      allowed, forbidden, filter);

        testApiAccess(POST, "/application/v4/tenant/mytenant/application/myapp/jobreport",
                      allowed, forbidden, filter);

        testApiAccess(POST, "/application/v4/tenant/mytenant/application/myapp/promote",
                      allowed, forbidden, filter);
    }

    private static void testApiAccess(Method method,
                                      String path,
                                      List<? extends AthenzIdentity> allowedIdentities,
                                      List<? extends AthenzIdentity> forbiddenIdentities,
                                      ControllerAuthorizationFilter filter) {
        allowedIdentities.forEach(
                identity -> assertIsAllowed(invokeFilter(filter, createRequest(method, path, identity))));
        forbiddenIdentities.forEach(
                identity -> assertIsForbidden(invokeFilter(filter, createRequest(method, path, identity))));
    }

    private static void assertIsAllowed(Optional<AuthorizationResponse> response) {
        assertFalse("Expected no response from filter", response.isPresent());
    }

    private static void assertIsForbidden(Optional<AuthorizationResponse> response) {
        assertTrue("Expected a response from filter", response.isPresent());
        assertEquals("Invalid status code", response.get().statusCode, FORBIDDEN);
    }

    private static ControllerAuthorizationFilter createFilter(ControllerTester controllerTester) {
        return new ControllerAuthorizationFilter(new AthenzClientFactoryMock(controllerTester.athenzDb()),
                                                 controllerTester.controller());
    }

    private static Optional<AuthorizationResponse> invokeFilter(ControllerAuthorizationFilter filter,
                                                                DiscFilterRequest request) {
        MockResponseHandler responseHandlerMock = new MockResponseHandler();
        filter.filter(request, responseHandlerMock);
        return Optional.ofNullable(responseHandlerMock.getResponse())
                .map(response -> new AuthorizationResponse(response.getStatus(), getErrorMessage(responseHandlerMock)));
    }

    private static DiscFilterRequest createRequest(Method method, String path, AthenzIdentity identity) {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getMethod()).thenReturn(method.name());
        when(request.getRequestURI()).thenReturn(path);
        when(request.getUserPrincipal()).thenReturn(new AthenzPrincipal(identity));
        return request;
    }

    private static String getErrorMessage(MockResponseHandler responseHandler) {
        try {
            return mapper.readTree(responseHandler.readAll()).get("message").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AthenzUser user(String name) {
        return new AthenzUser(name);
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