// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.http.ContentHandlerTestBase;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class ApplicationContentHandlerTest extends ContentHandlerTestBase {

    private ApplicationHandler handler;
    private TenantName tenant1 = TenantName.from("mofet");
    private TenantName tenant2 = TenantName.from("bla");
    private String baseServer = "http://foo:1337";

    private ApplicationId idTenant1 = new ApplicationId.Builder()
                                      .tenant(tenant1)
                                      .applicationName("foo").instanceName("quux").build();
    private ApplicationId idTenant2 = new ApplicationId.Builder()
                                      .tenant(tenant2)
                                      .applicationName("foo").instanceName("quux").build();
    private MockSession session2;

    @Before
    public void setupHandler() throws Exception {
        TestTenantBuilder testTenantBuilder = new TestTenantBuilder();
        testTenantBuilder.createTenant(tenant1);
        testTenantBuilder.createTenant(tenant2);
        session2 = new MockSession(2l, FilesApplicationPackage.fromFile(new File("src/test/apps/content")));
        testTenantBuilder.tenants().get(tenant1).getLocalSessionRepo().addSession(session2);
        testTenantBuilder.tenants().get(tenant2).getLocalSessionRepo().addSession(new MockSession(3l, FilesApplicationPackage.fromFile(new File("src/test/apps/content2"))));
        testTenantBuilder.tenants().get(tenant1).getApplicationRepo().createPutApplicationTransaction(idTenant1, 2l).commit();
        testTenantBuilder.tenants().get(tenant2).getApplicationRepo().createPutApplicationTransaction(idTenant2, 3l).commit();
        handler = new ApplicationHandler(command -> command.run(),
                                         AccessLog.voidAccessLog(),
                                         Zone.defaultZone(),
                                         new ApplicationRepository(testTenantBuilder.createTenants(),
                                                                   HostProvisionerProvider.empty(),
                                                                   new MockCurator(),
                                                                   new LogServerLogGrabber(),
                                                                   new ApplicationConvergenceChecker(),
                                                                   new HttpProxy(new SimpleHttpFetcher()),
                                                                   new ConfigserverConfig(new ConfigserverConfig.Builder())));
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
    public void require_that_nonexistant_application_returns_not_found() throws IOException {
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
