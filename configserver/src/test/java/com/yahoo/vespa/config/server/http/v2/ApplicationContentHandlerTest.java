// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.Response;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.http.ContentHandlerTestBase;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ApplicationContentHandlerTest extends ContentHandlerTestBase {
    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
    private final Clock clock = componentRegistry.getClock();

    private ApplicationHandler handler;
    private TenantName tenantName1 = TenantName.from("mofet");
    private TenantName tenantName2 = TenantName.from("bla");
    private String baseServer = "http://foo:1337";

    private ApplicationId idTenant1 = new ApplicationId.Builder()
                                      .tenant(tenantName1)
                                      .applicationName("foo").instanceName("quux").build();
    private ApplicationId idTenant2 = new ApplicationId.Builder()
                                      .tenant(tenantName2)
                                      .applicationName("foo").instanceName("quux").build();
    private MockSession session2;

    @Before
    public void setupHandler() {
        TenantRepository tenantRepository = new TenantRepository(componentRegistry, false);
        tenantRepository.addTenant(TenantBuilder.create(componentRegistry, tenantName1));
        tenantRepository.addTenant(TenantBuilder.create(componentRegistry, tenantName2));

        session2 = new MockSession(2l, FilesApplicationPackage.fromFile(new File("src/test/apps/content")));
        Tenant tenant1 = tenantRepository.getTenant(tenantName1);
        tenant1.getLocalSessionRepo().addSession(session2);
        tenant1.getApplicationRepo().createPutApplicationTransaction(idTenant1, 2l).commit();

        MockSession session3 = new MockSession(3l, FilesApplicationPackage.fromFile(new File("src/test/apps/content2")));
        Tenant tenant2 = tenantRepository.getTenant(tenantName2);
        tenant2.getLocalSessionRepo().addSession(session3);
        tenant2.getApplicationRepo().createPutApplicationTransaction(idTenant2, 3l).commit();

        handler = new ApplicationHandler(ApplicationHandler.testOnlyContext(),
                                         Zone.defaultZone(),
                                         new ApplicationRepository(tenantRepository,
                                                                   new MockProvisioner(),
                                                                   clock));
        pathPrefix = createPath(idTenant1, Zone.defaultZone());
        baseUrl = baseServer + pathPrefix;
    }

    private String createPath(ApplicationId applicationId, Zone zone) {
        return "/application/v2/tenant/"
             + applicationId.tenant().value()
             + "/application/"
             + applicationId.application().value()
             + "/environment/"
             + zone.environment().value()
             + "/region/"
             + zone.region().value()
             + "/instance/"
             + applicationId.instance().value()
             + "/content/";
    }

    @Test
    public void require_that_nonexistant_application_returns_not_found() {
        assertNotFound(HttpRequest.createTestRequest(baseServer + createPath(new ApplicationId.Builder()
                                                                             .tenant("tenant")
                                                                             .applicationName("notexist").instanceName("baz").build(), Zone.defaultZone()),
                                                     com.yahoo.jdisc.http.HttpRequest.Method.GET));
        assertNotFound(HttpRequest.createTestRequest(baseServer + createPath(new ApplicationId.Builder()
                                                                             .tenant("unknown")
                                                                             .applicationName("notexist").instanceName("baz").build(), Zone.defaultZone()),
                                                     com.yahoo.jdisc.http.HttpRequest.Method.GET));
    }

    @Test
    public void require_that_multiple_tenants_are_handled() throws IOException {
        assertContent("/test.txt", "foo\n");
        pathPrefix = createPath(idTenant2, Zone.defaultZone());
        baseUrl = baseServer + pathPrefix;
        assertContent("/test.txt", "bar\n");
    }

    @Test
    public void require_that_get_does_not_set_write_flag() throws IOException {
        session2.status = Session.Status.PREPARE;
        assertContent("/test.txt", "foo\n");
        assertThat(session2.status, is(Session.Status.PREPARE));
    }

    private void assertNotFound(HttpRequest request) {
        HttpResponse response = handler.handle(request);
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND));
    }

    @Override
    protected HttpResponse doRequest(com.yahoo.jdisc.http.HttpRequest.Method method, String path) {
        HttpRequest request = HttpRequest.createTestRequest(baseUrl + path, method);
        return handler.handle(request);
    }
}
