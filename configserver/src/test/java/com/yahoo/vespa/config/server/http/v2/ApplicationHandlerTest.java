// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.SuperModelGenerationCounter;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.StaticResponse;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.MockSessionZKClient;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.SessionContext;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Client;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hmusum
 */
public class ApplicationHandlerTest {

    private static File testApp = new File("src/test/apps/app");

    private ApplicationHandler mockHandler;
    private ListApplicationsHandler listApplicationsHandler;
    private final static TenantName mytenantName = TenantName.from("mytenant");
    private final static TenantName foobar = TenantName.from("foobar");
    private Tenants tenants;
    private SessionHandlerTest.MockProvisioner provisioner;
    private MockStateApiFactory stateApiFactory = new MockStateApiFactory();
    private final HttpProxy mockHttpProxy = mock(HttpProxy.class);

    @Before
    public void setup() {
        TestTenantBuilder testBuilder = new TestTenantBuilder();
        testBuilder.createTenant(mytenantName).withReloadHandler(new MockReloadHandler());
        testBuilder.createTenant(foobar).withReloadHandler(new MockReloadHandler());

        tenants = testBuilder.createTenants();
        provisioner = new SessionHandlerTest.MockProvisioner();
        mockHandler = createMockApplicationHandler(
                provisioner,
                new ApplicationConvergenceChecker(stateApiFactory),
                mockHttpProxy,
                new MockLogServerLogGrabber());
        listApplicationsHandler = new ListApplicationsHandler(
                ListApplicationsHandler.testOnlyContext(),
                tenants, Zone.defaultZone());
    }

    private ApplicationHandler createMockApplicationHandler(
            Provisioner provisioner,
            ApplicationConvergenceChecker convergeChecker,
            HttpProxy httpProxy,
            LogServerLogGrabber logServerLogGrabber) {
        return new ApplicationHandler(
                ApplicationHandler.testOnlyContext(),
                Zone.defaultZone(),
                new ApplicationRepository(tenants,
                                          HostProvisionerProvider.withProvisioner(provisioner),
                                          logServerLogGrabber,
                                          convergeChecker,
                                          httpProxy,
                                          new ConfigserverConfig(new ConfigserverConfig.Builder())));
    }

    private ApplicationHandler createApplicationHandler(Tenants tenants) {
        return new ApplicationHandler(
                ApplicationHandler.testOnlyContext(),
                Zone.defaultZone(),
                new ApplicationRepository(tenants,
                                          HostProvisionerProvider.withProvisioner(provisioner),
                                          new LogServerLogGrabber(),
                                          new ApplicationConvergenceChecker(stateApiFactory),
                                          new HttpProxy(new SimpleHttpFetcher()),
                                          new ConfigserverConfig(new ConfigserverConfig.Builder())));
    }

    @Test
    public void testDelete() throws Exception {
        Clock clock = Clock.systemUTC();
        ApplicationId defaultId = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        assertApplicationExists(mytenantName, null, Zone.defaultZone());

        long sessionId = 1;
        {
            // This block is a real test of the interplay of (most of) the components of the config server
            // TODO: Extract it to ApplicationRepositoryTest, rewrite to bypass the HTTP layer and extend
            //       as login is moved from the HTTP layer into ApplicationRepository
            Tenants tenants = addApplication(defaultId, sessionId);
            ApplicationHandler handler = createApplicationHandler(tenants);
            Tenant mytenant = tenants.getTenant(defaultId.tenant());
            LocalSession applicationData = mytenant.getLocalSessionRepo().getSession(sessionId);
            assertNotNull(applicationData);
            assertNotNull(applicationData.getApplicationId());
            assertFalse(provisioner.removed);

            deleteAndAssertOKResponse(handler, mytenant, defaultId);
            assertTrue(provisioner.removed);
            assertThat(provisioner.lastApplicationId.tenant(), is(mytenantName));
            assertThat(provisioner.lastApplicationId, is(defaultId));

            assertNull(mytenant.getLocalSessionRepo().getSession(sessionId));
            assertNull(mytenant.getRemoteSessionRepo().getSession(sessionId));
        }
        
        sessionId++;
        {
            addMockApplication(tenants.getTenant(mytenantName), defaultId, sessionId, clock);
            deleteAndAssertOKResponseMocked(defaultId, true);

            ApplicationId fooId = new ApplicationId.Builder()
                    .tenant(mytenantName)
                    .applicationName("foo").instanceName("quux").build();

            sessionId++;

            addMockApplication(tenants.getTenant(mytenantName), fooId, sessionId, clock);
            addMockApplication(tenants.getTenant(foobar), fooId, sessionId, clock);
            assertApplicationExists(mytenantName, fooId, Zone.defaultZone());
            assertApplicationExists(foobar, fooId, Zone.defaultZone());
            deleteAndAssertOKResponseMocked(fooId, true);
            assertThat(provisioner.lastApplicationId.tenant(), is(mytenantName));
            assertThat(provisioner.lastApplicationId, is(fooId));
            assertApplicationExists(mytenantName, null, Zone.defaultZone());
            assertApplicationExists(foobar, fooId, Zone.defaultZone());
        }

        sessionId++;
        {
            ApplicationId baliId = new ApplicationId.Builder()
                    .tenant(mytenantName)
                    .applicationName("bali").instanceName("quux").build();
            addMockApplication(tenants.getTenant(mytenantName), baliId, sessionId, clock);
            deleteAndAssertOKResponseMocked(baliId, true);
            assertApplicationExists(mytenantName, null, Zone.defaultZone());
        }
    }

