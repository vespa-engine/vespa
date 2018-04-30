// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.path.Path;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.MockRefeedAction;
import com.yahoo.vespa.config.server.configchange.MockRestartAction;
import com.yahoo.vespa.config.server.http.*;
import com.yahoo.vespa.config.server.session.*;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.jdisc.Response.Status.*;
import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.vespa.config.server.http.HandlerTest.assertHttpStatusCodeErrorCodeAndMessage;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class SessionPrepareHandlerTest extends SessionHandlerTest {
    private static final TenantName tenant = TenantName.from("test");

    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
    private final Clock clock = componentRegistry.getClock();

    private Curator curator;
    private LocalSessionRepo localRepo;

    private String preparedMessage = " prepared.\"}";
    private String tenantMessage = "";
    private RemoteSessionRepo remoteSessionRepo;
    private TenantRepository tenantRepository;

    @Before
    public void setupRepo() {
        curator = new MockCurator();
        localRepo = new LocalSessionRepo(clock);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        preparedMessage = " for tenant '" + tenant + "' prepared.\"";
        tenantMessage = ",\"tenant\":\"" + tenant + "\"";
        tenantRepository = new TenantRepository(componentRegistry, false);
        remoteSessionRepo = new RemoteSessionRepo(tenant);
        TenantBuilder tenantBuilder = TenantBuilder.create(componentRegistry, tenant)
                .withSessionFactory(new MockSessionFactory())
                .withLocalSessionRepo(localRepo)
                .withRemoteSessionRepo(remoteSessionRepo)
                .withApplicationRepo(new MemoryTenantApplications());
        tenantRepository.addTenant(tenantBuilder);
    }

    @Test
    public void require_error_when_session_id_does_not_exist() throws Exception {
        // No session with this id exists
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 9999L));
        assertHttpStatusCodeErrorCodeAndMessage(response, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "Session 9999 was not found");
    }

    @Test
    public void require_error_when_session_id_not_a_number() throws Exception {
        final String session = "notanumber/prepared";
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix + session));
        assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session id in request is not a number, request was 'http://" + hostname + ":" + port + pathPrefix + session + "'");
    }

    @Test
    public void require_that_handler_gives_error_for_unsupported_methods() throws Exception {
        testUnsupportedMethod(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.POST, Cmd.PREPARED, 1L));
        testUnsupportedMethod(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.DELETE, Cmd.PREPARED, 1L));
    }

    private void testUnsupportedMethod(com.yahoo.container.jdisc.HttpRequest request) throws Exception {
        HttpResponse response = createHandler().handle(request);
        assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                                                HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                                                "Method '" + request.getMethod().name() + "' is not supported");
    }

    @Test
    public void require_that_activate_url_is_returned_on_success() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertResponseContains(response, "\"activate\":\"http://foo:1337" + pathPrefix + "1/active\",\"message\":\"Session 1" + preparedMessage);
    }

    @Test
    public void require_debug() throws Exception {
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 9999L, "?debug=true"));
        assertThat(response.getStatus(), is(NOT_FOUND));
        assertThat(SessionHandlerTest.getRenderedString(response), containsString("NotFoundException"));
    }

    @Test
    public void require_verbose() throws Exception {
        MockSession session = new MockSession(1, null);
        session.doVerboseLogging = true;
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L, "?verbose=true"));
        assertThat(response.getStatus(), is(OK));
        assertThat(SessionHandlerTest.getRenderedString(response), containsString("debuglog"));
    }

    /**
     * A mock remote session repo based on contents of local repo. Only works when there is just one session in local repo
     */
    // TODO: Fix this mess
    private SessionZooKeeperClient fromLocalSessionRepo(LocalSessionRepo localRepo) {
        SessionZooKeeperClient zooKeeperClient = null;
        for (LocalSession ls : localRepo.listSessions()) {
            zooKeeperClient = new MockSessionZKClient(curator, tenant, ls.getSessionId());
            if (ls.getStatus()!=null) zooKeeperClient.writeStatus(ls.getStatus());
            RemoteSession remSess = new RemoteSession(tenant, ls.getSessionId(),
                                                      new TestComponentRegistry.Builder().curator(curator).build(),
                                                      zooKeeperClient,
                                                      clock);
            remoteSessionRepo.addSession(remSess);
        }
        return zooKeeperClient;
    }

    @Test
    public void require_get_response_activate_url_on_ok() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler();
        sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        session.setStatus(Session.Status.PREPARE);
        SessionZooKeeperClient zooKeeperClient = fromLocalSessionRepo(localRepo);
        zooKeeperClient.writeStatus(Session.Status.PREPARE);
        HttpResponse getResponse = sessHandler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.GET, Cmd.PREPARED, 1L));
        assertResponseContains(getResponse, "\"activate\":\"http://foo:1337" + pathPrefix +
                "1/active\",\"message\":\"Session 1" + preparedMessage);
    }

    @Test
    public void require_get_response_error_on_not_prepared() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler();
        session.setStatus(Session.Status.NEW);
        SessionZooKeeperClient zooKeeperClient = fromLocalSessionRepo(localRepo);
        zooKeeperClient.writeStatus(Session.Status.NEW);
        HttpResponse getResponse = sessHandler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.GET, Cmd.PREPARED, 1L));
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session not prepared: 1");
        session.setStatus(Session.Status.ACTIVATE);
        zooKeeperClient.writeStatus(Session.Status.ACTIVATE);
        getResponse = sessHandler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.GET, Cmd.PREPARED, 1L));
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session is active: 1");
    }

    @Test
    public void require_cannot_prepare_active_session() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        session.setStatus(Session.Status.ACTIVATE);
        SessionHandler sessionHandler = createHandler();
        HttpResponse putResponse = sessionHandler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertHttpStatusCodeErrorCodeAndMessage(putResponse, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session is active: 1");
    }

    @Test
    public void require_get_response_error_when_session_id_does_not_exist() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler();
        HttpResponse getResponse = sessHandler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.GET, Cmd.PREPARED, 9999L));
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, NOT_FOUND,
                                                HttpErrorResponse.errorCodes.NOT_FOUND,
                                                "Session 9999 was not found");
    }

    @Test
    public void require_that_tenant_is_in_response() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        assertResponseContains(response, tenantMessage);
    }

    @Test
    public void require_that_preparing_with_multiple_tenants_work() throws Exception {
        // Need different repo for 'test2' tenant
        LocalSessionRepo localRepoDefault = new LocalSessionRepo(clock);
        final TenantName defaultTenant = TenantName.from("test2");
        TenantBuilder defaultTenantBuilder = TenantBuilder.create(componentRegistry, defaultTenant)
                .withLocalSessionRepo(localRepoDefault)
                .withRemoteSessionRepo(new RemoteSessionRepo(defaultTenant))
                .withSessionFactory(new MockSessionFactory());
        tenantRepository.addTenant(defaultTenantBuilder);
        final SessionHandler handler = createHandler();

        long sessionId = 1;
        // Deploy with default tenant
        MockSession session = new MockSession(sessionId, null);
        localRepoDefault.addSession(session);
        pathPrefix = "/application/v2/tenant/" + defaultTenant + "/session/";

        HttpResponse response = handler.handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));

        // Same session id, as this is for another tenant
        session = new MockSession(sessionId, null);
        localRepo.addSession(session);
        String applicationName = "myapp";
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId +
                "/prepared?applicationName=" + applicationName;
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));

        sessionId++;
        session = new MockSession(sessionId, null);
        localRepo.addSession(session);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId +
                "/prepared?applicationName=" + applicationName + "&instance=quux";
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
    }

    @Test
    public void require_that_config_change_actions_are_in_response() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseContains(response, "\"configChangeActions\":{\"restart\":[],\"refeed\":[]}");
    }

    @Test
    public void require_that_config_change_actions_are_logged_if_existing() throws Exception {
        List<ServiceInfo> services = Collections.singletonList(
                new ServiceInfo("serviceName", "serviceType", null,
                                ImmutableMap.of("clustername", "foo", "clustertype", "bar"), "configId", "hostName"));
        ConfigChangeActions actions = new ConfigChangeActions(Arrays.asList(
                new MockRestartAction("change", services),
                new MockRefeedAction("change-id", false, "other change", services, "test")));
        MockSession session = new MockSession(1, null, actions);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseContains(response,
                               "Change(s) between active and new application that require restart:\\nIn cluster 'foo' of type 'bar");
        assertResponseContains(response,
                               "Change(s) between active and new application that may require re-feed:\\nchange-id: Consider removing data and re-feed document type 'test'");
    }

    @Test
    public void require_that_config_change_actions_are_not_logged_if_not_existing() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(
                SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseNotContains(response, "Change(s) between active and new application that require restart");
        assertResponseNotContains(response, "Change(s) between active and new application that require re-feed");
    }

    @Test
    public void test_out_of_capacity_response() throws InterruptedException, IOException {
        String message = "Internal error";
        SessionThrowingException session = new SessionThrowingException(new OutOfCapacityException(message));
        localRepo.addSession(session);
        HttpResponse response = createHandler()
                .handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertEquals(400, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.OUT_OF_CAPACITY.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    @Test
    public void test_that_nullpointerexception_gives_internal_server_error() throws InterruptedException, IOException {
        String message = "No nodes available";
        SessionThrowingException session = new SessionThrowingException(new NullPointerException(message));
        localRepo.addSession(session);
        HttpResponse response = createHandler()
                .handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertEquals(500, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.INTERNAL_SERVER_ERROR.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    @Test
    public void test_application_lock_failure() throws InterruptedException, IOException {
        String message = "Timed out after waiting PT1M to acquire lock '/provision/v1/locks/foo/bar/default'";
        SessionThrowingException session =
                new SessionThrowingException(new ApplicationLockException(new UncheckedTimeoutException(message)));
        localRepo.addSession(session);
        HttpResponse response = createHandler()
                .handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertEquals(500, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.APPLICATION_LOCK_FAILURE.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    private Slime getData(HttpResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        Slime data = new Slime();
        new JsonDecoder().decode(data, baos.toByteArray());
        return data;
    }

    private static void assertResponseContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), containsString(string));
    }

    private static void assertResponseNotContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), not(containsString(string)));
    }

    private SessionHandler createHandler() {
        return new SessionPrepareHandler(
                SessionPrepareHandler.testOnlyContext(),
                new ApplicationRepository(tenantRepository,
                                          new MockProvisioner(),
                                          clock),
                tenantRepository,
                componentRegistry.getConfigserverConfig());

    }

    public static class SessionThrowingException extends LocalSession {
        private final RuntimeException exception;

        SessionThrowingException(RuntimeException exception) {
            super(TenantName.defaultName(), 1, null,
                  new SessionContext(null,
                                     new MockSessionZKClient(MockApplicationPackage.createEmpty()),
                                     null,
                                     null,
                                     new HostRegistry<>(),
                                     null));
            this.exception = exception;
        }

        @Override
        public ConfigChangeActions prepare(DeployLogger logger,
                                           PrepareParams params,
                                           Optional<ApplicationSet> application,
                                           Path tenantPath,
                                           Instant now) {
            throw exception;
        }

        @Override
        public Session.Status getStatus() {
            return null;
        }

        @Override
        public Transaction createDeactivateTransaction() {
            return null;
        }

        @Override
        public Transaction createActivateTransaction() {
            return null;
        }

        @Override
        public ApplicationFile getApplicationFile(Path relativePath, Mode mode) {
            return null;
        }

        @Override
        public ApplicationId getApplicationId() {
            return null;
        }

        @Override
        public long getCreateTime() {
            return 0;
        }

        @Override
        public void delete() {  }
    }
}
