// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.Tenants;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.MockSessionZKClient;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.RemoteSessionRepo;
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
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 * @since 5.4
 */
public class ApplicationHandlerTest {

    private static File testApp = new File("src/test/apps/app");

    private ApplicationHandler handler;
    private ListApplicationsHandler listApplicationsHandler;
    private final static TenantName mytenant = TenantName.from("mytenant");
    private final static TenantName foobar = TenantName.from("foobar");
    private Tenants tenants;
    private SessionActiveHandlerTest.MockProvisioner provisioner;
    private MockStateApiFactory stateApiFactory = new MockStateApiFactory();

    @Before
    public void setup() throws Exception {
        TestTenantBuilder testBuilder = new TestTenantBuilder();
        testBuilder.createTenant(mytenant).withReloadHandler(new MockReloadHandler());
        testBuilder.createTenant(foobar).withReloadHandler(new MockReloadHandler());

        tenants = testBuilder.createTenants();
        provisioner = new SessionActiveHandlerTest.MockProvisioner();
        handler = createApplicationHandler(
                provisioner, new ApplicationConvergenceChecker(stateApiFactory), new LogServerLogGrabber());
        listApplicationsHandler = new ListApplicationsHandler(
                Runnable::run, AccessLog.voidAccessLog(), tenants, Zone.defaultZone());
    }

    private ApplicationHandler createApplicationHandler(
            Provisioner provisioner,
            ApplicationConvergenceChecker convergeChecker,
            LogServerLogGrabber logServerLogGrabber) {
        return new ApplicationHandler(
                Runnable::run,
                AccessLog.voidAccessLog(),
                tenants,
                HostProvisionerProvider.withProvisioner(provisioner),
                Zone.defaultZone(),
                convergeChecker,
                logServerLogGrabber);
    }

    @Test
    public void testDelete() throws Exception {
        ApplicationId defaultId = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenant).build();
        assertApplicationExists(mytenant, null, Zone.defaultZone());

        long sessionId = 1;
        addApplication(tenants.tenantsCopy().get(mytenant), defaultId, sessionId);
        assertApplicationExists(mytenant, defaultId, Zone.defaultZone());
        assertFalse(provisioner.removed);
        deleteAndAssertOKResponse(defaultId);
        assertTrue(provisioner.removed);
        assertThat(provisioner.lastApplicationId.tenant(), is(mytenant));
        assertThat(provisioner.lastApplicationId, is(defaultId));
        sessionId++;
        addApplication(tenants.tenantsCopy().get(mytenant), defaultId, sessionId);
        deleteAndAssertOKResponse(defaultId, true);

        ApplicationId fooId = new ApplicationId.Builder()
                              .tenant(mytenant)
                              .applicationName("foo").instanceName("quux").build();
        sessionId++;
        addApplication(tenants.tenantsCopy().get(mytenant), fooId, sessionId);
        addApplication(tenants.tenantsCopy().get(foobar), fooId, sessionId);
        assertApplicationExists(mytenant, fooId, Zone.defaultZone());
        assertApplicationExists(foobar, fooId, Zone.defaultZone());
        deleteAndAssertOKResponse(fooId, true);
        assertThat(provisioner.lastApplicationId.tenant(), is(mytenant));
        assertThat(provisioner.lastApplicationId, is(fooId));
        assertApplicationExists(mytenant, null, Zone.defaultZone());
        assertApplicationExists(foobar, fooId, Zone.defaultZone());


