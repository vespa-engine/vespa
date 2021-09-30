// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ListApplicationsHandlerTest {
    private static final TenantName mytenant = TenantName.from("mytenant");
    private static final TenantName foobar = TenantName.from("foobar");

    private TenantApplications applicationRepo, applicationRepo2;
    private ListApplicationsHandler handler;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        tenantRepository.addTenant(mytenant);
        tenantRepository.addTenant(foobar);
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
        ApplicationId id1 = ApplicationId.from("mytenant", "foo", "quux");
        applicationRepo.createApplication(id1);
        applicationRepo.createPutTransaction(id1, 1).commit();
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "foo/environment/dev/region/us-east/instance/quux\"]");
        ApplicationId id2 = ApplicationId.from("mytenant", "bali", "quux");
        applicationRepo.createApplication(id2);
        applicationRepo.createPutTransaction(id2, 1).commit();
        assertResponse(url, Response.Status.OK,
                "[\"" + url + "bali/environment/dev/region/us-east/instance/quux\"," +
                        "\"" + url + "foo/environment/dev/region/us-east/instance/quux\"]"
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
        ApplicationId id1 = ApplicationId.from("mytenant", "foo", "quux");
        applicationRepo.createApplication(id1);
        applicationRepo.createPutTransaction(id1, 1).commit();
        ApplicationId id2 = ApplicationId.from("foobar", "quux", "foo");
        applicationRepo2.createApplication(id2);
        applicationRepo2.createPutTransaction(id2, 1).commit();
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
