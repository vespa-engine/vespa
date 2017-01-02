// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.collect.ImmutableMap;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.path.Path;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.MockRefeedAction;
import com.yahoo.vespa.config.server.configchange.MockRestartAction;
import com.yahoo.vespa.config.server.http.*;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.*;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 *
 * @since 5.1.14
 */
public class SessionPrepareHandlerTest extends SessionPrepareHandlerTestBase {
    private static final TenantName tenant = TenantName.from("test");
    private TestTenantBuilder builder;

    @Before
    public void setupRepo() throws Exception {
        TenantApplications applicationRepo = new MemoryTenantApplications();
        curator = new MockCurator();
        localRepo = new LocalSessionRepo(applicationRepo);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        preparedMessage = " for tenant '" + tenant + "' prepared.\"";
        tenantMessage = ",\"tenant\":\"" + tenant + "\"";
        builder = new TestTenantBuilder();
    }

    @Test
    public void require_that_tenant_is_in_response() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler(addTestTenant()).handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        assertResponseContains(response, tenantMessage);
    }

    @Test
    public void require_that_preparing_with_multiple_tenants_work() throws Exception {
        TenantApplications applicationRepoDefault = new MemoryTenantApplications();
        LocalSessionRepo localRepoDefault = new LocalSessionRepo(applicationRepoDefault);
        final TenantName tenantName = TenantName.defaultName();
        addTenant(tenantName, localRepoDefault, new RemoteSessionRepo(), new MockSessionFactory());
        addTestTenant();
        final SessionHandler handler = createHandler(builder);

        long sessionId = 1;
        // Deploy with default tenant
        MockSession session = new MockSession(sessionId, null);
        localRepoDefault.addSession(session);
        pathPrefix = "/application/v2/tenant/default/session/";

        HttpResponse response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, sessionId));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));

        // Same session id, as this is for another tenant
        session = new MockSession(sessionId, null);
        localRepo.addSession(session);
        String applicationName = "myapp";
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId + "/prepared?applicationName=" + applicationName;
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));

        sessionId++;
        session = new MockSession(sessionId, null);
        localRepo.addSession(session);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/" + sessionId + "/prepared?applicationName=" + applicationName + "&instance=quux";
        response = handler.handle(SessionHandlerTest.createTestRequest(pathPrefix));
        assertNotNull(response);
        assertThat(SessionHandlerTest.getRenderedString(response), response.getStatus(), is(OK));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
    }

    @Test
    public void require_that_config_change_actions_are_in_response() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler(addTestTenant()).handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseContains(response, "\"configChangeActions\":{\"restart\":[],\"refeed\":[]}");
    }

    @Test
    public void require_that_config_change_actions_are_logged_if_existing() throws Exception {
        List<ServiceInfo> services = Collections.singletonList(new ServiceInfo("serviceName", "serviceType", null,
                ImmutableMap.of("clustername", "foo", "clustertype", "bar"), "configId", "hostName"));
        ConfigChangeActions actions = new ConfigChangeActions(Arrays.asList(
                new MockRestartAction("change", services),
                new MockRefeedAction("change-id", false, "other change", services, "test")));
        MockSession session = new MockSession(1, null, actions);
        localRepo.addSession(session);
        HttpResponse response = createHandler(addTestTenant()).handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseContains(response, "Change(s) between active and new application that require restart:\\nIn cluster 'foo' of type 'bar");
        assertResponseContains(response, "Change(s) between active and new application that may require re-feed:\\nchange-id: Consider removing data and re-feed document type 'test'");
    }

    @Test
    public void require_that_config_change_actions_are_not_logged_if_not_existing() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler(addTestTenant()).handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertResponseNotContains(response, "Change(s) between active and new application that require restart");
        assertResponseNotContains(response, "Change(s) between active and new application that require re-feed");
    }

    @Test
    public void test_out_of_capacity_response() throws InterruptedException, IOException {
        String message = "No nodes available";
        SessionThrowingException session = new SessionThrowingException(new OutOfCapacityException(message));
        localRepo.addSession(session);
        HttpResponse response = createHandler(addTestTenant()).handle(SessionHandlerTest.createTestRequest(pathPrefix, HttpRequest.Method.PUT, Cmd.PREPARED, 1L));
        assertEquals(400, response.getStatus());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        Slime data = new Slime();
        new JsonDecoder().decode(data, baos.toByteArray());
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.OUT_OF_CAPACITY.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    @Override
    public SessionHandler createHandler() {
        return createHandler(addTestTenant());
    }

    @Override
    public SessionHandler createHandler(RemoteSessionRepo remoteSessionRepo) {
        return createHandler(addTenant(tenant, localRepo, remoteSessionRepo, new MockSessionFactory()));
    }

    private TestTenantBuilder addTestTenant() {
        return addTenant(tenant, localRepo, new RemoteSessionRepo(), new MockSessionFactory());
    }

    static SessionHandler createHandler(TestTenantBuilder builder) {
        final ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder());
        return new SessionPrepareHandler(new Executor() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, AccessLog.voidAccessLog(), builder.createTenants(), configserverConfig,
                                         new ApplicationRepository(builder.createTenants(),
                                                                   HostProvisionerProvider.withProvisioner(new SessionActiveHandlerTest.MockProvisioner()),
                                                                   new MockCurator(),
                                                                   new LogServerLogGrabber(),
                                                                   new ApplicationConvergenceChecker()));
    }

    private TestTenantBuilder addTenant(TenantName tenantName,
                                        LocalSessionRepo localSessionRepo,
                                        RemoteSessionRepo remoteSessionRepo,
                                        SessionFactory sessionFactory) {
        builder.createTenant(tenantName).withSessionFactory(sessionFactory)
                .withLocalSessionRepo(localSessionRepo)
                .withRemoteSessionRepo(remoteSessionRepo)
                .withApplicationRepo(new MemoryTenantApplications());
        return builder;
    }

    public static class SessionThrowingException extends LocalSession {
        private final RuntimeException exception;

        public SessionThrowingException(RuntimeException exception) {
            super(TenantName.defaultName(), 1, null, new SessionContext(null, new MockSessionZKClient(MockApplicationPackage.createEmpty()), null, null, new HostRegistry<>(), null));
            this.exception = exception;
        }

        @Override
        public ConfigChangeActions prepare(DeployLogger logger, PrepareParams params, Optional<ApplicationSet> application, Path tenantPath) {
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
