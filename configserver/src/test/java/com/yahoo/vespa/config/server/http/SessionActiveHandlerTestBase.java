// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import static com.yahoo.jdisc.Response.Status.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.*;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.SuperModelGenerationCounter;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.session.*;

import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModelFactory;
import org.hamcrest.core.Is;
import org.junit.Test;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.PathProvider;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

public abstract class SessionActiveHandlerTestBase extends SessionHandlerTest {

    private File testApp = new File("src/test/apps/app");
    protected static final String appName = "default";
    protected TenantName tenant = null;
    protected ConfigCurator configCurator;
    protected Curator curator;
    protected RemoteSessionRepo remoteSessionRepo;
    protected LocalSessionRepo localRepo;
    protected PathProvider pathProvider;
    protected TenantApplications applicationRepo;
    protected String activatedMessage = " activated.";

    @Test
    public void testThatPreviousSessionIsDeactivated() throws Exception {
        RemoteSession firstSession = activateAndAssertOK(90l, 0l);
        activateAndAssertOK(91l, 90l);
        assertThat(firstSession.getStatus(), is(Session.Status.DEACTIVATE));
    }
    
    @Test
    public void testForceActivationWithActivationInBetween() throws Exception {
        activateAndAssertOK(90l, 0l);
        activateAndAssertOK(92l, 89l, "?force=true");
    }