    @Test
    public void testGet() throws Exception {
        long sessionId = 1;
        ApplicationId defaultId = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), defaultId, sessionId, Clock.systemUTC());
        assertApplicationGeneration(defaultId, Zone.defaultZone(), 1, true);
        assertApplicationGeneration(defaultId, Zone.defaultZone(), 1, false);
    }

    @Test
    public void testRestart() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), application, sessionId, Clock.systemUTC());
        assertFalse(provisioner.restarted);
        restart(application, Zone.defaultZone());
        assertTrue(provisioner.restarted);
        assertEquals(application, provisioner.lastApplicationId);
    }

    @Test
    public void testConverge() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), application, sessionId, Clock.systemUTC());
        converge(application, Zone.defaultZone());
    }

    @Test
    public void testGrabLog() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), application, sessionId, Clock.systemUTC());
        assertEquals("log line", grabLog(application, Zone.defaultZone()));
    }

    @Test
    public void testClusterControllerStatus() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), application, sessionId, Clock.systemUTC());
        String host = "foo.yahoo.com";
        String url = toUrlPath(application, Zone.defaultZone(), true) + "/clustercontroller/" + host + "/status/v1/clusterName1";

        when(mockHttpProxy.get(any(), eq(host), eq("container-clustercontroller"), eq("clustercontroller-status/v1/clusterName1")))
                .thenReturn(new StaticResponse(200, "text/html", "<html>...</html>"));

        HttpResponse response = mockHandler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "text/html", "<html>...</html>");
    }

    @Test
    public void testPutIsIllegal() throws IOException {
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.PUT);
    }

    @Test
    @Ignore
    public void testFailingProvisioner() throws Exception {
        provisioner = new SessionHandlerTest.FailingMockProvisioner();
        mockHandler = createMockApplicationHandler(
                provisioner,
                new ApplicationConvergenceChecker(stateApiFactory),
                new HttpProxy(new SimpleHttpFetcher()),
                new LogServerLogGrabber());
        final ApplicationId applicationId = ApplicationId.defaultId();
        addMockApplication(tenants.getTenant(mytenantName), applicationId, 1, Clock.systemUTC());
        assertApplicationExists(mytenantName, applicationId, Zone.defaultZone());
        provisioner.activated = true;

        String url = "http://myhost:14000/application/v2/tenant/" + mytenantName + "/application/" + applicationId.application();
        deleteAndAssertResponse(mockHandler, url, 500, null, "{\"message\":\"Cannot remove application\"}", com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
        assertApplicationExists(mytenantName, applicationId, Zone.defaultZone());
        Assert.assertTrue(provisioner.activated);
    }

    static void addMockApplication(Tenant tenant, ApplicationId applicationId, long sessionId, Clock clock) {
        tenant.getApplicationRepo().createPutApplicationTransaction(applicationId, sessionId).commit();
        ApplicationPackage app = FilesApplicationPackage.fromFile(testApp);
        tenant.getLocalSessionRepo().addSession(new SessionHandlerTest.MockSession(sessionId, app, applicationId));
        TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder()
                .modelFactoryRegistry(new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry()))))
                .build();
        tenant.getRemoteSessionRepo().addSession(new RemoteSession(tenant.getName(), sessionId, componentRegistry, new MockSessionZKClient(app), clock));
    }

    @Test
    public void testFileDistributionStatus() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenantName).build();
        addMockApplication(tenants.getTenant(mytenantName), application, sessionId, Clock.systemUTC());
        Zone zone = Zone.defaultZone();

        HttpResponse response = fileDistributionStatus(application, zone);
        assertEquals(200, response.getStatus());
        SessionHandlerTest.getRenderedString(response);
        assertEquals("{\"hosts\":[{\"hostname\":\"mytesthost\",\"status\":\"UNKNOWN\",\"message\":\"error: Connection error(104)\",\"fileReferences\":[]}],\"status\":\"UNKNOWN\"}",
                     SessionHandlerTest.getRenderedString(response));

        // 404 for unknown application
        ApplicationId unknown = new ApplicationId.Builder().applicationName("unknown").tenant(mytenantName).build();
        HttpResponse responseForUnknown = fileDistributionStatus(unknown, zone);
        assertEquals(404, responseForUnknown.getStatus());
        assertEquals("{\"error-code\":\"NOT_FOUND\",\"message\":\"No such application id: mytenant.unknown\"}",
                     SessionHandlerTest.getRenderedString(responseForUnknown));
    }

    private static Tenants addApplication(ApplicationId applicationId, long sessionId) throws Exception {
        // This method is a good illustration of the spaghetti wiring resulting from no design
        // TODO: When this setup looks sane we have refactored sufficiently that there is a design

        TenantName tenantName = applicationId.tenant();
        
        Path tenantPath = Tenants.getTenantPath(tenantName);
        Path sessionPath = tenantPath.append(Tenant.SESSIONS).append(String.valueOf(sessionId));

        MockCurator curator = new MockCurator();
        GlobalComponentRegistry componentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .modelFactoryRegistry(new ModelFactoryRegistry(
                        Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry()))))
                .build();
        
        Tenants tenants = new Tenants(componentRegistry); // Creates the application path element in zk
        tenants.addTenant(tenantName);
        Tenant tenant = tenants.getTenant(tenantName);

        tenant.getApplicationRepo().createPutApplicationTransaction(applicationId, sessionId).commit();
        ApplicationPackage app = FilesApplicationPackage.fromFile(testApp);

        SessionZooKeeperClient sessionClient = new SessionZooKeeperClient(curator, sessionPath);
        SessionContext context = new SessionContext(app,
                                                    sessionClient,
                                                    new File("/serverDb"),
                                                    tenant.getApplicationRepo(),
                                                    null,
                                                    new SuperModelGenerationCounter(curator));
        tenant.getLocalSessionRepo().addSession(new LocalSession(tenantName, sessionId, null, context));
        sessionClient.writeApplicationId(applicationId); // TODO: Instead, use ApplicationRepository to deploy the application

        tenant.getRemoteSessionRepo().addSession(
                new RemoteSession(tenantName, sessionId,
                                  componentRegistry,
                                  sessionClient,
                                  Clock.systemUTC()));
        return tenants;
    }

    private void assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        String url = "http://myhost:14000/application/v2/tenant/" + mytenantName + "/application/default";
        deleteAndAssertResponse(mockHandler, url, Response.Status.METHOD_NOT_ALLOWED, HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED, "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
                                method);
    }

    private void deleteAndAssertOKResponseMocked(ApplicationId applicationId, boolean fullAppIdInUrl) throws IOException {
        long sessionId = tenants.getTenant(applicationId.tenant()).getApplicationRepo().getSessionIdForApplication(applicationId);
        deleteAndAssertResponse(mockHandler, applicationId, Zone.defaultZone(), Response.Status.OK, null, fullAppIdInUrl);
        assertNull(tenants.getTenant(applicationId.tenant()).getLocalSessionRepo().getSession(sessionId));
    }

    private void deleteAndAssertOKResponse(ApplicationHandler handler, Tenant tenant, ApplicationId applicationId) throws IOException {
        long sessionId = tenant.getApplicationRepo().getSessionIdForApplication(applicationId);
        deleteAndAssertResponse(handler, applicationId, Zone.defaultZone(), Response.Status.OK, null, true);
        assertNull(tenant.getLocalSessionRepo().getSession(sessionId));
    }

    private void deleteAndAssertResponse(ApplicationHandler handler, ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.errorCodes errorCode, boolean fullAppIdInUrl) throws IOException {
        String expectedResponse = "{\"message\":\"Application '" + applicationId + "' deleted\"}";
        deleteAndAssertResponse(handler, toUrlPath(applicationId, zone, fullAppIdInUrl), expectedStatus, errorCode, expectedResponse, com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
    }

    private void deleteAndAssertResponse(ApplicationHandler handler, String url, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse, com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, method));
        if (expectedStatus == 200) {
            HandlerTest.assertHttpStatusCodeAndMessage(response, 200, expectedResponse);
        } else {
            HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
        }
    }

    private void assertApplicationGeneration(ApplicationId applicationId, Zone zone, long expectedGeneration, boolean fullAppIdInUrl) throws IOException {
        assertApplicationGeneration(toUrlPath(applicationId, zone, fullAppIdInUrl), expectedGeneration);
    }

    private String toUrlPath(ApplicationId application, Zone zone, boolean fullAppIdInUrl) {
        String url = "http://myhost:14000/application/v2/tenant/" + application.tenant().value() + "/application/" + application.application().value();
        if (fullAppIdInUrl)
            url = url + "/environment/" + zone.environment().value() + "/region/" + zone.region().value() + "/instance/" + application.instance().value();
        return url;
    }

    private void assertApplicationGeneration(String url, long expectedGeneration) throws IOException {
        HttpResponse response = mockHandler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "{\"generation\":" + expectedGeneration + "}");
    }

    private void assertApplicationExists(TenantName tenantName, ApplicationId applicationId, Zone zone) throws IOException {
        String expected = applicationId == null ? "[]" : "[\"http://myhost:14000/application/v2/tenant/" + tenantName + "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value() + "\"]";
        ListApplicationsHandlerTest.assertResponse(listApplicationsHandler, "http://myhost:14000/application/v2/tenant/" + tenantName + "/application/",
                Response.Status.OK,
                expected,
                com.yahoo.jdisc.http.HttpRequest.Method.GET);
    }

    private void restart(ApplicationId application, Zone zone) throws IOException {
        String restartUrl = toUrlPath(application, zone, true) + "/restart";
        HttpResponse response = mockHandler.handle(HttpRequest.createTestRequest(restartUrl, com.yahoo.jdisc.http.HttpRequest.Method.POST));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "");
    }

    private void converge(ApplicationId application, Zone zone) throws IOException {
        String convergeUrl = toUrlPath(application, zone, true) + "/serviceconverge";
        HttpResponse response = mockHandler.handle(HttpRequest.createTestRequest(convergeUrl, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "");
    }

    private String grabLog(ApplicationId application, Zone zone) throws IOException {
        String restartUrl = toUrlPath(application, zone, true) + "/log";
        HttpResponse response = mockHandler.handle(HttpRequest.createTestRequest(restartUrl, com.yahoo.jdisc.http.HttpRequest.Method.POST));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "");
        return SessionHandlerTest.getRenderedString(response);
    }

    private HttpResponse fileDistributionStatus(ApplicationId application, Zone zone) {
        String restartUrl = toUrlPath(application, zone, true) + "/filedistributionstatus";
        return mockHandler.handle(HttpRequest.createTestRequest(restartUrl, com.yahoo.jdisc.http.HttpRequest.Method.GET));
    }

    private static class MockStateApiFactory implements ApplicationConvergenceChecker.StateApiFactory {
        public boolean createdApi = false;
        @Override
        public ApplicationConvergenceChecker.StateApi createStateApi(Client client, URI serviceUri) {
            createdApi = true;
            return () -> {
                try {
                    return new ObjectMapper().readTree("{\"config\":{\"generation\":1}}");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    private static class MockLogServerLogGrabber extends LogServerLogGrabber {
        @Override
        protected String readLog(String host, int port) throws IOException {
            return "log line";
        }
    }
}
