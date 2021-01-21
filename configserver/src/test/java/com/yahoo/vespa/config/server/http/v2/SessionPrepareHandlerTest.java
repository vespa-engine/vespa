// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
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
    private static final File app = new File("src/test/resources/deploy/validapp");

    private final Curator curator = new MockCurator();
    private TimeoutBudget timeoutBudget;
    private ApplicationRepository applicationRepository;

    private ConfigserverConfig configserverConfig;
    private String preparedMessage = " prepared.\"}";
    private String tenantMessage = "";
    private TenantRepository tenantRepository;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupRepo() throws IOException {
        configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        Clock clock = Clock.systemUTC();
        timeoutBudget = new TimeoutBudget(clock, Duration.ofSeconds(10));
        tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .withCurator(curator)
                .build();
        tenantRepository.addTenant(tenant);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .build();
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        preparedMessage = " for tenant '" + tenant + "' prepared.\"";
        tenantMessage = ",\"tenant\":\"" + tenant + "\"";
    }

    @Test
    public void require_error_when_session_id_does_not_exist() throws Exception {
        // No session with this id exists
        HttpResponse response = request(HttpRequest.Method.PUT, 9999L);
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
        testUnsupportedMethod(createTestRequest(pathPrefix, HttpRequest.Method.POST, Cmd.PREPARED, 1L));
        testUnsupportedMethod(createTestRequest(pathPrefix, HttpRequest.Method.DELETE, Cmd.PREPARED, 1L));
    }

    private void testUnsupportedMethod(com.yahoo.container.jdisc.HttpRequest request) throws Exception {
        HttpResponse response = createHandler().handle(request);
        assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                                                HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                                                "Method '" + request.getMethod().name() + "' is not supported");
    }

    @Test
    public void require_that_activate_url_is_returned_on_success() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        HttpResponse response = request(HttpRequest.Method.PUT, sessionId);
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertResponseContains(response, "\"activate\":\"http://foo:1337" + pathPrefix + sessionId +
                                         "/active\",\"message\":\"Session " + sessionId + preparedMessage);
    }

    @Test
    public void require_debug() throws Exception {
        HttpResponse response = createHandler().handle(
                createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 9999L, "?debug=true"));
        assertThat(response.getStatus(), is(NOT_FOUND));
        assertThat(SessionHandlerTest.getRenderedString(response), containsString("NotFoundException"));
    }

    @Test
    public void require_verbose() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        HttpResponse response = createHandler().handle(
                createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId, "?verbose=true"));
        System.out.println(getRenderedString(response));
        assertThat(response.getStatus(), is(OK));
        assertThat(getRenderedString(response), containsString("Created application "));
    }

    @Test
    public void require_get_response_activate_url_on_ok() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        request(HttpRequest.Method.PUT, sessionId);
        HttpResponse getResponse = request(HttpRequest.Method.GET, sessionId);
        assertResponseContains(getResponse, "\"activate\":\"http://foo:1337" + pathPrefix +
                sessionId + "/active\",\"message\":\"Session " + sessionId + preparedMessage);
    }

    @Test
    public void require_get_response_error_on_not_prepared() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);

        HttpResponse getResponse = request(HttpRequest.Method.GET, sessionId);
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session not prepared: " + sessionId);

        request(HttpRequest.Method.PUT, sessionId);
        applicationRepository.activate(tenantRepository.getTenant(tenant), sessionId, timeoutBudget, false);

        getResponse = request(HttpRequest.Method.GET, sessionId);
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                                                HttpErrorResponse.errorCodes.BAD_REQUEST,
                                                "Session is active: " + sessionId);
    }

    @Test
    public void require_get_response_error_when_session_id_does_not_exist() throws Exception {
        HttpResponse getResponse = request(HttpRequest.Method.GET, 9999L);
        assertHttpStatusCodeErrorCodeAndMessage(getResponse, NOT_FOUND,
                                                HttpErrorResponse.errorCodes.NOT_FOUND,
                                                "Session 9999 was not found");
    }

    @Test
    public void require_that_tenant_is_in_response() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        HttpResponse response = request(HttpRequest.Method.PUT, sessionId);
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertResponseContains(response, tenantMessage);
    }

    @Test
    public void require_that_preparing_with_multiple_tenants_work() throws Exception {
        SessionHandler handler = createHandler();

        TenantName defaultTenant = TenantName.from("test2");
        tenantRepository.addTenant(defaultTenant);
        ApplicationId applicationId1 = ApplicationId.from(defaultTenant, ApplicationName.from("app"), InstanceName.defaultName());
        long sessionId = applicationRepository.createSession(applicationId1, timeoutBudget, app);

        pathPrefix = "/application/v2/tenant/" + defaultTenant + "/session/";
        HttpResponse response = request(HttpRequest.Method.PUT, sessionId);
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));

        String applicationName = "myapp";
        ApplicationId applicationId2 = ApplicationId.from(tenant.value(), applicationName, "default");
        long sessionId2 = applicationRepository.createSession(applicationId2, timeoutBudget, app);
        assertEquals(sessionId, sessionId2);  // Want to test when they are equal (but for different tenants)

        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId2 +
                "/prepared?applicationName=" + applicationName;
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));

        ApplicationId applicationId3 = ApplicationId.from(tenant.value(), applicationName, "quux");
        long sessionId3 = applicationRepository.createSession(applicationId3, timeoutBudget, app);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId3 +
                "/prepared?applicationName=" + applicationName + "&instance=quux";
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
    }

    @Test
    public void require_that_config_change_actions_are_in_response() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        HttpResponse response = request(HttpRequest.Method.PUT, sessionId);
        assertResponseContains(response, "\"configChangeActions\":{\"restart\":[],\"refeed\":[],\"reindex\":[]}");
    }

    @Test
    public void require_that_config_change_actions_are_not_logged_if_not_existing() throws Exception {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        HttpResponse response = request(HttpRequest.Method.PUT, sessionId);
        assertResponseNotContains(response, "Change(s) between active and new application that may require restart");
        assertResponseNotContains(response, "Change(s) between active and new application that may require re-feed");
        assertResponseNotContains(response, "Change(s) between active and new application that may require re-index");
    }

    @Test
    public void test_out_of_capacity_response() throws IOException {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        String exceptionMessage = "Out of capacity";
        FailingSessionPrepareHandler handler = new FailingSessionPrepareHandler(SessionPrepareHandler.testOnlyContext(),
                                                                                applicationRepository,
                                                                                configserverConfig,
                                                                                new OutOfCapacityException(exceptionMessage));
        HttpResponse response = handler.handle(createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId));
        assertEquals(400, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.OUT_OF_CAPACITY.name()));
        assertThat(data.get().field("message").asString(), is(exceptionMessage));
    }

    @Test
    public void test_that_nullpointerexception_gives_internal_server_error() throws IOException {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        String exceptionMessage = "nullpointer thrown in test handler";
        FailingSessionPrepareHandler handler = new FailingSessionPrepareHandler(SessionPrepareHandler.testOnlyContext(),
                                                                                applicationRepository,
                                                                                configserverConfig,
                                                                                new NullPointerException(exceptionMessage));
        HttpResponse response = handler.handle(createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId));
        assertEquals(500, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.INTERNAL_SERVER_ERROR.name()));
        assertThat(data.get().field("message").asString(), is(exceptionMessage));
    }

    @Test
    public void test_application_lock_failure() throws IOException {
        String exceptionMessage = "Timed out after waiting PT1M to acquire lock '/provision/v1/locks/foo/bar/default'";
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        FailingSessionPrepareHandler handler = new FailingSessionPrepareHandler(SessionPrepareHandler.testOnlyContext(),
                                                                                applicationRepository,
                                                                                configserverConfig,
                                                                                new ApplicationLockException(new UncheckedTimeoutException(exceptionMessage)));
        HttpResponse response = handler.handle(createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId));
        assertEquals(500, response.getStatus());
        Slime data = getData(response);
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.APPLICATION_LOCK_FAILURE.name()));
        assertThat(data.get().field("message").asString(), is(exceptionMessage));
    }

    @Test
    public void test_docker_image_repository() {
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, app);
        String dockerImageRepository = "foo.bar.com:4443/baz";
        request(HttpRequest.Method.PUT, sessionId, Map.of("dockerImageRepository", dockerImageRepository,
                                                          "applicationName", applicationId().application().value()));
        applicationRepository.activate(tenantRepository.getTenant(tenant), sessionId, timeoutBudget, false);
        assertEquals(DockerImage.fromString(dockerImageRepository),
                     applicationRepository.getActiveSession(applicationId()).getDockerImageRepository().get());
    }

    private Slime getData(HttpResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        return SlimeUtils.jsonToSlime(baos.toByteArray());
    }

    private static void assertResponseContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), containsString(string));
    }

    private static void assertResponseNotContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), not(containsString(string)));
    }

    private SessionHandler createHandler() {
        return new SessionPrepareHandler(SessionPrepareHandler.testOnlyContext(), applicationRepository, configserverConfig);
    }

    private HttpResponse request(HttpRequest.Method put, long l) {
        return request(put, l, Map.of());
    }

    private HttpResponse request(HttpRequest.Method put, long l, Map<String, String> requestParameters) {
        return createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, put, Cmd.PREPARED, l, "", null, requestParameters));
    }

    private ApplicationId applicationId() {
        return ApplicationId.from(tenant.value(), "app", "default");
    }

    private static class FailingSessionPrepareHandler extends SessionPrepareHandler {

        private final RuntimeException exceptionToBeThrown;

        public FailingSessionPrepareHandler(Context ctx, ApplicationRepository applicationRepository,
                                            ConfigserverConfig configserverConfig, RuntimeException exceptionToBeThrown) {
            super(ctx, applicationRepository, configserverConfig);
            this.exceptionToBeThrown = exceptionToBeThrown;
        }

        @Override
        protected HttpResponse handlePUT(com.yahoo.container.jdisc.HttpRequest request) {
            throw exceptionToBeThrown;
        }
    }

}