        sessionId++;
        ApplicationId baliId = new ApplicationId.Builder()
                              .tenant(mytenant)
                              .applicationName("bali").instanceName("quux").build();
        addApplication(tenants.tenantsCopy().get(mytenant), baliId, sessionId);
        deleteAndAssertOKResponse(baliId, true);
        assertApplicationExists(mytenant, null, Zone.defaultZone());
    }

    @Test
    public void testGet() throws Exception {
        long sessionId = 1;
        ApplicationId defaultId = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenant).build();
        addApplication(tenants.tenantsCopy().get(mytenant), defaultId, sessionId);
        assertApplicationGeneration(defaultId, Zone.defaultZone(), 1, true);
        assertApplicationGeneration(defaultId, Zone.defaultZone(), 1, false);
    }

    @Test
    public void testRestart() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenant).build();
        addApplication(tenants.tenantsCopy().get(mytenant), application, sessionId);
        assertFalse(provisioner.restarted);
        restart(application, Zone.defaultZone());
        assertTrue(provisioner.restarted);
        assertEquals(application, provisioner.lastApplicationId);
    }

    @Test
    public void testConverge() throws Exception {
        long sessionId = 1;
        ApplicationId application = new ApplicationId.Builder().applicationName(ApplicationName.defaultName()).tenant(mytenant).build();
        addApplication(tenants.tenantsCopy().get(mytenant), application, sessionId);
        assertFalse(stateApiFactory.createdApi);
        converge(application, Zone.defaultZone());
        assertTrue(stateApiFactory.createdApi);
    }

    @Test
    public void testPutIsIllegal() throws IOException {
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.PUT);
    }

    @Test
    @Ignore
    public void testFailingProvisioner() throws Exception {
        provisioner = new SessionActiveHandlerTest.FailingMockProvisioner();
        handler = createApplicationHandler(
                provisioner, new ApplicationConvergenceChecker(stateApiFactory), new LogServerLogGrabber());
        final ApplicationId applicationId = ApplicationId.defaultId();
        addApplication(tenants.tenantsCopy().get(mytenant), applicationId, 1);
        assertApplicationExists(mytenant, applicationId, Zone.defaultZone());
        provisioner.activated = true;

        String url = "http://myhost:14000/application/v2/tenant/" + mytenant + "/application/" + applicationId.application();
        deleteAndAssertResponse(url, 500, null, "{\"message\":\"Cannot remove application\"}", com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
        assertApplicationExists(mytenant, applicationId, Zone.defaultZone());
        Assert.assertTrue(provisioner.activated);
    }

    static void addApplication(Tenant tenant, ApplicationId applicationId, long sessionId) throws Exception {
        tenant.getApplicationRepo().createPutApplicationTransaction(applicationId, sessionId).commit();
        ApplicationPackage app = FilesApplicationPackage.fromFile(testApp);
        addLocalSession(tenant, app, sessionId, applicationId);
        addRemoteSession(tenant, app, sessionId);
    }

    static void addLocalSession(Tenant tenant, ApplicationPackage app, long sessionId, ApplicationId applicationId) {
        LocalSessionRepo localRepo = tenant.getLocalSessionRepo();
        localRepo.addSession(new SessionHandlerTest.MockSession(sessionId, app, applicationId));
    }

    static void addRemoteSession(Tenant tenant, ApplicationPackage app, long sessionId) {
        RemoteSessionRepo remoteRepo = tenant.getRemoteSessionRepo();
        remoteRepo.addSession(new RemoteSession(tenant.getName(), sessionId, new TestComponentRegistry(new MockCurator(), new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry())))), new MockSessionZKClient(app)));
    }

    private void assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        String url = "http://myhost:14000/application/v2/tenant/" + mytenant + "/application/default";
        deleteAndAssertResponse(url, Response.Status.METHOD_NOT_ALLOWED, HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED, "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
                method);
    }

    private void deleteAndAssertOKResponse(ApplicationId applicationId) throws IOException {
        deleteAndAssertOKResponse(applicationId, true);
    }

    private void deleteAndAssertOKResponse(ApplicationId applicationId, boolean fullAppIdInUrl) throws IOException {
        long sessionId = tenants.tenantsCopy().get(applicationId.tenant()).getApplicationRepo().getSessionIdForApplication(applicationId);
        deleteAndAssertResponse(applicationId, Zone.defaultZone(), Response.Status.OK, null, fullAppIdInUrl);
        assertNull(tenants.tenantsCopy().get(applicationId.tenant()).getLocalSessionRepo().getSession(sessionId));
    }

    private void deleteAndAssertResponse(ApplicationId applicationId, Zone zone, int expectedStatus, HttpErrorResponse.errorCodes errorCode, boolean fullAppIdInUrl) throws IOException {
        String expectedResponse = "{\"message\":\"Application '" + applicationId + "' deleted\"}";
        deleteAndAssertResponse(toUrlPath(applicationId, zone, fullAppIdInUrl), expectedStatus, errorCode, expectedResponse, com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
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
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "{\"generation\":" + expectedGeneration + "}");
    }

    private void deleteAndAssertResponse(String url, int expectedStatus, HttpErrorResponse.errorCodes errorCode, String expectedResponse, com.yahoo.jdisc.http.HttpRequest.Method method) throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, method));
        if (expectedStatus == 200) {
            HandlerTest.assertHttpStatusCodeAndMessage(response, 200, expectedResponse);
        } else {
            HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
        }
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
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(restartUrl, com.yahoo.jdisc.http.HttpRequest.Method.POST));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "");
    }

    private void converge(ApplicationId application, Zone zone) throws IOException {
        String restartUrl = toUrlPath(application, zone, true) + "/converge";
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(restartUrl, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, 200, "");
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
}
