// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.JsonFormat;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.Cmd;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.createTestRequest;
import static com.yahoo.vespa.config.server.http.SessionHandlerTest.getRenderedString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionActiveHandlerTest {

    private static final File testApp = new File("src/test/apps/app");
    private static final String appName = "default";
    private static final TenantName tenantName = TenantName.from("activatetest");
    private static final String activatedMessage = " for tenant '" + tenantName + "' activated.";
    private static final String pathPrefix = "/application/v2/tenant/" + tenantName + "/session/";

    private MockProvisioner provisioner;
    private ApplicationRepository applicationRepository;
    private SessionActiveHandler handler;
    private final Clock clock = Clock.systemUTC();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        VespaModelFactory modelFactory = new TestModelFactory(Version.fromString("7.222.2"));
        provisioner = new MockProvisioner();
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .withModelFactoryRegistry(new ModelFactoryRegistry(List.of((modelFactory))))
                .build();
        tenantRepository.addTenant(tenantName);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(provisioner)
                .withOrchestrator(new OrchestratorMock())
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .build();
        handler = createHandler();
    }

    @Test
    public void testActivation() throws Exception {
        activateAndAssertOK();
    }

    @Test
    public void testUnknownSession() {
        HttpResponse response = handler.handle(createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.ACTIVE, 9999L, "?timeout=1.0"));
        assertEquals(response.getStatus(), NOT_FOUND);
    }

    @Test
    public void require_that_handler_gives_error_for_unsupported_methods() throws Exception {
        testUnsupportedMethod(createTestRequest(pathPrefix, HttpRequest.Method.POST, Cmd.PREPARED, 1L));
        testUnsupportedMethod(createTestRequest(pathPrefix, HttpRequest.Method.DELETE, Cmd.PREPARED, 1L));
        testUnsupportedMethod(createTestRequest(pathPrefix, HttpRequest.Method.GET, Cmd.PREPARED, 1L));
    }

    private void testUnsupportedMethod(com.yahoo.container.jdisc.HttpRequest request) throws Exception {
        HttpResponse response = handler.handle(request);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response,
                                                            METHOD_NOT_ALLOWED,
                                                            HttpErrorResponse.ErrorCode.METHOD_NOT_ALLOWED,
                                                            "Method '" + request.getMethod().name() + "' is not supported");
    }

    protected class ActivateRequest {

        private long sessionId;
        private HttpResponse actResponse;
        private ApplicationMetaData metaData;
        private final String subPath;

        ActivateRequest(String subPath) {
            this.subPath = subPath;
        }

        HttpResponse getActResponse() { return actResponse; }

        public long getSessionId() { return sessionId; }

        ApplicationMetaData getMetaData() { return metaData; }

        void invoke() {
            long sessionId = applicationRepository.createSession(applicationId(),
                                                                 new TimeoutBudget(clock, Duration.ofSeconds(10)),
                                                                 testApp,
                                                                 new BaseDeployLogger());
            applicationRepository.prepare(sessionId, new PrepareParams.Builder().applicationId(applicationId()).build());
            actResponse = handler.handle(createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.ACTIVE, sessionId, subPath));
            Tenant tenant = applicationRepository.getTenant(applicationId());
            Session session = applicationRepository.getActiveLocalSession(tenant, applicationId()).get();
            metaData = session.getMetaData();
            this.sessionId = sessionId;
        }
    }

    private void activateAndAssertOK() throws Exception {
        ActivateRequest activateRequest = new ActivateRequest("");
        activateRequest.invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        String message = getRenderedString(actResponse);
        assertEquals(message, OK, actResponse.getStatus());
        assertActivationMessageOK(activateRequest, message);
    }

    private void assertActivationMessageOK(ActivateRequest activateRequest, String message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new JsonFormat(true).encode(byteArrayOutputStream, activateRequest.getMetaData().getSlime());
        long sessionId = activateRequest.getSessionId();
        assertTrue(message.contains("\"tenant\":\"" + tenantName));
        assertTrue(message.contains("\"session-id\":\"" + sessionId));
        assertTrue(message.contains("\"message\":\"Session " + sessionId + activatedMessage));
        assertTrue(message.contains("/application/v2/tenant/" + tenantName +
                "/application/" + appName +
                "/environment/" + "prod" +
                "/region/" + "default" +
                "/instance/" + "default"));
        assertTrue(provisioner.activated());
        assertEquals(1, provisioner.lastHosts().size());
    }

    private SessionActiveHandler createHandler() {
        return new SessionActiveHandler(SessionActiveHandler.testContext(),
                                        applicationRepository,
                                        Zone.defaultZone());
    }

    private ApplicationId applicationId() {
        return ApplicationId.from(tenantName.value(), appName, "default");
    }

}
