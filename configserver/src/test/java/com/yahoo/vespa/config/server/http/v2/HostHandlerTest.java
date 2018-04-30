// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.*;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.4
 */
public class HostHandlerTest {
    private static final String urlPrefix = "http://myhost:14000/application/v2/host/";

    private HostHandler handler;
    private final static TenantName mytenant = TenantName.from("mytenant");
    private final static String hostname = "testhost";
    private TenantRepository tenantRepository;
    private HostRegistries hostRegistries;
    private HostHandler hostHandler;

    @Before
    public void setup() {
        TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
        tenantRepository = new TenantRepository(componentRegistry, false);
        TenantBuilder tb = TenantBuilder.create(componentRegistry, mytenant)
                .withReloadHandler(new MockReloadHandler());
        tenantRepository.addTenant(tb);
        handler = createHostHandler();
    }

    private HostHandler createHostHandler() {
        final HostRegistry<TenantName> hostRegistry = new HostRegistry<>();
        hostRegistry.update(mytenant, Collections.singletonList(hostname));
        TestComponentRegistry testComponentRegistry = new TestComponentRegistry.Builder().build();
        hostRegistries = testComponentRegistry.getHostRegistries();
        hostRegistries.createApplicationHostRegistry(mytenant).update(ApplicationId.from(mytenant, ApplicationName.defaultName(), InstanceName.defaultName()), Collections.singletonList(hostname));
        hostRegistries.getTenantHostRegistry().update(mytenant, Collections.singletonList(hostname));
        hostHandler = new HostHandler(
                HostHandler.testOnlyContext(),
                testComponentRegistry);
        return hostHandler;
    }

    @Test
    public void require_correct_tenant_and_application_for_hostname() throws Exception {
        assertThat(hostRegistries, is(hostHandler.hostRegistries));
        long sessionId = 1;
        ApplicationId id = ApplicationId.from(mytenant, ApplicationName.defaultName(), InstanceName.defaultName());
        ApplicationHandlerTest.addMockApplication(tenantRepository.getTenant(mytenant), id, sessionId, Clock.systemUTC());
        assertApplicationForHost(hostname, mytenant, id, Zone.defaultZone());
    }

    @Test
    public void require_that_handler_gives_error_for_unknown_hostname() throws Exception {
        long sessionId = 1;
        ApplicationHandlerTest.addMockApplication(tenantRepository.getTenant(mytenant), ApplicationId.defaultId(), sessionId, Clock.systemUTC());
        final String hostname = "unknown";
        assertErrorForHost(hostname,
                Response.Status.NOT_FOUND,
                HttpErrorResponse.errorCodes.NOT_FOUND,
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not find any application using host '" + hostname + "'\"}");
    }

    @Test
    public void require_that_only_get_method_is_allowed() throws IOException {
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.PUT);
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.POST);
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
    }

    private void assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        String url = urlPrefix + hostname;
        deleteAndAssertResponse(url, Response.Status.METHOD_NOT_ALLOWED,
                HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
                method);
    }

    private void assertApplicationForHost(String hostname, TenantName expectedTenantName, ApplicationId expectedApplicationId, Zone zone) throws IOException {
        String url = urlPrefix + hostname;
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, Response.Status.OK,
                "{\"tenant\":\"" + expectedTenantName.value() + "\"," +
                        "\"application\":\"" + expectedApplicationId.application().value() + "\"," +
                        "\"environment\":\"" + zone.environment().value() + "\"," +
                        "\"region\":\"" + zone.region().value() + "\"," +
                        "\"instance\":\"" + expectedApplicationId.instance().value() + "\"}"
        );
    }

    private void assertErrorForHost(String hostname, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse) throws IOException {
        String url = urlPrefix + hostname;
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
    }

    private void deleteAndAssertResponse(String url, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse, com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, method));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
    }
}
