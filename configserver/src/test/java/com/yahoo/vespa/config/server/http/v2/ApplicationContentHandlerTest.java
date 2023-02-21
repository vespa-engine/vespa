// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.ContentHandlerTestBase;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Ulf Lilleengen
 */
public class ApplicationContentHandlerTest extends ContentHandlerTestBase {

    private static final File testApp = new File("src/test/apps/content");

    private final TenantName tenantName1 = TenantName.from("mofet");
    private final TenantName tenantName2 = TenantName.from("bla");
    private final String baseServer = "http://foo:1337";

    private final ApplicationId appId1 = new ApplicationId.Builder().tenant(tenantName1).applicationName("foo").instanceName("quux").build();

    private ApplicationRepository applicationRepository;
    private ApplicationHandler handler;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupHandler() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();

        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        tenantRepository.addTenant(tenantName1);
        tenantRepository.addTenant(tenantName2);

        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();

        applicationRepository.deploy(testApp, prepareParams(appId1));
        handler = new ApplicationHandler(ApplicationHandler.testContext(),
                                         Zone.defaultZone(),
                                         applicationRepository);
        pathPrefix = createPath(appId1, Zone.defaultZone());
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
    public void require_that_get_does_not_set_write_flag() throws IOException {
        Tenant tenant1 = applicationRepository.getTenant(appId1);
        Session session = applicationRepository.getActiveLocalSession(tenant1, appId1).get();
        assertContent("/test.txt", "foo\n");
        assertEquals(Session.Status.ACTIVATE, session.getStatus());
    }

    private void assertNotFound(HttpRequest request) {
        HttpResponse response = handler.handle(request);
        assertNotNull(response);
        assertEquals(Response.Status.NOT_FOUND, response.getStatus());
    }

    @Override
    protected HttpResponse doRequest(com.yahoo.jdisc.http.HttpRequest.Method method, String path) {
        HttpRequest request = HttpRequest.createTestRequest(baseUrl + path, method);
        return handler.handle(request);
    }

    private PrepareParams prepareParams(ApplicationId applicationId) {
        return new PrepareParams.Builder().applicationId(applicationId).build();
    }

}