    @Test
    public void testUnknownSession() throws Exception {
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.ACTIVE, 9999L, "?timeout=1.0"));
        assertEquals(response.getStatus(), 404);
    }
    
    @Test
    public void testActivationWithActivationInBetween() throws Exception {
        activateAndAssertOK(90l, 0l);
        activateAndAssertError(92l, 89l,
                HttpErrorResponse.errorCodes.BAD_REQUEST,
                getActivateLogPre() +
                "Cannot activate session 92 because the currently active session (90) has changed since session 92 was created (was 89 at creation time)");
    }

    protected abstract String getActivateLogPre();

    @Test
    public void testActivationOfUnpreparedSession() throws Exception {
        // Needed so we can test that previous active session is still active after a failed activation
        RemoteSession firstSession = activateAndAssertOK(90l, 0l);
        long sessionId = 91l;
        ActivateRequest activateRequest = new ActivateRequest(sessionId, 0l, Session.Status.NEW, "").invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        RemoteSession session = activateRequest.getSession();
        assertThat(actResponse.getStatus(), is(BAD_REQUEST));
        assertThat(getRenderedString(actResponse), is("{\"error-code\":\"BAD_REQUEST\",\"message\":\"" + getActivateLogPre() + "Session " + sessionId + " is not prepared\"}"));
        assertThat(session.getStatus(), is(not(Session.Status.ACTIVATE)));
        assertThat(firstSession.getStatus(), is(Session.Status.ACTIVATE));
    }

    @Test
    public void testActivationWithBarrierTimeout() throws Exception {
        // Needed so we can test that previous active session is still active after a failed activation
        activateAndAssertOK(90l, 0l);
        ((MockCurator) curator).timeoutBarrierOnEnter(true);
        ActivateRequest activateRequest = new ActivateRequest(91l, 90l, "").invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        assertThat(actResponse.getStatus(), is(INTERNAL_SERVER_ERROR));
    }

    @Test
    public void testActivationOfSessionThatDoesNotExistAsLocalSession() throws Exception {
        ActivateRequest activateRequest = new ActivateRequest(90l, 0l, "").invoke(false);
        HttpResponse actResponse = activateRequest.getActResponse();
        assertThat(actResponse.getStatus(), is(NOT_FOUND));
        String message = getRenderedString(actResponse);
        assertThat(message, is("{\"error-code\":\"NOT_FOUND\",\"message\":\"Session 90 was not found\"}"));
    }

    @Test
    public void require_that_session_created_from_active_that_is_no_longer_active_cannot_be_activated() throws Exception {
        long sessionId = 1;
        activateAndAssertOK(1, 0);
        sessionId++;
        activateAndAssertOK(sessionId, 1);

        sessionId++;
        ActivateRequest activateRequest = new ActivateRequest(sessionId, 1, "").invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        String message = getRenderedString(actResponse);
        assertThat(message, actResponse.getStatus(), Is.is(BAD_REQUEST));
        assertThat(message,
                containsString("Cannot activate session 3 because the currently active session (2) has changed since session 3 was created (was 1 at creation time)"));
    }

    @Test
    public void testAlreadyActivatedSession() throws Exception {
        activateAndAssertOK(1, 0);
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.ACTIVE, 1l));
        String message = getRenderedString(response);
        assertThat(message, response.getStatus(), Is.is(BAD_REQUEST));
        assertThat(message, containsString("Session 1 is already active"));
    }

    protected abstract SessionHandler createHandler() throws Exception;

    private RemoteSession createRemoteSession(long sessionId, Session.Status status, SessionZooKeeperClient zkClient) throws IOException {
        zkClient.writeStatus(status);
        ZooKeeperClient zkC = new ZooKeeperClient(configCurator, new BaseDeployLogger(), false, pathProvider.getSessionDirs().append(String.valueOf(sessionId)));
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        zkC.feedZKFileRegistries(Collections.singletonMap(modelFactory.getVersion(), new MockFileRegistry()));
        zkC.feedProvisionInfos(Collections.singletonMap(modelFactory.getVersion(), ProvisionInfo.withHosts(Collections.emptySet())));
        TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .configCurator(configCurator)
                .modelFactoryRegistry(new ModelFactoryRegistry(Collections.singletonList(modelFactory)))
                .build();
        RemoteSession session = new RemoteSession(TenantName.from("default"), sessionId, componentRegistry, zkClient);
        remoteSessionRepo.addSession(session);
        return session;
    }

    private LocalSessionRepo addLocalSession(long sessionId, DeployData deployData, SessionZooKeeperClient zkc) {
        writeApplicationId(zkc, deployData.getApplicationName());
        TenantFileSystemDirs tenantFileSystemDirs = TenantFileSystemDirs.createTestDirs(tenant);
        ApplicationPackage app = FilesApplicationPackage.fromFileWithDeployData(testApp, deployData);
        localRepo.addSession(new LocalSession(tenant, sessionId, new SessionTest.MockSessionPreparer(), new SessionContext(app, zkc, new File(tenantFileSystemDirs.path(), String.valueOf(sessionId)), applicationRepo, new HostRegistry<>(), new SuperModelGenerationCounter(curator))));
        return localRepo;
    }

    protected abstract void writeApplicationId(SessionZooKeeperClient zkc, String applicationName);

    protected abstract Session activateAndAssertOK(long sessionId, long previousSessionId, String subPath) throws Exception;

    protected abstract RemoteSession activateAndAssertOK(long sessionId, long previousSessionId) throws Exception;
    
    protected ActivateRequest activateAndAssertOKPut(long sessionId, long previousSessionId, String subPath) throws Exception {
        ActivateRequest activateRequest = new ActivateRequest(sessionId, previousSessionId, subPath);
        activateRequest.invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        String message = getRenderedString(actResponse);
        assertThat(message, actResponse.getStatus(), is(OK));
        assertActivationMessageOK(activateRequest, message);
        RemoteSession session = activateRequest.getSession();
        assertThat(session.getStatus(), is(Session.Status.ACTIVATE));
        return activateRequest;
    }

    protected abstract void assertActivationMessageOK(ActivateRequest activateRequest, String message) throws IOException;

    protected abstract void activateAndAssertError(long sessionId, long previousSessionId, HttpErrorResponse.errorCodes errorCode, String expectedError) throws Exception;

    protected ActivateRequest activateAndAssertErrorPut(long sessionId, long previousSessionId, HttpErrorResponse.errorCodes errorCode, String expectedError) throws Exception {
        ActivateRequest activateRequest = new ActivateRequest(sessionId, previousSessionId, "");
        activateRequest.invoke();
        HttpResponse actResponse = activateRequest.getActResponse();
        RemoteSession session = activateRequest.getSession();
        assertThat(actResponse.getStatus(), is(BAD_REQUEST));
        String message = getRenderedString(actResponse);
        assertThat(message, is("{\"error-code\":\"" + errorCode.name() + "\",\"message\":\"" + expectedError + "\"}"));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        return activateRequest;
    }

    protected void testUnsupportedMethod(com.yahoo.container.jdisc.HttpRequest request) throws Exception {
        HttpResponse response = createHandler().handle(request);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                "Method '" + request.getMethod().name() + "' is not supported");
    }

    protected class ActivateRequest {

        private long sessionId;
        private RemoteSession session;
        private SessionHandler handler;
        private HttpResponse actResponse;
        private Session.Status initialStatus;
        private DeployData deployData;
        private ApplicationMetaData metaData;
        private String subPath;

        public ActivateRequest(long sessionId, long previousSessionId, String subPath) {
            this(sessionId, previousSessionId, Session.Status.PREPARE, subPath);
        }

        public ActivateRequest(long sessionId, long previousSessionId, Session.Status initialStatus, String subPath) {
            this.sessionId = sessionId;
            this.initialStatus = initialStatus; 
            this.deployData = new DeployData("foo", "bar", appName, 0l, sessionId, previousSessionId);
            this.subPath = subPath;
        }

        public RemoteSession getSession() {
            return session;
        }

        public SessionHandler getHandler() {
            return handler;
        }

        public HttpResponse getActResponse() {
            return actResponse;
        }

        public long getSessionId() {
            return sessionId;
        }

        public ApplicationMetaData getMetaData() {
            return metaData;
        }

        public ActivateRequest invoke() throws Exception {
            return invoke(true);
        }

        public ActivateRequest invoke(boolean createLocalSession) throws Exception {
            SessionZooKeeperClient zkClient = new MockSessionZKClient(curator, pathProvider.getSessionDirs().append(String.valueOf(sessionId)), 
                                                                      Optional.of(ProvisionInfo.withHosts(Collections.singleton(new HostSpec("bar", Collections.emptyList())))));
            session = createRemoteSession(sessionId, initialStatus, zkClient);
            if (createLocalSession) {
                LocalSessionRepo repo = addLocalSession(sessionId, deployData, zkClient);
                metaData = repo.getSession(sessionId).getMetaData();
            }
            handler = createHandler();
            actResponse = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.ACTIVE, sessionId, subPath));
            return this;
        }
    }

}
