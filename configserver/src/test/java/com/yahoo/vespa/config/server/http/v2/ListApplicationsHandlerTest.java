// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.*;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Test;
import org.junit.Before;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import static com.yahoo.jdisc.http.HttpRequest.Method.*;

/**
 * @author Ulf Lilleengen
 */
public class ListApplicationsHandlerTest {
    private static final TenantName mytenant = TenantName.from("mytenant");
    private static final TenantName foobar = TenantName.from("foobar");

    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();

    private TenantApplications applicationRepo, applicationRepo2;
    private ListApplicationsHandler handler;

    @Before
    public void setup() {
        TenantRepository tenantRepository = new TenantRepository(componentRegistry, false);
        tenantRepository.addTenant(TenantBuilder.create(componentRegistry, mytenant));
        tenantRepository.addTenant(TenantBuilder.create(componentRegistry, foobar));
        applicationRepo = tenantRepository.getTenant(mytenant).getApplicationRepo();
        applicationRepo2 = tenantRepository.getTenant(foobar).getApplicationRepo();
        handler = new ListApplicationsHandler(ListApplicationsHandler.testOnlyContext(),
                                              tenantRepository,
                                              new Zone(Environment.dev, RegionName.from("us-east")));
    }

    @Test
    public void require_that_applications_are_listed() throws Exception {
        final String url = "http://myhost:14000/application/v2/tenant/mytenant/application/";
        assertResponse(url, Response.Status.OK,
                "[]");
        applicationRepo.createPutApplicationTransaction(
                new ApplicationId.Builder().tenant("tenant").applicationName("foo").instanceName("quux").build(),
                1).commit();
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "foo/environment/dev/region/us-east/instance/quux\"]");
        applicationRepo.createPutApplicationTransaction(
                new ApplicationId.Builder().tenant("tenant").applicationName("bali").instanceName("quux").build(),
                1).commit();
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "foo/environment/dev/region/us-east/instance/quux\"," +
                        "\"" + url + "bali/environment/dev/region/us-east/instance/quux\"]"
        );
    }

    @Test
    public void require_that_get_is_required() throws IOException {
        final String url = "http://myhost:14000/application/v2/tenant/mytenant/application/";
        assertResponse(url, Response.Status.METHOD_NOT_ALLOWED,
                createMethodNotAllowedMessage(DELETE), DELETE);
        assertResponse(url, Response.Status.METHOD_NOT_ALLOWED,
                createMethodNotAllowedMessage(PUT), PUT);
        assertResponse(url, Response.Status.METHOD_NOT_ALLOWED,
                createMethodNotAllowedMessage(POST), POST);
    }

    private static String createMethodNotAllowedMessage(Method method) {
        return "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method.name() + "' is not supported\"}";
    }

    @Test
    public void require_that_listing_works_with_multiple_tenants() throws Exception {
        applicationRepo.createPutApplicationTransaction(new ApplicationId.Builder()
                .tenant("tenant")
                .applicationName("foo").instanceName("quux").build(), 1).commit();
        applicationRepo2.createPutApplicationTransaction(new ApplicationId.Builder()
                .tenant("tenant")
                .applicationName("quux").instanceName("foo").build(), 1).commit();
        String url = "http://myhost:14000/application/v2/tenant/mytenant/application/";
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "foo/environment/dev/region/us-east/instance/quux\"]");
        url = "http://myhost:14000/application/v2/tenant/foobar/application/";
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "quux/environment/dev/region/us-east/instance/foo\"]");
    }

    private void assertResponse(String url, int expectedStatus, String expectedResponse) throws IOException {
        assertResponse(url, expectedStatus, expectedResponse, GET);
    }

    private void assertResponse(String url, int expectedStatus, String expectedResponse, Method method) throws IOException {
        assertResponse(handler, url, expectedStatus, expectedResponse, method);
    }

    static void assertResponse(ListApplicationsHandler handler, String url, int expectedStatus, String expectedResponse, Method method) throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, method));
        assertNotNull(response);
        assertThat(response.getStatus(), is(expectedStatus));
        assertThat(SessionHandlerTest.getRenderedString(response), is(expectedResponse));
    }
}
